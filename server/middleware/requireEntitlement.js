const { getUserEntitlements } = require('../services/entitlementService');

function requireEntitlement(featureKey) {
    return (req, res, next) => {
        if (!req.user?.id) {
            return res.status(401).json({
                error: 'Access token required',
                code: 'access_token_required',
            });
        }

        const entitlements = getUserEntitlements(req.user.id);
        req.entitlements = entitlements;

        if (!entitlements.features?.[featureKey]) {
            return res.status(403).json({
                error: 'Эта функция доступна в тарифе Семейный PRO',
                code: 'PRO_REQUIRED',
                feature: featureKey,
                entitlements,
            });
        }

        return next();
    };
}

module.exports = {
    requireEntitlement,
};
