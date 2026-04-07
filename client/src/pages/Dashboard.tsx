import React, { useEffect, useState, useCallback } from 'react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
    PieChart, Pie, Cell
} from 'recharts';
import { TrendingUp, TrendingDown, Wallet, PiggyBank, ChevronLeft, ChevronRight } from 'lucide-react';
import { getAnalyticsTrend, ensureMonth, getMonthSummary, getExpenses, getSavingsGoals, getCumulativeBalance, getBudgets } from '../api';
import type { SavingsGoal } from '../api';
import { formatCurrency } from '../utils';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useDataChanged } from '../hooks/useWebSocket';
import { useTheme } from '../context/ThemeContext';
import PageState from '../components/PageState';
import StatusBanner from '../components/StatusBanner';

const COLORS = ['#10b981', '#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b', '#06b6d4', '#6366f1', '#14b8a6'];
const DOT_COLOR_CLASSES = ['bg-emerald-500', 'bg-blue-500', 'bg-violet-500', 'bg-pink-500', 'bg-amber-500', 'bg-cyan-500', 'bg-indigo-500', 'bg-teal-500'];

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

const DASHBOARD_MONTH_KEY = 'dashboard_selected_month';

const getInitialDate = (): Date => {
    const saved = localStorage.getItem(DASHBOARD_MONTH_KEY);
    if (saved) {
        const parsed = JSON.parse(saved);
        return new Date(parsed.year, parsed.month - 1, 1);
    }
    return new Date();
};

const Dashboard: React.FC = () => {
    const [currentDate, setCurrentDate] = useState(getInitialDate);
    const [trendData, setTrendData] = useState<TrendItem[]>([]);
    const [currentSummary, setCurrentSummary] = useState<SummaryData | null>(null);
    const [expenseStructure, setExpenseStructure] = useState<ExpenseStructureItem[]>([]);
    const [totalSavings, setTotalSavings] = useState<number>(0);
    const [cumulativeBalance, setCumulativeBalance] = useState<number>(0);
    const [totalLimit, setTotalLimit] = useState<number>(0);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const { resolvedTheme } = useTheme();

    const saveMonth = (date: Date) => {
        localStorage.setItem(DASHBOARD_MONTH_KEY, JSON.stringify({
            year: date.getFullYear(),
            month: date.getMonth() + 1
        }));
        // Notify Layout to update sidebar financial data
        window.dispatchEvent(new Event('dashboardMonthChanged'));
    };

    const prevMonth = () => {
        setCurrentDate(prev => {
            const newDate = new Date(prev);
            newDate.setMonth(newDate.getMonth() - 1);
            saveMonth(newDate);
            return newDate;
        });
    };

    const nextMonth = () => {
        setCurrentDate(prev => {
            const newDate = new Date(prev);
            newDate.setMonth(newDate.getMonth() + 1);
            saveMonth(newDate);
            return newDate;
        });
    };

    const fetchData = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            // 1. Trend Data
            const trendRes = await getAnalyticsTrend();
            setTrendData(trendRes.data);

            // 2. Selected Month Summary
            const mRes = await ensureMonth(currentDate.getFullYear(), currentDate.getMonth() + 1);
            const summaryRes = await getMonthSummary(mRes.data.id);
            setCurrentSummary(summaryRes.data);

            // 3. Category Breakdown (Pie Chart)
            const [expRes, budgetsRes] = await Promise.all([
                getExpenses(mRes.data.id),
                getBudgets(mRes.data.id)
            ]);

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
            setTotalLimit(budgetsRes.data.reduce((sum, item) => sum + item.limit_amount, 0));

            // 4. Total Savings from all goals
            const savingsRes = await getSavingsGoals();
            const totalSavingsAmount = savingsRes.data.reduce((sum: number, goal: SavingsGoal) => sum + goal.current_amount, 0);
            setTotalSavings(totalSavingsAmount);

            // 5. Cumulative balance up to and including selected month
            const cumulativeRes = await getCumulativeBalance(currentDate.getFullYear(), currentDate.getMonth() + 1);
            setCumulativeBalance(cumulativeRes.data.cumulativeBalance);

        } catch (e) {
            console.error(e);
            setError('Не удалось загрузить обзор. Проверьте соединение и попробуйте ещё раз.');
        } finally {
            setIsLoading(false);
        }
    }, [currentDate]);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        fetchData();
        
        // Listen for savings updates
        const handleSavingsUpdate = () => fetchData();
        window.addEventListener('savingsUpdated', handleSavingsUpdate);
        
        return () => {
            window.removeEventListener('savingsUpdated', handleSavingsUpdate);
        };
    }, [fetchData]);

    // Refresh on WebSocket data changes
    useDataChanged(null, () => { fetchData(); });

    if (isLoading && !currentSummary) {
        return (
            <PageState
                variant="loading"
                title="Загружаем обзор"
                description="Собираем сводку, структуру расходов и накопления за выбранный месяц."
            />
        );
    }

    if (error && !currentSummary) {
        return (
            <PageState
                variant="error"
                title="Не удалось открыть обзор"
                description={error}
                actionLabel="Повторить"
                onAction={() => {
                    void fetchData();
                }}
            />
        );
    }

    return (
        <div className="mx-auto w-full min-w-0 max-w-5xl space-y-8">
            <header>
                <h2 className="text-2xl font-bold text-slate-900 dark:text-[var(--color-text-main)] tracking-tight">Обзор финансов</h2>
                <p className="text-slate-500 dark:text-[var(--color-text-muted)] text-sm mt-1">Сводка за выбранный месяц и аналитика</p>
            </header>

            {error && (
                <StatusBanner variant="error" title="Обзор обновлён не полностью">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <span>{error}</span>
                        <button
                            onClick={() => {
                                void fetchData();
                            }}
                            className="rounded-xl bg-rose-600 px-4 py-2 text-white transition-colors hover:bg-rose-700 dark:bg-rose-700 dark:hover:bg-rose-600"
                        >
                            Повторить
                        </button>
                    </div>
                </StatusBanner>
            )}

            {/* Month Navigation */}
            <div className="bg-[var(--color-surface)] p-3 rounded-2xl shadow-sm border border-[var(--color-border-default)] flex items-center justify-between transition-colors">
                <button
                    onClick={prevMonth}
                    aria-label="Предыдущий месяц"
                    title="Предыдущий месяц"
                    className="p-2 hover:bg-[var(--color-surface-soft)] rounded-full transition-colors"
                >
                    <ChevronLeft className="w-5 h-5 text-slate-600 dark:text-[var(--color-text-muted)]" />
                </button>
                <span className="text-lg font-semibold text-slate-800 dark:text-[var(--color-text-main)] capitalize">
                    {format(currentDate, 'LLLL yyyy', { locale: ru })}
                </span>
                <button
                    onClick={nextMonth}
                    aria-label="Следующий месяц"
                    title="Следующий месяц"
                    className="p-2 hover:bg-[var(--color-surface-soft)] rounded-full transition-colors"
                >
                    <ChevronRight className="w-5 h-5 text-slate-600 dark:text-[var(--color-text-muted)]" />
                </button>
            </div>

            {/* Total Assets Top Card */}
            <div className="bg-[var(--color-primary)]/95 p-3 rounded-3xl shadow-[0_10px_40px_-10px_rgba(27,144,91,0.4)] dark:shadow-[0_10px_40px_-10px_rgba(16,185,129,0.3)] border border-white/20 backdrop-blur-xl transition-shadow overflow-hidden relative flex items-center justify-between">
                <div className="relative z-10 flex items-center gap-4">
                    <div className="p-3 bg-white/10 backdrop-blur-md shadow-sm border border-white/10 rounded-2xl text-white">
                        <Wallet className="w-6 h-6" />
                    </div>
                    <div>
                        <p className="text-white/80 text-lg font-medium mb-0.5">Всего активов</p>
                        <p className="text-3xl font-bold text-white tracking-tight">
                            {formatCurrency(cumulativeBalance + totalSavings)}
                        </p>
                    </div>
                </div>
                <div className="absolute -right-16 -bottom-16 w-48 h-48 bg-white/10 rounded-full blur-3xl"></div>
                <div className="absolute -left-16 -top-16 w-48 h-48 bg-black/10 rounded-full blur-3xl"></div>
            </div>

            {/* Stats Cards */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3 gap-5">
                <div className="bg-[image:var(--color-stat-emerald-bg)] backdrop-blur-md p-4 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-emerald-border)] hover:shadow-[0_12px_40px_rgba(16,185,129,0.15)] transition-all">
                    <div className="flex gap-4 items-center mb-4">
                        <div className="p-3 bg-emerald-100/80 dark:bg-emerald-900/60 rounded-2xl text-emerald-600 dark:text-emerald-400 shadow-sm border border-emerald-200/50 dark:border-emerald-800/50">
                            <TrendingUp className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-sm font-medium text-emerald-800/80 dark:text-emerald-300/80">Доходы</p>
                            <p className="text-2xl font-bold text-emerald-950 dark:text-emerald-100 tracking-tight mt-0.5">{formatCurrency(currentSummary?.income || 0)}</p>
                        </div>
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-rose-bg)] backdrop-blur-md p-4 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-rose-border)] hover:shadow-[0_12px_40px_rgba(244,63,94,0.15)] transition-all">
                    <div className="flex gap-4 items-center mb-4">
                        <div className="p-3 bg-rose-100/80 dark:bg-rose-900/60 rounded-2xl text-rose-600 dark:text-rose-400 shadow-sm border border-rose-200/50 dark:border-rose-800/50">
                            <TrendingDown className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-sm font-medium text-rose-800/80 dark:text-rose-300/80">Расходы</p>
                            <p className="text-2xl font-bold text-rose-950 dark:text-rose-100 tracking-tight mt-0.5">{formatCurrency(currentSummary?.expenses || 0)}</p>
                        </div>
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-cyan-bg)] backdrop-blur-md p-4 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-cyan-border)] hover:shadow-[0_12px_40px_rgba(6,182,212,0.15)] transition-all">
                    <div className="flex gap-4 items-center mb-4">
                        <div className="p-3 bg-cyan-100/80 dark:bg-cyan-900/60 rounded-2xl text-cyan-600 dark:text-cyan-400 shadow-sm border border-cyan-200/50 dark:border-cyan-800/50">
                            <PiggyBank className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-sm font-medium text-cyan-800/80 dark:text-cyan-300/80">Накопления</p>
                            <p className="text-2xl font-bold text-cyan-950 dark:text-cyan-100 tracking-tight mt-0.5">{formatCurrency(totalSavings)}</p>
                        </div>
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-blue-bg)] backdrop-blur-md p-5 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-blue-border)] hover:shadow-[0_12px_40px_rgba(59,130,246,0.15)] transition-all">
                    <div className="flex flex-col justify-center h-full">
                        <p className="text-sm font-medium text-blue-800/80 dark:text-blue-300/80 mb-1">% в копилку</p>
                        <div className="flex items-baseline gap-2">
                            <p className="text-2xl font-bold text-indigo-950 dark:text-indigo-100 tracking-tight">
                                {currentSummary?.income ? ((currentSummary.savings / currentSummary.income) * 100).toFixed(1) : '0.0'}%
                            </p>
                            <span className="text-xs text-blue-500/80 dark:text-blue-400/80">от дохода</span>
                        </div>
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-amber-bg)] backdrop-blur-md p-5 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-amber-border)] hover:shadow-[0_12px_40px_rgba(245,158,11,0.15)] transition-all relative overflow-hidden">
                    {(Math.max(0, totalLimit - (currentSummary?.expenses || 0))) < 0 && (
                        <div className="absolute top-0 right-0 w-24 h-24 bg-rose-200 dark:bg-rose-900/50 rounded-bl-full blur-2xl opacity-40"></div>
                    )}
                    <div className="flex flex-col justify-center h-full relative z-10">
                        <p className="text-sm font-medium text-amber-800/80 dark:text-amber-300/80 mb-1">Лимит на расходы</p>
                        <p className={`text-2xl font-bold tracking-tight ${Math.max(0, totalLimit - (currentSummary?.expenses || 0)) >= 0 ? 'text-amber-950 dark:text-amber-100' : 'text-rose-600 dark:text-rose-400'}`}>
                            {formatCurrency(Math.max(0, totalLimit - (currentSummary?.expenses || 0)))}
                        </p>
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-orange-bg)] backdrop-blur-md p-5 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-orange-border)] hover:shadow-[0_12px_40px_rgba(249,115,22,0.15)] transition-all">
                    <div className="flex flex-col justify-center h-full">
                        <p className="text-sm font-medium text-orange-800/80 dark:text-orange-300/80 mb-1">В наличии без накоплений</p>
                        <p className="text-2xl font-bold text-orange-950 dark:text-orange-100 tracking-tight">
                            {formatCurrency((cumulativeBalance + totalSavings) - totalSavings)}
                        </p>
                    </div>
                </div>
            </div>

            {/* Charts Row */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">

                {/* Trend Chart */}
                <div className="min-w-0 overflow-hidden bg-[var(--color-surface)]/90 backdrop-blur-md p-6 rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-sm dark:shadow-none border border-[var(--color-border-default)] transition-colors">
                    <div className="flex items-center justify-between mb-8">
                        <h3 className="text-lg font-bold text-slate-800 dark:text-[var(--color-text-main)] tracking-tight">Динамика финансов</h3>
                        <span className="text-xs font-medium text-slate-500 dark:text-[var(--color-text-muted)] bg-slate-100 dark:bg-[var(--color-surface-soft)] px-3 py-1.5 rounded-full transition-colors">6 месяцев</span>
                    </div>
                    <div className="h-80 w-full min-w-0">
                        <ResponsiveContainer width="100%" height="100%" minWidth={0} minHeight={320}>
                            <BarChart data={trendData} barGap={4}>
                                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={resolvedTheme === 'dark' ? '#2a3441' : '#f8fafc'} />
                                <XAxis
                                    dataKey="month"
                                    tick={{ fontSize: 12, fill: resolvedTheme === 'dark' ? '#8b9a8e' : '#94a3b8' }}
                                    axisLine={false}
                                    tickLine={false}
                                    dy={10}
                                />
                                <YAxis hide />
                                <Tooltip
                                    cursor={{ fill: resolvedTheme === 'dark' ? '#1a222d' : '#f8fafc' }}
                                    formatter={(value: number | undefined) => formatCurrency(value ?? 0)}
                                    contentStyle={{
                                        borderRadius: '16px',
                                        border: resolvedTheme === 'dark' ? '1px solid #2a3441' : '1px solid #f1f5f9',
                                        backgroundColor: resolvedTheme === 'dark' ? '#111820' : '#ffffff',
                                        color: resolvedTheme === 'dark' ? '#e8f5ec' : '#1e293b',
                                        boxShadow: '0 10px 40px -10px rgb(0 0 0 / 0.1)',
                                        padding: '12px'
                                    }}
                                />
                                <Legend wrapperStyle={{ paddingTop: '20px' }} iconType="circle" />
                                <Bar dataKey="income" name="Доходы" fill="#10b981" radius={[6, 6, 0, 0]} barSize={10} />
                                <Bar dataKey="expense" name="Расходы" fill="#ef4444" radius={[6, 6, 0, 0]} barSize={10} />
                                <Bar dataKey="savings" name="Накопления" fill="#8b5cf6" radius={[6, 6, 0, 0]} barSize={10} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                {/* Breakdown Chart & Table */}
                <div className="min-w-0 overflow-hidden bg-[var(--color-surface)]/90 backdrop-blur-md p-6 rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-sm dark:shadow-none border border-[var(--color-border-default)] flex flex-col transition-colors">
                    <h3 className="text-lg font-bold text-slate-800 dark:text-[var(--color-text-main)] tracking-tight mb-8">Структура расходов</h3>
                    
                    <div className="flex flex-col xl:flex-row items-center gap-8 h-full">
                        {/* Chart */}
                        <div className="relative h-64 w-full min-w-0 xl:flex-1">
                            {expenseStructure.length > 0 ? (
                                <ResponsiveContainer width="100%" height="100%" minWidth={0} minHeight={256}>
                                    <PieChart>
                                        <Pie
                                            data={expenseStructure}
                                            cx="50%"
                                            cy="50%"
                                            innerRadius={65}
                                            outerRadius={85}
                                            paddingAngle={4}
                                            dataKey="value"
                                            stroke="none"
                                            cornerRadius={4}
                                        >
                                            {expenseStructure.map((_entry, index) => (
                                                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                            ))}
                                        </Pie>
                                        <Tooltip 
                                            formatter={(value: number | undefined) => formatCurrency(value ?? 0)}
                                            contentStyle={{
                                                borderRadius: '16px',
                                                border: resolvedTheme === 'dark' ? '1px solid #2a3441' : '1px solid #f1f5f9',
                                                backgroundColor: resolvedTheme === 'dark' ? '#111820' : '#ffffff',
                                                color: resolvedTheme === 'dark' ? '#e8f5ec' : '#1e293b',
                                                boxShadow: '0 10px 40px -10px rgb(0 0 0 / 0.1)',
                                                padding: '12px'
                                            }}
                                        />
                                    </PieChart>
                                </ResponsiveContainer>
                            ) : (
                                <div className="text-center h-full flex flex-col justify-center">
                                    <div className="w-16 h-16 bg-slate-50 dark:bg-slate-800/50 rounded-2xl flex items-center justify-center mx-auto mb-3 text-slate-300 dark:text-slate-500">
                                        <Wallet className="w-8 h-8" />
                                    </div>
                                    <p className="text-slate-400 dark:text-slate-500 text-sm font-medium">Нет расходов</p>
                                </div>
                            )}
                            {/* Center Text overlay */}
                            {expenseStructure.length > 0 && (
                                <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                                    <span className="text-xs font-medium text-slate-400 dark:text-[var(--color-text-muted)] mb-1">Всего</span>
                                    <span className="text-xl font-bold text-slate-800 dark:text-[var(--color-text-main)] tracking-tight">
                                        {formatCurrency(currentSummary?.expenses || 0)}
                                    </span>
                                </div>
                            )}
                        </div>

                        {/* Detailed Table */}
                        <div className="w-full xl:flex-1 overflow-auto max-h-80 custom-scrollbar pr-2 min-w-0">
                            <table className="w-full text-sm relative border-separate border-spacing-0 table-fixed">
                                <thead>
                                    <tr>
                                        <th className="text-left pb-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider w-[55%]">Категория</th>
                                        <th className="text-right pb-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider w-[30%]">Сумма</th>
                                        <th className="text-right pb-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider pr-4 w-[15%]">%</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100 dark:divide-[var(--color-border-default)]">
                                    {expenseStructure.map((entry, idx) => {
                                        const totalExp = currentSummary?.expenses || 1; 
                                        const share = (entry.value / totalExp) * 100;
                                        
                                        return (
                                            <tr key={idx} className="group hover:bg-[var(--color-surface-soft)] transition-colors">
                                                <td className="py-3 pr-2 truncate max-w-0">
                                                    <div className="flex items-center gap-3">
                                                        <div className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${DOT_COLOR_CLASSES[idx % DOT_COLOR_CLASSES.length]}`}></div>
                                                        <span className="text-slate-700 dark:text-[var(--color-text-main)] truncate font-medium">{entry.name}</span>
                                                    </div>
                                                </td>
                                                <td className="py-3 px-2 text-right text-slate-600 dark:text-[var(--color-text-muted)] font-medium tabular-nums whitespace-nowrap">
                                                    {formatCurrency(entry.value)}
                                                </td>
                                                <td className="py-3 pl-2 pr-4 text-right text-slate-400 dark:text-[var(--color-text-muted)] tabular-nums">
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