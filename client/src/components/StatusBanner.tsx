import React from 'react';
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react';

interface StatusBannerProps {
    children: React.ReactNode;
    className?: string;
    title?: string;
    variant: 'success' | 'error' | 'info' | 'warning';
}

const styles = {
    error: {
        container: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-900/50 dark:bg-rose-950/30 dark:text-rose-300',
        icon: 'text-rose-600 dark:text-rose-400',
        title: 'text-rose-800 dark:text-rose-200',
    },
    info: {
        container: 'border-sky-200 bg-sky-50 text-sky-700 dark:border-sky-900/50 dark:bg-sky-950/30 dark:text-sky-300',
        icon: 'text-sky-600 dark:text-sky-400',
        title: 'text-sky-800 dark:text-sky-200',
    },
    success: {
        container: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/50 dark:bg-emerald-950/30 dark:text-emerald-300',
        icon: 'text-emerald-600 dark:text-emerald-400',
        title: 'text-emerald-800 dark:text-emerald-200',
    },
    warning: {
        container: 'border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900/50 dark:bg-amber-950/30 dark:text-amber-300',
        icon: 'text-amber-600 dark:text-amber-400',
        title: 'text-amber-900 dark:text-amber-200',
    },
} as const;

const icons = {
    error: AlertCircle,
    info: Info,
    success: CheckCircle2,
    warning: TriangleAlert,
} as const;

const StatusBanner: React.FC<StatusBannerProps> = ({ children, className = '', title, variant }) => {
    const Icon = icons[variant];
    const palette = styles[variant];
    const content = (
        <>
            <div className="flex items-start gap-3">
                <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${palette.icon}`} />
                <div className="min-w-0 space-y-1">
                    {title ? <p className={`font-semibold ${palette.title}`}>{title}</p> : null}
                    <div>{children}</div>
                </div>
            </div>
        </>
    );

    if (variant === 'error') {
        return (
            <div
                role="alert"
                aria-live="assertive"
                className={`rounded-2xl border px-4 py-4 text-sm shadow-sm ${palette.container} ${className}`.trim()}
            >
                {content}
            </div>
        );
    }

    return (
        <div
            role="status"
            aria-live="polite"
            className={`rounded-2xl border px-4 py-4 text-sm shadow-sm ${palette.container} ${className}`.trim()}
        >
            {content}
        </div>
    );
};

export default StatusBanner;
