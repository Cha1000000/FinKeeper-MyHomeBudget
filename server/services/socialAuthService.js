const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const { db, nowIso } = require('../db/connection');
const config = require('../config');
const { getMailConfig } = require('../mailer');
const authService = require('./authService');

function getAppBaseUrl() {
    return getMailConfig().appBaseUrl || '';
}

function getPublicApiBaseUrl(req) {
    if (config.publicApiBaseUrl) return config.publicApiBaseUrl;
    const forwardedProto = authService.normalizeInputString(req.get('x-forwarded-proto'));
    const forwardedHost = authService.normalizeInputString(req.get('x-forwarded-host'));
    const protocol = forwardedProto || req.protocol || 'http';
    const host = forwardedHost || req.get('host');
    return host ? `${protocol}://${host}` : '';
}

function buildWebSocialCallbackUrl(provider) {
    const appBaseUrl = getAppBaseUrl();
    if (!appBaseUrl) return '';
    return new URL(`/auth/${provider}/callback`, `${appBaseUrl}/`).toString();
}

function buildApiOAuthCallbackUrl(req, provider) {
    const apiBaseUrl = getPublicApiBaseUrl(req);
    if (!apiBaseUrl) return '';
    return new URL(`/api/auth/oauth/${provider}/callback`, `${apiBaseUrl}/`).toString();
}

function encodeOAuthStatePayload(payload) {
    return Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
}

function decodeOAuthStatePayload(value) {
    try {
        const decoded = Buffer.from(value, 'base64url').toString('utf8');
        const payload = JSON.parse(decoded);
        return payload && typeof payload === 'object' ? payload : null;
    } catch {
        return null;
    }
}

function createOAuthStatePayload({ provider, clientType, redirectUri, attemptToken = null }) {
    return {
        nonce: authService.generateOpaqueToken(),
        provider,
        clientType,
        redirectUri,
        attemptToken,
        createdAt: nowIso(),
    };
}

function readOAuthStatePayloadFromRequest(req) {
    const stateFromQuery = authService.normalizeInputString(req.query?.state);
    const stateFromCookie = authService.getOAuthStateCookie(req);

    if (!stateFromQuery || !stateFromCookie || stateFromQuery !== stateFromCookie) return null;

    const payload = decodeOAuthStatePayload(stateFromQuery);
    if (!payload) return null;
    return payload;
}

function buildFrontendAuthRedirectUrl(provider, query) {
    const callbackUrl = buildWebSocialCallbackUrl(provider);
    if (!callbackUrl) return '';
    const url = new URL(callbackUrl);
    Object.entries(query).forEach(([key, rawValue]) => {
        if (rawValue !== undefined && rawValue !== null && rawValue !== '') {
            url.searchParams.set(key, String(rawValue));
        }
    });
    return url.toString();
}

function buildNativeSocialAuthCompletionHtml({ success, provider, errorCode = null }) {
    const providerName = provider === 'yandex' ? 'Яндекс' : provider === 'google' ? 'Google' : 'соцсеть';
    const title = success ? 'Вход завершён' : 'Вход не завершён';
    const description = success
        ? `Авторизация через ${providerName} завершена. Вернитесь в приложение FinKeeper.`
        : `Авторизация через ${providerName} не завершилась. Вернитесь в приложение FinKeeper и попробуйте ещё раз.`;
    const errorHint = errorCode ? `<p style="margin-top:12px;color:#64748b;font-size:13px;">Код ошибки: ${errorCode}</p>` : '';
    return `<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>${title}</title>
</head>
<body style="margin:0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#f8fafc;color:#0f172a;">
  <main style="min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;">
    <section style="max-width:420px;width:100%;background:#ffffff;border:1px solid #e2e8f0;border-radius:20px;padding:28px;box-shadow:0 12px 40px rgba(15,23,42,.08);text-align:center;">
      <div style="font-size:34px;line-height:1;margin-bottom:16px;">${success ? '✅' : '⚠️'}</div>
      <h1 style="margin:0 0 12px;font-size:24px;font-weight:700;">${title}</h1>
      <p style="margin:0;color:#475569;font-size:15px;line-height:1.6;">${description}</p>
      ${errorHint}
    </section>
  </main>
</body>
</html>`;
}

function sendNativeSocialAuthCompletionPage(res, { success, provider, errorCode = null, statusCode = 200 }) {
    return res.status(statusCode).type('html').send(buildNativeSocialAuthCompletionHtml({ success, provider, errorCode }));
}

function getFutureIsoFromMinutes(minutes) {
    return new Date(Date.now() + (minutes * 60 * 1000)).toISOString();
}

function isPlaceholderSocialConfigValue(value) {
    const normalizedValue = value.trim().toLowerCase();
    if (!normalizedValue) return true;
    const placeholderMarkers = ['your-', 'placeholder', 'example', 'replace-me', 'replace_with', 'changeme', 'dummy', 'sample'];
    return placeholderMarkers.some(marker => normalizedValue.includes(marker));
}

function hasUsableSocialProviderCredentials(providerConfig) {
    if (!providerConfig?.clientId || !providerConfig?.clientSecret) return false;
    return !isPlaceholderSocialConfigValue(providerConfig.clientId) && !isPlaceholderSocialConfigValue(providerConfig.clientSecret);
}

function isValidPublicOAuthCallbackBaseUrl(req) {
    const apiBaseUrl = getPublicApiBaseUrl(req);
    if (!apiBaseUrl) return false;
    try {
        const parsedUrl = new URL(apiBaseUrl);
        const hostname = authService.normalizeInputString(parsedUrl.hostname).toLowerCase();
        const isLocalhost = hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1';
        return parsedUrl.protocol === 'https:' || isLocalhost;
    } catch {
        return false;
    }
}

function getPublicSocialProviderPayload(providerConfig, req = null) {
    return {
        id: providerConfig.id,
        displayName: providerConfig.displayName,
        enabled: isSocialProviderAvailable(providerConfig, req),
    };
}

function getEnabledSocialProviders(req = null) {
    return config.socialProviderConfigs.map(providerConfig => getPublicSocialProviderPayload(providerConfig, req));
}

function getSocialProviderConfig(providerId) {
    return config.socialProviderConfigMap.get(providerId) || null;
}

function isSocialProviderAvailable(providerConfig, req = null) {
    return Boolean(providerConfig?.enabled && hasUsableSocialProviderCredentials(providerConfig) && isValidPublicOAuthCallbackBaseUrl(req));
}

function cleanupExpiredSocialAuthExchangeCodes() {
    db.prepare(`
        DELETE FROM auth_login_exchange_codes
        WHERE used_at IS NOT NULL OR expires_at <= ?
    `).run(nowIso());
}

function cleanupExpiredNativeSocialLoginAttempts() {
    db.prepare(`
        DELETE FROM auth_social_login_attempts
        WHERE expires_at <= ? OR consumed_at IS NOT NULL
    `).run(nowIso());
}

function issueSocialAuthExchangeCode({ userId, provider, clientType = null, redirectUri = null }) {
    cleanupExpiredSocialAuthExchangeCodes();
    const code = authService.generateOpaqueToken();
    const codeHash = authService.hashOpaqueToken(code);
    const timestamp = nowIso();
    const expiresAt = getFutureIsoFromMinutes(config.socialAuthExchangeCodeTtlMinutes);

    db.prepare(`
        INSERT INTO auth_login_exchange_codes (user_id, provider, code_hash, client_type, redirect_uri, expires_at, created_at, used_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, NULL)
    `).run(userId, provider, codeHash, clientType, redirectUri, expiresAt, timestamp);

    return { code, expiresAt };
}

function issueNativeSocialLoginAttempt({ provider, clientType, redirectUri = null }) {
    cleanupExpiredNativeSocialLoginAttempts();
    const attemptToken = authService.generateOpaqueToken();
    const attemptTokenHash = authService.hashOpaqueToken(attemptToken);
    const timestamp = nowIso();
    const expiresAt = getFutureIsoFromMinutes(config.socialAuthExchangeCodeTtlMinutes);

    db.prepare(`
        INSERT INTO auth_social_login_attempts (
            attempt_token_hash, provider, client_type, redirect_uri, user_id, status, exchange_code, error_code, error_description, expires_at, created_at, completed_at, consumed_at
        ) VALUES (?, ?, ?, ?, NULL, 'pending', NULL, NULL, NULL, ?, ?, NULL, NULL)
    `).run(attemptTokenHash, provider, clientType, redirectUri, expiresAt, timestamp);

    return { attemptToken, expiresAt };
}

function getValidSocialAuthExchangeCode(code) {
    cleanupExpiredSocialAuthExchangeCodes();
    return db.prepare(`
        SELECT id, user_id, provider, client_type, redirect_uri, expires_at, used_at
        FROM auth_login_exchange_codes
        WHERE code_hash = ?
    `).get(authService.hashOpaqueToken(code));
}

function getNativeSocialLoginAttempt(attemptToken) {
    cleanupExpiredNativeSocialLoginAttempts();
    return db.prepare(`
        SELECT id, provider, client_type, redirect_uri, user_id, status, exchange_code, error_code, error_description, expires_at, created_at, completed_at, consumed_at
        FROM auth_social_login_attempts
        WHERE attempt_token_hash = ?
    `).get(authService.hashOpaqueToken(attemptToken));
}

function markSocialAuthExchangeCodeUsed(exchangeCodeId) {
    db.prepare(`
        UPDATE auth_login_exchange_codes
        SET used_at = COALESCE(used_at, ?)
        WHERE id = ?
    `).run(nowIso(), exchangeCodeId);
}

function completeNativeSocialLoginAttemptSuccess(attemptId, { userId, exchangeCode }) {
    db.prepare(`
        UPDATE auth_social_login_attempts
        SET user_id = ?, status = 'completed', exchange_code = ?, error_code = NULL, error_description = NULL, completed_at = ?, consumed_at = NULL
        WHERE id = ?
    `).run(userId, exchangeCode, nowIso(), attemptId);
}

function completeNativeSocialLoginAttemptError(attemptId, { errorCode, errorDescription = null }) {
    db.prepare(`
        UPDATE auth_social_login_attempts
        SET status = 'error', exchange_code = NULL, error_code = ?, error_description = ?, completed_at = ?, consumed_at = NULL
        WHERE id = ?
    `).run(errorCode, errorDescription, nowIso(), attemptId);
}

function markNativeSocialLoginAttemptConsumed(attemptId) {
    db.prepare(`
        UPDATE auth_social_login_attempts
        SET consumed_at = COALESCE(consumed_at, ?)
        WHERE id = ?
    `).run(nowIso(), attemptId);
}

function markNativeSocialLoginAttemptConsumedByExchangeCode(exchangeCode) {
    if (!exchangeCode) return;
    db.prepare(`
        UPDATE auth_social_login_attempts
        SET consumed_at = COALESCE(consumed_at, ?)
        WHERE exchange_code = ?
    `).run(nowIso(), exchangeCode);
}

function findAuthIdentity(provider, providerUserId) {
    return db.prepare(`
        SELECT id, user_id, provider, provider_user_id, provider_email, provider_email_verified
        FROM auth_identities
        WHERE provider = ? AND provider_user_id = ?
    `).get(provider, providerUserId);
}

function touchAuthIdentity(identityId, { providerEmail = null, providerEmailVerified = false } = {}) {
    db.prepare(`
        UPDATE auth_identities
        SET provider_email = ?, provider_email_verified = ?, updated_at = ?, last_login_at = ?
        WHERE id = ?
    `).run(providerEmail, providerEmailVerified ? 1 : 0, nowIso(), nowIso(), identityId);
}

function createAuthIdentity({ userId, provider, providerUserId, providerEmail = null, providerEmailVerified = false }) {
    const timestamp = nowIso();
    db.prepare(`
        INSERT INTO auth_identities (user_id, provider, provider_user_id, provider_email, provider_email_verified, created_at, updated_at, last_login_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(userId, provider, providerUserId, providerEmail, providerEmailVerified ? 1 : 0, timestamp, timestamp, timestamp);
}

function createDisabledPasswordHash() {
    return bcrypt.hashSync(crypto.randomBytes(32).toString('hex'), config.BCRYPT_ROUNDS);
}

function buildUsernameCandidate(value) {
    const normalized = String(value || '').toLowerCase().replace(/\s+/g, '-').replace(/[^\p{L}\p{N}._-]/gu, '').replace(/^[._-]+|[._-]+$/g, '');
    const fallback = normalized || 'user';
    return fallback.slice(0, 48) || 'user';
}

function buildSocialDisplayUsernameCandidate(value) {
    const normalized = authService.normalizeInputString(value).replace(/\s+/g, ' ').replace(/[^\p{L}\p{N} ._''-]/gu, '').trim();
    if (normalized) return normalized.slice(0, 64);
    const fallback = buildUsernameCandidate(value);
    return fallback.slice(0, 64) || 'user';
}

function generateUniqueUsername(baseCandidate, excludedUserId = null) {
    const base = buildUsernameCandidate(baseCandidate);
    let candidate = base;
    let counter = 0;
    while (db.prepare('SELECT id FROM users WHERE username = ? AND (? IS NULL OR id != ?)').get(candidate, excludedUserId, excludedUserId)) {
        counter += 1;
        const suffix = counter > 9 ? `-${crypto.randomBytes(2).toString('hex')}` : `-${counter}`;
        candidate = `${base.slice(0, Math.max(1, 48 - suffix.length))}${suffix}`;
    }
    return candidate;
}

function generateUniqueSocialUsername(baseCandidate, excludedUserId = null) {
    const base = buildSocialDisplayUsernameCandidate(baseCandidate);
    let candidate = base;
    let counter = 1;
    while (db.prepare('SELECT id FROM users WHERE username = ? AND (? IS NULL OR id != ?)').get(candidate, excludedUserId, excludedUserId)) {
        counter += 1;
        const suffix = ` ${counter}`;
        candidate = `${base.slice(0, Math.max(1, 64 - suffix.length))}${suffix}`;
    }
    return candidate;
}

async function exchangeGoogleAuthorizationCode({ code, redirectUri, providerConfig }) {
    const response = await fetch(config.GOOGLE_OAUTH_TOKEN_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            code,
            client_id: providerConfig.clientId,
            client_secret: providerConfig.clientSecret,
            redirect_uri: redirectUri,
            grant_type: 'authorization_code',
        }),
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error('Google token exchange failed');
        error.statusCode = response.status;
        error.payload = payload;
        throw error;
    }
    return payload;
}

async function fetchGoogleUserInfo(accessToken) {
    const response = await fetch(config.GOOGLE_OAUTH_USERINFO_URL, {
        headers: {
            Authorization: `Bearer ${accessToken}`,
        },
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error('Google userinfo fetch failed');
        error.statusCode = response.status;
        error.payload = payload;
        throw error;
    }
    return payload;
}

function isGeneratedSocialUsername(username) {
    const normalizedUsername = authService.normalizeInputString(username).toLowerCase();
    if (!normalizedUsername) return true;
    return normalizedUsername === 'user'
        || /^user(?:-[a-z0-9]+(?:-[a-z0-9]+)*)?$/.test(normalizedUsername)
        || /^user \d+$/.test(normalizedUsername);
}

function shouldRefreshSocialUsername(user, profileName) {
    const currentUsername = authService.normalizeInputString(user?.username);
    if (!currentUsername || !profileName) return Boolean(profileName);
    if (isGeneratedSocialUsername(currentUsername)) return true;
    const slugifiedProfileName = buildUsernameCandidate(profileName);
    if (currentUsername.toLowerCase() === slugifiedProfileName) return true;
    return false;
}

function syncExistingSocialAuthUser(userId, { profileName = '', providerEmail = '', providerEmailVerified = false } = {}) {
    const user = db.prepare(`SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled FROM users WHERE id = ?`).get(userId);
    if (!user) return null;

    let nextUsername = user.username;
    let nextEmail = authService.normalizeInputString(user.email) || null;
    let nextEmailConfirmedAt = user.email_confirmed_at;
    let hasChanges = false;

    if (profileName && shouldRefreshSocialUsername(user, profileName)) {
        const generatedUsername = generateUniqueSocialUsername(profileName, user.id);
        if (generatedUsername && generatedUsername !== user.username) {
            nextUsername = generatedUsername;
            hasChanges = true;
        }
    }

    if (!nextEmail && providerEmail && providerEmailVerified) {
        const conflictingUser = db.prepare('SELECT id FROM users WHERE email = ? AND id != ?').get(providerEmail, user.id);
        if (!conflictingUser) {
            nextEmail = providerEmail;
            nextEmailConfirmedAt = nowIso();
            hasChanges = true;
        }
    }

    if (hasChanges) {
        db.prepare(`UPDATE users SET username = ?, email = ?, email_confirmed_at = ? WHERE id = ?`).run(nextUsername, nextEmail, nextEmailConfirmedAt, user.id);
    }

    return db.prepare(`SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled FROM users WHERE id = ?`).get(user.id);
}

function resolveGoogleEmailForNewUser(googleProfile) {
    const email = authService.normalizeEmail(googleProfile?.email);
    if (!email || !googleProfile?.email_verified) return { email: null, emailConfirmedAt: null };
    const conflictingUser = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
    if (conflictingUser) return { email: null, emailConfirmedAt: null };
    return { email, emailConfirmedAt: nowIso() };
}

function resolveOrCreateGoogleAuthUser(googleProfile) {
    const provider = 'google';
    const providerUserId = authService.normalizeInputString(googleProfile?.sub);
    if (!providerUserId) {
        const error = new Error('Google profile is missing subject identifier');
        error.code = 'google_profile_invalid';
        throw error;
    }

    const providerEmail = authService.normalizeEmail(googleProfile?.email);
    const providerEmailVerified = Boolean(googleProfile?.email_verified);
    const profileName = authService.normalizeInputString(googleProfile?.name) || authService.normalizeInputString(providerEmail?.split('@')[0]) || provider;
    const identity = findAuthIdentity(provider, providerUserId);

    if (identity) {
        touchAuthIdentity(identity.id, { providerEmail, providerEmailVerified });
        return syncExistingSocialAuthUser(identity.user_id, { profileName, providerEmail, providerEmailVerified });
    }

    const username = generateUniqueSocialUsername(profileName);
    const emailForNewUser = resolveGoogleEmailForNewUser(googleProfile);
    const user = authService.createUserWithDefaults({
        username,
        passwordHash: createDisabledPasswordHash(),
        authPasswordEnabled: false,
        email: emailForNewUser.email,
        emailConfirmedAt: emailForNewUser.emailConfirmedAt,
    });

    createAuthIdentity({ userId: user.id, provider, providerUserId, providerEmail, providerEmailVerified });
    return user;
}

async function exchangeYandexAuthorizationCode({ code, providerConfig }) {
    const response = await fetch(config.YANDEX_OAUTH_TOKEN_URL, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams({
            grant_type: 'authorization_code',
            code,
            client_id: providerConfig.clientId,
            client_secret: providerConfig.clientSecret,
        }),
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error('Yandex token exchange failed');
        error.statusCode = response.status;
        error.payload = payload;
        throw error;
    }
    return payload;
}

async function fetchYandexUserInfo(accessToken) {
    const response = await fetch(`${config.YANDEX_OAUTH_USERINFO_URL}?format=json`, {
        headers: {
            Authorization: `OAuth ${accessToken}`,
        },
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok) {
        const error = new Error('Yandex userinfo fetch failed');
        error.statusCode = response.status;
        error.payload = payload;
        throw error;
    }
    return payload;
}

function resolveYandexEmailForNewUser(yandexProfile) {
    const email = authService.normalizeEmail(yandexProfile?.default_email);
    if (!email) return { email: null, emailConfirmedAt: null };
    const conflictingUser = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
    if (conflictingUser) return { email: null, emailConfirmedAt: null };
    return { email, emailConfirmedAt: nowIso() };
}

function resolveOrCreateYandexAuthUser(yandexProfile) {
    const provider = 'yandex';
    const providerUserId = authService.normalizeInputString(yandexProfile?.id);
    if (!providerUserId) {
        const error = new Error('Yandex profile is missing user identifier');
        error.code = 'yandex_profile_invalid';
        throw error;
    }

    const providerEmail = authService.normalizeEmail(yandexProfile?.default_email);
    const providerEmailVerified = Boolean(providerEmail);
    const profileName = authService.normalizeInputString(yandexProfile?.real_name) || authService.normalizeInputString(yandexProfile?.display_name) || authService.normalizeInputString(yandexProfile?.login) || authService.normalizeInputString(providerEmail?.split('@')[0]) || provider;
    const identity = findAuthIdentity(provider, providerUserId);

    if (identity) {
        touchAuthIdentity(identity.id, { providerEmail, providerEmailVerified });
        return syncExistingSocialAuthUser(identity.user_id, { profileName, providerEmail, providerEmailVerified });
    }

    const username = generateUniqueSocialUsername(profileName);
    const emailForNewUser = resolveYandexEmailForNewUser(yandexProfile);
    const user = authService.createUserWithDefaults({
        username,
        passwordHash: createDisabledPasswordHash(),
        authPasswordEnabled: false,
        email: emailForNewUser.email,
        emailConfirmedAt: emailForNewUser.emailConfirmedAt,
    });

    createAuthIdentity({ userId: user.id, provider, providerUserId, providerEmail, providerEmailVerified });
    return user;
}

function buildYandexAuthorizeUrl(req, providerConfig, stateValue) {
    const redirectUri = buildApiOAuthCallbackUrl(req, providerConfig.id);
    if (!redirectUri) return '';
    const url = new URL(config.YANDEX_OAUTH_AUTHORIZE_URL);
    url.searchParams.set('client_id', providerConfig.clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('state', stateValue);
    return url.toString();
}

function buildGoogleAuthorizeUrl(req, providerConfig, stateValue) {
    const redirectUri = buildApiOAuthCallbackUrl(req, providerConfig.id);
    if (!redirectUri) return '';
    const url = new URL(config.GOOGLE_OAUTH_AUTHORIZE_URL);
    url.searchParams.set('client_id', providerConfig.clientId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('response_type', 'code');
    url.searchParams.set('scope', 'openid email profile');
    url.searchParams.set('state', stateValue);
    url.searchParams.set('prompt', 'select_account');
    return url.toString();
}

function redirectSocialAuthResult(res, provider, query, fallbackStatusCode = 500) {
    const redirectUrl = buildFrontendAuthRedirectUrl(provider, query);
    if (redirectUrl) {
        return res.redirect(302, redirectUrl);
    }
    const statusCode = query.error ? fallbackStatusCode : 200;
    return res.status(statusCode).json(query);
}

module.exports = {
    getAppBaseUrl,
    getPublicApiBaseUrl,
    buildWebSocialCallbackUrl,
    encodeOAuthStatePayload,
    decodeOAuthStatePayload,
    createOAuthStatePayload,
    readOAuthStatePayloadFromRequest,
    buildApiOAuthCallbackUrl,
    buildFrontendAuthRedirectUrl,
    sendNativeSocialAuthCompletionPage,
    getEnabledSocialProviders,
    getSocialProviderConfig,
    isSocialProviderAvailable,
    issueSocialAuthExchangeCode,
    issueNativeSocialLoginAttempt,
    getValidSocialAuthExchangeCode,
    getNativeSocialLoginAttempt,
    markSocialAuthExchangeCodeUsed,
    completeNativeSocialLoginAttemptSuccess,
    completeNativeSocialLoginAttemptError,
    markNativeSocialLoginAttemptConsumed,
    markNativeSocialLoginAttemptConsumedByExchangeCode,
    findAuthIdentity,
    touchAuthIdentity,
    createAuthIdentity,
    buildUsernameCandidate,
    generateUniqueUsername,
    generateUniqueSocialUsername,
    exchangeGoogleAuthorizationCode,
    fetchGoogleUserInfo,
    resolveOrCreateGoogleAuthUser,
    exchangeYandexAuthorizationCode,
    fetchYandexUserInfo,
    resolveOrCreateYandexAuthUser,
    buildYandexAuthorizeUrl,
    buildGoogleAuthorizeUrl,
    redirectSocialAuthResult,
};
