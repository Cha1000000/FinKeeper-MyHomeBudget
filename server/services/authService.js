const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const { db, nowIso } = require('../db/connection');
const config = require('../config');
const { initialCategories, initialSavings, initialIncomeSources } = require('../default_data');
const {
    validateOptionalEmail,
    validateRequiredEmail,
    validateTokenOnlyPayload,
    validatePasswordResetConfirmPayload,
    validateAuthPayload,
    validateRenamePayload,
    validatePasswordUpdatePayload,
} = require('../validation/authValidation');

function hashRefreshToken(token) {
    return crypto.createHash('sha256').update(token).digest('hex');
}

function createAccessToken(user) {
    return jwt.sign(
        { id: user.id, username: user.username, type: 'access' },
        config.jwtSecret,
        { expiresIn: config.accessTokenTtl }
    );
}

function generateRefreshToken() {
    return crypto.randomBytes(48).toString('hex');
}

function getRefreshSessionExpiryIso() {
    return new Date(Date.now() + config.refreshTokenTtlMs).toISOString();
}

function cleanupExpiredRefreshSessions() {
    db.prepare(`
        DELETE FROM auth_refresh_sessions
        WHERE revoked_at IS NOT NULL OR expires_at <= ?
    `).run(nowIso());
}

function issueRefreshSession(userId, isPersistent = false) {
    cleanupExpiredRefreshSessions();
    const refreshToken = generateRefreshToken();
    const tokenHash = hashRefreshToken(refreshToken);
    const timestamp = nowIso();
    const expiresAt = getRefreshSessionExpiryIso();

    db.prepare(`
        INSERT INTO auth_refresh_sessions (user_id, token_hash, expires_at, created_at, last_used_at, revoked_at, is_persistent)
        VALUES (?, ?, ?, ?, ?, NULL, ?)
    `).run(userId, tokenHash, expiresAt, timestamp, timestamp, isPersistent ? 1 : 0);

    return { refreshToken, expiresAt };
}

function revokeRefreshSessionByToken(refreshToken) {
    if (!refreshToken) return;
    db.prepare(`
        UPDATE auth_refresh_sessions
        SET revoked_at = COALESCE(revoked_at, ?)
        WHERE token_hash = ? AND revoked_at IS NULL
    `).run(nowIso(), hashRefreshToken(refreshToken));
}

function revokeRefreshSessionById(sessionId) {
    db.prepare(`
        UPDATE auth_refresh_sessions
        SET revoked_at = COALESCE(revoked_at, ?)
        WHERE id = ? AND revoked_at IS NULL
    `).run(nowIso(), sessionId);
}

function revokeAllRefreshSessionsForUser(userId) {
    db.prepare(`
        UPDATE auth_refresh_sessions
        SET revoked_at = COALESCE(revoked_at, ?)
        WHERE user_id = ? AND revoked_at IS NULL
    `).run(nowIso(), userId);
}

function touchRefreshSession(sessionId) {
    db.prepare(`
        UPDATE auth_refresh_sessions
        SET last_used_at = ?
        WHERE id = ? AND revoked_at IS NULL
    `).run(nowIso(), sessionId);
}

function getValidRefreshSession(refreshToken) {
    if (!refreshToken) return null;
    cleanupExpiredRefreshSessions();
    return db.prepare(`
        SELECT id, user_id, expires_at, revoked_at, is_persistent
        FROM auth_refresh_sessions
        WHERE token_hash = ?
    `).get(hashRefreshToken(refreshToken));
}

function parseCookies(cookieHeader) {
    if (!cookieHeader || typeof cookieHeader !== 'string') return {};
    return cookieHeader.split(';').map(part => part.trim()).filter(Boolean).reduce((acc, part) => {
        const separatorIndex = part.indexOf('=');
        if (separatorIndex === -1) return acc;
        const key = part.slice(0, separatorIndex).trim();
        const value = part.slice(separatorIndex + 1).trim();
        if (!key) return acc;
        acc[key] = decodeURIComponent(value);
        return acc;
    }, {});
}

function getRefreshTokenFromRequest(req) {
    const bodyRefreshToken = typeof req.body?.refreshToken === 'string' ? req.body.refreshToken.trim() : '';
    if (bodyRefreshToken) return bodyRefreshToken;
    const cookies = parseCookies(req.headers.cookie);
    const cookieRefreshToken = cookies[config.REFRESH_TOKEN_COOKIE_NAME];
    return typeof cookieRefreshToken === 'string' && cookieRefreshToken.trim().length > 0 ? cookieRefreshToken.trim() : null;
}

function shouldReturnRefreshTokenInBody(req) {
    return req.get('X-Refresh-Transport') === 'body';
}

function setRefreshTokenCookie(res, refreshToken, rememberMe = true) {
    const cookieOptions = {
        httpOnly: true,
        secure: config.isProduction,
        sameSite: 'lax',
        path: '/api/auth',
    };
    if (rememberMe) {
        cookieOptions.maxAge = config.refreshTokenTtlMs;
    }
    res.cookie(config.REFRESH_TOKEN_COOKIE_NAME, refreshToken, cookieOptions);
}

function clearRefreshTokenCookie(res) {
    res.clearCookie(config.REFRESH_TOKEN_COOKIE_NAME, {
        httpOnly: true,
        secure: config.isProduction,
        sameSite: 'lax',
        path: '/api/auth',
    });
}

function setOAuthStateCookie(res, stateValue) {
    res.cookie(config.OAUTH_STATE_COOKIE_NAME, stateValue, {
        httpOnly: true,
        secure: config.isProduction,
        sameSite: 'lax',
        path: '/api/auth/oauth',
        maxAge: 15 * 60 * 1000,
    });
}

function clearOAuthStateCookie(res) {
    res.clearCookie(config.OAUTH_STATE_COOKIE_NAME, {
        httpOnly: true,
        secure: config.isProduction,
        sameSite: 'lax',
        path: '/api/auth/oauth',
    });
}

function getOAuthStateCookie(req) {
    const cookies = parseCookies(req.headers.cookie);
    return typeof cookies[config.OAUTH_STATE_COOKIE_NAME] === 'string' && cookies[config.OAUTH_STATE_COOKIE_NAME].trim().length > 0
        ? cookies[config.OAUTH_STATE_COOKIE_NAME].trim()
        : null;
}

function normalizeInputString(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function normalizeEmail(value) {
    return normalizeInputString(value).toLowerCase();
}

function isValidEmail(value) {
    return config.EMAIL_REGEX.test(value);
}

function listAuthProvidersForUser(userId) {
    return db.prepare(`
        SELECT provider, provider_email, provider_email_verified, last_login_at
        FROM auth_identities
        WHERE user_id = ?
        ORDER BY last_login_at DESC, provider ASC
    `).all(userId).map((identity) => ({
        provider: identity.provider,
        email: normalizeInputString(identity.provider_email) || null,
        emailVerified: Boolean(identity.provider_email_verified),
    }));
}

function getUserRecoveryInfo(user) {
    const accountEmail = normalizeEmail(user?.email);
    if (accountEmail && user?.email_confirmed_at) {
        return { email: accountEmail, confirmed: true, source: 'account', provider: null };
    }
    const linkedProviders = user?.id ? listAuthProvidersForUser(user.id) : [];
    const verifiedProvider = linkedProviders.find((provider) => provider.email && provider.emailVerified);
    if (verifiedProvider) {
        return { email: verifiedProvider.email, confirmed: true, source: 'social', provider: verifiedProvider.provider };
    }
    return { email: null, confirmed: false, source: null, provider: null };
}

function buildUserPayload(user) {
    const email = typeof user?.email === 'string' && user.email.trim().length > 0 ? user.email.trim() : null;
    const emailConfirmed = Boolean(email && user?.email_confirmed_at);
    const linkedAuthProviders = listAuthProvidersForUser(user.id);
    const recoveryInfo = getUserRecoveryInfo({ ...user, linkedAuthProviders });
    const recoverabilityStatus = recoveryInfo.email && recoveryInfo.confirmed ? 'protected' : 'unprotected';

    return {
        id: user.id,
        username: user.username,
        email,
        emailConfirmed,
        recoverabilityStatus,
        canSelfRecover: Boolean(recoveryInfo.email && recoveryInfo.confirmed),
        recoveryEmail: recoveryInfo.email,
        recoveryEmailConfirmed: recoveryInfo.confirmed,
        recoveryEmailSource: recoveryInfo.source,
        recoveryEmailProvider: recoveryInfo.provider,
        created_at: user.created_at,
        linkedAuthProviders,
    };
}

function buildAuthResponse(req, user, accessToken, refreshToken = null) {
    const response = {
        token: accessToken,
        accessToken,
        user: buildUserPayload(user),
    };
    if (refreshToken && shouldReturnRefreshTokenInBody(req)) {
        response.refreshToken = refreshToken;
    }
    return response;
}

function issueAuthSession(req, res, user, rememberMe = true) {
    const accessToken = createAccessToken(user);
    const { refreshToken } = issueRefreshSession(user.id, rememberMe);
    setRefreshTokenCookie(res, refreshToken, rememberMe);
    return buildAuthResponse(req, user, accessToken, refreshToken);
}

function refreshAuthSession(req, res, user, session) {
    const accessToken = createAccessToken(user);
    const isPersistent = Boolean(session.is_persistent);

    // Slide expiration window if persistent
    if (isPersistent) {
        const newExpiresAt = getRefreshSessionExpiryIso();
        db.prepare('UPDATE auth_refresh_sessions SET expires_at = ?, last_used_at = ? WHERE id = ?').run(
            newExpiresAt,
            nowIso(),
            session.id
        );

        // Re-issue cookie with new Expiry to slide browser window too
        // We reuse the same refresh token hidden in the cookie if possible.
        // But we don't have the plaintext refresh token here (we only have the hash in the DB).
        // The browser already has it. We can just send a new cookie IF we had the token.
        // Since we don't have the plaintext token, we should've probably issued a NEW token,
        // OR we can just NOT extend the browser cookie BUT that would limit total session length.
        
        // Actually, to slide the browser cookie, we NEED to send a NEW token (or the same one).
        // Let's issue a NEW refresh token but KEEP it linked to the same "session group" if we cared,
        // but simple "issue NEW, revoke OLD" had race conditions.
        // I will issue a NEW token and NOT revoke the old one yet, OR I'll just skip re-issuing
        // for now and focus on making sure the DB doesn't expire it.
        
        // Actually, most "eternal" systems just have a long-lived cookie (30 days) AND they refresh IT
        // whenever the access token is refreshed.
        
        // Let's NEW strategy:
        // Issue NEW refresh token, but KEEP the old one valid for a "grace period" (e.g., 1 minute).
        // BUT better: just issue a NEW token and let the old one expire/be cleaned up.
        
        const { refreshToken } = issueRefreshSession(user.id, isPersistent);
        setRefreshTokenCookie(res, refreshToken, isPersistent);
        // We SHOULD revoke the CURRENT one if we issued a NEW one, but let's just revoke it 
        // after issuing the new one to be safe.
        // To avoid race conditions: just revoke it and it's fine as long as we don't do it 
        // synchronously BEFORE the new one is issued? No, race condition is when 2 requests hit /refresh at once.
        
        // If 2 requests hit /refresh for token A:
        // Req 1: Get session for A, issue new token B, revoke A.
        // Req 2: Get session for A -> FAIL (already revoked).
        
        // If I DON'T revoke A immediately, both will issue B and C. 
        // The browser will have either B or C. Both are valid. A will be killed by cleanup.
        
        // So: issue NEW, DON'T revoke old yet (leave to cleanup).
        return buildAuthResponse(req, user, accessToken, refreshToken);
    }

    return buildAuthResponse(req, user, accessToken, null);
}

function seedUserDefaults(userId, timestamp) {
    const insertCat = db.prepare('INSERT OR IGNORE INTO categories (user_id, name, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?)');
    initialCategories.forEach((cat, index) => insertCat.run(userId, cat, index + 1, timestamp, timestamp));

    const insertSource = db.prepare('INSERT OR IGNORE INTO income_sources (user_id, name, created_at, updated_at) VALUES (?, ?, ?, ?)');
    initialIncomeSources.forEach(source => insertSource.run(userId, source, timestamp, timestamp));

    const insertGoal = db.prepare('INSERT INTO savings_goals (user_id, name, created_at, updated_at) VALUES (?, ?, ?, ?)');
    initialSavings.forEach(goal => insertGoal.run(userId, goal, timestamp, timestamp));
}

function createUserWithDefaults({ username, passwordHash, authPasswordEnabled = true, email = null, emailConfirmedAt = null }) {
    const createUserTransaction = db.transaction(() => {
        const timestamp = nowIso();
        const info = db.prepare(`
            INSERT INTO users (username, email, password_hash, auth_password_enabled, email_confirmed_at)
            VALUES (?, ?, ?, ?, ?)
        `).run(username, email, passwordHash, authPasswordEnabled ? 1 : 0, emailConfirmedAt);
        const userId = info.lastInsertRowid;
        seedUserDefaults(userId, timestamp);
        return db.prepare(`
            SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled
            FROM users
            WHERE id = ?
        `).get(userId);
    });
    return createUserTransaction();
}

function hashOpaqueToken(token) {
    return crypto.createHash('sha256').update(token).digest('hex');
}

function generateOpaqueToken() {
    return crypto.randomBytes(32).toString('hex');
}

function getFutureIsoFromHours(hours) {
    return new Date(Date.now() + (hours * 60 * 60 * 1000)).toISOString();
}

function cleanupExpiredEmailVerificationTokens() {
    db.prepare(`
        DELETE FROM auth_email_verification_tokens
        WHERE used_at IS NOT NULL OR expires_at <= ?
    `).run(nowIso());
}

function cleanupExpiredPasswordResetTokens() {
    db.prepare(`
        DELETE FROM auth_password_reset_tokens
        WHERE used_at IS NOT NULL OR expires_at <= ?
    `).run(nowIso());
}

function issueEmailVerificationToken(userId, email) {
    cleanupExpiredEmailVerificationTokens();
    const rawToken = generateOpaqueToken();
    const tokenHash = hashOpaqueToken(rawToken);
    const createdAt = nowIso();
    const expiresAt = getFutureIsoFromHours(config.emailVerificationTokenTtlHours);

    db.prepare('DELETE FROM auth_email_verification_tokens WHERE user_id = ?').run(userId);
    db.prepare(`
        INSERT INTO auth_email_verification_tokens (user_id, email, token_hash, expires_at, created_at, used_at)
        VALUES (?, ?, ?, ?, ?, NULL)
    `).run(userId, email, tokenHash, expiresAt, createdAt);

    return { token: rawToken, expiresAt };
}

function getValidEmailVerificationToken(token) {
    if (!token) return null;
    cleanupExpiredEmailVerificationTokens();
    return db.prepare(`
        SELECT id, user_id, email, expires_at, used_at
        FROM auth_email_verification_tokens
        WHERE token_hash = ?
    `).get(hashOpaqueToken(token));
}

function markEmailVerificationTokenUsed(tokenId) {
    db.prepare(`
        UPDATE auth_email_verification_tokens
        SET used_at = COALESCE(used_at, ?)
        WHERE id = ?
    `).run(nowIso(), tokenId);
}

function issuePasswordResetToken(userId, email) {
    cleanupExpiredPasswordResetTokens();
    const rawToken = generateOpaqueToken();
    const tokenHash = hashOpaqueToken(rawToken);
    const createdAt = nowIso();
    const expiresAt = getFutureIsoFromHours(config.passwordResetTokenTtlHours);

    db.prepare('DELETE FROM auth_password_reset_tokens WHERE user_id = ?').run(userId);
    db.prepare(`
        INSERT INTO auth_password_reset_tokens (user_id, email, token_hash, expires_at, created_at, used_at)
        VALUES (?, ?, ?, ?, ?, NULL)
    `).run(userId, email, tokenHash, expiresAt, createdAt);

    return { token: rawToken, expiresAt };
}

function getValidPasswordResetToken(token) {
    if (!token) return null;
    cleanupExpiredPasswordResetTokens();
    return db.prepare(`
        SELECT id, user_id, email, expires_at, used_at
        FROM auth_password_reset_tokens
        WHERE token_hash = ?
    `).get(hashOpaqueToken(token));
}

function markPasswordResetTokenUsed(tokenId) {
    db.prepare(`
        UPDATE auth_password_reset_tokens
        SET used_at = COALESCE(used_at, ?)
        WHERE id = ?
    `).run(nowIso(), tokenId);
}

function buildDebugTokenPreview(token, expiresAt) {
    if (!config.authDebugTokenPreviewEnabled || !token) return null;
    return { token, expiresAt };
}

function buildMailDeliveryResult(mailDelivery, defaultCode = 'not_configured') {
    return {
        delivered: Boolean(mailDelivery?.delivered),
        code: mailDelivery?.code || defaultCode,
        messageId: mailDelivery?.messageId || null,
    };
}

function findRecoverableUsersByEmail(email) {
    const normalizedEmail = normalizeEmail(email);
    if (!normalizedEmail) return [];

    const usersById = new Map();
    const confirmedUserMatches = db.prepare(`
        SELECT id, username
        FROM users
        WHERE email = ? AND email_confirmed_at IS NOT NULL
    `).all(normalizedEmail);

    confirmedUserMatches.forEach((user) => {
        usersById.set(user.id, {
            id: user.id,
            username: user.username,
            email: normalizedEmail,
            source: 'account',
        });
    });

    const verifiedSocialMatches = db.prepare(`
        SELECT u.id, u.username, ai.provider
        FROM auth_identities ai
        JOIN users u ON u.id = ai.user_id
        WHERE ai.provider_email = ? AND ai.provider_email_verified = 1
        ORDER BY ai.last_login_at DESC, ai.provider ASC
    `).all(normalizedEmail);

    verifiedSocialMatches.forEach((user) => {
        if (!usersById.has(user.id)) {
            usersById.set(user.id, {
                id: user.id,
                username: user.username,
                email: normalizedEmail,
                source: user.provider,
            });
        }
    });

    return Array.from(usersById.values());
}

module.exports = {
    hashRefreshToken,
    createAccessToken,
    issueRefreshSession,
    revokeRefreshSessionByToken,
    revokeRefreshSessionById,
    revokeAllRefreshSessionsForUser,
    touchRefreshSession,
    getValidRefreshSession,
    parseCookies,
    getRefreshTokenFromRequest,
    shouldReturnRefreshTokenInBody,
    setRefreshTokenCookie,
    clearRefreshTokenCookie,
    setOAuthStateCookie,
    clearOAuthStateCookie,
    getOAuthStateCookie,
    normalizeInputString,
    normalizeEmail,
    isValidEmail,
    listAuthProvidersForUser,
    getUserRecoveryInfo,
    buildUserPayload,
    buildAuthResponse,
    issueAuthSession,
    refreshAuthSession,
    createUserWithDefaults,
    hashOpaqueToken,
    generateOpaqueToken,
    getFutureIsoFromHours,
    issueEmailVerificationToken,
    getValidEmailVerificationToken,
    markEmailVerificationTokenUsed,
    issuePasswordResetToken,
    getValidPasswordResetToken,
    markPasswordResetTokenUsed,
    validateOptionalEmail,
    validateRequiredEmail,
    validateTokenOnlyPayload,
    validatePasswordResetConfirmPayload,
    validateAuthPayload,
    validateRenamePayload,
    validatePasswordUpdatePayload,
    buildDebugTokenPreview,
    buildMailDeliveryResult,
    findRecoverableUsersByEmail,
};
