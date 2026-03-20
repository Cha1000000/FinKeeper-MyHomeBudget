import React, { createContext, useContext, useEffect, useState } from 'react';
import { useAuth } from './AuthContext';

type Theme = 'light' | 'dark' | 'night' | 'system';

interface ThemeContextType {
  theme: Theme;
  resolvedTheme: 'light' | 'dark' | 'night';
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { uiSettings } = useAuth();
  const theme = uiSettings.theme || 'light';
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark' | 'night'>('light');

  useEffect(() => {
    const root = window.document.documentElement;

    const applyTheme = (currentTheme: Theme) => {
      let effectiveTheme: 'light' | 'dark' | 'night';

      if (currentTheme === 'system') {
        effectiveTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
      } else {
        effectiveTheme = currentTheme;
      }

      root.setAttribute('data-theme', effectiveTheme);
      
      // Handle standard Tailwind dark class for both dark modes
      if (effectiveTheme === 'dark' || effectiveTheme === 'night') {
        root.classList.add('dark');
      } else {
        root.classList.remove('dark');
      }
      
      setResolvedTheme(effectiveTheme);
    };

    applyTheme(theme);

    if (theme === 'system') {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      const handleChange = () => applyTheme('system');
      mediaQuery.addEventListener('change', handleChange);
      return () => mediaQuery.removeEventListener('change', handleChange);
    }
  }, [theme]);

  return (
    <ThemeContext.Provider value={{ theme, resolvedTheme }}>
      {children}
    </ThemeContext.Provider>
  );
};

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (context === undefined) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};
