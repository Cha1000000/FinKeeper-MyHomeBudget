import React from 'react';
import { AlertCircle, Inbox, Loader2, RefreshCw } from 'lucide-react';

interface PageStateProps {
    actionLabel?: string;
    className?: string;
    compact?: boolean;
    description: string;
    onAction?: () => void;
    title: string;
    variant: 'loading' | 'error' | 'empty';
}

const styles = {
    empty: {
        accent: 'bg-slate-100 text-slate-500 border-slate-200 dark:bg-slate-800/50 dark:text-slate-400 dark:border-slate-700',
        button: 'bg-slate-900 text-white hover:bg-slate-800 dark:bg-slate-700 dark:hover:bg-slate-600',
        container: 'border-slate-100 bg-white/90 dark:border-[var(--color-border-default)] dark:bg-[var(--color-surface)]/90',
        description: 'text-slate-500 dark:text-[var(--color-text-muted)]',
        title: 'text-slate-800 dark:text-[var(--color-text-main)]',
    },
    error: {
        accent: 'bg-rose-100 text-rose-600 border-rose-200 dark:bg-rose-950/30 dark:text-rose-400 dark:border-rose-900/50',
        button: 'bg-rose-600 text-white hover:bg-rose-700 dark:bg-rose-700 dark:hover:bg-rose-600',
        container: 'border-rose-100 bg-white/90 dark:border-rose-900/50 dark:bg-[var(--color-surface)]/90',
        description: 'text-rose-600 dark:text-rose-400',
        title: 'text-slate-800 dark:text-[var(--color-text-main)]',
    },
    loading: {
        accent: 'bg-blue-100 text-blue-600 border-blue-200 dark:bg-blue-950/30 dark:text-blue-400 dark:border-blue-900/50',
        button: 'bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-700 dark:hover:bg-blue-600',
        container: 'border-blue-100 bg-white/90 dark:border-blue-900/50 dark:bg-[var(--color-surface)]/90',
        description: 'text-slate-500 dark:text-[var(--color-text-muted)]',
        title: 'text-slate-800 dark:text-[var(--color-text-main)]',
    },
} as const;

const icons = {
    empty: Inbox,
    error: AlertCircle,
    loading: Loader2,
} as const;

const PageState: React.FC<PageStateProps> = ({ actionLabel, className = '', compact = false, description, onAction, title, variant }) => {
    const Icon = icons[variant];
    const palette = styles[variant];
    const wrapperClassName = compact
        ? `rounded-2xl border p-5 text-center shadow-[0_8px_24px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_24px_rgba(0,0,0,0.2)] backdrop-blur-md ${palette.container}`
        : `rounded-3xl border p-8 text-center shadow-[0_8px_30px_rgb(0,0,0,0.04)] dark:shadow-[0_8px_30px_rgba(0,0,0,0.2)] backdrop-blur-md ${palette.container}`;
    const accentClassName = compact
        ? `mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-2xl border ${palette.accent}`
        : `mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl border ${palette.accent}`;
    const iconClassName = compact
        ? `h-5 w-5 ${variant === 'loading' ? 'animate-spin' : ''}`
        : `h-7 w-7 ${variant === 'loading' ? 'animate-spin' : ''}`;
    const titleClassName = compact ? `text-base font-bold ${palette.title}` : `text-lg font-bold ${palette.title}`;
    const descriptionClassName = compact
        ? `mx-auto mt-2 max-w-md text-sm ${palette.description}`
        : `mx-auto mt-2 max-w-md text-sm ${palette.description}`;
    const buttonClassName = compact
        ? `mx-auto mt-4 inline-flex items-center gap-2 rounded-xl px-4 py-2 text-sm font-medium transition-colors ${palette.button}`
        : `mx-auto mt-5 inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-colors ${palette.button}`;

    return (
        <div className={`${wrapperClassName} ${className}`.trim()}>
            <div className={accentClassName}>
                <Icon className={iconClassName} />
            </div>
            <h3 className={titleClassName}>{title}</h3>
            <p className={descriptionClassName}>{description}</p>
            {actionLabel && onAction ? (
                <button
                    onClick={onAction}
                    className={buttonClassName}
                >
                    <RefreshCw className="h-4 w-4" />
                    {actionLabel}
                </button>
            ) : null}
        </div>
    );
};

export default PageState;
