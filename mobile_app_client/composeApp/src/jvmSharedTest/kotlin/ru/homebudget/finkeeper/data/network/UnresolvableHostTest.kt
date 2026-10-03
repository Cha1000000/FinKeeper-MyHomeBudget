package ru.homebudget.finkeeper.data.network

import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnresolvableHostTest {
    @Test
    fun unknownHost_isUnresolvable_evenWhenWrapped() {
        assertTrue(UnknownHostException("finkeeper.invalid").isTimeoutOrUnresolvable())
        assertTrue(IllegalStateException("wrapped", UnknownHostException("x")).isTimeoutOrUnresolvable())
    }

    @Test
    fun connectionReset_isNotUnresolvable() {
        assertFalse(java.net.SocketException("Connection reset").isTimeoutOrUnresolvable())
    }
}
