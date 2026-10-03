package ru.homebudget.finkeeper.data.repository

import ru.homebudget.finkeeper.data.repository.income.matchIncomeSourceByName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Чистые функции pull-синхронизации: курсор tombstone и сопоставление источника дохода по имени. */
class SyncPullHelpersTest {
    @Test
    fun cursor_withoutDeferred_movesToLatestFetched() {
        assertEquals("t3", nextDeletedRecordsCursor("t0", listOf("t2", "t3", "t1"), emptyList()))
    }

    @Test
    fun cursor_withoutFetched_staysAtSince() {
        assertEquals("t0", nextDeletedRecordsCursor("t0", emptyList(), emptyList()))
        assertNull(nextDeletedRecordsCursor(null, emptyList(), emptyList()))
    }

    @Test
    fun cursor_withDeferred_stopsBeforeFirstDeferred() {
        // Отложенный t2 должен прийти снова: курсор не переходит через него
        assertEquals("t1", nextDeletedRecordsCursor("t0", listOf("t1", "t2", "t3"), listOf("t3", "t2")))
    }

    @Test
    fun cursor_whenFirstFetchedIsDeferred_staysAtSince() {
        assertEquals("t0", nextDeletedRecordsCursor("t0", listOf("t1", "t2"), listOf("t1")))
    }

    private fun match(sources: List<String>, name: String) = matchIncomeSourceByName(sources, name) { it }

    @Test
    fun match_prefersExactName() {
        assertEquals("Зарплата", match(listOf("зарплата", "Зарплата", "Зарплата жены"), "Зарплата"))
    }

    @Test
    fun match_ignoresCaseWhenUnambiguous() {
        assertEquals("зарплата", match(listOf("зарплата", "Аванс"), "Зарплата"))
    }

    @Test
    fun match_bySubstringOnlyWhenSingleCandidate() {
        assertEquals("Фриланс", match(listOf("Фриланс", "Аванс"), "Фриланс (проект)"))
        assertNull(match(listOf("Зарплата", "Зарплата жены"), "Зарп"), "неоднозначное совпадение не угадываем")
    }

    @Test
    fun match_nothingSimilar_returnsNull() {
        assertNull(match(listOf("Аванс"), "Подарок"))
    }
}
