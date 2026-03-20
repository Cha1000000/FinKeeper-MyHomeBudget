import React, { useEffect, useState, useCallback } from 'react';
import { Plus, Edit2, Trash2, Check, X } from 'lucide-react';
import api, { getCategories, getIncomeSources } from '../api';
import type { Category, IncomeSource } from '../api';
import Modal from '../components/Modal';
import PageState from '../components/PageState';
import StatusBanner from '../components/StatusBanner';
import { useDataChanged } from '../hooks/useWebSocket';

type TabType = 'expenses' | 'income';

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
                await api.put(`/categories/${editingId}`, { name: editName, is_active: 1 });
            } else {
                await api.put(`/income_sources/${editingId}`, { name: editName, is_active: 1 });
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

    const currentList = activeTab === 'expenses' ? categories : incomeSources;
    const itemName = activeTab === 'expenses' ? 'эту категорию' : 'этот источник дохода';
    const modalTitle = activeTab === 'expenses' ? 'Добавить категорию' : 'Добавить источник дохода';
    const buttonTitle = activeTab === 'expenses' ? 'Новая категория' : 'Новый источник';

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
        </div>
    );
};

export default Categories;
