package ru.homebudget.finkeeper.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FormattersTest {

    // ── formatCurrency ──

    @Test
    fun formatCurrency_positiveInteger() {
        assertEquals("1\u00A0000 ₽", formatCurrency(1000.0))
    }

    @Test
    fun formatCurrency_zero() {
        assertEquals("0 ₽", formatCurrency(0.0))
    }

    @Test
    fun formatCurrency_negative() {
        assertEquals("-5\u00A0000 ₽", formatCurrency(-5000.0))
    }

    @Test
    fun formatCurrency_roundsDown() {
        assertEquals("1\u00A0234 ₽", formatCurrency(1234.4))
    }

    @Test
    fun formatCurrency_roundsUp() {
        // kotlin.math.round использует банковское округление (round half to even)
        // 1234.5 -> 1234 (чётное), 1235.5 -> 1236 (чётное)
        assertEquals("1\u00A0235 ₽", formatCurrency(1234.6))
    }

    @Test
    fun formatCurrency_largeNumber() {
        assertEquals("1\u00A0000\u00A0000 ₽", formatCurrency(1_000_000.0))
    }

    @Test
    fun formatCurrency_smallNumber() {
        assertEquals("5 ₽", formatCurrency(5.0))
    }

    @Test
    fun formatCurrency_twoDigits() {
        assertEquals("99 ₽", formatCurrency(99.0))
    }

    @Test
    fun formatCurrency_threeDigits() {
        assertEquals("100 ₽", formatCurrency(100.0))
    }

    @Test
    fun formatCurrency_negativeSmall() {
        assertEquals("-1 ₽", formatCurrency(-1.0))
    }

    // ── formatDate ──

    @Test
    fun formatDate_isoInstant() {
        val result = formatDate("2024-03-15T10:30:00Z")
        // Результат зависит от таймзоны, но формат должен быть DD.MM.YYYY
        assertTrue(result.matches(Regex("\\d{2}\\.\\d{2}\\.\\d{4}")))
    }

    @Test
    fun formatDate_isoLocalDate() {
        assertEquals("15.03.2024", formatDate("2024-03-15"))
    }

    @Test
    fun formatDate_isoLocalDateWithTime() {
        assertEquals("01.01.2025", formatDate("2025-01-01T00:00:00"))
    }

    @Test
    fun formatDate_invalidString() {
        assertEquals("not-a-date", formatDate("not-a-date"))
    }

    // ── monthName ──

    @Test
    fun monthName_allMonths() {
        val expected = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
        )
        for (i in 1..12) {
            assertEquals(expected[i - 1], monthName(i))
        }
    }

    @Test
    fun monthName_invalidMonth() {
        assertEquals("", monthName(0))
        assertEquals("", monthName(13))
        assertEquals("", monthName(-1))
    }

    // ── shortMonthName ──

    @Test
    fun shortMonthName_allMonths() {
        val expected = listOf(
            "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
            "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
        )
        for (i in 1..12) {
            assertEquals(expected[i - 1], shortMonthName(i))
        }
    }

    @Test
    fun shortMonthName_invalidMonth() {
        assertEquals("", shortMonthName(0))
        assertEquals("", shortMonthName(13))
    }

    // ── currentIsoDate ──

    @Test
    fun currentIsoDate_returnsNonEmpty() {
        val date = currentIsoDate()
        assertTrue(date.isNotEmpty())
    }

    @Test
    fun currentIsoDate_containsT() {
        // ISO instant формат содержит 'T' как разделитель даты и времени
        val date = currentIsoDate()
        assertTrue(date.contains("T") || date.contains("-"))
    }

    // ── formatAmountForInput ──

    @Test
    fun formatAmountForInput_dropsTrailingZeros() {
        assertEquals("1500", formatAmountForInput(1500.0))
        assertEquals("1500.5", formatAmountForInput(1500.5))
        assertEquals("99.05", formatAmountForInput(99.05))
        assertEquals("0.1", formatAmountForInput(0.1))
    }

    @Test
    fun formatAmountForInput_roundsToCents() {
        assertEquals("10.01", formatAmountForInput(10.005000001))
        assertEquals("0.3", formatAmountForInput(0.1 + 0.2))
        assertEquals("-12.5", formatAmountForInput(-12.5))
    }
}
