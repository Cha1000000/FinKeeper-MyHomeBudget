const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');

const { db, nowIso } = require('../db/connection');
const { 
    validateRenamePayload,
    validatePasswordUpdatePayload,
    validateOptionalEmail,
    normalizeEmail,
    issueEmailVerificationToken,
    buildDebugTokenPreview,
    buildMailDeliveryResult,
    buildUserPayload
} = require('../services/authService');

const { 
    authRateLimiter, 
    passwordRateLimiter, 
    emailVerificationRateLimiter,
    restoreRateLimiter
} = require('../middleware/rateLimit');

const { authenticateToken, sendError, sendValidationError } = require('../middleware/authenticate');
const { 
    createBackup, 
    listUserBackupEntries, 
    getUserBackupRecord, 
    parseBackupDataSafely, 
    restoreBackupSnapshot, 
    buildBackupEntryPayload 
} = require('../db/helpers');

const { sendEmailVerificationEmail } = require('../mailer');
const logger = require('../logger');

const BACKUP_RESTORE_CONFIRMATION_TEXT = 'ВОССТАНОВИТЬ';

// Защита всех роутов внутри /api/user токеном:
router.use(authenticateToken);

router.put('/rename', (req, res) => {
    const { newUsername, details } = validateRenamePayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    try {
        db.prepare('UPDATE users SET username = ? WHERE id = ?').run(newUsername, req.user.id);
        res.json({ success: true, username: newUsername });
    } catch (err) {
        if (err.message.includes('UNIQUE constraint failed')) {
            return sendError(res, 409, 'Username already exists', 'username_conflict');
        }
        logger.error('rename_user_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: err,
        });
        return sendError(res, 500, 'Internal server error', 'internal_error');
    }
});

router.put('/password', passwordRateLimiter, (req, res) => {
    const { currentPassword, newPassword, details } = validatePasswordUpdatePayload(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const user = db.prepare('SELECT password_hash, auth_password_enabled FROM users WHERE id = ?').get(req.user.id);
    if (!user) return sendError(res, 404, 'User not found', 'user_not_found');
    if (!user.auth_password_enabled) {
        return sendError(res, 403, 'Password sign-in is not enabled for this account', 'password_auth_disabled');
    }

    const currentPasswordIsValid = bcrypt.compareSync(currentPassword, user.password_hash);
    if (!currentPasswordIsValid) return sendError(res, 401, 'Current password is incorrect', 'invalid_current_password');

    const hashedPassword = bcrypt.hashSync(newPassword, 10);
    db.prepare('UPDATE users SET password_hash = ?, auth_password_enabled = 1 WHERE id = ?').run(hashedPassword, req.user.id);
    res.json({ success: true });
});

router.put('/email', emailVerificationRateLimiter, async (req, res) => {
    const { email, details } = validateOptionalEmail(req.body);
    if (details.length > 0) return sendValidationError(res, details);

    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(req.user.id);
    if (!user) return sendError(res, 404, 'User not found', 'user_not_found');

    const normalizedExistingEmail = normalizeEmail(user.email);
    const hasEmailChanged = email !== normalizedExistingEmail;

    if (!email) {
        db.prepare(`
            UPDATE users
            SET email = NULL, email_confirmed_at = NULL
            WHERE id = ?
        `).run(user.id);
        db.prepare('DELETE FROM auth_email_verification_tokens WHERE user_id = ?').run(user.id);
        db.prepare('DELETE FROM auth_password_reset_tokens WHERE user_id = ?').run(user.id);

        const updatedUser = db.prepare(`
            SELECT id, username, email, email_confirmed_at, created_at
            FROM users
            WHERE id = ?
        `).get(user.id);

        return res.json({
            success: true,
            verificationRequired: false,
            user: buildUserPayload(updatedUser),
        });
    }

    const conflictingUser = db.prepare(`
        SELECT id
        FROM users
        WHERE email = ? AND id != ?
    `).get(email, user.id);
    if (conflictingUser) {
        return sendError(res, 409, 'Этот email уже используется', 'email_conflict');
    }

    db.prepare(`
        UPDATE users
        SET email = ?, email_confirmed_at = CASE WHEN email = ? THEN email_confirmed_at ELSE NULL END
        WHERE id = ?
    `).run(email, email, user.id);
    if (hasEmailChanged) {
        db.prepare('DELETE FROM auth_password_reset_tokens WHERE user_id = ?').run(user.id);
    }

    let debugTokenPreview = null;
    let verificationDelivery = undefined;
    if (hasEmailChanged || !user.email_confirmed_at) {
        const tokenResult = issueEmailVerificationToken(user.id, email);
        debugTokenPreview = buildDebugTokenPreview(tokenResult.token, tokenResult.expiresAt);

        try {
            const mailDelivery = await sendEmailVerificationEmail({
                email,
                username: user.username,
                token: tokenResult.token,
                expiresAt: tokenResult.expiresAt,
            });
            verificationDelivery = buildMailDeliveryResult(mailDelivery);

            logger.info('email_verification_requested', {
                requestId: req.requestId,
                userId: user.id,
                email,
                source: hasEmailChanged ? 'email_update' : 'verification_request_implicit',
                debugPreviewEnabled: Boolean(debugTokenPreview),
                mailDelivered: mailDelivery.delivered,
                mailReason: mailDelivery.reason ?? null,
            });
        } catch (error) {
            logger.error('email_verification_send_failed', {
                requestId: req.requestId,
                userId: user.id,
                email,
                source: hasEmailChanged ? 'email_update' : 'verification_request_implicit',
                error,
            });
            verificationDelivery = buildMailDeliveryResult(null, 'send_failed');
        }
    }

    const updatedUser = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(user.id);

    return res.json({
        success: true,
        verificationRequired: !updatedUser.email_confirmed_at,
        debug: debugTokenPreview ? { emailVerification: debugTokenPreview } : undefined,
        delivery: verificationDelivery,
        user: buildUserPayload(updatedUser),
    });
});

router.post('/email/verification/request', emailVerificationRateLimiter, async (req, res) => {
    const user = db.prepare(`
        SELECT id, username, email, email_confirmed_at, created_at
        FROM users
        WHERE id = ?
    `).get(req.user.id);
    if (!user) return sendError(res, 404, 'Пользователь не найден', 'user_not_found');

    if (!user.email) {
        return sendError(res, 409, 'Email ещё не настроен', 'email_not_configured');
    }

    if (user.email_confirmed_at) {
        return res.json({
            success: true,
            verificationRequired: false,
            user: buildUserPayload(user),
        });
    }

    const { token, expiresAt } = issueEmailVerificationToken(user.id, user.email);
    const debugTokenPreview = buildDebugTokenPreview(token, expiresAt);
    let verificationDelivery = undefined;

    try {
        const mailDelivery = await sendEmailVerificationEmail({
            email: user.email,
            username: user.username,
            token,
            expiresAt,
        });
        verificationDelivery = buildMailDeliveryResult(mailDelivery);

        logger.info('email_verification_requested', {
            requestId: req.requestId,
            userId: user.id,
            email: user.email,
            source: 'verification_request_explicit',
            debugPreviewEnabled: Boolean(debugTokenPreview),
            mailDelivered: mailDelivery.delivered,
            mailReason: mailDelivery.reason ?? null,
        });
    } catch (error) {
        logger.error('email_verification_send_failed', {
            requestId: req.requestId,
            userId: user.id,
            email: user.email,
            source: 'verification_request_explicit',
            error,
        });
        verificationDelivery = buildMailDeliveryResult(null, 'send_failed');
    }

    return res.json({
        success: true,
        verificationRequired: true,
        debug: debugTokenPreview ? { emailVerification: debugTokenPreview } : undefined,
        delivery: verificationDelivery,
        user: buildUserPayload(user),
    });
});

router.post('/backup', (req, res) => {
    try {
        createBackup(req.user.id);
        res.json({ success: true });
    } catch (e) {
        logger.error('manual_backup_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: e,
        });
        res.status(500).json({ error: "Backup creation failed" });
    }
});

router.get('/backups', (req, res) => {
    try {
        const backups = listUserBackupEntries(req.user.id);
        res.json({ backups });
    } catch (e) {
        logger.error('list_backups_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: e,
        });
        res.status(500).json({ error: 'Failed to list backups' });
    }
});

router.post('/restore', restoreRateLimiter, (req, res) => {
    const { backupId, confirmationText } = req.body || {};

    if (!backupId || typeof backupId !== 'number') {
        return res.status(400).json({ error: 'backupId is required and must be a number' });
    }
    if (!confirmationText || typeof confirmationText !== 'string') {
        return res.status(400).json({ error: 'confirmationText is required' });
    }
    if (confirmationText.trim() !== BACKUP_RESTORE_CONFIRMATION_TEXT) {
        return res.status(400).json({ error: `Для подтверждения введите "${BACKUP_RESTORE_CONFIRMATION_TEXT}"` });
    }

    const backupRow = getUserBackupRecord(req.user.id, backupId);
    if (!backupRow) {
        return res.status(404).json({ error: 'Backup not found' });
    }

    const backupData = parseBackupDataSafely(backupRow.data);
    if (!backupData) {
        return res.status(500).json({ error: 'Backup data is corrupted' });
    }

    try {
        restoreBackupSnapshot(req.user.id, backupData);
        const restoredEntry = buildBackupEntryPayload(backupRow);
        res.json({ success: true, restoredBackup: restoredEntry });
        // NOTE: broadcastChange was removed from scope. Data routes broadcast, so we can pass it via req.app.get
        const broadcast = req.app.get('broadcast');
        if (broadcast) broadcast(req.user.id, 'all', 'restored');
    } catch (e) {
        logger.error('restore_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            backupId,
            error: e,
        });
        res.status(500).json({ error: e.message });
    }
});

module.exports = router;
