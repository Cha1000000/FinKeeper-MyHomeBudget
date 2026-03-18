import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Loader2, ShieldAlert } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const SocialAuthCallback: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { completeSocialLogin } = useAuth();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const exchangeStartedRef = useRef(false);

  const code = searchParams.get('code')?.trim() ?? '';
  const error = searchParams.get('error')?.trim() ?? '';
  const provider = useMemo(() => {
    const rawProvider = searchParams.get('provider')?.trim().toLowerCase() ?? '';
    return rawProvider || 'google';
  }, [searchParams]);
  const providerDisplayName = provider === 'yandex' ? 'Яндекс' : 'Google';
  const [message, setMessage] = useState(`Завершаем вход через ${providerDisplayName}...`);

  useEffect(() => {
    setMessage(`Завершаем вход через ${providerDisplayName}...`);
  }, [providerDisplayName]);

  useEffect(() => {
    if (exchangeStartedRef.current) {
      return;
    }

    if (error) {
      exchangeStartedRef.current = true;
      setStatus('error');
      setMessage(`Не удалось выполнить вход через ${providerDisplayName}. Попробуйте ещё раз.`);
      return;
    }

    if (!code) {
      exchangeStartedRef.current = true;
      setStatus('error');
      setMessage(`Код авторизации не найден. Запустите вход через ${providerDisplayName} ещё раз.`);
      return;
    }

    exchangeStartedRef.current = true;

    const run = async () => {
      try {
        await completeSocialLogin(code);
        setStatus('success');
        setMessage(`Вход через ${providerDisplayName} выполнен успешно. Перенаправляем...`);
        window.setTimeout(() => {
          navigate('/', { replace: true });
        }, 800);
      } catch (authError) {
        const fallbackMessage = `Не удалось завершить вход через ${providerDisplayName}. Попробуйте ещё раз.`;
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const err = authError as any;
        setStatus('error');
        setMessage(err.response?.data?.error || err.message || fallbackMessage);
      }
    };

    void run();
  }, [code, completeSocialLogin, error, navigate, providerDisplayName]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-green-100 via-emerald-50 to-teal-100 px-4 py-12">
      <div className="w-full max-w-md rounded-3xl border border-white/60 bg-white/95 p-8 shadow-2xl shadow-emerald-100">
        <div className="mb-6 flex items-center gap-3">
          <div className="rounded-2xl bg-emerald-100 p-3 text-emerald-700 shadow-inner">
            {status === 'loading' ? (
              <Loader2 className="h-5 w-5 animate-spin" />
            ) : status === 'success' ? (
              <CheckCircle2 className="h-5 w-5" />
            ) : (
              <ShieldAlert className="h-5 w-5" />
            )}
          </div>
          <h1 className="text-xl font-semibold text-slate-900">{`Вход через ${providerDisplayName}`}</h1>
        </div>

        <div
          role="status"
          aria-live="polite"
          className={`rounded-2xl border px-4 py-4 text-sm ${
            status === 'error'
              ? 'border-rose-200 bg-rose-50 text-rose-700'
              : status === 'success'
                ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
                : 'border-emerald-200 bg-emerald-50/80 text-slate-700'
          }`}
        >
          {message}
        </div>

        <div className="mt-6 flex flex-col gap-3 sm:flex-row">
          <Link
            to="/login"
            className="flex flex-1 items-center justify-center gap-2 rounded-2xl border border-slate-200 px-4 py-3 font-medium text-slate-700 transition-colors hover:bg-slate-50"
          >
            <ArrowLeft className="h-4 w-4" />
            Вернуться ко входу
          </Link>
          <button
            type="button"
            onClick={() => navigate('/', { replace: true })}
            className="flex-1 rounded-2xl bg-emerald-600 px-4 py-3 font-semibold text-white shadow-lg shadow-emerald-200 transition-all hover:bg-emerald-700"
          >
            Открыть приложение
          </button>
        </div>
      </div>
    </div>
  );
};

export default SocialAuthCallback;
