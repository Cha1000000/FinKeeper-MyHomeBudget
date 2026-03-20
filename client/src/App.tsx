import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import MonthView from './pages/MonthView';
import Categories from './pages/Categories';
import Savings from './pages/Savings';
import Settings from './pages/Settings';
import Login from './pages/Login';
import PasswordRecovery from './pages/PasswordRecovery';
import EmailVerification from './pages/EmailVerification';
import SocialAuthCallback from './pages/SocialAuthCallback';
import { AuthProvider, useAuth } from './context/AuthContext';
import { ThemeProvider } from './context/ThemeContext';
import PageState from './components/PageState';

const RequireAuth: React.FC<{ children: React.ReactElement }> = ({ children }) => {
    const { user, isLoading } = useAuth();
    const location = useLocation();

    if (isLoading) {
        return (
            <div className="flex min-h-screen items-center justify-center bg-slate-50/70 px-4 py-10">
                <div className="w-full max-w-lg">
                    <PageState
                        variant="loading"
                        title="Проверяем сессию"
                        description="Подготавливаем доступ к приложению и восстанавливаем авторизацию, если это возможно."
                    />
                </div>
            </div>
        );
    }

    if (!user) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    return children;
};

const AppRoutes: React.FC = () => {
    return (
        <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/password-recovery" element={<PasswordRecovery />} />
            <Route path="/verify-email" element={<EmailVerification />} />
            <Route path="/auth/google/callback" element={<SocialAuthCallback />} />
            <Route path="/auth/yandex/callback" element={<SocialAuthCallback />} />
            
            <Route path="/" element={
                <RequireAuth>
                    <Layout />
                </RequireAuth>
            }>
                <Route index element={<Dashboard />} />
                <Route path="month" element={<MonthView />} />
                <Route path="categories" element={<Categories />} />
                <Route path="savings" element={<Savings />} />
                <Route path="settings" element={<Settings />} />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
        </Routes>
    );
};

const App: React.FC = () => {
  return (
    <AuthProvider>
        <ThemeProvider>
            <BrowserRouter>
                <AppRoutes />
            </BrowserRouter>
        </ThemeProvider>
    </AuthProvider>
  );
};

export default App;
