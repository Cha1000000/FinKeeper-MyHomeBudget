package ru.homebudget.finkeeper.util

import kotlinx.datetime.Clock
import kotlinx.datetime.*

fun formatCurrency(amount: Double): String {
    val rounded = kotlin.math.round(amount).toLong()
    val formatted = buildString {
        val str = kotlin.math.abs(rounded).toString()
        var count = 0
        for (i in str.lastIndex downTo 0) {
            if (count > 0 && count % 3 == 0) insert(0, '\u00A0')
            insert(0, str[i])
            count++
        }
        if (rounded < 0) insert(0, '-')
    }
    return "$formatted ₽"
}

fun formatDate(isoDate: String): String {
    return try {
        val instant = Instant.parse(isoDate)
        val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        "${localDate.dayOfMonth.toString().padStart(2, '0')}.${localDate.monthNumber.toString().padStart(2, '0')}.${localDate.year}"
    } catch (_: Exception) {
        try {
            val localDate = LocalDate.parse(isoDate.substringBefore('T'))
            "${localDate.dayOfMonth.toString().padStart(2, '0')}.${localDate.monthNumber.toString().padStart(2, '0')}.${localDate.year}"
        } catch (_: Exception) {
            isoDate
        }
    }
}

fun currentIsoDate(): String {
    val now = Clock.System.now()
    return now.toString()
}

fun monthName(month: Int): String {
    return when (month) {
        1 -> "Январь"
        2 -> "Февраль"
        3 -> "Март"
        4 -> "Апрель"
        5 -> "Май"
        6 -> "Июнь"
        7 -> "Июль"
        8 -> "Август"
        9 -> "Сентябрь"
        10 -> "Октябрь"
        11 -> "Ноябрь"
        12 -> "Декабрь"
        else -> ""
    }
}

fun shortMonthName(month: Int): String {
    return when (month) {
        1 -> "Янв"
        2 -> "Фев"
        3 -> "Мар"
        4 -> "Апр"
        5 -> "Май"
        6 -> "Июн"
        7 -> "Июл"
        8 -> "Авг"
        9 -> "Сен"
        10 -> "Окт"
        11 -> "Ноя"
        12 -> "Дек"
        else -> ""
    }
}
