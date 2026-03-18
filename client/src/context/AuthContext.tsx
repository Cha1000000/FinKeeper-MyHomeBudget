/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import axios from 'axios';
import { exchangeSocialAuthCode, getMe, loginUser, registerUser } from '../api';
import type { User, AuthData } from '../api';
import {
  AUTH_LOGOUT_REQUIRED_EVENT,
  clearToken,
  getToken,
  setRememberSession,
  setToken,
  shouldRememberSession,
} from '../auth/tokenStorage';

interface UiSettings {
  autoCollapseSidebar: boolean;
}

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  login: (data: AuthData) => Promise<void>;
  register: (data: AuthData) => Promise<void>;
  completeSocialLogin: (code: string) => Promise<void>;
  logout: () => void;
  updateUser: (data: Partial<User>) => void;
  uiSettings: UiSettings;
  updateUiSettings: (settings: Partial<UiSettings>) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);
let rememberSessionBootstrapPromise: Promise<{ accessToken?: string; token?: string; user?: User } | null> | null = null;

function bootstrapRememberedSession() {
  if (!rememberSessionBootstrapPromise) {
    rememberSessionBootstrapPromise = axios
      .post('/api/auth/refresh', {}, { withCredentials: true })
      .then(response => response.data as { accessToken?: string; token?: string; user?: User })
      .finally(() => {
        rememberSessionBootstrapPromise = null;
      });
  }

  return rememberSessionBootstrapPromise;
}

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [uiSettings, setUiSettings] = useState<UiSettings>(() => {
      const saved = localStorage.getItem('uiSettings');
      return saved ? JSON.parse(saved) : { autoCollapseSidebar: false };
  });

  useEffect(() => {
    const checkAuth = async () => {
      const token = getToken();
      if (token) {
        try {
          const { data } = await getMe();
          setUser(data);
        } catch (error) {
          console.error("Auth check failed", error);
          clearToken();
          setUser(null);
        }
      } else if (shouldRememberSession()) {
        try {
          const data = await bootstrapRememberedSession();
          const refreshedToken = data?.accessToken || data?.token;
          if (refreshedToken) {
            setToken(refreshedToken, true);
          }
          if (data?.user) {
            setUser(data.user);
          }
        } catch (error) {
          console.error("Auth refresh bootstrap failed", error);
          clearToken();
          setUser(null);
        }
      }
      setIsLoading(false);
    };

    checkAuth();
  }, []);

  useEffect(() => {
    const handleLogoutRequired = () => {
      clearToken();
      setUser(null);
    };

    window.addEventListener(AUTH_LOGOUT_REQUIRED_EVENT, handleLogoutRequired);
    return () => window.removeEventListener(AUTH_LOGOUT_REQUIRED_EVENT, handleLogoutRequired);
  }, []);

  const login = async (formData: AuthData) => {
    const { data } = await loginUser(formData);
    const persist = formData.rememberMe === true;
    setRememberSession(persist);
    setToken(data.token, persist);
    setUser(data.user);
  };

  const register = async (formData: AuthData) => {
    const { data } = await registerUser(formData);
    setRememberSession(false);
    setToken(data.token, false);
    setUser(data.user);
  };

  const completeSocialLogin = async (code: string) => {
    const { data } = await exchangeSocialAuthCode(code);
    setToken(data.token, shouldRememberSession());
    setUser(data.user);
  };

  const logout = useCallback(() => {
    axios.post('/api/auth/logout', {}, { withCredentials: true }).catch(() => {});
    setRememberSession(false);
    clearToken();
    setUser(null);
  }, []);

  const updateUser = useCallback((data: Partial<User>) => {
    setUser(prev => prev ? { ...prev, ...data } : null);
  }, []);

  const updateUiSettings = (newSettings: Partial<UiSettings>) => {
    setUiSettings(prev => {
        const updated = { ...prev, ...newSettings };
        localStorage.setItem('uiSettings', JSON.stringify(updated));
        return updated;
    });
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, register, completeSocialLogin, logout, updateUser, uiSettings, updateUiSettings }}>
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