import React, { useState } from 'react';
import { Settings as SettingsIcon, Save, RotateCcw, Key, User, ShieldAlert, Loader2 } from 'lucide-react';
import { updateUsername, updatePassword, restoreBackup, createManualBackup } from '../api';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';

const Settings: React.FC = () => {
    const { user, login, updateUser } = useAuth(); // Need login or some way to refresh user
    const [username, setUsername] = useState(user?.username || '');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    
    const [modalType, setModalType] = useState<'restore' | 'rename' | 'password' | null>(null);
    const [statusMessage, setStatusMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);
    const [modalError, setModalError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);

    const closeModal = () => {
        if (!isLoading) {
            setModalType(null);
            setModalError(null);
        }
    };

    const openModal = (type: 'restore' | 'rename' | 'password') => {
        setModalError(null);
        setModalType(type);
    };

    const handleBackup = async () => {
        setIsLoading(true);
        setStatusMessage(null);
        try {
            await createManualBackup();
            setStatusMessage({ type: 'success', text: 'Резервная копия успешно создана.' });
        } catch (e) {
            setStatusMessage({ type: 'error', text: 'Ошибка создания резервной копии.' });
            console.error(e);
        } finally {
            setIsLoading(false);
        }
    };

    const handleRestore = async () => {
        setIsLoading(true);
        setModalError(null);
        try {
            await restoreBackup();
            setModalType(null);
            setStatusMessage({ type: 'success', text: 'Данные успешно восстановлены. Перезагрузка...' });
            
            // Reload page to reflect data changes
            setTimeout(() => window.location.reload(), 1000);
        } catch (e: any) {
            const msg = e.response?.data?.error || 'Ошибка восстановления данных.';
            setModalError(msg);
            console.error(e);
            setIsLoading(false);
        }
    };

    const handleRename = async () => {
        setIsLoading(true);
        setModalError(null);
        try {
            await updateUsername(username);
            updateUser({ username });
            setStatusMessage({ type: 'success', text: 'Имя пользователя успешно обновлено.' });
            setModalType(null);
        } catch (e: any) {
            const msg = e.response?.data?.error || 'Ошибка обновления имени.';
            setModalError(msg);
        } finally {
            setIsLoading(false);
        }
    };

    const handlePasswordChange = async () => {
        if (newPassword !== confirmPassword) {
            setModalError('Пароли не совпадают.');
            return;
        }
        setIsLoading(true);
        setModalError(null);
        try {
            await updatePassword(newPassword);
            setStatusMessage({ type: 'success', text: 'Пароль успешно изменен.' });
            setModalType(null);
            setNewPassword('');
            setConfirmPassword('');
        } catch (e: any) {
            const msg = e.response?.data?.error || 'Ошибка смены пароля.';
            setModalError(msg);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            <div className="flex items-center gap-3 mb-8">
                <div className="bg-white p-2 rounded-lg shadow-sm">
                    <SettingsIcon className="w-6 h-6 text-gray-700" />
                </div>
                <h1 className="text-2xl font-bold text-gray-800">Настройки</h1>
            </div>

            {statusMessage && (
                <div className={`p-4 rounded-lg mb-6 ${statusMessage.type === 'success' ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>
                    {statusMessage.text}
                </div>
            )}

            <div className="grid grid-cols-1 gap-6">
                {/* Profile Settings */}
                <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                    <div className="p-6 border-b border-gray-100 bg-gray-50/50">
                        <h2 className="text-lg font-bold text-gray-800 flex items-center gap-2">
                            <User className="w-5 h-5 text-blue-500" />
                            Профиль
                        </h2>
                    </div>
                    
                    <div className="p-6 space-y-6">
                        <div className="grid md:grid-cols-2 gap-6">
                            <div className="space-y-2">
                                <label className="text-sm font-medium text-gray-700">Имя пользователя</label>
                                <div className="flex gap-2">
                                    <input 
                                        type="text" 
                                        value={username} 
                                        onChange={(e) => setUsername(e.target.value)}
                                        className="flex-1 p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                                        disabled={isLoading}
                                    />
                                    <button 
                                        onClick={() => openModal('rename')}
                                        disabled={username === user?.username || isLoading}
                                        className="bg-blue-50 text-blue-600 px-4 py-2 rounded-lg font-medium hover:bg-blue-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                                    >
                                        Сохранить
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Password Settings */}
                <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                    <div className="p-6 border-b border-gray-100 bg-gray-50/50">
                        <h2 className="text-lg font-bold text-gray-800 flex items-center gap-2">
                            <Key className="w-5 h-5 text-amber-500" />
                            Безопасность
                        </h2>
                    </div>
                    
                    <div className="p-6">
                        <div className="grid md:grid-cols-2 gap-6">
                            <div className="space-y-4">
                                <div className="space-y-2">
                                    <label className="text-sm font-medium text-gray-700">Новый пароль</label>
                                    <input 
                                        type="password" 
                                        value={newPassword}
                                        onChange={(e) => setNewPassword(e.target.value)}
                                        className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                                        placeholder="••••••••"
                                        disabled={isLoading}
                                    />
                                </div>
                                <div className="space-y-2">
                                    <label className="text-sm font-medium text-gray-700">Подтвердите пароль</label>
                                    <input 
                                        type="password" 
                                        value={confirmPassword}
                                        onChange={(e) => setConfirmPassword(e.target.value)}
                                        className="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                                        placeholder="••••••••"
                                        disabled={isLoading}
                                    />
                                </div>
                                <button 
                                    onClick={() => openModal('password')}
                                    disabled={!newPassword || !confirmPassword || isLoading}
                                    className="bg-amber-50 text-amber-600 px-4 py-2 rounded-lg font-medium hover:bg-amber-100 disabled:opacity-50 disabled:cursor-not-allowed transition-colors w-full md:w-auto"
                                >
                                    Сменить пароль
                                </button>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Data Management */}
                <div className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                    <div className="p-6 border-b border-gray-100 bg-gray-50/50">
                        <h2 className="text-lg font-bold text-gray-800 flex items-center gap-2">
                            <ShieldAlert className="w-5 h-5 text-red-500" />
                            Управление данными
                        </h2>
                    </div>
                    
                    <div className="p-6">
                        <div className="flex items-start gap-4 bg-red-50 p-4 rounded-xl border border-red-100">
                            <RotateCcw className="w-6 h-6 text-red-600 flex-shrink-0 mt-1" />
                            <div>
                                <h3 className="font-bold text-red-800">Резервное копирование</h3>
                                <p className="text-sm text-red-700 mt-1 mb-3">
                                    Создайте точку восстановления текущих данных или откатитесь к предыдущей сохраненной версии (созданной автоматически при входе или вручную).
                                </p>
                                <div className="flex flex-wrap gap-3">
                                    <button 
                                        onClick={handleBackup}
                                        disabled={isLoading}
                                        className="flex items-center bg-white text-blue-600 border border-blue-200 px-4 py-2 rounded-lg font-medium hover:bg-blue-50 transition-colors shadow-sm disabled:opacity-50"
                                    >
                                        <Save className="w-4 h-4 mr-2" />
                                        {isLoading ? 'Сохранение...' : 'Создать бэкап сейчас'}
                                    </button>
                                    <button 
                                        onClick={() => openModal('restore')}
                                        disabled={isLoading}
                                        className="bg-white text-red-600 border border-red-200 px-4 py-2 rounded-lg font-medium hover:bg-red-50 transition-colors shadow-sm disabled:opacity-50"
                                    >
                                        Откатить к последнему бэкапу
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Modals */}
            <Modal
                isOpen={modalType === 'restore'}
                onClose={closeModal}
                title="Подтверждение отката"
            >
                <div className="space-y-4">
                    <p className="text-gray-600">
                        Вы уверены, что хотите откатить данные?
                        Текущее состояние будет заменено данными из <strong>последней резервной копии</strong>.
                        Все несохраненные изменения будут потеряны.
                    </p>
                    
                    {modalError && (
                        <div className="p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
                            {modalError}
                        </div>
                    )}

                    <div className="flex gap-3">
                        <button
                            onClick={handleRestore}
                            disabled={isLoading}
                            className="flex-1 bg-red-600 text-white py-2 rounded-lg font-medium hover:bg-red-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                            {isLoading ? 'Восстановление...' : 'Да, откатить'}
                        </button>
                        <button
                            onClick={closeModal}
                            disabled={isLoading}
                            className="flex-1 bg-gray-100 text-gray-700 py-2 rounded-lg font-medium hover:bg-gray-200 transition-colors disabled:opacity-50"
                        >
                            Отмена
                        </button>
                    </div>
                </div>
            </Modal>

            <Modal
                isOpen={modalType === 'rename'}
                onClose={closeModal}
                title="Смена имени"
            >
                <div className="space-y-4">
                    <p className="text-gray-600">
                        Изменить имя пользователя на <strong>{username}</strong>?
                    </p>

                    {modalError && (
                        <div className="p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
                            {modalError}
                        </div>
                    )}

                    <div className="flex gap-3">
                        <button
                            onClick={handleRename}
                            disabled={isLoading}
                            className="flex-1 bg-blue-600 text-white py-2 rounded-lg font-medium hover:bg-blue-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                            Сохранить
                        </button>
                        <button
                            onClick={closeModal}
                            disabled={isLoading}
                            className="flex-1 bg-gray-100 text-gray-700 py-2 rounded-lg font-medium hover:bg-gray-200 transition-colors disabled:opacity-50"
                        >
                            Отмена
                        </button>
                    </div>
                </div>
            </Modal>

            <Modal
                isOpen={modalType === 'password'}
                onClose={closeModal}
                title="Смена пароля"
            >
                <div className="space-y-4">
                    <p className="text-gray-600">
                        Вы уверены, что хотите изменить пароль?
                    </p>

                    {modalError && (
                        <div className="p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
                            {modalError}
                        </div>
                    )}

                    <div className="flex gap-3">
                        <button
                            onClick={handlePasswordChange}
                            disabled={isLoading}
                            className="flex-1 bg-amber-500 text-white py-2 rounded-lg font-medium hover:bg-amber-600 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                            Изменить
                        </button>
                        <button
                            onClick={closeModal}
                            disabled={isLoading}
                            className="flex-1 bg-gray-100 text-gray-700 py-2 rounded-lg font-medium hover:bg-gray-200 transition-colors disabled:opacity-50"
                        >
                            Отмена
                        </button>
                    </div>
                </div>
            </Modal>
        </div>
    );
};

export default Settings;
