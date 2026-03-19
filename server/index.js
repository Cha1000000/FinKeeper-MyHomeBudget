const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const path = require('path');
const http = require('http');
const crypto = require('crypto');
const jwt = require('jsonwebtoken');
const { WebSocketServer } = require('ws');
const logger = require('./logger');

// Database initialization and schema sync
// the connection.js ensures the schema is up to date immediately when imported
const { db, nowIso } = require('./db/connection');
const { getMailConfig } = require('./mailer');

const isProduction = process.env.NODE_ENV === 'production';
const PORT = process.env.PORT ? Number(process.env.PORT.trim()) : 3002;
const JWT_SECRET = process.env.JWT_SECRET?.trim();
const jsonBodyLimit = process.env.JSON_BODY_LIMIT?.trim() || '1mb';

const DEFAULT_DEV_ALLOWED_ORIGINS = [
    'http://localhost:5174',
    'http://127.0.0.1:5174',
    'http://localhost:4173',
    'http://127.0.0.1:4173',
];
const allowedOriginsEnv = process.env.ALLOWED_ORIGINS?.trim();
const allowedOrigins = (allowedOriginsEnv
    ? allowedOriginsEnv.split(',').map(origin => origin.trim()).filter(Boolean)
    : isProduction
        ? []
        : DEFAULT_DEV_ALLOWED_ORIGINS
);

if (!JWT_SECRET) {
    throw new Error('JWT_SECRET environment variable is required');
}
if (isProduction && allowedOrigins.length === 0) {
    throw new Error('ALLOWED_ORIGINS environment variable is required in production');
}

const app = express();

app.disable('x-powered-by');
app.use(helmet({
    contentSecurityPolicy: {
        directives: {
            upgradeInsecureRequests: null,
            reportUri: '/api/csp-report',
        },
    },
    crossOriginResourcePolicy: false,
}));

app.use(cors({
    origin(origin, callback) {
        if (!origin || allowedOrigins.includes(origin)) {
            callback(null, true);
            return;
        }
        callback(new Error('Origin not allowed by CORS'));
    },
    credentials: true,
}));

app.use(express.json({ limit: jsonBodyLimit }));
app.use((req, res, next) => {
    req.requestId = crypto.randomUUID();
    const startedAt = Date.now();

    res.on('finish', () => {
        if (!req.path.startsWith('/api/')) return;
        logger.info('http_request', {
            requestId: req.requestId,
            method: req.method,
            path: req.originalUrl,
            statusCode: res.statusCode,
            durationMs: Date.now() - startedAt,
            ip: req.ip,
            userId: req.user?.id ?? null,
        });
    });
    next();
});

// CSP report endpoint
app.post('/api/csp-report', express.json({ type: 'application/csp-report' }), (req, res) => {
    logger.warn('csp_violation', {
        report: req.body?.['csp-report'] || req.body,
        ip: req.ip,
        userAgent: req.get('user-agent'),
    });
    res.status(204).send();
});

// Health check endpoint
const requiredHealthTables = [
    'users', 'categories', 'months', 'incomes', 'expenses', 'budgets',
    'savings_goals', 'savings_transactions', 'income_sources', 'user_backups',
    'auth_refresh_sessions', 'auth_email_verification_tokens', 'auth_password_reset_tokens',
    'auth_identities', 'auth_login_exchange_codes', 'auth_social_login_attempts',
    'idempotency_keys', 'deleted_records',
];

function hasTable(tableName) {
    const row = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?").get(tableName);
    return !!row;
}

app.get('/api/health', (req, res) => {
    try {
        const databaseProbe = db.prepare('SELECT 1 AS ok').get();
        const missingTables = requiredHealthTables.filter(tableName => !hasTable(tableName));
        const mailConfig = getMailConfig();
        const healthStatus = databaseProbe?.ok === 1 && missingTables.length === 0 && (!isProduction || mailConfig.configured) ? 'ok' : 'error';
        const payload = {
            status: healthStatus,
            timestamp: nowIso(),
            uptime: Number(process.uptime().toFixed(3)),
            env: process.env.NODE_ENV || 'development',
            database: databaseProbe?.ok === 1 ? 'ok' : 'error',
            schema: missingTables.length === 0 ? 'ok' : 'error',
            recoveryDelivery: mailConfig.configured ? 'ok' : (isProduction ? 'error' : 'dev_only'),
            appBaseUrl: mailConfig.appBaseUrl ? 'ok' : (isProduction ? 'error' : 'dev_default'),
            missingTables,
        };

        if (payload.status !== 'ok') {
            logger.warn('health_check_degraded', payload);
            return res.status(503).json(payload);
        }

        logger.info('health_check_ok', {
            requestId: req.requestId,
            status: payload.status,
        });
        return res.json(payload);
    } catch (error) {
        logger.error('health_check_failed', { requestId: req.requestId, error });
        return res.status(503).json({
            status: 'error',
            timestamp: nowIso(),
            uptime: Number(process.uptime().toFixed(3)),
            env: process.env.NODE_ENV || 'development',
            database: 'error',
        });
    }
});

// Broadcast function exposed globally for routers (via app.get('broadcast'))
const server = http.createServer(app);
const wss = new WebSocketServer({ server });

function broadcastChange(userId, entity, action) {
    const message = JSON.stringify({ type: 'data_changed', entity, action });
    wss.clients.forEach(ws => {
        if (ws.readyState === 1 && ws.userId === userId) {
            ws.send(message);
        }
    });
}
app.set('broadcast', broadcastChange);

// Mount Modular Routes
const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/user');
const dataRoutes = require('./routes/data');

app.use('/api/auth', authRoutes);
app.use('/api/user', userRoutes);
app.use('/api', dataRoutes);

// Static Web Client
const clientDistPath = path.join(__dirname, '..', 'client', 'dist');
app.use(express.static(clientDistPath));

// Web SPA Catch-All
app.get('/{*splat}', (req, res) => {
    if (req.path.startsWith('/api/')) {
        return res.status(404).json({ error: 'API endpoint not found' });
    }
    res.sendFile(path.join(clientDistPath, 'index.html'));
});

// Final Error Boundary
const { sendError } = require('./middleware/authenticate');
app.use((err, req, res, next) => {
    logger.error('unhandled_request_error', {
        requestId: req.requestId,
        method: req.method,
        path: req.originalUrl,
        userId: req.user?.id ?? null,
        error: err,
    });
    if (res.headersSent) return next(err);
    return sendError(res, 500, 'Internal server error', 'internal_error');
});

// --- WebSocket logic ---
wss.on('connection', (ws, req) => {
    const url = new URL(req.url, `http://${req.headers.host}`);
    const token = url.searchParams.get('token');

    if (!token) {
        ws.close(4001, 'Authentication required');
        return;
    }

    try {
        const decoded = jwt.verify(token, JWT_SECRET);
        if (decoded.type !== 'access') {
            ws.close(4003, 'Invalid token type');
            return;
        }
        ws.userId = decoded.id;
        ws.isAlive = true;
        logger.info('websocket_connected', { userId: decoded.id });
    } catch (err) {
        ws.close(4003, 'Invalid token');
        return;
    }

    ws.on('pong', () => { ws.isAlive = true; });
    ws.on('close', () => {
        logger.info('websocket_disconnected', { userId: ws.userId ?? null });
    });
});

setInterval(() => {
    wss.clients.forEach(ws => {
        if (!ws.isAlive) return ws.terminate();
        ws.isAlive = false;
        ws.ping();
    });
}, 30000);

// --- Boot Server ---
server.listen(PORT, () => {
    const mailConfig = getMailConfig();
    const dbPath = process.env.DB_PATH?.trim()
        ? path.resolve(process.env.DB_PATH.trim())
        : path.resolve(__dirname, 'database.sqlite');
        
    logger.info('server_started', {
        port: PORT,
        url: `http://localhost:${PORT}`,
        dbPath,
        env: process.env.NODE_ENV || 'development',
        mailConfigured: mailConfig.configured,
        mailFrom: mailConfig.from || null,
        appBaseUrl: mailConfig.appBaseUrl || null,
    });
});

// --- Graceful Shutdown ---
function gracefulShutdown(signal) {
    logger.info('shutdown_initiated', { signal });

    server.close(() => {
        logger.info('http_server_closed');
        wss.close(() => {
            logger.info('websocket_server_closed');
            db.close();
            logger.info('database_closed');
            process.exit(0);
        });
    });

    setTimeout(() => {
        logger.error('shutdown_forced', { signal, reason: 'timeout' });
        process.exit(1);
    }, 10000).unref();
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));
