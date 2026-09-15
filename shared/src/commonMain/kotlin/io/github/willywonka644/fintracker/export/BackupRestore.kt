package io.github.willywonka644.fintracker.export

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.serialization.AppJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class BackupData(
    val schemaVersion: Int,
    val accounts: List<Account>,
    val bookings: List<Booking>,
    val categories: List<Category> = emptyList(),
    val recurringRules: List<RecurringRule> = emptyList()
)

enum class BackupParseError {
    INVALID_JSON,
    UNSUPPORTED_VERSION,
    MISSING_FIELDS
}

sealed class BackupParseResult {
    data class Success(val backup: BackupData) : BackupParseResult()
    data class Error(val error: BackupParseError) : BackupParseResult()
}

object BackupParser {
    private val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2, 3, 4, 5)
    private const val LEGACY_VERSION = "0.1.0"

    private val lenientJson = Json(from = AppJson) { isLenient = true }

    @Serializable
    private data class BackupFile(
        val version: String? = null,
        val schemaVersion: Int? = null,
        val accounts: List<Account> = emptyList(),
        val bookings: List<Booking> = emptyList(),
        val categories: List<Category> = emptyList(),
        val recurringRules: List<RecurringRule> = emptyList()
    )

    fun parse(json: String): BackupParseResult {
        val file = try {
            lenientJson.decodeFromString<BackupFile>(json)
        } catch (e: SerializationException) {
            return BackupParseResult.Error(BackupParseError.INVALID_JSON)
        } catch (e: IllegalArgumentException) {
            return BackupParseResult.Error(BackupParseError.INVALID_JSON)
        }

        val schemaVersion = when {
            file.schemaVersion != null -> file.schemaVersion
            file.version == LEGACY_VERSION -> 1
            else -> -1
        }

        if (schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
            return BackupParseResult.Error(BackupParseError.UNSUPPORTED_VERSION)
        }

        return BackupParseResult.Success(
            BackupData(
                schemaVersion = schemaVersion,
                accounts = file.accounts,
                bookings = file.bookings,
                categories = file.categories,
                recurringRules = file.recurringRules
            )
        )
    }
}

enum class RestoreError {
    WRITE_FAILED
}

sealed class RestoreResult {
    data object Success : RestoreResult()
    data class Error(val error: RestoreError) : RestoreResult()
}

interface BackupStorage {
    fun readAccountsJson(): String?
    fun readBookingsJson(): String?
    fun readCategoriesJson(): String?
    fun readRecurringRulesJson(): String?
    fun writeAccountsJson(json: String?): Boolean
    fun writeBookingsJson(json: String?): Boolean
    fun writeCategoriesJson(json: String?): Boolean
    fun writeRecurringRulesJson(json: String?): Boolean
    fun serializeAccounts(accounts: List<Account>): String
    fun serializeBookings(bookings: List<Booking>): String
    fun serializeCategories(categories: List<Category>): String
    fun serializeRecurringRules(rules: List<RecurringRule>): String
}

object RestoreService {
    fun restore(backup: BackupData, storage: BackupStorage): RestoreResult {
        val previousAccounts = storage.readAccountsJson()
        val previousBookings = storage.readBookingsJson()
        val previousCategories = storage.readCategoriesJson()
        val previousRecurringRules = storage.readRecurringRulesJson()

        val accountsJson = storage.serializeAccounts(backup.accounts)
        val bookingsJson = storage.serializeBookings(backup.bookings)

        val accountsOk = storage.writeAccountsJson(accountsJson)
        val bookingsOk = storage.writeBookingsJson(bookingsJson)

        val categoriesOk = if (backup.categories.isNotEmpty()) {
            val categoriesJson = storage.serializeCategories(backup.categories)
            storage.writeCategoriesJson(categoriesJson)
        } else {
            true
        }

        val recurringRulesOk = if (backup.recurringRules.isNotEmpty()) {
            val rulesJson = storage.serializeRecurringRules(backup.recurringRules)
            storage.writeRecurringRulesJson(rulesJson)
        } else {
            true
        }

        if (accountsOk && bookingsOk && categoriesOk && recurringRulesOk) {
            return RestoreResult.Success
        }

        storage.writeAccountsJson(previousAccounts)
        storage.writeBookingsJson(previousBookings)
        storage.writeCategoriesJson(previousCategories)
        storage.writeRecurringRulesJson(previousRecurringRules)
        return RestoreResult.Error(RestoreError.WRITE_FAILED)
    }
}
