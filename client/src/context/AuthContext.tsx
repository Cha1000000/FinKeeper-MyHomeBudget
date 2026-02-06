/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, useEffect } from 'react';
import { getMe, loginUser, registerUser } from '../api';
import type { User, AuthData } from '../api';

interface UiSettings {
  autoCollapseSidebar: boolean;
}

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  login: (data: AuthData) => Promise<void>;
  register: (data: AuthData) => Promise<void>;
  logout: () => void;
  updateUser: (data: Partial<User>) => void;
  uiSettings: UiSettings;
  updateUiSettings: (settings: Partial<UiSettings>) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [uiSettings, setUiSettings] = useState<UiSettings>(() => {
      const saved = localStorage.getItem('uiSettings');
      return saved ? JSON.parse(saved) : { autoCollapseSidebar: false };
  });

  useEffect(() => {
    const checkAuth = async () => {
      const token = localStorage.getItem('token');
      if (token) {
        try {
          const { data } = await getMe();
          setUser(data);
        } catch (error) {
          console.error("Auth check failed", error);
          localStorage.removeItem('token');
          setUser(null);
        }
      }
      setIsLoading(false);
    };

    checkAuth();
  }, []);

  const login = async (formData: AuthData) => {
    const { data } = await loginUser(formData);
    localStorage.setItem('token', data.token);
    setUser(data.user);
  };

  const register = async (formData: AuthData) => {
    const { data } = await registerUser(formData);
    localStorage.setItem('token', data.token);
    setUser(data.user);
  };

  const logout = () => {
    localStorage.removeItem('token');
    setUser(null);
  };

  const updateUser = (data: Partial<User>) => {
    setUser(prev => prev ? { ...prev, ...data } : null);
  };

  const updateUiSettings = (newSettings: Partial<UiSettings>) => {
    setUiSettings(prev => {
        const updated = { ...prev, ...newSettings };
        localStorage.setItem('uiSettings', JSON.stringify(updated));
        return updated;
    });
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, register, logout, updateUser, uiSettings, updateUiSettings }}>
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