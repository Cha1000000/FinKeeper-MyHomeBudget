import React from 'react';
import { AlertCircle, Inbox, Loader2, RefreshCw } from 'lucide-react';

interface PageStateProps {
    actionLabel?: string;
    description: string;
    onAction?: () => void;
    title: string;
    variant: 'loading' | 'error' | 'empty';
}

const styles = {
    empty: {
        accent: 'bg-slate-100 text-slate-500 border-slate-200',
        button: 'bg-slate-900 text-white hover:bg-slate-800',
        container: 'border-slate-100 bg-white/90',
        description: 'text-slate-500',
        title: 'text-slate-800',
    },
    error: {
        accent: 'bg-rose-100 text-rose-600 border-rose-200',
        button: 'bg-rose-600 text-white hover:bg-rose-700',
        container: 'border-rose-100 bg-white/90',
        description: 'text-rose-600',
        title: 'text-slate-800',
    },
    loading: {
        accent: 'bg-blue-100 text-blue-600 border-blue-200',
        button: 'bg-blue-600 text-white hover:bg-blue-700',
        container: 'border-blue-100 bg-white/90',
        description: 'text-slate-500',
        title: 'text-slate-800',
    },
} as const;

const icons = {
    empty: Inbox,
    error: AlertCircle,
    loading: Loader2,
} as const;

const PageState: React.FC<PageStateProps> = ({ actionLabel, description, onAction, title, variant }) => {
    const Icon = icons[variant];
    const palette = styles[variant];

    return (
        <div className={`rounded-3xl border p-8 text-center shadow-[0_8px_30px_rgb(0,0,0,0.04)] backdrop-blur-md ${palette.container}`}>
            <div className={`mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-2xl border ${palette.accent}`}>
                <Icon className={`h-7 w-7 ${variant === 'loading' ? 'animate-spin' : ''}`} />
            </div>
            <h3 className={`text-lg font-bold ${palette.title}`}>{title}</h3>
            <p className={`mx-auto mt-2 max-w-md text-sm ${palette.description}`}>{description}</p>
            {actionLabel && onAction ? (
                <button
                    onClick={onAction}
                    className={`mx-auto mt-5 inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-sm font-medium transition-colors ${palette.button}`}
                >
                    <RefreshCw className="h-4 w-4" />
                    {actionLabel}
                </button>
            ) : null}
        </div>
    );
};

export default PageState;
