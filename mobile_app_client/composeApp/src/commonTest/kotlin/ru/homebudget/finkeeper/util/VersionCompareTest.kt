package ru.homebudget.finkeeper.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionCompareTest {

    @Test
    fun newerPatch() {
        assertTrue(isNewerVersion("2.2.1", "2.2.0"))
    }

    @Test
    fun newerMinor() {
        assertTrue(isNewerVersion("2.3.0", "v2.2.0"))
    }

    @Test
    fun newerMajor() {
        assertTrue(isNewerVersion("3.0.0", "2.9.9"))
    }

    @Test
    fun equalIsNotNewer() {
        assertFalse(isNewerVersion("2.2.0", "v2.2.0"))
    }

    @Test
    fun olderIsNotNewer() {
        assertFalse(isNewerVersion("2.1.5", "2.2.0"))
    }

    @Test
    fun vPrefixOnBothSides() {
        assertTrue(isNewerVersion("v2.2.1", "v2.2.0"))
    }

    @Test
    fun differentComponentCount() {
        assertTrue(isNewerVersion("2.2", "2.1.9"))
        assertFalse(isNewerVersion("2.2", "2.2.0"))
    }

    @Test
    fun garbageReturnsFalse() {
        assertFalse(isNewerVersion("", "2.2.0"))
        assertFalse(isNewerVersion("abc", "2.2.0"))
    }
}
