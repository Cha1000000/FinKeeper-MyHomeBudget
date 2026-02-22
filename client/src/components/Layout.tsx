import React, { useEffect, useState, useRef } from 'react';
import { Outlet, NavLink } from 'react-router-dom';
import { LayoutDashboard, Wallet, PiggyBank, Receipt, Calendar, Menu, LogOut, Settings } from 'lucide-react';
import classNames from 'classnames';
import { ensureMonth, getMonthSummary, getSavingsGoals, getCumulativeBalance } from '../api';
import type { SavingsGoal } from '../api';
import { formatCurrency } from '../utils';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useAuth } from '../context/AuthContext';
import { useWebSocket, useDataChanged } from '../hooks/useWebSocket';

const DASHBOARD_MONTH_KEY = 'dashboard_selected_month';

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
    const [totalAssets, setTotalAssets] = useState(0);
    const [isCollapsed, setIsCollapsed] = useState(false);
    const { user, logout, uiSettings } = useAuth();
    const mainRef = useRef<HTMLElement>(null);
    const scrollTimeout = useRef<any>(null);

    // Establish WebSocket connection (one per Layout mount)
    useWebSocket();

    const navItems = [
        { name: 'Обзор', path: '/', icon: LayoutDashboard },
        { name: 'Месяц', path: '/month', icon: Calendar },
        { name: 'Категории', path: '/categories', icon: Receipt },
        { name: 'Копилки', path: '/savings', icon: PiggyBank },
    ];

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

    // Sync sidebar state with settings changes
    useEffect(() => {
        if (uiSettings?.autoCollapseSidebar !== undefined && uiSettings.autoCollapseSidebar !== isCollapsed) {
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setIsCollapsed(uiSettings.autoCollapseSidebar);
        }
    }, [uiSettings?.autoCollapseSidebar, isCollapsed]);

    useEffect(() => {
        const fetchFinancialData = async () => {
            try {
                // Используем выбранный месяц из localStorage (Dashboard)
                const selectedDate = getSelectedDashboardDate();
                // Месяц для отображения
                setCurrentMonth(format(selectedDate, 'LLLL yyyy', { locale: ru }));

                // Получить данные выбранного месяца
                const monthRes = await ensureMonth(selectedDate.getFullYear(), selectedDate.getMonth() + 1);
                const summaryRes = await getMonthSummary(monthRes.data.id);
                
                // Сумма всех копилок
                const goalsRes = await getSavingsGoals();
                const savingsTotal = goalsRes.data.reduce((sum: number, goal: SavingsGoal) => sum + (goal.current_amount || 0), 0);

                // Доступный остаток = доходы - расходы (текущий месяц)
                const available = summaryRes.data.income - summaryRes.data.expenses;
                setAvailableBalance(available);

                // Всего активов = кумулятивный баланс до выбранного месяца + накопления
                const cumulativeRes = await getCumulativeBalance(selectedDate.getFullYear(), selectedDate.getMonth() + 1);
                setTotalAssets(cumulativeRes.data.cumulativeBalance + savingsTotal);

            } catch (e) {
                console.error('Error fetching financial data:', e);
            }
        };

        fetchFinancialData();
        
        // Обновлять каждые 30 секунд
        const interval = setInterval(fetchFinancialData, 30000);
        
        // Listen for savings updates
        const handleSavingsUpdate = () => fetchFinancialData();
        window.addEventListener('savingsUpdated', handleSavingsUpdate);
        
        // Listen for dashboard month changes
        const handleDashboardMonthChange = () => fetchFinancialData();
        window.addEventListener('dashboardMonthChanged', handleDashboardMonthChange);
        
        return () => {
            clearInterval(interval);
            window.removeEventListener('savingsUpdated', handleSavingsUpdate);
            window.removeEventListener('dashboardMonthChanged', handleDashboardMonthChange);
        };
    }, []);

    // Refresh sidebar financial data on any WebSocket data change
    useDataChanged(null, async () => {
        try {
            const selectedDate = getSelectedDashboardDate();
            const monthRes = await ensureMonth(selectedDate.getFullYear(), selectedDate.getMonth() + 1);
            const summaryRes = await getMonthSummary(monthRes.data.id);
            const goalsRes = await getSavingsGoals();
            const savingsTotal = goalsRes.data.reduce((sum: number, goal: SavingsGoal) => sum + (goal.current_amount || 0), 0);
            const available = summaryRes.data.income - summaryRes.data.expenses;
            setAvailableBalance(available);
            const cumulativeRes = await getCumulativeBalance(selectedDate.getFullYear(), selectedDate.getMonth() + 1);
            setTotalAssets(cumulativeRes.data.cumulativeBalance + savingsTotal);
            setCurrentMonth(format(selectedDate, 'LLLL yyyy', { locale: ru }));
        } catch (e) {
            console.error('WS refresh error:', e);
        }
    });

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
                            <item.icon className={classNames("w-5 h-5 transition-colors", ({ isActive }: { isActive: boolean }) => isActive ? 'text-white' : 'text-slate-300 group-hover:text-white')} />
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
                    
                    <div className="bg-white/10 backdrop-blur-sm border border-white/5 rounded-xl p-3 space-y-2 shadow-inner">
                        <div className="flex justify-between items-center">
                            <span className="text-xs text-emerald-100/80">Всего активов</span>
                            <span className="text-sm font-bold text-white tracking-wide">{formatCurrency(totalAssets)}</span>
                        </div>
                        <div className="flex justify-between items-center">
                            <span className="text-xs text-emerald-100/80">Доступно</span>
                            <span className={classNames("text-sm font-semibold", availableBalance >= 0 ? "text-emerald-200" : "text-red-300")}>
                                {formatCurrency(availableBalance)}
                            </span>
                        </div>
                    </div>
                    
                    <p className="text-[10px] text-emerald-300/50 text-center pt-1">Домашняя бухгалтерия v1.1.0</p>
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
                <Outlet />
            </main>
        </div>
    );
};

export default Layout;