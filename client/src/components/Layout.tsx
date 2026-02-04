import React, { useEffect, useState } from 'react';
import { Outlet, NavLink } from 'react-router-dom';
import { LayoutDashboard, Wallet, PiggyBank, Receipt, Calendar, Menu, LogOut, Settings } from 'lucide-react';
import classNames from 'classnames';
import { ensureMonth, getMonthSummary, getSavingsGoals } from '../api';
import { formatCurrency } from '../utils';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useAuth } from '../context/AuthContext';

const Layout: React.FC = () => {
    const [currentMonth, setCurrentMonth] = useState('');
    const [availableBalance, setAvailableBalance] = useState(0);
    const [totalAssets, setTotalAssets] = useState(0);
    const [isCollapsed, setIsCollapsed] = useState(false);
    const { user, logout } = useAuth();

    const navItems = [
        { name: 'Обзор', path: '/', icon: LayoutDashboard },
        { name: 'Месяц', path: '/month', icon: Calendar },
        { name: 'Категории', path: '/categories', icon: Receipt },
        { name: 'Копилки', path: '/savings', icon: PiggyBank },
    ];

    useEffect(() => {
        const fetchFinancialData = async () => {
            try {
                const now = new Date();
                // Текущий месяц для отображения
                setCurrentMonth(format(now, 'LLLL yyyy', { locale: ru }));

                // Получить данные текущего месяца
                const monthRes = await ensureMonth(now.getFullYear(), now.getMonth() + 1);
                const summaryRes = await getMonthSummary(monthRes.data.id);
                
                // Сумма всех копилок
                const goalsRes = await getSavingsGoals();
                const savingsTotal = goalsRes.data.reduce((sum: number, goal: any) => sum + (goal.current_amount || 0), 0);

                // Доступный остаток = доходы - расходы
                const available = summaryRes.data.income - summaryRes.data.expenses;
                setAvailableBalance(available);

                // Всего активов = доступно + накопления
                setTotalAssets(available + savingsTotal);

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
        
        return () => {
            clearInterval(interval);
            window.removeEventListener('savingsUpdated', handleSavingsUpdate);
        };
    }, []);

    return (
        <div className="flex h-screen overflow-hidden">
            {/* Sidebar Desktop */}
            <aside className={classNames(
            "bg-gradient-to-br from-emerald-600 to-emerald-800 text-white shadow-xl flex-shrink-0 hidden md:flex flex-col relative z-20 transition-all duration-300",
            isCollapsed ? "w-16" : "w-58"
        )}>
                <div className={classNames("flex items-center border-b border-white/10", isCollapsed ? "justify-center p-4" : "p-6 gap-3")}>
                    <button 
                        onClick={() => setIsCollapsed(!isCollapsed)}
                        className={classNames(
                            "rounded-lg transition-all duration-200",
                            isCollapsed ? "bg-white/20 backdrop-blur-md p-2" : "bg-white/20 backdrop-blur-md p-2"
                        )}
                    >
                        {isCollapsed ? (
                            <Menu className="w-6 h-6 text-white" />
                        ) : (
                            <Wallet className="w-6 h-6 text-white" />
                        )}
                    </button>
                    {!isCollapsed && <h1 className="text-xl font-bold tracking-tight">FinKeeper</h1>}
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
                                        ? 'bg-white/20 text-white shadow-lg shadow-emerald-900/20 backdrop-blur-sm'
                                        : 'text-emerald-100 hover:bg-white/10 hover:text-white'
                                )
                            }
                        >
                            <item.icon className={classNames("w-5 h-5 transition-colors", ({ isActive }: { isActive: boolean }) => isActive ? 'text-white' : 'text-slate-400 group-hover:text-white')} />
                            {!isCollapsed && <span>{item.name}</span>}
                        </NavLink>
                    ))}
                </nav>

                {/* Финансовая сводка и юзер */}
                {!isCollapsed && (
                <div className="p-4 border-t border-white/10 space-y-3">
                     <div className="flex justify-between items-center px-2">
                        <span className="text-sm font-medium text-emerald-100 truncate max-w-[100px]">{user?.username}</span>
                        <div className="flex gap-1">
                            <NavLink to="/settings" className="text-emerald-200 hover:text-white transition-colors p-1 rounded hover:bg-white/10" title="Настройки">
                                <Settings className="w-4 h-4" />
                            </NavLink>
                            <button onClick={logout} className="text-emerald-200 hover:text-white transition-colors p-1 rounded hover:bg-white/10" title="Выйти">
                                <LogOut className="w-4 h-4" />
                            </button>
                        </div>
                    </div>

                    <p className="text-xs text-slate-400 uppercase tracking-wider px-2 pt-2 border-t border-white/10">{currentMonth}</p>
                    
                    <div className="bg-white/5 rounded-lg p-3 space-y-2">
                        <div className="flex justify-between items-center">
                            <span className="text-xs text-slate-400">Всего активов</span>
                            <span className="text-sm font-bold text-white">{formatCurrency(totalAssets)}</span>
                        </div>
                        <div className="flex justify-between items-center">
                            <span className="text-xs text-slate-400">Доступно</span>
                            <span className={classNames("text-sm font-semibold", availableBalance >= 0 ? "text-brand-light" : "text-red-400")}>
                                {formatCurrency(availableBalance)}
                            </span>
                        </div>
                    </div>
                    
                    <p className="text-[10px] text-slate-500 text-center">Домашняя бухгалтерия v1.1.0</p>
                </div>
                )}
                {isCollapsed && (
                    <div className="p-4 border-t border-white/10 flex flex-col items-center gap-3">
                        <NavLink to="/settings" className="text-emerald-200 hover:text-white transition-colors" title="Настройки">
                            <Settings className="w-5 h-5" />
                        </NavLink>
                        <button onClick={logout} className="text-emerald-200 hover:text-white transition-colors" title="Выйти">
                            <LogOut className="w-5 h-5" />
                        </button>
                    </div>
                )}
            </aside>

            {/* Mobile Nav (Bottom) */}
            <nav className="md:hidden fixed bottom-0 left-0 right-0 bg-white border-t border-gray-200 flex justify-around p-3 z-50 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.05)]">
                {navItems.map((item) => (
                    <NavLink
                        key={item.path}
                        to={item.path}
                        className={({ isActive }) =>
                            classNames(
                                'flex flex-col items-center justify-center p-2 rounded-xl transition-all',
                                isActive ? 'text-brand bg-blue-50' : 'text-slate-400'
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
                            'flex flex-col items-center justify-center p-2 rounded-xl transition-all',
                            isActive ? 'text-brand bg-blue-50' : 'text-slate-400'
                        )
                    }
                >
                    <Settings className="w-6 h-6" />
                    <span className="text-[10px] mt-1 font-medium">Настр.</span>
                </NavLink>
            </nav>


            {/* Main Content */}
            <main 
                    className="flex-1 overflow-auto p-4 md:p-8 pb-24 md:pb-8 bg-gradient-to-br from-slate-50 to-slate-100"
                    onClick={() => !isCollapsed && setIsCollapsed(true)}
                >
                <Outlet />
            </main>
        </div>
    );
};

export default Layout;
