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
        container: 'border-rose-200 bg-rose-50 text-rose-700',
        icon: 'text-rose-600',
        title: 'text-rose-800',
    },
    info: {
        container: 'border-sky-200 bg-sky-50 text-sky-700',
        icon: 'text-sky-600',
        title: 'text-sky-800',
    },
    success: {
        container: 'border-emerald-200 bg-emerald-50 text-emerald-700',
        icon: 'text-emerald-600',
        title: 'text-emerald-800',
    },
    warning: {
        container: 'border-amber-200 bg-amber-50 text-amber-800',
        icon: 'text-amber-600',
        title: 'text-amber-900',
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
