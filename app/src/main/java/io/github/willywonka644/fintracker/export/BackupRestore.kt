package io.github.willywonka644.fintracker.export

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.SqlAccountRepository
import io.github.willywonka644.fintracker.SqlBookingRepository
import io.github.willywonka644.fintracker.SqlRecurringRuleRepository
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.FintrackerDatabase

class SqlBackupStorage(db: FintrackerDatabase) : BackupStorage {
    private val accountsRepository = SqlAccountRepository(db)
    private val bookingsRepository = SqlBookingRepository(db)
    private val categoriesRepository = SqlCategoryRepository(db)
    private val recurringRulesRepository = SqlRecurringRuleRepository(db)

    override fun readAccountsJson(): String? = accountsRepository.getRawAccountsJson()
    override fun readBookingsJson(): String? = bookingsRepository.getRawBookingsJson()
    override fun readCategoriesJson(): String? = categoriesRepository.getRawCategoriesJson()
    override fun readRecurringRulesJson(): String? = recurringRulesRepository.getRawRulesJson()
    override fun writeAccountsJson(json: String?): Boolean = accountsRepository.replaceAccountsJson(json)
    override fun writeBookingsJson(json: String?): Boolean = bookingsRepository.replaceBookingsJson(json)
    override fun writeCategoriesJson(json: String?): Boolean = categoriesRepository.replaceCategoriesJson(json)
    override fun writeRecurringRulesJson(json: String?): Boolean = recurringRulesRepository.replaceRulesJson(json)
    override fun serializeAccounts(accounts: List<Account>): String =
        accountsRepository.serializeAccounts(accounts)
    override fun serializeBookings(bookings: List<Booking>): String =
        bookingsRepository.serializeBookings(bookings)
    override fun serializeCategories(categories: List<Category>): String =
        categoriesRepository.serializeCategories(categories)
    override fun serializeRecurringRules(rules: List<RecurringRule>): String =
        recurringRulesRepository.serializeRules(rules)
}

fun RestoreService.restoreWithDb(db: FintrackerDatabase, backup: BackupData): RestoreResult =
    restore(backup, SqlBackupStorage(db))
