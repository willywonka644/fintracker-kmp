package io.github.willywonka644.fintracker.ui.exportimport

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.IAccountRepository
import io.github.willywonka644.fintracker.IBookingRepository
import io.github.willywonka644.fintracker.IRecurringRuleRepository
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.booking.BookingFactory
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.ICategoryRepository
import io.github.willywonka644.fintracker.csvimport.CsvColumnRole
import io.github.willywonka644.fintracker.csvimport.CsvParser
import io.github.willywonka644.fintracker.csvimport.CsvParseResult
import io.github.willywonka644.fintracker.export.BackupParser
import io.github.willywonka644.fintracker.export.BackupParseResult
import io.github.willywonka644.fintracker.export.BackupStorage
import io.github.willywonka644.fintracker.export.CsvExporter
import io.github.willywonka644.fintracker.export.ExportData
import io.github.willywonka644.fintracker.export.JsonExporter
import io.github.willywonka644.fintracker.export.RestoreResult
import io.github.willywonka644.fintracker.export.RestoreService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import java.util.UUID

// ── CSV Export ────────────────────────────────────────────────────────────────

fun exportCsvAllAccounts(
    accountRepo: IAccountRepository,
    bookingRepo: IBookingRepository,
): Result<String> = runCatching {
    CsvExporter.toCsv(
        ExportData(
            accounts = accountRepo.loadAccounts(),
            bookings = bookingRepo.loadBookings(),
        )
    )
}

fun exportCsvSingleAccount(
    account: Account,
    bookingRepo: IBookingRepository,
): Result<String> = runCatching {
    CsvExporter.toCsv(
        ExportData(
            accounts = listOf(account),
            bookings = bookingRepo.loadBookings().filter { it.accountId == account.id },
        )
    )
}

// ── CSV Import ────────────────────────────────────────────────────────────────

fun parseCsvFile(text: String): CsvParseResult = CsvParser.parse(text)

fun importCsvBookings(
    parseResult: CsvParseResult,
    columnMapping: Map<Int, CsvColumnRole>,
    targetAccountId: String,
    bookingRepo: IBookingRepository,
): Int {
    val existing = bookingRepo.loadBookings()
    val duplicateKeys = CsvParser.buildDuplicateKeySet(existing)
    val newBookings = mutableListOf<Booking>()

    val dateIdx = columnMapping.entries.firstOrNull { it.value == CsvColumnRole.DATE }?.key
    val amountIdx = columnMapping.entries.firstOrNull { it.value == CsvColumnRole.AMOUNT }?.key
    val descIdx = columnMapping.entries.firstOrNull { it.value == CsvColumnRole.DESCRIPTION }?.key
    val catIdx = columnMapping.entries.firstOrNull { it.value == CsvColumnRole.CATEGORY }?.key

    for (row in parseResult.rows) {
        val dateStr = dateIdx?.let { row.getOrNull(it) } ?: continue
        val amountStr = amountIdx?.let { row.getOrNull(it) } ?: continue
        val description = descIdx?.let { row.getOrNull(it)?.takeIf { s -> s.isNotBlank() } } ?: continue

        val date = CsvParser.tryParseDate(dateStr) ?: continue
        val amount = CsvParser.tryParseAmount(amountStr) ?: continue
        val category = catIdx?.let { row.getOrNull(it)?.takeIf { s -> s.isNotBlank() } }

        val timestamp = date
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

        val booking = BookingFactory.createManualBooking(
            idProvider = { UUID.randomUUID().toString() },
            accountId = targetAccountId,
            amount = amount,
            description = description,
            category = category,
            timestamp = timestamp,
            source = BookingSource.IMPORT,
        )

        if (CsvParser.duplicateKey(booking) !in duplicateKeys) {
            newBookings.add(booking)
        }
    }

    if (newBookings.isNotEmpty()) {
        bookingRepo.saveBookings(existing + newBookings)
    }
    return newBookings.size
}

// ── JSON Backup / Restore ─────────────────────────────────────────────────────

fun exportJson(
    accountRepo: IAccountRepository,
    bookingRepo: IBookingRepository,
    categoryRepo: ICategoryRepository,
    recurringRepo: IRecurringRuleRepository,
): Result<String> = runCatching {
    JsonExporter.toJson(
        ExportData(
            accounts = accountRepo.loadAccounts(),
            bookings = bookingRepo.loadBookings(),
            categories = categoryRepo.loadCategories(),
            recurringRules = recurringRepo.loadRules(),
        )
    )
}

fun restoreJson(
    json: String,
    accountRepo: IAccountRepository,
    bookingRepo: IBookingRepository,
    categoryRepo: ICategoryRepository,
    recurringRepo: IRecurringRuleRepository,
): Result<Unit> = runCatching {
    val parsed = BackupParser.parse(json)
    if (parsed is BackupParseResult.Error) error("Parse error: ${parsed.error}")
    val backup = (parsed as BackupParseResult.Success).backup

    val storage = object : BackupStorage {
        override fun readAccountsJson() = accountRepo.getRawAccountsJson()
        override fun readBookingsJson() = bookingRepo.getRawBookingsJson()
        override fun readCategoriesJson() = categoryRepo.getRawCategoriesJson()
        override fun readRecurringRulesJson() = recurringRepo.getRawRulesJson()
        override fun writeAccountsJson(json: String?) = accountRepo.replaceAccountsJson(json)
        override fun writeBookingsJson(json: String?) = bookingRepo.replaceBookingsJson(json)
        override fun writeCategoriesJson(json: String?) = categoryRepo.replaceCategoriesJson(json)
        override fun writeRecurringRulesJson(json: String?) = recurringRepo.replaceRulesJson(json)
        override fun serializeAccounts(accounts: List<Account>) = accountRepo.serializeAccounts(accounts)
        override fun serializeBookings(bookings: List<Booking>) = bookingRepo.serializeBookings(bookings)
        override fun serializeCategories(categories: List<Category>) = categoryRepo.serializeCategories(categories)
        override fun serializeRecurringRules(rules: List<RecurringRule>) = recurringRepo.serializeRules(rules)
    }

    val result = RestoreService.restore(backup, storage)
    if (result is RestoreResult.Error) error("Restore failed: ${result.error}")
}
