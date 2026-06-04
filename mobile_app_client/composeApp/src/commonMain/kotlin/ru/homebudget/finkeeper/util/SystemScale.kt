package ru.homebudget.finkeeper.util

/**
 * Пытается определить системный scale (заданный в ОС множитель UI).
 *
 * Возвращает желаемый множитель (например 2.0 для 200%) или `null`, если
 * определить не удалось. Используется только на desktop как дефолт режима «Авто».
 *
 * Ограничения: на чистом Wayland Java/AWT не видит системный scale и вернёт
 * 1.0/null — там нужен ручной выбор. См. PLAN_desktop_ui_scaling.md.
 */
expect fun detectSystemUiScale(): Float?
