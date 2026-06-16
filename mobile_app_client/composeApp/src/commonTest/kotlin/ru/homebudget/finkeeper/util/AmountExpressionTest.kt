package ru.homebudget.finkeeper.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AmountExpressionTest {

    @Test
    fun plainNumberPassesThrough() {
        assertEquals(523.0, evalAmount("523"))
    }

    @Test
    fun sumWithEqualsPrefix() {
        assertEquals(1902.0, evalAmount("=523+1275+104"))
    }

    @Test
    fun sumWithoutEqualsPrefix() {
        assertEquals(1868.0, evalAmount("846+1036-14"))
    }

    @Test
    fun operatorPrecedence() {
        assertEquals(7.0, evalAmount("1+2*3"))
    }

    @Test
    fun parentheses() {
        assertEquals(9.0, evalAmount("(1+2)*3"))
    }

    @Test
    fun division() {
        assertEquals(2.5, evalAmount("10/4"))
    }

    @Test
    fun commaAsDecimalSeparator() {
        assertEquals(4.0, evalAmount("1,5+2,5"))
    }

    @Test
    fun spacesAreIgnored() {
        assertEquals(1002.0, evalAmount("1 000 + 2"))
    }

    @Test
    fun roundsToTwoDecimals() {
        assertEquals(0.3, evalAmount("0.1+0.2"))
    }

    @Test
    fun unaryMinus() {
        assertEquals(-5.0, evalAmount("-5"))
        assertEquals(95.0, evalAmount("100+-5"))
    }

    @Test
    fun divisionByZeroIsNull() {
        assertNull(evalAmount("5/0"))
    }

    @Test
    fun trailingOperatorIsNull() {
        assertNull(evalAmount("5++"))
        assertNull(evalAmount("5+"))
    }

    @Test
    fun emptyIsNull() {
        assertNull(evalAmount(""))
        assertNull(evalAmount("="))
        assertNull(evalAmount("   "))
    }

    @Test
    fun illegalCharsAreNull() {
        assertNull(evalAmount("5a"))
        assertNull(evalAmount("2^3"))
        assertNull(evalAmount("1.2.3"))
    }

    @Test
    fun isAmountExpressionDetection() {
        assertTrue(isAmountExpression("=523"))
        assertTrue(isAmountExpression("1+2"))
        assertTrue(isAmountExpression("10*4"))
        assertFalse(isAmountExpression("523"))
        assertFalse(isAmountExpression("-5"))
    }
}
