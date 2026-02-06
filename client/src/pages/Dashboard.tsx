import React, { useEffect, useState } from 'react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
    PieChart, Pie, Cell
} from 'recharts';
import { TrendingUp, TrendingDown, Wallet, PiggyBank } from 'lucide-react';
import { getAnalyticsTrend, ensureMonth, getMonthSummary, getExpenses, getSavingsGoals } from '../api';
import type { SavingsGoal } from '../api';
import { formatCurrency } from '../utils';

const COLORS = ['#6b8e23', '#2f3e30', '#d4a017', '#8fbc8f', '#a0522d', '#556b2f', '#c0c0c0', '#bdb76b'];

interface TrendItem {
    month: string;
    income: number;
    expense: number;
    savings: number;
}

interface SummaryData {
    income: number;
    expenses: number;
    savings: number;
    balance: number;
}

interface ExpenseStructureItem {
    name: string;
    value: number;
}

const Dashboard: React.FC = () => {
    const [trendData, setTrendData] = useState<TrendItem[]>([]);
    const [currentSummary, setCurrentSummary] = useState<SummaryData | null>(null);
    const [expenseStructure, setExpenseStructure] = useState<ExpenseStructureItem[]>([]);
    const [totalSavings, setTotalSavings] = useState<number>(0);

    useEffect(() => {
        const fetchData = async () => {
            try {
                // 1. Trend Data
                const trendRes = await getAnalyticsTrend();
                setTrendData(trendRes.data);

                // 2. Current Month Summary
                const now = new Date();
                const mRes = await ensureMonth(now.getFullYear(), now.getMonth() + 1);
                const summaryRes = await getMonthSummary(mRes.data.id);
                setCurrentSummary(summaryRes.data);

                // 3. Category Breakdown (Pie Chart)
                const expRes = await getExpenses(mRes.data.id);

                // Group expenses by category (exclude hidden savings expenses)
                const catMap: Record<string, number> = {};
                expRes.data.forEach((e) => {
                    // Skip hidden savings-related expenses
                    if (e.category_name === 'Пополнение копилки') return;
                    if (e.category_name) {
                        if (!catMap[e.category_name]) catMap[e.category_name] = 0;
                        catMap[e.category_name] += e.amount;
                    }
                });

                const pieData = Object.keys(catMap).map(name => ({
                    name, value: catMap[name]
                })).sort((a, b) => b.value - a.value);

                setExpenseStructure(pieData);

                // 4. Total Savings from all goals
                const savingsRes = await getSavingsGoals();
                const totalSavingsAmount = savingsRes.data.reduce((sum: number, goal: SavingsGoal) => sum + goal.current_amount, 0);
                setTotalSavings(totalSavingsAmount);

            } catch (e) {
                console.error(e);
            }
        };
        fetchData();
        
        // Listen for savings updates
        const handleSavingsUpdate = () => fetchData();
        window.addEventListener('savingsUpdated', handleSavingsUpdate);
        
        return () => {
            window.removeEventListener('savingsUpdated', handleSavingsUpdate);
        };
    }, []);

    return (
        <div className="space-y-8 max-w-7xl mx-auto">
            <header>
                <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Обзор финансов</h2>
                <p className="text-gray-500 text-sm mt-1">Сводка за текущий месяц и аналитика</p>
            </header>

            {/* Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6 gap-4">
                <div className="bg-emerald-50 p-3 rounded-2xl shadow-sm border border-slate-100 transition-shadow">
                    <div className="flex justify-between items-start mb-3">
                        <div className="p-2.5 bg-white/20 backdrop-blur-md shadow-sm border border-white/30 rounded-xl text-brand">
                            <TrendingUp className="w-5 h-5" />
                        </div>
                    </div>
                    <p className="text-s font-medium text-slate-500">Доходы</p>
                    <p className="text-xl font-bold text-slate-900 mt-1">{formatCurrency(currentSummary?.income || 0)}</p>
                </div>

                <div className="bg-rose-50 p-3 rounded-2xl shadow-sm border border-slate-100 transition-shadow">
                    <div className="flex justify-between items-start mb-3">
                        <div className="p-2.5 bg-white/20 backdrop-blur-md shadow-sm border border-white/30 rounded-xl text-red-600">
                            <TrendingDown className="w-5 h-5" />
                        </div>
                    </div>
                    <p className="text-s font-medium text-slate-500">Расходы</p>
                    <p className="text-xl font-bold text-slate-900 mt-1">{formatCurrency(currentSummary?.expenses || 0)}</p>
                </div>

                <div className="bg-teal-100 p-3 rounded-2xl shadow-sm border border-slate-100 transition-shadow">
                    <div className="flex justify-between items-start mb-3">
                        <div className="p-2.5 bg-white/20 backdrop-blur-md shadow-sm border border-white/30 rounded-xl text-warning">
                            <PiggyBank className="w-5 h-5" />
                        </div>
                    </div>
                    <p className="text-s font-medium text-slate-500">Накопления</p>
                    <p className="text-xl font-bold text-slate-900 mt-1">{formatCurrency(totalSavings)}</p>
                </div>

                {/* Savings Share Card */}
                <div className="bg-blue-50 p-3 rounded-2xl shadow-sm border border-slate-100 transition-shadow">
                    <div className="flex justify-between items-start mb-3">
                        <div className="p-2.5 bg-white/20 backdrop-blur-md shadow-sm border border-white/30 rounded-xl text-blue-600">
                            <PiggyBank className="w-5 h-5" />
                        </div>
                    </div>
                    <p className="text-s font-medium text-slate-500">% в копилку</p>
                    <div className="flex items-baseline gap-2 mt-1">
                        <p className="text-xl font-bold text-slate-900">
                            {currentSummary?.income ? ((currentSummary.savings / currentSummary.income) * 100).toFixed(1) : '0.0'}%
                        </p>
                        <span className="text-xs text-slate-500">от дохода</span>
                    </div>
                </div>

                {/* Доступный баланс = Доходы - Расходы */}
                <div className={`${((currentSummary?.income || 0) - (currentSummary?.expenses || 0)) >= 0 ? 'bg-yellow-50 border-yellow-100' : 'bg-red-100 border-red-100'} p-3 rounded-2xl shadow-sm border transition-shadow`}>
                    <div className="flex justify-between items-start mb-3">
                        <div className="p-2.5 bg-white/20 backdrop-blur-md shadow-sm border border-white/30 rounded-xl text-brand">
                            <Wallet className="w-5 h-5" />
                        </div>
                    </div>
                    <p className="text-s font-medium text-slate-500">Доступно</p>
                    <p className={`text-xl font-bold mt-1 ${((currentSummary?.income || 0) - (currentSummary?.expenses || 0)) >= 0 ? 'text-slate-900' : 'text-red-600'}`}>
                        {formatCurrency((currentSummary?.income || 0) - (currentSummary?.expenses || 0))}
                    </p>
                </div>

                {/* Всего активов = Доступно + Накопления */}
                <div className="bg-primary p-3 rounded-2xl shadow-sm border border-white/20 backdrop-blur-md transition-shadow overflow-hidden">
                    <div className="relative z-10">
                        <div className="flex justify-between items-start mb-3">
                            <div className="p-2.5 bg-white/20 backdrop-blur-md shadow-sm border border-white/20 rounded-xl text-white">
                                <Wallet className="w-5 h-5" />
                            </div>
                        </div>
                        <p className="text-s font-medium text-slate-100/90">Всего активов</p>
                        <p className="text-xl font-bold text-white mt-1">
                            {formatCurrency((currentSummary?.income || 0) - (currentSummary?.expenses || 0) + totalSavings)}
                        </p>
                    </div>
                    <div className="absolute -right-8 -bottom-8 w-32 h-32 bg-brand/20 rounded-full blur-2xl"></div>
                </div>
            </div>

            {/* Charts Row */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

                {/* Trend Chart */}
                <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
                    <div className="flex items-center justify-between mb-6">
                        <h3 className="text-lg font-bold text-slate-800">Динамика финансов</h3>
                        <span className="text-xs font-medium text-slate-400 bg-slate-50 px-3 py-1 rounded-full">6 месяцев</span>
                    </div>
                    <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={trendData} barGap={8}>
                                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                                <XAxis
                                    dataKey="month"
                                    tick={{ fontSize: 12, fill: '#64748b' }}
                                    axisLine={false}
                                    tickLine={false}
                                    dy={10}
                                />
                                <YAxis hide />
                                <Tooltip
                                    cursor={{ fill: '#f8fafc' }}
                                    formatter={(value: number | undefined) => formatCurrency(value ?? 0)}
                                    contentStyle={{
                                        borderRadius: '12px',
                                        border: 'none',
                                        boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
                                        padding: '12px'
                                    }}
                                />
                                <Legend wrapperStyle={{ paddingTop: '20px' }} />
                                <Bar dataKey="income" name="Доход" fill="#6b8e23" radius={[4, 4, 0, 0]} barSize={12} />
                                <Bar dataKey="expense" name="Расход" fill="#c62828" radius={[4, 4, 0, 0]} barSize={12} />
                                <Bar dataKey="savings" name="Накопления" fill="#d4a017" radius={[4, 4, 0, 0]} barSize={12} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* Breakdown Chart & Table */}
                <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex flex-col">
                    <h3 className="text-lg font-bold text-slate-800 mb-6">Структура расходов</h3>
                    
                    <div className="flex flex-col xl:flex-row items-center gap-6 h-full">
                        {/* Chart */}
                        <div className="w-full xl:flex-1 h-64 relative min-w-0">
                            {expenseStructure.length > 0 ? (
                                <ResponsiveContainer width="100%" height="100%">
                                    <PieChart>
                                        <Pie
                                            data={expenseStructure}
                                            cx="50%"
                                            cy="50%"
                                            innerRadius={60}
                                            outerRadius={80}
                                            paddingAngle={5}
                                            dataKey="value"
                                            stroke="none"
                                        >
                                            {expenseStructure.map((_entry, index) => (
                                                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                            ))}
                                        </Pie>
                                        <Tooltip formatter={(value: number | undefined) => formatCurrency(value ?? 0)} />
                                    </PieChart>
                                </ResponsiveContainer>
                            ) : (
                                <div className="text-center h-full flex flex-col justify-center">
                                    <div className="w-16 h-16 bg-slate-50 rounded-full flex items-center justify-center mx-auto mb-3 text-slate-300">
                                        <Wallet className="w-8 h-8" />
                                    </div>
                                    <p className="text-slate-400 text-sm">Нет расходов</p>
                                </div>
                            )}
                            {/* Center Text overlay */}
                            {expenseStructure.length > 0 && (
                                <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                                    <span className="text-xs font-bold text-slate-400">Total</span>
                                </div>
                            )}
                        </div>

                        {/* Detailed Table */}
                        <div className="w-full xl:flex-1 overflow-auto max-h-80 custom-scrollbar min-w-0">
                            <table className="w-full text-sm relative border-separate border-spacing-0 table-fixed">
                                <thead className="text-xs text-slate-400 font-medium uppercase border-b border-slate-100 sticky top-0 bg-white z-10 shadow-sm">
                                    <tr>
                                        <th className="text-left py-3 font-medium bg-white w-[55%]">Категория</th>
                                        <th className="text-right py-3 font-medium bg-white w-[30%]">Сумма</th>
                                        <th className="text-right py-3 pr-4 font-medium bg-white w-[15%]">%</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-50">
                                    {expenseStructure.map((entry, idx) => {
                                        const totalExp = currentSummary?.expenses || 1; // avoid div by zero
                                        const share = (entry.value / totalExp) * 100;
                                        
                                        return (
                                            <tr key={idx} className="group hover:bg-slate-50 transition-colors">
                                                <td className="py-2.5 pr-2 truncate max-w-0">
                                                    <div className="flex items-center gap-2">
                                                        <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: COLORS[idx % COLORS.length] }}></div>
                                                        <span className="text-slate-700 truncate font-medium">{entry.name}</span>
                                                    </div>
                                                </td>
                                                <td className="py-2.5 px-2 text-right text-slate-600 tabular-nums whitespace-nowrap">
                                                    {formatCurrency(entry.value)}
                                                </td>
                                                <td className="py-2.5 pl-2 pr-4 text-right text-slate-400 tabular-nums text-xs">
                                                    {share.toFixed(1)}%
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

            </div>
        </div>
    );
};

export default Dashboard;
