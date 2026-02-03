import React, { useEffect, useState } from 'react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
    PieChart, Pie, Cell
} from 'recharts';
import { TrendingUp, TrendingDown, Wallet, PiggyBank } from 'lucide-react';
import { getAnalyticsTrend, ensureMonth, getMonthSummary, getExpenses } from '../api';
import { formatCurrency } from '../utils';

const COLORS = ['#6b8e23', '#2f3e30', '#d4a017', '#8fbc8f', '#a0522d', '#556b2f', '#c0c0c0', '#bdb76b'];

const Dashboard: React.FC = () => {
    const [trendData, setTrendData] = useState<any[]>([]);
    const [currentSummary, setCurrentSummary] = useState<any>(null);
    const [expenseStructure, setExpenseStructure] = useState<any[]>([]);

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

                // Group expenses by category
                const catMap: Record<string, number> = {};
                expRes.data.forEach((e: any) => {
                    if (!catMap[e.category_name]) catMap[e.category_name] = 0;
                    catMap[e.category_name] += e.amount;
                });

                const pieData = Object.keys(catMap).map(name => ({
                    name, value: catMap[name]
                })).sort((a, b) => b.value - a.value);

                setExpenseStructure(pieData);

            } catch (e) {
                console.error(e);
            }
        };
        fetchData();
    }, []);

    return (
        <div className="space-y-8 max-w-7xl mx-auto">
            <header>
                <h2 className="text-2xl font-bold text-gray-900 tracking-tight">Обзор финансов</h2>
                <p className="text-gray-500 text-sm mt-1">Сводка за текущий месяц и аналитика</p>
            </header>

            {/* Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
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
                    <p className="text-xl font-bold text-slate-900 mt-1">{formatCurrency(currentSummary?.savings || 0)}</p>
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

                {/* Всего активов = Доступный баланс + Накопления */}
                <div className="bg-primary p-3 rounded-2xl shadow-sm border border-white/20 backdrop-blur-md transition-shadow overflow-hidden">
                    <div className="relative z-10">
                        <div className="flex justify-between items-start mb-3">
                            <div className="p-2.5 bg-white/20 backdrop-blur-md shadow-sm border border-white/20 rounded-xl text-white">
                                <Wallet className="w-5 h-5" />
                            </div>
                        </div>
                        <p className="text-s font-medium text-slate-100/90">Всего активов</p>
                        <p className="text-xl font-bold text-white mt-1">
                            {formatCurrency((currentSummary?.income || 0) - (currentSummary?.expenses || 0) + (currentSummary?.savings || 0))}
                        </p>
                    </div>
                    <div className="absolute -right-8 -bottom-8 w-32 h-32 bg-brand/20 rounded-full blur-2xl"></div>
                </div>
            </div>

            {/* Charts Row */}
            <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">

                {/* Trend Chart (Wider) */}
                <div className="lg:col-span-3 bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
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
                                    formatter={(value: any) => formatCurrency(value)}
                                    contentStyle={{
                                        borderRadius: '12px',
                                        border: 'none',
                                        boxShadow: '0 10px 15px -3px rgb(0 0 0 / 0.1)',
                                        padding: '12px'
                                    }}
                                />
                                <Legend wrapperStyle={{ paddingTop: '20px' }} />
                                <Bar dataKey="income" name="Доход" fill="#6b8e23" radius={[4, 4, 0, 0]} barSize={20} />
                                <Bar dataKey="expense" name="Расход" fill="#c62828" radius={[4, 4, 0, 0]} barSize={20} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* Breakdown Chart */}
                <div className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
                    <h3 className="text-lg font-bold text-slate-800 mb-6">Структура расходов</h3>
                    <div className="h-64 flex items-center justify-center relative">
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
                                    <Tooltip formatter={(value: any) => formatCurrency(value)} />
                                </PieChart>
                            </ResponsiveContainer>
                        ) : (
                            <div className="text-center">
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
                    {/* Legend List */}
                    <div className="mt-4 space-y-2 max-h-40 overflow-y-auto pr-2 custom-scrollbar">
                        {expenseStructure.slice(0, 5).map((entry, idx) => (
                            <div key={idx} className="flex items-center justify-between text-sm">
                                <div className="flex items-center gap-2">
                                    <div className="w-2 h-2 rounded-full" style={{ backgroundColor: COLORS[idx % COLORS.length] }}></div>
                                    <span className="text-slate-600 truncate max-w-[120px]">{entry.name}</span>
                                </div>
                                <span className="font-medium text-slate-800">{formatCurrency(entry.value)}</span>
                            </div>
                        ))}
                    </div>
                </div>

            </div>
        </div>
    );
};

export default Dashboard;
