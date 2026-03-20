import axios from 'axios';
import { setToken, shouldRememberSession } from './tokenStorage';
import type { User } from '../api';

export interface RememberedSessionResponse {
    accessToken?: string;
    token?: string;
    user?: User;
}

let rememberedSessionRefreshPromise: Promise<RememberedSessionResponse | null> | null = null;

export function refreshRememberedSession() {
    if (!rememberedSessionRefreshPromise) {
        rememberedSessionRefreshPromise = axios
            .post('/api/auth/refresh', {}, { withCredentials: true })
            .then(response => {
                const data = response.data as RememberedSessionResponse;
                const refreshedToken = data.accessToken || data.token;
                if (refreshedToken) {
                    setToken(refreshedToken, shouldRememberSession());
                }
                return data;
            })
            .finally(() => {
                rememberedSessionRefreshPromise = null;
            });
    }

    return rememberedSessionRefreshPromise;
}
