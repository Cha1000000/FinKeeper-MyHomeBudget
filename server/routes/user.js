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
    buildUserPayload,
    listActiveRefreshSessions,
    updateRefreshSessionDeviceMetadata,
    revokeUserRefreshSessionById,
    extractDeviceMetadata,
    getRefreshTokenFromRequest,
    getValidRefreshSession,
} = require('../services/authService');
const { getUserEntitlements } = require('../services/entitlementService');
const { requireEntitlement } = require('../middleware/requireEntitlement');

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
        const entitlements = getUserEntitlements(req.user.id);
        const backups = listUserBackupEntries(req.user.id);
        res.json({
            backups,
            maxBackups: entitlements.limits.maxBackups,
            isExtended: entitlements.features.extendedBackups,
        });
    } catch (e) {
        logger.error('list_backups_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: e,
        });
        res.status(500).json({ error: 'Failed to list backups' });
    }
});

router.put('/backups/:id/pin', requireEntitlement('extendedBackups'), (req, res) => {
    const backupId = Number(req.params.id);
    if (!Number.isInteger(backupId) || backupId <= 0) {
        return sendError(res, 400, 'Некорректный ID резервной копии', 'invalid_backup_id');
    }

    const row = getUserBackupRecord(req.user.id, backupId);
    if (!row) {
        return sendError(res, 404, 'Резервная копия не найдена', 'backup_not_found');
    }

    const nextPinned = req.body?.isPinned !== undefined
        ? (req.body.isPinned ? 1 : 0)
        : (row.is_pinned ? 0 : 1);

    db.prepare('UPDATE user_backups SET is_pinned = ? WHERE id = ? AND user_id = ?')
        .run(nextPinned, backupId, req.user.id);

    const updatedRow = getUserBackupRecord(req.user.id, backupId);
    return res.json({
        success: true,
        backup: buildBackupEntryPayload(updatedRow),
    });
});

router.get('/backups/:id/export', requireEntitlement('extendedBackups'), (req, res) => {
    const backupId = Number(req.params.id);
    if (!Number.isInteger(backupId) || backupId <= 0) {
        return sendError(res, 400, 'Некорректный ID резервной копии', 'invalid_backup_id');
    }

    const row = getUserBackupRecord(req.user.id, backupId);
    if (!row) {
        return sendError(res, 404, 'Резервная копия не найдена', 'backup_not_found');
    }

    const parsedData = parseBackupDataSafely(row.data);
    if (!parsedData) {
        return sendError(res, 500, 'Данные резервной копии повреждены', 'backup_corrupted');
    }

    const datePart = String(row.created_at || nowIso()).slice(0, 10);
    const filename = `finkeeper24-backup-${backupId}-${datePart}.json`;
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.setHeader('Content-Type', 'application/json; charset=utf-8');

    return res.json({
        format: 'finkeeper24-backup',
        version: '2.3.0',
        exportedAt: nowIso(),
        backupId: row.id,
        createdAt: row.created_at,
        isPinned: Boolean(row.is_pinned),
        data: parsedData,
    });
});

router.post('/backups/import', requireEntitlement('extendedBackups'), (req, res) => {
    const rawPayload = req.body?.backup ?? req.body?.data ?? req.body;
    const restoreImmediately = Boolean(req.body?.restoreImmediately);
    const confirmationText = typeof req.body?.confirmationText === 'string'
        ? req.body.confirmationText.trim()
        : '';

    const snapshot = (rawPayload && rawPayload.format === 'finkeeper24-backup' && rawPayload.data)
        ? rawPayload.data
        : rawPayload;

    if (!snapshot || typeof snapshot !== 'object' || !Array.isArray(snapshot.categories) || !Array.isArray(snapshot.months)) {
        return sendError(res, 400, 'Некорректный формат JSON-файла резервной копии FinKeeper24', 'invalid_backup_format');
    }

    if (restoreImmediately && confirmationText !== BACKUP_RESTORE_CONFIRMATION_TEXT) {
        return sendError(
            res,
            400,
            `Для подтверждения введите "${BACKUP_RESTORE_CONFIRMATION_TEXT}"`,
            'invalid_confirmation_text'
        );
    }

    try {
        const serialized = JSON.stringify({
            categories: Array.isArray(snapshot.categories) ? snapshot.categories : [],
            income_sources: Array.isArray(snapshot.income_sources) ? snapshot.income_sources : [],
            savings_goals: Array.isArray(snapshot.savings_goals) ? snapshot.savings_goals : [],
            months: Array.isArray(snapshot.months) ? snapshot.months : [],
            incomes: Array.isArray(snapshot.incomes) ? snapshot.incomes : [],
            expenses: Array.isArray(snapshot.expenses) ? snapshot.expenses : [],
            budgets: Array.isArray(snapshot.budgets) ? snapshot.budgets : [],
            savings_transactions: Array.isArray(snapshot.savings_transactions) ? snapshot.savings_transactions : [],
            auto_created_records: Array.isArray(snapshot.auto_created_records) ? snapshot.auto_created_records : [],
            planned_overrides: Array.isArray(snapshot.planned_overrides) ? snapshot.planned_overrides : [],
        });

        const info = db.prepare(
            'INSERT INTO user_backups (user_id, data, is_pinned, created_at) VALUES (?, ?, 0, ?)'
        ).run(req.user.id, serialized, nowIso());

        const insertedRow = getUserBackupRecord(req.user.id, Number(info.lastInsertRowid));
        if (restoreImmediately) {
            restoreBackupSnapshot(req.user.id, JSON.parse(serialized));
            const broadcast = req.app.get('broadcast');
            if (broadcast) broadcast(req.user.id, 'all', 'restored');
        }

        return res.json({
            success: true,
            restored: restoreImmediately,
            backup: buildBackupEntryPayload(insertedRow),
        });
    } catch (e) {
        logger.error('import_backup_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: e,
        });
        return sendError(res, 500, e.message || 'Не удалось импортировать резервную копию', 'backup_import_failed');
    }
});

router.get('/sessions', (req, res) => {
    try {
        let currentSessionId = req.user?.sid ? Number(req.user.sid) : null;
        if (!currentSessionId) {
            const rt = getRefreshTokenFromRequest(req);
            const session = rt ? getValidRefreshSession(rt) : null;
            if (session && session.user_id === req.user.id) {
                currentSessionId = session.id;
            }
        }
        if (currentSessionId) {
            updateRefreshSessionDeviceMetadata(currentSessionId, req.user.id, extractDeviceMetadata(req));
        }
        const entitlements = getUserEntitlements(req.user.id);
        const sessions = listActiveRefreshSessions(req.user.id, currentSessionId);
        return res.json({
            sessions,
            maxDevices: entitlements.limits.maxDevices,
            isUnlimited: entitlements.features.unlimitedDevices,
        });
    } catch (e) {
        logger.error('list_sessions_failed', {
            requestId: req.requestId,
            userId: req.user.id,
            error: e,
        });
        return sendError(res, 500, 'Не удалось загрузить список активных устройств', 'list_sessions_failed');
    }
});

router.delete('/sessions/:id', (req, res) => {
    const sessionId = Number(req.params.id);
    if (!Number.isInteger(sessionId) || sessionId <= 0) {
        return sendError(res, 400, 'Некорректный ID сессии', 'invalid_session_id');
    }

    const revoked = revokeUserRefreshSessionById(req.user.id, sessionId);
    if (!revoked) {
        return sendError(res, 404, 'Сессия не найдена или уже завершена', 'session_not_found');
    }

    return res.json({
        success: true,
        id: sessionId,
    });
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
