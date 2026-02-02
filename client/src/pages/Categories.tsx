import React, { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, Check, X } from 'lucide-react';
import api, { getCategories } from '../api';
import type { Category } from '../api';
import Modal from '../components/Modal';

const Categories: React.FC = () => {
    const [categories, setCategories] = useState<Category[]>([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [newCategoryName, setNewCategoryName] = useState('');

    // Editing state
    const [editingId, setEditingId] = useState<number | null>(null);
    const [editName, setEditName] = useState('');

    useEffect(() => {
        fetchCategories();
    }, []);

    const fetchCategories = async () => {
        try {
            const res = await getCategories();
            setCategories(res.data);
        } catch (e) {
            console.error(e);
        }
    };

    const handleAdd = async (e: React.FormEvent) => {
        e.preventDefault();
        try {
            await api.post('/categories', { name: newCategoryName });
            setIsModalOpen(false);
            setNewCategoryName('');
            fetchCategories();
        } catch (e) {
            console.error(e);
        }
    };

    const startEdit = (cat: Category) => {
        setEditingId(cat.id);
        setEditName(cat.name);
    };

    const saveEdit = async () => {
        if (!editingId) return;
        try {
            await api.put(`/categories/${editingId}`, { name: editName, is_active: 1 });
            setEditingId(null);
            fetchCategories();
        } catch (e) {
            console.error(e);
        }
    };

    const cancelEdit = () => {
        setEditingId(null);
        setEditName('');
    };

    const handleDelete = async (id: number) => {
        if (!confirm('Вы уверены, что хотите удалить (скрыть) эту категорию?')) return;
        try {
            await api.delete(`/categories/${id}`);
            fetchCategories();
        } catch (e) {
            console.error(e);
        }
    };

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            <div className="flex items-center justify-between">
                <h2 className="text-2xl font-bold text-gray-800">Управление категориями</h2>
                <button
                    onClick={() => setIsModalOpen(true)}
                    className="flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-lg hover:bg-blue-600 transition-colors shadow-sm"
                >
                    <Plus className="w-5 h-5" />
                    Новая категория
                </button>
            </div>

            <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                <ul className="divide-y divide-gray-100">
                    {categories.length === 0 && (
                        <li className="p-8 text-center text-gray-400">Список категорий пуст</li>
                    )}
                    {categories.map((cat) => (
                        <li key={cat.id} className="p-4 hover:bg-gray-50 flex items-center justify-between group">
                            {editingId === cat.id ? (
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
                                    <span className="font-medium text-gray-700">{cat.name}</span>
                                    <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                                        <button onClick={() => startEdit(cat)} className="p-2 text-gray-400 hover:text-blue-600 hover:bg-blue-50 rounded transition-colors">
                                            <Edit2 className="w-4 h-4" />
                                        </button>
                                        <button onClick={() => handleDelete(cat.id)} className="p-2 text-gray-400 hover:text-red-600 hover:bg-red-50 rounded transition-colors">
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
                title="Добавить категорию"
            >
                <form onSubmit={handleAdd} className="space-y-4">
                    <div className="space-y-2">
                        <label className="text-sm font-medium text-gray-700">Название</label>
                        <input
                            type="text"
                            required
                            value={newCategoryName}
                            onChange={(e) => setNewCategoryName(e.target.value)}
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
        </div>
    );
};

export default Categories;
