import React, { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ArrowLeft, KeyRound, Loader2, Mail, ShieldAlert } from 'lucide-react';
import { confirmPasswordRecovery, requestPasswordRecovery } from '../api';
import StatusBanner from '../components/StatusBanner';

const PasswordRecovery: React.FC = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token')?.trim() ?? '';
  const mode = token ? 'confirm' : 'request';

  const [email, setEmail] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [debugTokenPreview, setDebugTokenPreview] = useState<{ token: string; expiresAt: string; username?: string; source?: string } | null>(null);
  const [debugTokenPreviews, setDebugTokenPreviews] = useState<Array<{ token: string; expiresAt: string; username?: string; source?: string }>>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const emailIsValid = useMemo(() => /\S+@\S+\.\S+/.test(email.trim()), [email]);
  const passwordTooShort = newPassword.length > 0 && newPassword.length < 6;
  const passwordsMismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;

  const getErrorMessage = (errorValue: unknown, fallback: string) => {
    const apiError = errorValue as { response?: { data?: { error?: string } }; message?: string };
    return apiError.response?.data?.error || apiError.message || fallback;
  };

  const handleRequestSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    setSuccessMessage('');
    setDebugTokenPreview(null);
    setDebugTokenPreviews([]);

    if (!email.trim()) {
      setError('Введите email.');
      return;
    }

    if (!emailIsValid) {
      setError('Введите корректный email.');
      return;
    }

    setIsSubmitting(true);
    try {
      const { data } = await requestPasswordRecovery(email.trim());
      setSuccessMessage('Если аккаунт с таким email существует и email подтверждён либо подтверждён у соцпровайдера, инструкция по восстановлению уже подготовлена.');
      setDebugTokenPreview(data.debug?.passwordReset ?? null);
      setDebugTokenPreviews(data.debug?.passwordResets ?? []);
    } catch (errorValue: unknown) {
      setError(getErrorMessage(errorValue, 'Не удалось подготовить восстановление доступа.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleConfirmSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    setSuccessMessage('');

    if (!token) {
      setError('Токен восстановления не найден.');
      return;
    }

    if (!newPassword) {
      setError('Введите новый пароль.');
      return;
    }

    if (newPassword.length < 6) {
      setError('Новый пароль должен содержать минимум 6 символов.');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('Подтверждение пароля не совпадает.');
      return;
    }

    setIsSubmitting(true);
    try {
      await confirmPasswordRecovery(token, newPassword);
      setSuccessMessage('Пароль обновлён. Теперь вы можете войти с новым паролем.');
      setNewPassword('');
      setConfirmPassword('');
    } catch (errorValue: unknown) {
      setError(getErrorMessage(errorValue, 'Не удалось обновить пароль.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-green-100 via-emerald-50 to-teal-100 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-lg w-full space-y-8 bg-white p-8 sm:p-10 rounded-3xl shadow-xl border border-white/60">
        <div className="space-y-4">
          <Link to="/login" className="inline-flex items-center gap-2 text-sm font-medium text-emerald-700 hover:text-emerald-800 transition-colors">
            <ArrowLeft className="w-4 h-4" />
            Вернуться ко входу
          </Link>
          <div className="flex items-center gap-3">
            <div className="p-3 rounded-2xl bg-emerald-100 text-emerald-700">
              {mode === 'request' ? <Mail className="w-6 h-6" /> : <KeyRound className="w-6 h-6" />}
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-900">
                {mode === 'request' ? 'Восстановление доступа' : 'Новый пароль'}
              </h1>
              <p className="text-sm text-slate-500 mt-1">
                {mode === 'request'
                  ? 'Самостоятельное восстановление доступно для аккаунтов с подтверждённым email или подтверждённым email соцвхода.'
                  : 'Установите новый пароль для вашего аккаунта.'}
              </p>
            </div>
          </div>
        </div>

        {mode === 'request' ? (
          <form className="space-y-5" onSubmit={handleRequestSubmit}>
            <div className="rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
              Если email не подтверждён в аккаунте, восстановление всё равно может сработать через подтверждённый email подключённого соцвхода.
            </div>
            <div className="space-y-2">
              <label htmlFor="recovery-email" className="block text-sm font-medium text-slate-700">
                Email
              </label>
              <input
                id="recovery-email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                disabled={isSubmitting}
                placeholder="you@example.com"
                className="w-full rounded-2xl border border-slate-200 bg-white px-4 py-3 text-slate-900 shadow-sm outline-none transition-all focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20"
              />
            </div>
            <button
              type="submit"
              disabled={isSubmitting || !email.trim()}
              className="w-full flex items-center justify-center gap-2 rounded-2xl bg-emerald-600 px-4 py-3 font-semibold text-white shadow-lg shadow-emerald-200 transition-all hover:bg-emerald-700 disabled:opacity-60"
            >
              {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
              {isSubmitting ? 'Подготавливаем...' : 'Подготовить восстановление'}
            </button>
          </form>
        ) : (
          <form className="space-y-5" onSubmit={handleConfirmSubmit}>
            <div className="space-y-2">
              <label htmlFor="new-password" className="block text-sm font-medium text-slate-700">
                Новый пароль
              </label>
              <input
                id="new-password"
                type="password"
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                disabled={isSubmitting}
                placeholder="Минимум 6 символов"
                className={`w-full rounded-2xl border px-4 py-3 text-slate-900 shadow-sm outline-none transition-all focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 ${
                  passwordTooShort ? 'border-amber-300 bg-amber-50/50' : 'border-slate-200 bg-white'
                }`}
              />
            </div>
            <div className="space-y-2">
              <label htmlFor="confirm-password" className="block text-sm font-medium text-slate-700">
                Подтвердите пароль
              </label>
              <input
                id="confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
                disabled={isSubmitting}
                placeholder="Повторите новый пароль"
                className={`w-full rounded-2xl border px-4 py-3 text-slate-900 shadow-sm outline-none transition-all focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20 ${
                  passwordsMismatch ? 'border-rose-300 bg-rose-50/50' : 'border-slate-200 bg-white'
                }`}
              />
            </div>
            <button
              type="submit"
              disabled={isSubmitting || !newPassword || !confirmPassword}
              className="w-full flex items-center justify-center gap-2 rounded-2xl bg-emerald-600 px-4 py-3 font-semibold text-white shadow-lg shadow-emerald-200 transition-all hover:bg-emerald-700 disabled:opacity-60"
            >
              {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
              {isSubmitting ? 'Сохраняем...' : 'Сохранить новый пароль'}
            </button>
          </form>
        )}

        {error ? (
          <StatusBanner variant="error">
            {error}
          </StatusBanner>
        ) : null}

        {successMessage ? (
          <StatusBanner variant="success" title={mode === 'request' ? 'Запрос подготовлен' : 'Пароль обновлён'} className="space-y-3">
            <p>{successMessage}</p>
            {debugTokenPreview ? (
              <div className="rounded-2xl border border-emerald-200 bg-white/80 px-4 py-3 text-xs text-slate-700">
                <div className="flex items-start gap-2">
                  <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5 text-emerald-700" />
                  <div className="space-y-1">
                    <p className="font-semibold text-slate-800">Отладочный токен</p>
                    {debugTokenPreview.username ? (
                      <p>Аккаунт: <span className="font-medium">{debugTokenPreview.username}</span></p>
                    ) : null}
                    <p>Токен: <span className="font-mono break-all">{debugTokenPreview.token}</span></p>
                    <p>Действует до: {new Date(debugTokenPreview.expiresAt).toLocaleString('ru-RU')}</p>
                    <p>
                      Ссылка для проверки:{' '}
                      <Link
                        to={`/password-recovery?token=${encodeURIComponent(debugTokenPreview.token)}`}
                        className="font-medium text-emerald-700 underline underline-offset-2"
                      >
                        открыть форму смены пароля
                      </Link>
                    </p>
                    {debugTokenPreviews.length > 1 ? (
                      <div className="mt-3 space-y-2 rounded-xl border border-emerald-200 bg-emerald-50/60 p-3">
                        <p className="font-semibold text-slate-800">Найдено несколько аккаунтов с этим email</p>
                        {debugTokenPreviews.map((preview) => (
                          <div key={`${preview.username || 'user'}-${preview.token}`} className="space-y-1 rounded-xl border border-emerald-200 bg-white/80 px-3 py-2">
                            <p>Аккаунт: <span className="font-medium">{preview.username || 'Без имени'}</span></p>
                            <p>Токен: <span className="font-mono break-all">{preview.token}</span></p>
                          </div>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </div>
              </div>
            ) : null}
          </StatusBanner>
        ) : null}
      </div>
    </div>
  );
};

export default PasswordRecovery;
