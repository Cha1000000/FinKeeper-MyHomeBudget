import { describe, it, expect, beforeEach } from 'vitest';
import { evalAmount, isAmountExpression, readLocalStorageJson, readSavedMonthDate } from '../utils';

describe('utils', () => {
    beforeEach(() => {
        window.localStorage.clear();
    });

    describe('evalAmount & isAmountExpression', () => {
        it('evaluates plain numbers and arithmetic expressions with comma/dot and optional leading =', () => {
            expect(evalAmount('1500')).toBe(1500);
            expect(evalAmount('= 150,5 + 349.5 * 2')).toBe(849.5);
            expect(evalAmount('(100 + 50) / 3')).toBe(50);
            expect(isAmountExpression('1500')).toBe(false);
            expect(isAmountExpression('150+340')).toBe(true);
            expect(isAmountExpression('=500')).toBe(true);
        });

        it('returns null for division by zero, empty input, or malformed expressions', () => {
            expect(evalAmount('')).toBeNull();
            expect(evalAmount('100 / 0')).toBeNull();
            expect(evalAmount('100 + (50')).toBeNull();
            expect(evalAmount('12..5')).toBeNull();
            expect(evalAmount('abc')).toBeNull();
        });
    });

    describe('readLocalStorageJson & readSavedMonthDate', () => {
        it('returns fallback instead of throwing when localStorage contains corrupted JSON', () => {
            window.localStorage.setItem('broken_key', '{not-valid-json');
            expect(readLocalStorageJson('broken_key', { ok: true })).toEqual({ ok: true });
        });

        it('parses valid saved year and month (1..12) and falls back to current date for invalid values', () => {
            window.localStorage.setItem('month_key', JSON.stringify({ year: 2026, month: 10 }));
            const parsed = readSavedMonthDate('month_key');
            expect(parsed.getFullYear()).toBe(2026);
            expect(parsed.getMonth()).toBe(9);

            window.localStorage.setItem('month_key', JSON.stringify({ year: 2026, month: 99 }));
            const now = new Date();
            const fallbackDate = readSavedMonthDate('month_key');
            expect(fallbackDate.getFullYear()).toBe(now.getFullYear());
            expect(fallbackDate.getMonth()).toBe(now.getMonth());
        });
    });
});
