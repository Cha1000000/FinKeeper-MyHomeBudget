package ru.homebudget.finkeeper

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import ru.homebudget.finkeeper.di.appModule

class FinKeeperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@FinKeeperApp)
            modules(appModule)
        }
    }
}
