import React, { useState } from 'react';
import { CheckCircle2, Loader2, X } from 'lucide-react';
import { redeemPromoCode, startFreeTrial } from '../api';
import { useAuth } from '../context/AuthContext';
import {
    ProBackupVaultIcon,
    ProBurnFlameIcon,
    ProCrownIcon,
    ProDevicesIcon,
    ProExportSheetIcon,
} from './ProIcons';

const FEATURE_TITLES: Record<string, string> = {
    exportReports: 'Экспорт отчётов в Excel (.xlsx) и CSV',
    extendedAnalytics: 'Глубокая аналитика за 12 месяцев и скорость трат',
    extendedBackups: 'Архив до 30 копий, закрепление и сохранение в файл',
    unlimitedDevices: 'Неограниченное число активных устройств',
};

const ProPaywallModal: React.FC = () => {
    const { isPaywallOpen, paywallFeature, closePaywall, entitlements, updateUser } = useAuth();
    const [isActivatingTrial, setIsActivatingTrial] = useState(false);
    const [showPromoInput, setShowPromoInput] = useState(false);
    const [promoCode, setPromoCode] = useState('');
    const [isRedeemingPromo, setIsRedeemingPromo] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

    if (!isPaywallOpen) return null;

    const highlightedTitle = paywallFeature ? FEATURE_TITLES[paywallFeature] : null;

    const handleStartTrial = async () => {
        setIsActivatingTrial(true);
        setErrorMessage(null);
        setSuccessMessage(null);
        try {
            const { data } = await startFreeTrial();
            updateUser({ entitlements: data.entitlements });
            setSuccessMessage('Пробный период Семейный PRO на 14 дней активирован!');
            setTimeout(() => {
                setSuccessMessage(null);
                closePaywall();
            }, 900);
        } catch (err: unknown) {
            const e = err as { response?: { data?: { error?: string } } };
            setErrorMessage(e.response?.data?.error || 'Не удалось активировать пробный период.');
        } finally {
            setIsActivatingTrial(false);
        }
    };

    const isLifetimePro = Boolean(entitlements.isLifetime || (entitlements.isPro && entitlements.expiresAt == null));

    const handleClosePromoDialog = () => {
        if (isRedeemingPromo) return;
        setShowPromoInput(false);
        setErrorMessage(null);
    };

    const handleRedeemPromo = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!promoCode.trim()) return;
        setIsRedeemingPromo(true);
        setErrorMessage(null);
        setSuccessMessage(null);
        try {
            const { data } = await redeemPromoCode(promoCode.trim());
            updateUser({ entitlements: data.entitlements });
            setPromoCode('');
            setSuccessMessage('Промокод успешно активирован!');
            setTimeout(() => {
                setSuccessMessage(null);
                setShowPromoInput(false);
                closePaywall();
            }, 900);
        } catch (err: unknown) {
            const errorObj = err as { response?: { data?: { error?: string } } };
            setErrorMessage(errorObj.response?.data?.error || 'Не удалось применить промокод.');
        } finally {
            setIsRedeemingPromo(false);
        }
    };

    const features = [
        {
            key: 'extendedAnalytics',
            icon: ProBurnFlameIcon,
            title: 'Глубокая аналитика за 12 мес и скорость трат',
            desc: 'Динамика за год и более, средняя скорость трат в день и сравнение категорий с прошлым месяцем.',
        },
        {
            key: 'exportReports',
            icon: ProExportSheetIcon,
            title: 'Экспорт отчётов в Excel (.xlsx) и CSV',
            desc: 'Выгрузка сводки, доходов, расходов и копилок за месяц, квартал или год в один клик.',
        },
        {
            key: 'extendedBackups',
            icon: ProBackupVaultIcon,
            title: 'Расширенный архив до 30 копий и файлы',
            desc: 'Закрепление важных точек восстановления от автоудаления и сохранение/загрузка копий на диск.',
        },
        {
            key: 'unlimitedDevices',
            icon: ProDevicesIcon,
            title: 'Неограниченно устройств и контроль сессий',
            desc: 'Синхронизация всех телефонов, ПК и браузеров семьи без лимита в 2 устройства.',
        },
    ];

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-md p-4 overflow-y-auto"
            onClick={closePaywall}
        >
            <div
                className="relative w-full max-w-xl rounded-3xl border border-emerald-500/30 bg-[var(--color-surface)] p-6 md:p-7 shadow-[0_24px_80px_rgba(0,0,0,0.45)] overflow-hidden transition-colors"
                onClick={(e) => e.stopPropagation()}
            >
                <div className="pointer-events-none absolute -top-24 -right-24 h-56 w-56 rounded-full bg-emerald-500/15 blur-3xl" />
                <div className="pointer-events-none absolute -bottom-24 -left-24 h-56 w-56 rounded-full bg-amber-500/10 blur-3xl" />

                <div className="relative z-10 flex items-start justify-between gap-4">
                    <div className="flex items-center gap-3.5">
                        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl border border-emerald-500/30 bg-emerald-500/15 text-emerald-500 shadow-inner">
                            <ProCrownIcon className="h-6 w-6" />
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <span className="inline-flex items-center rounded-full border border-emerald-500/35 bg-emerald-500/15 px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wider text-emerald-600 dark:text-emerald-300">
                                    Семейный PRO
                                </span>
                                {entitlements.canStartTrial && (
                                    <span className="inline-flex items-center rounded-full border border-amber-500/35 bg-amber-500/15 px-2.5 py-0.5 text-[11px] font-bold text-amber-700 dark:text-amber-300">
                                        14 дней бесплатно
                                    </span>
                                )}
                            </div>
                            <h3 className="mt-1 text-xl font-extrabold tracking-tight text-slate-900 dark:text-[var(--color-text-main)]">
                                Больше аналитики, контроля и надёжности
                            </h3>
                        </div>
                    </div>

                    <button
                        type="button"
                        onClick={closePaywall}
                        aria-label="Закрыть"
                        className="rounded-xl border border-[var(--color-border-default)] bg-[var(--color-surface-soft)] p-2 text-slate-500 hover:text-slate-800 dark:text-[var(--color-text-muted)] dark:hover:text-white transition-colors"
                    >
                        <X className="h-4 w-4" />
                    </button>
                </div>

                {highlightedTitle && !entitlements.isPro && (
                    <div className="relative z-10 mt-4 rounded-2xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-2.5 text-xs font-medium text-emerald-800 dark:text-emerald-200">
                        Вы открыли функцию PRO: <strong className="font-bold">{highlightedTitle}</strong>
                    </div>
                )}

                {/* Блок тарифа */}
                <div className="relative z-10 mt-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[var(--color-border-default)] bg-[var(--color-surface-soft)]/80 px-4 py-3.5">
                    <div>
                        <div className="flex items-baseline gap-1.5">
                            <span className="text-2xl font-extrabold tabular-nums text-slate-900 dark:text-[var(--color-text-main)]">199 ₽</span>
                            <span className="text-xs font-medium text-slate-500 dark:text-[var(--color-text-muted)]">/ месяц после триала</span>
                        </div>
                        <p className="mt-0.5 text-xs text-slate-500 dark:text-[var(--color-text-muted)]">
                            Весь базовый учёт доходов, расходов, лимитов и копилок всегда остаётся бесплатным
                        </p>
                    </div>
                    <span className="rounded-xl border border-emerald-500/25 bg-emerald-500/10 px-3 py-1.5 text-xs font-bold text-emerald-700 dark:text-emerald-300">
                        Без привязки карты
                    </span>
                </div>

                {/* Список преимуществ */}
                <div className="relative z-10 mt-5 space-y-2.5">
                    {features.map((item) => {
                        const IconComponent = item.icon;
                        const isHighlighted = paywallFeature === item.key;
                        return (
                            <div
                                key={item.key}
                                className={`flex items-start gap-3.5 rounded-2xl border p-3.5 transition-colors ${
                                    isHighlighted
                                        ? 'border-emerald-500/50 bg-emerald-500/10'
                                        : 'border-[var(--color-border-default)] bg-[var(--color-surface-soft)]/50'
                                }`}
                            >
                                <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl border border-emerald-500/25 bg-emerald-500/10 text-emerald-600 dark:text-emerald-300">
                                    <IconComponent className="h-5 w-5" />
                                </div>
                                <div className="min-w-0 flex-1">
                                    <p className="text-sm font-bold text-slate-800 dark:text-[var(--color-text-main)]">
                                        {item.title}
                                    </p>
                                    <p className="mt-0.5 text-xs leading-relaxed text-slate-500 dark:text-[var(--color-text-muted)]">
                                        {item.desc}
                                    </p>
                                </div>
                            </div>
                        );
                    })}
                </div>

                {errorMessage && (
                    <div className="relative z-10 mt-4 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-2.5 text-xs font-medium text-rose-600 dark:text-rose-300">
                        {errorMessage}
                    </div>
                )}

                {successMessage && (
                    <div className="relative z-10 mt-4 flex items-center gap-2 rounded-xl border border-emerald-500/40 bg-emerald-500/15 px-4 py-2.5 text-xs font-semibold text-emerald-700 dark:text-emerald-300">
                        <CheckCircle2 className="h-4 w-4 shrink-0" />
                        <span>{successMessage}</span>
                    </div>
                )}

                {/* Главная кнопка действия */}
                <div className="relative z-10 mt-5 space-y-3">
                    {entitlements.canStartTrial ? (
                        <button
                            type="button"
                            onClick={handleStartTrial}
                            disabled={isActivatingTrial}
                            className="flex w-full items-center justify-center gap-2.5 rounded-2xl bg-gradient-to-r from-emerald-500 to-emerald-600 px-5 py-3.5 text-sm font-bold text-white shadow-[0_10px_28px_rgba(16,185,129,0.35)] transition-all hover:opacity-95 disabled:opacity-60"
                        >
                            {isActivatingTrial ? (
                                <Loader2 className="h-4 w-4 animate-spin" />
                            ) : (
                                <ProCrownIcon className="h-5 w-5" />
                            )}
                            <span>
                                {isActivatingTrial
                                    ? 'Активируем пробный период...'
                                    : 'Включить 14 дней PRO бесплатно'}
                            </span>
                        </button>
                    ) : entitlements.isPro ? (
                        <div className="flex items-center justify-between rounded-2xl border border-emerald-500/35 bg-emerald-500/10 px-4 py-3 text-sm font-semibold text-emerald-700 dark:text-emerald-300">
                            <span className="flex items-center gap-2">
                                <CheckCircle2 className="h-4 w-4" />
                                {entitlements.isTrial
                                    ? `Триал Семейный PRO активен (осталось ${entitlements.daysRemaining ?? 0} дн.)`
                                    : entitlements.isLifetime || entitlements.expiresAt == null
                                        ? 'Подписка Семейный PRO активна навсегда'
                                        : `Подписка Семейный PRO активна (${entitlements.daysRemaining ?? 0} дн.)`}
                            </span>
                        </div>
                    ) : (
                        <div className="rounded-2xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-xs text-amber-800 dark:text-amber-300">
                            14-дневный пробный период уже был использован. Онлайн-оплата картами «Мир» и через СБП (199 ₽/мес) подключается — пока вы можете активировать промокод ниже.
                        </div>
                    )}

                    {/* Промокод */}
                    {!isLifetimePro && (
                        <div className="pt-1 text-center">
                            <button
                                type="button"
                                onClick={() => {
                                    setErrorMessage(null);
                                    setSuccessMessage(null);
                                    setShowPromoInput(true);
                                }}
                                className="mx-auto inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold text-emerald-600 hover:bg-emerald-500/10 dark:text-emerald-400 transition-colors"
                            >
                                <span>У меня есть промокод</span>
                            </button>
                        </div>
                    )}
                </div>
            </div>

            {showPromoInput && !isLifetimePro && (
                <div
                    className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/75 backdrop-blur-sm p-4"
                    onClick={handleClosePromoDialog}
                >
                    <div
                        className="w-full max-w-md rounded-3xl border border-emerald-500/35 bg-[var(--color-surface)] p-6 shadow-[0_24px_80px_rgba(0,0,0,0.5)]"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="flex items-start justify-between gap-3">
                            <div className="flex items-center gap-3">
                                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-emerald-500/30 bg-emerald-500/15 text-emerald-500">
                                    <ProCrownIcon className="h-5 w-5" />
                                </div>
                                <div>
                                    <h4 className="text-base font-extrabold text-slate-900 dark:text-[var(--color-text-main)]">
                                        Активация промокода
                                    </h4>
                                    <p className="text-xs text-slate-500 dark:text-[var(--color-text-muted)]">
                                        Введите промокод для активации тарифа Семейный PRO
                                    </p>
                                </div>
                            </div>
                            <button
                                type="button"
                                onClick={handleClosePromoDialog}
                                disabled={isRedeemingPromo}
                                aria-label="Закрыть"
                                className="rounded-xl border border-[var(--color-border-default)] bg-[var(--color-surface-soft)] p-1.5 text-slate-500 hover:text-slate-800 dark:text-[var(--color-text-muted)] dark:hover:text-white"
                            >
                                <X className="h-4 w-4" />
                            </button>
                        </div>

                        <form onSubmit={handleRedeemPromo} className="mt-4 space-y-3">
                            <input
                                type="text"
                                autoFocus
                                value={promoCode}
                                onChange={(e) => setPromoCode(e.target.value.toUpperCase())}
                                placeholder="Промокод"
                                disabled={isRedeemingPromo || Boolean(successMessage)}
                                className="w-full rounded-xl border border-[var(--color-border-default)] bg-[var(--color-surface-soft)] px-4 py-3 text-sm font-semibold uppercase tracking-wider text-slate-900 dark:text-[var(--color-text-main)] outline-none focus:border-emerald-500"
                            />

                            {errorMessage && (
                                <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 px-3.5 py-2 text-xs font-medium text-rose-600 dark:text-rose-300">
                                    {errorMessage}
                                </div>
                            )}

                            {successMessage && (
                                <div className="flex items-center gap-2 rounded-xl border border-emerald-500/40 bg-emerald-500/15 px-3.5 py-2 text-xs font-semibold text-emerald-700 dark:text-emerald-300">
                                    <CheckCircle2 className="h-4 w-4 shrink-0" />
                                    <span>{successMessage}</span>
                                </div>
                            )}

                            <div className="flex gap-2.5 pt-1">
                                <button
                                    type="button"
                                    onClick={handleClosePromoDialog}
                                    disabled={isRedeemingPromo}
                                    className="flex-1 rounded-xl border border-[var(--color-border-default)] bg-[var(--color-surface-soft)] py-2.5 text-xs font-semibold text-slate-600 dark:text-[var(--color-text-muted)]"
                                >
                                    Отмена
                                </button>
                                <button
                                    type="submit"
                                    disabled={isRedeemingPromo || !promoCode.trim() || Boolean(successMessage)}
                                    className="flex-[1.3] inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-emerald-500 to-emerald-600 py-2.5 text-xs font-bold text-white transition-opacity hover:opacity-95 disabled:opacity-50"
                                >
                                    {isRedeemingPromo ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                                    <span>{isRedeemingPromo ? 'Проверка...' : 'Применить'}</span>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ProPaywallModal;
