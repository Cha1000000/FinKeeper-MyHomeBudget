import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { AUTH_LOGOUT_REQUIRED_EVENT, clearToken, getToken, setAuthNotice, setToken } from '../auth/tokenStorage';

const api = axios.create({
    baseURL: '/api',
});

// Add a request interceptor
api.interceptors.request.use(
  (config) => {
    const token = getToken();
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

let isRefreshing = false;
let failedQueue: Array<{
    resolve: (token: string) => void;
    reject: (error: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null) {
    failedQueue.forEach(pending => {
        if (error || !token) {
            pending.reject(error);
        } else {
            pending.resolve(token);
        }
    });
    failedQueue = [];
}

function isAuthEndpoint(url: string): boolean {
    return (
        url.includes('/auth/login') ||
        url.includes('/auth/register') ||
        url.includes('/auth/refresh') ||
        url.includes('/auth/oauth/exchange') ||
        url.includes('/auth/password-recovery/') ||
        url.includes('/auth/email-verification/') ||
        url.includes('/auth/logout')
    );
}

function shouldAttemptRefresh(error: AxiosError, requestUrl: string, alreadyRetried: boolean): boolean {
    if (isAuthEndpoint(requestUrl) || alreadyRetried) {
        return false;
    }

    const status = error.response?.status;
    const errorCode = (error.response?.data as { code?: string } | undefined)?.code;

    return status === 401 || (status === 403 && errorCode === 'invalid_access_token');
}

api.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
        const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
        const requestUrl = originalRequest?.url ?? '';

        if (shouldAttemptRefresh(error, requestUrl, Boolean(originalRequest._retry))) {
            if (isRefreshing) {
                return new Promise<string>((resolve, reject) => {
                    failedQueue.push({ resolve, reject });
                }).then(token => {
                    originalRequest.headers['Authorization'] = 'Bearer ' + token;
                    return api(originalRequest);
                });
            }

            isRefreshing = true;
            originalRequest._retry = true;

            try {
                const { data } = await axios.post('/api/auth/refresh', {}, {
                    withCredentials: true,
                });

                const newToken = data.accessToken || data.token;
                if (!newToken) {
                    throw new Error('No token in refresh response');
                }

                setToken(newToken);
                processQueue(null, newToken);

                originalRequest.headers['Authorization'] = 'Bearer ' + newToken;
                return api(originalRequest);
            } catch (refreshError: unknown) {
                const axiosError = refreshError as AxiosError;
                const status = axiosError.response?.status;
                
                // Only force logout if the refresh token is invalid (401) or forbidden (403)
                // Network errors or 5xx server errors should NOT log the user out
                if (status === 401 || status === 403) {
                    processQueue(refreshError, null);
                    setAuthNotice('session-expired');
                    clearToken();
                    if (typeof window !== 'undefined') {
                        window.dispatchEvent(new Event(AUTH_LOGOUT_REQUIRED_EVENT));
                    }
                } else {
                    // For other errors (network, 500), just fail the request but keep the session
                    processQueue(refreshError, null);
                }
                
                return Promise.reject(refreshError);
            } finally {
                isRefreshing = false;
            }
        }

        const status = error.response?.status;
        const errorCode = (error.response?.data as { code?: string } | undefined)?.code;
        const shouldForceLogout = !isAuthEndpoint(requestUrl) && (
            status === 401 ||
            (status === 403 && errorCode === 'invalid_access_token')
        );

        if (shouldForceLogout) {
            setAuthNotice('session-expired');
            clearToken();
            if (typeof window !== 'undefined') {
                window.dispatchEvent(new Event(AUTH_LOGOUT_REQUIRED_EVENT));
            }
        }

        return Promise.reject(error);
    }
);

// Auth Endpoints
export interface AuthData {
    username: string;
    password?: string;
    rememberMe?: boolean;
}

export interface DebugTokenPreview {
    token: string;
    expiresAt: string;
}

export interface User {
    id: number;
    username: string;
    email: string | null;
    emailConfirmed: boolean;
    recoverabilityStatus: 'protected' | 'unprotected';
    canSelfRecover: boolean;
    recoveryEmail: string | null;
    recoveryEmailConfirmed: boolean;
    recoveryEmailSource: 'account' | 'social' | null;
    recoveryEmailProvider: string | null;
    created_at?: string;
    linkedAuthProviders: Array<{
        provider: string;
        email: string | null;
        emailVerified: boolean;
    }>;
}

export interface AuthResponse {
    token: string;
    accessToken: string;
    refreshToken?: string;
    user: User;
}

export interface SocialProvider {
    id: string;
    displayName: string;
    enabled: boolean;
}

export interface PasswordRecoveryRequestResponse {
    success: boolean;
    message: string;
    debug?: {
        passwordReset?: DebugTokenPreview & {
            username?: string;
            source?: string;
        };
        passwordResets?: Array<DebugTokenPreview & {
            username?: string;
            source?: string;
        }>;
    };
}

export interface PasswordRecoveryConfirmResponse {
    success: boolean;
}

export interface EmailVerificationResponse {
    success: boolean;
    user: User;
}

export interface EmailDeliveryStatus {
    delivered: boolean;
    reason: string | null;
}

export interface UserEmailUpdateResponse {
    success: boolean;
    verificationRequired: boolean;
    user: User;
    delivery?: EmailDeliveryStatus;
    debug?: {
        emailVerification?: DebugTokenPreview;
    };
}

export interface UserEmailVerificationRequestResponse {
    success: boolean;
    verificationRequired: boolean;
    user: User;
    delivery?: EmailDeliveryStatus;
    debug?: {
        emailVerification?: DebugTokenPreview;
    };
}

export interface BackupEntrySummary {
    categories: number;
    incomeSources: number;
    savingsGoals: number;
    months: number;
    incomes: number;
    expenses: number;
    budgets: number;
    savingsTransactions: number;
}

export interface BackupEntry {
    id: number;
    createdAt: string;
    sizeBytes: number;
    summary: BackupEntrySummary;
}

export interface BackupListResponse {
    backups: BackupEntry[];
}

export interface RestoreBackupResponse {
    success: boolean;
    backup: BackupEntry;
}

export const loginUser = (data: AuthData) => api.post<AuthResponse>('/auth/login', data);
export const registerUser = (data: AuthData) => api.post<AuthResponse>('/auth/register', data);
export const getMe = () => api.get<User>('/auth/me');
export const getSocialProviders = () => api.get<{ providers: SocialProvider[] }>('/auth/social/providers', {
    params: {
        _: Date.now(),
    },
    headers: {
        'Cache-Control': 'no-cache',
        Pragma: 'no-cache',
    },
});
export const exchangeSocialAuthCode = (code: string, rememberMe: boolean = false) => api.post<AuthResponse>('/auth/oauth/exchange', { code, rememberMe });
export const requestPasswordRecovery = (email: string) => api.post<PasswordRecoveryRequestResponse>('/auth/password-recovery/request', { email });
export const confirmPasswordRecovery = (token: string, newPassword: string) => api.post<PasswordRecoveryConfirmResponse>('/auth/password-recovery/confirm', { token, newPassword });
export const confirmEmailVerification = (token: string) => api.post<EmailVerificationResponse>('/auth/email-verification/confirm', { token });

// User Settings
export const updateUsername = (newUsername: string) => api.put('/user/rename', { newUsername });
export const updatePassword = (currentPassword: string, newPassword: string) => api.put('/user/password', { currentPassword, newPassword });
export const updateUserEmail = (email: string) => api.put<UserEmailUpdateResponse>('/user/email', { email });
export const clearUserEmail = () => api.put<UserEmailUpdateResponse>('/user/email', { email: '' });
export const requestEmailVerification = () => api.post<UserEmailVerificationRequestResponse>('/user/email/verification/request');
export const getBackupEntries = () => api.get<BackupListResponse>('/user/backups');
export const restoreBackup = (backupId: number, confirmationText: string) => api.post<RestoreBackupResponse>('/user/restore', { backupId, confirmationText });
export const createManualBackup = () => api.post('/user/backup');

export interface Category {
    id: number;
    name: string;
    is_active: number;
    is_fixed: number;
    fixed_amount: number | null;
    auto_day: number | null;
}

export interface IncomeSource {
    id: number;
    name: string;
    is_active: number;
    is_fixed: number;
    fixed_amount: number | null;
    auto_day: number | null;
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

export const getIncomeSources = () => api.get<IncomeSource[]>('/income_sources');
export const addIncomeSource = (data: { name: string }) => api.post<IncomeSource>('/income_sources', data);
export const updateIncomeSource = (id: number, data: { name?: string, is_active?: number }) => api.put(`/income_sources/${id}`, data);
export const deleteIncomeSource = (id: number) => api.delete(`/income_sources/${id}`);

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

export interface SavingsTransaction {
    id: number;
    goal_id: number;
    amount: number;
    date: string;
    month_id: number;
}

export const getSavingsGoals = () => api.get<SavingsGoal[]>('/savings_goals');
export const addSavingsGoal = (data: { name: string, target_amount: number }) => api.post<SavingsGoal>('/savings_goals', data);
export const updateSavingsGoal = (id: number, data: { name?: string, target_amount?: number, current_amount?: number }) => api.put(`/savings_goals/${id}`, data);
export const deleteSavingsGoal = (id: number) => api.delete(`/savings_goals/${id}`);
export const addSavingsTransaction = (data: { goal_id: number, amount: number, date: string, month_id?: number }) => api.post('/savings_transactions', data);
export const getSavingsTransactions = (goalId: number) => api.get<SavingsTransaction[]>(`/savings_transactions/${goalId}`);

export const getAnalyticsTrend = () => api.get<{ month: string, income: number, expense: number, savings: number }[]>('/analytics/trend');
export const getMonthSummary = (monthId: number) => api.get<{ income: number, expenses: number, savings: number, balance: number }>(`/months/${monthId}/summary`);
export const getCumulativeBalance = (year: number, month: number) => api.get<{ cumulativeBalance: number }>(`/analytics/cumulative-balance?year=${year}&month=${month}`);

export default api;
