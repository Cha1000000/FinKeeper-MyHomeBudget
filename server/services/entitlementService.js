const { db, nowIso } = require('../db/connection');

const TRIAL_DURATION_DAYS = 14;
const MS_PER_DAY = 24 * 60 * 60 * 1000;
const LIFETIME_FAMILY_PROMO_CODE = 'BOBA777';
const DEFAULT_OWNER_PRO_EMAILS = ['racerkafa@yandex.ru'];

function getOwnerProEmails() {
    const raw = process.env.OWNER_PRO_EMAILS?.trim();
    const list = raw
        ? raw.split(',').map(item => item.trim().toLowerCase()).filter(Boolean)
        : DEFAULT_OWNER_PRO_EMAILS;
    return new Set(list);
}

function isLifetimeOwnerAccount(userId) {
    if (!userId) return false;
    const ownerEmails = getOwnerProEmails();
    if (ownerEmails.size === 0) return false;

    const user = db.prepare(
        'SELECT email, email_confirmed_at FROM users WHERE id = ?'
    ).get(userId);
    if (user?.email && user?.email_confirmed_at) {
        if (ownerEmails.has(String(user.email).trim().toLowerCase())) {
            return true;
        }
    }

    const identities = db.prepare(
        'SELECT provider_email, provider_email_verified FROM auth_identities WHERE user_id = ?'
    ).all(userId);
    return identities.some(identity => (
        Boolean(identity.provider_email_verified) &&
        identity.provider_email &&
        ownerEmails.has(String(identity.provider_email).trim().toLowerCase())
    ));
}

const FREE_LIMITS = Object.freeze({
    maxBackups: 5,
    maxDevices: 2,
    analyticsMonths: 6,
    maxFamilyMembers: 1,
});

const PRO_LIMITS = Object.freeze({
    maxBackups: 30,
    maxDevices: 99,
    analyticsMonths: 36,
    maxFamilyMembers: 5,
});

function createEntitlementError(message, code, statusCode = 400) {
    const error = new Error(message);
    error.code = code;
    error.statusCode = statusCode;
    return error;
}

function getSubscriptionRow(userId) {
    if (!userId) return null;
    return db.prepare('SELECT * FROM subscriptions WHERE user_id = ?').get(userId) || null;
}

function getUserEntitlements(userId, now = new Date()) {
    let sub = getSubscriptionRow(userId);
    const nowTime = now.getTime();
    const nowStr = now.toISOString();

    // Автор приложения (подтверждённый email владельца) всегда имеет бесплатный вечный Семейный PRO
    if (isLifetimeOwnerAccount(userId) && (!sub || sub.plan !== 'pro_family' || sub.expires_at !== null || sub.status !== 'active')) {
        const startedAt = sub?.started_at || nowStr;
        db.prepare(`
            INSERT INTO subscriptions (user_id, plan, status, source, trial_used, started_at, expires_at, created_at, updated_at)
            VALUES (?, 'pro_family', 'active', 'manual_grant', 0, ?, NULL, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                plan = 'pro_family',
                status = 'active',
                source = 'manual_grant',
                expires_at = NULL,
                updated_at = excluded.updated_at
        `).run(userId, startedAt, nowStr, nowStr);
        sub = getSubscriptionRow(userId);
    }

    if (sub && sub.expires_at) {
        const expiresTime = new Date(sub.expires_at).getTime();
        if (Number.isFinite(expiresTime) && expiresTime <= nowTime && (sub.status === 'active' || sub.status === 'trial' || sub.status === 'grace_period')) {
            db.prepare(`
                UPDATE subscriptions
                SET plan = 'free', status = 'expired', updated_at = ?
                WHERE user_id = ?
            `).run(nowStr, userId);
            sub = getSubscriptionRow(userId);
        }
    }

    const activeProStatuses = new Set(['active', 'trial', 'grace_period']);
    const isNotExpired = !sub?.expires_at || new Date(sub.expires_at).getTime() > nowTime;
    const isPro = Boolean(
        sub &&
        sub.plan &&
        sub.plan !== 'free' &&
        activeProStatuses.has(sub.status) &&
        isNotExpired
    );

    const effectivePlan = isPro ? sub.plan : 'free';
    const effectiveStatus = isPro ? sub.status : (sub?.status === 'expired' ? 'expired' : 'active');
    const isTrial = Boolean(isPro && effectiveStatus === 'trial');
    const isLifetime = Boolean(isPro && !sub?.expires_at && !isTrial);
    const trialUsed = Boolean(sub?.trial_used);
    const canStartTrial = !trialUsed && !isPro;

    let daysRemaining = null;
    if (isPro && sub?.expires_at) {
        const diffMs = new Date(sub.expires_at).getTime() - nowTime;
        daysRemaining = Math.max(0, Math.ceil(diffMs / MS_PER_DAY));
    }

    const limits = isPro ? { ...PRO_LIMITS } : { ...FREE_LIMITS };

    return {
        plan: effectivePlan,
        status: effectiveStatus,
        isPro,
        isTrial,
        isLifetime,
        trialUsed,
        canStartTrial,
        startedAt: sub?.started_at || null,
        expiresAt: isPro ? (sub?.expires_at || null) : (sub?.expires_at || null),
        daysRemaining,
        source: isPro ? (sub?.source || 'system') : 'system',
        features: {
            exportReports: isPro,
            extendedAnalytics: isPro,
            extendedBackups: isPro,
            unlimitedDevices: isPro,
            familyAccess: isPro,
            prioritySupport: isPro,
        },
        limits,
    };
}

function startFreeTrial(userId, now = new Date()) {
    const activateTx = db.transaction(() => {
        const current = getUserEntitlements(userId, now);
        if (current.trialUsed) {
            throw createEntitlementError(
                'Пробный период 14 дней уже был использован на этом аккаунте',
                'TRIAL_ALREADY_USED',
                409
            );
        }
        if (current.isPro) {
            throw createEntitlementError(
                'У вас уже активна подписка Семейный PRO',
                'ALREADY_PRO',
                409
            );
        }

        const startedAt = now.toISOString();
        const expiresAt = new Date(now.getTime() + TRIAL_DURATION_DAYS * MS_PER_DAY).toISOString();

        db.prepare(`
            INSERT INTO subscriptions (user_id, plan, status, source, trial_used, started_at, expires_at, created_at, updated_at)
            VALUES (?, 'pro', 'trial', 'trial', 1, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                plan = 'pro',
                status = 'trial',
                source = 'trial',
                trial_used = 1,
                started_at = excluded.started_at,
                expires_at = excluded.expires_at,
                updated_at = excluded.updated_at
        `).run(userId, startedAt, expiresAt, startedAt, startedAt);

        db.prepare(`
            INSERT INTO billing_transactions (user_id, provider, external_payment_id, plan_period, amount_rub, status, raw_payload, created_at, updated_at)
            VALUES (?, 'trial', NULL, 'trial_14d', 0, 'succeeded', ?, ?, ?)
        `).run(userId, JSON.stringify({ durationDays: TRIAL_DURATION_DAYS }), startedAt, startedAt);

        return getUserEntitlements(userId, now);
    });

    return activateTx();
}

function redeemPromoCode(userId, rawCode, now = new Date()) {
    const normalizedCode = typeof rawCode === 'string' ? rawCode.trim().toUpperCase() : '';
    if (!normalizedCode) {
        throw createEntitlementError('Введите промокод', 'PROMO_CODE_REQUIRED', 400);
    }

    const redeemTx = db.transaction(() => {
        const promo = db.prepare('SELECT * FROM promo_codes WHERE code = ?').get(normalizedCode);
        if (!promo || !promo.is_active) {
            throw createEntitlementError('Промокод не найден или неактивен', 'PROMO_NOT_FOUND', 404);
        }

        const nowTime = now.getTime();
        const nowStr = now.toISOString();

        if (promo.expires_at && new Date(promo.expires_at).getTime() <= nowTime) {
            throw createEntitlementError('Срок действия промокода истёк', 'PROMO_EXPIRED', 410);
        }

        if (promo.max_uses != null && promo.used_count >= promo.max_uses) {
            throw createEntitlementError('Лимит активаций этого промокода исчерпан', 'PROMO_EXHAUSTED', 409);
        }

        const alreadyRedeemed = db.prepare(
            'SELECT id FROM promo_code_redemptions WHERE user_id = ? AND code = ?'
        ).get(userId, normalizedCode);
        if (alreadyRedeemed) {
            throw createEntitlementError('Вы уже активировали этот промокод', 'PROMO_ALREADY_REDEEMED', 409);
        }

        const current = getUserEntitlements(userId, now);
        if (current.isLifetime) {
            throw createEntitlementError(
                'У вас уже активирован бессрочный доступ Семейный PRO',
                'ALREADY_LIFETIME_PRO',
                409
            );
        }

        const existingRow = getSubscriptionRow(userId);
        const isLifetimePromo = Boolean(promo.is_lifetime);
        const targetPlan = promo.plan === 'pro_family' || existingRow?.plan === 'pro_family'
            ? 'pro_family'
            : (promo.plan || 'pro');

        const startedAt = (current.isPro && existingRow?.started_at) ? existingRow.started_at : nowStr;
        let expiresAt = null;
        if (!isLifetimePromo) {
            const baseTime = (current.isPro && current.expiresAt && new Date(current.expiresAt).getTime() > nowTime)
                ? new Date(current.expiresAt).getTime()
                : nowTime;
            expiresAt = new Date(baseTime + Number(promo.duration_days) * MS_PER_DAY).toISOString();
        }

        db.prepare(`
            INSERT INTO subscriptions (user_id, plan, status, source, trial_used, started_at, expires_at, created_at, updated_at)
            VALUES (?, ?, 'active', 'promo', ?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                plan = excluded.plan,
                status = 'active',
                source = 'promo',
                expires_at = excluded.expires_at,
                updated_at = excluded.updated_at
        `).run(userId, targetPlan, existingRow?.trial_used ? 1 : 0, startedAt, expiresAt, nowStr, nowStr);

        db.prepare('UPDATE promo_codes SET used_count = used_count + 1 WHERE code = ?').run(normalizedCode);
        db.prepare(
            'INSERT INTO promo_code_redemptions (user_id, code, redeemed_at) VALUES (?, ?, ?)'
        ).run(userId, normalizedCode, nowStr);

        db.prepare(`
            INSERT INTO billing_transactions (user_id, provider, external_payment_id, plan_period, amount_rub, status, raw_payload, created_at, updated_at)
            VALUES (?, 'promo', NULL, ?, 0, 'succeeded', ?, ?, ?)
        `).run(
            userId,
            isLifetimePromo ? 'lifetime' : `promo_${promo.duration_days}d`,
            JSON.stringify({
                code: normalizedCode,
                plan: targetPlan,
                isLifetime: isLifetimePromo,
                durationDays: Number(promo.duration_days) || 0,
            }),
            nowStr,
            nowStr
        );

        return getUserEntitlements(userId, now);
    });

    return redeemTx();
}

module.exports = {
    TRIAL_DURATION_DAYS,
    LIFETIME_FAMILY_PROMO_CODE,
    FREE_LIMITS,
    PRO_LIMITS,
    isLifetimeOwnerAccount,
    getUserEntitlements,
    startFreeTrial,
    redeemPromoCode,
};
