import React, { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Loader2 } from 'lucide-react';
import { getSocialProviders } from '../api';
import { clearAuthNotice, getAuthNotice, setRememberSession, shouldRememberSession } from '../auth/tokenStorage';
import { useAuth } from '../context/AuthContext';

const Login: React.FC = () => {
  const [isLogin, setIsLogin] = useState(true);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(() => shouldRememberSession());
  const [error, setError] = useState('');
  const [authNotice, setAuthNotice] = useState(() => {
    const notice = getAuthNotice();
    return notice === 'session-expired'
      ? 'Сессия истекла. Войдите снова, чтобы продолжить работу.'
      : '';
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isGoogleSubmitting, setIsGoogleSubmitting] = useState(false);
  const [isYandexSubmitting, setIsYandexSubmitting] = useState(false);
  const [googleAvailable, setGoogleAvailable] = useState(false);
  const [yandexAvailable, setYandexAvailable] = useState(false);
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const trimmedUsername = username.trim();
  const passwordIsTooShort = !isLogin && password.length > 0 && password.length < 6;
  const canSubmit = trimmedUsername.length > 0 && password.length > 0 && !passwordIsTooShort && !isSubmitting;
  const canUseGoogle = isLogin && googleAvailable && !isSubmitting && !isGoogleSubmitting && !isYandexSubmitting;
  const canUseYandex = isLogin && yandexAvailable && !isSubmitting && !isYandexSubmitting && !isGoogleSubmitting;

  useEffect(() => {
    let isMounted = true;

    const loadSocialProviders = async () => {
      try {
        const { data } = await getSocialProviders();
        if (!isMounted) {
          return;
        }

        const googleProvider = data.providers.find((provider) => provider.id === 'google');
        const yandexProvider = data.providers.find((provider) => provider.id === 'yandex');
        setGoogleAvailable(Boolean(googleProvider?.enabled));
        setYandexAvailable(Boolean(yandexProvider?.enabled));
      } catch {
        if (!isMounted) {
          return;
        }

        setGoogleAvailable(false);
        setYandexAvailable(false);
      }
    };

    void loadSocialProviders();

    return () => {
      isMounted = false;
    };
  }, []);

  const socialLoginHint = useMemo(() => {
    if (!isLogin) {
      return '';
    }

    if (googleAvailable && yandexAvailable) {
      return 'Можно войти через Google, Яндекс или использовать локальные учётные данные.';
    }

    if (googleAvailable) {
      return 'Можно войти через Google или использовать локальные учётные данные.';
    }

    if (yandexAvailable) {
      return 'Можно войти через Яндекс или использовать локальные учётные данные.';
    }

    return 'Сейчас доступен вход по имени пользователя и паролю.';
  }, [googleAvailable, isLogin, yandexAvailable]);

  const getErrorMessage = (error: unknown) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const err = error as any;
    return err.response?.data?.error || err.message || 'Ошибка авторизации';
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setAuthNotice('');
    clearAuthNotice();
    if (!trimmedUsername) {
      setError('Введите имя пользователя.');
      return;
    }
    if (!password) {
      setError('Введите пароль.');
      return;
    }
    if (!isLogin && password.length < 6) {
      setError('Пароль должен содержать минимум 6 символов.');
      return;
    }

    setIsSubmitting(true);
    try {
      if (isLogin) {
        await login({ username: trimmedUsername, password, rememberMe });
      } else {
        await register({ username: trimmedUsername, password });
      }
      navigate('/');
    } catch (error: unknown) {
      setError(getErrorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleGoogleLogin = () => {
    setError('');
    setAuthNotice('');
    clearAuthNotice();
    setRememberSession(rememberMe);
    setIsGoogleSubmitting(true);
    window.location.assign('/api/auth/oauth/google/start');
  };

  const handleYandexLogin = () => {
    setError('');
    setAuthNotice('');
    clearAuthNotice();
    setRememberSession(rememberMe);
    setIsYandexSubmitting(true);
    window.location.assign('/api/auth/oauth/yandex/start');
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-green-100 via-emerald-50 to-teal-100 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8 bg-white p-10 rounded-2xl shadow-xl border border-white/50">
        <div className="flex flex-col items-center">
            {/* Logo */}
            <div className="bg-emerald-100 p-3 rounded-full mb-4 shadow-inner">
                <img src="/purse.svg" alt="FinKeeper Logo" className="h-12 w-12" />
            </div>
            
            {/* App Name */}
            <h1 className="text-3xl font-bold text-emerald-800 tracking-tight">FinKeeper</h1>
            
            <h2 className="mt-2 text-center text-lg font-medium text-gray-500">
                {isLogin ? 'Вход в систему' : 'Создание аккаунта'}
            </h2>
        </div>
        
        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          <div className="space-y-5">
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-1 ml-1">Имя пользователя</label>
              <input
                id="username"
                name="username"
                type="text"
                required
                className="appearance-none block w-full px-4 py-3 border border-gray-300 placeholder-gray-400 text-gray-900 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm transition-shadow shadow-sm disabled:bg-gray-50 disabled:text-gray-500"
                placeholder="Введите имя"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                disabled={isSubmitting}
              />
            </div>
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1 ml-1">Пароль</label>
              <input
                id="password"
                name="password"
                type="password"
                required
                className={`appearance-none block w-full px-4 py-3 border placeholder-gray-400 text-gray-900 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm transition-shadow shadow-sm disabled:bg-gray-50 disabled:text-gray-500 ${
                  passwordIsTooShort ? 'border-amber-300 bg-amber-50/50' : 'border-gray-300'
                }`}
                placeholder={isLogin ? 'Введите пароль' : 'Минимум 6 символов'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={isSubmitting}
              />
              <p className={`mt-2 text-xs ml-1 ${passwordIsTooShort ? 'text-amber-700' : 'text-gray-500'}`}>
                {isLogin
                  ? 'Используйте пароль от вашей учётной записи.'
                  : 'Пароль должен содержать минимум 6 символов. Чем длиннее пароль, тем надёжнее защита.'}
              </p>
              {isLogin ? (
                <div className="mt-3 flex items-start justify-between gap-3">
                  <p className="text-xs text-gray-500 max-w-[75%]">
                    Самостоятельное восстановление доступа работает только для аккаунтов с подтверждённым email.
                  </p>
                  <Link
                    to="/password-recovery"
                    className="shrink-0 text-xs font-medium text-emerald-700 hover:text-emerald-800 transition-colors"
                  >
                    Забыли пароль?
                  </Link>
                </div>
              ) : (
                <p className="mt-3 text-xs ml-1 text-gray-500">
                  После регистрации добавьте и подтвердите email в настройках, чтобы восстановление доступа было доступно.
                </p>
              )}
            </div>
          </div>

          {isLogin ? (
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <div className="h-px flex-1 bg-slate-200" />
                <span className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-400">или</span>
                <div className="h-px flex-1 bg-slate-200" />
              </div>

              <button
                type="button"
                disabled={!canUseGoogle}
                onClick={handleGoogleLogin}
                className="flex w-full items-center justify-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 shadow-sm transition-all hover:border-emerald-200 hover:bg-emerald-50/50 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isGoogleSubmitting ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <svg className="h-5 w-5" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                    <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 0 1-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4" />
                    <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
                    <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
                    <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
                  </svg>
                )}
                {isGoogleSubmitting ? 'Перенаправляем в Google...' : 'Войти через Google'}
              </button>

              <button
                type="button"
                disabled={!canUseYandex}
                onClick={handleYandexLogin}
                className="flex w-full items-center justify-center gap-3 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 shadow-sm transition-all hover:border-red-200 hover:bg-red-50/40 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isYandexSubmitting ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <svg
                    className="h-5 w-5 shrink-0 rounded-full"
                    viewBox="0 0 44 44"
                    aria-hidden="true"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <rect width="44" height="44" fill="#FC3F1D" />
                    <path
                      d="M24.7407 33.9778H29.0889V9.04443H22.7592C16.3929 9.04443 13.0538 12.303 13.0538 17.1176C13.0538 21.2731 15.2187 23.6163 19.0532 26.1609L21.3832 27.6987L18.3927 25.1907L12.4667 33.9778H17.1818L23.5115 24.5317L21.3098 23.0671C18.6496 21.2731 17.3469 19.8818 17.3469 16.8613C17.3469 14.2068 19.2183 12.4128 22.7776 12.4128H24.7223V33.9778H24.7407Z"
                      fill="#FFFFFF"
                    />
                  </svg>
                )}
                {isYandexSubmitting ? 'Перенаправляем в Яндекс...' : 'Войти с Яндекс ID'}
              </button>

              <p className="text-xs text-center text-gray-500">
                {socialLoginHint}
              </p>
            </div>
          ) : null}

          {isLogin ? (
            <label className="flex items-center gap-3 text-sm text-gray-600">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-gray-300 text-emerald-600 focus:ring-emerald-500"
                checked={rememberMe}
                onChange={(e) => setRememberMe(e.target.checked)}
                disabled={isSubmitting || isGoogleSubmitting || isYandexSubmitting}
              />
              <span>Запомнить меня</span>
            </label>
          ) : null}

          {authNotice && (
            <div role="status" aria-live="polite" className="bg-amber-50 border border-amber-200 text-amber-800 rounded-lg p-3 text-sm text-center">
              {authNotice}
            </div>
          )}

          {error && (
            <div role="alert" aria-live="polite" className="bg-red-50 border border-red-200 text-red-600 rounded-lg p-3 text-sm text-center">
              {error}
            </div>
          )}

          <div>
            <button
              type="submit"
              disabled={!canSubmit}
              className="group relative w-full flex justify-center items-center gap-2 py-3 px-4 border border-transparent text-sm font-bold rounded-xl text-white bg-emerald-600 hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500 shadow-lg shadow-emerald-200 transition-all active:scale-[0.98] disabled:opacity-60 disabled:hover:bg-emerald-600 disabled:cursor-not-allowed"
            >
              {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              {isSubmitting ? (isLogin ? 'Входим...' : 'Создаем аккаунт...') : (isLogin ? 'Войти' : 'Зарегистрироваться')}
            </button>
          </div>
        </form>

        <div className="text-center mt-4">
            <p className="text-sm text-gray-500">
                {isLogin ? 'Еще нет аккаунта?' : 'Уже есть аккаунт?'}
                <button 
                    onClick={() => {
                        setIsLogin(!isLogin);
                        setPassword('');
                        setError('');
                        setAuthNotice('');
                        clearAuthNotice();
                    }}
                    disabled={isSubmitting}
                    className="ml-2 font-medium text-emerald-600 hover:text-emerald-500 transition-colors"
                >
                    {isLogin ? 'Создать' : 'Войти'}
                </button>
            </p>
        </div>
      </div>
    </div>
  );
};

export default Login;