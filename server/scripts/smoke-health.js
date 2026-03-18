const fs = require('fs');
const path = require('path');
const { spawn, spawnSync } = require('child_process');

const serverPath = path.resolve(__dirname, '..', 'index.js');
const dbSetupPath = path.resolve(__dirname, '..', 'db_setup.js');
const port = process.env.SMOKE_PORT || '3210';
const dbPath = path.resolve(__dirname, '..', '.smoke-health.sqlite');
const baseUrl = `http://127.0.0.1:${port}`;

async function waitForHealth(timeoutMs = 10000) {
    const startedAt = Date.now();

    while (Date.now() - startedAt < timeoutMs) {
        try {
            const response = await fetch(`${baseUrl}/api/health`);
            if (response.ok) {
                return response;
            }
        } catch (_) {
        }

        await new Promise(resolve => setTimeout(resolve, 250));
    }

    throw new Error('Health endpoint did not become ready in time');
}

async function main() {
    const setupResult = spawnSync(process.execPath, [dbSetupPath], {
        env: {
            ...process.env,
            DB_PATH: dbPath,
        },
        stdio: 'inherit',
    });

    if (setupResult.status !== 0) {
        throw new Error(`db_setup.js failed with code ${setupResult.status}`);
    }

    const child = spawn(process.execPath, [serverPath], {
        env: {
            ...process.env,
            NODE_ENV: 'development',
            PORT: port,
            DB_PATH: dbPath,
            JWT_SECRET: process.env.JWT_SECRET || 'smoke-test-secret',
            ALLOWED_ORIGINS: process.env.ALLOWED_ORIGINS || 'http://localhost:5174',
        },
        stdio: ['ignore', 'pipe', 'pipe'],
    });

    child.stdout.on('data', chunk => {
        process.stdout.write(chunk);
    });

    child.stderr.on('data', chunk => {
        process.stderr.write(chunk);
    });

    try {
        try {
            const response = await waitForHealth();
            const body = await response.json();

            if (body.status !== 'ok' || body.database !== 'ok' || body.schema !== 'ok') {
                throw new Error(`Unexpected health status: ${JSON.stringify(body)}`);
            }

            console.log('Health smoke test passed');
        } finally {
            child.kill('SIGTERM');
        }

        await new Promise((resolve, reject) => {
            child.on('exit', code => {
                if (code === 0 || code === null) {
                    resolve();
                    return;
                }
                reject(new Error(`Server exited with code ${code}`));
            });
        });
    } finally {
        fs.rmSync(dbPath, { force: true });
    }
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
