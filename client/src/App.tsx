import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import MonthView from './pages/MonthView';
import Categories from './pages/Categories';
import Savings from './pages/Savings';
import Login from './pages/Login';
import { AuthProvider, useAuth } from './context/AuthContext';

const RequireAuth: React.FC<{ children: JSX.Element }> = ({ children }) => {
    const { user, isLoading } = useAuth();
    const location = useLocation();

    if (isLoading) {
        return <div className="flex justify-center items-center h-screen">Загрузка...</div>;
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
            
            <Route path="/" element={
                <RequireAuth>
                    <Layout />
                </RequireAuth>
            }>
                <Route index element={<Dashboard />} />
                <Route path="month" element={<MonthView />} />
                <Route path="categories" element={<Categories />} />
                <Route path="savings" element={<Savings />} />
                <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
        </Routes>
    );
};

const App: React.FC = () => {
  return (
    <AuthProvider>
        <BrowserRouter>
            <AppRoutes />
        </BrowserRouter>
    </AuthProvider>
  );
};

export default App;