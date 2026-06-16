import React from 'react';

export const formatCurrency = (amount: number) => {
    const formatted = new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
    }).format(amount);

    const parts = formatted.split('₽');
    if (parts.length === 2 && React) {
        return React.createElement(
            'span',
            { className: 'inline-flex items-baseline whitespace-nowrap' },
            parts[0],
            React.createElement(
                'span',
                { className: 'text-[0.85em] font-medium opacity-60 ml-0.45' },
                '₽'
            )
        );
    }
    return formatted;
};

export const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('ru-RU');
}

/**
 * Вычисляет арифметическое выражение из поля суммы (по аналогии с формулой Excel).
 * Поддержка: + - * / ( ), десятичная точка/запятая, опциональный ведущий '=', пробелы игнорируются.
 * Обычное число проходит как есть. Возвращает результат (округлённый до 2 знаков) или null при ошибке.
 * Проверку "> 0" выполняет вызывающий код.
 */
export const evalAmount = (input: string): number | null => {
    let s = input.trim();
    if (s.startsWith('=')) s = s.slice(1);
    s = s.replace(/\s/g, '').replace(/,/g, '.');
    if (s.length === 0) return null;
    if (!/^[0-9.+\-*/()]+$/.test(s)) return null;

    try {
        const parser = new ExpressionParser(s);
        const result = parser.parseExpression();
        if (!parser.atEnd() || !isFinite(result)) return null;
        return Math.round(result * 100) / 100;
    } catch {
        return null;
    }
};

/** Похож ли ввод на выражение (есть оператор или ведущий '='), а не на простое число. */
export const isAmountExpression = (input: string): boolean => {
    const t = input.trim();
    if (t.startsWith('=')) return true;
    return /[+\-*/]/.test(t.slice(1)); // игнорируем ведущий унарный минус
};

class ExpressionParser {
    private pos = 0;
    private readonly s: string;

    constructor(s: string) {
        this.s = s;
    }

    atEnd(): boolean {
        return this.pos >= this.s.length;
    }

    private peek(): string | undefined {
        return this.pos < this.s.length ? this.s[this.pos] : undefined;
    }

    parseExpression(): number {
        let value = this.parseTerm();
        for (;;) {
            const c = this.peek();
            if (c === '+') { this.pos++; value += this.parseTerm(); }
            else if (c === '-') { this.pos++; value -= this.parseTerm(); }
            else return value;
        }
    }

    private parseTerm(): number {
        let value = this.parseFactor();
        for (;;) {
            const c = this.peek();
            if (c === '*') { this.pos++; value *= this.parseFactor(); }
            else if (c === '/') {
                this.pos++;
                const divisor = this.parseFactor();
                if (divisor === 0) throw new Error('division by zero');
                value /= divisor;
            }
            else return value;
        }
    }

    private parseFactor(): number {
        const c = this.peek();
        if (c === '+') { this.pos++; return this.parseFactor(); }
        if (c === '-') { this.pos++; return -this.parseFactor(); }
        if (c === '(') {
            this.pos++;
            const value = this.parseExpression();
            if (this.peek() !== ')') throw new Error("missing ')'");
            this.pos++;
            return value;
        }
        return this.parseNumber();
    }

    private parseNumber(): number {
        const start = this.pos;
        while (this.pos < this.s.length && /[0-9.]/.test(this.s[this.pos])) this.pos++;
        const numStr = this.s.slice(start, this.pos);
        if (numStr.length === 0 || (numStr.match(/\./g) || []).length > 1) {
            throw new Error(`bad number: '${numStr}'`);
        }
        const n = Number(numStr);
        if (isNaN(n)) throw new Error(`bad number: '${numStr}'`);
        return n;
    }
}
