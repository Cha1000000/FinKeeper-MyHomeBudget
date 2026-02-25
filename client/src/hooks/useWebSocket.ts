import { useEffect, useRef, useCallback } from 'react';

export interface DataChangedEvent {
    type: 'data_changed';
    entity: string;
    action: string;
}

/**
 * Custom hook that connects to the server WebSocket and dispatches
 * 'ws:data_changed' CustomEvents on window when data mutations arrive.
 * Pages subscribe via window.addEventListener('ws:data_changed', handler).
 *
 * Handles reconnection with exponential backoff.
 */
export function useWebSocket() {
    const wsRef = useRef<WebSocket | null>(null);
    const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
    const reconnectDelay = useRef(1000);
    const connectRef = useRef<(() => void) | null>(null);

    const connect = useCallback(() => {
        const token = localStorage.getItem('token');
        if (!token) return;

        // Determine WebSocket URL from current location
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}?token=${token}`;

        const ws = new WebSocket(wsUrl);
        wsRef.current = ws;

        ws.onopen = () => {
            reconnectDelay.current = 1000; // Reset backoff on successful connect
        };

        ws.onmessage = (event) => {
            try {
                const data: DataChangedEvent = JSON.parse(event.data);
                if (data.type === 'data_changed') {
                    window.dispatchEvent(
                        new CustomEvent('ws:data_changed', { detail: data })
                    );
                }
            } catch {
                // Ignore non-JSON messages (pings, etc.)
            }
        };

        ws.onclose = (event) => {
            wsRef.current = null;
            // Don't reconnect if closed intentionally (4001/4003 = auth errors)
            if (event.code === 4001 || event.code === 4003) return;

            // Reconnect with exponential backoff (max 30s)
            reconnectTimer.current = setTimeout(() => {
                reconnectDelay.current = Math.min(reconnectDelay.current * 2, 30000);
                connectRef.current?.();
            }, reconnectDelay.current);
        };

        ws.onerror = () => {
            ws.close();
        };
    }, []);

    useEffect(() => {
        connectRef.current = connect;
    });

    useEffect(() => {
        connect();

        return () => {
            if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
            if (wsRef.current) {
                wsRef.current.onclose = null; // Prevent reconnect on unmount
                wsRef.current.close();
            }
        };
    }, [connect]);
}

/**
 * Hook to listen for specific entity changes via WebSocket.
 * @param entities - Array of entity types to listen for (e.g. ['income', 'expense']), or null for all
 * @param callback - Function to call when a matching event arrives
 */
export function useDataChanged(
    entities: string[] | null,
    callback: (event: DataChangedEvent) => void
) {
    const callbackRef = useRef(callback);

    useEffect(() => {
        callbackRef.current = callback;
    });

    useEffect(() => {
        const handler = (e: Event) => {
            const detail = (e as CustomEvent<DataChangedEvent>).detail;
            if (!entities || entities.includes(detail.entity) || detail.entity === 'all') {
                callbackRef.current(detail);
            }
        };

        window.addEventListener('ws:data_changed', handler);
        return () => window.removeEventListener('ws:data_changed', handler);
    }, [entities]);
}
