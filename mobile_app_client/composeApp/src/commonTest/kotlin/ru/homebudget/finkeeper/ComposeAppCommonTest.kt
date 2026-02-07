package ru.homebudget.finkeeper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ComposeAppCommonTest {

    @Test
    fun appPackageExists() {
        // Базовая проверка что пакет доступен
        assertEquals("ru.homebudget.finkeeper", ComposeAppCommonTest::class.qualifiedName?.substringBeforeLast('.'))
    }

    @Test
    fun kotlinVersionIsCompatible() {
        // Проверяем что Kotlin runtime доступен
        val version = KotlinVersion.CURRENT
        assertNotNull(version)
        assertEquals(2, version.major)
    }
}