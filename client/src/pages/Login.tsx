import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

const Login: React.FC = () => {
  const [isLogin, setIsLogin] = useState(true);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      if (isLogin) {
        await login({ username, password });
      } else {
        await register({ username, password });
      }
      navigate('/');
    } catch (err: any) {
        const msg = err.response?.data?.error || err.message || 'Ошибка авторизации';
        setError(msg);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-green-100 via-emerald-50 to-teal-100 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8 bg-white p-10 rounded-2xl shadow-xl border border-white/50">
        <div className="flex flex-col items-center">
            {/* Logo */}
            <div className="bg-emerald-100 p-3 rounded-full mb-4 shadow-inner">
                <img src="/purse.svg" alt="FinKeeper Logo" className="h-12 w-12" />
            </div>
            
            {/* App Name */}
            <h1 className="text-3xl font-bold text-emerald-800 tracking-tight">FinKeeper</h1>
            
            <h2 className="mt-2 text-center text-lg font-medium text-gray-500">
                {isLogin ? 'Вход в систему' : 'Создание аккаунта'}
            </h2>
        </div>
        
        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          <div className="space-y-5">
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-gray-700 mb-1 ml-1">Имя пользователя</label>
              <input
                id="username"
                name="username"
                type="text"
                required
                className="appearance-none block w-full px-4 py-3 border border-gray-300 placeholder-gray-400 text-gray-900 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm transition-shadow shadow-sm"
                placeholder="Введите имя"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1 ml-1">Пароль</label>
              <input
                id="password"
                name="password"
                type="password"
                required
                className="appearance-none block w-full px-4 py-3 border border-gray-300 placeholder-gray-400 text-gray-900 rounded-xl focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500 sm:text-sm transition-shadow shadow-sm"
                placeholder="Введите пароль"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-600 rounded-lg p-3 text-sm text-center">
              {error}
            </div>
          )}

          <div>
            <button
              type="submit"
              className="group relative w-full flex justify-center py-3 px-4 border border-transparent text-sm font-bold rounded-xl text-white bg-emerald-600 hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500 shadow-lg shadow-emerald-200 transition-all active:scale-[0.98]"
            >
              {isLogin ? 'Войти' : 'Зарегистрироваться'}
            </button>
          </div>
        </form>

        <div className="text-center mt-4">
            <p className="text-sm text-gray-500">
                {isLogin ? 'Еще нет аккаунта?' : 'Уже есть аккаунт?'}
                <button 
                    onClick={() => {
                        setIsLogin(!isLogin);
                        setError('');
                    }}
                    className="ml-2 font-medium text-emerald-600 hover:text-emerald-500 transition-colors"
                >
                    {isLogin ? 'Создать' : 'Войти'}
                </button>
            </p>
        </div>
      </div>
    </div>
  );
};

export default Login;