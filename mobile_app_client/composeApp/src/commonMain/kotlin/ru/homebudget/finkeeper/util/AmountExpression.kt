package ru.homebudget.finkeeper.util

import kotlin.math.round

private const val ALLOWED_CHARS = "0123456789.+-*/()"

/**
 * Вычисляет арифметическое выражение из поля суммы (по аналогии с формулой Excel).
 *
 * Поддержка: `+ - * / ( )`, десятичная точка или запятая, опциональный ведущий `=`,
 * пробелы (в т.ч. как разделители тысяч) игнорируются.
 * Обычное число (`523`) проходит как есть — обратная совместимость.
 *
 * @return результат, округлённый до 2 знаков, либо null при ошибке/недопустимых символах
 *         (вызывающий код сам проверяет, что результат > 0).
 */
fun evalAmount(input: String): Double? {
    var s = input.trim()
    if (s.startsWith("=")) s = s.substring(1)
    s = s.filterNot { it.isWhitespace() }.replace(',', '.')
    if (s.isEmpty()) return null
    if (s.any { it !in ALLOWED_CHARS }) return null

    return try {
        val parser = ExpressionParser(s)
        val result = parser.parseExpression()
        if (!parser.atEnd()) return null
        if (!result.isFinite()) return null
        round(result * 100.0) / 100.0
    } catch (_: Exception) {
        null
    }
}

/**
 * Похож ли ввод на выражение (есть оператор или ведущий `=`), а не на простое число.
 * Используется для показа live-превью результата.
 */
fun isAmountExpression(input: String): Boolean {
    val trimmed = input.trim()
    if (trimmed.startsWith("=")) return true
    // Игнорируем ведущий унарный минус, ищем оператор дальше.
    return trimmed.drop(1).any { it == '+' || it == '-' || it == '*' || it == '/' }
}

private class ExpressionParser(private val s: String) {
    private var pos = 0

    fun atEnd(): Boolean = pos >= s.length

    private fun peek(): Char? = if (pos < s.length) s[pos] else null

    fun parseExpression(): Double {
        var value = parseTerm()
        while (true) {
            when (peek()) {
                '+' -> { pos++; value += parseTerm() }
                '-' -> { pos++; value -= parseTerm() }
                else -> return value
            }
        }
    }

    private fun parseTerm(): Double {
        var value = parseFactor()
        while (true) {
            when (peek()) {
                '*' -> { pos++; value *= parseFactor() }
                '/' -> {
                    pos++
                    val divisor = parseFactor()
                    if (divisor == 0.0) throw ArithmeticException("division by zero")
                    value /= divisor
                }
                else -> return value
            }
        }
    }

    private fun parseFactor(): Double {
        return when (peek()) {
            '+' -> { pos++; parseFactor() }
            '-' -> { pos++; -parseFactor() }
            '(' -> {
                pos++
                val value = parseExpression()
                if (peek() != ')') throw IllegalStateException("missing ')'")
                pos++
                value
            }
            else -> parseNumber()
        }
    }

    private fun parseNumber(): Double {
        val start = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
        val numStr = s.substring(start, pos)
        return numStr.toDoubleOrNull() ?: throw IllegalStateException("bad number: '$numStr'")
    }
}
