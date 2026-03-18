import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, Loader2, MailCheck, ShieldAlert } from 'lucide-react';
import { confirmEmailVerification } from '../api';
import { useAuth } from '../context/AuthContext';

const EmailVerification: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { updateUser } = useAuth();
  const token = searchParams.get('token')?.trim() ?? '';

  const [status, setStatus] = useState<'idle' | 'loading' | 'success' | 'error'>(token ? 'loading' : 'error');
  const [message, setMessage] = useState(token ? 'Подтверждаем email...' : 'Токен подтверждения не найден.');
  const verificationAttempted = useRef(false);

  useEffect(() => {
    if (verificationAttempted.current) return;
    verificationAttempted.current = true;

    const runVerification = async () => {
      if (!token) {
        setStatus('error');
        setMessage('Токен подтверждения не найден.');
        return;
      }

      try {
        const { data } = await confirmEmailVerification(token);
        updateUser(data.user);
        setStatus('success');
        setMessage('Email подтверждён. Теперь самостоятельное восстановление доступа доступно для этого аккаунта.');
      } catch (errorValue: unknown) {
        const apiError = errorValue as { response?: { data?: { error?: string } }; message?: string };
        setStatus('error');
        setMessage(apiError.response?.data?.error || apiError.message || 'Не удалось подтвердить email.');
      }
    };

    void runVerification();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

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
              <MailCheck className="w-6 h-6" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-900">Подтверждение email</h1>
              <p className="text-sm text-slate-500 mt-1">Подтверждённый email включает самостоятельное восстановление доступа.</p>
            </div>
          </div>
        </div>

        <div
          aria-live="polite"
          className={`rounded-2xl border px-5 py-5 text-sm ${
            status === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
              : status === 'error'
                ? 'border-rose-200 bg-rose-50 text-rose-700'
                : 'border-slate-200 bg-slate-50 text-slate-700'
          }`}
        >
          <div className="flex items-start gap-3">
            {status === 'loading' ? (
              <Loader2 className="w-5 h-5 shrink-0 mt-0.5 animate-spin" />
            ) : status === 'success' ? (
              <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5" />
            ) : (
              <ShieldAlert className="w-5 h-5 shrink-0 mt-0.5" />
            )}
            <p>{message}</p>
          </div>
        </div>

        <div className="flex flex-col sm:flex-row gap-3">
          <button
            onClick={() => navigate('/settings')}
            className="flex-1 rounded-2xl bg-emerald-600 px-4 py-3 font-semibold text-white shadow-lg shadow-emerald-200 transition-all hover:bg-emerald-700"
          >
            Перейти в настройки
          </button>
          <Link
            to="/login"
            className="flex-1 rounded-2xl border border-slate-200 bg-white px-4 py-3 text-center font-semibold text-slate-700 transition-all hover:bg-slate-50"
          >
            Ко входу
          </Link>
        </div>
      </div>
    </div>
  );
};

export default EmailVerification;
