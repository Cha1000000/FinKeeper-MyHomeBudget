package ru.homebudget.finkeeper.data.remote

import kotlinx.coroutines.CancellationException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ru.homebudget.finkeeper.data.model.*
import ru.homebudget.finkeeper.data.network.GATEWAY_FAILURE_STATUS_CODES
import ru.homebudget.finkeeper.data.network.ServerLinkState
import ru.homebudget.finkeeper.data.network.isConnectivityFailure
import ru.homebudget.finkeeper.ui.Strings

/** Таймауты HTTP; отдельный тип — чтобы тесты могли проверить «молчащий» сервер за секунды. */
data class ApiTimeouts(
    val requestMillis: Long = 30_000,
    val connectMillis: Long = 10_000,
    // Без явного значения CIO (десктоп) ждёт молчащий сервер до requestTimeout, OkHttp — 10 с
    val socketMillis: Long = 15_000,
)

class ApiClient(
    private val tokenStorage: TokenStorage,
    private val serverLinkState: ServerLinkState = ServerLinkState(),
    private val timeouts: ApiTimeouts = ApiTimeouts(),
) {
    private val refreshMutex = Mutex()
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    private val client =
        HttpClient {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                level = LogLevel.NONE
            }
            install(HttpTimeout) {
                requestTimeoutMillis = timeouts.requestMillis
                connectTimeoutMillis = timeouts.connectMillis
                socketTimeoutMillis = timeouts.socketMillis
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
                val token = tokenStorage.accessToken
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }
        }.also { httpClient ->
            httpClient.plugin(HttpSend).intercept { request ->
                val originalCall = executeTracked(request)
                val statusCode = originalCall.response.status.value
                if (!shouldAttemptRefresh(statusCode) || !shouldHandleAuthRetry(request)) {
                    return@intercept originalCall
                }

                val refreshedToken = recoverAuthToken(extractBearerToken(request)) ?: return@intercept originalCall
                request.attributes.put(AUTH_RETRY_MARKER, true)
                request.headers.remove(HttpHeaders.Authorization)
                request.headers.append(HttpHeaders.Authorization, "Bearer $refreshedToken")
                executeTracked(request)
            }
        }

    /** Ответ сервера — он доступен (кроме 502/503/504 от прокси); сетевая неудача — нет. */
    private suspend fun Sender.executeTracked(request: HttpRequestBuilder): HttpClientCall {
        val requestId = serverLinkState.beginRequest()
        return try {
            execute(request).also { call ->
                val status = call.response.status.value
                serverLinkState.reportResult(requestId, reachable = status !in GATEWAY_FAILURE_STATUS_CODES)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isConnectivityFailure()) serverLinkState.reportResult(requestId, reachable = false)
            throw e
        }
    }

    private val baseUrl: String get() = tokenStorage.serverUrl + "/api"

    private fun HttpRequestBuilder.applyOperationId(operationId: String?) {
        if (!operationId.isNullOrBlank()) {
            header("X-Operation-Id", operationId)
        }
    }

    private fun HttpRequestBuilder.applyRefreshTransportHeader() {
        header("X-Refresh-Transport", "body")
    }

    private fun persistAuthData(authData: AuthData) {
        tokenStorage.accessToken = authData.accessToken
        tokenStorage.refreshToken = authData.refreshToken
        tokenStorage.userId = authData.user.id.toLong()
    }

    private fun shouldAttemptRefresh(statusCode: Int): Boolean = statusCode == 401 || statusCode == 403
    
    private fun isAuthEndpoint(url: String): Boolean {
        return (
            url.contains("/auth/login") ||
                url.contains("/auth/register") ||
                url.contains("/auth/refresh") ||
                url.contains("/auth/oauth/exchange") ||
                url.contains("/auth/oauth/") ||
                url.contains("/auth/password-recovery/") ||
                url.contains("/auth/email-verification/") ||
                url.contains("/auth/logout")
        )
    }

    private fun shouldHandleAuthRetry(request: HttpRequestBuilder): Boolean {
        if (request.attributes.contains(AUTH_RETRY_MARKER)) {
            return false
        }
        if (tokenStorage.refreshToken.isNullOrBlank()) {
            return false
        }
        return !isAuthEndpoint(request.url.buildString())
    }

    private fun extractBearerToken(request: HttpRequestBuilder): String? {
        return request.headers[HttpHeaders.Authorization]
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.ifBlank { null }
    }

    private suspend fun recoverAuthToken(failedToken: String?): String? {
        val currentToken = tokenStorage.accessToken
        if (!currentToken.isNullOrBlank() && currentToken != failedToken) {
            return currentToken
        }

        return refreshMutex.withLock {
            val latestToken = tokenStorage.accessToken
            if (!latestToken.isNullOrBlank() && latestToken != failedToken) {
                return@withLock latestToken
            }

            try {
                refreshAuth().accessToken
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Сервер недоступен — отдаём сетевую ошибку, а не исходный 401: иначе вызывающий
                // примет её за ответ сервера и не прекратит запросы к «зависшему» серверу
                if (e.isConnectivityFailure()) throw e
                println("[AUTH] token refresh failed: ${e::class.simpleName}: ${e.message}")
                // Сессию сбрасываем только по 401/403 на refresh, не по прочим ошибкам
                if (e is ApiException && (e.statusCode == 401 || e.statusCode == 403)) {
                    tokenStorage.clear(AuthSessionEvent.SessionExpired)
                }
                null
            }
        }
    }

    // ── Auth ──

    suspend fun login(
        username: String,
        password: String,
    ): AuthData {
        val response =
            client.post("$baseUrl/auth/login") {
                applyRefreshTransportHeader()
                setBody(LoginRequest(username, password))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun register(
        username: String,
        password: String,
    ): AuthData {
        val response =
            client.post("$baseUrl/auth/register") {
                applyRefreshTransportHeader()
                setBody(LoginRequest(username, password))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun refreshAuth(): AuthData {
        val refreshToken =
            tokenStorage.refreshToken
                ?: throw ApiException(
                    statusCode = 401,
                    message = "Refresh token required",
                )
        val response =
            client.post("$baseUrl/auth/refresh") {
                applyRefreshTransportHeader()
                setBody(RefreshTokenRequest(refreshToken))
            }
        checkResponse(response)
        val authData = response.body<AuthData>()
        persistAuthData(authData)
        return authData
    }

    suspend fun logout() {
        val refreshToken = tokenStorage.refreshToken
        val response =
            client.post("$baseUrl/auth/logout") {
                if (refreshToken != null) {
                    applyRefreshTransportHeader()
                    setBody(RefreshTokenRequest(refreshToken))
                }
            }
        checkResponse(response)
    }

    suspend fun getMe(): User {
        val response = client.get("$baseUrl/auth/me")
        checkResponse(response)
        return response.body()
    }

    suspend fun requestPasswordRecovery(email: String): PasswordRecoveryRequestResponse {
        val response =
            client.post("$baseUrl/auth/password-recovery/request") {
                setBody(PasswordRecoveryRequest(email))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun confirmPasswordRecovery(
        token: String,
        newPassword: String,
    ): PasswordRecoveryConfirmResponse {
        val response =
            client.post("$baseUrl/auth/password-recovery/confirm") {
                setBody(PasswordRecoveryConfirmRequest(token, newPassword))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun confirmEmailVerification(token: String): User {
        val response =
            client.post("$baseUrl/auth/email-verification/confirm") {
                setBody(EmailVerificationConfirmRequest(token))
            }
        checkResponse(response)
        val payload = response.body<EmailVerificationResponse>()
        return payload.user
    }

    suspend fun getSocialProviders(): List<SocialProvider> {
        val response = client.get("$baseUrl/auth/social/providers")
        checkResponse(response)
        return response.body<SocialProvidersResponse>().providers
    }

    suspend fun startNativeSocialAuth(
        provider: String,
        clientType: String,
    ): NativeSocialAuthStartResponse {
        val response =
            client.post("$baseUrl/auth/oauth/$provider/native/start") {
                setBody(NativeSocialAuthStartRequest(clientType = clientType))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun getNativeSocialAuthStatus(attemptToken: String): NativeSocialAuthStatusResponse {
        val response = client.get("$baseUrl/auth/oauth/native/$attemptToken")
        checkResponse(response)
        return response.body()
    }

    suspend fun exchangeSocialAuthCode(code: String): AuthData {
        val response =
            client.post("$baseUrl/auth/oauth/exchange") {
                applyRefreshTransportHeader()
                setBody(SocialAuthExchangeRequest(code))
            }
        checkResponse(response)
        val authData = response.body<AuthData>()
        persistAuthData(authData)
        return authData
    }

    // ── User ──

    suspend fun updateUsername(newUsername: String) {
        val response =
            client.put("$baseUrl/user/rename") {
                setBody(UpdateUsernameRequest(newUsername))
            }
        checkResponse(response)
    }

    suspend fun updatePassword(
        currentPassword: String,
        newPassword: String,
    ) {
        val response =
            client.put("$baseUrl/user/password") {
                setBody(UpdatePasswordRequest(currentPassword, newPassword))
            }
        checkResponse(response)
    }

    suspend fun updateUserEmail(email: String): UserEmailUpdateResponse {
        val response =
            client.put("$baseUrl/user/email") {
                setBody(UpdateUserEmailRequest(email))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun clearUserEmail(): UserEmailUpdateResponse {
        val response =
            client.put("$baseUrl/user/email") {
                setBody(UpdateUserEmailRequest(""))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun requestEmailVerification(): UserEmailVerificationRequestResponse {
        val response = client.post("$baseUrl/user/email/verification/request")
        checkResponse(response)
        return response.body()
    }

    suspend fun getBackupEntries(): BackupListResponse {
        val response = client.get("$baseUrl/user/backups")
        checkResponse(response)
        return response.body()
    }

    suspend fun createManualBackup() {
        val response = client.post("$baseUrl/user/backup")
        checkResponse(response)
    }

    suspend fun restoreBackup(
        backupId: Int,
        confirmationText: String,
    ): RestoreBackupResponse {
        val response =
            client.post("$baseUrl/user/restore") {
                setBody(RestoreBackupRequest(backupId, confirmationText))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun getDeletedRecords(
        entityType: String? = null,
        since: String? = null,
    ): List<DeletedRecord> {
        val response =
            client.get("$baseUrl/deleted_records") {
                url {
                    entityType?.takeIf { it.isNotBlank() }?.let { parameters.append("entity_type", it) }
                    since?.takeIf { it.isNotBlank() }?.let { parameters.append("since", it) }
                }
            }
        checkResponse(response)
        return response.body()
    }

    // ── Categories ──

    // Нужны и неактивные категории (в т.ч. скрытая «Пополнение копилки»): без них
    // синхронизация выбрасывает их расходы, и локальные суммы расходятся с сервером.
    // Из UI неактивные отсекаются фильтром isActive.
    suspend fun getCategories(): List<Category> {
        val response = client.get("$baseUrl/categories?include_inactive=1")
        checkResponse(response)
        return response.body()
    }

    suspend fun createCategory(
        name: String,
        isFixed: Int? = null,
        fixedAmount: Double? = null,
        autoDay: Int? = null,
        requireConfirm: Int? = null,
        operationId: String? = null,
    ): Category {
        val response =
            client.post("$baseUrl/categories") {
                applyOperationId(operationId)
                setBody(CreateCategoryRequest(name, isFixed, fixedAmount, autoDay, requireConfirm))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateCategory(
        id: Int,
        request: UpdateCategoryRequest,
        operationId: String? = null,
    ): Category {
        val response =
            client.put("$baseUrl/categories/$id") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun reorderCategories(
        ids: List<Int>,
        operationId: String? = null,
    ) {
        val response =
            client.put("$baseUrl/categories/reorder") {
                applyOperationId(operationId)
                setBody(ReorderCategoriesRequest(ids))
            }
        checkResponse(response)
    }

    suspend fun deleteCategory(
        id: Int,
        operationId: String? = null,
    ) {
        val response =
            client.delete("$baseUrl/categories/$id") {
                applyOperationId(operationId)
            }
        checkResponse(response)
    }

    // ── Income Sources ──

    suspend fun getIncomeSources(): List<IncomeSource> {
        // С неактивными (как категории): удалённый источник остаётся локально, и его доходы
        // сохраняют название. Из UI неактивные отсекаются фильтром isActive.
        val response = client.get("$baseUrl/income_sources?include_inactive=1")
        checkResponse(response)
        return response.body()
    }

    suspend fun createIncomeSource(
        name: String,
        isFixed: Int? = null,
        fixedAmount: Double? = null,
        autoDay: Int? = null,
        requireConfirm: Int? = null,
        operationId: String? = null,
    ): IncomeSource {
        val response =
            client.post("$baseUrl/income_sources") {
                applyOperationId(operationId)
                setBody(CreateIncomeSourceRequest(name, isFixed, fixedAmount, autoDay, requireConfirm))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateIncomeSource(
        id: Int,
        request: UpdateIncomeSourceRequest,
        operationId: String? = null,
    ): IncomeSource {
        val response =
            client.put("$baseUrl/income_sources/$id") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun reorderIncomeSources(
        ids: List<Int>,
        operationId: String? = null,
    ) {
        val response =
            client.put("$baseUrl/income_sources/reorder") {
                applyOperationId(operationId)
                setBody(ReorderIncomeSourcesRequest(ids))
            }
        checkResponse(response)
    }

    suspend fun deleteIncomeSource(
        id: Int,
        operationId: String? = null,
    ) {
        val response =
            client.delete("$baseUrl/income_sources/$id") {
                applyOperationId(operationId)
            }
        checkResponse(response)
    }

    // ── Months ──

    suspend fun ensureMonth(
        year: Int,
        month: Int,
    ): Month {
        val response =
            client.post("$baseUrl/months/ensure") {
                setBody(EnsureMonthRequest(year, month))
            }
        checkResponse(response)
        return response.body()
    }

    // ── Incomes ──

    suspend fun getIncomes(monthId: Int): List<Income> {
        val response = client.get("$baseUrl/months/$monthId/incomes")
        checkResponse(response)
        return response.body()
    }

    suspend fun addIncome(
        request: AddIncomeRequest,
        operationId: String? = null,
    ): Income {
        val response =
            client.post("$baseUrl/incomes") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateIncome(
        id: Int,
        amount: Double,
        operationId: String? = null,
    ): Income {
        val response =
            client.put("$baseUrl/incomes/$id") {
                applyOperationId(operationId)
                setBody(UpdateAmountRequest(amount))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun deleteIncome(
        id: Int,
        operationId: String? = null,
    ) {
        val response =
            client.delete("$baseUrl/incomes/$id") {
                applyOperationId(operationId)
            }
        checkResponse(response)
    }

    // ── Expenses ──

    suspend fun getExpenses(monthId: Int): List<Expense> {
        val response = client.get("$baseUrl/months/$monthId/expenses")
        checkResponse(response)
        return response.body()
    }

    suspend fun addExpense(
        request: AddExpenseRequest,
        operationId: String? = null,
    ): Expense {
        val response =
            client.post("$baseUrl/expenses") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateExpense(
        id: Int,
        amount: Double,
        comment: String? = null,
        operationId: String? = null,
    ): Expense {
        val response =
            client.put("$baseUrl/expenses/$id") {
                applyOperationId(operationId)
                setBody(UpdateExpenseRequest(amount, comment))
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun deleteExpense(
        id: Int,
        operationId: String? = null,
    ) {
        val response =
            client.delete("$baseUrl/expenses/$id") {
                applyOperationId(operationId)
            }
        checkResponse(response)
    }

    // ── Budgets ──

    suspend fun getBudgets(monthId: Int): List<Budget> {
        val response = client.get("$baseUrl/months/$monthId/budgets")
        checkResponse(response)
        return response.body()
    }

    suspend fun setBudget(
        request: SetBudgetRequest,
        operationId: String? = null,
    ): Budget {
        val response =
            client.post("$baseUrl/budgets") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateBudget(
        id: Int,
        request: SetBudgetRequest,
        operationId: String? = null,
    ): Budget {
        val response =
            client.post("$baseUrl/budgets") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    // ── Savings ──

    suspend fun getSavingsGoals(): List<SavingsGoal> {
        val response = client.get("$baseUrl/savings_goals")
        checkResponse(response)
        return response.body()
    }

    suspend fun createSavingsGoal(
        request: CreateSavingsGoalRequest,
        operationId: String? = null,
    ): SavingsGoal {
        val response =
            client.post("$baseUrl/savings_goals") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun updateSavingsGoal(
        id: Int,
        request: UpdateSavingsGoalRequest,
        operationId: String? = null,
    ): SavingsGoal {
        val response =
            client.put("$baseUrl/savings_goals/$id") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun deleteSavingsGoal(
        id: Int,
        operationId: String? = null,
    ) {
        val response =
            client.delete("$baseUrl/savings_goals/$id") {
                applyOperationId(operationId)
            }
        checkResponse(response)
    }

    suspend fun addSavingsTransaction(
        request: AddSavingsTransactionRequest,
        operationId: String? = null,
    ): SavingsTransaction {
        val response =
            client.post("$baseUrl/savings_transactions") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun getSavingsTransactions(goalId: Int): List<SavingsTransaction> {
        val response = client.get("$baseUrl/savings_transactions/$goalId")
        checkResponse(response)
        return response.body()
    }

    suspend fun updateSavingsTransaction(
        id: Int,
        request: AddSavingsTransactionRequest,
        operationId: String? = null,
    ): SavingsTransaction {
        val response =
            client.put("$baseUrl/savings_transactions/$id") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
        return response.body()
    }

    suspend fun deleteSavingsTransaction(
        id: Int,
        operationId: String? = null,
    ) {
        val response =
            client.delete("$baseUrl/savings_transactions/$id") {
                applyOperationId(operationId)
            }
        checkResponse(response)
    }

    // ── План-слой (запланированные регулярные платежи) ──

    /** Сырое состояние план-слоя месяца (материализованные + исключения) для локального вычисления плана */
    suspend fun getPlannedState(monthId: Int): PlannedStateResponse {
        val response = client.get("$baseUrl/months/$monthId/planned-state")
        checkResponse(response)
        return response.body()
    }

    /** Полное состояние исключения (skip/override); сервер вернёт 409, если платёж уже материализован */
    suspend fun putPlannedOverride(
        monthId: Int,
        templateType: String,
        templateId: Int,
        request: PlannedOverrideRequest,
        operationId: String? = null,
    ) {
        val response =
            client.put("$baseUrl/months/$monthId/planned/$templateType/$templateId") {
                applyOperationId(operationId)
                setBody(request)
            }
        checkResponse(response)
    }

    /** Полный сброс исключения к шаблону */
    suspend fun deletePlannedOverride(
        monthId: Int,
        templateType: String,
        templateId: Int,
        operationId: String? = null,
    ) {
        val response =
            client.delete("$baseUrl/months/$monthId/planned/$templateType/$templateId/override") {
                applyOperationId(operationId)
            }
        checkResponse(response)
    }

    /** «Оплачено/Получено»: материализует плановый платёж в реальную запись */
    suspend fun confirmPlanned(
        monthId: Int,
        templateType: String,
        templateId: Int,
        amount: Double? = null,
        operationId: String? = null,
    ) {
        val response =
            client.post("$baseUrl/months/$monthId/planned/$templateType/$templateId/confirm") {
                applyOperationId(operationId)
                setBody(ConfirmPlannedRequest(amount = amount))
            }
        checkResponse(response)
    }

    // ── Analytics ──

    suspend fun getMonthSummary(monthId: Int): MonthSummary {
        val response = client.get("$baseUrl/months/$monthId/summary")
        checkResponse(response)
        return response.body()
    }

    suspend fun getTrend(): List<TrendItem> {
        val response = client.get("$baseUrl/analytics/trend")
        checkResponse(response)
        return response.body()
    }

    suspend fun getCumulativeBalance(year: Int, month: Int): CumulativeBalanceResponse {
        val response = client.get("$baseUrl/analytics/cumulative-balance?year=$year&month=$month")
        checkResponse(response)
        return response.body()
    }

    // ── Helpers ──

    private suspend fun checkResponse(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            val errorResponse =
                try {
                    response.body<ErrorResponse>()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            throw ApiException(
                statusCode = response.status.value,
                message =
                    errorResponse?.error
                        ?: Strings.HTTP_ERROR
                            .replace(
                                "%1\$d",
                                response.status.value.toString(),
                            ).replace("%2\$s", response.status.description),
                code = errorResponse?.code,
                originalResponse = errorResponse?.originalResponse,
            )
        }
    }

    companion object {
        private val AUTH_RETRY_MARKER = AttributeKey<Boolean>("auth_retry_marker")
    }
}

class ApiException(
    val statusCode: Int,
    override val message: String,
    /** Машиночитаемый код ошибки сервера (`code` в теле ответа), если он есть. */
    val code: String? = null,
    /** Для [CODE_IDEMPOTENCY_KEY_REUSED]: ответ сервера на первое применение ключа операции. */
    val originalResponse: JsonElement? = null,
) : Exception(message) {
    companion object {
        /** Ключ операции уже применён к запросу с другим телом (повтор после потерянного ответа). */
        const val CODE_IDEMPOTENCY_KEY_REUSED = "IDEMPOTENCY_KEY_REUSED"

        /** Нельзя удалить копилку с ненулевым балансом. */
        const val CODE_SAVINGS_GOAL_NOT_EMPTY = "SAVINGS_GOAL_NOT_EMPTY"

        /** 404 на PUT: нет самой изменяемой записи (а не связанной с ней), её удалили на другом устройстве. */
        const val CODE_RECORD_NOT_FOUND = "RECORD_NOT_FOUND"
    }
}
