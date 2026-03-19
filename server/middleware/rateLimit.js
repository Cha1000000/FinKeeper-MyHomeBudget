const config = require('../config');

const rateLimitBuckets = new Map();

function buildRateLimitKey(req, scope) {
    const normalizedUsername = typeof req.body?.username === 'string' && req.body.username.trim().length > 0
        ? req.body.username.trim().toLowerCase()
        : null;
    const normalizedEmail = typeof req.body?.email === 'string' && req.body.email.trim().length > 0
        ? req.body.email.trim().toLowerCase()
        : null;
    const identity = req.user?.id ? `user:${req.user.id}` : `ip:${req.ip || req.socket?.remoteAddress || 'unknown'}`;
    if (normalizedEmail) {
        return `${scope}:${identity}:email:${normalizedEmail}`;
    }
    if (normalizedUsername) {
        return `${scope}:${identity}:username:${normalizedUsername}`;
    }
    return `${scope}:${identity}`;
}

const createRateLimiter = (options) => {
    return (req, res, next) => {
        const key = buildRateLimitKey(req, options.scope);
        const now = Date.now();

        let bucket = rateLimitBuckets.get(key) || [];
        bucket = bucket.filter(timestamp => now - timestamp < options.windowMs);

        if (bucket.length >= options.max) {
            return res.status(429).json({
                error: options.message || 'Too many requests, please try again later.',
            });
        }

        bucket.push(now);
        rateLimitBuckets.set(key, bucket);
        next();
    };
};

const authRateLimiter = createRateLimiter({
    scope: 'auth',
    max: config.authRateLimitMax,
    windowMs: config.rateLimitWindowMs,
});

const passwordRateLimiter = createRateLimiter({
    scope: 'password',
    max: config.passwordRateLimitMax,
    windowMs: config.rateLimitWindowMs,
});

const restoreRateLimiter = createRateLimiter({
    scope: 'restore',
    max: config.restoreRateLimitMax,
    windowMs: config.rateLimitWindowMs,
});

const emailVerificationRateLimiter = createRateLimiter({
    scope: 'email_verification',
    max: config.emailVerificationRateLimitMax,
    windowMs: config.rateLimitWindowMs,
});

const passwordRecoveryRequestRateLimiter = createRateLimiter({
    scope: 'password_recovery_request',
    max: config.passwordRecoveryRequestRateLimitMax,
    windowMs: config.rateLimitWindowMs,
});

const passwordRecoveryConfirmRateLimiter = createRateLimiter({
    scope: 'password_recovery_confirm',
    max: config.passwordRecoveryConfirmRateLimitMax,
    windowMs: config.rateLimitWindowMs,
});

// Periodic cleanup of expired rate-limit buckets to prevent memory leaks
setInterval(() => {
    const now = Date.now();
    for (const [key, bucket] of rateLimitBuckets) {
        const activeBucket = bucket.filter(ts => now - ts < config.rateLimitWindowMs);
        if (activeBucket.length === 0) {
            rateLimitBuckets.delete(key);
        } else {
            rateLimitBuckets.set(key, activeBucket);
        }
    }
}, 60 * 1000);

module.exports = {
    authRateLimiter,
    passwordRateLimiter,
    restoreRateLimiter,
    emailVerificationRateLimiter,
    passwordRecoveryRequestRateLimiter,
    passwordRecoveryConfirmRateLimiter,
};
