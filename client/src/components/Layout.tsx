import React, { useEffect, useState } from 'react';
import { Outlet, NavLink } from 'react-router-dom';
import { LayoutDashboard, Wallet, PiggyBank, Receipt, Calendar } from 'lucide-react';
import classNames from 'classnames';
import { ensureMonth, getMonthSummary, getSavingsGoals } from '../api';
import { formatCurrency } from '../utils';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';

const Layout: React.FC = () => {
    const [currentMonth, setCurrentMonth] = useState('');
    const [availableBalance, setAvailableBalance] = useState(0);
    const [totalAssets, setTotalAssets] = useState(0);

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
                
                // Доступный остаток (доходы - расходы за месяц)
                const balance = summaryRes.data.income - summaryRes.data.expenses;
                setAvailableBalance(balance);

                // Сумма всех копилок
                const goalsRes = await getSavingsGoals();
                const savingsTotal = goalsRes.data.reduce((sum: number, goal: any) => sum + (goal.current_amount || 0), 0);

                // Общая сумма = баланс + копилки
                setTotalAssets(balance + savingsTotal);

            } catch (e) {
                console.error('Error fetching financial data:', e);
            }
        };

        fetchFinancialData();
        // Обновлять каждые 60 секунд
        const interval = setInterval(fetchFinancialData, 60000);
        return () => clearInterval(interval);
    }, []);

    return (
        <div className="flex h-screen overflow-hidden bg-background">
            {/* Sidebar Desktop */}
            <aside className="w-64 bg-primary text-white shadow-xl flex-shrink-0 hidden md:flex flex-col relative z-20">
                <div className="p-6 flex items-center gap-3 border-b border-white/10">
                    <div className="bg-brand p-2 rounded-lg">
                        <Wallet className="w-6 h-6 text-white" />
                    </div>
                    <h1 className="text-xl font-bold tracking-tight">FinKeeper</h1>
                </div>

                <nav className="flex-1 p-4 space-y-2 mt-2">
                    {navItems.map((item) => (
                        <NavLink
                            key={item.path}
                            to={item.path}
                            className={({ isActive }) =>
                                classNames(
                                    'flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 group font-medium text-sm',
                                    isActive
                                        ? 'bg-brand text-white shadow-md shadow-brand/20'
                                        : 'text-slate-300 hover:bg-white/5 hover:text-white'
                                )
                            }
                        >
                            <item.icon className={classNames("w-5 h-5 transition-colors", ({ isActive }: { isActive: boolean }) => isActive ? 'text-white' : 'text-slate-400 group-hover:text-white')} />
                            {item.name}
                        </NavLink>
                    ))}
                </nav>

                {/* Финансовая сводка */}
                <div className="p-4 border-t border-white/10 space-y-3">
                    <p className="text-xs text-slate-400 uppercase tracking-wider px-2">{currentMonth}</p>
                    
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
                    
                    <p className="text-[10px] text-slate-500 text-center">Домашняя бухгалтерия v1.0</p>
                </div>
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
            </nav>


            {/* Main Content */}
            <main className="flex-1 overflow-auto p-4 md:p-8 pb-24 md:pb-8">
                <Outlet />
            </main>
        </div>
    );
};

export default Layout;
