const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');

const { db, nowIso } = require('../db/connection');
const { 
    issueAuthSession, 
    refreshAuthSession,
    validateAuthPayload,
    createUserWithDefaults,
    getRefreshTokenFromRequest,
    clearRefreshTokenCookie,
    getValidRefreshSession,
    revokeRefreshSessionByToken,
    revokeRefreshSessionById,
    touchRefreshSession,
    getValidPasswordResetToken,
    markPasswordResetTokenUsed,
    revokeAllRefreshSessionsForUser,
    issuePasswordResetToken,
    buildDebugTokenPreview,
    validatePasswordResetConfirmPayload,
    validateTokenOnlyPayload,
    getValidEmailVerificationToken,
    markEmailVerificationTokenUsed,
    buildUserPayload,
    issueEmailVerificationToken,
    validateRequiredEmail,
    findRecoverableUsersByEmail
} = require('../services/authService');

const {
    getSocialProviderConfig,
    getEnabledSocialProviders,
    isSocialProviderAvailable,
    validateNativeSocialAuthStartPayload,
    buildApiOAuthCallbackUrl,
    issueNativeSocialLoginAttempt,
    createOAuthStatePayload,
    encodeOAuthStatePayload,
    buildGoogleAuthorizeUrl,
    buildYandexAuthorizeUrl,
    completeNativeSocialLoginAttemptError,
    getNativeSocialLoginAttempt,
    validateNativeSocialAuthAttemptToken,
    buildWebSocialCallbackUrl,
    setOAuthStateCookie,
    decodeOAuthStatePayload,
    readOAuthStatePayloadFromRequest,
    clearOAuthStateCookie,
    sendNativeSocialAuthCompletionPage,
    redirectSocialAuthResult,
    exchangeGoogleAuthorizationCode,
    fetchGoogleUserInfo,
    resolveOrCreateGoogleAuthUser,
    exchangeYandexAuthorizationCode,
    fetchYandexUserInfo,
    resolveOrCreateYandexAuthUser,
    issueSocialAuthExchangeCode,
    completeNativeSocialLoginAttemptSuccess,
    validateSocialAuthExchangePayload,
    getValidSocialAuthExchangeCode,
    markSocialAuthExchangeCodeUsed,
    markNativeSocialLoginAttemptConsumedByExchangeCode
} = require('../services/socialAuthService');

const { 
    authRateLimiter, 
    passwordRateLimiter, 
    passwordRecoveryRequestRateLimiter, 
    passwordRecoveryConfirmRateLimiter 
} = require('../middleware/rateLimit');

const { authenticateToken, sendError, sendValidationError } = require('../middleware/authenticate');
const { createBackup } = require('../db/helpers');
const { sendPasswordRecoveryEmail, sendEmailVerificationEmail } = require('../mailer');
const { normalizeInputString } = require('../validation/authValidation');
const logger = require('../logger');

// Retrieve auth interval
const nativeSocialAuthPollIntervalMs = process.env.NATIVE_SOCIAL_AUTH_POLL_INTERVAL_MS 
    ? Number(process.env.NATIVE_SOCIAL_AUTH_POLL_INTERVAL_MS) 
    : 1500;

router.post('/register', authRateLimiter, (req, res) => {
    const { username, password, rememberMe, details } = validateAuthPayload(req.body, { requirePasswordMinLength: true });
    if (details.length > 0) return sendValidationError(res, details);

    try {
        const hashedPassword = bcrypt.hashSync(password, 10);
        const user = createUserWithDefaults({
            username,
            passwordHash: hashedPassword,
            authPasswordEnabled: true,
        });

        // Create initial backup
        createBackup(user.id);

        const authResponse = issueAuthSession(req, res, user, rememberMe);
        res.json(authResponse);
    } catch (err) {
        if (err.message.includes('UNIQUE constraint failed')) {
            return sendError(res, 409, 'Username already exists', 'username_conflict');
        }
        logger.error('register_failed', {
            requestId: req.requestId,
            username,
            error: err,
        });
        return sendError(res, 500, 'Internal server error', 'internal_error');
    }
});

router.post('/login', authRateLimiter, (req, res) => {
    const { username, password, rememberMe, details } = validateAuthPayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
    if (!user) return sendError(res, 401, 'Invalid credentials', 'invalid_credentials');
    if (!user.auth_password_enabled) {
        return sendError(res, 403, 'Password sign-in is not enabled for this account', 'password_auth_disabled');
    }

    const passwordIsValid = bcrypt.compareSync(password, user.password_hash);
    if (!passwordIsValid) return sendError(res, 401, 'Invalid credentials', 'invalid_credentials');

    // Create backup on login
    createBackup(user.id);

    const authResponse = issueAuthSession(req, res, user, rememberMe);
    res.json(authResponse);
});

router.post('/refresh', (req, res) => {
    const refreshToken = getRefreshTokenFromRequest(req);
    if (!refreshToken) {
        clearRefreshTokenCookie(res);
        return sendError(res, 401, 'Refresh token required', 'refresh_token_required');
    }

    const session = getValidRefreshSession(refreshToken);
    if (!session || session.revoked_at || session.expires_at <= nowIso()) {
        revokeRefreshSessionByToken(refreshToken);
        clearRefreshTokenCookie(res);
        return sendError(res, 401, 'Invalid refresh token', 'invalid_refresh_token');
    }

    const user = db.prepare('SELECT id, username, email, email_confirmed_at, created_at FROM users WHERE id = ?').get(session.user_id);
    if (!user) {
        revokeRefreshSessionById(session.id);
        clearRefreshTokenCookie(res);
        return sendError(res, 401, 'Invalid refresh token', 'invalid_refresh_token');
    }

    // Prolong session instead of revoking to prevent concurrency race conditions from logging user out
    touchRefreshSession(session.id);
    const authResponse = refreshAuthSession(req, res, user, session);
    res.json(authResponse);
});

router.get('/social/providers', (req, res) => {
    res.set('Cache-Control', 'no-store, no-cache, must-revalidate, private');
    res.set('Pragma', 'no-cache');
    return res.json({
        providers: getEnabledSocialProviders(req),
    });
});

router.post('/oauth/:provider/native/start', authRateLimiter, (req, res) => {
    const providerId = normalizeInputString(req.params.provider).toLowerCase();
    const providerConfig = getSocialProviderConfig(providerId);
    if (!providerConfig) {
        return sendError(res, 404, 'Social provider not found', 'social_provider_not_found');
    }
    if (!isSocialProviderAvailable(providerConfig, req)) {
        return sendError(res, 503, 'Social provider is not configured', 'social_provider_not_configured');
    }
    if (providerId !== 'google' && providerId !== 'yandex') {
        return sendError(
            res,
            501,
            `${providerConfig.displayName} social login handshake is not implemented yet`,
            'social_auth_not_implemented'
        );
    }

    const { clientType, redirectUri, details } = validateNativeSocialAuthStartPayload(req.body);
    if (details.length > 0) {
        return sendValidationError(res, details);
    }

    const apiCallbackUrl = buildApiOAuthCallbackUrl(req, providerId);
    if (!apiCallbackUrl) {
        return sendError(
            res,
            500,
            `${providerConfig.displayName} OAuth callback configuration is incomplete`,
            'social_auth_configuration_invalid'
        );
    }

    const attempt = issueNativeSocialLoginAttempt({
        provider: providerId,
        clientType,
        redirectUri,
    });
    const statePayload = createOAuthStatePayload({
        provider: providerId,
        clientType,
        redirectUri,
        attemptToken: attempt.attemptToken,
    });
    const stateValue = encodeOAuthStatePayload(statePayload);
    const authorizeUrl = providerId === 'google'
        ? buildGoogleAuthorizeUrl(req, providerConfig, stateValue)
        : buildYandexAuthorizeUrl(req, providerConfig, stateValue);

    if (!authorizeUrl) {
        completeNativeSocialLoginAttemptError(
            getNativeSocialLoginAttempt(attempt.attemptToken)?.id,
            { errorCode: 'social_auth_configuration_invalid' }
        );
        return sendError(
            res,
            500,
            `${providerConfig.displayName} OAuth start configuration is incomplete`,
            'social_auth_configuration_invalid'
        );
    }

    return res.json({
        provider: providerId,
        clientType,
        attemptToken: attempt.attemptToken,
        authorizeUrl,
        expiresAt: attempt.expiresAt,
        pollIntervalMs: nativeSocialAuthPollIntervalMs,
    });
});

router.get('/oauth/native/:attemptToken', (req, res) => {
    const { attemptToken, details } = validateNativeSocialAuthAttemptToken(req.params.attemptToken);
    if (details.length > 0) {
        return sendValidationError(res, details);
    }

    const attempt = getNativeSocialLoginAttempt(attemptToken);
    if (!attempt || attempt.expires_at <= nowIso()) {
        return sendError(res, 404, 'Native social auth attempt not found or expired', 'social_auth_attempt_not_found');
    }

    return res.json({
        provider: attempt.provider,
        clientType: attempt.client_type,
        status: attempt.status,
        code: attempt.status === 'completed' ? attempt.exchange_code : null,
        error: attempt.status === 'error' ? attempt.error_code : null,
        errorDescription: attempt.status === 'error' ? attempt.error_description : null,
        expiresAt: attempt.expires_at,
        completedAt: attempt.completed_at || null,
    });
});

router.get('/oauth/:provider/start', (req, res) => {
    const providerId = normalizeInputString(req.params.provider).toLowerCase();
    const providerConfig = getSocialProviderConfig(providerId);
    if (!providerConfig) {
        return sendError(res, 404, 'Social provider not found', 'social_provider_not_found');
    }
    if (!isSocialProviderAvailable(providerConfig, req)) {
        return sendError(res, 503, 'Social provider is not configured', 'social_provider_not_configured');
    }
    if (providerId !== 'google' && providerId !== 'yandex') {
        return sendError(
            res,
            501,
            `${providerConfig.displayName} social login handshake is not implemented yet`,
            'social_auth_not_implemented'
        );
    }

    const frontendCallbackUrl = buildWebSocialCallbackUrl(providerId);
    const apiCallbackUrl = buildApiOAuthCallbackUrl(req, providerId);
    if (!frontendCallbackUrl || !apiCallbackUrl) {
        return sendError(
            res,
            500,
            `${providerConfig.displayName} OAuth callback configuration is incomplete`,
            'social_auth_configuration_invalid'
        );
    }

    const statePayload = createOAuthStatePayload({
        provider: providerId,
        clientType: 'web',
        redirectUri: frontendCallbackUrl,
    });
    const stateValue = encodeOAuthStatePayload(statePayload);
    const authorizeUrl = providerId === 'google'
        ? buildGoogleAuthorizeUrl(req, providerConfig, stateValue)
        : buildYandexAuthorizeUrl(req, providerConfig, stateValue);
    if (!authorizeUrl) {
        return sendError(
            res,
            500,
            `${providerConfig.displayName} OAuth start configuration is incomplete`,
            'social_auth_configuration_invalid'
        );
    }

    setOAuthStateCookie(res, stateValue);
    return res.redirect(302, authorizeUrl);
});

router.get('/oauth/:provider/callback', async (req, res) => {
    const providerId = normalizeInputString(req.params.provider).toLowerCase();
    const providerConfig = getSocialProviderConfig(providerId);
    if (!providerConfig) {
        return sendError(res, 404, 'Social provider not found', 'social_provider_not_found');
    }
    if (!isSocialProviderAvailable(providerConfig, req)) {
        return sendError(res, 503, 'Social provider is not configured', 'social_provider_not_configured');
    }
    if (providerId !== 'google' && providerId !== 'yandex') {
        return sendError(
            res,
            501,
            `${providerConfig.displayName} social login callback is not implemented yet`,
            'social_auth_not_implemented'
        );
    }

    const oauthError = normalizeInputString(req.query?.error);
    const oauthErrorDescription = normalizeInputString(req.query?.error_description);
    const rawStateValue = normalizeInputString(req.query?.state);
    const decodedStatePayload = rawStateValue ? decodeOAuthStatePayload(rawStateValue) : null;
    const nativeAttemptToken = normalizeInputString(decodedStatePayload?.attemptToken);
    const nativeAttempt = nativeAttemptToken ? getNativeSocialLoginAttempt(nativeAttemptToken) : null;
    const isNativeClient = Boolean(
        nativeAttempt &&
        decodedStatePayload &&
        decodedStatePayload.clientType &&
        decodedStatePayload.clientType !== 'web'
    );
    const statePayload = isNativeClient ? decodedStatePayload : readOAuthStatePayloadFromRequest(req);
    clearOAuthStateCookie(res);

    if (!statePayload || statePayload.provider !== providerId) {
        if (nativeAttempt) {
            completeNativeSocialLoginAttemptError(nativeAttempt.id, { errorCode: 'invalid_oauth_state' });
            return sendNativeSocialAuthCompletionPage(res, {
                success: false,
                provider: providerId,
                errorCode: 'invalid_oauth_state',
                statusCode: 400,
            });
        }
        return redirectSocialAuthResult(res, providerId, {
            error: 'invalid_oauth_state',
            provider: providerId,
        }, 400);
    }

    if (oauthError) {
        if (nativeAttempt) {
            completeNativeSocialLoginAttemptError(nativeAttempt.id, {
                errorCode: oauthError,
                errorDescription: oauthErrorDescription || null,
            });
            return sendNativeSocialAuthCompletionPage(res, {
                success: false,
                provider: providerId,
                errorCode: oauthError,
                statusCode: 400,
            });
        }
        return redirectSocialAuthResult(res, providerId, {
            error: oauthError,
            errorDescription: oauthErrorDescription || null,
            provider: providerId,
        }, 400);
    }

    const authorizationCode = normalizeInputString(req.query?.code);
    if (!authorizationCode) {
        if (nativeAttempt) {
            completeNativeSocialLoginAttemptError(nativeAttempt.id, {
                errorCode: `${providerId}_authorization_code_missing`,
            });
            return sendNativeSocialAuthCompletionPage(res, {
                success: false,
                provider: providerId,
                errorCode: `${providerId}_authorization_code_missing`,
                statusCode: 400,
            });
        }
        return redirectSocialAuthResult(res, providerId, {
            error: `${providerId}_authorization_code_missing`,
            provider: providerId,
        }, 400);
    }

    try {
        let user = null;

        if (providerId === 'google') {
            const redirectUri = buildApiOAuthCallbackUrl(req, providerId);
            const tokenPayload = await exchangeGoogleAuthorizationCode({
                code: authorizationCode,
                redirectUri,
                providerConfig,
            });
            const googleAccessToken = normalizeInputString(tokenPayload?.access_token);
            if (!googleAccessToken) {
                throw new Error('Google token response is missing access_token');
            }

            const googleProfile = await fetchGoogleUserInfo(googleAccessToken);
            user = resolveOrCreateGoogleAuthUser(googleProfile);
        } else if (providerId === 'yandex') {
            const tokenPayload = await exchangeYandexAuthorizationCode({
                code: authorizationCode,
                providerConfig,
            });
            const yandexAccessToken = normalizeInputString(tokenPayload?.access_token);
            if (!yandexAccessToken) {
                throw new Error('Yandex token response is missing access_token');
            }

            const yandexProfile = await fetchYandexUserInfo(yandexAccessToken);
            user = resolveOrCreateYandexAuthUser(yandexProfile);
        }

        if (!user) {
            throw new Error(`${providerConfig.displayName} auth user resolution failed`);
        }

        const exchangeCode = issueSocialAuthExchangeCode({
            userId: user.id,
            provider: providerId,
            clientType: statePayload.clientType || (nativeAttempt ? nativeAttempt.client_type : 'web'),
            redirectUri: statePayload.redirectUri || buildWebSocialCallbackUrl(providerId),
        });

        logger.info('social_auth_callback_succeeded', {
            requestId: req.requestId,
            userId: user.id,
            provider: providerId,
        });

        if (nativeAttempt) {
            completeNativeSocialLoginAttemptSuccess(nativeAttempt.id, {
                userId: user.id,
                exchangeCode: exchangeCode.code,
            });
            return sendNativeSocialAuthCompletionPage(res, {
                success: true,
                provider: providerId,
            });
        }

        return redirectSocialAuthResult(res, providerId, {
            code: exchangeCode.code,
            provider: providerId,
        });
    } catch (error) {
        logger.error('social_auth_callback_failed', {
            requestId: req.requestId,
            provider: providerId,
            error,
        });

        if (nativeAttempt) {
            completeNativeSocialLoginAttemptError(nativeAttempt.id, {
                errorCode: `${providerId}_auth_failed`,
            });
            return sendNativeSocialAuthCompletionPage(res, {
                success: false,
                provider: providerId,
                errorCode: `${providerId}_auth_failed`,
                statusCode: 500,
            });
        }

        return redirectSocialAuthResult(res, providerId, {
            error: `${providerId}_auth_failed`,
            provider: providerId,
        }, 500);
    }
});

router.post('/oauth/exchange', authRateLimiter, (req, res) => {
    const { code, rememberMe, details } = validateSocialAuthExchangePayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const exchangeCode = getValidSocialAuthExchangeCode(code);
    if (!exchangeCode || exchangeCode.used_at || exchangeCode.expires_at <= nowIso()) {
        return sendError(res, 400, 'Social auth exchange code is invalid or expired', 'invalid_social_auth_exchange_code');
    }

    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled
        FROM users
        WHERE id = ?
    `).get(exchangeCode.user_id);
    if (!user) {
        markSocialAuthExchangeCodeUsed(exchangeCode.id);
        return sendError(res, 404, 'User not found', 'user_not_found');
    }

    markSocialAuthExchangeCodeUsed(exchangeCode.id);
    markNativeSocialLoginAttemptConsumedByExchangeCode(code);
    createBackup(user.id);

    const authResponse = issueAuthSession(req, res, user, rememberMe);
    return res.json({
        ...authResponse,
        authMethod: 'social',
        provider: exchangeCode.provider,
    });
});

router.post('/password-recovery/request', passwordRecoveryRequestRateLimiter, async (req, res) => {
    const { email, details } = validateRequiredEmail(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const genericResponse = {
        success: true,
        message: 'Если аккаунт с таким email существует и email подтверждён либо подтверждён у соцпровайдера, инструкции по восстановлению уже отправлены.',
    };

    const matchedUsers = findRecoverableUsersByEmail(email);
    const debugPreviews = [];

    for (const user of matchedUsers) {
        const { token, expiresAt } = issuePasswordResetToken(user.id, user.email);
        const debugTokenPreview = buildDebugTokenPreview(token, expiresAt);
        let mailDelivery = { delivered: false, reason: 'not_attempted' };

        try {
            mailDelivery = await sendPasswordRecoveryEmail({
                email: user.email,
                username: user.username,
                token,
                expiresAt,
            });
        } catch (error) {
            logger.error('password_recovery_email_send_failed', {
                requestId: req.requestId,
                userId: user.id,
                email: user.email,
                source: user.source,
                error,
            });
        }

        logger.info('password_recovery_requested', {
            requestId: req.requestId,
            userId: user.id,
            email: user.email,
            source: user.source,
            debugPreviewEnabled: Boolean(debugTokenPreview),
            mailDelivered: mailDelivery.delivered,
            mailReason: mailDelivery.reason ?? null,
        });

        if (debugTokenPreview) {
            debugPreviews.push({
                username: user.username,
                source: user.source,
                ...debugTokenPreview,
            });
        }
    }

    if (debugPreviews.length > 0) {
        genericResponse.debug = {
            passwordReset: debugPreviews[0],
            passwordResets: debugPreviews,
        };
    }

    return res.json(genericResponse);
});

router.post('/password-recovery/confirm', passwordRecoveryConfirmRateLimiter, (req, res) => {
    const { token, newPassword, details } = validatePasswordResetConfirmPayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const resetToken = getValidPasswordResetToken(token);
    if (!resetToken || resetToken.used_at || resetToken.expires_at <= nowIso()) {
        return sendError(res, 400, 'Ссылка для сброса пароля недействительна или устарела', 'invalid_reset_token');
    }

    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(resetToken.user_id);
    if (!user) {
        return sendError(res, 404, 'Пользователь не найден', 'user_not_found');
    }

    const hashedPassword = bcrypt.hashSync(newPassword, 10);
    const transaction = db.transaction(() => {
        db.prepare('UPDATE users SET password_hash = ?, auth_password_enabled = 1 WHERE id = ?').run(hashedPassword, user.id);
        revokeAllRefreshSessionsForUser(user.id);
        markPasswordResetTokenUsed(resetToken.id);
    });

    transaction();

    logger.info('password_recovery_confirmed', {
        requestId: req.requestId,
        userId: user.id,
    });

    return res.json({ success: true });
});

router.post('/email-verification/confirm', (req, res) => {
    const { token, details } = validateTokenOnlyPayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const verificationToken = getValidEmailVerificationToken(token);
    if (!verificationToken || verificationToken.used_at || verificationToken.expires_at <= nowIso()) {
        return sendError(res, 400, 'Ссылка для подтверждения email недействительна или устарела', 'invalid_email_verification_token');
    }

    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(verificationToken.user_id);
    if (!user) {
        return sendError(res, 404, 'Пользователь не найден', 'user_not_found');
    }

    const timestamp = nowIso();
    const transaction = db.transaction(() => {
        db.prepare(`
            UPDATE users
            SET email = ?, email_confirmed_at = ?
            WHERE id = ?
        `).run(verificationToken.email, timestamp, user.id);
        markEmailVerificationTokenUsed(verificationToken.id);
    });

    transaction();

    const updatedUser = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(user.id);

    logger.info('email_verified', {
        requestId: req.requestId,
        userId: user.id,
        email: verificationToken.email,
    });

    return res.json({
        success: true,
        user: buildUserPayload(updatedUser),
    });
});

router.post('/logout', (req, res) => {
    const refreshToken = getRefreshTokenFromRequest(req);
    if (refreshToken) {
        revokeRefreshSessionByToken(refreshToken);
    }

    clearRefreshTokenCookie(res);
    return res.status(204).send();
});

router.get('/me', authenticateToken, (req, res) => {
    const user = db.prepare('SELECT id, username, email, email_confirmed_at, created_at, auth_password_enabled FROM users WHERE id = ?').get(req.user.id);
    if (!user) return sendError(res, 404, 'User not found', 'user_not_found');

    // Check last backup time to avoid spamming backups on reload
    const lastBackup = db.prepare('SELECT created_at FROM user_backups WHERE user_id = ? ORDER BY created_at DESC LIMIT 1').get(req.user.id);
    const shouldBackup = !lastBackup || (new Date() - new Date(lastBackup.created_at + 'Z')) > 60 * 60 * 1000; // 1 hour

    if (shouldBackup) {
        createBackup(req.user.id);
    }

    res.json(buildUserPayload(user));
});

module.exports = router;
