import React, { useEffect, useState, useRef, useCallback } from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { ArrowRight, LayoutDashboard, Wallet, PiggyBank, Receipt, Calendar, Menu, LogOut, Settings, ShieldAlert } from 'lucide-react';
import classNames from 'classnames';
import { ensureMonth, getMonthSummary, getSavingsGoals, getCumulativeBalance, getBudgets } from '../api';
import type { SavingsGoal } from '../api';
import { formatCurrency } from '../utils';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useAuth } from '../context/AuthContext';
import { useWebSocket, useDataChanged } from '../hooks/useWebSocket';
import {
    dismissSecurityOnboardingPrompt,
    markSecurityOnboardingPromptShown,
    resetSecurityOnboardingPromptState,
    shouldShowSecurityOnboardingPrompt,
} from '../onboarding/securityPrompt';

const DASHBOARD_MONTH_KEY = 'dashboard_selected_month';
const APP_VERSION = import.meta.env.VITE_APP_VERSION;

const getSelectedDashboardDate = (): Date => {
    const saved = localStorage.getItem(DASHBOARD_MONTH_KEY);
    if (saved) {
        const parsed = JSON.parse(saved);
        return new Date(parsed.year, parsed.month - 1, 1);
    }
    return new Date();
};

const Layout: React.FC = () => {
    const [currentMonth, setCurrentMonth] = useState('');
    const [availableBalance, setAvailableBalance] = useState(0);
    const [resourceBalance, setResourceBalance] = useState(0);
    const [totalAssets, setTotalAssets] = useState(0);
    const [sidebarRefreshError, setSidebarRefreshError] = useState<string | null>(null);
    const [isSecurityPromptVisible, setIsSecurityPromptVisible] = useState(false);
    const [isCollapsed, setIsCollapsed] = useState(false);
    const { user, logout, uiSettings } = useAuth();
    const mainRef = useRef<HTMLElement>(null);
    const scrollTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
    const location = useLocation();
    const navigate = useNavigate();

    // Establish WebSocket connection (one per Layout mount)
    useWebSocket();

    const navItems = [
        { name: 'Обзор', path: '/', icon: LayoutDashboard },
        { name: 'Месяц', path: '/month', icon: Calendar },
        { name: 'Категории', path: '/categories', icon: Receipt },
        { name: 'Копилки', path: '/savings', icon: PiggyBank },
    ];

    const refreshSidebarSummary = useCallback(async () => {
        try {
            const selectedDate = getSelectedDashboardDate();
            setCurrentMonth(format(selectedDate, 'LLLL yyyy', { locale: ru }));

            const monthRes = await ensureMonth(selectedDate.getFullYear(), selectedDate.getMonth() + 1);
            const summaryRes = await getMonthSummary(monthRes.data.id);
            const budgetsRes = await getBudgets(monthRes.data.id);
            const goalsRes = await getSavingsGoals();
            const savingsTotal = goalsRes.data.reduce((sum: number, goal: SavingsGoal) => sum + (goal.current_amount || 0), 0);
            const totalLimit = budgetsRes.data.reduce((sum, item) => sum + item.limit_amount, 0);
            const available = Math.max(0, totalLimit - summaryRes.data.expenses);
            const cumulativeRes = await getCumulativeBalance(selectedDate.getFullYear(), selectedDate.getMonth() + 1);

            setAvailableBalance(available);
            setResourceBalance(cumulativeRes.data.cumulativeBalance);
            setTotalAssets(cumulativeRes.data.cumulativeBalance + savingsTotal);
            setSidebarRefreshError(null);
        } catch (e) {
            console.error('Sidebar refresh error:', e);
            setSidebarRefreshError('Не удалось обновить боковую сводку. Показаны последние доступные значения.');
        }
    }, []);

    // Scrollbar auto-hide logic
    useEffect(() => {
        const mainEl = mainRef.current;
        if (!mainEl) return;

        const handleScroll = () => {
            mainEl.classList.add('scrolling');
            
            if (scrollTimeout.current) {
                clearTimeout(scrollTimeout.current);
            }

            scrollTimeout.current = setTimeout(() => {
                mainEl.classList.remove('scrolling');
            }, 2000);
        };

        mainEl.addEventListener('scroll', handleScroll);
        return () => {
            mainEl.removeEventListener('scroll', handleScroll);
            if (scrollTimeout.current) clearTimeout(scrollTimeout.current);
        };
    }, []);

    // Sync sidebar state with settings changes ONLY when settings change
    useEffect(() => {
        if (uiSettings?.autoCollapseSidebar !== undefined) {
            setIsCollapsed(uiSettings.autoCollapseSidebar);
        }
    }, [uiSettings?.autoCollapseSidebar]);

    useEffect(() => {
        void refreshSidebarSummary();
        
        // Обновлять каждые 30 секунд
        const interval = setInterval(() => {
            void refreshSidebarSummary();
        }, 30000);
        
        // Listen for savings updates
        const handleSavingsUpdate = () => {
            void refreshSidebarSummary();
        };
        window.addEventListener('savingsUpdated', handleSavingsUpdate);
        
        // Listen for dashboard month changes
        const handleDashboardMonthChange = () => {
            void refreshSidebarSummary();
        };
        window.addEventListener('dashboardMonthChanged', handleDashboardMonthChange);
        
        return () => {
            clearInterval(interval);
            window.removeEventListener('savingsUpdated', handleSavingsUpdate);
            window.removeEventListener('dashboardMonthChanged', handleDashboardMonthChange);
        };
    }, [refreshSidebarSummary]);

    // Refresh sidebar financial data on any WebSocket data change
    useDataChanged(null, async () => {
        await refreshSidebarSummary();
    });

    useEffect(() => {
        if (!user) {
            setIsSecurityPromptVisible(false);
            return;
        }

        if (user.recoverabilityStatus === 'protected') {
            resetSecurityOnboardingPromptState();
            setIsSecurityPromptVisible(false);
            return;
        }

        if (location.pathname === '/settings') {
            setIsSecurityPromptVisible(false);
            return;
        }

        if (shouldShowSecurityOnboardingPrompt(user.recoverabilityStatus)) {
            markSecurityOnboardingPromptShown(user.recoverabilityStatus);
            setIsSecurityPromptVisible(true);
            return;
        }

        setIsSecurityPromptVisible(false);
    }, [location.pathname, user]);

    const handleSecurityPromptDismiss = useCallback(() => {
        if (!user) {
            setIsSecurityPromptVisible(false);
            return;
        }

        dismissSecurityOnboardingPrompt(user.recoverabilityStatus);
        setIsSecurityPromptVisible(false);
    }, [user]);

    const handleSecurityPromptOpenSettings = useCallback(() => {
        if (user) {
            dismissSecurityOnboardingPrompt(user.recoverabilityStatus);
        }
        setIsSecurityPromptVisible(false);
        navigate('/settings');
    }, [navigate, user]);

    return (
        <div className="flex h-screen overflow-hidden">
            {/* Sidebar Desktop - Glassmorphism Style */}
            <aside className={classNames(
            "bg-gradient-to-b from-emerald-900/85 via-emerald-600/85 to-teal-900/85 text-white backdrop-blur-2xl border-r border-white/20 shadow-[10px_0_20px_-10px_rgba(0,0,0,0.5)] flex-shrink-0 hidden md:flex flex-col relative z-20 transition-all duration-300",
            isCollapsed ? "w-16" : "w-58"
        )}>
                <div className={classNames("flex items-center border-b border-white/10", isCollapsed ? "justify-center p-4" : "p-6 gap-3")}>
                    <button 
                        onClick={() => setIsCollapsed(!isCollapsed)}
                        className={classNames(
                            "rounded-lg transition-all duration-200 hover:bg-white/20 active:scale-95",
                            isCollapsed ? "bg-white/10 backdrop-blur-md p-2" : "bg-white/10 backdrop-blur-md p-2"
                        )}
                    >
                        {isCollapsed ? (
                            <Menu className="w-6 h-6 text-white" />
                        ) : (
                            <Wallet className="w-6 h-6 text-white" />
                        )}
                    </button>
                    {!isCollapsed && <h1 className="text-xl font-bold tracking-tight text-white drop-shadow-sm">FinKeeper</h1>}
                </div>

                <nav className={classNames("flex-1 p-4 space-y-2 mt-2", isCollapsed ? "justify-center" : "")}>
                    {navItems.map((item) => (
                        <NavLink
                            key={item.path}
                            to={item.path}
                            className={({ isActive }) =>
                                classNames(
                                    'flex items-center rounded-lg transition-all duration-200 group font-medium text-sm',
                                    isCollapsed ? 'justify-center px-2 py-3' : 'gap-3 px-4 py-3',
                                    isActive
                                        ? 'bg-white/20 text-white shadow-lg shadow-emerald-900/20 backdrop-blur-md border border-white/10'
                                        : 'text-emerald-100 hover:bg-white/10 hover:text-white'
                                )
                            }
                        >
                            <item.icon className="w-5 h-5 transition-colors" />
                            {!isCollapsed && <span>{item.name}</span>}
                        </NavLink>
                    ))}
                </nav>

                {/* Финансовая сводка и юзер */}
                {!isCollapsed && (
                <div className="p-4 border-t border-white/10 space-y-3 bg-gradient-to-t from-black/20 to-transparent">
                     <div className="flex justify-between items-center px-2">
                        <span className="text-sm font-medium text-emerald-50 truncate max-w-[120px] shadow-black/10 drop-shadow-sm">{user?.username}</span>
                        <div className="flex gap-1">
                            <NavLink to="/settings" className="text-emerald-200 hover:text-white transition-colors p-1.5 rounded-md hover:bg-white/10" title="Настройки">
                                <Settings className="w-4 h-4" />
                            </NavLink>
                            <button onClick={logout} className="text-emerald-200 hover:text-white transition-colors p-1.5 rounded-md hover:bg-white/10" title="Выйти">
                                <LogOut className="w-4 h-4" />
                            </button>
                        </div>
                    </div>

                    <p className="text-[10px] text-emerald-200/70 uppercase tracking-widest px-2 pt-2 border-t border-white/10">{currentMonth}</p>

                    {sidebarRefreshError ? (
                        <div className="mx-2 rounded-xl border border-amber-200/40 bg-amber-300/10 px-3 py-2 text-[11px] text-amber-50/90">
                            <p>{sidebarRefreshError}</p>
                            <button
                                onClick={() => {
                                    void refreshSidebarSummary();
                                }}
                                className="mt-2 rounded-lg bg-white/10 px-3 py-1.5 text-[11px] font-medium text-white transition-colors hover:bg-white/20"
                            >
                                Обновить сводку
                            </button>
                        </div>
                    ) : null}
                    
                    <div className="bg-white/10 backdrop-blur-sm border border-white/5 rounded-xl p-3 space-y-2 shadow-inner">
                        <div className="flex justify-between items-center">
                            <span className="text-xs text-emerald-100/80">Всего активов</span>
                            <span className="text-sm font-bold text-white tracking-wide">{formatCurrency(totalAssets)}</span>
                        </div>
                        <div className="flex justify-between items-center">
                            <span className="text-xs text-emerald-100/80">Ресурс</span>
                            <span className="text-sm font-semibold text-emerald-200">
                                {formatCurrency(resourceBalance)}
                            </span>
                        </div>
                        <div className="flex justify-between items-center">
                            <span className="text-xs text-emerald-100/80">Доступно</span>
                            <span className={classNames("text-sm font-semibold", availableBalance >= 0 ? "text-emerald-200" : "text-red-300")}>
                                {formatCurrency(availableBalance)}
                            </span>
                        </div>
                    </div>
                    
                    <p className="text-[10px] text-emerald-300/50 text-center pt-1">Домашняя бухгалтерия v{APP_VERSION}</p>
                </div>
                )}
                {isCollapsed && (
                    <div className="p-4 border-t border-white/10 flex flex-col items-center gap-3 bg-gradient-to-t from-black/20 to-transparent">
                        <NavLink to="/settings" className="text-emerald-200 hover:text-white transition-colors p-2 rounded-lg hover:bg-white/10" title="Настройки">
                            <Settings className="w-5 h-5" />
                        </NavLink>
                        <button onClick={logout} className="text-emerald-200 hover:text-white transition-colors p-2 rounded-lg hover:bg-white/10" title="Выйти">
                            <LogOut className="w-5 h-5" />
                        </button>
                    </div>
                )}
            </aside>

            {/* Mobile Nav (Bottom) */}
            <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-gradient-to-r from-emerald-900/90 via-emerald-700/90 to-teal-900/90 backdrop-blur-2xl border-t border-white/20 flex overflow-x-auto no-scrollbar p-3 z-50 shadow-[0_-10px_20px_-5px_rgba(0,0,0,0.3)]">
                <div className="flex justify-around min-w-full gap-2">
                    {navItems.map((item) => (
                        <NavLink
                            key={item.path}
                            to={item.path}
                            className={({ isActive }) =>
                                classNames(
                                    'flex flex-col items-center justify-center p-2 rounded-xl transition-all min-w-[70px]',
                                    isActive 
                                        ? 'bg-white/20 text-white shadow-lg backdrop-blur-md border border-white/10' 
                                        : 'text-emerald-100/70 hover:text-white'
                                )
                            }
                        >
                            <item.icon className="w-6 h-6" />
                            <span className="text-[10px] mt-1 font-medium">{item.name}</span>
                        </NavLink>
                    ))}
                    <NavLink
                        to="/settings"
                        className={({ isActive }) =>
                            classNames(
                                'flex flex-col items-center justify-center p-2 rounded-xl transition-all min-w-[70px]',
                                isActive 
                                    ? 'bg-white/20 text-white shadow-lg backdrop-blur-md border border-white/10' 
                                    : 'text-emerald-100/70 hover:text-white'
                            )
                        }
                    >
                        <Settings className="w-6 h-6" />
                        <span className="text-[10px] mt-1 font-medium">Настр.</span>
                    </NavLink>
                    <button
                        onClick={logout}
                        className="flex flex-col items-center justify-center p-2 rounded-xl transition-all min-w-[70px] text-emerald-100/70 hover:text-white hover:bg-white/10"
                    >
                        <LogOut className="w-6 h-6" />
                        <span className="text-[10px] mt-1 font-medium">Выход</span>
                    </button>
                </div>
            </nav>


            {/* Main Content with Gradient and Inner Glow */}
            <main 
                    ref={mainRef}
                    className="smart-scrollbar flex-1 overflow-auto p-4 md:p-8 pb-24 md:pb-8 bg-gradient-to-br from-green-50/20 via-emerald-50 to-teal-100 shadow-[inset_0_0_80px_rgba(16,185,129,0.3)]"
                    onClick={() => {
                        if (uiSettings?.autoCollapseSidebar && !isCollapsed) {
                            setIsCollapsed(true);
                        }
                    }}
                >
                {isSecurityPromptVisible ? (
                    <div className="mx-auto mb-6 max-w-5xl rounded-3xl border border-amber-200 bg-white/90 p-5 shadow-[0_12px_40px_rgba(245,158,11,0.12)] backdrop-blur-xl">
                        <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                            <div className="flex gap-4">
                                <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-amber-100 text-amber-700">
                                    <ShieldAlert className="h-6 w-6" />
                                </div>
                                <div className="space-y-2">
                                    <p className="text-sm font-semibold uppercase tracking-[0.18em] text-amber-700">
                                        Защита аккаунта
                                    </p>
                                    <div>
                                        <h2 className="text-xl font-bold text-slate-900">
                                            Добавьте email для восстановления доступа
                                        </h2>
                                        <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-600">
                                            Сейчас аккаунт не защищён: без подтверждённого email самостоятельное восстановление доступа недоступно. Подключите email в настройках, чтобы не потерять доступ к данным.
                                        </p>
                                    </div>
                                </div>
                            </div>

                            <div className="flex flex-col gap-3 sm:flex-row md:justify-end">
                                <button
                                    onClick={handleSecurityPromptOpenSettings}
                                    className="inline-flex items-center justify-center gap-2 rounded-2xl bg-emerald-600 px-5 py-3 text-sm font-semibold text-white shadow-lg shadow-emerald-200 transition-all hover:bg-emerald-700"
                                >
                                    Добавить email
                                    <ArrowRight className="h-4 w-4" />
                                </button>
                                <button
                                    onClick={handleSecurityPromptDismiss}
                                    className="inline-flex items-center justify-center rounded-2xl border border-slate-200 bg-white px-5 py-3 text-sm font-semibold text-slate-700 transition-all hover:bg-slate-50"
                                >
                                    Позже
                                </button>
                            </div>
                        </div>
                    </div>
                ) : null}
                <Outlet />
            </main>
        </div>
    );
};

export default Layout;