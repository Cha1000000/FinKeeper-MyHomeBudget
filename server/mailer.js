const logger = require('./logger');

const DEFAULT_DEV_APP_BASE_URL = 'http://localhost:5174';
const isProduction = process.env.NODE_ENV === 'production';

let transportPromise = null;

function parseBooleanEnv(name, fallbackValue = false) {
    const rawValue = process.env[name]?.trim().toLowerCase();
    if (!rawValue) {
        return fallbackValue;
    }

    return rawValue === '1' || rawValue === 'true' || rawValue === 'yes' || rawValue === 'on';
}

function parsePort(value) {
    if (!value) {
        return null;
    }

    const parsedValue = Number(value);
    if (!Number.isInteger(parsedValue) || parsedValue <= 0 || parsedValue > 65535) {
        return null;
    }

    return parsedValue;
}

function trimTrailingSlashes(value) {
    return value.replace(/\/+$/, '');
}

function getMailConfig() {
    const host = process.env.SMTP_HOST?.trim() || '';
    const port = parsePort(process.env.SMTP_PORT?.trim());
    const user = process.env.SMTP_USER?.trim() || '';
    const pass = process.env.SMTP_PASS ?? '';
    const from = process.env.MAIL_FROM?.trim() || user;
    const appBaseUrl = trimTrailingSlashes((process.env.APP_BASE_URL?.trim() || (isProduction ? '' : DEFAULT_DEV_APP_BASE_URL)));
    const secure = parseBooleanEnv('SMTP_SECURE', port === 465);
    const requireTls = parseBooleanEnv('SMTP_REQUIRE_TLS', false);
    const hasAuth = Boolean(user || pass);
    const hasValidAuth = hasAuth ? Boolean(user && pass) : true;
    const configured = Boolean(host && port && from && appBaseUrl && hasValidAuth);

    return {
        host,
        port,
        user,
        pass,
        from,
        appBaseUrl,
        secure,
        requireTls,
        hasAuth,
        configured,
    };
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function formatDateTime(value) {
    return new Intl.DateTimeFormat('ru-RU', {
        dateStyle: 'medium',
        timeStyle: 'short',
    }).format(new Date(value));
}

function buildAppUrl(pathname, query) {
    const { appBaseUrl } = getMailConfig();
    if (!appBaseUrl) {
        return '';
    }

    const url = new URL(pathname, `${appBaseUrl}/`);
    Object.entries(query).forEach(([key, rawValue]) => {
        if (rawValue !== undefined && rawValue !== null && rawValue !== '') {
            url.searchParams.set(key, String(rawValue));
        }
    });
    return url.toString();
}

async function createTransport() {
    const config = getMailConfig();
    if (!config.configured) {
        return null;
    }

    let nodemailer;
    try {
        nodemailer = require('nodemailer');
    } catch (error) {
        logger.error('mailer_dependency_missing', { error });
        const missingDependencyError = new Error('nodemailer dependency is not installed');
        missingDependencyError.code = 'mailer_dependency_missing';
        throw missingDependencyError;
    }

    const transporter = nodemailer.createTransport({
        host: config.host,
        port: config.port,
        secure: config.secure,
        requireTLS: config.requireTls,
        auth: config.hasAuth ? {
            user: config.user,
            pass: config.pass,
        } : undefined,
    });

    await transporter.verify();

    logger.info('mailer_ready', {
        host: config.host,
        port: config.port,
        secure: config.secure,
        requireTls: config.requireTls,
        from: config.from,
        appBaseUrl: config.appBaseUrl,
    });

    return transporter;
}

async function getTransport() {
    if (!transportPromise) {
        transportPromise = createTransport();
    }

    try {
        return await transportPromise;
    } catch (error) {
        transportPromise = null;
        throw error;
    }
}

async function sendMail({ to, subject, text, html }) {
    const config = getMailConfig();
    if (!config.configured) {
        return {
            delivered: false,
            reason: 'mailer_not_configured',
        };
    }

    const transporter = await getTransport();
    const info = await transporter.sendMail({
        from: config.from,
        to,
        subject,
        text,
        html,
    });

    logger.info('mailer_message_sent', {
        to,
        subject,
        messageId: info.messageId,
        accepted: info.accepted,
        rejected: info.rejected,
        response: info.response,
    });

    return {
        delivered: true,
        messageId: info.messageId,
    };
}

async function sendPasswordRecoveryEmail({ email, username, token, expiresAt }) {
    const recoveryUrl = buildAppUrl('/password-recovery', { token });
    const expiresAtLabel = formatDateTime(expiresAt);
    const safeUsername = escapeHtml(username || 'пользователь');
    const safeRecoveryUrl = escapeHtml(recoveryUrl);
    const safeExpiresAt = escapeHtml(expiresAtLabel);

    return sendMail({
        to: email,
        subject: 'FinKeeper — восстановление доступа',
        text: [
            `Здравствуйте, ${username || 'пользователь'}!`,
            '',
            'Мы получили запрос на восстановление доступа к аккаунту FinKeeper.',
            `Откройте ссылку, чтобы задать новый пароль: ${recoveryUrl}`,
            `Ссылка действует до ${expiresAtLabel}.`,
            '',
            'Если вы не запрашивали восстановление, просто проигнорируйте это письмо.',
        ].join('\n'),
        html: `
            <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #0f172a;">
                <h2 style="margin-bottom: 16px;">Восстановление доступа FinKeeper</h2>
                <p>Здравствуйте, ${safeUsername}!</p>
                <p>Мы получили запрос на восстановление доступа к вашему аккаунту.</p>
                <p>
                    <a href="${safeRecoveryUrl}" style="display: inline-block; padding: 12px 18px; border-radius: 12px; background: #059669; color: #ffffff; text-decoration: none; font-weight: 600;">
                        Задать новый пароль
                    </a>
                </p>
                <p>Или откройте ссылку вручную:</p>
                <p><a href="${safeRecoveryUrl}">${safeRecoveryUrl}</a></p>
                <p>Ссылка действует до <strong>${safeExpiresAt}</strong>.</p>
                <p>Если вы не запрашивали восстановление, просто проигнорируйте это письмо.</p>
            </div>
        `,
    });
}

async function sendEmailVerificationEmail({ email, username, token, expiresAt }) {
    const verificationUrl = buildAppUrl('/verify-email', { token });
    const expiresAtLabel = formatDateTime(expiresAt);
    const safeUsername = escapeHtml(username || 'пользователь');
    const safeVerificationUrl = escapeHtml(verificationUrl);
    const safeExpiresAt = escapeHtml(expiresAtLabel);

    return sendMail({
        to: email,
        subject: 'FinKeeper — подтверждение email',
        text: [
            `Здравствуйте, ${username || 'пользователь'}!`,
            '',
            'Подтвердите email для аккаунта FinKeeper, чтобы включить самостоятельное восстановление доступа.',
            `Откройте ссылку для подтверждения: ${verificationUrl}`,
            `Ссылка действует до ${expiresAtLabel}.`,
            '',
            'Если вы не меняли email, просто проигнорируйте это письмо.',
        ].join('\n'),
        html: `
            <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #0f172a;">
                <h2 style="margin-bottom: 16px;">Подтверждение email в FinKeeper</h2>
                <p>Здравствуйте, ${safeUsername}!</p>
                <p>Подтвердите email, чтобы включить самостоятельное восстановление доступа к аккаунту.</p>
                <p>
                    <a href="${safeVerificationUrl}" style="display: inline-block; padding: 12px 18px; border-radius: 12px; background: #059669; color: #ffffff; text-decoration: none; font-weight: 600;">
                        Подтвердить email
                    </a>
                </p>
                <p>Или откройте ссылку вручную:</p>
                <p><a href="${safeVerificationUrl}">${safeVerificationUrl}</a></p>
                <p>Ссылка действует до <strong>${safeExpiresAt}</strong>.</p>
                <p>Если вы не меняли email, просто проигнорируйте это письмо.</p>
            </div>
        `,
    });
}

module.exports = {
    getMailConfig,
    sendPasswordRecoveryEmail,
    sendEmailVerificationEmail,
};
