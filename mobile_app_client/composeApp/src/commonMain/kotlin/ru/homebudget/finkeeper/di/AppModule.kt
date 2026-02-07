package ru.homebudget.finkeeper.di

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.ui.viewmodel.*

val appModule = module {
    singleOf(::TokenStorage)
    singleOf(::ApiClient)

    viewModelOf(::AuthViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::MonthViewModel)
    viewModelOf(::CategoriesViewModel)
    viewModelOf(::SavingsViewModel)
    viewModelOf(::SettingsViewModel)
}
