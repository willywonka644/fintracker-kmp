package io.github.willywonka644.fintracker.export

import android.content.Context
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.SqlAccountRepository
import io.github.willywonka644.fintracker.SqlBookingRepository
import io.github.willywonka644.fintracker.SqlRecurringRuleRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExportService {
    fun exportAsCsv(context: Context, db: FintrackerDatabase) {
        val data = loadData(db)
        val csv = CsvExporter.toCsv(data)
        val fileName = buildFileName("fintracker-export", "csv")
        ShareUtil.shareTextFile(
            context = context,
            fileName = fileName,
            mimeType = "text/csv",
            contents = csv
        )
    }

    fun exportAccountCsv(context: Context, account: Account, bookings: List<Booking>) {
        val data = ExportData(
            accounts = listOf(account),
            bookings = bookings
        )
        val csv = CsvExporter.toCsv(data)
        val safeName = account.name.replace(Regex("[^a-zA-Z0-9äöüÄÖÜß_-]"), "_")
        val fileName = buildFileName("fintracker-$safeName", "csv")
        ShareUtil.shareTextFile(
            context = context,
            fileName = fileName,
            mimeType = "text/csv",
            contents = csv
        )
    }

    fun exportAsJson(context: Context, db: FintrackerDatabase) {
        val data = loadData(db)
        val json = JsonExporter.toJson(data)
        val fileName = buildFileName("fintracker-export", "json")
        ShareUtil.shareTextFile(
            context = context,
            fileName = fileName,
            mimeType = "application/json",
            contents = json
        )
    }

    private fun loadData(db: FintrackerDatabase): ExportData {
        val accounts = SqlAccountRepository(db).loadAccounts()
        val bookings = SqlBookingRepository(db).loadBookings()
        val categories = SqlCategoryRepository(db).loadCategories()
        val recurringRules = SqlRecurringRuleRepository(db).loadRules()
        return ExportData(
            accounts = accounts,
            bookings = bookings,
            categories = categories,
            recurringRules = recurringRules
        )
    }

    private fun buildFileName(prefix: String, ext: String): String {
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())
        return "$prefix-$timestamp.$ext"
    }
}
