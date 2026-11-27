import React, { useEffect, useState, useCallback, useRef } from 'react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
    PieChart, Pie, Cell
} from 'recharts';
import { TrendingUp, TrendingDown, Wallet, PiggyBank, ChevronLeft, ChevronRight, Clock, CalendarClock, Download, Loader2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import {
    getAnalyticsTrend,
    getAnalyticsInsights,
    exportFinancialReport,
    ensureMonth,
    getMonthSummary,
    getExpenses,
    getSavingsGoals,
    getCumulativeBalance,
    getBudgets,
    getPlannedRecords,
} from '../api';
import type { SavingsGoal, Expense, PlannedResponse, PlannedItem, AnalyticsInsightsResponse } from '../api';
import { formatCurrency, readSavedMonthDate } from '../utils';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import { useDataChanged } from '../hooks/useWebSocket';
import { useTheme } from '../context/ThemeContext';
import { useEntitlements } from '../context/AuthContext';
import PageState from '../components/PageState';
import StatusBanner from '../components/StatusBanner';
import Modal from '../components/Modal';
import { ProBurnFlameIcon, ProCrownIcon, ProExportSheetIcon, ProLockIcon } from '../components/ProIcons';

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

const getInitialDate = (): Date => readSavedMonthDate(DASHBOARD_MONTH_KEY);

const SHORT_MONTH_NAMES_RU = [
    'янв.', 'фев.', 'мар.', 'апр.', 'май', 'июн.',
    'июл.', 'авг.', 'сен.', 'окт.', 'ноя.', 'дек.',
];

const FULL_MONTH_NAMES_RU = [
    'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
    'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
];

const formatTrendMonthLabel = (rawMonth: string): string => {
    const parts = String(rawMonth).split('/');
    if (parts.length !== 2) return String(rawMonth);
    const month = Number(parts[0]);
    const year = Number(parts[1]);
    if (!Number.isInteger(month) || month < 1 || month > 12 || !Number.isInteger(year)) {
        return String(rawMonth);
    }
    return `${SHORT_MONTH_NAMES_RU[month - 1]} ${year}`;
};

const formatTrendMonthFullLabel = (rawMonth: string): string => {
    const parts = String(rawMonth).split('/');
    if (parts.length !== 2) return String(rawMonth);
    const month = Number(parts[0]);
    const year = Number(parts[1]);
    if (!Number.isInteger(month) || month < 1 || month > 12 || !Number.isInteger(year)) {
        return String(rawMonth);
    }
    return `${FULL_MONTH_NAMES_RU[month - 1]} ${year}`;
};

const Dashboard: React.FC = () => {
    const [currentDate, setCurrentDate] = useState(getInitialDate);
    const [trendData, setTrendData] = useState<TrendItem[]>([]);
    const [currentSummary, setCurrentSummary] = useState<SummaryData | null>(null);
    const [expenseStructure, setExpenseStructure] = useState<ExpenseStructureItem[]>([]);
    const [allExpenses, setAllExpenses] = useState<Expense[]>([]);
    const [totalSavings, setTotalSavings] = useState<number>(0);
    const [cumulativeBalance, setCumulativeBalance] = useState<number>(0);
    const [totalLimit, setTotalLimit] = useState<number>(0);
    const [planned, setPlanned] = useState<PlannedResponse | null>(null);
    const [planModal, setPlanModal] = useState<'expenses' | 'incomes' | null>(null);
    const [trendMonths, setTrendMonths] = useState<3 | 6 | 12>(6);
    const [insights, setInsights] = useState<AnalyticsInsightsResponse | null>(null);
    const [isExportModalOpen, setIsExportModalOpen] = useState(false);
    const [exportFormat, setExportFormat] = useState<'xlsx' | 'csv'>('xlsx');
    const [exportPeriod, setExportPeriod] = useState<'month' | 'quarter' | 'year' | 'all'>('month');
    const [isExporting, setIsExporting] = useState(false);
    const [exportError, setExportError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const { resolvedTheme } = useTheme();
    const { entitlements, openPaywall } = useEntitlements();
    const isDarkSurface = resolvedTheme !== 'light';
    const fetchRequestIdRef = useRef(0);
    const effectiveTrendMonths: 3 | 6 | 12 = !entitlements.features.extendedAnalytics && trendMonths > 6 ? 6 : trendMonths;

    const totalAllExpenses = allExpenses.reduce((sum, e) => sum + e.amount, 0);
    const totalVisibleExpense = allExpenses
        .filter(e => e.category_name !== 'Пополнение копилки')
        .reduce((sum, e) => sum + e.amount, 0);
    const remainingLimit = totalLimit - totalAllExpenses;
    const isOverTotalLimit = totalLimit > 0 && remainingLimit < 0;

    // План-слой: суммы без скипнутых приходят с сервера, списки — для попапа
    const plannedExpensesTotal = planned?.totals.plannedExpenses ?? 0;
    const plannedIncomesTotal = planned?.totals.plannedIncomes ?? 0;
    const hasPlannedExpenses = (planned?.expenses.length ?? 0) > 0;
    const hasPlannedIncomes = (planned?.incomes.length ?? 0) > 0;
    // Прогноз считаем от «видимых» цифр карточек: скрытые расходы копилки уменьшают
    // свободные средства (totalAllExpenses), но savings отдельно не вычитаем — иначе двойной счёт
    const forecastExpensesVisible = totalVisibleExpense + plannedExpensesTotal;
    const forecastFree = (currentSummary?.income || 0) + plannedIncomesTotal - (totalAllExpenses + plannedExpensesTotal);

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
        const requestId = ++fetchRequestIdRef.current;
        setIsLoading(true);
        setError(null);
        try {
            const year = currentDate.getFullYear();
            const month = currentDate.getMonth() + 1;

            // 1. Trend Data + Selected Month + Savings + Cumulative Balance + PRO Insights
            const [trendRes, mRes, savingsRes, cumulativeRes, insightsRes] = await Promise.all([
                getAnalyticsTrend(effectiveTrendMonths, year, month),
                ensureMonth(year, month),
                getSavingsGoals(),
                getCumulativeBalance(year, month),
                entitlements.features.extendedAnalytics
                    ? getAnalyticsInsights(year, month).catch(() => null)
                    : Promise.resolve(null),
            ]);
            if (requestId !== fetchRequestIdRef.current) return;

            const [summaryRes, expRes, budgetsRes, plannedRes] = await Promise.all([
                getMonthSummary(mRes.data.id),
                getExpenses(mRes.data.id),
                getBudgets(mRes.data.id),
                getPlannedRecords(mRes.data.id),
            ]);
            if (requestId !== fetchRequestIdRef.current) return;

            setTrendData(trendRes.data);
            setCurrentSummary(summaryRes.data);
            setPlanned(plannedRes.data);
            setInsights(insightsRes ? insightsRes.data : null);

            // Group expenses by category (exclude hidden savings expenses)
            const catMap: Record<string, number> = {};
            expRes.data.forEach((e) => {
                if (e.category_name === 'Пополнение копилки') return;
                if (e.category_name) {
                    if (!catMap[e.category_name]) catMap[e.category_name] = 0;
                    catMap[e.category_name] += e.amount;
                }
            });

            setAllExpenses(expRes.data);

            const pieData = Object.keys(catMap).map(name => ({
                name, value: catMap[name]
            })).sort((a, b) => b.value - a.value);

            setExpenseStructure(pieData);
            setTotalLimit(budgetsRes.data.reduce((sum, item) => sum + item.limit_amount, 0));

            const totalSavingsAmount = savingsRes.data.reduce((sum: number, goal: SavingsGoal) => sum + goal.current_amount, 0);
            setTotalSavings(totalSavingsAmount);
            setCumulativeBalance(cumulativeRes.data.cumulativeBalance);

        } catch (e) {
            if (requestId !== fetchRequestIdRef.current) return;
            console.error(e);
            setError('Не удалось загрузить обзор. Проверьте соединение и попробуйте ещё раз.');
        } finally {
            if (requestId === fetchRequestIdRef.current) {
                setIsLoading(false);
            }
        }
    }, [currentDate, effectiveTrendMonths, entitlements.features.extendedAnalytics]);

    const handleSelectTrendPeriod = (period: 3 | 6 | 12) => {
        if (period > 6 && !entitlements.features.extendedAnalytics) {
            openPaywall('extendedAnalytics');
            return;
        }
        setTrendMonths(period);
    };

    const handleOpenExport = () => {
        if (!entitlements.features.exportReports) {
            openPaywall('exportReports');
            return;
        }
        setExportError(null);
        setIsExportModalOpen(true);
    };

    const handleDownloadReport = async () => {
        setIsExporting(true);
        setExportError(null);
        try {
            const year = currentDate.getFullYear();
            const month = currentDate.getMonth() + 1;
            const response = await exportFinancialReport({
                format: exportFormat,
                period: exportPeriod,
                year,
                month,
            });
            const blobUrl = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = blobUrl;
            const ext = exportFormat === 'csv' ? 'csv' : 'xlsx';
            link.setAttribute('download', `finkeeper24-report-${year}-${String(month).padStart(2, '0')}-${exportPeriod}.${ext}`);
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(blobUrl);
            setIsExportModalOpen(false);
        } catch (err: unknown) {
            console.error('Export report error:', err);
            setExportError('Не удалось сформировать отчёт. Попробуйте ещё раз.');
        } finally {
            setIsExporting(false);
        }
    };

    useEffect(() => {
        void fetchData();
        
        // Listen for savings updates
        const handleSavingsUpdate = () => { void fetchData(); };
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
        <div className="mx-auto w-full min-w-0 max-w-5xl xl:max-w-[1440px] grid grid-rows-[auto_auto_auto_auto_1fr] gap-8 min-h-full">
            <header className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-[var(--color-text-main)] tracking-tight">Обзор финансов</h2>
                    <p className="text-slate-500 dark:text-[var(--color-text-muted)] text-sm mt-1">Сводка за выбранный месяц и аналитика</p>
                </div>
                <button
                    type="button"
                    onClick={handleOpenExport}
                    className="inline-flex items-center justify-center gap-2 self-start sm:self-auto rounded-2xl border border-emerald-500/30 bg-emerald-500/10 hover:bg-emerald-500/20 px-4 py-2.5 text-xs font-bold text-emerald-700 dark:text-emerald-300 transition-all shadow-sm"
                >
                    <ProExportSheetIcon className="w-4 h-4" />
                    <span>Экспорт Excel / CSV</span>
                    {!entitlements.features.exportReports && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/20 px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-wider text-emerald-700 dark:text-emerald-200">
                            <ProLockIcon className="w-3 h-3" />
                            PRO
                        </span>
                    )}
                </button>
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

            {/* Stats Cards: ряд 1 — операционные цифры месяца, ряд 2 — итоги */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3 gap-5">
                <div
                    onClick={hasPlannedIncomes ? () => setPlanModal('incomes') : undefined}
                    role={hasPlannedIncomes ? 'button' : undefined}
                    title={hasPlannedIncomes ? 'Показать ожидаемые поступления' : undefined}
                    className={`bg-[image:var(--color-stat-emerald-bg)] backdrop-blur-md p-4 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-emerald-border)] hover:shadow-[0_12px_40px_rgba(16,185,129,0.15)] transition-all ${hasPlannedIncomes ? 'cursor-pointer' : ''}`}
                >
                    <div className="flex gap-4 items-center">
                        <div className="p-3 bg-emerald-100/80 dark:bg-emerald-900/60 rounded-2xl text-emerald-600 dark:text-emerald-400 shadow-sm border border-emerald-200/50 dark:border-emerald-800/50">
                            <TrendingUp className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-sm font-medium text-emerald-800/80 dark:text-emerald-300/80">Доходы</p>
                            <p className="text-2xl font-bold text-emerald-950 dark:text-emerald-100 tracking-tight mt-0.5">{formatCurrency(currentSummary?.income || 0)}</p>
                            {plannedIncomesTotal > 0 && (
                                <p className="flex items-center gap-1.5 text-xs font-medium text-emerald-700/70 dark:text-emerald-300/60 mt-1">
                                    <Clock className="w-3.5 h-3.5" />
                                    ожидается: {formatCurrency(plannedIncomesTotal)}
                                </p>
                            )}
                        </div>
                    </div>
                </div>

                <div
                    onClick={hasPlannedExpenses ? () => setPlanModal('expenses') : undefined}
                    role={hasPlannedExpenses ? 'button' : undefined}
                    title={hasPlannedExpenses ? 'Показать запланированные платежи' : undefined}
                    className={`bg-[image:var(--color-stat-rose-bg)] backdrop-blur-md p-4 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-rose-border)] hover:shadow-[0_12px_40px_rgba(244,63,94,0.15)] transition-all ${hasPlannedExpenses ? 'cursor-pointer' : ''}`}
                >
                    <div className="flex gap-4 items-center">
                        <div className="p-3 bg-rose-100/80 dark:bg-rose-900/60 rounded-2xl text-rose-600 dark:text-rose-400 shadow-sm border border-rose-200/50 dark:border-rose-800/50">
                            <TrendingDown className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-sm font-medium text-rose-800/80 dark:text-rose-300/80">Расходы</p>
                            <p className="text-2xl font-bold text-rose-950 dark:text-rose-100 tracking-tight mt-0.5">{formatCurrency(totalVisibleExpense)}</p>
                            {plannedExpensesTotal > 0 && (
                                <p className="flex items-center gap-1.5 text-xs font-medium text-rose-700/70 dark:text-rose-300/60 mt-1">
                                    <Clock className="w-3.5 h-3.5" />
                                    ожидается: {formatCurrency(plannedExpensesTotal)}
                                </p>
                            )}
                        </div>
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-amber-bg)] backdrop-blur-md p-5 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-amber-border)] hover:shadow-[0_12px_40px_rgba(245,158,11,0.15)] transition-all relative overflow-hidden">
                    {isOverTotalLimit && (
                        <div className="absolute top-0 right-0 w-24 h-24 bg-rose-200 dark:bg-rose-900/50 rounded-bl-full blur-2xl opacity-40"></div>
                    )}
                    <div className="flex flex-col justify-center h-full relative z-10">
                        <p className="text-sm font-medium text-amber-800/80 dark:text-amber-300/80 mb-1">Лимит на расходы</p>
                        <p className={`text-2xl font-bold tracking-tight ${!isOverTotalLimit ? 'text-amber-950 dark:text-amber-100' : 'text-rose-600 dark:text-rose-400'}`}>
                            {formatCurrency(Math.max(0, remainingLimit))}
                        </p>
                        {isOverTotalLimit && (
                            <p className="text-xs font-medium text-rose-600 dark:text-rose-400 mt-1">
                                перерасход: {formatCurrency(Math.abs(remainingLimit))}
                            </p>
                        )}
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-blue-bg)] backdrop-blur-md p-4 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-blue-border)] hover:shadow-[0_12px_40px_rgba(59,130,246,0.15)] transition-all">
                    <div className="flex gap-4 items-center">
                        <div className="p-3 bg-blue-100/80 dark:bg-blue-900/60 rounded-2xl text-blue-600 dark:text-blue-400 shadow-sm border border-blue-200/50 dark:border-blue-800/50">
                            <CalendarClock className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-sm font-medium text-blue-800/80 dark:text-blue-300/80">Прогноз на месяц</p>
                            <p className={`text-2xl font-bold tracking-tight mt-0.5 ${forecastFree >= 0 ? 'text-indigo-950 dark:text-indigo-100' : 'text-rose-600 dark:text-rose-400'}`}>
                                {formatCurrency(forecastFree)}
                                <span className="text-xs font-medium text-blue-500/80 dark:text-blue-400/80 ml-2">свободно</span>
                            </p>
                            <p className="text-xs font-medium text-blue-700/70 dark:text-blue-300/60 mt-1">
                                расходы: {formatCurrency(forecastExpensesVisible)}
                            </p>
                        </div>
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-cyan-bg)] backdrop-blur-md p-4 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-cyan-border)] hover:shadow-[0_12px_40px_rgba(6,182,212,0.15)] transition-all">
                    <div className="flex gap-4 items-center">
                        <div className="p-3 bg-cyan-100/80 dark:bg-cyan-900/60 rounded-2xl text-cyan-600 dark:text-cyan-400 shadow-sm border border-cyan-200/50 dark:border-cyan-800/50">
                            <PiggyBank className="w-5 h-5" />
                        </div>
                        <div>
                            <p className="text-sm font-medium text-cyan-800/80 dark:text-cyan-300/80">Накопления</p>
                            <p className="text-2xl font-bold text-cyan-950 dark:text-cyan-100 tracking-tight mt-0.5">{formatCurrency(totalSavings)}</p>
                            <p className="text-xs font-medium text-cyan-700/70 dark:text-cyan-300/60 mt-1">
                                в копилку: {currentSummary?.income ? ((currentSummary.savings / currentSummary.income) * 100).toFixed(1) : '0.0'}% от дохода
                            </p>
                        </div>
                    </div>
                </div>

                <div className="bg-[image:var(--color-stat-orange-bg)] backdrop-blur-md p-5 rounded-3xl shadow-sm dark:shadow-none border border-[var(--color-stat-orange-border)] hover:shadow-[0_12px_40px_rgba(249,115,22,0.15)] transition-all">
                    <div className="flex flex-col justify-center h-full">
                        <p className="text-sm font-medium text-orange-800/80 dark:text-orange-300/80 mb-1">В наличии без накоплений</p>
                        <p className="text-2xl font-bold text-orange-950 dark:text-orange-100 tracking-tight">
                            {formatCurrency(cumulativeBalance)}
                        </p>
                    </div>
                </div>
            </div>

            {/* Charts Row */}
            <div className="grid grid-cols-1 xl:grid-cols-2 grid-rows-1 gap-4 min-h-0">

                {/* Trend Chart */}
                <div className="min-w-0 overflow-hidden bg-[var(--color-surface)]/90 backdrop-blur-md p-6 rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-sm dark:shadow-none border border-[var(--color-border-default)] transition-colors flex flex-col min-h-0">
                    <div className="flex flex-wrap items-center justify-between gap-3 mb-8 flex-shrink-0">
                        <h3 className="text-lg font-bold text-slate-800 dark:text-[var(--color-text-main)] tracking-tight">Динамика финансов</h3>
                        <div className="inline-flex items-center gap-1 rounded-2xl bg-slate-100 dark:bg-[var(--color-surface-soft)] p-1 border border-[var(--color-border-default)]">
                            {([3, 6, 12] as const).map((period) => {
                                const isActive = effectiveTrendMonths === period;
                                const isLocked = period > 6 && !entitlements.features.extendedAnalytics;
                                return (
                                    <button
                                        key={period}
                                        type="button"
                                        onClick={() => handleSelectTrendPeriod(period)}
                                        className={`inline-flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold transition-all ${
                                            isActive
                                                ? 'bg-[var(--color-primary)] text-white shadow-sm'
                                                : 'text-slate-600 dark:text-[var(--color-text-muted)] hover:text-slate-900 dark:hover:text-white'
                                        }`}
                                    >
                                        <span>{period} мес</span>
                                        {isLocked && <ProLockIcon className="w-3.5 h-3.5 text-emerald-600 dark:text-emerald-400" />}
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                    <div className="flex-1 min-h-0 w-full">
                        <ResponsiveContainer width="100%" height="100%" minWidth={0} minHeight={320}>
                            <BarChart data={trendData} barGap={4}>
                                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke={isDarkSurface ? '#252b41' : '#f8fafc'} />
                                <XAxis
                                    dataKey="month"
                                    tickFormatter={formatTrendMonthLabel}
                                    tick={{ fontSize: 12, fill: isDarkSurface ? '#949eb5' : '#94a3b8' }}
                                    axisLine={false}
                                    tickLine={false}
                                    dy={10}
                                />
                                <YAxis hide />
                                <Tooltip
                                    cursor={{ fill: isDarkSurface ? '#191d2d' : '#f8fafc' }}
                                    labelFormatter={(label) => formatTrendMonthFullLabel(String(label ?? ''))}
                                    formatter={(value: number | undefined) => formatCurrency(value ?? 0)}
                                    contentStyle={{
                                        borderRadius: '16px',
                                        border: isDarkSurface ? '1px solid #252b41' : '1px solid #f1f5f9',
                                        backgroundColor: isDarkSurface ? '#111820' : '#ffffff',
                                        color: isDarkSurface ? '#e8f5ec' : '#1e293b',
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
                <div className="min-w-0 overflow-hidden bg-[var(--color-surface)]/90 backdrop-blur-md p-6 rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-sm dark:shadow-none border border-[var(--color-border-default)] flex flex-col transition-colors min-h-0">
                    <h3 className="text-lg font-bold text-slate-800 dark:text-[var(--color-text-main)] tracking-tight mb-8 flex-shrink-0">Структура расходов</h3>
                    
                    <div className="flex flex-col xl:flex-row xl:items-center items-stretch gap-4 xl:gap-6 flex-1 min-h-0 overflow-hidden">
                        {/* Chart */}
                        <div className="relative h-64 w-full min-w-0 xl:w-64 xl:flex-shrink-0 self-center">
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
                                                border: isDarkSurface ? '1px solid #252b41' : '1px solid #f1f5f9',
                                                backgroundColor: isDarkSurface ? '#111820' : '#ffffff',
                                                color: isDarkSurface ? '#e8f5ec' : '#1e293b',
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
                                        {formatCurrency(totalVisibleExpense)}
                                    </span>
                                </div>
                            )}
                        </div>

                        {/* Detailed Table */}
                        <div className="flex-1 min-h-0 overflow-auto custom-scrollbar pr-2 min-w-0 self-start xl:self-stretch">
                            <table className="w-full text-sm relative border-separate border-spacing-0 table-fixed">
                                <thead>
                                    <tr>
                                        <th className="text-left pb-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider w-[60%]">Категория</th>
                                        <th className="text-right pb-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider w-[25%]">Сумма</th>
                                        <th className="text-right pb-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider pr-4 w-[15%]">%</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100 dark:divide-[var(--color-border-default)]">
                                    {expenseStructure.map((entry, idx) => {
                                        const totalExp = totalVisibleExpense || 1;
                                        const share = (entry.value / totalExp) * 100;
                                        
                                        return (
                                            <tr key={idx} className="group hover:bg-[var(--color-surface-soft)] transition-colors">
                                                <td className="py-2 pr-1 truncate max-w-0">
                                                    <div className="flex items-center gap-3">
                                                        <div className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${DOT_COLOR_CLASSES[idx % DOT_COLOR_CLASSES.length]}`}></div>
                                                        <span className="text-slate-700 dark:text-[var(--color-text-main)] truncate font-medium" title={entry.name}>{entry.name}</span>
                                                    </div>
                                                </td>
                                                <td className="py-2 px-1 text-right text-slate-600 dark:text-[var(--color-text-muted)] font-medium tabular-nums whitespace-nowrap">
                                                    {formatCurrency(entry.value)}
                                                </td>
                                                <td className="py-2 pl-1 pr-2 text-right text-slate-400 dark:text-[var(--color-text-muted)] tabular-nums">
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

            {/* Глубокая аналитика PRO: Burn Rate & Сравнение с прошлым месяцем */}
            {entitlements.features.extendedAnalytics && insights ? (
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    <div className="rounded-3xl border border-[var(--color-border-default)] bg-[var(--color-surface)]/90 backdrop-blur-md p-6 shadow-sm transition-colors">
                        <div className="flex items-start justify-between gap-3">
                            <div className="flex items-center gap-3">
                                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-amber-500/25 bg-amber-500/10">
                                    <ProBurnFlameIcon className="h-6 w-6" />
                                </div>
                                <div>
                                    <div className="flex items-center gap-2">
                                        <h3 className="text-base font-bold text-slate-800 dark:text-[var(--color-text-main)]">
                                            Скорость трат
                                        </h3>
                                        <span className="rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-emerald-700 dark:text-emerald-300">
                                            PRO
                                        </span>
                                    </div>
                                    <p className="text-xs text-slate-500 dark:text-[var(--color-text-muted)] mt-0.5">
                                        Учтено дней: {insights.burnRate.elapsedDays} из {insights.burnRate.daysInMonth}
                                    </p>
                                </div>
                            </div>
                            {insights.comparison.expenseDeltaPercent !== null && (
                                <span
                                    className={`rounded-xl px-2.5 py-1 text-xs font-bold tabular-nums ${
                                        insights.comparison.expenseDeltaPercent <= 0
                                            ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
                                            : 'bg-amber-500/15 text-amber-700 dark:text-amber-300'
                                    }`}
                                >
                                    {insights.comparison.expenseDeltaPercent > 0 ? '+' : ''}
                                    {insights.comparison.expenseDeltaPercent}% к прошл. мес.
                                </span>
                            )}
                        </div>

                        <div className="mt-5 grid grid-cols-1 sm:grid-cols-2 gap-3">
                            <div className="rounded-2xl border border-[var(--color-border-default)] bg-[var(--color-surface-soft)]/70 p-4">
                                <p className="text-xs font-medium text-slate-500 dark:text-[var(--color-text-muted)]">Средний расход в день</p>
                                <p className="mt-1 text-2xl font-extrabold tabular-nums text-slate-900 dark:text-[var(--color-text-main)]">
                                    {formatCurrency(insights.burnRate.dailyBurnRate)}
                                    <span className="text-xs font-semibold text-slate-400 ml-1">/ день</span>
                                </p>
                            </div>
                            <div className="rounded-2xl border border-[var(--color-border-default)] bg-[var(--color-surface-soft)]/70 p-4">
                                <p className="text-xs font-medium text-slate-500 dark:text-[var(--color-text-muted)]">Оценка к концу месяца</p>
                                <p className="mt-1 text-2xl font-extrabold tabular-nums text-slate-900 dark:text-[var(--color-text-main)]">
                                    {formatCurrency(insights.burnRate.projectedWithPlannedExpenses)}
                                </p>
                                <p className="mt-0.5 text-[11px] text-slate-400 dark:text-[var(--color-text-muted)]">
                                    прошлый месяц: {formatCurrency(insights.comparison.previousMonthExpenses)}
                                </p>
                            </div>
                        </div>
                    </div>

                    <div className="rounded-3xl border border-[var(--color-border-default)] bg-[var(--color-surface)]/90 backdrop-blur-md p-6 shadow-sm transition-colors">
                        <div className="flex items-center justify-between gap-3 mb-4">
                            <div>
                                <h3 className="text-base font-bold text-slate-800 dark:text-[var(--color-text-main)]">
                                    Сравнение категорий с прошлым месяцем
                                </h3>
                                <p className="text-xs text-slate-500 dark:text-[var(--color-text-muted)] mt-0.5">
                                    Динамика топ-статей расходов
                                </p>
                            </div>
                            <span className="rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-emerald-700 dark:text-emerald-300">
                                PRO
                            </span>
                        </div>

                        {insights.comparison.categories.length > 0 ? (
                            <div className="space-y-2.5">
                                {insights.comparison.categories.slice(0, 4).map((cat) => (
                                    <div
                                        key={cat.categoryId}
                                        className="flex items-center justify-between gap-3 rounded-2xl border border-[var(--color-border-default)] bg-[var(--color-surface-soft)]/60 px-3.5 py-2.5 text-xs"
                                    >
                                        <span className="font-semibold text-slate-800 dark:text-[var(--color-text-main)] truncate">
                                            {cat.categoryName}
                                        </span>
                                        <div className="flex items-center gap-2 shrink-0 tabular-nums">
                                            <span className="text-slate-400">
                                                {formatCurrency(cat.previousAmount)} →
                                            </span>
                                            <span className="font-bold text-slate-900 dark:text-[var(--color-text-main)]">
                                                {formatCurrency(cat.currentAmount)}
                                            </span>
                                            {cat.deltaPercent !== null && (
                                                <span
                                                    className={`rounded-lg px-1.5 py-0.5 text-[11px] font-bold ${
                                                        cat.deltaPercent <= 0
                                                            ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
                                                            : 'bg-rose-500/15 text-rose-600 dark:text-rose-300'
                                                    }`}
                                                >
                                                    {cat.deltaPercent > 0 ? '+' : ''}
                                                    {cat.deltaPercent}%
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <p className="text-xs text-slate-400 dark:text-[var(--color-text-muted)] py-6 text-center">
                                Недостаточно данных за текущий и предыдущий месяцы для сравнения категорий.
                            </p>
                        )}
                    </div>
                </div>
            ) : (
                <div className="rounded-3xl border border-emerald-500/25 bg-[var(--color-surface)]/90 backdrop-blur-md p-5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                    <div className="flex items-center gap-3.5">
                        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-emerald-500/30 bg-emerald-500/10">
                            <ProBurnFlameIcon className="h-6 w-6" />
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <h3 className="text-sm font-bold text-slate-800 dark:text-[var(--color-text-main)]">
                                    Глубокая аналитика и скорость трат
                                </h3>
                                <span className="rounded-full border border-emerald-500/30 bg-emerald-500/15 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-emerald-700 dark:text-emerald-300">
                                    Семейный PRO
                                </span>
                            </div>
                            <p className="text-xs text-slate-500 dark:text-[var(--color-text-muted)] mt-0.5">
                                Скорость трат в день, сравнение категорий с прошлым месяцем, динамика за 12 месяцев и выгрузка в Excel.
                            </p>
                        </div>
                    </div>
                    <button
                        type="button"
                        onClick={() => openPaywall('extendedAnalytics')}
                        className="inline-flex items-center justify-center gap-2 shrink-0 rounded-2xl bg-[var(--color-primary)] px-4 py-2.5 text-xs font-bold text-white shadow-sm hover:opacity-90 transition-opacity"
                    >
                        <ProCrownIcon className="w-4 h-4" />
                        <span>{entitlements.canStartTrial ? 'Попробовать 14 дней бесплатно' : 'Открыть PRO'}</span>
                    </button>
                </div>
            )}

            {/* Модалка экспорта отчёта Excel / CSV */}
            <Modal
                isOpen={isExportModalOpen}
                onClose={() => !isExporting && setIsExportModalOpen(false)}
                title="Экспорт финансового отчёта"
            >
                <div className="space-y-4">
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-[var(--color-text-muted)] mb-2">
                            Формат файла
                        </p>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                            <button
                                type="button"
                                onClick={() => setExportFormat('xlsx')}
                                className={`rounded-2xl border p-3.5 text-left transition-all ${
                                    exportFormat === 'xlsx'
                                        ? 'border-emerald-500 bg-emerald-500/10'
                                        : 'border-[var(--color-border-default)] bg-[var(--color-surface-soft)]'
                                }`}
                            >
                                <div className="flex items-center gap-2 font-bold text-sm text-slate-900 dark:text-[var(--color-text-main)]">
                                    <ProExportSheetIcon className="w-4 h-4 text-emerald-500" />
                                    <span>Excel (.xlsx)</span>
                                </div>
                                <p className="mt-1 text-xs text-slate-500 dark:text-[var(--color-text-muted)]">
                                    4 вкладки: Сводка, Расходы, Доходы, Копилки
                                </p>
                            </button>
                            <button
                                type="button"
                                onClick={() => setExportFormat('csv')}
                                className={`rounded-2xl border p-3.5 text-left transition-all ${
                                    exportFormat === 'csv'
                                        ? 'border-emerald-500 bg-emerald-500/10'
                                        : 'border-[var(--color-border-default)] bg-[var(--color-surface-soft)]'
                                }`}
                            >
                                <div className="flex items-center gap-2 font-bold text-sm text-slate-900 dark:text-[var(--color-text-main)]">
                                    <Download className="w-4 h-4 text-emerald-500" />
                                    <span>CSV (.csv)</span>
                                </div>
                                <p className="mt-1 text-xs text-slate-500 dark:text-[var(--color-text-muted)]">
                                    Универсальная таблица в кодировке UTF-8 BOM
                                </p>
                            </button>
                        </div>
                    </div>

                    <div>
                        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500 dark:text-[var(--color-text-muted)] mb-2">
                            Период выгрузки (относительно {format(currentDate, 'LLLL yyyy', { locale: ru })})
                        </p>
                        <div className="grid grid-cols-2 gap-2">
                            {([
                                { id: 'month', label: '1 месяц' },
                                { id: 'quarter', label: 'Квартал (3 мес)' },
                                { id: 'year', label: 'Год (12 мес)' },
                                { id: 'all', label: 'За всё время' },
                            ] as const).map((item) => (
                                <button
                                    key={item.id}
                                    type="button"
                                    onClick={() => setExportPeriod(item.id)}
                                    className={`rounded-xl border px-3 py-2.5 text-xs font-bold transition-all ${
                                        exportPeriod === item.id
                                            ? 'border-emerald-500 bg-emerald-500/15 text-emerald-800 dark:text-emerald-200'
                                            : 'border-[var(--color-border-default)] bg-[var(--color-surface-soft)] text-slate-700 dark:text-[var(--color-text-muted)]'
                                    }`}
                                >
                                    {item.label}
                                </button>
                            ))}
                        </div>
                    </div>

                    {exportError && (
                        <StatusBanner variant="error">{exportError}</StatusBanner>
                    )}

                    <div className="flex gap-3 pt-2">
                        <button
                            type="button"
                            onClick={handleDownloadReport}
                            disabled={isExporting}
                            className="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-[var(--color-primary)] py-3 text-sm font-bold text-white shadow-sm hover:opacity-90 disabled:opacity-50"
                        >
                            {isExporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
                            <span>{isExporting ? 'Формируем файл...' : `Скачать .${exportFormat}`}</span>
                        </button>
                        <button
                            type="button"
                            onClick={() => setIsExportModalOpen(false)}
                            disabled={isExporting}
                            className="rounded-xl bg-slate-100 dark:bg-[var(--color-surface-soft)] px-5 py-3 text-sm font-semibold text-slate-700 dark:text-[var(--color-text-muted)]"
                        >
                            Отмена
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Read-only попап списка плановых платежей/поступлений (управление — на экране Месяц) */}
            <Modal
                isOpen={planModal !== null}
                onClose={() => setPlanModal(null)}
                title={planModal === 'incomes' ? 'Ожидаемые поступления' : 'Запланированные платежи'}
            >
                <div className="space-y-1">
                    {(planModal === 'incomes' ? planned?.incomes : planned?.expenses)?.map((item: PlannedItem) => (
                        <div
                            key={`${item.template_type}-${item.template_id}`}
                            className="flex items-center justify-between gap-3 py-2.5 px-3 rounded-xl hover:bg-[var(--color-surface-soft)] transition-colors"
                        >
                            <div className="flex items-center gap-3 min-w-0">
                                <Clock className={`w-4 h-4 flex-shrink-0 ${item.is_overdue ? 'text-amber-500' : 'text-slate-400 dark:text-slate-500'}`} />
                                <div className="min-w-0">
                                    <p className={`font-medium truncate text-slate-700 dark:text-[var(--color-text-main)] ${item.is_skipped ? 'line-through opacity-50' : ''}`}>
                                        {item.name}
                                    </p>
                                    <p className={`text-xs ${item.is_overdue ? 'text-amber-600 dark:text-amber-400 font-medium' : 'text-slate-400 dark:text-[var(--color-text-muted)]'}`}>
                                        {item.is_skipped
                                            ? 'пропущен в этом месяце'
                                            : item.is_overdue
                                                ? `ждёт подтверждения (${item.due_day}-е)`
                                                : `${planModal === 'incomes' ? 'ожидается' : 'спишется'} ${item.due_day}-го`}
                                    </p>
                                </div>
                            </div>
                            <span className={`font-semibold tabular-nums whitespace-nowrap text-slate-800 dark:text-[var(--color-text-main)] ${item.is_skipped ? 'line-through opacity-50' : ''}`}>
                                {formatCurrency(item.amount)}
                            </span>
                        </div>
                    ))}
                    <div className="flex items-center justify-between pt-3 mt-2 border-t border-[var(--color-border-default)]">
                        <span className="text-sm text-slate-500 dark:text-[var(--color-text-muted)]">
                            Итого: <span className="font-semibold text-slate-800 dark:text-[var(--color-text-main)]">
                                {formatCurrency(planModal === 'incomes' ? plannedIncomesTotal : plannedExpensesTotal)}
                            </span>
                        </span>
                        <Link
                            to="/month"
                            onClick={() => setPlanModal(null)}
                            className="text-sm font-medium text-[var(--color-primary)] hover:underline"
                        >
                            Управлять — на экране Месяц →
                        </Link>
                    </div>
                </div>
            </Modal>
        </div>
    );
};

export default Dashboard;
