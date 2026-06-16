package ru.homebudget.finkeeper.data.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.homebudget.finkeeper.data.remote.SocialAuthLauncher
import ru.rustore.sdk.appupdate.listener.InstallStateUpdateListener
import ru.rustore.sdk.appupdate.manager.RuStoreAppUpdateManager
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.AppUpdateInfo
import ru.rustore.sdk.appupdate.model.AppUpdateOptions
import ru.rustore.sdk.appupdate.model.AppUpdateType
import ru.rustore.sdk.appupdate.model.InstallStatus
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import kotlin.coroutines.resume

/**
 * Android-реализация проверки обновлений через RuStore In-app Update SDK.
 *
 * [check] узнаёт о наличии обновления нативно (`getAppUpdateInfo`), [startUpdate] запускает
 * FLEXIBLE-флоу (фоновая загрузка + установка). При недоступности SDK (RuStore не установлен,
 * сборка не из стора) — `Unknown`; при сбое запуска флоу — fallback на открытие страницы в RuStore.
 */
class RuStoreUpdateChecker(
    private val context: Context,
    private val socialAuthLauncher: SocialAuthLauncher,
) : AppUpdateChecker {

    private var manager: RuStoreAppUpdateManager? = null
    private var lastInfo: AppUpdateInfo? = null

    private val flexibleOptions: AppUpdateOptions =
        AppUpdateOptions.Builder().appUpdateType(AppUpdateType.FLEXIBLE).build()

    private val installStateListener = InstallStateUpdateListener { installState ->
        when (installState.installStatus) {
            InstallStatus.DOWNLOADED -> {
                // Обновление загружено — запускаем установку.
                runCatching { manager?.completeUpdate(flexibleOptions) }
            }
            InstallStatus.FAILED -> Log.e(TAG, "RuStore update download failed")
            else -> Unit
        }
    }

    override suspend fun check(): AppUpdateStatus = suspendCancellableCoroutine { cont ->
        try {
            val mgr = RuStoreAppUpdateManagerFactory.create(context).also { manager = it }
            mgr.getAppUpdateInfo()
                .addOnSuccessListener { info ->
                    lastInfo = info
                    if (!cont.isActive) return@addOnSuccessListener
                    if (info.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE) {
                        cont.resume(
                            AppUpdateStatus.Available(
                                versionLabel = info.availableVersionName.orEmpty(),
                                versionToken = info.availableVersionCode.toString(),
                            ),
                        )
                    } else {
                        cont.resume(AppUpdateStatus.UpToDate)
                    }
                }
                .addOnFailureListener { throwable ->
                    Log.w(TAG, "getAppUpdateInfo error: ${throwable.message}")
                    if (cont.isActive) cont.resume(AppUpdateStatus.Unknown)
                }
        } catch (t: Throwable) {
            Log.w(TAG, "RuStore update check failed: ${t.message}")
            if (cont.isActive) cont.resume(AppUpdateStatus.Unknown)
        }
    }

    override fun startUpdate() {
        val mgr = manager
        val info = lastInfo
        if (mgr == null || info == null) {
            openStorePage()
            return
        }
        try {
            mgr.registerListener(installStateListener)
            mgr.startUpdateFlow(info, flexibleOptions)
                .addOnFailureListener { throwable ->
                    Log.w(TAG, "startUpdateFlow error: ${throwable.message}")
                    openStorePage()
                }
        } catch (t: Throwable) {
            Log.w(TAG, "startUpdate failed: ${t.message}")
            openStorePage()
        }
    }

    private fun openStorePage() {
        socialAuthLauncher.open(RUSTORE_APP_PAGE)
    }

    private companion object {
        const val TAG = "RuStoreUpdateChecker"
        const val RUSTORE_APP_PAGE = "https://www.rustore.ru/catalog/app/ru.homebudget.finkeeper"
    }
}
