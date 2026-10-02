package ru.homebudget.finkeeper.ui.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import ru.homebudget.finkeeper.data.remote.ApiException
import ru.homebudget.finkeeper.ui.Strings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ViewModelErrorsTest {
    @Test
    fun actionError_showsFallbackInsteadOfExceptionText() {
        assertEquals(Strings.ERROR_REORDERING, ApiException(500, "Internal server error").toActionError(Strings.ERROR_REORDERING))
        assertEquals(Strings.OPERATION_FAILED, IllegalStateException("UNIQUE constraint failed").toActionError())
    }

    @Test
    fun actionError_offlineSaysRetryLater() {
        assertEquals(Strings.OFFLINE_RETRY_LATER, IOException("Unable to resolve host").toActionError(Strings.ERROR_REORDERING))
    }

    @Test
    fun actionError_rethrowsCancellation() {
        assertFailsWith<CancellationException> { CancellationException("cancel").toActionError() }
    }

    @Test
    fun localLoad_clearsOnlyLoadingError() {
        assertNull(Strings.LOADING_ERROR.withoutLocalLoadError())
        assertEquals(Strings.SERVER_REFRESH_FAILED, Strings.SERVER_REFRESH_FAILED.withoutLocalLoadError())
        assertNull(null.withoutLocalLoadError())
    }

    @Test
    fun screenError_actionErrorWinsOverLoadError() {
        val state = MonthViewState(loadError = Strings.SERVER_REFRESH_FAILED, actionError = Strings.ERROR_ADDING_INCOME)
        assertEquals(Strings.ERROR_ADDING_INCOME, state.error)
        assertEquals(Strings.SERVER_REFRESH_FAILED, state.copy(actionError = null).error)
        assertNull(SavingsState().error)
        assertEquals(Strings.LOADING_ERROR, CategoriesState(loadError = Strings.LOADING_ERROR).error)
    }
}
