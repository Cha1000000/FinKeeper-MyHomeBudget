/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import axios from 'axios';
import { exchangeSocialAuthCode, getBillingStatus, getMe, loginUser, registerUser } from '../api';
import type { User, AuthData, Entitlements } from '../api';
import { refreshRememberedSession } from '../auth/sessionRecovery';
import { readLocalStorageJson } from '../utils';
import {
  AUTH_LOGOUT_REQUIRED_EVENT,
  clearToken,
  getToken,
  getTokenExpiryMs,
  setRememberSession,
  setToken,
  shouldRememberSession,
} from '../auth/tokenStorage';

export interface UiSettings {
  autoCollapseSidebar: boolean;
  theme: 'light' | 'dark' | 'night' | 'system';
}

const DEFAULT_UI_SETTINGS: UiSettings = {
  autoCollapseSidebar: false,
  theme: 'light',
};

export const DEFAULT_FREE_ENTITLEMENTS: Entitlements = {
  plan: 'free',
  status: 'active',
  isPro: false,
  isTrial: false,
  isLifetime: false,
  trialUsed: false,
  canStartTrial: true,
  startedAt: null,
  expiresAt: null,
  daysRemaining: null,
  source: 'system',
  features: {
    exportReports: false,
    extendedAnalytics: false,
    extendedBackups: false,
    unlimitedDevices: false,
    familyAccess: false,
    prioritySupport: false,
  },
  limits: {
    maxBackups: 5,
    maxDevices: 2,
    analyticsMonths: 6,
    maxFamilyMembers: 1,
  },
};

interface AuthContextType {
  user: User | null;
  entitlements: Entitlements;
  isLoading: boolean;
  login: (data: AuthData) => Promise<void>;
  register: (data: AuthData) => Promise<void>;
  completeSocialLogin: (code: string) => Promise<void>;
  logout: () => void;
  updateUser: (data: Partial<User>) => void;
  refreshEntitlements: () => Promise<Entitlements | null>;
  isPaywallOpen: boolean;
  paywallFeature: string | null;
  openPaywall: (highlightFeature?: string) => void;
  closePaywall: () => void;
  uiSettings: UiSettings;
  updateUiSettings: (settings: Partial<UiSettings>) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isPaywallOpen, setIsPaywallOpen] = useState(false);
  const [paywallFeature, setPaywallFeature] = useState<string | null>(null);
  const [uiSettings, setUiSettings] = useState<UiSettings>(() => {
    const parsed = readLocalStorageJson<Partial<UiSettings>>('uiSettings', DEFAULT_UI_SETTINGS);
    return {
      autoCollapseSidebar: typeof parsed.autoCollapseSidebar === 'boolean' ? parsed.autoCollapseSidebar : false,
      theme: parsed.theme === 'dark' || parsed.theme === 'night' || parsed.theme === 'system' ? parsed.theme : 'light',
    };
  });

  const proactiveTimerRef = useRef<number | null>(null);
  const scheduleProactiveRefreshRef = useRef<() => void>(() => {});

  const clearProactiveTimer = useCallback(() => {
    if (proactiveTimerRef.current !== null) {
      window.clearTimeout(proactiveTimerRef.current);
      proactiveTimerRef.current = null;
    }
  }, []);

  const scheduleProactiveRefresh = useCallback(() => {
    clearProactiveTimer();
    const remainingMs = getTokenExpiryMs();
    if (!remainingMs || remainingMs <= 0 || !shouldRememberSession()) return;
    const refreshAt = Math.max(remainingMs * 0.8, remainingMs - 60 * 60 * 1000);
    if (refreshAt <= 0) return;
    proactiveTimerRef.current = window.setTimeout(async () => {
      proactiveTimerRef.current = null;
      try {
        await refreshRememberedSession();
        scheduleProactiveRefreshRef.current();
      } catch (e) {
        console.warn('Proactive token refresh failed, will retry in 5 min', e);
        proactiveTimerRef.current = window.setTimeout(() => {
          proactiveTimerRef.current = null;
          scheduleProactiveRefreshRef.current();
        }, 5 * 60 * 1000);
      }
    }, refreshAt);
  }, [clearProactiveTimer]);

  useEffect(() => {
    scheduleProactiveRefreshRef.current = scheduleProactiveRefresh;
  }, [scheduleProactiveRefresh]);

  useEffect(() => {
    let retryTimer: ReturnType<typeof setTimeout> | null = null;
    let cancelled = false;

    const tryRefreshSession = async (): Promise<boolean> => {
      try {
        const data = await refreshRememberedSession();
        if (cancelled) return true;
        if (data?.user) {
          setUser(data.user);
        } else {
          const me = await getMe();
          if (cancelled) return true;
          setUser(me.data);
        }
        return true;
      } catch (error: unknown) {
        const status = axios.isAxiosError(error) ? error.response?.status : undefined;
        if (status === 401 || status === 403) {
          clearToken();
          if (!cancelled) setUser(null);
          return true;
        }
        return false;
      }
    };

    const checkAuth = async () => {
      const token = getToken();
      if (token) {
        try {
          const { data } = await getMe();
          if (cancelled) return;
          setUser(data);
          setIsLoading(false);
          scheduleProactiveRefresh();
          return;
        } catch (error: unknown) {
          console.error('Auth check failed', error);
          const resolved = await tryRefreshSession();
          if (cancelled) return;
          if (resolved) {
            setIsLoading(false);
            scheduleProactiveRefresh();
            return;
          }
        }
      } else if (shouldRememberSession()) {
        const resolved = await tryRefreshSession();
        if (cancelled) return;
        if (resolved) {
          setIsLoading(false);
          scheduleProactiveRefresh();
          return;
        }
      } else {
        if (!cancelled) setIsLoading(false);
        return;
      }
      if (!cancelled) {
        console.warn('Auth check failed due to network error, retrying in 3s...');
        retryTimer = setTimeout(() => {
          void checkAuth();
        }, 3000);
      }
    };

    void checkAuth();

    return () => {
      cancelled = true;
      if (retryTimer) clearTimeout(retryTimer);
      clearProactiveTimer();
    };
  }, [clearProactiveTimer, scheduleProactiveRefresh]);

  useEffect(() => {
    const handleLogoutRequired = () => {
      clearProactiveTimer();
      clearToken();
      setUser(null);
    };

    window.addEventListener(AUTH_LOGOUT_REQUIRED_EVENT, handleLogoutRequired);
    return () => window.removeEventListener(AUTH_LOGOUT_REQUIRED_EVENT, handleLogoutRequired);
  }, [clearProactiveTimer]);

  const login = async (formData: AuthData) => {
    const { data } = await loginUser(formData);
    const persist = formData.rememberMe === true;
    setRememberSession(persist);
    setToken(data.token, persist);
    setUser(data.user);
    scheduleProactiveRefresh();
  };

  const register = async (formData: AuthData) => {
    const { data } = await registerUser(formData);
    setRememberSession(false);
    setToken(data.token, false);
    setUser(data.user);
  };

  const completeSocialLogin = async (code: string) => {
    const rememberMe = shouldRememberSession();
    const { data } = await exchangeSocialAuthCode(code, rememberMe);
    setToken(data.token, rememberMe);
    setUser(data.user);
    if (rememberMe) {
      scheduleProactiveRefresh();
    }
  };

  const logout = useCallback(() => {
    clearProactiveTimer();
    axios.post('/api/auth/logout', {}, { withCredentials: true }).catch(() => {});
    setRememberSession(false);
    clearToken();
    setUser(null);
  }, [clearProactiveTimer]);

  const updateUser = useCallback((data: Partial<User>) => {
    setUser(prev => prev ? { ...prev, ...data } : null);
  }, []);

  const refreshEntitlements = useCallback(async (): Promise<Entitlements | null> => {
    try {
      const { data } = await getBillingStatus();
      setUser(prev => prev ? { ...prev, entitlements: data.entitlements } : null);
      return data.entitlements;
    } catch {
      return null;
    }
  }, []);

  const openPaywall = useCallback((highlightFeature?: string) => {
    setPaywallFeature(highlightFeature ?? null);
    setIsPaywallOpen(true);
  }, []);

  const closePaywall = useCallback(() => {
    setIsPaywallOpen(false);
    setPaywallFeature(null);
  }, []);

  useEffect(() => {
    const handleEntitlementUpdated = (e: Event) => {
      const nextEntitlements = (e as CustomEvent<Entitlements>).detail;
      if (nextEntitlements) {
        setUser(prev => prev ? { ...prev, entitlements: nextEntitlements } : null);
      } else {
        void refreshEntitlements();
      }
    };

    window.addEventListener('ws:entitlement_updated', handleEntitlementUpdated);
    return () => window.removeEventListener('ws:entitlement_updated', handleEntitlementUpdated);
  }, [refreshEntitlements]);

  const updateUiSettings = (newSettings: Partial<UiSettings>) => {
    setUiSettings(prev => {
      const updated = { ...prev, ...newSettings };
      localStorage.setItem('uiSettings', JSON.stringify(updated));
      return updated;
    });
  };

  const entitlements = user?.entitlements ?? DEFAULT_FREE_ENTITLEMENTS;

  return (
    <AuthContext.Provider value={{
      user,
      entitlements,
      isLoading,
      login,
      register,
      completeSocialLogin,
      logout,
      updateUser,
      refreshEntitlements,
      isPaywallOpen,
      paywallFeature,
      openPaywall,
      closePaywall,
      uiSettings,
      updateUiSettings,
    }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

export const useEntitlements = () => {
  const { entitlements, openPaywall, closePaywall, refreshEntitlements } = useAuth();
  return {
    entitlements,
    isPro: entitlements.isPro,
    isTrial: entitlements.isTrial,
    canStartTrial: entitlements.canStartTrial,
    openPaywall,
    closePaywall,
    refreshEntitlements,
  };
};
