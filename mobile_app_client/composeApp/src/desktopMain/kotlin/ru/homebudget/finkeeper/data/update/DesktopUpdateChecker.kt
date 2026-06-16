package ru.homebudget.finkeeper.data.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.homebudget.finkeeper.BuildConfig
import ru.homebudget.finkeeper.data.remote.SocialAuthLauncher
import ru.homebudget.finkeeper.util.isNewerVersion

/**
 * Манифест последней версии desktop-сборки, лежит на сайте рядом с downloads.html
 * (Vite-статика из lending/public → корень сайта): https://finkeeper24.ru/latest.json
 */
@Serializable
private data class UpdateManifest(
    val version: String,
    val url: String? = null,
    val notes: String? = null,
)

/**
 * Desktop-реализация проверки обновлений: тянет JSON-манифест с сайта и сравнивает версию
 * с [BuildConfig.APP_VERSION]. «Обновить» открывает страницу загрузок в браузере.
 */
class DesktopUpdateChecker(
    private val socialAuthLauncher: SocialAuthLauncher,
) : AppUpdateChecker {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
        }
    }

    private var lastManifest: UpdateManifest? = null

    override suspend fun check(): AppUpdateStatus {
        return try {
            // Читаем как текст и парсим вручную — не зависим от Content-Type статики сайта.
            val text = client.get(MANIFEST_URL).bodyAsText()
            val manifest = json.decodeFromString<UpdateManifest>(text)
            lastManifest = manifest
            if (isNewerVersion(manifest.version, BuildConfig.APP_VERSION)) {
                AppUpdateStatus.Available(
                    versionLabel = manifest.version,
                    versionToken = manifest.version,
                )
            } else {
                AppUpdateStatus.UpToDate
            }
        } catch (t: Throwable) {
            AppUpdateStatus.Unknown
        }
    }

    override fun startUpdate() {
        socialAuthLauncher.open(lastManifest?.url ?: DOWNLOADS_PAGE)
    }

    private companion object {
        const val MANIFEST_URL = "https://finkeeper24.ru/latest.json"
        const val DOWNLOADS_PAGE = "https://finkeeper24.ru/downloads.html"
    }
}
