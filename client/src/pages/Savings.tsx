import React, { useEffect, useState } from 'react';
import { Plus, PiggyBank, ArrowDown, ArrowUp } from 'lucide-react';
import classNames from 'classnames';
import { getSavingsGoals, addSavingsGoal, updateSavingsGoal, deleteSavingsGoal, addSavingsTransaction, ensureMonth } from '../api';
import type { SavingsGoal } from '../api';
import { formatCurrency } from '../utils';
import Modal from '../components/Modal';

const Savings: React.FC = () => {
    const [goals, setGoals] = useState<SavingsGoal[]>([]);
    const [isGoalModalOpen, setIsGoalModalOpen] = useState(false);
    const [isTransModalOpen, setIsTransModalOpen] = useState(false);
    
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

    const loadData = async () => {
        try {
            const res = await getSavingsGoals();
            setGoals(res.data);
        } catch (e) { console.error(e); }
    };

    useEffect(() => { 
        // eslint-disable-next-line react-hooks/set-state-in-effect
        loadData(); 
    }, []);

    const handleCreateGoal = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            await addSavingsGoal({ name: newGoalName, target_amount: parseFloat(newGoalTarget) || 0 });
            setIsGoalModalOpen(false);
            setNewGoalName('');
            setNewGoalTarget('');
            loadData();
            window.dispatchEvent(new Event('savingsUpdated'));
        } catch (e) { console.error(e); }
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
            setEditingGoalId(null);
            setEditGoalName('');
            setEditGoalTarget('');
            setEditGoalCurrent('');
            loadData();
            window.dispatchEvent(new Event('savingsUpdated'));
        } catch (e) { console.error(e); }
    };

    const handleDeleteGoal = async () => {
        if (!editingGoalId) return;
        if (!confirm('Вы уверены, что хотите удалить эту копилку?')) return;
        try {
            await deleteSavingsGoal(editingGoalId);
            setIsGoalModalOpen(false);
            setEditingGoalId(null);
            setEditGoalName('');
            setEditGoalTarget('');
            setEditGoalCurrent('');
            loadData();
            window.dispatchEvent(new Event('savingsUpdated'));
        } catch (e) { console.error(e); }
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
        } catch (e) { console.error(e); }
    };

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <h2 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
                    <PiggyBank className="w-8 h-8 text-pink-500" />
                    Копилки
                </h2>
                <button
                    onClick={() => setIsGoalModalOpen(true)}
                    className="flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-lg hover:bg-blue-600 transition-colors shadow-sm"
                >
                    <Plus className="w-5 h-5" />
                    Новая цель
                </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6">
                {goals.map((goal) => {
                    const progress = goal.target_amount > 0
                        ? Math.min((goal.current_amount / goal.target_amount) * 100, 100)
                        : 0;
                    
                    return (
                        <div key={goal.id} className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 flex flex-col justify-between h-full cursor-pointer hover:shadow-md transition-shadow" onClick={() => openEditGoal(goal)}>
                            <div className="mb-4">
                                <div className="flex justify-between items-start mb-2">
                                    <h3 className="text-lg font-bold text-gray-800">{goal.name}</h3>
                                    <div className="bg-blue-50 text-blue-700 px-2 py-1 rounded text-xs font-bold">
                                        {progress.toFixed(0)}%
                                    </div>
                                </div>
                                <p className="text-3xl font-bold text-gray-900 mb-1">{formatCurrency(goal.current_amount)}</p>
                                <p className="text-sm text-gray-400">из {formatCurrency(goal.target_amount)}</p>
                            </div>

                            <div className="space-y-4">
                                <div className="w-full bg-gray-100 rounded-full h-2.5 overflow-hidden">
                                    <div
                                        className="bg-primary h-2.5 rounded-full transition-all duration-500"
                                        style={{ width: `${progress}%` }}
                                    ></div>
                                </div>

                                <div className="grid grid-cols-2 gap-3">
                                    <button
                                        onClick={(e) => { e.stopPropagation(); openTransaction(goal.id, 'deposit'); }}
                                        className="flex items-center justify-center gap-1 py-2 bg-emerald-50 text-emerald-600 hover:bg-emerald-100 rounded-lg font-medium transition-colors text-sm"
                                    >
                                        <ArrowUp className="w-4 h-4" /> Пополнить
                                    </button>
                                    <button
                                        onClick={(e) => { e.stopPropagation(); openTransaction(goal.id, 'withdraw'); }}
                                        className="flex items-center justify-center gap-1 py-2 bg-red-50 text-red-600 hover:bg-red-100 rounded-lg font-medium transition-colors text-sm"
                                    >
                                        <ArrowDown className="w-4 h-4" /> Снять
                                    </button>
                                </div>
                            </div>
                        </div>
                    );
                })}
            </div>

            {/* Create/Edit Goal Modal */}
            <Modal
                isOpen={isGoalModalOpen}
                onClose={() => {
                    setIsGoalModalOpen(false);
                    setEditingGoalId(null);
                    setEditGoalName('');
                    setEditGoalTarget('');
                    setEditGoalCurrent('');
                }}
                title={editingGoalId ? "Редактировать копилку" : "Создать новую копилку"}
            >
                <form onSubmit={editingGoalId ? handleUpdateGoal : handleCreateGoal} className="space-y-4">
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-700">Название цели</label>
                        <input
                            type="text" required
                            value={editingGoalId ? editGoalName : newGoalName}
                            onChange={e => editingGoalId ? setEditGoalName(e.target.value) : setNewGoalName(e.target.value)}
                            className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                            placeholder="Например: На отпуск"
                        />
                    </div>
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-700">Целевая сумма</label>
                        <input
                            type="number" required
                            value={editingGoalId ? editGoalTarget : newGoalTarget}
                            onChange={e => editingGoalId ? setEditGoalTarget(e.target.value) : setNewGoalTarget(e.target.value)}
                            className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                            placeholder="100000"
                        />
                    </div>
                    {editingGoalId && (
                        <div className="space-y-2">
                            <label className="text-sm font-medium text-gray-700">Текущий баланс</label>
                            <input
                                type="number" required
                                value={editGoalCurrent}
                                onChange={e => setEditGoalCurrent(e.target.value)}
                                className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                                placeholder="0"
                            />
                        </div>
                    )}
                    <div className="flex gap-3 mt-4">
                        <button
                            type="submit"
                            className="flex-1 bg-primary text-white py-3 rounded-lg font-medium hover:bg-blue-600 transition-colors"
                        >
                            {editingGoalId ? 'Сохранить' : 'Создать'}
                        </button>
                        {editingGoalId && (
                            <button
                                type="button"
                                onClick={handleDeleteGoal}
                                className="flex-1 bg-red-500 text-white py-3 rounded-lg font-medium hover:bg-red-600 transition-colors"
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
                        <label className="text-sm font-medium text-gray-700">Сумма</label>
                        <input
                            type="number" required
                            value={transAmount}
                            onChange={e => setTransAmount(e.target.value)}
                            className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                            placeholder="0.00"
                            autoFocus
                        />
                    </div>
                    <button
                        type="submit"
                        className={classNames(
                            "w-full text-white py-3 rounded-lg font-medium mt-2",
                            transType === 'deposit' ? 'bg-emerald-500 hover:bg-emerald-600' : 'bg-red-500 hover:bg-red-600'
                        )}
                    >
                        {transType === 'deposit' ? 'Внести' : 'Снять'}
                    </button>
                </form>
            </Modal>
        </div>
    );
};

export default Savings;
