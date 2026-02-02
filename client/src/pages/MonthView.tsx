import React, { useEffect, useState, useRef } from 'react';
import { ChevronLeft, ChevronRight, Plus, Trash2, Target, ChevronDown, Pencil, GripVertical } from 'lucide-react';
import { DndContext, closestCenter, KeyboardSensor, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import type { DragEndEvent } from '@dnd-kit/core';
import { arrayMove, SortableContext, sortableKeyboardCoordinates, verticalListSortingStrategy, useSortable } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { format } from 'date-fns';
import { ru } from 'date-fns/locale';
import {
    ensureMonth, getIncomes, getExpenses, getCategories, getBudgets, setBudget,
    addIncome, addExpense, deleteIncome, deleteExpense, updateExpense, reorderCategories
} from '../api';
import type { Month, Income, Expense, Category, Budget } from '../api';
import { formatCurrency, formatDate } from '../utils';
import Modal from '../components/Modal';

// Sortable Group Component
const SortableGroup = ({ group, isExpanded, toggleCategory, itemsContent, contextAddButton }: any) => {
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
        <div ref={setNodeRef} style={style} className="bg-white group/dnd">
            {/* Group Header */}
            <div
                className={`flex items-center justify-between p-4 bg-gray-50/80 hover:bg-gray-100/80 transition-colors ${isMulti ? 'cursor-pointer' : ''}`}
                onClick={() => isMulti && toggleCategory(group.id)}
            >
                <div className="flex items-center gap-3">
                    {isMulti && (
                        <div className={`p-1 rounded-full bg-white shadow-sm transition-transform duration-200 ${isExpanded ? 'rotate-180' : ''}`}>
                            <ChevronDown className="w-4 h-4 text-gray-500" />
                        </div>
                    )}
                    <span className="font-medium text-gray-800">{group.name}</span>
                    <span className="text-xs text-gray-400 bg-white px-2 py-0.5 rounded-full border border-gray-200">
                        {group.items.length} {group.items.length === 1 ? 'запись' : 'записей'}
                    </span>
                </div>
                <div className="flex items-center gap-4">
                    <span className="font-bold text-gray-800">{formatCurrency(group.total)}</span>
                    <div {...attributes} {...listeners} className="cursor-grab text-gray-400 hover:text-gray-600 p-1 rounded hover:bg-gray-200" onClick={e => e.stopPropagation()}>
                        <GripVertical className="w-5 h-5" />
                    </div>
                </div>
            </div>

            {/* Items List */}
            <div className={`transition-all duration-300 ease-in-out overflow-hidden ${isMulti && !isExpanded ? 'max-h-0 opacity-0' : 'max-h-[1000px] opacity-100'}`}>
                <table className="w-full text-left">
                    <tbody className="divide-y divide-gray-100">
                        {itemsContent}
                    </tbody>
                </table>
                {contextAddButton}
            </div>
        </div>
    );
};

const MonthView: React.FC = () => {
    const [currentDate, setCurrentDate] = useState(new Date());
    const [monthData, setMonthData] = useState<Month | null>(null);
    const [incomes, setIncomes] = useState<Income[]>([]);
    const [expenses, setExpenses] = useState<Expense[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
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
    const isSavingRef = useRef(false);

    const sensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        })
    );

    // State for expanded groups
    const [expandedCategories, setExpandedCategories] = useState<Record<number, boolean>>({});

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

    const loadData = async () => {
        try {
            const year = currentDate.getFullYear();
            const month = currentDate.getMonth() + 1;

            const mRes = await ensureMonth(year, month);
            setMonthData(mRes.data);

            const [incRes, expRes, catRes, budRes] = await Promise.all([
                getIncomes(mRes.data.id),
                getExpenses(mRes.data.id),
                getCategories(),
                getBudgets(mRes.data.id)
            ]);

            setIncomes(incRes.data);
            setExpenses(expRes.data);
            setCategories(catRes.data);
            setBudgets(budRes.data);
            
        } catch (e) {
            console.error("Error loading data", e);
        }
    };

    useEffect(() => {
        loadData();
    }, [currentDate]);

    const handlePrevMonth = () => {
        setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() - 1, 1));
    };

    const handleNextMonth = () => {
        setCurrentDate(new Date(currentDate.getFullYear(), currentDate.getMonth() + 1, 1));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!monthData) return;

        try {
            if (activeTab === 'income') {
                await addIncome({
                    month_id: monthData.id,
                    source: formData.source,
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
        }
    };

    const handleEditClick = (item: Expense) => {
        setEditingId(item.id);
        setEditingAmount(item.amount.toString());
        isSavingRef.current = false;
    };

    const saveExpenseAmount = async (id: number) => {
        const amount = parseFloat(editingAmount);
        if (isNaN(amount)) {
            setEditingId(null);
            return;
        }
        try {
            await updateExpense(id, { amount });
            await loadData();
        } catch (e) { console.error(e); }
        setEditingId(null);
    };

    const handleBlur = (id: number) => {
        if (isSavingRef.current) {
            isSavingRef.current = false;
            return;
        }
        if (confirm('Сохранить изменения?')) {
            saveExpenseAmount(id);
        } else {
            setEditingId(null);
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent, id: number) => {
        if (e.key === 'Enter') {
            isSavingRef.current = true;
            saveExpenseAmount(id);
        } else if (e.key === 'Escape') {
            isSavingRef.current = true; // Prevent blur confirm
            setEditingId(null);
        }
    };

    const handleDelete = async (id: number, type: 'income' | 'expense') => {
        if (!confirm('Удалить запись?')) return;
        try {
            if (type === 'income') await deleteIncome(id);
            else await deleteExpense(id);
            loadData();
        } catch (e) { console.error(e); }
    }

    const handleDragEnd = async (event: DragEndEvent) => {
        const { active, over } = event;

        if (over && active.id !== over.id) {
            const oldIndex = categories.findIndex((c) => c.id === active.id);
            const newIndex = categories.findIndex((c) => c.id === over.id);

            const newCategories = arrayMove(categories, oldIndex, newIndex);
            
            // Optimistic update
            setCategories(newCategories);

            try {
                const newOrderIds = newCategories.map(c => c.id);
                await reorderCategories(newOrderIds);
            } catch (e) {
                console.error("Failed to save order", e);
            }
        }
    };

    const handleBudgetChange = async (categoryId: number, limit: number) => {
        if (!monthData) return;
        try {
            await setBudget({
                month_id: monthData.id,
                category_id: categoryId,
                limit_amount: limit
            });
            // Update local state optimizing update
            setBudgets(prev => {
                const idx = prev.findIndex(b => b.category_id === categoryId);
                if (idx >= 0) {
                    const newB = [...prev];
                    newB[idx] = { ...newB[idx], limit_amount: limit };
                    return newB;
                }
                return [...prev, { id: 0, month_id: monthData.id, category_id: categoryId, limit_amount: limit }]; // id 0 is temp
            });
        } catch (e) { console.error(e); }
    };

    const totalIncome = incomes.reduce((sum, item) => sum + item.amount, 0);
    const totalExpense = expenses.reduce((sum, item) => sum + item.amount, 0);
    const totalLimit = budgets.reduce((sum, item) => sum + item.limit_amount, 0);

    // Grouping Expenses
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

    // Sort groups according to category order (which is preserved in 'categories' state)
    const sortedGroups = categories
        .filter(cat => groupedExpenses[cat.id]) // Only categories with expenses
        .map(cat => groupedExpenses[cat.id]);
    
    // Add any groups that might be missing from categories (safe fallback)
    Object.values(groupedExpenses).forEach(group => {
        if (!categories.find(c => c.id === group.id)) {
            sortedGroups.push(group);
        }
    });

    return (
        <div className="space-y-6">
            {/* Header / Month Selector */}
            <div className="flex items-center justify-between bg-white p-4 rounded-xl shadow-sm border border-gray-100">
                <button onClick={handlePrevMonth} className="p-2 hover:bg-gray-100 rounded-lg">
                    <ChevronLeft className="w-6 h-6 text-gray-600" />
                </button>
                <h2 className="text-xl font-bold text-gray-800 capitalize">
                    {format(currentDate, 'LLLL yyyy', { locale: ru })}
                </h2>
                <button onClick={handleNextMonth} className="p-2 hover:bg-gray-100 rounded-lg">
                    <ChevronRight className="w-6 h-6 text-gray-600" />
                </button>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-emerald-50 p-6 rounded-xl border border-emerald-100">
                    <p className="text-sm text-emerald-600 font-medium">Доходы</p>
                    <p className="text-3xl font-bold text-emerald-700">{formatCurrency(totalIncome)}</p>
                </div>
                <div className="bg-red-50 p-6 rounded-xl border border-red-100">
                    <p className="text-sm text-red-600 font-medium">Расходы</p>
                    <p className="text-3xl font-bold text-red-700">{formatCurrency(totalExpense)}</p>
                </div>
                <div className="bg-blue-50 p-6 rounded-xl border border-blue-100 cursor-pointer hover:bg-blue-100 transition-colors" onClick={() => setIsBudgetModalOpen(true)}>
                    <div className="flex justify-between items-start">
                        <p className="text-sm text-blue-600 font-medium">Лимит на месяц</p>
                        <Target className="w-4 h-4 text-blue-500" />
                    </div>
                    <p className="text-3xl font-bold text-blue-700">{formatCurrency(totalLimit)}</p>
                    <div className="w-full bg-blue-200 rounded-full h-1.5 mt-2">
                        <div className="bg-blue-500 h-1.5 rounded-full" style={{ width: `${Math.min((totalExpense / (totalLimit || 1)) * 100, 100)}%` }}></div>
                    </div>
                    <p className="text-xs text-blue-500 mt-1">Остаток: {formatCurrency(Math.max(0, totalLimit - totalExpense))}</p>
                </div>
            </div>

            {/* Tabs */}
            <div className="flex gap-4 border-b border-gray-200">
                <button
                    onClick={() => setActiveTab('expense')}
                    className={`pb-2 px-4 font-medium transition-colors ${activeTab === 'expense' ? 'text-blue-600 border-b-2 border-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
                >
                    Расходы
                </button>
                <button
                    onClick={() => setActiveTab('income')}
                    className={`pb-2 px-4 font-medium transition-colors ${activeTab === 'income' ? 'text-blue-600 border-b-2 border-blue-600' : 'text-gray-500 hover:text-gray-700'}`}
                >
                    Доходы
                </button>
            </div>

            {/* Action Bar */}
            <div className="flex justify-end">
                <button
                    onClick={() => openAddModal(activeTab)}
                    className="flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-lg hover:bg-blue-600 transition-colors shadow-sm"
                >
                    <Plus className="w-5 h-5" />
                    Добавить {activeTab === 'income' ? 'Доход' : 'Расход'}
                </button>
            </div>

            {/* Tables */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                {activeTab === 'expense' ? (
                    <div className="divide-y divide-gray-100">
                        {expenses.length === 0 && (
                            <div className="p-8 text-center text-gray-400">Нет записей о расходах</div>
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
                                                <tr key={item.id} className="hover:bg-gray-50 group/row">
                                                    <td className="p-4 pl-12 text-sm text-gray-500 w-32 whitespace-nowrap">{formatDate(item.date)}</td>
                                                    <td className="p-4 text-sm text-gray-600">{item.comment}</td>
                                                    <td className="p-4 text-sm font-medium text-right w-32">
                                                        {editingId === item.id ? (
                                                            <input
                                                                type="number"
                                                                value={editingAmount}
                                                                onChange={(e) => setEditingAmount(e.target.value)}
                                                                onBlur={() => handleBlur(item.id)}
                                                                onKeyDown={(e) => handleKeyDown(e, item.id)}
                                                                className="w-full p-1 border border-blue-300 rounded text-right focus:outline-none focus:ring-2 focus:ring-blue-100"
                                                                autoFocus
                                                                onClick={(e) => e.stopPropagation()}
                                                            />
                                                        ) : (
                                                            formatCurrency(item.amount)
                                                        )}
                                                    </td>
                                                    <td className="p-4 w-24 text-right">
                                                        <div className="flex items-center justify-end gap-1 opacity-0 group-hover/row:opacity-100 transition-opacity">
                                                            <button
                                                                onClick={(e) => { e.stopPropagation(); handleEditClick(item); }}
                                                                className="p-1 text-gray-400 hover:text-blue-600 rounded"
                                                                title="Редактировать"
                                                            >
                                                                <Pencil className="w-4 h-4" />
                                                            </button>
                                                            <button 
                                                                onClick={(e) => { e.stopPropagation(); handleDelete(item.id, 'expense'); }}
                                                                className="p-1 text-gray-400 hover:text-red-500 rounded"
                                                                title="Удалить"
                                                            >
                                                                <Trash2 className="w-4 h-4" />
                                                            </button>
                                                        </div>
                                                    </td>
                                                </tr>
                                            ))}
                                            contextAddButton={isMulti && (
                                                <div className="p-3 pl-12 border-t border-gray-100 bg-gray-50/30 flex justify-end">
                                                    <button
                                                        onClick={(e) => { e.stopPropagation(); openAddModal('expense', group.id); }}
                                                        className="flex items-center gap-2 text-sm text-blue-600 hover:text-blue-700 font-medium px-3 py-1.5 rounded-lg hover:bg-blue-50 transition-colors"
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
                        <thead className="bg-gray-50 border-b border-gray-100">
                            <tr>
                                <th className="p-4 font-medium text-gray-500">Дата</th>
                                <th className="p-4 font-medium text-gray-500">Источник</th>
                                <th className="p-4 font-medium text-gray-500 text-right">Сумма</th>
                                <th className="p-4 w-10"></th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {incomes.length === 0 && (
                                <tr><td colSpan={4} className="p-8 text-center text-gray-400">Нет записей</td></tr>
                            )}
                            {incomes.map(item => (
                                <tr key={item.id} className="hover:bg-gray-50">
                                    <td className="p-4">{formatDate(item.date)}</td>
                                    <td className="p-4 font-medium">{item.source}</td>
                                    <td className="p-4 font-medium text-right text-emerald-600">{formatCurrency(item.amount)}</td>
                                    <td className="p-4">
                                        <button onClick={() => handleDelete(item.id, 'income')} className="text-gray-400 hover:text-red-500">
                                            <Trash2 className="w-4 h-4" />
                                        </button>
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
                        <label className="text-sm font-medium text-gray-700">Сумма</label>
                        <input
                            type="number"
                            required
                            value={formData.amount}
                            onChange={e => setFormData({ ...formData, amount: e.target.value })}
                            className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                            placeholder="0.00"
                        />
                    </div>

                    {activeTab === 'income' ? (
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-700">Источник</label>
                            <input
                                type="text"
                                required
                                value={formData.source}
                                onChange={e => setFormData({ ...formData, source: e.target.value })}
                                className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                                placeholder="Например: Зарплата"
                            />
                        </div>
                    ) : (
                        <>
                            <div className="space-y-2">
                                <label className="text-sm font-medium text-gray-700">Категория</label>
                                <select
                                    required
                                    value={formData.categoryId}
                                    onChange={e => setFormData({ ...formData, categoryId: e.target.value })}
                                    className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                                >
                                    <option value="">Выберите категорию</option>
                                    {categories.map(cat => (
                                        <option key={cat.id} value={cat.id}>{cat.name}</option>
                                    ))}
                                </select>
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium text-gray-700">Комментарий</label>
                                <input
                                    type="text"
                                    value={formData.comment}
                                    onChange={e => setFormData({ ...formData, comment: e.target.value })}
                                    className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                                    placeholder="Опционально"
                                />
                            </div>
                        </>
                    )}

                    <button
                        type="submit"
                        className="w-full bg-primary text-white py-3 rounded-lg font-medium hover:bg-blue-600 transition-colors mt-4"
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
                <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
                    <div className="bg-blue-50 p-4 rounded-lg mb-4">
                        <p className="text-sm text-blue-800">Общий лимит на месяц: <span className="font-bold">{formatCurrency(totalLimit)}</span></p>
                    </div>
                    {categories.map(cat => {
                        const budget = budgets.find(b => b.category_id === cat.id);
                        const limit = budget ? budget.limit_amount : 0;
                        return (
                            <div key={cat.id} className="flex items-center justify-between gap-4">
                                <label className="text-sm font-medium text-gray-700 flex-1">{cat.name}</label>
                                <div className="relative w-32">
                                    <input
                                        type="number"
                                        value={limit}
                                        onChange={e => handleBudgetChange(cat.id, parseFloat(e.target.value) || 0)}
                                        className="w-full p-2 pr-6 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 outline-none text-right"
                                    />
                                    <span className="absolute right-2 top-2 text-gray-400 text-xs">₽</span>
                                </div>
                            </div>
                        );
                    })}
                </div>
                <div className="mt-6 pt-4 border-t border-gray-100 sticky bottom-0 bg-white">
                    <button
                        onClick={() => setIsBudgetModalOpen(false)}
                        className="w-full bg-primary text-white py-3 rounded-lg font-medium hover:bg-blue-600"
                    >
                        Готово
                    </button>
                </div>
            </Modal>
        </div>
    );
};

export default MonthView;
