package ru.homebudget.finkeeper.di

import org.koin.dsl.module
import ru.homebudget.finkeeper.data.local.dao.*
import ru.homebudget.finkeeper.data.remote.ApiClient
import ru.homebudget.finkeeper.data.remote.SecureTokenStorage
import ru.homebudget.finkeeper.data.remote.SocialAuthLauncher
import ru.homebudget.finkeeper.data.remote.TokenStorage
import ru.homebudget.finkeeper.data.repository.SyncManager
import ru.homebudget.finkeeper.data.repository.SyncStateStorage
import ru.homebudget.finkeeper.data.repository.SyncService
import ru.homebudget.finkeeper.data.repository.WebSocketService
import ru.homebudget.finkeeper.data.repository.budget.BudgetRepository
import ru.homebudget.finkeeper.data.repository.category.CategoryRepository
import ru.homebudget.finkeeper.data.repository.expense.ExpenseRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeRepository
import ru.homebudget.finkeeper.data.repository.income.IncomeSourceRepository
import ru.homebudget.finkeeper.data.repository.month.MonthRepository
import ru.homebudget.finkeeper.data.repository.planned.PlannedRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsGoalRepository
import ru.homebudget.finkeeper.data.repository.savings.SavingsTransactionRepository
import ru.homebudget.finkeeper.ui.viewmodel.*

/**
 * Основной модуль Koin DI
 * Содержит общие зависимости для всех платформ
 */
val appModule =
    module {
        // Remote data
        single<TokenStorage> { TokenStorage(secureTokenStorage = get<SecureTokenStorage>()) }
        single { ApiClient(get()) }
        single { SyncStateStorage(get()) }

        // DAOs (DatabaseProvider предоставляется в platform-specific модулях)
        single { UserDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { CategoryDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { IncomeSourceDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { MonthDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { IncomeDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { ExpenseDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { BudgetDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { SavingsGoalDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { SavingsTransactionDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { SyncQueueDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { PlannedOverrideDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }
        single { AutoCreatedDao(get<ru.homebudget.finkeeper.data.local.database.DatabaseProvider>().database) }

        // Repositories
        single { CategoryRepository(get(), get(), get()) }
        single { IncomeSourceRepository(get(), get(), get()) }
        single { IncomeRepository(get(), get(), get(), get(), get()) }
        single { ExpenseRepository(get(), get(), get(), get(), get()) }
        single { MonthRepository(get(), get(), get()) }
        single { BudgetRepository(get(), get(), get(), get(), get()) }
        single { SavingsGoalRepository(get(), get(), get()) }
        single { SavingsTransactionRepository(get(), get(), get(), get()) }
        single { PlannedRepository(get(), get(), get(), get(), get(), get(), get()) }

        // SyncManager должен быть создан после репозиториев
        single {
            SyncManager(
                get(), // syncQueueDao
                get(), // categoryDao
                get(), // incomeSourceDao
                get(), // incomeDao
                get(), // expenseDao
                get(), // budgetDao
                get(), // savingsGoalDao
                get(), // savingsTransactionDao
                get(), // monthDao
                get(), // apiClient
                get(), // categoryRepository
                get(), // incomeSourceRepository
                get(), // monthRepository
                get(), // incomeRepository
                get(), // expenseRepository
                get(), // budgetRepository
                get(), // savingsGoalRepository
                get(), // savingsTransactionRepository
                get(), // plannedRepository
                get(), // plannedOverrideDao
                get(), // syncStateStorage
                get(), // tokenStorage
            )
        }

        // ViewModels
        factory { AuthViewModel(get(), get(), get<SocialAuthLauncher>()) }
        factory { DashboardViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        factory { MonthViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        factory { CategoriesViewModel(get(), get(), get(), get(), get()) }
        factory { SavingsViewModel(get(), get(), get(), get(), get()) }
        factory { SettingsViewModel(get(), get(), get()) }

        // Сервис авто-синхронизации
        single { SyncService(get(), get(), get(), get()) }

        // WebSocket сервис для real-time обновлений
        single { WebSocketService(get(), get()) }
    }
