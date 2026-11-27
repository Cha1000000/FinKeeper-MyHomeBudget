import React from 'react';

interface IconProps {
    className?: string;
}

export const ProCrownIcon: React.FC<IconProps> = ({ className = 'w-5 h-5' }) => (
    <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
        <defs>
            <linearGradient id="fk-pro-crown-grad" x1="2" y1="4" x2="22" y2="20" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#34D399" />
                <stop offset="55%" stopColor="#10B981" />
                <stop offset="100%" stopColor="#F59E0B" />
            </linearGradient>
            <linearGradient id="fk-pro-crown-base" x1="4" y1="17" x2="20" y2="21" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#059669" />
                <stop offset="100%" stopColor="#D97706" />
            </linearGradient>
        </defs>
        <path
            d="M3.5 17.5L2.2 8.4C2.08 7.55 3.04 7.02 3.68 7.6L7.6 11.15L11.18 5.2C11.56 4.56 12.44 4.56 12.82 5.2L16.4 11.15L20.32 7.6C20.96 7.02 21.92 7.55 21.8 8.4L20.5 17.5H3.5Z"
            fill="url(#fk-pro-crown-grad)"
            stroke="currentColor"
            strokeOpacity="0.25"
            strokeWidth="1.2"
            strokeLinejoin="round"
        />
        <rect x="3.5" y="18.2" width="17" height="2.6" rx="1.3" fill="url(#fk-pro-crown-base)" />
        <circle cx="12" cy="12.5" r="1.4" fill="#FEF3C7" />
        <circle cx="7.4" cy="14" r="1.1" fill="#A7F3D0" />
        <circle cx="16.6" cy="14" r="1.1" fill="#FDE68A" />
    </svg>
);

export const ProLockIcon: React.FC<IconProps> = ({ className = 'w-4 h-4' }) => (
    <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
        <defs>
            <linearGradient id="fk-pro-lock-body" x1="5" y1="10" x2="19" y2="21" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#34D399" />
                <stop offset="100%" stopColor="#059669" />
            </linearGradient>
        </defs>
        <path
            d="M8 10V7C8 4.79086 9.79086 3 12 3C14.2091 3 16 4.79086 16 7V10"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
        />
        <rect
            x="4.5"
            y="10"
            width="15"
            height="11"
            rx="3.2"
            fill="url(#fk-pro-lock-body)"
            fillOpacity="0.22"
            stroke="currentColor"
            strokeWidth="1.8"
        />
        <circle cx="12" cy="15.2" r="1.5" fill="currentColor" />
    </svg>
);

export const ProBurnFlameIcon: React.FC<IconProps> = ({ className = 'w-5 h-5' }) => (
    <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
        <defs>
            <linearGradient id="fk-burn-outer" x1="6" y1="2" x2="18" y2="22" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#FBBF24" />
                <stop offset="50%" stopColor="#F97316" />
                <stop offset="100%" stopColor="#E11D48" />
            </linearGradient>
            <linearGradient id="fk-burn-inner" x1="10" y1="10" x2="15" y2="21" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#FEF08A" />
                <stop offset="100%" stopColor="#FB923C" />
            </linearGradient>
        </defs>
        <path
            d="M12.8 2.4C13.2 5.4 11.1 7.3 9.7 9.1C8.3 10.9 8.9 13.1 8.9 13.1C8.9 13.1 7.2 12.1 7.1 10.1C5.2 12 4.5 14.4 5.2 16.9C6 19.8 8.7 21.8 12 21.8C15.8 21.8 19 18.8 19 14.7C19 10.1 15.8 6.5 12.8 2.4Z"
            fill="url(#fk-burn-outer)"
        />
        <path
            d="M12.2 11.2C12.5 12.8 11.3 13.8 10.6 14.9C9.8 16.1 10.4 18.2 12.2 18.8C14 19.3 15.7 17.8 15.6 15.7C15.5 13.8 13.8 12.4 12.2 11.2Z"
            fill="url(#fk-burn-inner)"
        />
    </svg>
);

export const ProExportSheetIcon: React.FC<IconProps> = ({ className = 'w-5 h-5' }) => (
    <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
        <defs>
            <linearGradient id="fk-sheet-bg" x1="4" y1="3" x2="20" y2="21" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#34D399" />
                <stop offset="100%" stopColor="#059669" />
            </linearGradient>
        </defs>
        <rect x="4" y="3" width="16" height="18" rx="3.5" fill="url(#fk-sheet-bg)" fillOpacity="0.18" stroke="currentColor" strokeWidth="1.8" />
        <path d="M8 8.5H16M8 12.5H16M8 16.5H13" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        <circle cx="16.2" cy="16.5" r="1.4" fill="#10B981" />
    </svg>
);

export const ProBackupVaultIcon: React.FC<IconProps> = ({ className = 'w-5 h-5' }) => (
    <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
        <defs>
            <linearGradient id="fk-vault-bg" x1="3" y1="4" x2="21" y2="20" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#38BDF8" />
                <stop offset="100%" stopColor="#10B981" />
            </linearGradient>
        </defs>
        <path
            d="M12 3L19.5 6.2V11.6C19.5 16.4 16.3 20.4 12 21.6C7.7 20.4 4.5 16.4 4.5 11.6V6.2L12 3Z"
            fill="url(#fk-vault-bg)"
            fillOpacity="0.2"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinejoin="round"
        />
        <path d="M9.2 12.2L11.1 14.1L15.2 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
);

export const ProDevicesIcon: React.FC<IconProps> = ({ className = 'w-5 h-5' }) => (
    <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
        <defs>
            <linearGradient id="fk-devices-bg" x1="2" y1="4" x2="22" y2="20" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#60A5FA" />
                <stop offset="100%" stopColor="#10B981" />
            </linearGradient>
        </defs>
        <rect x="2.5" y="4.5" width="14" height="10" rx="2.2" fill="url(#fk-devices-bg)" fillOpacity="0.18" stroke="currentColor" strokeWidth="1.8" />
        <path d="M6.5 18H12.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        <rect x="15.5" y="9.5" width="6" height="10" rx="1.8" fill="url(#fk-devices-bg)" fillOpacity="0.3" stroke="currentColor" strokeWidth="1.8" />
        <circle cx="18.5" cy="17.2" r="0.8" fill="currentColor" />
    </svg>
);

export const ProPinIcon: React.FC<IconProps & { active?: boolean }> = ({ className = 'w-4 h-4', active = false }) => (
    <svg viewBox="0 0 24 24" fill="none" className={className} aria-hidden="true">
        <path
            d="M15.5 3.5L20.5 8.5L18.5 10.5L15 8.8L11.5 12.3L13.5 16L12 17.5L8.2 13.7L4.2 17.7L3.5 17L7.5 13L3.7 9.2L5.2 7.7L8.9 9.7L12.4 6.2L10.7 2.7L12.7 0.7"
            fill={active ? 'currentColor' : 'none'}
            fillOpacity={active ? '0.25' : '0'}
        />
        <path
            d="M9 4.5H15L14 10.5L17 13.5V15H7V13.5L10 10.5L9 4.5Z"
            fill={active ? 'currentColor' : 'none'}
            fillOpacity={active ? '0.28' : '0'}
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinejoin="round"
        />
        <path d="M12 15V20.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
);
