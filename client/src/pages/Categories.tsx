import React, { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, Check, X } from 'lucide-react';
import api, { getCategories, getIncomeSources } from '../api';
import type { Category, IncomeSource } from '../api';
import Modal from '../components/Modal';

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

    useEffect(() => {
        fetchData();
    }, [activeTab]);

    const fetchData = async () => {
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
    };

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
    const itemName = activeTab === 'expenses' ? 'категорию' : 'источник дохода';
    const listEmpty = activeTab === 'expenses' ? 'Список категорий пуст' : 'Список источников дохода пуст';
    const modalTitle = activeTab === 'expenses' ? 'Добавить категорию' : 'Добавить источник дохода';
    const buttonTitle = activeTab === 'expenses' ? 'Новая категория' : 'Новый источник';

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            <div className="flex items-center justify-between">
                <h2 className="text-2xl font-bold text-gray-800">Управление категориями</h2>
                <button
                    onClick={() => setIsModalOpen(true)}
                    className="flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-lg hover:bg-blue-600 transition-colors shadow-sm"
                >
                    <Plus className="w-5 h-5" />
                    {buttonTitle}
                </button>
            </div>

            {/* Tabs */}
            <div className="flex gap-2 border-b border-gray-200">
                <button
                    onClick={() => setActiveTab('expenses')}
                    className={`px-4 py-2 font-medium transition-colors border-b-2 -mb-px ${
                        activeTab === 'expenses'
                            ? 'text-blue-600 border-blue-600'
                            : 'text-gray-500 border-transparent hover:text-gray-700'
                    }`}
                >
                    Категории расходов
                </button>
                <button
                    onClick={() => setActiveTab('income')}
                    className={`px-4 py-2 font-medium transition-colors border-b-2 -mb-px ${
                        activeTab === 'income'
                            ? 'text-blue-600 border-blue-600'
                            : 'text-gray-500 border-transparent hover:text-gray-700'
                    }`}
                >
                    Источники дохода
                </button>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                <ul className="divide-y divide-gray-100">
                    {currentList.length === 0 && (
                        <li className="p-8 text-center text-gray-400">{listEmpty}</li>
                    )}
                    {currentList.map((item) => (
                        <li key={item.id} className="p-4 hover:bg-gray-50 flex items-center justify-between group">
                            {editingId === item.id ? (
                                <div className="flex items-center gap-2 w-full">
                                    <input
                                        type="text"
                                        value={editName}
                                        onChange={(e) => setEditName(e.target.value)}
                                        className="flex-1 p-2 border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 outline-none"
                                        autoFocus
                                    />
                                    <button onClick={saveEdit} className="p-2 text-green-600 hover:bg-green-50 rounded">
                                        <Check className="w-5 h-5" />
                                    </button>
                                    <button onClick={cancelEdit} className="p-2 text-red-500 hover:bg-red-50 rounded">
                                        <X className="w-5 h-5" />
                                    </button>
                                </div>
                            ) : (
                                <>
                                    <span className="font-medium text-gray-700">{item.name}</span>
                                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button onClick={() => startEdit(item)} className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors">
                                            <Edit2 className="w-4 h-4" />
                                        </button>
                                        <button onClick={() => handleDelete(item.id)} className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors">
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
                        <label className="text-sm font-medium text-gray-700">Название</label>
                        <input
                            type="text"
                            required
                            value={newName}
                            onChange={(e) => setNewName(e.target.value)}
                            className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                            placeholder="Введите название..."
                        />
                    </div>
                    <button
                        type="submit"
                        className="w-full bg-primary text-white py-3 rounded-lg font-medium hover:bg-blue-600 transition-colors"
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
                    <p className="text-gray-600">Вы уверены, что хотите удалить {itemName}?</p>
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
