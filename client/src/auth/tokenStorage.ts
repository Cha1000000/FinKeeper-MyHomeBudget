const TOKEN_KEY = 'token';
const AUTH_NOTICE_KEY = 'auth_notice';
const REMEMBER_ME_KEY = 'remember_me';

export const AUTH_TOKEN_CHANGED_EVENT = 'auth:token-changed';
export const AUTH_LOGOUT_REQUIRED_EVENT = 'auth:logout-required';

let memoryToken: string | null = null;

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

export const getToken = (): string | null => {
    if (memoryToken) {
        return memoryToken;
    }

    const sessionToken = readFromSession();
    if (sessionToken) {
        memoryToken = sessionToken;
        return sessionToken;
    }

    if (shouldRememberSession()) {
        const localToken = readFromLocal();
        if (localToken) {
            memoryToken = localToken;
            return localToken;
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
    getSessionStorage()?.removeItem(TOKEN_KEY);
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
