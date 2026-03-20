import React, { useEffect, useState, useRef, useCallback } from 'react';
import { ChevronLeft, ChevronRight, Plus, Trash2, ChevronDown, Pencil, GripVertical } from 'lucide-react';
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import type { DragEndEvent } from '@dnd-kit/core';
import { arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import {
    ensureMonth, getIncomes, getExpenses, getCategories, getBudgets, setBudget,
    addIncome, addExpense, deleteIncome, deleteExpense, updateExpense, updateIncome, reorderCategories, getIncomeSources, addIncomeSource
} from '../api';
import type { Month, Income, Expense, Category, Budget, IncomeSource } from '../api';
import { formatCurrency, formatDate } from '../utils';
import Modal from '../components/Modal';
import { useDataChanged } from '../hooks/useWebSocket';
import PageState from '../components/PageState';
import StatusBanner from '../components/StatusBanner';

interface GroupedExpense {
    id: number;
    name: string;
    items: Expense[];
    total: number;
    limit: number;
    isOverLimit: boolean;
}

interface SortableGroupProps {
    group: GroupedExpense;
    isExpanded: boolean;
    toggleCategory: (id: number) => void;
    itemsContent: React.ReactNode;
    contextAddButton?: React.ReactNode;
}

// Sortable Group Component
const SortableGroup: React.FC<SortableGroupProps> = ({ group, isExpanded, toggleCategory, itemsContent, contextAddButton }) => {
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        transition,
    } = useSortable({ id: group.id });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
    };

    const isMulti = group.items.length > 1;

    return (
        <div ref={setNodeRef} style={style} className="bg-[var(--color-surface)] dark:bg-[var(--color-surface)] group/dnd transition-colors">
            {/* Group Header */}
            <div
                className={`flex items-center justify-between p-4 bg-slate-50/40 dark:bg-[var(--color-surface-soft)]/40 hover:bg-slate-50/80 dark:hover:bg-[#1a222d]/80 transition-colors ${isMulti ? 'cursor-pointer' : ''}`}
                onClick={() => isMulti && toggleCategory(group.id)}
            >
                <div className="flex items-center gap-3">
                    {isMulti && (
                        <div className={`p-1.5 rounded-xl bg-[var(--color-surface)] dark:bg-[var(--color-surface)] shadow-sm border border-slate-100 dark:border-[var(--color-border-default)] transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}>
                            <ChevronDown className="w-4 h-4 text-slate-500 dark:text-[var(--color-text-muted)]" />
                        </div>
                    )}
                    <div className="flex flex-col">
                        <span className="font-bold text-slate-800 dark:text-[var(--color-text-main)] tracking-tight">{group.name}</span>
                        {group.isOverLimit && (
                            <span className="text-xs text-rose-600 dark:text-rose-400 font-medium">
                                Превышен лимит! ({formatCurrency(group.limit)})
                            </span>
                        )}
                    </div>
                </div>
                <div className="flex items-center gap-4">
                    <span className="text-xs text-slate-400 dark:text-slate-500 bg-[var(--color-surface)] dark:bg-[var(--color-surface)] px-2.5 py-1 rounded-lg border border-slate-200 dark:border-[var(--color-border-default)] font-medium transition-colors">
                        {group.items.length} {group.items.length === 1 ? 'запись' : 'записей'}
                    </span>
                    <span className={`font-bold tabular-nums tracking-tight ${group.isOverLimit ? 'text-rose-600 dark:text-rose-400' : 'text-slate-800 dark:text-[var(--color-text-main)]'}`}>{formatCurrency(group.total)}</span>
                    <div {...attributes} {...listeners} className="cursor-grab text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-[#e8f5ec] p-1.5 rounded-lg hover:bg-[var(--color-surface)] dark:hover:bg-[#111820] border border-transparent hover:border-slate-200 dark:hover:border-[#2a3441] hover:shadow-sm transition-colors" onClick={e => e.stopPropagation()}>
                        <GripVertical className="w-5 h-5" />
                    </div>
                </div>
            </div>

            {/* Items List */}
            <div className={`transition-opacity duration-300 ease-in-out ${isMulti && !isExpanded ? 'max-h-0 opacity-0 overflow-hidden' : 'max-h-none opacity-100 overflow-visible'}`}>
                <table className="w-full text-left">
                    <tbody className="divide-y divide-slate-100/60 dark:divide-[var(--color-border-default)]/60">
                        {itemsContent}
                    </tbody>
                </table>
                {contextAddButton}
            </div>
        </div>
    );
};

const MONTHVIEW_MONTH_KEY = 'monthview_selected_month';

const getInitialMonthViewDate = (): Date => {
    const saved = localStorage.getItem(MONTHVIEW_MONTH_KEY);
    if (saved) {
        const parsed = JSON.parse(saved);
        return new Date(parsed.year, parsed.month - 1, 1);
    }
    return new Date();
};

const saveMonthViewMonth = (date: Date) => {
    localStorage.setItem(MONTHVIEW_MONTH_KEY, JSON.stringify({
        year: date.getFullYear(),
        month: date.getMonth() + 1
    }));
};

const MonthView: React.FC = () => {
    const [currentDate, setCurrentDate] = useState(getInitialMonthViewDate);
    const [monthData, setMonthData] = useState<Month | null>(null);
    const [incomes, setIncomes] = useState<Income[]>([]);
    const [expenses, setExpenses] = useState<Expense[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [incomeSources, setIncomeSources] = useState<IncomeSource[]>([]);
    const [budgets, setBudgets] = useState<Budget[]>([]);

    const [activeTab, setActiveTab] = useState<'income' | 'expense'>('expense');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isBudgetModalOpen, setIsBudgetModalOpen] = useState(false);

    // Form State
    const [formData, setFormData] = useState({
        source: '',
        amount: '',
        categoryId: '',
        comment: ''
    });

    // Editing State
    const [editingId, setEditingId] = useState<number | null>(null);
    const [editingAmount, setEditingAmount] = useState('');
    const [pendingSave, setPendingSave] = useState<{ id: number, type: 'income' | 'expense' } | null>(null);
    const [pendingDelete, setPendingDelete] = useState<{ id: number, type: 'income' | 'expense' } | null>(null);
    const isSavingRef = useRef(false);

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        })
    );

    // State for expanded groups
    const [expandedCategories, setExpandedCategories] = useState<Record<number, boolean>>({});

    const [showNewSourceConfirm, setShowNewSourceConfirm] = useState(false);
    const [newSourceName, setNewSourceName] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [pageError, setPageError] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    const getActionErrorMessage = (errorValue: unknown, fallback: string) => {
        const apiError = errorValue as { response?: { data?: { error?: string } }; message?: string };
        return apiError.response?.data?.error || apiError.message || fallback;
    };

    // Toggle expansion
    const toggleCategory = (catId: number) => {
        setExpandedCategories(prev => ({ ...prev, [catId]: !prev[catId] }));
    };

    // Helper to open modal with pre-filled category
    const openAddModal = (type: 'income' | 'expense', categoryId?: number) => {
        setActiveTab(type);
        setFormData({
            source: '',
            amount: '',
            categoryId: categoryId ? categoryId.toString() : '',
            comment: ''
        });
        setIsModalOpen(true);
    };

    const loadData = useCallback(async () => {
        setIsLoading(true);
        setPageError(null);
        try {
            const year = currentDate.getFullYear();
            const month = currentDate.getMonth() + 1;

            const mRes = await ensureMonth(year, month);
            setMonthData(mRes.data);

            const [incRes, expRes, catRes, budRes, srcRes] = await Promise.all([
                getIncomes(mRes.data.id),
                getExpenses(mRes.data.id),
                getCategories(),
                getBudgets(mRes.data.id),
                getIncomeSources()
            ]);

            setIncomes(incRes.data);
            // Filter out hidden expenses (savings deposits) - category "Пополнение копилки"
            const visibleExpenses = expRes.data.filter((e: Expense) => e.category_name !== 'Пополнение копилки');
            setExpenses(visibleExpenses);
            setCategories(catRes.data);
            setBudgets(budRes.data);
            setIncomeSources(srcRes.data);
            
        } catch (e) {
            console.error("Error loading data", e);
            setPageError('Не удалось загрузить данные месяца. Попробуйте ещё раз.');
        } finally {
            setIsLoading(false);
        }
    }, [currentDate]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Refresh on WebSocket data changes
    useDataChanged(['income', 'expense', 'category', 'budget', 'income_source', 'all'], () => { loadData(); });

    const handlePrevMonth = () => {
        const newDate = new Date(currentDate.getFullYear(), currentDate.getMonth() - 1, 1);
        saveMonthViewMonth(newDate);
        setCurrentDate(newDate);
    };

    const handleNextMonth = () => {
        const newDate = new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 1);
        saveMonthViewMonth(newDate);
        setCurrentDate(newDate);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!monthData) return;
        setActionError(null);

        try {
            if (activeTab === 'income') {
                const sourceName = formData.source.trim();
                
                // Check if source is new (not found in incomeSources)
                const existingSource = incomeSources.find(s => s.name.toLowerCase() === sourceName.toLowerCase());
                
                if (!existingSource && sourceName) {
                    // New source - show confirmation
                    setNewSourceName(sourceName);
                    setShowNewSourceConfirm(true);
                    return;
                }
                
                await addIncome({
                    month_id: monthData.id,
                    source: sourceName || existingSource?.name || '',
                    amount: parseFloat(formData.amount),
                    date: new Date().toISOString()
                });
            } else {
                await addExpense({
                    month_id: monthData.id,
                    category_id: parseInt(formData.categoryId),
                    amount: parseFloat(formData.amount),
                    date: new Date().toISOString(),
                    comment: formData.comment
                });
                
                // Auto-expand the category we just added to
                if (formData.categoryId) {
                     setExpandedCategories(prev => ({ ...prev, [parseInt(formData.categoryId)]: true }));
                }
            }
            setIsModalOpen(false);
            setFormData({ source: '', amount: '', categoryId: '', comment: '' });
            loadData();
        } catch (err) {
            console.error(err);
            setActionError(getActionErrorMessage(err, activeTab === 'income'
                ? 'Не удалось сохранить доход. Попробуйте ещё раз.'
                : 'Не удалось сохранить расход. Попробуйте ещё раз.'));
        }
    };

    const handleEditClick = (item: Income | Expense) => {
        setEditingId(item.id);
        setEditingAmount(item.amount.toString());
        isSavingRef.current = false;
    };

    const saveAmount = async (id: number, type: 'income' | 'expense') => {
        const amount = parseFloat(editingAmount);
        
        if (isNaN(amount)) {
            setEditingId(null);
            return;
        }

        try {
            setActionError(null);
            if (type === 'income') {
                await updateIncome(id, { amount });
            } else {
                await updateExpense(id, { amount });
            }
            await loadData();
        } catch (e) { 
            console.error(e); 
            setActionError(getActionErrorMessage(e, type === 'income'
                ? 'Не удалось обновить сумму дохода. Попробуйте ещё раз.'
                : 'Не удалось обновить сумму расхода. Попробуйте ещё раз.'));
        } finally {
            setEditingId(null);
            isSavingRef.current = false;
        }
    };

    const handleBlur = (id: number, type: 'income' | 'expense') => {
        if (isSavingRef.current) {
            return;
        }

        // Instead of native confirm, we show our custom confirmation
        setPendingSave({ id, type });
    };

    const handleKeyDown = (e: React.KeyboardEvent, id: number, type: 'income' | 'expense') => {
        if (e.key === 'Enter') {
            isSavingRef.current = true;
            saveAmount(id, type);
        } else if (e.key === 'Escape') {
            isSavingRef.current = true; // Prevent blur confirm
            setEditingId(null);
            // Reset isSaving after a short delay so subsequent blurs are handled
            setTimeout(() => { isSavingRef.current = false; }, 100);
        }
    };

    const handleDelete = async (id: number, type: 'income' | 'expense') => {
        setPendingDelete({ id, type });
    };

    const confirmDelete = async () => {
        if (!pendingDelete) return;
        try {
            setActionError(null);
            if (pendingDelete.type === 'income') await deleteIncome(pendingDelete.id);
            else await deleteExpense(pendingDelete.id);
            setPendingDelete(null);
            loadData();
        } catch (e) {
            console.error(e);
            setActionError(getActionErrorMessage(e, pendingDelete.type === 'income'
                ? 'Не удалось удалить доход. Попробуйте ещё раз.'
                : 'Не удалось удалить расход. Попробуйте ещё раз.'));
        }
    };

    const confirmAddNewSource = async (addToList: boolean) => {
        if (!monthData) return;
        
        try {
            setActionError(null);
            if (addToList) {
                await addIncomeSource({ name: newSourceName });
            }
            
            await addIncome({
                month_id: monthData.id,
                source: newSourceName,
                amount: parseFloat(formData.amount),
                date: new Date().toISOString()
            });
            
            setShowNewSourceConfirm(false);
            setNewSourceName('');
            setIsModalOpen(false);
            setFormData({ source: '', amount: '', categoryId: '', comment: '' });
            loadData();
        } catch (err) {
            console.error(err);
            setActionError(getActionErrorMessage(err, addToList
                ? 'Не удалось добавить новый источник и сохранить доход. Попробуйте ещё раз.'
                : 'Не удалось сохранить доход с новым источником. Попробуйте ещё раз.'));
        }
    };

    const handleDragEnd = async (event: DragEndEvent) => {
        const { active, over } = event;

        if (over && active.id !== over.id) {
            const oldIndex = categories.findIndex((c) => c.id === active.id);
            const newIndex = categories.findIndex((c) => c.id === over.id);

            const newCategories = arrayMove(categories, oldIndex, newIndex);
            setCategories(newCategories);

            try {
                const newOrderIds = newCategories.map(c => c.id);
                await reorderCategories(newOrderIds);
            } catch (e) {
                console.error("Failed to save order", e);
                setActionError(getActionErrorMessage(e, 'Не удалось сохранить новый порядок категорий. Попробуйте ещё раз.'));
            }
        }
    };

    const handleBudgetChange = async (categoryId: number, limit: number) => {
        if (!monthData) return;

        try {
            setActionError(null);
            await setBudget({ month_id: monthData.id, category_id: categoryId, limit_amount: limit });
            setBudgets(prev => {
                const existing = prev.find(b => b.category_id === categoryId);
                if (existing) {
                    return prev.map(b => b.category_id === categoryId ? { ...b, limit_amount: limit } : b);
                }
                return [...prev, { id: 0, month_id: monthData.id, category_id: categoryId, limit_amount: limit }];
            });
        } catch (e) {
            console.error(e);
            setActionError(getActionErrorMessage(e, 'Не удалось обновить лимит категории. Попробуйте ещё раз.'));
        }
    };

    const totalIncome = incomes.reduce((sum, item) => sum + item.amount, 0);
    const totalExpense = expenses.reduce((sum, item) => sum + item.amount, 0);
    const totalLimit = budgets.reduce((sum, item) => sum + item.limit_amount, 0);

    // Grouping Expenses with Budget Info
    const groupedExpenses = expenses.reduce((acc, item) => {
        const catId = item.category_id;
        if (!acc[catId]) {
            acc[catId] = {
                id: catId,
                name: item.category_name || 'Без категории',
                items: [],
                total: 0
            };
        }
        acc[catId].items.push(item);
        acc[catId].total += item.amount;
        return acc;
    }, {} as Record<number, { id: number, name: string, items: Expense[], total: number }>);

    // Add budget info to groups
    const groupsWithBudget = Object.values(groupedExpenses).map(group => {
        const budget = budgets.find(b => b.category_id === group.id);
        const limit = budget ? budget.limit_amount : 0;
        return {
            ...group,
            limit,
            isOverLimit: limit > 0 && group.total > limit
        };
    });

    // Sort groups according to category order (which is preserved in 'categories' state)
    const sortedGroups = categories
        .filter(cat => groupedExpenses[cat.id]) // Only categories with expenses
        .map(cat => groupsWithBudget.find(g => g.id === cat.id)!)
        .filter(Boolean);
    
    // Add any groups that might be missing from categories (safe fallback)
    groupsWithBudget.forEach(group => {
        if (!categories.find(c => c.id === group.id)) {
            sortedGroups.push(group);
        }
    });

    if (isLoading && !monthData) {
        return (
            <PageState
                variant="loading"
                title="Загружаем месяц"
                description="Подготавливаем доходы, расходы, категории и лимиты выбранного месяца."
            />
        );
    }

    if (pageError && !monthData) {
        return (
            <PageState
                variant="error"
                title="Не удалось открыть месяц"
                description={pageError}
                actionLabel="Повторить"
                onAction={() => {
                    void loadData();
                }}
            />
        );
    }

    return (
        <div className="space-y-6 max-w-5xl mx-auto">
            {actionError ? (
                <StatusBanner variant="error" title="Операция не завершена">
                    {actionError}
                </StatusBanner>
            ) : null}

            {pageError && (
                <StatusBanner variant="error" title="Данные месяца недоступны">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <span>{pageError}</span>
                        <button
                            onClick={() => {
                                void loadData();
                            }}
                            className="rounded-xl bg-rose-600 px-4 py-2 text-white transition-colors hover:bg-rose-700"
                        >
                            Повторить
                        </button>
                    </div>
                </StatusBanner>
            )}

            {/* Header / Month Selector */}
            <div className="flex items-center justify-between bg-[var(--color-surface)]/90 dark:bg-[var(--color-surface)]/90 backdrop-blur-md p-4 rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgba(0,0,0,0.2)] border border-[var(--color-border-default)] dark:border-[var(--color-border-default)] transition-colors">
                <button onClick={handlePrevMonth} aria-label="Предыдущий месяц" title="Предыдущий месяц" className="p-2.5 hover:bg-[var(--color-surface-soft)] dark:hover:bg-[#1a222d] rounded-2xl transition-colors">
                    <ChevronLeft className="w-6 h-6 text-slate-600 dark:text-[var(--color-text-muted)]" />
                </button>
                <h2 className="text-xl font-bold text-slate-800 dark:text-[var(--color-text-main)] capitalize tracking-tight">
                    {format(currentDate, 'LLLL yyyy', { locale: ru })}
                </h2>
                <button onClick={handleNextMonth} aria-label="Следующий месяц" title="Следующий месяц" className="p-2.5 hover:bg-[var(--color-surface-soft)] dark:hover:bg-[#1a222d] rounded-2xl transition-colors">
                    <ChevronRight className="w-6 h-6 text-slate-600 dark:text-[var(--color-text-muted)]" />
                </button>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                <div className="bg-gradient-to-br from-emerald-50/90 to-emerald-100/50 dark:from-emerald-950/40 dark:to-emerald-900/40 backdrop-blur-md p-5 rounded-3xl shadow-[0_8px_30px_rgba(16,185,129,0.06)] border border-emerald-200/50 dark:border-emerald-800/50 transition-colors">
                    <p className="text-sm text-emerald-600 dark:text-emerald-400 font-medium mb-1">Доходы</p>
                    <p className="text-3xl font-bold text-emerald-700 dark:text-emerald-300 tracking-tight">{formatCurrency(totalIncome)}</p>
                </div>
                <div className="bg-gradient-to-br from-rose-50/90 to-rose-100/50 dark:from-rose-950/40 dark:to-rose-900/40 backdrop-blur-md p-5 rounded-3xl shadow-[0_8px_30px_rgba(244,63,94,0.06)] border border-rose-200/50 dark:border-rose-800/50 transition-colors">
                    <p className="text-sm text-rose-600 dark:text-rose-400 font-medium mb-1">Расходы</p>
                    <p className="text-3xl font-bold text-rose-700 dark:text-rose-300 tracking-tight">{formatCurrency(totalExpense)}</p>
                </div>
                <div className="bg-gradient-to-br from-blue-50/90 to-indigo-50/50 dark:from-blue-950/40 dark:to-indigo-950/40 backdrop-blur-md p-5 rounded-3xl shadow-[0_8px_30px_rgba(59,130,246,0.06)] border border-blue-200/50 dark:border-blue-800/50 cursor-pointer hover:shadow-[0_8px_30px_rgba(59,130,246,0.12)] transition-all" onClick={() => setIsBudgetModalOpen(true)}>
                    <div className="flex justify-between items-center mb-1">
                        <p className="text-sm text-blue-600 dark:text-blue-400 font-medium">Лимит трат на месяц</p>
                        <span className="text-xs text-blue-500 dark:text-blue-300 font-bold bg-blue-100/80 dark:bg-blue-900/60 px-2 py-0.5 rounded-lg border border-blue-200/50 dark:border-blue-700/50 transition-colors">
                            {Math.min((totalExpense / (totalLimit || 1)) * 100, 100).toFixed(0)}%
                        </span>
                    </div>
                    <p className="text-3xl font-bold text-blue-700 dark:text-blue-300 tracking-tight">{formatCurrency(totalLimit)}</p>
                    <div className="w-full bg-blue-200/50 dark:bg-blue-900/30 rounded-full h-1.5 mt-3 mb-2 overflow-hidden relative">
                        <div className="bg-blue-500 dark:bg-blue-400 h-1.5 rounded-full absolute left-0 top-0 transition-all duration-500 shadow-[0_0_10px_rgba(59,130,246,0.5)]" style={{ width: `${Math.min((totalExpense / (totalLimit || 1)) * 100, 100)}%` }}></div>
                    </div>
                    <p className="text-xs text-blue-600/80 dark:text-blue-400/80 font-medium">Остаток: {formatCurrency(Math.max(0, totalLimit - totalExpense))}</p>
                </div>
            </div>

            <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
                {/* Tabs */}
                <div className="flex gap-2">
                    <button
                        onClick={() => setActiveTab('expense')}
                        className={`px-5 py-2.5 rounded-xl font-medium transition-all ${activeTab === 'expense' ? 'bg-[var(--color-surface)] dark:bg-[var(--color-surface)] text-blue-600 dark:text-blue-400 shadow-sm border border-slate-200/60 dark:border-[var(--color-border-strong)]' : 'text-slate-500 dark:text-[var(--color-text-muted)] hover:text-slate-700 dark:hover:text-[#e8f5ec] hover:bg-[var(--color-surface-soft)] dark:hover:bg-[#1a222d]'}`}
                    >
                        Расходы
                    </button>
                    <button
                        onClick={() => setActiveTab('income')}
                        className={`px-5 py-2.5 rounded-xl font-medium transition-all ${activeTab === 'income' ? 'bg-[var(--color-surface)] dark:bg-[var(--color-surface)] text-blue-600 dark:text-blue-400 shadow-sm border border-slate-200/60 dark:border-[var(--color-border-strong)]' : 'text-slate-500 dark:text-[var(--color-text-muted)] hover:text-slate-700 dark:hover:text-[#e8f5ec] hover:bg-[var(--color-surface-soft)] dark:hover:bg-[#1a222d]'}`}
                    >
                        Доходы
                    </button>
                </div>

                {/* Action Bar */}
                <button
                    onClick={() => openAddModal(activeTab)}
                    className="flex items-center gap-2 bg-[var(--color-primary)] text-white px-5 py-2.5 rounded-xl hover:opacity-90 transition-all shadow-sm hover:shadow-md font-medium w-full sm:w-auto justify-center"
                >
                    <Plus className="w-5 h-5" />
                    Добавить {activeTab === 'income' ? 'Доход' : 'Расход'}
                </button>
            </div>

            {/* Tables */}
            <div className="bg-[var(--color-surface)]/90 dark:bg-[var(--color-surface)]/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgba(0,0,0,0.2)] border border-[var(--color-border-default)] dark:border-[var(--color-border-default)] overflow-hidden transition-colors">
                {activeTab === 'expense' ? (
                    <div className="divide-y divide-slate-100/60 dark:divide-[var(--color-border-default)]/60">
                        {expenses.length === 0 && (
                            <div className="p-6">
                                <PageState
                                    variant="empty"
                                    title="Расходов пока нет"
                                    description="Добавьте первый расход за этот месяц, чтобы увидеть структуру категорий и лимиты."
                                    actionLabel="Добавить расход"
                                    onAction={() => openAddModal('expense')}
                                />
                            </div>
                        )}
                        <DndContext
                            sensors={sensors}
                            collisionDetection={closestCenter}
                            onDragEnd={handleDragEnd}
                        >
                            <SortableContext
                                items={sortedGroups.map(g => g.id)}
                                strategy={verticalListSortingStrategy}
                            >
                                {sortedGroups.map(group => {
                                    const isMulti = group.items.length > 1;
                                    const isExpanded = expandedCategories[group.id] ?? false;

                                    return (
                                        <SortableGroup
                                            key={group.id}
                                            group={group}
                                            isExpanded={isExpanded}
                                            toggleCategory={toggleCategory}
                                            itemsContent={group.items.map(item => (
                                                <tr key={item.id} className="hover:bg-slate-50/50 dark:hover:bg-[#1a222d] group/row transition-colors">
                                                    <td className="p-4 pl-12 text-sm text-slate-500 dark:text-[var(--color-text-muted)] w-32 whitespace-nowrap">{formatDate(item.date)}</td>
                                                    <td className="p-4 text-sm text-slate-600 dark:text-slate-300">{item.comment}</td>
                                                    <td className="p-4 text-sm font-medium text-right w-32">
                                                        {editingId === item.id && activeTab === 'expense' ? (
                                                            <input
                                                                type="number"
                                                                value={editingAmount}
                                                                onChange={(e) => setEditingAmount(e.target.value)}
                                                                onBlur={() => handleBlur(item.id, 'expense')}
                                                                onKeyDown={(e) => handleKeyDown(e, item.id, 'expense')}
                                                                className="w-full p-1.5 border border-blue-200 dark:border-blue-800 rounded-lg text-right focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 dark:bg-[var(--color-surface)] dark:text-[var(--color-text-main)]"
                                                                autoFocus
                                                                aria-label="Сумма расхода"
                                                                onClick={(e) => e.stopPropagation()}
                                                            />
                                                        ) : (
                                                            <span className="text-slate-700 dark:text-[var(--color-text-main)]">{formatCurrency(item.amount)}</span>
                                                        )}
                                                    </td>
                                                    <td className="p-4 w-24 text-right">
                                                        <div className="flex items-center justify-end gap-1 opacity-0 group-hover/row:opacity-100 transition-opacity">
                                                            <button
                                                                onClick={(e) => { e.stopPropagation(); handleEditClick(item); }}
                                                                className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors"
                                                                title="Редактировать"
                                                            >
                                                                <Pencil className="w-4 h-4" />
                                                            </button>
                                                            <button 
                                                                onClick={(e) => { e.stopPropagation(); handleDelete(item.id, 'expense'); }}
                                                                className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-rose-500 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors"
                                                                title="Удалить"
                                                            >
                                                                <Trash2 className="w-4 h-4" />
                                                            </button>
                                                        </div>
                                                    </td>
                                                </tr>
                                            ))}
                                            contextAddButton={isMulti && (
                                                <div className="p-3 pl-12 border-t border-slate-100/50 dark:border-[var(--color-border-default)]/50 bg-slate-50/30 dark:bg-[var(--color-surface-soft)]/30 flex justify-end transition-colors">
                                                    <button
                                                        onClick={(e) => { e.stopPropagation(); openAddModal('expense', group.id); }}
                                                        className="flex items-center gap-2 text-sm text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300 font-medium px-4 py-2 rounded-xl hover:bg-blue-50 dark:hover:bg-blue-900/30 transition-colors"
                                                    >
                                                        <Plus className="w-4 h-4" />
                                                        Добавить расход в "{group.name}"
                                                    </button>
                                                </div>
                                            )}
                                        />
                                    );
                                })}
                            </SortableContext>
                        </DndContext>
                    </div>
                ) : (
                    <table className="w-full text-left">
                        <thead className="bg-slate-50/50 dark:bg-[var(--color-surface-soft)]/50 border-b border-slate-100/60 dark:border-[var(--color-border-default)]/60 transition-colors">
                            <tr>
                                <th className="p-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider">Дата</th>
                                <th className="p-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider">Источник</th>
                                <th className="p-4 font-semibold text-xs text-slate-400 dark:text-[var(--color-text-muted)] uppercase tracking-wider text-right">Сумма</th>
                                <th className="p-4 w-10"></th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100/60 dark:divide-[var(--color-border-default)]/60">
                            {incomes.length === 0 && (
                                <tr>
                                    <td colSpan={4} className="p-6">
                                        <PageState
                                            variant="empty"
                                            title="Доходов пока нет"
                                            description="Добавьте первый доход за этот месяц, чтобы начать расчёт баланса."
                                            actionLabel="Добавить доход"
                                            onAction={() => openAddModal('income')}
                                        />
                                    </td>
                                </tr>
                            )}
                            {incomes.map(item => (
                                <tr key={item.id} className="hover:bg-slate-50/50 dark:hover:bg-[#1a222d] group/row transition-colors">
                                    <td className="p-4 text-sm text-slate-500 dark:text-[var(--color-text-muted)]">{formatDate(item.date)}</td>
                                    <td className="p-4 font-medium text-slate-700 dark:text-[var(--color-text-main)]">{item.source}</td>
                                    <td className="p-4 font-medium text-right text-emerald-600 dark:text-emerald-400">
                                        {editingId === item.id && activeTab === 'income' ? (
                                            <input
                                                type="number"
                                                value={editingAmount}
                                                onChange={(e) => setEditingAmount(e.target.value)}
                                                onBlur={() => handleBlur(item.id, 'income')}
                                                onKeyDown={(e) => handleKeyDown(e, item.id, 'income')}
                                                className="w-24 p-1.5 border border-blue-200 dark:border-blue-800 rounded-lg text-right focus:outline-none focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 dark:bg-[var(--color-surface)] dark:text-emerald-400"
                                                autoFocus
                                                aria-label="Сумма дохода"
                                            />
                                        ) : (
                                            formatCurrency(item.amount)
                                        )}
                                    </td>
                                    <td className="p-4 w-24 text-right">
                                        <div className="flex items-center justify-end gap-1 opacity-0 group-hover/row:opacity-100 transition-opacity">
                                            <button
                                                onClick={() => handleEditClick(item)}
                                                className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors"
                                                title="Редактировать"
                                            >
                                                <Pencil className="w-4 h-4" />
                                            </button>
                                            <button 
                                                onClick={() => handleDelete(item.id, 'income')} 
                                                className="p-1.5 text-slate-400 dark:text-slate-500 hover:text-rose-500 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors"
                                                title="Удалить"
                                            >
                                                <Trash2 className="w-4 h-4" />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Modal */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={activeTab === 'income' ? 'Новый Доход' : 'Новый Расход'}
            >
                <form onSubmit={handleSubmit} className="space-y-4">
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-muted)]">Сумма</label>
                        <input
                            type="number"
                            required
                            value={formData.amount}
                            onChange={e => setFormData({ ...formData, amount: e.target.value })}
                            className="w-full p-2 border border-gray-300 dark:border-[var(--color-border-strong)] rounded-lg focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                            placeholder="0.00"
                        />
                    </div>

                    {activeTab === 'income' ? (
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-muted)]">Источник</label>
                            <input
                                list="income-sources-list"
                                required
                                value={formData.source}
                                onChange={e => setFormData({ ...formData, source: e.target.value })}
                                className="w-full p-2 border border-gray-300 dark:border-[var(--color-border-strong)] rounded-lg focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                                placeholder="Например: Зарплата"
                            />
                            <datalist id="income-sources-list">
                                {incomeSources.map(source => (
                                    <option key={source.id} value={source.name} />
                                ))}
                            </datalist>
                        </div>
                    ) : (
                        <>
                            <div className="space-y-2">
                                <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-muted)]">Категория</label>
                                <select
                                    required
                                    value={formData.categoryId}
                                    onChange={e => setFormData({ ...formData, categoryId: e.target.value })}
                                    aria-label="Категория расхода"
                                    title="Категория расхода"
                                    className="w-full p-2 border border-gray-300 dark:border-[var(--color-border-strong)] rounded-lg focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                                >
                                    <option value="">Выберите категорию</option>
                                    {categories.map(cat => (
                                        <option key={cat.id} value={cat.id}>{cat.name}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-muted)]">Комментарий</label>
                                <input
                                    type="text"
                                    value={formData.comment}
                                    onChange={e => setFormData({ ...formData, comment: e.target.value })}
                                    className="w-full p-2 border border-gray-300 dark:border-[var(--color-border-strong)] rounded-lg focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                                    placeholder="Опционально"
                                />
                            </div>
                        </>
                    )}

                    <button
                        type="submit"
                        className="w-full bg-[var(--color-primary)] text-white py-3 rounded-lg font-medium hover:opacity-90 transition-colors mt-4"
                    >
                        Сохранить
                    </button>
                </form>
            </Modal>

            {/* Budget Modal */}
            <Modal
                isOpen={isBudgetModalOpen}
                onClose={() => setIsBudgetModalOpen(false)}
                title="Настройка бюджета (лимитов)"
            >
                <div className="space-y-4 max-h-[55vh] overflow-y-auto pr-3 -mr-3">
                    <div className="bg-gradient-to-r from-blue-100/60 to-indigo-100/40 dark:from-blue-900/40 dark:to-indigo-900/20 backdrop-blur-md p-2 rounded-2xl mb-6 shadow-sm border border-blue-200/50 dark:border-blue-800/50 text-center transition-colors">
                        <p className="text-sm font-medium text-blue-800/80 dark:text-blue-300/80 mb-1">Общий лимит на месяц</p>
                        <p className="text-xl font-bold tracking-tight text-blue-900 dark:text-blue-100">{formatCurrency(totalLimit)}</p>
                    </div>
                    {categories.map(cat => {
                        const budget = budgets.find(b => b.category_id === cat.id);
                        const limit = budget ? budget.limit_amount : 0;
                        return (
                            <div key={cat.id} className="flex items-center justify-between gap-4">
                                <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-main)] flex-1">{cat.name}</label>
                                <div className="relative w-32">
                                    <input
                                        type="number"
                                        value={limit}
                                        onChange={e => handleBudgetChange(cat.id, parseFloat(e.target.value) || 0)}
                                        aria-label={`Лимит для категории ${cat.name}`}
                                        title={`Лимит для категории ${cat.name}`}
                                        className="w-full p-2 pr-6 border border-gray-300 dark:border-[var(--color-border-strong)] rounded focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none text-right bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                                    />
                                    <span className="absolute right-2 top-2 text-gray-400 dark:text-[var(--color-text-muted)] text-xs">₽</span>
                                </div>
                            </div>
                        );
                    })}
                </div>
                <div className="mt-6 pt-5 border-t border-emerald-100/30 dark:border-[var(--color-border-default)]">
                    <button
                        onClick={() => setIsBudgetModalOpen(false)}
                        className="w-full bg-[var(--color-primary)] text-white py-3 rounded-lg font-medium hover:opacity-90 transition-colors"
                    >
                        Готово
                    </button>
                </div>
            </Modal>
            {/* Confirmation Modal for Blur Save */}
            <Modal
                isOpen={!!pendingSave}
                onClose={() => {
                    setPendingSave(null);
                    setEditingId(null);
                }}
                title="Подтверждение"
            >
                <div className="space-y-4">
                    <p className="text-gray-600 dark:text-[var(--color-text-muted)]">Сохранить изменения суммы?</p>
                    <div className="rounded-xl border border-blue-200 dark:border-blue-900/50 bg-blue-50 dark:bg-blue-950/30 px-4 py-3 text-sm text-blue-700 dark:text-blue-300 transition-colors">
                        Новое значение сразу повлияет на баланс и расчёты выбранного месяца.
                    </div>
                    <div className="flex gap-3">
                        <button
                            onClick={() => {
                                if (pendingSave) {
                                    saveAmount(pendingSave.id, pendingSave.type);
                                }
                                setPendingSave(null);
                            }}
                            className="flex-1 bg-[var(--color-primary)] text-white py-3 rounded-xl font-medium hover:opacity-90 transition-colors shadow-sm"
                        >
                            Сохранить
                        </button>
                        <button
                            onClick={() => {
                                setPendingSave(null);
                                setEditingId(null);
                            }}
                            className="flex-1 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 py-3 rounded-xl font-medium hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                        >
                            Отмена
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Confirmation Modal for Delete */}
            <Modal
                isOpen={!!pendingDelete}
                onClose={() => {
                    setPendingDelete(null);
                }}
                title="Подтверждение удаления"
            >
                <div className="space-y-4">
                    <p className="text-gray-600 dark:text-[var(--color-text-muted)]">Вы уверены, что хотите удалить эту запись?</p>
                    <div className="rounded-xl border border-rose-200 dark:border-rose-900/50 bg-rose-50 dark:bg-rose-950/30 px-4 py-3 text-sm text-rose-700 dark:text-rose-300 transition-colors">
                        Запись будет удалена из месяца без возможности восстановить её из интерфейса.
                    </div>
                    <div className="flex gap-3">
                        <button
                            onClick={confirmDelete}
                            className="flex-1 bg-rose-600 text-white py-3 rounded-xl font-medium hover:bg-rose-700 transition-colors"
                        >
                            Удалить
                        </button>
                        <button
                            onClick={() => {
                                setPendingDelete(null);
                            }}
                            className="flex-1 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 py-3 rounded-xl font-medium hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                        >
                            Отмена
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Confirmation Modal for New Source */}
            <Modal
                isOpen={showNewSourceConfirm}
                onClose={() => {
                    setShowNewSourceConfirm(false);
                    setNewSourceName('');
                }}
                title="Новый источник дохода"
            >
                <div className="space-y-4">
                    <p className="text-gray-600 dark:text-[var(--color-text-muted)]">Добавить новый источник <span className="font-medium text-slate-900 dark:text-[var(--color-text-main)]">'{newSourceName}'</span> в список?</p>
                    <div className="rounded-xl border border-blue-200 dark:border-blue-900/50 bg-blue-50 dark:bg-blue-950/30 px-4 py-3 text-sm text-blue-700 dark:text-blue-300 transition-colors">
                        Если подтвердить добавление, источник станет доступен и в следующих месяцах.
                    </div>
                    <div className="flex gap-3">
                        <button
                            onClick={() => confirmAddNewSource(true)}
                            className="flex-1 bg-[var(--color-primary)] text-white py-3 rounded-xl font-medium hover:opacity-90 transition-colors shadow-sm"
                        >
                            Да, добавить
                        </button>
                        <button
                            onClick={() => confirmAddNewSource(false)}
                            className="flex-1 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 py-3 rounded-xl font-medium hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                        >
                            Нет, только сохранить
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
};

export default MonthView;
