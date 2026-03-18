import React, { useEffect, useState } from 'react';
import { Settings as SettingsIcon, Save, RotateCcw, Key, User, ShieldAlert, Loader2, Layout as LayoutIcon, LogOut } from 'lucide-react';
import { clearUserEmail, createManualBackup, getBackupEntries, requestEmailVerification, restoreBackup, updatePassword, updateUserEmail, updateUsername } from '../api';
import type { BackupEntry } from '../api';
import { useAuth } from '../context/AuthContext';
import Modal from '../components/Modal';

const BACKUP_RESTORE_CONFIRMATION_TEXT = 'ВОССТАНОВИТЬ';

const Settings: React.FC = () => {
    const { user, updateUser, uiSettings, updateUiSettings, logout } = useAuth(); // Need login or some way to refresh user
    const [username, setUsername] = useState(user?.username || '');
    const [email, setEmail] = useState(user?.email || user?.recoveryEmail || '');
    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [emailDebugToken, setEmailDebugToken] = useState<{ token: string; expiresAt: string } | null>(null);
    const [backups, setBackups] = useState<BackupEntry[]>([]);
    const [selectedBackupId, setSelectedBackupId] = useState<number | null>(null);
    const [restoreConfirmationText, setRestoreConfirmationText] = useState('');
    const [isBackupsLoading, setIsBackupsLoading] = useState(false);
    
    const [modalType, setModalType] = useState<'restore' | 'rename' | 'password' | 'logout' | null>(null);
    const [statusMessage, setStatusMessage] = useState<{type: 'success' | 'error', text: string} | null>(null);
    const [modalError, setModalError] = useState<string | null>(null);
    const [isLoading, setIsLoading] = useState(false);
    const normalizedUsername = username.trim();
    const usernameTooLong = normalizedUsername.length > 64;
    const usernameChanged = normalizedUsername.length > 0 && normalizedUsername !== (user?.username ?? '');
    const passwordTooShort = newPassword.length > 0 && newPassword.length < 6;
    const passwordsMismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;
    const passwordReadyToSubmit = currentPassword.length > 0 && newPassword.length > 0 && confirmPassword.length > 0 && !passwordTooShort && !passwordsMismatch;
    const normalizedEmail = email.trim().toLowerCase();
    const providerDisplayNames: Record<string, string> = {
        google: 'Google',
        yandex: 'Яндекс',
        mailru: 'Mail.ru',
    };
    const currentDisplayedRecoveryEmail = (user?.email || user?.recoveryEmail || '').trim().toLowerCase();
    const emailChanged = normalizedEmail !== currentDisplayedRecoveryEmail;
    const emailLooksValid = normalizedEmail.length === 0 || /\S+@\S+\.\S+/.test(normalizedEmail);
    const linkedAuthProviders = user?.linkedAuthProviders ?? [];
    const hasManualRecoveryEmail = Boolean(user?.email);
    const activeRecoveryProviderLabel = user?.recoveryEmailProvider
        ? (providerDisplayNames[user.recoveryEmailProvider] || user.recoveryEmailProvider)
        : null;

    useEffect(() => {
        setUsername(user?.username || '');
    }, [user?.username]);

    useEffect(() => {
        setEmail(user?.email || user?.recoveryEmail || '');
    }, [user?.email, user?.recoveryEmail]);

    const loadBackups = async (options?: { keepSelection?: boolean }) => {
        setIsBackupsLoading(true);
        try {
            const { data } = await getBackupEntries();
            setBackups(data.backups);
            setSelectedBackupId((currentSelected) => {
                if (options?.keepSelection && currentSelected && data.backups.some((backup) => backup.id === currentSelected)) {
                    return currentSelected;
                }
                return data.backups[0]?.id ?? null;
            });
        } catch (error) {
            setStatusMessage({ type: 'error', text: getErrorMessage(error, 'Не удалось загрузить список резервных копий.') });
        } finally {
            setIsBackupsLoading(false);
        }
    };

    useEffect(() => {
        void loadBackups();
    }, []);

    const getErrorMessage = (error: unknown, fallback: string) => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const e = error as any;
        return e.response?.data?.error || fallback;
    };

    const closeModal = () => {
        if (!isLoading) {
            setModalType(null);
            setModalError(null);
        }
    };

    const openModal = (type: 'restore' | 'rename' | 'password' | 'logout') => {
        setModalError(null);
        setStatusMessage(null);
        if (type === 'restore') {
            setRestoreConfirmationText('');
            void loadBackups({ keepSelection: true });
        }
        setModalType(type);
    };

    const formatBackupDate = (value: string) => {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return new Intl.DateTimeFormat('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        }).format(date);
    };

    const formatBackupSize = (value: number) => {
        if (value < 1024) return `${value} Б`;
        if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} КБ`;
        return `${(value / (1024 * 1024)).toFixed(1)} МБ`;
    };

    const buildBackupDetails = (backup: BackupEntry) => {
        const parts = [
            `Месяцев: ${backup.summary.months}`,
            `Доходов: ${backup.summary.incomes}`,
            `Расходов: ${backup.summary.expenses}`,
            `Лимитов: ${backup.summary.budgets}`,
            `Копилок: ${backup.summary.savingsGoals}`,
        ];
        return parts.join(' · ');
    };

    const handleBackup = async () => {
        setIsLoading(true);
        setStatusMessage(null);
        try {
            await createManualBackup();
            setStatusMessage({ type: 'success', text: 'Резервная копия успешно создана.' });
            await loadBackups();
        } catch (e) {
            setStatusMessage({ type: 'error', text: 'Ошибка создания резервной копии.' });
            console.error(e);
        } finally {
            setIsLoading(false);
        }
    };

    const handleRestore = async () => {
        if (!selectedBackupId) {
            setModalError('Выберите резервную копию для восстановления.');
            return;
        }
        if (restoreConfirmationText.trim() !== BACKUP_RESTORE_CONFIRMATION_TEXT) {
            setModalError(`Введите ${BACKUP_RESTORE_CONFIRMATION_TEXT}, чтобы подтвердить восстановление.`);
            return;
        }
        setIsLoading(true);
        setModalError(null);
        try {
            await restoreBackup(selectedBackupId, restoreConfirmationText.trim());
            setModalType(null);
            setRestoreConfirmationText('');
            setStatusMessage({ type: 'success', text: 'Данные из выбранной резервной копии успешно восстановлены. Перезагрузка...' });
            
            setTimeout(() => window.location.reload(), 1000);
        } catch (error: unknown) {
            setModalError(getErrorMessage(error, 'Ошибка восстановления данных.'));
            console.error(error);
            setIsLoading(false);
        }
    };

    const handleRename = async () => {
        if (!normalizedUsername) {
            setModalError('Введите имя пользователя.');
            return;
        }
        if (usernameTooLong) {
            setModalError('Имя пользователя должно быть не длиннее 64 символов.');
            return;
        }
        setIsLoading(true);
        setModalError(null);
        try {
            await updateUsername(normalizedUsername);
            updateUser({ username: normalizedUsername });
            setUsername(normalizedUsername);
            setStatusMessage({ type: 'success', text: 'Имя пользователя успешно обновлено.' });
            setModalType(null);
        } catch (error: unknown) {
            setModalError(getErrorMessage(error, 'Ошибка обновления имени.'));
        } finally {
            setIsLoading(false);
        }
    };

    const handlePasswordChange = async () => {
        if (!currentPassword) {
            setModalError('Введите текущий пароль.');
            return;
        }
        if (newPassword.length < 6) {
            setModalError('Новый пароль должен содержать минимум 6 символов.');
            return;
        }
        if (newPassword !== confirmPassword) {
            setModalError('Пароли не совпадают.');
            return;
        }
        setIsLoading(true);
        setModalError(null);
        try {
            await updatePassword(currentPassword, newPassword);
            setStatusMessage({ type: 'success', text: 'Пароль успешно изменен.' });
            setModalType(null);
            setCurrentPassword('');
            setNewPassword('');
            setConfirmPassword('');
        } catch (error: unknown) {
            setModalError(getErrorMessage(error, 'Ошибка смены пароля.'));
        } finally {
            setIsLoading(false);
        }
    };

    const handleEmailSave = async () => {
        if (!hasManualRecoveryEmail && !normalizedEmail) {
            setStatusMessage({ type: 'error', text: 'Введите email, если хотите заменить email от соцвхода.' });
            return;
        }

        if (!emailLooksValid) {
            setStatusMessage({ type: 'error', text: 'Введите корректный email.' });
            return;
        }

        setIsLoading(true);
        setStatusMessage(null);
        setEmailDebugToken(null);
        try {
            const { data } = normalizedEmail
                ? await updateUserEmail(normalizedEmail)
                : await clearUserEmail();
            updateUser(data.user);
            setEmail(data.user.email || data.user.recoveryEmail || '');
            setEmailDebugToken(data.debug?.emailVerification ?? null);
            setStatusMessage({
                type: 'success',
                text: data.verificationRequired
                    ? 'Email сохранён. Подтвердите адрес, чтобы восстановление доступа стало доступно.'
                    : 'Email обновлён.',
            });
        } catch (error: unknown) {
            setStatusMessage({ type: 'error', text: getErrorMessage(error, 'Ошибка обновления email.') });
        } finally {
            setIsLoading(false);
        }
    };

    const handleVerificationRequest = async () => {
        setIsLoading(true);
        setStatusMessage(null);
        setEmailDebugToken(null);
        try {
            const { data } = await requestEmailVerification();
            updateUser(data.user);
            setEmailDebugToken(data.debug?.emailVerification ?? null);
            setStatusMessage({
                type: 'success',
                text: data.verificationRequired
                    ? 'Подтверждение для email подготовлено повторно.'
                    : 'Email уже подтверждён.',
            });
        } catch (error: unknown) {
            setStatusMessage({ type: 'error', text: getErrorMessage(error, 'Не удалось подготовить подтверждение email.') });
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="mx-auto max-w-5xl space-y-6">
            <div className="mb-8 flex items-start gap-4 rounded-3xl border border-white/60 bg-white/60 px-5 py-5 shadow-[0_12px_40px_rgba(15,23,42,0.06)] backdrop-blur-xl md:px-6">
                <div className="bg-white/80 p-3 rounded-2xl shadow-sm border border-slate-100/50 backdrop-blur-md text-slate-600">
                    <SettingsIcon className="w-6 h-6" />
                </div>
                <div className="min-w-0">
                    <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Настройки</h1>
                    <p className="text-sm text-slate-500 mt-0.5">Приложение и ваш профиль</p>
                </div>
            </div>

            {statusMessage && (
                <div role="status" aria-live="polite" className={`rounded-2xl border px-5 py-4 text-sm shadow-sm ${statusMessage.type === 'success' ? 'border-green-200 bg-green-50 text-green-700' : 'border-red-200 bg-red-50 text-red-700'}`}>
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
                        <div className="space-y-4">
                            <div className="min-w-0 space-y-3">
                                <label htmlFor="username-input" className="text-sm font-medium text-slate-700">Имя пользователя</label>
                                <input 
                                    id="username-input"
                                    type="text" 
                                    value={username} 
                                    onChange={(e) => setUsername(e.target.value)}
                                    className={`w-full p-3 border rounded-xl focus:ring-2 focus:ring-blue-500/20 focus:border-blue-500 outline-none transition-all shadow-sm ${usernameTooLong ? 'border-amber-300 bg-amber-50/40' : 'border-slate-200'}`}
                                    disabled={isLoading}
                                    placeholder="Ваше имя"
                                />
                                <p className={`text-xs ${usernameTooLong ? 'text-amber-700' : 'text-slate-500'}`}>
                                    Имя будет сохранено без пробелов по краям. Максимум 64 символа.
                                </p>
                            </div>
                            <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                                <button 
                                    onClick={() => openModal('rename')}
                                    disabled={!usernameChanged || usernameTooLong || isLoading}
                                    className="w-full rounded-xl bg-primary px-5 py-3 font-medium text-white shadow-sm transition-all hover:bg-blue-600 hover:shadow-md disabled:opacity-50 disabled:hover:shadow-none sm:w-auto sm:min-w-[180px]"
                                >
                                    Сохранить имя
                                </button>
                                <button 
                                    onClick={() => openModal('logout')}
                                    disabled={isLoading}
                                    className="flex w-full items-center justify-center gap-2 rounded-xl border border-rose-100/50 bg-rose-50 px-5 py-3 font-medium text-rose-600 transition-colors hover:bg-rose-100 sm:w-auto sm:min-w-[160px]"
                                >
                                    <LogOut className="w-4 h-4" />
                                    Выйти
                                </button>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="bg-white/90 backdrop-blur-md rounded-3xl shadow-[0_8px_30px_rgb(0,0,0,0.04)] border border-slate-100 overflow-hidden">
                    <div className="p-6 border-b border-slate-100/60 bg-slate-50/40">
                        <h2 className="text-lg font-bold text-slate-800 flex items-center gap-3">
                            <div className={`p-2 rounded-xl ${user?.recoverabilityStatus === 'protected' ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'}`}>
                                <ShieldAlert className="w-5 h-5" />
                            </div>
                            Защита аккаунта
                        </h2>
                    </div>

                    <div className="p-6 md:p-8 space-y-6">
                        <div className={`rounded-2xl border px-5 py-4 ${user?.recoverabilityStatus === 'protected' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-amber-200 bg-amber-50 text-amber-800'}`}>
                            <p className="text-sm font-semibold">
                                {user?.recoverabilityStatus === 'protected' ? 'Аккаунт защищён' : 'Аккаунт не защищён'}
                            </p>
                            <p className="text-sm mt-2">
                                {user?.recoverabilityStatus === 'protected'
                                    ? `Email для восстановления уже подключён${user?.recoveryEmailSource === 'social' && activeRecoveryProviderLabel ? ` через ${activeRecoveryProviderLabel}` : ''}. Самостоятельное восстановление доступа доступно.`
                                    : 'Без подтверждённого email самостоятельное восстановление доступа недоступно.'}
                            </p>
                        </div>

                        {linkedAuthProviders.length > 0 ? (
                            <div className="rounded-2xl border border-slate-200 bg-slate-50/70 px-5 py-4">
                                <p className="text-sm font-semibold text-slate-800">Подключённые соцвходы</p>
                                <div className="mt-3 space-y-3">
                                    {linkedAuthProviders.map((provider) => (
                                        <div key={provider.provider} className="rounded-xl border border-slate-200 bg-white px-4 py-3">
                                            <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                                                <p className="text-sm font-medium text-slate-800">
                                                    {providerDisplayNames[provider.provider] || provider.provider}
                                                </p>
                                                <p className={`text-xs ${provider.emailVerified ? 'text-emerald-600' : 'text-slate-500'}`}>
                                                    {provider.emailVerified ? 'email подтверждён провайдером' : 'email не подтверждён провайдером'}
                                                </p>
                                            </div>
                                            <p className="mt-2 text-sm text-slate-600">
                                                {provider.email || 'Провайдер не передал email для этого входа.'}
                                            </p>
                                        </div>
                                    ))}
                                </div>
                                <p className="mt-3 text-xs text-slate-500">
                                    {user?.recoveryEmailSource === 'social'
                                        ? 'Подтверждённый email соцпровайдера уже используется для восстановления доступа.'
                                        : 'Email соцпровайдера отображается отдельно и может использоваться для восстановления, если он подтверждён провайдером.'}
                                </p>
                            </div>
                        ) : null}

                        <div className="space-y-4">
                            <div className="min-w-0 space-y-3">
                                <label htmlFor="email-input" className="text-sm font-medium text-slate-700">Email для восстановления доступа</label>
                                <input
                                    id="email-input"
                                    type="email"
                                    value={email}
                                    onChange={(e) => setEmail(e.target.value)}
                                    className={`w-full p-3 border rounded-xl focus:ring-2 focus:ring-emerald-500/20 focus:border-emerald-500 outline-none transition-all shadow-sm ${emailLooksValid ? 'border-slate-200' : 'border-amber-300 bg-amber-50/40'}`}
                                    disabled={isLoading}
                                    placeholder="you@example.com"
                                />
                                <p className={`text-xs ${emailLooksValid ? 'text-slate-500' : 'text-amber-700'}`}>
                                    {user?.emailConfirmed
                                        ? 'Этот email сохранён в аккаунте и подтверждён.'
                                        : user?.email
                                            ? 'После подтверждения этот email будет использоваться для восстановления доступа.'
                                            : user?.recoveryEmail && user?.recoveryEmailSource === 'social'
                                                ? `Сейчас для восстановления автоматически используется email из ${activeRecoveryProviderLabel || 'соцвхода'}. Вы можете сохранить другой email поверх него.`
                                                : 'После сохранения email его нужно подтвердить, чтобы восстановление доступа стало доступно.'}
                                </p>
                            </div>
                            <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                                <button
                                    onClick={handleEmailSave}
                                    disabled={isLoading || !emailChanged || !emailLooksValid || (!hasManualRecoveryEmail && normalizedEmail.length === 0)}
                                    className="w-full rounded-xl bg-emerald-600 px-5 py-3 font-medium text-white shadow-sm transition-all hover:bg-emerald-700 hover:shadow-md disabled:opacity-50 disabled:hover:shadow-none sm:w-auto sm:min-w-[180px]"
                                >
                                    {hasManualRecoveryEmail
                                        ? (normalizedEmail ? 'Сохранить email' : 'Удалить email')
                                        : 'Сохранить другой email'}
                                </button>
                                <button
                                    onClick={handleVerificationRequest}
                                    disabled={isLoading || !user?.email || !!user?.emailConfirmed}
                                    className="w-full rounded-xl border border-emerald-200 bg-white px-5 py-3 text-center font-medium text-emerald-700 transition-colors hover:bg-emerald-50 sm:w-auto sm:min-w-[260px]"
                                >
                                    Отправить подтверждение повторно
                                </button>
                            </div>
                        </div>

                        {emailDebugToken && (
                            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-5 py-4 text-sm text-emerald-900">
                                <p className="font-semibold">Отладочный токен</p>
                                <p className="mt-2 break-all"><span className="font-medium">Токен:</span> <span className="font-mono">{emailDebugToken.token}</span></p>
                                <p className="mt-1"><span className="font-medium">Действует до:</span> {new Date(emailDebugToken.expiresAt).toLocaleString('ru-RU')}</p>
                                <a
                                    href={`/verify-email?token=${encodeURIComponent(emailDebugToken.token)}`}
                                    className="inline-flex mt-3 font-medium text-emerald-700 underline underline-offset-2 hover:text-emerald-800"
                                >
                                    Открыть страницу подтверждения
                                </a>
                            </div>
                        )}
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
                    
                    <div className="p-6 md:p-8 space-y-6">
                        <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
                            <div className="space-y-3">
                                <label htmlFor="current-password" className="text-sm font-medium text-slate-700">Текущий пароль</label>
                                <input 
                                    id="current-password"
                                    type="password" 
                                    value={currentPassword}
                                    onChange={(e) => setCurrentPassword(e.target.value)}
                                    className="w-full p-3 border border-slate-200 rounded-xl focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 outline-none transition-all shadow-sm font-sans"
                                    placeholder="••••••••"
                                    disabled={isLoading}
                                />
                            </div>
                            <div className="space-y-3">
                                <label htmlFor="new-password" className="text-sm font-medium text-slate-700">Новый пароль</label>
                                <input 
                                    id="new-password"
                                    type="password" 
                                    value={newPassword}
                                    onChange={(e) => setNewPassword(e.target.value)}
                                    className={`w-full p-3 border rounded-xl focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 outline-none transition-all shadow-sm font-sans ${passwordTooShort ? 'border-amber-300 bg-amber-50/40' : 'border-slate-200'}`}
                                    placeholder="••••••••"
                                    disabled={isLoading}
                                />
                                <p className={`text-xs ${passwordTooShort ? 'text-amber-700' : 'text-slate-500'}`}>
                                    Минимум 6 символов. Лучше использовать длинный уникальный пароль.
                                </p>
                            </div>
                            <div className="space-y-3">
                                <label htmlFor="confirm-password" className="text-sm font-medium text-slate-700">Подтвердите пароль</label>
                                <input 
                                    id="confirm-password"
                                    type="password" 
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    className={`w-full p-3 border rounded-xl focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 outline-none transition-all shadow-sm font-sans ${passwordsMismatch ? 'border-rose-300 bg-rose-50/40' : 'border-slate-200'}`}
                                    placeholder="••••••••"
                                    disabled={isLoading}
                                />
                                <p className={`text-xs ${passwordsMismatch ? 'text-rose-700' : 'text-slate-500'}`}>
                                    {passwordsMismatch ? 'Подтверждение не совпадает с новым паролем.' : 'Повторите новый пароль без изменений.'}
                                </p>
                            </div>
                        </div>
                        <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                            <button 
                                onClick={() => openModal('password')}
                                disabled={!passwordReadyToSubmit || isLoading}
                                className="w-full rounded-xl bg-amber-500 px-5 py-3 font-medium text-white shadow-sm transition-all hover:bg-amber-600 hover:shadow-md disabled:opacity-50 disabled:hover:shadow-none sm:w-auto sm:min-w-[180px]"
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
                        <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
                            <div className="min-w-0">
                                <h3 className="font-semibold text-slate-800">Автоматически сворачивать боковую панель</h3>
                                <p className="text-sm text-slate-500 mt-1">
                                    Сворачивать меню при клике на основную область (удобно для небольших экранов)
                                </p>
                            </div>
                            <label className="relative inline-flex items-center cursor-pointer shrink-0 rounded-full p-1 transition-transform hover:scale-[1.02]">
                                <input 
                                    type="checkbox" 
                                    className="sr-only peer"
                                    checked={uiSettings.autoCollapseSidebar}
                                    onChange={(e) => updateUiSettings({ autoCollapseSidebar: e.target.checked })}
                                    aria-label="Автоматически сворачивать боковую панель"
                                    title="Автоматически сворачивать боковую панель"
                                />
                                <div className="relative h-8 w-14 rounded-full border border-white/70 bg-slate-200/90 shadow-[inset_0_1px_2px_rgba(15,23,42,0.10)] transition-all duration-200 peer-focus:outline-none peer-focus-visible:ring-4 peer-focus-visible:ring-emerald-300/30 peer-checked:border-emerald-200 peer-checked:bg-gradient-to-r peer-checked:from-emerald-700 peer-checked:via-emerald-600 peer-checked:to-teal-800 peer-checked:shadow-[0_10px_24px_rgba(5,150,105,0.28)] after:absolute after:left-[3px] after:top-[3px] after:h-6 after:w-6 after:rounded-full after:bg-white after:shadow-[0_3px_10px_rgba(15,23,42,0.18)] after:ring-1 after:ring-slate-200/70 after:transition-all after:duration-200 after:content-[''] peer-checked:after:translate-x-6 peer-checked:after:ring-white/70"></div>
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
                        <div className="flex flex-col gap-5 rounded-2xl border border-slate-100 bg-slate-50 p-6 md:flex-row md:items-start">
                            <div className="p-3 bg-white shadow-sm border border-slate-100 rounded-xl text-slate-500 shrink-0">
                                <RotateCcw className="w-6 h-6" />
                            </div>
                            <div className="min-w-0 flex-1">
                                <h3 className="font-bold text-slate-800">Резервное копирование</h3>
                                <p className="text-sm text-slate-600 mt-1 mb-5 leading-relaxed">
                                    Создайте точку восстановления текущих данных или откатитесь к предыдущей сохраненной версии. Полезно перед экспериментами с категориями или крупным импортом.
                                </p>
                                <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap">
                                    <button 
                                        onClick={handleBackup}
                                        disabled={isLoading}
                                        className="flex w-full items-center justify-center rounded-xl border border-slate-200 bg-white px-5 py-2.5 font-medium text-slate-700 shadow-sm transition-all hover:bg-slate-50 hover:shadow-sm disabled:opacity-50 sm:w-auto"
                                    >
                                        <Save className="w-4 h-4 mr-2 text-slate-500" />
                                        {isLoading ? 'Сохранение...' : 'Создать бэкап сейчас'}
                                    </button>
                                    <button 
                                        onClick={() => openModal('restore')}
                                        disabled={isLoading || isBackupsLoading}
                                        className="w-full rounded-xl border border-rose-200 bg-white px-5 py-2.5 font-medium text-rose-600 shadow-sm transition-all hover:bg-rose-50 hover:shadow-sm disabled:opacity-50 sm:w-auto"
                                    >
                                        Выбрать копию для восстановления
                                    </button>
                                </div>
                                <div className="mt-4 space-y-2">
                                    <p className="text-xs text-slate-500">
                                        {isBackupsLoading
                                            ? 'Загружаем список резервных копий...'
                                            : backups.length > 0
                                                ? `Доступно резервных копий: ${backups.length}. Последняя: ${formatBackupDate(backups[0].createdAt)}`
                                                : 'Резервные копии пока не найдены.'}
                                    </p>
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
                        Выберите резервную копию для восстановления. Текущее состояние аккаунта будет полностью заменено содержимым выбранной копии.
                    </p>
                    <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                        Это действие затронет все текущие данные аккаунта и не может быть отменено в интерфейсе. Для подтверждения введите <strong>{BACKUP_RESTORE_CONFIRMATION_TEXT}</strong>.
                    </div>

                    <div className="space-y-2">
                        {isBackupsLoading ? (
                            <div className="flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                                <Loader2 className="h-4 w-4 animate-spin" />
                                Загрузка резервных копий...
                            </div>
                        ) : backups.length > 0 ? (
                            backups.map((backup) => (
                                <button
                                    key={backup.id}
                                    type="button"
                                    onClick={() => setSelectedBackupId(backup.id)}
                                    className={`w-full rounded-xl border px-4 py-3 text-left transition-colors ${
                                        selectedBackupId === backup.id
                                            ? 'border-rose-300 bg-rose-50'
                                            : 'border-slate-200 bg-white hover:bg-slate-50'
                                    }`}
                                >
                                    <div className="flex items-start justify-between gap-3">
                                        <div className="min-w-0">
                                            <div className="font-medium text-slate-800">{formatBackupDate(backup.createdAt)}</div>
                                            <div className="mt-1 text-xs text-slate-500">{buildBackupDetails(backup)}</div>
                                        </div>
                                        <div className="shrink-0 text-xs text-slate-500">{formatBackupSize(backup.sizeBytes)}</div>
                                    </div>
                                </button>
                            ))
                        ) : (
                            <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                                Резервные копии пока не найдены. Сначала создайте новую копию.
                            </div>
                        )}
                    </div>

                    <div className="space-y-2">
                        <label htmlFor="restore-confirmation-input" className="block text-sm font-medium text-slate-700">
                            Подтверждение
                        </label>
                        <input
                            id="restore-confirmation-input"
                            type="text"
                            value={restoreConfirmationText}
                            onChange={(e) => setRestoreConfirmationText(e.target.value)}
                            className="w-full rounded-xl border border-slate-200 px-4 py-3 outline-none transition-all focus:border-rose-400 focus:ring-2 focus:ring-rose-500/10"
                            placeholder={BACKUP_RESTORE_CONFIRMATION_TEXT}
                            disabled={isLoading || backups.length === 0}
                        />
                    </div>
                    
                    {modalError && (
                        <div role="alert" aria-live="polite" className="p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
                            {modalError}
                        </div>
                    )}

                    <div className="flex gap-3">
                        <button
                            onClick={handleRestore}
                            disabled={isLoading || backups.length === 0 || !selectedBackupId || restoreConfirmationText.trim() !== BACKUP_RESTORE_CONFIRMATION_TEXT}
                            className="flex-1 bg-red-600 text-white py-2 rounded-lg font-medium hover:bg-red-700 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
                        >
                            {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
                            {isLoading ? 'Восстановление...' : 'Восстановить из выбранной копии'}
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
                        Изменить имя пользователя на <strong>{normalizedUsername || username}</strong>?
                    </p>

                    {modalError && (
                        <div role="alert" aria-live="polite" className="p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
                            {modalError}
                        </div>
                    )}

                    <div className="flex gap-3">
                        <button
                            onClick={handleRename}
                            disabled={isLoading || !usernameChanged || usernameTooLong}
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
                        Вы уверены, что хотите изменить пароль? Для подтверждения будет использован текущий пароль.
                    </p>
                    <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700">
                        После смены пароля убедитесь, что новый пароль сохранён в надёжном месте. Минимальная длина — 6 символов.
                    </div>

                    {modalError && (
                        <div role="alert" aria-live="polite" className="p-3 bg-red-50 text-red-700 text-sm rounded-lg border border-red-200">
                            {modalError}
                        </div>
                    )}

                    <div className="flex gap-3">
                        <button
                            onClick={handlePasswordChange}
                            disabled={isLoading || !passwordReadyToSubmit}
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
            <Modal
                isOpen={modalType === 'logout'}
                onClose={closeModal}
                title="Подтверждение выхода"
            >
                <div className="space-y-4">
                    <p className="text-gray-600">
                        Выйти из текущей сессии на этом устройстве?
                    </p>
                    <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                        После выхода потребуется заново ввести логин и пароль.
                    </div>
                    <div className="flex gap-3">
                        <button
                            onClick={() => {
                                setModalType(null);
                                logout();
                            }}
                            disabled={isLoading}
                            className="flex-1 bg-rose-600 text-white py-2 rounded-lg font-medium hover:bg-rose-700 transition-colors disabled:opacity-50"
                        >
                            Выйти
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
