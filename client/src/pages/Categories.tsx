import React, { useEffect, useState, useCallback } from 'react';
import { Plus, Edit2, Trash2, Check, X } from 'lucide-react';
import api, { getCategories, getIncomeSources } from '../api';
import type { Category, IncomeSource } from '../api';
import Modal from '../components/Modal';
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

    const fetchData = useCallback(async () => {
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
        }
    };

    const startEdit = (item: Category | IncomeSource) => {
        setEditingId(item.id);
        setEditName(item.name);
    };

    const saveEdit = async () => {
        if (!editingId) return;
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
        }
    };

    const currentList = activeTab === 'expenses' ? categories : incomeSources;
    const itemName = activeTab === 'expenses' ? 'эту категорию' : 'этот источник дохода';
    const listEmpty = activeTab === 'expenses' ? 'Список категорий пуст' : 'Список источников дохода пуст';
    const modalTitle = activeTab === 'expenses' ? 'Добавить категорию' : 'Добавить источник дохода';
    const buttonTitle = activeTab === 'expenses' ? 'Новая категория' : 'Новый источник';

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            <div className="flex items-center justify-between mb-2">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 tracking-tight">Категории и источники</h2>
                    <p className="text-sm text-slate-500 mt-0.5">Настройка статей доходов и расходов</p>
                </div>
                <button
                    onClick={() => setIsModalOpen(true)}
                    className="flex items-center gap-2 bg-primary text-white px-5 py-2.5 rounded-xl hover:bg-blue-600 transition-all shadow-sm hover:shadow-md font-medium"
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
                            ? 'bg-white text-blue-600 shadow-sm border border-slate-200/60'
                            : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
                    }`}
                >
                    Категории расходов
                </button>
                <button
                    onClick={() => setActiveTab('income')}
                    className={`px-5 py-2.5 rounded-xl font-medium transition-all ${
                        activeTab === 'income'
                            ? 'bg-white text-blue-600 shadow-sm border border-slate-200/60'
                            : 'text-slate-500 hover:text-slate-700 hover:bg-slate-50'
                    }`}
                >
                    Источники дохода
                </button>
            </div>

            <div className="bg-white/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 overflow-hidden">
                <ul className="divide-y divide-slate-100/60">
                    {currentList.length === 0 && (
                        <li className="p-8 text-center text-gray-400">{listEmpty}</li>
                    )}
                    {currentList.map((item) => (
                        <li key={item.id} className="p-4 px-6 hover:bg-slate-50/50 flex items-center justify-between group transition-colors">
                            {editingId === item.id ? (
                                <div className="flex items-center gap-3 w-full">
                                    <input
                                        type="text"
                                        value={editName}
                                        onChange={(e) => setEditName(e.target.value)}
                                        className="flex-1 p-2.5 border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition-all"
                                        autoFocus
                                    />
                                    <button onClick={saveEdit} className="p-2.5 text-emerald-600 bg-emerald-50 hover:bg-emerald-100 rounded-xl transition-colors">
                                        <Check className="w-5 h-5" />
                                    </button>
                                    <button onClick={cancelEdit} className="p-2.5 text-slate-500 bg-slate-100 hover:bg-slate-200 rounded-xl transition-colors">
                                        <X className="w-5 h-5" />
                                    </button>
                                </div>
                            ) : (
                                <>
                                    <span className="font-medium text-slate-700">{item.name}</span>
                                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button onClick={() => startEdit(item)} className="p-2 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition-colors">
                                            <Edit2 className="w-4 h-4" />
                                        </button>
                                        <button onClick={() => handleDelete(item.id)} className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors">
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
                        <label className="text-sm font-medium text-slate-700">Название</label>
                        <input
                            type="text"
                            required
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            className="w-full p-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition-all shadow-sm"
                            placeholder="Введите название..."
                        />
                    </div>
                    <button
                        type="submit"
                        className="w-full bg-primary text-white py-3 rounded-xl font-medium hover:bg-blue-600 transition-all shadow-sm hover:shadow-md mt-4"
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
                    <p className="text-gray-600">Уверены, что хотите удалить {itemName}?</p>
                    <div className="flex gap-3">
                        <button
                            onClick={confirmDelete}
                            className="flex-1 bg-red-500 text-white py-2 rounded-lg font-medium hover:bg-red-600 transition-colors"
                        >
                            Удалить
                        </button>
                        <button
                            onClick={() => setPendingDelete(null)}
                            className="flex-1 bg-gray-100 text-gray-700 py-2 rounded-lg font-medium hover:bg-gray-200 transition-colors"
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
