package io.github.willywonka644.fintracker.export

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ExportFile(
    val version: String,
    val schemaVersion: Int,
    val exportedAt: String,
    val accounts: List<Account>,
    val bookings: List<Booking>,
    val categories: List<Category>,
    val recurringRules: List<RecurringRule> = emptyList()
)

object JsonExporter {
    private const val VERSION = "0.5.0"
    private const val SCHEMA_VERSION = 5

    private val prettyJson = Json { prettyPrint = true }

    fun toJson(data: ExportData): String =
        prettyJson.encodeToString(
            ExportFile(
                version = VERSION,
                schemaVersion = SCHEMA_VERSION,
                exportedAt = Clock.System.now().toString(),
                accounts = data.accounts,
                bookings = data.bookings,
                categories = data.categories,
                recurringRules = data.recurringRules
            )
        )
}
