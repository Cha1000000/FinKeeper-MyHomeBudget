import React, { useEffect, useState, useCallback } from 'react';
import { Plus, Edit2, Trash2, Check, X, CalendarDays } from 'lucide-react';
import api, { getCategories, getIncomeSources } from '../api';
import type { Category, IncomeSource } from '../api';
import Modal from '../components/Modal';
import PageState from '../components/PageState';
import StatusBanner from '../components/StatusBanner';
import { useDataChanged } from '../hooks/useWebSocket';

type TabType = 'expenses' | 'income';

interface FixedFormData {
    name: string;
    fixedAmount: string;
    autoDay: string;
    requireConfirm: boolean;
}

const emptyFixedForm: FixedFormData = { name: '', fixedAmount: '', autoDay: '', requireConfirm: false };

function formatCurrency(amount: number): string {
    return amount.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

const Categories: React.FC = () => {
    const [activeTab, setActiveTab] = useState<TabType>('expenses');
    const [categories, setCategories] = useState<Category[]>([]);
    const [incomeSources, setIncomeSources] = useState<IncomeSource[]>([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [newName, setNewName] = useState('');

    // Editing state
    const [editingId, setEditingId] = useState<number | null>(null);
    const [editName, setEditName] = useState('');
    const [pendingDelete, setPendingDelete] = useState<number | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [actionError, setActionError] = useState<string | null>(null);

    // Фиксированные элементы
    const [isFixedModalOpen, setIsFixedModalOpen] = useState(false);
    const [editingFixedId, setEditingFixedId] = useState<number | null>(null);
    const [fixedForm, setFixedForm] = useState<FixedFormData>(emptyFixedForm);
    const [pendingFixedDelete, setPendingFixedDelete] = useState<number | null>(null);

    const getActionErrorMessage = (errorValue: unknown, fallback: string) => {
        const apiError = errorValue as { response?: { data?: { error?: string } }; message?: string };
        return apiError.response?.data?.error || apiError.message || fallback;
    };

    const fetchData = useCallback(async () => {
        setIsLoading(true);
        setError(null);
        try {
            if (activeTab === 'expenses') {
                const res = await getCategories();
                setCategories(res.data);
            } else {
                const res = await getIncomeSources();
                setIncomeSources(res.data);
            }
        } catch (e) {
            console.error(e);
            setError(activeTab === 'expenses'
                ? 'Не удалось загрузить категории расходов. Попробуйте ещё раз.'
                : 'Не удалось загрузить источники дохода. Попробуйте ещё раз.');
        } finally {
            setIsLoading(false);
        }
    }, [activeTab]);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        fetchData();
    }, [fetchData]);

    // Refresh on WebSocket data changes
    useDataChanged(['category', 'income_source', 'all'], () => { fetchData(); });

    const handleAdd = async (e: React.FormEvent) => {
        e.preventDefault();
        setActionError(null);
        try {
            if (activeTab === 'expenses') {
                await api.post('/categories', { name: newName });
            } else {
                await api.post('/income_sources', { name: newName });
            }
            setIsModalOpen(false);
            setNewName('');
            fetchData();
        } catch (e) {
            console.error(e);
            setActionError(getActionErrorMessage(e, activeTab === 'expenses'
                ? 'Не удалось создать категорию. Попробуйте ещё раз.'
                : 'Не удалось создать источник дохода. Попробуйте ещё раз.'));
        }
    };

    const startEdit = (item: Category | IncomeSource) => {
        setEditingId(item.id);
        setEditName(item.name);
    };

    const saveEdit = async () => {
        if (!editingId) return;
        setActionError(null);
        try {
            if (activeTab === 'expenses') {
                await api.put(`/categories/${editingId}`, { name: editName });
            } else {
                await api.put(`/income_sources/${editingId}`, { name: editName });
            }
            setEditingId(null);
            fetchData();
        } catch (e) {
            console.error(e);
            setActionError(getActionErrorMessage(e, activeTab === 'expenses'
                ? 'Не удалось обновить категорию. Попробуйте ещё раз.'
                : 'Не удалось обновить источник дохода. Попробуйте ещё раз.'));
        }
    };

    const cancelEdit = () => {
        setEditingId(null);
        setEditName('');
    };

    const handleDelete = (id: number) => {
        setPendingDelete(id);
    };

    const confirmDelete = async () => {
        if (!pendingDelete) return;
        setActionError(null);
        try {
            if (activeTab === 'expenses') {
                await api.delete(`/categories/${pendingDelete}`);
            } else {
                await api.delete(`/income_sources/${pendingDelete}`);
            }
            setPendingDelete(null);
            fetchData();
        } catch (e) {
            console.error(e);
            setActionError(getActionErrorMessage(e, activeTab === 'expenses'
                ? 'Не удалось удалить категорию. Попробуйте ещё раз.'
                : 'Не удалось удалить источник дохода. Попробуйте ещё раз.'));
        }
    };

    // --- Обработчики для фиксированных элементов ---
    const openAddFixed = () => {
        setEditingFixedId(null);
        setFixedForm(emptyFixedForm);
        setIsFixedModalOpen(true);
    };

    const openEditFixed = (item: Category | IncomeSource) => {
        setEditingFixedId(item.id);
        setFixedForm({
            name: item.name,
            fixedAmount: item.fixed_amount != null ? String(item.fixed_amount) : '',
            autoDay: item.auto_day != null ? String(item.auto_day) : '',
            requireConfirm: item.require_confirm === 1,
        });
        setIsFixedModalOpen(true);
    };

    const handleFixedSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setActionError(null);
        const amount = parseFloat(fixedForm.fixedAmount);
        const day = parseInt(fixedForm.autoDay, 10);
        if (!fixedForm.name.trim() || isNaN(amount) || amount <= 0 || isNaN(day) || day < 1 || day > 31) return;

        try {
            const endpoint = activeTab === 'expenses' ? '/categories' : '/income_sources';
            if (editingFixedId) {
                await api.put(`${endpoint}/${editingFixedId}`, {
                    name: fixedForm.name,
                    is_fixed: 1,
                    fixed_amount: amount,
                    auto_day: day,
                    require_confirm: fixedForm.requireConfirm ? 1 : 0,
                });
            } else {
                await api.post(endpoint, {
                    name: fixedForm.name,
                    is_fixed: 1,
                    fixed_amount: amount,
                    auto_day: day,
                    require_confirm: fixedForm.requireConfirm ? 1 : 0,
                });
            }
            setIsFixedModalOpen(false);
            setFixedForm(emptyFixedForm);
            setEditingFixedId(null);
            fetchData();
        } catch (e) {
            console.error(e);
            setActionError(getActionErrorMessage(e, 'Не удалось сохранить фиксированный элемент.'));
        }
    };

    const confirmFixedDelete = async () => {
        if (!pendingFixedDelete) return;
        setActionError(null);
        try {
            const endpoint = activeTab === 'expenses' ? '/categories' : '/income_sources';
            await api.put(`${endpoint}/${pendingFixedDelete}`, { is_active: 0 });
            setPendingFixedDelete(null);
            fetchData();
        } catch (e) {
            console.error(e);
            setActionError(getActionErrorMessage(e, 'Не удалось удалить фиксированный элемент.'));
        }
    };

    // Разделяем на обычные и фиксированные
    const allItems = activeTab === 'expenses' ? categories : incomeSources;
    const fixedItems = allItems.filter(i => i.is_fixed === 1 && i.is_active === 1);
    const regularItems = allItems.filter(i => i.is_fixed !== 1);

    const currentList = regularItems;
    const itemName = activeTab === 'expenses' ? 'эту категорию' : 'этот источник дохода';
    const modalTitle = activeTab === 'expenses' ? 'Добавить категорию' : 'Добавить источник дохода';
    const buttonTitle = activeTab === 'expenses' ? 'Новая категория' : 'Новый источник';
    const fixedModalTitle = editingFixedId
        ? (activeTab === 'expenses' ? 'Редактировать фиксированную категорию' : 'Редактировать фиксированный источник')
        : (activeTab === 'expenses' ? 'Новая фиксированная категория' : 'Новый фиксированный источник');

    if (isLoading && currentList.length === 0) {
        return (
            <PageState
                variant="loading"
                title={activeTab === 'expenses' ? 'Загружаем категории' : 'Загружаем источники дохода'}
                description={activeTab === 'expenses'
                    ? 'Подготавливаем список категорий расходов и их текущее состояние.'
                    : 'Подготавливаем список источников дохода для работы с месяцем.'}
            />
        );
    }

    if (error && currentList.length === 0) {
        return (
            <PageState
                variant="error"
                title={activeTab === 'expenses' ? 'Не удалось открыть категории' : 'Не удалось открыть источники дохода'}
                description={error}
                actionLabel="Повторить"
                onAction={() => {
                    void fetchData();
                }}
            />
        );
    }

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            {actionError ? (
                <StatusBanner variant="error" title="Операция не завершена">
                    {actionError}
                </StatusBanner>
            ) : null}

            {error ? (
                <StatusBanner variant="error">
                    {error}
                </StatusBanner>
            ) : null}

            <div className="flex items-center justify-between mb-2">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-[var(--color-text-main)] tracking-tight">Категории и источники</h2>
                    <p className="text-sm text-slate-500 dark:text-[var(--color-text-muted)] mt-0.5">Настройка статей доходов и расходов</p>
                </div>
                <button
                    onClick={() => setIsModalOpen(true)}
                    className="flex items-center gap-2 bg-[var(--color-primary)] text-white px-5 py-2.5 rounded-xl hover:opacity-90 transition-all shadow-sm hover:shadow-md font-medium"
                >
                    <Plus className="w-5 h-5" />
                    {buttonTitle}
                </button>
            </div>

            {/* Tabs */}
            <div className="flex gap-2">
                <button
                    onClick={() => setActiveTab('expenses')}
                    className={`px-5 py-2.5 rounded-xl font-medium transition-all ${
                        activeTab === 'expenses'
                            ? 'bg-[var(--color-surface)] dark:bg-[var(--color-surface)] text-blue-600 dark:text-blue-400 shadow-sm border border-slate-200/60 dark:border-[var(--color-border-strong)]'
                            : 'text-slate-500 dark:text-[var(--color-text-muted)] hover:text-slate-700 dark:hover:text-[#e8f5ec] hover:bg-[var(--color-surface-soft)] dark:hover:bg-[#1a222d]'
                    }`}
                >
                    Категории расходов
                </button>
                <button
                    onClick={() => setActiveTab('income')}
                    className={`px-5 py-2.5 rounded-xl font-medium transition-all ${
                        activeTab === 'income'
                            ? 'bg-[var(--color-surface)] dark:bg-[var(--color-surface)] text-blue-600 dark:text-blue-400 shadow-sm border border-slate-200/60 dark:border-[var(--color-border-strong)]'
                            : 'text-slate-500 dark:text-[var(--color-text-muted)] hover:text-slate-700 dark:hover:text-[#e8f5ec] hover:bg-[var(--color-surface-soft)] dark:hover:bg-[#1a222d]'
                    }`}
                >
                    Источники дохода
                </button>
            </div>

            {/* Секция "Фиксированные" */}
            <div className="bg-[var(--color-surface)]/90 dark:bg-[var(--color-surface)]/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgba(0,0,0,0.2)] border border-[var(--color-border-default)] dark:border-[var(--color-border-default)] overflow-hidden transition-colors">
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100/60 dark:border-[var(--color-border-default)]/60">
                    <div className="flex items-center gap-2">
                        <CalendarDays className="w-5 h-5 text-amber-500" />
                        <h3 className="font-semibold text-slate-800 dark:text-[var(--color-text-main)]">Фиксированные</h3>
                    </div>
                    <button
                        onClick={openAddFixed}
                        className="flex items-center gap-1.5 text-sm font-medium text-[var(--color-primary)] hover:opacity-80 transition-opacity"
                    >
                        <Plus className="w-4 h-4" />
                        Добавить
                    </button>
                </div>
                {fixedItems.length === 0 ? (
                    <div className="px-6 py-4 text-sm text-slate-400 dark:text-[var(--color-text-muted)]">
                        {activeTab === 'expenses' ? 'Нет фиксированных категорий' : 'Нет фиксированных источников'}
                    </div>
                ) : (
                    <ul className="divide-y divide-slate-100/60 dark:divide-[var(--color-border-default)]/60">
                        {fixedItems.map(item => (
                            <li key={item.id} className="p-4 px-6 hover:bg-[var(--color-surface-soft)] dark:hover:bg-[#1a222d] flex items-center justify-between group transition-colors">
                                <div>
                                    <div className="flex items-center gap-2">
                                        <span className="font-medium text-slate-700 dark:text-[var(--color-text-main)]">{item.name}</span>
                                        {item.require_confirm === 1 && (
                                            <span className="text-[11px] font-medium text-amber-700 dark:text-amber-400 bg-amber-100/70 dark:bg-amber-900/40 border border-amber-200/60 dark:border-amber-800/50 px-2 py-0.5 rounded-lg" title={activeTab === 'expenses'
                                                ? 'Платёж не списывается автоматически — ждёт кнопки «Оплачено» на экране Месяц'
                                                : 'Доход не записывается автоматически — ждёт кнопки «Получено» на экране Месяц'}>
                                                ✋ вручную
                                            </span>
                                        )}
                                    </div>
                                    <div className="text-sm text-slate-400 dark:text-[var(--color-text-muted)] mt-0.5">
                                        {item.fixed_amount != null ? formatCurrency(item.fixed_amount) : '—'} ₽ · {item.auto_day} числа
                                    </div>
                                </div>
                                <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                    <button onClick={() => openEditFixed(item)} aria-label="Редактировать" title="Редактировать" className="p-2 text-slate-400 dark:text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors">
                                        <Edit2 className="w-4 h-4" />
                                    </button>
                                    <button onClick={() => setPendingFixedDelete(item.id)} aria-label="Удалить" title="Удалить" className="p-2 text-slate-400 dark:text-slate-500 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors">
                                        <Trash2 className="w-4 h-4" />
                                    </button>
                                </div>
                            </li>
                        ))}
                    </ul>
                )}
            </div>

            {/* Обычные категории / источники */}
            <div className="bg-[var(--color-surface)]/90 dark:bg-[var(--color-surface)]/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgba(0,0,0,0.2)] border border-[var(--color-border-default)] dark:border-[var(--color-border-default)] overflow-hidden transition-colors">
                <ul className="divide-y divide-slate-100/60 dark:divide-[var(--color-border-default)]/60">
                    {currentList.length === 0 && (
                        <li className="p-6">
                            <PageState
                                variant="empty"
                                title={activeTab === 'expenses' ? 'Категорий пока нет' : 'Источников дохода пока нет'}
                                description={activeTab === 'expenses'
                                    ? 'Добавьте первую категорию расходов, чтобы затем использовать её в месячных операциях и лимитах.'
                                    : 'Добавьте первый источник дохода, чтобы он был доступен при создании доходов.'}
                                compact
                            />
                        </li>
                    )}
                    {currentList.map((item) => (
                        <li key={item.id} className="p-4 px-6 hover:bg-[var(--color-surface-soft)] dark:hover:bg-[#1a222d] flex items-center justify-between group transition-colors">
                            {editingId === item.id ? (
                                <div className="flex items-center gap-3 w-full">
                                    <input
                                        type="text"
                                        value={editName}
                                        onChange={(e) => setEditName(e.target.value)}
                                        aria-label={activeTab === 'expenses' ? 'Редактировать название категории' : 'Редактировать название источника дохода'}
                                        className="flex-1 p-2.5 border border-[var(--color-border-default)] dark:border-[var(--color-border-strong)] rounded-xl focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                                        placeholder={activeTab === 'expenses' ? 'Название категории' : 'Название источника'}
                                        autoFocus
                                    />
                                    <button onClick={saveEdit} aria-label="Сохранить изменения" title="Сохранить изменения" className="p-2.5 text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/30 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 rounded-xl transition-colors">
                                        <Check className="w-5 h-5" />
                                    </button>
                                    <button onClick={cancelEdit} aria-label="Отменить редактирование" title="Отменить редактирование" className="p-2.5 text-slate-500 dark:text-[var(--color-text-muted)] bg-slate-100 dark:bg-[#2a3441] hover:bg-slate-200 dark:hover:bg-[#3d4a5c] rounded-xl transition-colors">
                                        <X className="w-5 h-5" />
                                    </button>
                                </div>
                            ) : (
                                <>
                                    <span className="font-medium text-slate-700 dark:text-[var(--color-text-main)]">{item.name}</span>
                                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button onClick={() => startEdit(item)} aria-label="Редактировать" title="Редактировать" className="p-2 text-slate-400 dark:text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/30 rounded-lg transition-colors">
                                            <Edit2 className="w-4 h-4" />
                                        </button>
                                        <button onClick={() => handleDelete(item.id)} aria-label="Удалить" title="Удалить" className="p-2 text-slate-400 dark:text-slate-500 hover:text-rose-600 dark:hover:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-lg transition-colors">
                                            <Trash2 className="w-4 h-4" />
                                        </button>
                                    </div>
                                </>
                            )}
                        </li>
                    ))}
                </ul>
            </div>

            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title={modalTitle}
            >
                <form onSubmit={handleAdd} className="space-y-4">
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-slate-700 dark:text-[var(--color-text-muted)]">Название</label>
                        <input
                            type="text"
                            required
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            className="w-full p-3 border border-[var(--color-border-default)] dark:border-[var(--color-border-strong)] rounded-xl focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors shadow-sm"
                            placeholder="Введите название..."
                        />
                    </div>
                    <button
                        type="submit"
                        className="w-full bg-[var(--color-primary)] text-white py-3 rounded-xl font-medium hover:opacity-90 transition-all shadow-sm hover:shadow-md mt-4"
                    >
                        Создать
                    </button>
                </form>
            </Modal>

            <Modal
                isOpen={!!pendingDelete}
                onClose={() => setPendingDelete(null)}
                title="Подтверждение удаления"
            >
                <div className="space-y-4">
                    <p className="text-gray-600 dark:text-[var(--color-text-muted)]">Уверены, что хотите удалить {itemName}?</p>
                    <div className="rounded-xl border border-rose-200 dark:border-rose-900/50 bg-rose-50 dark:bg-rose-950/30 px-4 py-3 text-sm text-rose-700 dark:text-rose-300 transition-colors">
                        Элемент будет скрыт из активного списка и перестанет быть доступен для новых операций.
                    </div>
                    <div className="flex gap-3">
                        <button
                            onClick={confirmDelete}
                            className="flex-1 bg-rose-600 text-white py-3 rounded-xl font-medium hover:bg-rose-700 transition-colors"
                        >
                            Удалить
                        </button>
                        <button
                            onClick={() => setPendingDelete(null)}
                            className="flex-1 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 py-3 rounded-xl font-medium hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                        >
                            Отмена
                        </button>
                    </div>
                </div>
            </Modal>

            {/* Модаль добавления/редактирования фиксированного элемента */}
            <Modal
                isOpen={isFixedModalOpen}
                onClose={() => { setIsFixedModalOpen(false); setEditingFixedId(null); }}
                title={fixedModalTitle}
            >
                <form onSubmit={handleFixedSubmit} className="space-y-4">
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-slate-700 dark:text-[var(--color-text-muted)]">Название</label>
                        <input
                            type="text"
                            required
                            value={fixedForm.name}
                            onChange={(e) => setFixedForm(f => ({ ...f, name: e.target.value }))}
                            className="w-full p-3 border border-[var(--color-border-default)] dark:border-[var(--color-border-strong)] rounded-xl focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors shadow-sm"
                            placeholder="Введите название..."
                            autoFocus
                        />
                    </div>
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-slate-700 dark:text-[var(--color-text-muted)]">Сумма</label>
                        <input
                            type="number"
                            required
                            min="0.01"
                            step="0.01"
                            value={fixedForm.fixedAmount}
                            onChange={(e) => setFixedForm(f => ({ ...f, fixedAmount: e.target.value }))}
                            className="w-full p-3 border border-[var(--color-border-default)] dark:border-[var(--color-border-strong)] rounded-xl focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors shadow-sm"
                            placeholder="0.00"
                        />
                    </div>
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-slate-700 dark:text-[var(--color-text-muted)]">День месяца (1–31)</label>
                        <input
                            type="number"
                            required
                            min="1"
                            max="31"
                            value={fixedForm.autoDay}
                            onChange={(e) => setFixedForm(f => ({ ...f, autoDay: e.target.value }))}
                            className="w-full p-3 border border-[var(--color-border-default)] dark:border-[var(--color-border-strong)] rounded-xl focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors shadow-sm"
                            placeholder="25"
                        />
                    </div>
                    <label className="flex items-start gap-3 p-3 rounded-xl border border-[var(--color-border-default)] dark:border-[var(--color-border-strong)] bg-[var(--color-surface-soft)]/50 cursor-pointer transition-colors">
                        <input
                            type="checkbox"
                            checked={fixedForm.requireConfirm}
                            onChange={(e) => setFixedForm(f => ({ ...f, requireConfirm: e.target.checked }))}
                            className="mt-0.5 w-4 h-4 accent-[var(--color-primary)]"
                        />
                        <span>
                            <span className="block text-sm font-medium text-slate-700 dark:text-[var(--color-text-main)]">
                                {activeTab === 'expenses' ? 'Требует подтверждения оплаты' : 'Требует подтверждения получения'}
                            </span>
                            <span className="block text-xs text-slate-400 dark:text-[var(--color-text-muted)] mt-0.5">
                                {activeTab === 'expenses'
                                    ? 'Платёж не спишется автоматически, а будет ждать кнопки «Оплачено» на экране Месяц'
                                    : 'Доход не запишется автоматически, а будет ждать кнопки «Получено» на экране Месяц'}
                            </span>
                        </span>
                    </label>
                    <button
                        type="submit"
                        className="w-full bg-[var(--color-primary)] text-white py-3 rounded-xl font-medium hover:opacity-90 transition-all shadow-sm hover:shadow-md mt-4"
                    >
                        {editingFixedId ? 'Сохранить' : 'Создать'}
                    </button>
                </form>
            </Modal>

            {/* Подтверждение удаления фиксированного элемента */}
            <Modal
                isOpen={!!pendingFixedDelete}
                onClose={() => setPendingFixedDelete(null)}
                title="Подтверждение удаления"
            >
                <div className="space-y-4">
                    <p className="text-gray-600 dark:text-[var(--color-text-muted)]">
                        Уверены, что хотите удалить этот фиксированный элемент?
                    </p>
                    <div className="rounded-xl border border-rose-200 dark:border-rose-900/50 bg-rose-50 dark:bg-rose-950/30 px-4 py-3 text-sm text-rose-700 dark:text-rose-300 transition-colors">
                        Элемент будет деактивирован. Автосоздание записей прекратится, но уже созданные записи останутся.
                    </div>
                    <div className="flex gap-3">
                        <button
                            onClick={confirmFixedDelete}
                            className="flex-1 bg-rose-600 text-white py-3 rounded-xl font-medium hover:bg-rose-700 transition-colors"
                        >
                            Удалить
                        </button>
                        <button
                            onClick={() => setPendingFixedDelete(null)}
                            className="flex-1 bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 py-3 rounded-xl font-medium hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
                        >
                            Отмена
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
};

export default Categories;
