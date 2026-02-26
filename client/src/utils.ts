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
