import React, { useEffect, useState } from 'react';
import { Plus, PiggyBank, ArrowDown, ArrowUp } from 'lucide-react';
import classNames from 'classnames';
import { getSavingsGoals, addSavingsGoal, updateSavingsGoal, deleteSavingsGoal, addSavingsTransaction, ensureMonth } from '../api';
import type { SavingsGoal } from '../api';
import { formatCurrency } from '../utils';
import Modal from '../components/Modal';
import PageState from '../components/PageState';
import StatusBanner from '../components/StatusBanner';
import { useDataChanged } from '../hooks/useWebSocket';

const Savings: React.FC = () => {
    const [goals, setGoals] = useState<SavingsGoal[]>([]);
    const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);
    const [isTransModalOpen, setIsTransModalOpen] = useState(false);
    const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
    
    // New Goal State
    const [newGoalName, setNewGoalName] = useState('');
    const [newGoalTarget, setNewGoalTarget] = useState('');
    
    // Edit Goal State
    const [editingGoalId, setEditingGoalId] = useState<number | null>(null);
    const [editGoalName, setEditGoalName] = useState('');
    const [editGoalTarget, setEditGoalTarget] = useState('');
    const [editGoalCurrent, setEditGoalCurrent] = useState('');
    
    // Transaction State
    const [selectedGoalId, setSelectedGoalId] = useState<number | null>(null);
    const [transAmount, setTransAmount] = useState('');
    const [transType, setTransType] = useState<'deposit' | 'withdraw'>('deposit');
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const resetGoalForm = () => {
        setEditingGoalId(null);
        setEditGoalName('');
        setEditGoalTarget('');
        setEditGoalCurrent('');
        setNewGoalName('');
        setNewGoalTarget('');
    };

    const loadData = async () => {
        setIsLoading(true);
        setError(null);
        try {
            const res = await getSavingsGoals();
            setGoals(res.data);
        } catch (e) {
            console.error(e);
            setError('Не удалось загрузить копилки. Проверьте соединение и попробуйте ещё раз.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => { 
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadData(); 
    }, []);

    // Refresh on WebSocket data changes
    useDataChanged(['savings_goal', 'savings_transaction', 'all'], () => { loadData(); });

    const handleCreateGoal = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            await addSavingsGoal({ name: newGoalName, target_amount: parseFloat(newGoalTarget) || 0 });
            setIsGoalModalOpen(false);
            setNewGoalName('');
            setNewGoalTarget('');
            loadData();
            window.dispatchEvent(new Event('savingsUpdated'));
        } catch (e) {
            console.error(e);
            setError('Не удалось создать копилку. Попробуйте ещё раз.');
        }
    };

    const openEditGoal = (goal: SavingsGoal) => {
        setEditingGoalId(goal.id);
        setEditGoalName(goal.name);
        setEditGoalTarget(goal.target_amount.toString());
        setEditGoalCurrent(goal.current_amount.toString());
        setIsGoalModalOpen(true);
    };

    const handleUpdateGoal = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!editingGoalId) return;
        try {
            await updateSavingsGoal(editingGoalId, {
                name: editGoalName,
                target_amount: parseFloat(editGoalTarget) || 0,
                current_amount: parseFloat(editGoalCurrent) || 0
            });
            setIsGoalModalOpen(false);
            resetGoalForm();
            loadData();
            window.dispatchEvent(new Event('savingsUpdated'));
        } catch (e) {
            console.error(e);
            setError('Не удалось обновить копилку. Попробуйте ещё раз.');
        }
    };

    const handleDeleteGoal = async () => {
        if (!editingGoalId) return;
        try {
            await deleteSavingsGoal(editingGoalId);
            setIsDeleteConfirmOpen(false);
            setIsGoalModalOpen(false);
            resetGoalForm();
            loadData();
            window.dispatchEvent(new Event('savingsUpdated'));
        } catch (e) {
            console.error(e);
            setError('Не удалось удалить копилку. Попробуйте ещё раз.');
        }
    };

    const openTransaction = (goalId: number, type: 'deposit' | 'withdraw') => {
        setSelectedGoalId(goalId);
        setTransType(type);
        setIsTransModalOpen(true);
    };

    const handleTransaction = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedGoalId) return;
        try {
            const currentDate = new Date();
            // Link to current month just for record keeping
            const mRes = await ensureMonth(currentDate.getFullYear(), currentDate.getMonth() + 1);

            const amount = parseFloat(transAmount);
            const finalAmount = transType === 'deposit' ? amount : -amount;

            await addSavingsTransaction({
                goal_id: selectedGoalId,
                amount: finalAmount,
                date: currentDate.toISOString(),
                month_id: mRes.data.id
            });

            setIsTransModalOpen(false);
            setTransAmount('');
            loadData();
            window.dispatchEvent(new Event('savingsUpdated'));
        } catch (e) {
            console.error(e);
            setError('Не удалось выполнить операцию по копилке. Попробуйте ещё раз.');
        }
    };

    if (isLoading && goals.length === 0) {
        return (
            <PageState
                variant="loading"
                title="Загружаем копилки"
                description="Подготавливаем ваши цели и текущее состояние накоплений."
            />
        );
    }

    if (error && goals.length === 0) {
        return (
            <PageState
                variant="error"
                title="Не удалось открыть копилки"
                description={error}
                actionLabel="Повторить"
                onAction={() => {
                    void loadData();
                }}
            />
        );
    }

    return (
        <div className="space-y-6 max-w-5xl mx-auto">
            {error && (
                <StatusBanner variant="error" title="Данные копилок обновлены не полностью">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                        <span>{error}</span>
                        <button
                            onClick={() => {
                                void loadData();
                            }}
                            className="rounded-xl bg-rose-600 px-4 py-2 text-white transition-colors hover:bg-rose-700 dark:bg-rose-700 dark:hover:bg-rose-600"
                        >
                            Повторить
                        </button>
                    </div>
                </StatusBanner>
            )}

            <div className="flex items-center justify-between">
                <div className="flex flex-col">
                    <h2 className="text-2xl font-bold text-gray-900 dark:text-[var(--color-text-main)] flex items-center gap-3">
                        <div className="p-2.5 bg-pink-50 dark:bg-pink-900/30 text-pink-600 dark:text-pink-400 rounded-2xl border border-pink-100/50 dark:border-pink-800/50 shadow-sm transition-colors">
                            <PiggyBank className="w-6 h-6" />
                        </div>
                        Копилки
                    </h2>
                    <p className="text-sm text-gray-500 dark:text-[var(--color-text-muted)] mt-1">Управление целями и накоплениями</p>
                </div>
                <button
                    onClick={() => setIsGoalModalOpen(true)}
                    className="flex items-center gap-2 bg-[var(--color-primary)] text-white px-5 py-2.5 rounded-xl hover:opacity-90 transition-all hover:shadow-md shadow-sm font-medium"
                >
                    <Plus className="w-5 h-5" />
                    Новая цель
                </button>
            </div>

            {goals.length === 0 ? (
                <PageState
                    variant="empty"
                    title="Копилок пока нет"
                    description="Создайте первую цель накоплений, чтобы отслеживать прогресс и операции пополнения или снятия."
                    actionLabel="Создать цель"
                    onAction={() => setIsGoalModalOpen(true)}
                />
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                    {goals.map((goal) => {
                        const progress = goal.target_amount > 0
                            ? Math.min((goal.current_amount / goal.target_amount) * 100, 100)
                            : 0;
                        
                        return (
                            <div key={goal.id} className="bg-gradient-to-br from-emerald-50/95 to-emerald-600/70 dark:from-emerald-950/40 dark:to-emerald-900/40 backdrop-blur-md rounded-3xl shadow-[0_8px_40px_rgba(16,185,129,0.08)] border border-emerald-200/70 dark:border-emerald-800/50 p-6 flex flex-col justify-between h-full cursor-pointer hover:shadow-[0_15px_50px_rgba(16,185,129,0.15)] dark:hover:shadow-[0_15px_50px_rgba(16,185,129,0.1)] hover:-translate-y-1 transition-all" onClick={() => openEditGoal(goal)}>
                                <div className="mb-6">
                                    <div className="flex justify-between items-start mb-3">
                                        <h3 className="text-lg font-bold text-slate-800 dark:text-[var(--color-text-main)] leading-tight">{goal.name}</h3>
                                        <div className="bg-slate-100/80 dark:bg-[var(--color-surface-soft)]/80 text-slate-600 dark:text-[var(--color-text-muted)] px-2.5 py-1 rounded-lg text-xs font-bold border border-slate-200/50 dark:border-[var(--color-border-strong)]/50 transition-colors">
                                            {progress.toFixed(0)}%
                                        </div>
                                    </div>
                                    <p className="text-3xl font-bold text-slate-900 dark:text-[var(--color-text-main)] mb-1 tracking-tight">{formatCurrency(goal.current_amount)}</p>
                                    <p className="text-sm text-slate-400 dark:text-slate-500 font-medium">из {formatCurrency(goal.target_amount)}</p>
                                </div>

                                <div className="space-y-5">
                                    <div className="w-full bg-gray-100 dark:bg-[#2a3441] rounded-full h-2.5 overflow-hidden transition-colors">
                                        <div
                                            className="bg-[var(--color-primary)] h-2.5 rounded-full transition-all duration-500"
                                            style={{ width: `${progress}%` }}
                                        ></div>
                                    </div>

                                    <div className="grid grid-cols-2 gap-3">
                                        <button
                                            onClick={(e) => { e.stopPropagation(); openTransaction(goal.id, 'deposit'); }}
                                            className="flex items-center justify-center gap-1.5 py-2.5 bg-emerald-50 dark:bg-emerald-950/30 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-100 dark:hover:bg-emerald-900/50 border border-emerald-100/50 dark:border-emerald-900/50 rounded-xl font-medium transition-colors text-sm shadow-sm"
                                        >
                                            <ArrowUp className="w-4 h-4" /> Пополнить
                                        </button>
                                        <button
                                            onClick={(e) => { e.stopPropagation(); openTransaction(goal.id, 'withdraw'); }}
                                            className="flex items-center justify-center gap-1.5 py-2.5 bg-rose-50 dark:bg-rose-950/30 text-rose-600 dark:text-rose-400 hover:bg-rose-100 dark:hover:bg-rose-900/50 border border-rose-100/50 dark:border-rose-900/50 rounded-xl font-medium transition-colors text-sm shadow-sm"
                                        >
                                            <ArrowDown className="w-4 h-4" /> Снять
                                        </button>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}

            {/* Create/Edit Goal Modal */}
            <Modal
                isOpen={isGoalModalOpen}
                onClose={() => {
                    setIsGoalModalOpen(false);
                    setIsDeleteConfirmOpen(false);
                    resetGoalForm();
                }}
                title={editingGoalId ? "Редактировать копилку" : "Создать новую копилку"}
            >
                <form onSubmit={editingGoalId ? handleUpdateGoal : handleCreateGoal} className="space-y-4">
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-muted)]">Название цели</label>
                        <input
                            type="text" required
                            value={editingGoalId ? editGoalName : newGoalName}
                            onChange={e => editingGoalId ? setEditGoalName(e.target.value) : setNewGoalName(e.target.value)}
                            className="w-full p-2 border border-gray-300 dark:border-[var(--color-border-strong)] rounded-lg focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                            placeholder="Например: На отпуск"
                        />
                    </div>
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-muted)]">Целевая сумма</label>
                        <input
                            type="number" required
                            value={editingGoalId ? editGoalTarget : newGoalTarget}
                            onChange={e => editingGoalId ? setEditGoalTarget(e.target.value) : setNewGoalTarget(e.target.value)}
                            className="w-full p-2 border border-gray-300 dark:border-[var(--color-border-strong)] rounded-lg focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                            placeholder="100000"
                        />
                    </div>
                    {editingGoalId && (
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-muted)]">Текущий баланс</label>
                            <input
                                type="number" required
                                value={editGoalCurrent}
                                onChange={e => setEditGoalCurrent(e.target.value)}
                                className="w-full p-2 border border-gray-300 dark:border-[var(--color-border-strong)] rounded-lg focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                                placeholder="0"
                            />
                        </div>
                    )}
                    <div className="flex gap-3 mt-6">
                        <button
                            type="submit"
                            className="flex-1 bg-[var(--color-primary)] text-white py-3 rounded-xl font-medium hover:opacity-90 transition-colors shadow-sm"
                        >
                            {editingGoalId ? 'Сохранить' : 'Создать'}
                        </button>
                        {editingGoalId && (
                            <button
                                type="button"
                                onClick={() => setIsDeleteConfirmOpen(true)}
                                className="flex-1 bg-rose-50 dark:bg-rose-950/30 text-rose-600 dark:text-rose-400 border border-rose-100 dark:border-rose-900/50 py-3 rounded-xl font-medium hover:bg-rose-100 dark:hover:bg-rose-900/50 transition-colors shadow-sm"
                            >
                                Удалить копилку
                            </button>
                        )}
                    </div>
                </form>
            </Modal>

            {/* Transaction Modal */}
            <Modal
                isOpen={isTransModalOpen}
                onClose={() => setIsTransModalOpen(false)}
                title={transType === 'deposit' ? 'Пополнить копилку' : 'Снять средства'}
            >
                <form onSubmit={handleTransaction} className="space-y-4">
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-700 dark:text-[var(--color-text-muted)]">Сумма</label>
                        <input
                            type="number" required
                            value={transAmount}
                            onChange={e => setTransAmount(e.target.value)}
                            className="w-full p-2 border border-gray-300 dark:border-[var(--color-border-strong)] rounded-lg focus:ring-2 focus:ring-[var(--color-primary)]/20 focus:border-[var(--color-primary)] outline-none bg-[var(--color-surface)] dark:bg-[var(--color-surface-soft)] text-slate-900 dark:text-[var(--color-text-main)] transition-colors"
                            placeholder="0.00"
                            autoFocus
                        />
                    </div>
                    <button
                        type="submit"
                        className={classNames(
                            "w-full text-white py-3 rounded-xl font-medium mt-4 shadow-sm transition-all",
                            transType === 'deposit' ? 'bg-emerald-500 hover:bg-emerald-600 hover:shadow-md dark:bg-emerald-600 dark:hover:bg-emerald-700' : 'bg-rose-500 hover:bg-rose-600 hover:shadow-md dark:bg-rose-600 dark:hover:bg-rose-700'
                        )}
                    >
                        {transType === 'deposit' ? 'Внести в копилку' : 'Снять средства'}
                    </button>
                </form>
            </Modal>

            <Modal
                isOpen={isDeleteConfirmOpen}
                onClose={() => setIsDeleteConfirmOpen(false)}
                title="Удаление копилки"
            >
                <div className="space-y-4">
                    <p className="text-gray-600 dark:text-[var(--color-text-muted)]">
                        Удалить копилку <strong className="text-slate-900 dark:text-[var(--color-text-main)]">{editGoalName}</strong>?
                    </p>
                    <div className="rounded-xl border border-rose-200 dark:border-rose-900/50 bg-rose-50 dark:bg-rose-950/30 px-4 py-3 text-sm text-rose-700 dark:text-rose-300 transition-colors">
                        Это действие удалит цель накопления и связанные операции. Отменить удаление из интерфейса будет нельзя.
                    </div>
                    <div className="flex gap-3">
                        <button
                            type="button"
                            onClick={handleDeleteGoal}
                            className="flex-1 bg-rose-600 text-white py-3 rounded-xl font-medium hover:bg-rose-700 transition-colors"
                        >
                            Удалить
                        </button>
                        <button
                            type="button"
                            onClick={() => setIsDeleteConfirmOpen(false)}
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

export default Savings;
