const config = require('../config');

function buildValidationDetail(field, message) {
    return { field, message };
}

function normalizeInputString(value) {
    return typeof value === 'string' ? value.trim() : '';
}

function normalizeEmail(value) {
    return normalizeInputString(value).toLowerCase();
}

function readRawString(value) {
    return typeof value === 'string' ? value : '';
}

function isValidEmail(value) {
    return config.EMAIL_REGEX.test(value);
}

function createAuthValidation({
    normalizeEmail,
    normalizeInputString,
    readRawString,
    isValidEmail,
}) {
    function validateOptionalEmail(body) {
        const email = normalizeEmail(body?.email);
        const details = [];

        if (email) {
            if (email.length > 254) {
                details.push(buildValidationDetail('email', 'Email must be 254 characters or fewer'));
            } else if (!isValidEmail(email)) {
                details.push(buildValidationDetail('email', 'Email must be a valid email address'));
            }
        }

        return {
            email,
            details,
        };
    }

    function validateRequiredEmail(body) {
        const email = normalizeEmail(body?.email);
        const details = [];

        if (!email) {
            details.push(buildValidationDetail('email', 'Email is required'));
        } else if (email.length > 254) {
            details.push(buildValidationDetail('email', 'Email must be 254 characters or fewer'));
        } else if (!isValidEmail(email)) {
            details.push(buildValidationDetail('email', 'Email must be a valid email address'));
        }

        return {
            email,
            details,
        };
    }

    function validateTokenOnlyPayload(body, fieldName = 'token') {
        const token = normalizeInputString(body?.[fieldName]);
        const details = [];

        if (!token) {
            details.push(buildValidationDetail(fieldName, 'Token is required'));
        } else if (token.length > 512) {
            details.push(buildValidationDetail(fieldName, 'Token must be 512 characters or fewer'));
        }

        return {
            token,
            details,
        };
    }

    function validateSocialAuthExchangePayload(body) {
        const code = normalizeInputString(body?.code);
        const details = [];

        if (!code) {
            details.push(buildValidationDetail('code', 'Code is required'));
        } else if (code.length > 512) {
            details.push(buildValidationDetail('code', 'Code must be 512 characters or fewer'));
        }

        return {
            code,
            details,
        };
    }

    function validateNativeSocialAuthStartPayload(body) {
        const clientType = normalizeInputString(body?.clientType).toLowerCase() || 'kmp';
        const redirectUri = normalizeInputString(body?.redirectUri);
        const details = [];
        const allowedClientTypes = ['kmp', 'android', 'ios', 'desktop'];

        if (!allowedClientTypes.includes(clientType)) {
            details.push(buildValidationDetail('clientType', 'Client type is not supported'));
        }

        if (redirectUri && redirectUri.length > 2048) {
            details.push(buildValidationDetail('redirectUri', 'Redirect URI must be 2048 characters or fewer'));
        }

        return {
            clientType,
            redirectUri: redirectUri || null,
            details,
        };
    }

    function validateNativeSocialAuthAttemptToken(value) {
        const attemptToken = normalizeInputString(value);
        const details = [];

        if (!attemptToken) {
            details.push(buildValidationDetail('attemptToken', 'Attempt token is required'));
        } else if (attemptToken.length > 512) {
            details.push(buildValidationDetail('attemptToken', 'Attempt token must be 512 characters or fewer'));
        }

        return {
            attemptToken,
            details,
        };
    }

    function validatePasswordResetConfirmPayload(body) {
        const tokenResult = validateTokenOnlyPayload(body);
        const newPassword = readRawString(body?.newPassword);
        const details = [...tokenResult.details];

        if (!newPassword) {
            details.push(buildValidationDetail('newPassword', 'New password is required'));
        } else if (newPassword.length > 256) {
            details.push(buildValidationDetail('newPassword', 'New password must be 256 characters or fewer'));
        } else if (newPassword.length < 6) {
            details.push(buildValidationDetail('newPassword', 'New password must be at least 6 characters'));
        }

        return {
            token: tokenResult.token,
            newPassword,
            details,
        };
    }

    function validateAuthPayload(body, { requirePasswordMinLength = false } = {}) {
        const username = normalizeInputString(body?.username);
        const password = readRawString(body?.password);
        const details = [];

        if (!username) {
            details.push(buildValidationDetail('username', 'Username is required'));
        } else if (username.length > 64) {
            details.push(buildValidationDetail('username', 'Username must be 64 characters or fewer'));
        }

        if (!password) {
            details.push(buildValidationDetail('password', 'Password is required'));
        } else if (password.length > 256) {
            details.push(buildValidationDetail('password', 'Password must be 256 characters or fewer'));
        } else if (requirePasswordMinLength && password.length < 6) {
            details.push(buildValidationDetail('password', 'Password must be at least 6 characters'));
        }

        return {
            username,
            password,
            details,
        };
    }

    function validateRenamePayload(body) {
        const newUsername = normalizeInputString(body?.newUsername);
        const details = [];

        if (!newUsername) {
            details.push(buildValidationDetail('newUsername', 'New username is required'));
        } else if (newUsername.length > 64) {
            details.push(buildValidationDetail('newUsername', 'Username must be 64 characters or fewer'));
        }

        return {
            newUsername,
            details,
        };
    }

    function validatePasswordUpdatePayload(body) {
        const currentPassword = readRawString(body?.currentPassword);
        const newPassword = readRawString(body?.newPassword);
        const details = [];

        if (!currentPassword) {
            details.push(buildValidationDetail('currentPassword', 'Current password is required'));
        } else if (currentPassword.length > 256) {
            details.push(buildValidationDetail('currentPassword', 'Current password must be 256 characters or fewer'));
        }

        if (!newPassword) {
            details.push(buildValidationDetail('newPassword', 'New password is required'));
        } else if (newPassword.length > 256) {
            details.push(buildValidationDetail('newPassword', 'New password must be 256 characters or fewer'));
        } else if (newPassword.length < 6) {
            details.push(buildValidationDetail('newPassword', 'New password must be at least 6 characters'));
        }

        return {
            currentPassword,
            newPassword,
            details,
        };
    }

    return {
        validateOptionalEmail,
        validateRequiredEmail,
        validateTokenOnlyPayload,
        validateSocialAuthExchangePayload,
        validateNativeSocialAuthStartPayload,
        validateNativeSocialAuthAttemptToken,
        validatePasswordResetConfirmPayload,
        validateAuthPayload,
        validateRenamePayload,
        validatePasswordUpdatePayload,
    };
}

const defaultValidation = createAuthValidation({
    normalizeEmail,
    normalizeInputString,
    readRawString,
    isValidEmail,
});

module.exports = {
    normalizeInputString,
    normalizeEmail,
    readRawString,
    isValidEmail,
    createAuthValidation,
    ...defaultValidation,
};
