import React, { useEffect } from 'react';
import { X } from 'lucide-react';

interface ModalProps {
    isOpen: boolean;
    onClose: () => void;
    title: string;
    children: React.ReactNode;
}

const Modal: React.FC<ModalProps> = ({ isOpen, onClose, title, children }) => {
    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        if (isOpen) window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/40 dark:bg-black/60 backdrop-blur-sm">
            <div className="bg-[var(--color-surface)] dark:bg-[var(--color-surface)] backdrop-blur-xl rounded-3xl shadow-[0_20px_60px_-15px_rgba(0,0,0,0.3)] border border-[var(--color-border-default)] dark:border-[var(--color-border-default)] w-full max-w-lg overflow-hidden animate-in fade-in zoom-in duration-200">
                <div className="flex justify-between items-center p-5 border-b border-[var(--color-border-default)] dark:border-[var(--color-border-default)] bg-white/40 dark:bg-[var(--color-surface-soft)]/40">
                    <h3 className="text-xl font-bold text-slate-800 dark:text-[var(--color-text-main)] tracking-tight">{title}</h3>
                    <button onClick={onClose} className="p-2 hover:bg-slate-200/50 dark:hover:bg-slate-700/50 rounded-xl transition-colors text-slate-400 hover:text-slate-600 dark:hover:text-[#e8f5ec]" aria-label="Закрыть" title="Закрыть">
                        <X className="w-5 h-5" />
                    </button>
                </div>
                <div className="p-6 text-slate-600 dark:text-[var(--color-text-muted)]">
                    {children}
                </div>
            </div>
        </div>
    );
};

export default Modal;
