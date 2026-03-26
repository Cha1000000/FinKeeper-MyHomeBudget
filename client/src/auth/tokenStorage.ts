const TOKEN_KEY = 'token';
const AUTH_NOTICE_KEY = 'auth_notice';
const REMEMBER_ME_KEY = 'remember_me';

export const AUTH_TOKEN_CHANGED_EVENT = 'auth:token-changed';
export const AUTH_LOGOUT_REQUIRED_EVENT = 'auth:logout-required';

let memoryToken: string | null = null;

const decodeBase64Url = (value: string): string | null => {
    try {
        const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
        const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
        return typeof window !== 'undefined' ? window.atob(padded) : null;
    } catch {
        return null;
    }
};

const isTokenUsable = (token: string | null): token is string => {
    if (!token) {
        return false;
    }

    const parts = token.split('.');
    if (parts.length !== 3) {
        return false;
    }

    const payloadJson = decodeBase64Url(parts[1]);
    if (!payloadJson) {
        return false;
    }

    try {
        const payload = JSON.parse(payloadJson) as { exp?: number };
        if (typeof payload.exp !== 'number') {
            return false;
        }

        return payload.exp * 1000 > Date.now();
    } catch {
        return false;
    }
};

export const getTokenExpiryMs = (): number | null => {
    const token = getToken();
    if (!token) return null;

    const parts = token.split('.');
    if (parts.length !== 3) return null;

    const payloadJson = decodeBase64Url(parts[1]);
    if (!payloadJson) return null;

    try {
        const payload = JSON.parse(payloadJson) as { exp?: number };
        if (typeof payload.exp !== 'number') return null;
        return payload.exp * 1000 - Date.now();
    } catch {
        return null;
    }
};

const getSessionStorage = (): Storage | null => {
    if (typeof window === 'undefined') {
        return null;
    }

    return window.sessionStorage;
};

const getLocalStorage = (): Storage | null => {
    if (typeof window === 'undefined') {
        return null;
    }

    return window.localStorage;
};

const readFromSession = (): string | null => {
    const storage = getSessionStorage();
    return storage?.getItem(TOKEN_KEY) ?? null;
};

const readFromLocal = (): string | null => {
    const storage = getLocalStorage();
    return storage?.getItem(TOKEN_KEY) ?? null;
};

const clearLegacyLocalToken = () => {
    const storage = getLocalStorage();
    storage?.removeItem(TOKEN_KEY);
};

const clearSessionToken = () => {
    getSessionStorage()?.removeItem(TOKEN_KEY);
};

export const getToken = (): string | null => {
    if (isTokenUsable(memoryToken)) {
        return memoryToken;
    }

    memoryToken = null;

    const sessionToken = readFromSession();
    if (isTokenUsable(sessionToken)) {
        memoryToken = sessionToken;
        return sessionToken;
    }

    if (sessionToken) {
        clearSessionToken();
    }

    if (shouldRememberSession()) {
        const localToken = readFromLocal();
        if (isTokenUsable(localToken)) {
            memoryToken = localToken;
            return localToken;
        }

        if (localToken) {
            clearLegacyLocalToken();
        }
    }

    return null;
};

export const shouldRememberSession = (): boolean => {
    return getLocalStorage()?.getItem(REMEMBER_ME_KEY) === '1';
};

export const setRememberSession = (enabled: boolean) => {
    const storage = getLocalStorage();
    if (!storage) {
        return;
    }

    if (enabled) {
        storage.setItem(REMEMBER_ME_KEY, '1');
        return;
    }

    storage.removeItem(REMEMBER_ME_KEY);
};

export const setToken = (token: string, persist = shouldRememberSession()) => {
    memoryToken = token;
    if (persist) {
        getLocalStorage()?.setItem(TOKEN_KEY, token);
        getSessionStorage()?.removeItem(TOKEN_KEY);
    } else {
        getSessionStorage()?.setItem(TOKEN_KEY, token);
        clearLegacyLocalToken();
    }
    if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event(AUTH_TOKEN_CHANGED_EVENT));
    }
};

export const clearToken = () => {
    memoryToken = null;
    clearSessionToken();
    clearLegacyLocalToken();
    if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event(AUTH_TOKEN_CHANGED_EVENT));
    }
};

export const setAuthNotice = (notice: string) => {
    getSessionStorage()?.setItem(AUTH_NOTICE_KEY, notice);
};

export const getAuthNotice = (): string | null => {
    return getSessionStorage()?.getItem(AUTH_NOTICE_KEY) ?? null;
};

export const clearAuthNotice = () => {
    getSessionStorage()?.removeItem(AUTH_NOTICE_KEY);
};
