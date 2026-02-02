import axios from 'axios';

const api = axios.create({
    baseURL: 'http://localhost:3001/api',
});

export interface Category {
    id: number;
    name: string;
    is_active: number;
}

export interface Month {
    id: number;
    year: number;
    month: number;
}

export interface Income {
    id: number;
    month_id: number;
    source: string;
    amount: number;
    date: string;
}

export interface Expense {
    id: number;
    month_id: number;
    category_id: number;
    amount: number;
    date: string;
    comment: string;
    category_name?: string;
}

export interface Budget {
    id: number;
    month_id: number;
    category_id: number;
    limit_amount: number;
    category_name?: string;
}

export const getCategories = () => api.get<Category[]>('/categories');
export const reorderCategories = (ids: number[]) => api.put('/categories/reorder', { ids });
export const getMonths = () => api.get<Month[]>('/months');
export const ensureMonth = (year: number, month: number) => api.post<Month>('/months/ensure', { year, month });

export const getIncomes = (monthId: number) => api.get<Income[]>(`/months/${monthId}/incomes`);
export const addIncome = (data: Omit<Income, 'id'>) => api.post<Income>('/incomes', data);
export const updateIncome = (id: number, data: { amount: number }) => api.put(`/incomes/${id}`, data);
export const deleteIncome = (id: number) => api.delete(`/incomes/${id}`);

export const getExpenses = (monthId: number) => api.get<Expense[]>(`/months/${monthId}/expenses`);
export const addExpense = (data: Omit<Expense, 'id' | 'category_name'>) => api.post<Expense>('/expenses', data);
export const updateExpense = (id: number, data: { amount: number }) => api.put(`/expenses/${id}`, data);
export const deleteExpense = (id: number) => api.delete(`/expenses/${id}`);

export const getBudgets = (monthId: number) => api.get<Budget[]>(`/months/${monthId}/budgets`);
export const setBudget = (data: { month_id: number, category_id: number, limit_amount: number }) => api.post<Budget>('/budgets', data);

export interface SavingsGoal {
    id: number;
    name: string;
    target_amount: number;
    current_amount: number;
}

export const getSavingsGoals = () => api.get<SavingsGoal[]>('/savings_goals');
export const addSavingsGoal = (data: { name: string, target_amount: number }) => api.post<SavingsGoal>('/savings_goals', data);
export const addSavingsTransaction = (data: { goal_id: number, amount: number, date: string, month_id?: number }) => api.post('/savings_transactions', data);
export const getSavingsTransactions = (goalId: number) => api.get<any[]>(`/savings_transactions/${goalId}`);

export const getAnalyticsTrend = () => api.get<{ month: string, income: number, expense: number }[]>('/analytics/trend');
export const getMonthSummary = (monthId: number) => api.get<{ income: number, expenses: number, savings: number, balance: number }>(`/months/${monthId}/summary`);

export default api;
