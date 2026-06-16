package ru.homebudget.finkeeper.util

/**
 * Сравнение версий приложения вида "2.2.0" / "v2.2.0".
 *
 * @return true, если [latest] строго новее [current].
 *         При неразборчивых строках — false (не показываем баннер на мусоре).
 */
fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = parseVersion(latest) ?: return false
    val currentParts = parseVersion(current) ?: return false

    val size = maxOf(latestParts.size, currentParts.size)
    for (i in 0 until size) {
        val l = latestParts.getOrElse(i) { 0 }
        val c = currentParts.getOrElse(i) { 0 }
        if (l != c) {
            return l > c
        }
    }
    return false
}

/**
 * Разбирает "v2.2.0" / "2.2" / "2.2.0" в список числовых компонентов.
 * Возвращает null, если ни одного числового компонента не нашлось.
 */
private fun parseVersion(raw: String): List<Int>? {
    val cleaned = raw.trim().removePrefix("v").removePrefix("V")
    if (cleaned.isEmpty()) return null

    val parts = cleaned.split('.').map { part ->
        // Берём ведущую числовую часть компонента (на случай "1-beta", "0+build").
        val digits = part.trimStart().takeWhile { it.isDigit() }
        digits.toIntOrNull() ?: return null
    }
    return parts.ifEmpty { null }
}
