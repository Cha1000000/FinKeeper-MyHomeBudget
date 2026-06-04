package ru.homebudget.finkeeper.ui

/**
 * Единый источник правды для масштаба интерфейса (desktop).
 *
 * Масштаб — это множитель, который накладывается поверх системного density:
 * итоговый density = systemDensity * uiScale. 1.0 = 100% (без изменений).
 *
 * Значение `null` в настройках означает режим «Авто» — следуем системному scale.
 */
object UiScale {
    const val MIN = 0.8f
    const val MAX = 2.0f
    const val DEFAULT = 1.0f

    /** Дискретные шаги для ручного выбора: 80, 90, 100, 125, 150, 175, 200%. */
    val steps = listOf(0.8f, 0.9f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    /** Ближайший допустимый шаг к произвольному значению (для авто-детекта). */
    fun nearestStep(value: Float): Float {
        val clamped = value.coerceIn(MIN, MAX)
        return steps.minBy { kotlin.math.abs(it - clamped) }
    }
}
