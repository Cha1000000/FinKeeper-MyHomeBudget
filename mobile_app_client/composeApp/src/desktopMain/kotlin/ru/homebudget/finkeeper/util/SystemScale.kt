package ru.homebudget.finkeeper.util

import java.awt.GraphicsEnvironment

/**
 * Best-effort определение системного scale на desktop.
 *
 * Порядок (от наиболее «независимого» сигнала к производному):
 *  1. env GDK_SCALE (+ GDK_DPI_SCALE), QT_SCALE_FACTOR — Linux X11/KDE/GNOME;
 *  2. system property sun.java2d.uiScale — если задан явно;
 *  3. AWT defaultTransform.scaleX — корректно на Windows/macOS.
 *
 * Возвращает первый валидный (>0) результат, иначе null.
 */
actual fun detectSystemUiScale(): Float? {
    // 1. Linux env-переменные
    parseScale(System.getenv("GDK_SCALE"))?.let { gdk ->
        val dpi = parseScale(System.getenv("GDK_DPI_SCALE")) ?: 1f
        val combined = gdk * dpi
        if (combined > 0f) return combined
    }
    parseScale(System.getenv("QT_SCALE_FACTOR"))?.let { if (it > 0f) return it }

    // 2. Явно заданный sun.java2d.uiScale (может быть "2" или "2.0")
    parseScale(System.getProperty("sun.java2d.uiScale"))?.let { if (it > 0f) return it }

    // 3. AWT transform главного экрана (Windows/macOS)
    runCatching {
        if (!GraphicsEnvironment.isHeadless()) {
            val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
            val scaleX = device.defaultConfiguration.defaultTransform.scaleX
            if (scaleX > 0.0) return scaleX.toFloat()
        }
    }

    return null
}

/** Парсит число из строки вида "2", "2.0", "1.5" или "2x" (на всякий случай). */
private fun parseScale(raw: String?): Float? {
    val value = raw?.trim()?.removeSuffix("x")?.trim() ?: return null
    return value.toFloatOrNull()
}
