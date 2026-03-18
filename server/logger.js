function serializeValue(value) {
    if (value instanceof Error) {
        return {
            name: value.name,
            message: value.message,
            stack: value.stack,
        };
    }

    if (Array.isArray(value)) {
        return value.map(serializeValue);
    }

    if (value && typeof value === 'object') {
        return Object.fromEntries(
            Object.entries(value).map(([key, nestedValue]) => [key, serializeValue(nestedValue)]),
        );
    }

    return value;
}

function writeLog(level, event, meta = {}) {
    const entry = {
        timestamp: new Date().toISOString(),
        level,
        event,
        ...serializeValue(meta),
    };
    const line = JSON.stringify(entry);

    if (level === 'error') {
        console.error(line);
        return;
    }

    if (level === 'warn') {
        console.warn(line);
        return;
    }

    console.log(line);
}

module.exports = {
    info(event, meta) {
        writeLog('info', event, meta);
    },
    warn(event, meta) {
        writeLog('warn', event, meta);
    },
    error(event, meta) {
        writeLog('error', event, meta);
    },
};
