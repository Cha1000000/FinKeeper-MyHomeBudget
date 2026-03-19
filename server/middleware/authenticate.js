const jwt = require('jsonwebtoken');
const config = require('../config');

function createErrorBody(error, code = 'error', details = null) {
    const body = { error, code };
    if (Array.isArray(details) && details.length > 0) {
        body.details = details;
    }
    return body;
}

function sendError(res, statusCode, error, code = 'error', details = null) {
    return res.status(statusCode).json(createErrorBody(error, code, details));
}

function sendValidationError(res, details, error = 'Validation failed', code = 'validation_error') {
    return sendError(res, 400, error, code, details);
}

const authenticateToken = (req, res, next) => {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1]; // Bearer TOKEN

    if (token == null) return sendError(res, 401, 'Access token required', 'access_token_required');

    jwt.verify(token, config.jwtSecret, (err, user) => {
        if (err || user?.type !== 'access') return sendError(res, 403, 'Invalid access token', 'invalid_access_token');
        req.user = user;
        next();
    });
};

module.exports = {
    authenticateToken,
    sendError,
    sendValidationError,
    createErrorBody
};
