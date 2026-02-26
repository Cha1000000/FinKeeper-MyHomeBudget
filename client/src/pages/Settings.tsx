import React, { useState } from 'react';
import { Settings as SettingsIcon, Save, RotateCcw, Key, User, ShieldAlert, Loader2, Layout as LayoutIcon, LogOut } from 'lucide-react';
import { updateUsername, updatePassword, restoreBackup, createManualBackup } from '../api';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';

const Settings: React.FC = () => {
    const { user, updateUser, uiSettings, updateUiSettings, logout } = useAuth(); // Need login or some way to refresh user
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
        } catch (error: unknown) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const e = error as any;
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
        } catch (error: unknown) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const e = error as any;
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
        } catch (error: unknown) {
            // eslint-disable-next-line @typescript-eslint/no-explicit-any
            const e = error as any;
            const msg = e.response?.data?.error || 'Ошибка смены пароля.';
            setModalError(msg);
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="space-y-6 max-w-4xl mx-auto">
            <div className="flex items-center gap-4 mb-10">
                <div className="bg-white/80 p-3 rounded-2xl shadow-sm border border-slate-100/50 backdrop-blur-md text-slate-600">
                    <SettingsIcon className="w-6 h-6" />
                </div>
                <div>
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Настройки</h1>
                    <p className="text-sm text-slate-500 mt-0.5">Приложение и ваш профиль</p>
                </div>
            </div>

            {statusMessage && (
                <div className={`p-4 rounded-lg mb-6 ${statusMessage.type === 'success' ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-red-50 text-red-700 border border-red-200'}`}>
                    {statusMessage.text}
                </div>
            )}

            <div className="grid grid-cols-1 gap-6">
                {/* Profile Settings */}
                <div className="bg-white/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 overflow-hidden">
                    <div className="p-6 border-b border-slate-100/60 bg-slate-50/40">
                        <h2 className="text-lg font-bold text-slate-800 flex items-center gap-3">
                            <div className="p-2 bg-blue-50 text-blue-600 rounded-xl">
                                <User className="w-5 h-5" />
                            </div>
                            Профиль
                        </h2>
                    </div>
                    
                    <div className="p-6 md:p-8">
                        <div className="grid md:grid-cols-[1fr_auto] gap-6 md:gap-12 items-end">
                            <div className="space-y-3">
                                <label htmlFor="username-input" className="text-sm font-medium text-slate-700">Имя пользователя</label>
                                <input 
                                    id="username-input"
                                    type="text" 
                                    value={username} 
                                    onChange={(e) => setUsername(e.target.value)}
                                    className="w-full p-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition-all shadow-sm"
                                    disabled={isLoading}
                                    placeholder="Ваше имя"
                                />
                            </div>
                            <div className="flex flex-wrap gap-3">
                                <button 
                                    onClick={() => openModal('rename')}
                                    disabled={username === user?.username || isLoading}
                                    className="bg-primary text-white px-5 py-3 rounded-xl font-medium hover:bg-blue-600 hover:shadow-md disabled:opacity-50 disabled:hover:shadow-none transition-all w-full md:w-auto shadow-sm"
                                >
                                    Сохранить имя
                                </button>
                                <button 
                                    onClick={logout}
                                    className="bg-rose-50 text-rose-600 px-5 py-3 rounded-xl font-medium hover:bg-rose-100 transition-colors w-full md:w-auto flex items-center justify-center gap-2 border border-rose-100/50"
                                >
                                    <LogOut className="w-4 h-4" />
                                    Выйти
                                </button>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Password Settings */}
                <div className="bg-white/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 overflow-hidden">
                    <div className="p-6 border-b border-slate-100/60 bg-slate-50/40">
                        <h2 className="text-lg font-bold text-slate-800 flex items-center gap-3">
                            <div className="p-2 bg-amber-50 text-amber-500 rounded-xl">
                                <Key className="w-5 h-5" />
                            </div>
                            Безопасность
                        </h2>
                    </div>
                    
                    <div className="p-6 md:p-8">
                        <div className="grid md:grid-cols-[1fr_1fr_auto] gap-6 items-end">
                            <div className="space-y-3">
                                <label htmlFor="new-password" className="text-sm font-medium text-slate-700">Новый пароль</label>
                                <input 
                                    id="new-password"
                                    type="password" 
                                    value={newPassword}
                                    onChange={(e) => setNewPassword(e.target.value)}
                                    className="w-full p-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 outline-none transition-all shadow-sm font-sans"
                                    placeholder="••••••••"
                                    disabled={isLoading}
                                />
                            </div>
                            <div className="space-y-3">
                                <label htmlFor="confirm-password" className="text-sm font-medium text-slate-700">Подтвердите пароль</label>
                                <input 
                                    id="confirm-password"
                                    type="password" 
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    className="w-full p-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 outline-none transition-all shadow-sm font-sans"
                                    placeholder="••••••••"
                                    disabled={isLoading}
                                />
                            </div>
                            <button 
                                onClick={() => openModal('password')}
                                disabled={!newPassword || !confirmPassword || isLoading}
                                className="bg-amber-500 text-white px-5 py-3 rounded-xl font-medium hover:bg-amber-600 hover:shadow-md disabled:opacity-50 disabled:hover:shadow-none transition-all w-full md:w-auto shadow-sm"
                            >
                                Сменить
                            </button>
                        </div>
                    </div>
                </div>

                {/* UI Settings */}
                <div className="bg-white/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 overflow-hidden">
                    <div className="p-6 border-b border-slate-100/60 bg-slate-50/40">
                        <h2 className="text-lg font-bold text-slate-800 flex items-center gap-3">
                            <div className="p-2 bg-indigo-50 text-indigo-500 rounded-xl">
                                <LayoutIcon className="w-5 h-5" />
                            </div>
                            Интерфейс
                        </h2>
                    </div>
                    
                    <div className="p-6 md:p-8">
                        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                            <div>
                                <h3 className="font-semibold text-slate-800">Автоматически сворачивать боковую панель</h3>
                                <p className="text-sm text-slate-500 mt-1">
                                    Сворачивать меню при клике на основную область (удобно для небольших экранов)
                                </p>
                            </div>
                            <label className="relative inline-flex items-center cursor-pointer shrink-0">
                                <input 
                                    type="checkbox" 
                                    className="sr-only peer"
                                    checked={uiSettings.autoCollapseSidebar}
                                    onChange={(e) => updateUiSettings({ autoCollapseSidebar: e.target.checked })}
                                    aria-label="Автоматически сворачивать боковую панель"
                                    title="Автоматически сворачивать боковую панель"
                                />
                                <div className="w-12 h-6 bg-slate-200 peer-focus:outline-none peer-focus:ring-4 peer-focus:ring-indigo-300/30 rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-indigo-500"></div>
                            </label>
                        </div>
                    </div>
                </div>

                {/* Data Management */}
                <div className="bg-white/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 overflow-hidden">
                    <div className="p-6 border-b border-slate-100/60 bg-slate-50/40">
                        <h2 className="text-lg font-bold text-slate-800 flex items-center gap-3">
                            <div className="p-2 bg-blue-50 text-blue-500 rounded-xl">
                                <ShieldAlert className="w-5 h-5" />
                            </div>
                            Управление данными
                        </h2>
                    </div>
                    
                    <div className="p-6 md:p-8">
                        <div className="flex flex-col md:flex-row items-start gap-5 bg-slate-50 p-6 rounded-2xl border border-slate-100">
                            <div className="p-3 bg-white shadow-sm border border-slate-100 rounded-xl text-slate-500 shrink-0">
                                <RotateCcw className="w-6 h-6" />
                            </div>
                            <div className="flex-1">
                                <h3 className="font-bold text-slate-800">Резервное копирование</h3>
                                <p className="text-sm text-slate-600 mt-1 mb-5 leading-relaxed">
                                    Создайте точку восстановления текущих данных или откатитесь к предыдущей сохраненной версии. Полезно перед экспериментами с категориями или крупным импортом.
                                </p>
                                <div className="flex flex-wrap gap-3">
                                    <button 
                                        onClick={handleBackup}
                                        disabled={isLoading}
                                        className="flex items-center bg-white text-slate-700 border border-slate-200 px-5 py-2.5 rounded-xl font-medium hover:bg-slate-50 hover:shadow-sm transition-all shadow-sm disabled:opacity-50"
                                    >
                                        <Save className="w-4 h-4 mr-2 text-slate-500" />
                                        {isLoading ? 'Сохранение...' : 'Создать бэкап сейчас'}
                                    </button>
                                    <button 
                                        onClick={() => openModal('restore')}
                                        disabled={isLoading}
                                        className="bg-white text-rose-600 border border-rose-200 px-5 py-2.5 rounded-xl font-medium hover:bg-rose-50 hover:shadow-sm transition-all shadow-sm disabled:opacity-50"
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
