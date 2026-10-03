package ru.homebudget.finkeeper.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals

class ListOrderTest {
    @Test
    fun wholeListReordered_takesNewOrder() {
        assertEquals(listOf(3L, 1L, 2L), mergeReorderedSubset(listOf(1L, 2L, 3L), listOf(3L, 1L, 2L)))
    }

    @Test
    fun subsetReordered_keepsOthersInPlace() {
        // Переставили B и D (например, только категории с расходами месяца): A и C не двигаются
        assertEquals(listOf(1L, 4L, 3L, 2L), mergeReorderedSubset(listOf(1L, 2L, 3L, 4L), listOf(4L, 2L)))
    }

    @Test
    fun unknownIds_goToTheEnd() {
        assertEquals(listOf(2L, 1L, 9L), mergeReorderedSubset(listOf(1L, 2L), listOf(2L, 9L, 1L)))
    }
}
