import type { User } from '../api';

const SECURITY_ONBOARDING_STORAGE_KEY = 'onboarding_security_prompt_v1';
const SECURITY_ONBOARDING_SESSION_KEY = 'onboarding_security_prompt_session_v1';
const SECURITY_ONBOARDING_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000;

type RecoverabilityStatus = User['recoverabilityStatus'];

interface SecurityOnboardingState {
    lastShownAt: number | null;
    dismissCount: number;
    lastStatus: RecoverabilityStatus | null;
}

const DEFAULT_STATE: SecurityOnboardingState = {
    lastShownAt: null,
    dismissCount: 0,
    lastStatus: null,
};

const canUseBrowserStorage = () => typeof window !== 'undefined';

const readSecurityOnboardingState = (): SecurityOnboardingState => {
    if (!canUseBrowserStorage()) {
        return DEFAULT_STATE;
    }

    const rawValue = window.localStorage.getItem(SECURITY_ONBOARDING_STORAGE_KEY);
    if (!rawValue) {
        return DEFAULT_STATE;
    }

    try {
        const parsed = JSON.parse(rawValue) as Partial<SecurityOnboardingState>;
        return {
            lastShownAt: typeof parsed.lastShownAt === 'number' ? parsed.lastShownAt : null,
            dismissCount: typeof parsed.dismissCount === 'number' ? parsed.dismissCount : 0,
            lastStatus: parsed.lastStatus === 'protected' || parsed.lastStatus === 'unprotected' ? parsed.lastStatus : null,
        };
    } catch {
        return DEFAULT_STATE;
    }
};

const writeSecurityOnboardingState = (state: SecurityOnboardingState) => {
    if (!canUseBrowserStorage()) {
        return;
    }

    window.localStorage.setItem(SECURITY_ONBOARDING_STORAGE_KEY, JSON.stringify(state));
};

const hasSecurityPromptBeenShownThisSession = () => {
    if (!canUseBrowserStorage()) {
        return false;
    }

    return window.sessionStorage.getItem(SECURITY_ONBOARDING_SESSION_KEY) === 'shown';
};

const markSecurityPromptShownThisSession = () => {
    if (!canUseBrowserStorage()) {
        return;
    }

    window.sessionStorage.setItem(SECURITY_ONBOARDING_SESSION_KEY, 'shown');
};

export const shouldShowSecurityOnboardingPrompt = (recoverabilityStatus: RecoverabilityStatus) => {
    if (recoverabilityStatus !== 'unprotected') {
        return false;
    }

    const state = readSecurityOnboardingState();
    if (state.lastShownAt === null) {
        return true;
    }

    if (hasSecurityPromptBeenShownThisSession()) {
        return false;
    }

    return Date.now() - state.lastShownAt >= SECURITY_ONBOARDING_COOLDOWN_MS;
};

export const markSecurityOnboardingPromptShown = (recoverabilityStatus: RecoverabilityStatus) => {
    if (recoverabilityStatus !== 'unprotected') {
        return;
    }

    const state = readSecurityOnboardingState();
    writeSecurityOnboardingState({
        ...state,
        lastStatus: recoverabilityStatus,
    });
    markSecurityPromptShownThisSession();
};

export const dismissSecurityOnboardingPrompt = (recoverabilityStatus: RecoverabilityStatus) => {
    if (recoverabilityStatus !== 'unprotected') {
        return;
    }

    const state = readSecurityOnboardingState();
    writeSecurityOnboardingState({
        lastShownAt: Date.now(),
        dismissCount: state.dismissCount + 1,
        lastStatus: recoverabilityStatus,
    });
    markSecurityPromptShownThisSession();
};

export const resetSecurityOnboardingPromptState = () => {
    if (!canUseBrowserStorage()) {
        return;
    }

    window.localStorage.removeItem(SECURITY_ONBOARDING_STORAGE_KEY);
    window.sessionStorage.removeItem(SECURITY_ONBOARDING_SESSION_KEY);
};
