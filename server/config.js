const crypto = require('crypto');

const DEFAULT_PORT = 3002;
const DEFAULT_DEV_ALLOWED_ORIGINS = [
    'http://localhost:5174',
    'http://127.0.0.1:5174',
    'http://localhost:4173',
    'http://127.0.0.1:4173',
];
const DEFAULT_JSON_BODY_LIMIT = '1mb';
const DEFAULT_RATE_LIMIT_WINDOW_MS = 15 * 60 * 1000;
const DEFAULT_AUTH_RATE_LIMIT_MAX = 5;
const DEFAULT_PASSWORD_RATE_LIMIT_MAX = 5;
const DEFAULT_RESTORE_RATE_LIMIT_MAX = 3;
const DEFAULT_EMAIL_VERIFICATION_RATE_LIMIT_MAX = 5;
const DEFAULT_PASSWORD_RECOVERY_REQUEST_RATE_LIMIT_MAX = 5;
const DEFAULT_PASSWORD_RECOVERY_CONFIRM_RATE_LIMIT_MAX = 5;
const DEFAULT_ACCESS_TOKEN_TTL = '60m';
const DEFAULT_REFRESH_TOKEN_TTL_DAYS = 30;
const DEFAULT_EMAIL_VERIFICATION_TOKEN_TTL_HOURS = 24;
const DEFAULT_PASSWORD_RESET_TOKEN_TTL_HOURS = 2;
const DEFAULT_SOCIAL_AUTH_EXCHANGE_CODE_TTL_MINUTES = 10;
const DEFAULT_NATIVE_SOCIAL_AUTH_POLL_INTERVAL_MS = 1500;
const BACKUP_RESTORE_CONFIRMATION_TEXT = 'ВОССТАНОВИТЬ';
const REFRESH_TOKEN_COOKIE_NAME = 'refresh_token';
const OAUTH_STATE_COOKIE_NAME = 'oauth_state';
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const GOOGLE_OAUTH_AUTHORIZE_URL = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_OAUTH_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const GOOGLE_OAUTH_USERINFO_URL = 'https://openidconnect.googleapis.com/v1/userinfo';
const YANDEX_OAUTH_AUTHORIZE_URL = 'https://oauth.yandex.com/authorize';
const YANDEX_OAUTH_TOKEN_URL = 'https://oauth.yandex.com/token';
const YANDEX_OAUTH_USERINFO_URL = 'https://login.yandex.ru/info';
const BCRYPT_ROUNDS = 10;
const MAX_BACKUPS = 5;

const isProduction = process.env.NODE_ENV === 'production';

function parsePositiveIntegerEnv(name, fallbackValue) {
    const rawValue = process.env[name]?.trim();
    if (!rawValue) {
        return fallbackValue;
    }

    const parsedValue = Number(rawValue);
    if (!Number.isInteger(parsedValue) || parsedValue <= 0) {
        throw new Error(`${name} environment variable must be a positive integer`);
    }

    return parsedValue;
}

function parseBooleanEnv(name, fallbackValue = false) {
    const rawValue = process.env[name]?.trim().toLowerCase();
    if (!rawValue) {
        return fallbackValue;
    }

    if (['1', 'true', 'yes', 'on'].includes(rawValue)) {
        return true;
    }

    if (['0', 'false', 'no', 'off'].includes(rawValue)) {
        return false;
    }

    throw new Error(`${name} environment variable must be a boolean-like value`);
}

function trimTrailingSlashes(value) {
    return value.replace(/\/+$/, '');
}

const jwtSecret = process.env.JWT_SECRET?.trim();
const allowedOriginsEnv = process.env.ALLOWED_ORIGINS?.trim();
const allowedOrigins = (allowedOriginsEnv
    ? allowedOriginsEnv.split(',').map(origin => origin.trim()).filter(Boolean)
    : isProduction
        ? []
        : DEFAULT_DEV_ALLOWED_ORIGINS
);
const portValue = process.env.PORT?.trim();
const parsedPort = portValue ? Number(portValue) : DEFAULT_PORT;
const jsonBodyLimit = process.env.JSON_BODY_LIMIT?.trim() || DEFAULT_JSON_BODY_LIMIT;
const rateLimitWindowMs = parsePositiveIntegerEnv('RATE_LIMIT_WINDOW_MS', DEFAULT_RATE_LIMIT_WINDOW_MS);
const authRateLimitMax = parsePositiveIntegerEnv('AUTH_RATE_LIMIT_MAX', DEFAULT_AUTH_RATE_LIMIT_MAX);
const passwordRateLimitMax = parsePositiveIntegerEnv('PASSWORD_RATE_LIMIT_MAX', DEFAULT_PASSWORD_RATE_LIMIT_MAX);
const restoreRateLimitMax = parsePositiveIntegerEnv('RESTORE_RATE_LIMIT_MAX', DEFAULT_RESTORE_RATE_LIMIT_MAX);
const emailVerificationRateLimitMax = parsePositiveIntegerEnv('EMAIL_VERIFICATION_RATE_LIMIT_MAX', DEFAULT_EMAIL_VERIFICATION_RATE_LIMIT_MAX);
const passwordRecoveryRequestRateLimitMax = parsePositiveIntegerEnv('PASSWORD_RECOVERY_REQUEST_RATE_LIMIT_MAX', DEFAULT_PASSWORD_RECOVERY_REQUEST_RATE_LIMIT_MAX);
const passwordRecoveryConfirmRateLimitMax = parsePositiveIntegerEnv('PASSWORD_RECOVERY_CONFIRM_RATE_LIMIT_MAX', DEFAULT_PASSWORD_RECOVERY_CONFIRM_RATE_LIMIT_MAX);
const accessTokenTtl = process.env.ACCESS_TOKEN_TTL?.trim() || DEFAULT_ACCESS_TOKEN_TTL;
const refreshTokenTtlDays = parsePositiveIntegerEnv('REFRESH_TOKEN_TTL_DAYS', DEFAULT_REFRESH_TOKEN_TTL_DAYS);
const refreshTokenTtlMs = refreshTokenTtlDays * 24 * 60 * 60 * 1000;
const emailVerificationTokenTtlHours = parsePositiveIntegerEnv('EMAIL_VERIFICATION_TOKEN_TTL_HOURS', DEFAULT_EMAIL_VERIFICATION_TOKEN_TTL_HOURS);
const passwordResetTokenTtlHours = parsePositiveIntegerEnv('PASSWORD_RESET_TOKEN_TTL_HOURS', DEFAULT_PASSWORD_RESET_TOKEN_TTL_HOURS);
const socialAuthExchangeCodeTtlMinutes = parsePositiveIntegerEnv('SOCIAL_AUTH_EXCHANGE_CODE_TTL_MINUTES', DEFAULT_SOCIAL_AUTH_EXCHANGE_CODE_TTL_MINUTES);
const nativeSocialAuthPollIntervalMs = parsePositiveIntegerEnv('NATIVE_SOCIAL_AUTH_POLL_INTERVAL_MS', DEFAULT_NATIVE_SOCIAL_AUTH_POLL_INTERVAL_MS);
const authDebugTokenPreviewEnabled = !isProduction && process.env.AUTH_DEBUG_TOKEN_PREVIEW !== '0';
const publicApiBaseUrl = process.env.PUBLIC_API_BASE_URL?.trim()
    ? trimTrailingSlashes(process.env.PUBLIC_API_BASE_URL.trim())
    : '';

const socialProviderConfigs = [
    {
        id: 'google',
        displayName: 'Google',
        enabled: parseBooleanEnv('GOOGLE_OAUTH_ENABLED', false),
        clientId: process.env.GOOGLE_OAUTH_CLIENT_ID?.trim() || '',
        clientSecret: process.env.GOOGLE_OAUTH_CLIENT_SECRET?.trim() || '',
    },
    {
        id: 'yandex',
        displayName: 'Яндекс',
        enabled: parseBooleanEnv('YANDEX_OAUTH_ENABLED', false),
        clientId: process.env.YANDEX_OAUTH_CLIENT_ID?.trim() || '',
        clientSecret: process.env.YANDEX_OAUTH_CLIENT_SECRET?.trim() || '',
    },
    {
        id: 'mailru',
        displayName: 'Mail.ru',
        enabled: parseBooleanEnv('MAILRU_OAUTH_ENABLED', false),
        clientId: process.env.MAILRU_OAUTH_CLIENT_ID?.trim() || '',
        clientSecret: process.env.MAILRU_OAUTH_CLIENT_SECRET?.trim() || '',
    },
];
const socialProviderConfigMap = new Map(socialProviderConfigs.map(provider => [provider.id, provider]));

if (!jwtSecret) {
    throw new Error('JWT_SECRET environment variable is required');
}

if (!Number.isInteger(parsedPort) || parsedPort <= 0 || parsedPort > 65535) {
    throw new Error('PORT environment variable must be a valid port number');
}

if (isProduction && allowedOrigins.length === 0) {
    throw new Error('ALLOWED_ORIGINS environment variable is required in production');
}

const requiredHealthTables = [
    'users',
    'categories',
    'months',
    'incomes',
    'expenses',
    'budgets',
    'savings_goals',
    'savings_transactions',
    'income_sources',
    'user_backups',
    'auth_refresh_sessions',
    'auth_email_verification_tokens',
    'auth_password_reset_tokens',
    'auth_identities',
    'auth_login_exchange_codes',
    'auth_social_login_attempts',
    'idempotency_keys',
    'deleted_records',
];

module.exports = {
    isProduction,
    jwtSecret,
    allowedOrigins,
    parsedPort,
    jsonBodyLimit,
    rateLimitWindowMs,
    authRateLimitMax,
    passwordRateLimitMax,
    restoreRateLimitMax,
    emailVerificationRateLimitMax,
    passwordRecoveryRequestRateLimitMax,
    passwordRecoveryConfirmRateLimitMax,
    accessTokenTtl,
    refreshTokenTtlDays,
    refreshTokenTtlMs,
    emailVerificationTokenTtlHours,
    passwordResetTokenTtlHours,
    socialAuthExchangeCodeTtlMinutes,
    nativeSocialAuthPollIntervalMs,
    authDebugTokenPreviewEnabled,
    publicApiBaseUrl,
    socialProviderConfigs,
    socialProviderConfigMap,
    requiredHealthTables,
    BCRYPT_ROUNDS,
    MAX_BACKUPS,
    BACKUP_RESTORE_CONFIRMATION_TEXT,
    REFRESH_TOKEN_COOKIE_NAME,
    OAUTH_STATE_COOKIE_NAME,
    EMAIL_REGEX,
    GOOGLE_OAUTH_AUTHORIZE_URL,
    GOOGLE_OAUTH_TOKEN_URL,
    GOOGLE_OAUTH_USERINFO_URL,
    YANDEX_OAUTH_AUTHORIZE_URL,
    YANDEX_OAUTH_TOKEN_URL,
    YANDEX_OAUTH_USERINFO_URL,
};
