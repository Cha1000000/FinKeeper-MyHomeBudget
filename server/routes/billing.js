const express = require('express');
const router = express.Router();

const { authenticateToken } = require('../middleware/authenticate');
const { promoRedeemRateLimiter } = require('../middleware/rateLimit');
const {
    getUserEntitlements,
    startFreeTrial,
    redeemPromoCode,
} = require('../services/entitlementService');
const logger = require('../logger');

router.use(authenticateToken);

function notifyEntitlementUpdated(req, userId, entitlements) {
    const broadcastEntitlements = req.app.get('broadcastEntitlements');
    if (broadcastEntitlements) {
        broadcastEntitlements(userId, entitlements);
    }
    const broadcast = req.app.get('broadcast');
    if (broadcast) {
        broadcast(userId, 'entitlements', 'updated');
    }
}

router.get('/status', (req, res) => {
    const entitlements = getUserEntitlements(req.user.id);
    return res.json({ entitlements });
});

router.post('/start-trial', (req, res) => {
    try {
        const entitlements = startFreeTrial(req.user.id);
        notifyEntitlementUpdated(req, req.user.id, entitlements);
        logger.info('trial_started', {
            requestId: req.requestId,
            userId: req.user.id,
            expiresAt: entitlements.expiresAt,
        });
        return res.json({
            success: true,
            entitlements,
        });
    } catch (error) {
        const statusCode = error.statusCode || 500;
        if (statusCode >= 500) {
            logger.error('start_trial_failed', {
                requestId: req.requestId,
                userId: req.user.id,
                error,
            });
        }
        return res.status(statusCode).json({
            error: error.message || 'Не удалось активировать пробный период',
            code: error.code || 'trial_activation_failed',
        });
    }
});

router.post('/redeem-promo', promoRedeemRateLimiter, (req, res) => {
    try {
        const entitlements = redeemPromoCode(req.user.id, req.body?.code);
        notifyEntitlementUpdated(req, req.user.id, entitlements);
        logger.info('promo_redeemed', {
            requestId: req.requestId,
            userId: req.user.id,
            code: req.body?.code,
            expiresAt: entitlements.expiresAt,
        });
        return res.json({
            success: true,
            entitlements,
        });
    } catch (error) {
        const statusCode = error.statusCode || 500;
        if (statusCode >= 500) {
            logger.error('redeem_promo_failed', {
                requestId: req.requestId,
                userId: req.user.id,
                error,
            });
        }
        return res.status(statusCode).json({
            error: error.message || 'Не удалось активировать промокод',
            code: error.code || 'promo_redemption_failed',
        });
    }
});

module.exports = router;
