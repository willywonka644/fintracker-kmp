package io.github.willywonka644.fintracker.export

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking

object CsvExporter {
    private const val DELIMITER = ';'

    /**
     * Column order of the export. Rows are built by looking values up under these names
     * rather than by counting commas — the hand-padded lists this replaced had drifted
     * apart, and booking rows were writing one column to the right of their header.
     */
    private val COLUMNS = listOf(
        "record_type",
        "account_id",
        "account_name",
        "account_type",
        "billing_start_day",
        "spending_limit",
        "booking_id",
        "booking_amount",
        "booking_description",
        "booking_timestamp",
        "booking_account_id",
        "booking_category",
        "booking_status",
        "booking_source",
        "booking_transfer_group_id",
    )

    fun toCsv(data: ExportData): String {
        val lines = mutableListOf<String>()
        lines += COLUMNS.joinToString(DELIMITER.toString())
        data.accounts.forEach { lines += row(accountValues(it)) }
        data.bookings.forEach { lines += row(bookingValues(it)) }
        return lines.joinToString("\n")
    }

    private fun row(values: Map<String, String>): String =
        COLUMNS.joinToString(DELIMITER.toString()) { escape(values[it].orEmpty()) }

    private fun accountValues(account: Account): Map<String, String> = mapOf(
        "record_type" to "account",
        "account_id" to account.id,
        "account_name" to account.name,
        "account_type" to account.type.name,
        "billing_start_day" to account.billingStartDay?.toString().orEmpty(),
        "spending_limit" to account.spendingLimit?.toString().orEmpty(),
    )

    private fun bookingValues(booking: Booking): Map<String, String> = mapOf(
        "record_type" to "booking",
        "booking_id" to booking.id,
        "booking_amount" to booking.amount.toString(),
        "booking_description" to booking.description,
        "booking_timestamp" to booking.timestamp.toString(),
        "booking_account_id" to booking.accountId,
        "booking_category" to booking.category.orEmpty(),
        "booking_status" to booking.status.name,
        "booking_source" to booking.source.name,
        // Both halves of a transfer carry the same value, so an export can be
        // reassembled into pairs without guessing at amounts and dates.
        "booking_transfer_group_id" to booking.transferGroupId.orEmpty(),
    )

    private fun escape(value: String): String {
        val needsQuoting = value.contains(DELIMITER) ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        if (!needsQuoting) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
