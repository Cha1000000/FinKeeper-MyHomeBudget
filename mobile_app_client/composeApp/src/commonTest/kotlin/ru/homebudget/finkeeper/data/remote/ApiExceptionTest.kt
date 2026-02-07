package ru.homebudget.finkeeper.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ApiExceptionTest {

    @Test
    fun apiException_properties() {
        val ex = ApiException(statusCode = 404, message = "Not found")
        assertEquals(404, ex.statusCode)
        assertEquals("Not found", ex.message)
    }

    @Test
    fun apiException_isException() {
        val ex = ApiException(statusCode = 500, message = "Server error")
        assertIs<Exception>(ex)
    }

    @Test
    fun apiException_401() {
        val ex = ApiException(statusCode = 401, message = "Unauthorized")
        assertEquals(401, ex.statusCode)
        assertEquals("Unauthorized", ex.message)
    }

    @Test
    fun apiException_messageInherited() {
        val ex: Exception = ApiException(statusCode = 403, message = "Forbidden")
        assertEquals("Forbidden", ex.message)
    }

    @Test
    fun apiException_httpStatusMessage() {
        val ex = ApiException(statusCode = 422, message = "HTTP 422: Unprocessable Entity")
        assertTrue(ex.message.contains("422"))
    }
}
