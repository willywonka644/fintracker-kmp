package io.github.willywonka644.fintracker.export

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The export had no test, and it had drifted: booking rows carried one padding column too
 * many, so every booking value sat one column to the right of its own header — `booking_id`
 * under `spending_limit`, and the status in a column that did not exist. Nobody noticed,
 * because nothing counted the columns.
 *
 * These tests count the columns.
 */
class CsvExporterTest {

    private val account = Account(
        id = "acc-1",
        name = "Girokonto",
        type = AccountType.GIRO,
        billingStartDay = 1,
        spendingLimit = 4000.0,
    )

    private val booking = Booking(
        id = "b1",
        accountId = "acc-1",
        amount = -1251.38,
        description = "Kreditkartenabrechnung Juli",
        timestamp = 1_600_000_000_000,
        category = "Kreditkartenabrechnung",
        source = BookingSource.TRANSFER,
        transferGroupId = "grp-1",
    )

    private fun lines(data: ExportData) = CsvExporter.toCsv(data).split("\n")

    private fun cells(line: String) = line.split(';')

    @Test
    fun everyRowHasExactlyAsManyColumnsAsTheHeader() {
        val out = lines(ExportData(accounts = listOf(account), bookings = listOf(booking)))
        val width = cells(out.first()).size

        out.forEachIndexed { index, line ->
            assertEquals(width, cells(line).size, "row $index has the wrong number of columns: $line")
        }
    }

    @Test
    fun aBookingLandsUnderItsOwnHeaders() {
        val out = lines(ExportData(accounts = emptyList(), bookings = listOf(booking)))
        val header = cells(out.first())
        val row = cells(out[1])
        val value = { name: String -> row[header.indexOf(name)] }

        assertEquals("booking", value("record_type"))
        assertEquals("b1", value("booking_id"))
        assertEquals("-1251.38", value("booking_amount"))
        assertEquals("acc-1", value("booking_account_id"))
        assertEquals("POSTED", value("booking_status"))
        assertEquals("", value("spending_limit"))
        assertEquals("", value("account_name"))
    }

    @Test
    fun anAccountLandsUnderItsOwnHeaders() {
        val out = lines(ExportData(accounts = listOf(account), bookings = emptyList()))
        val header = cells(out.first())
        val row = cells(out[1])
        val value = { name: String -> row[header.indexOf(name)] }

        assertEquals("account", value("record_type"))
        assertEquals("acc-1", value("account_id"))
        assertEquals("Girokonto", value("account_name"))
        assertEquals("GIRO", value("account_type"))
        assertEquals("", value("booking_id"))
        assertEquals("", value("booking_transfer_group_id"))
    }

    @Test
    fun aTransferCarriesItsSourceAndItsGroup() {
        // Without these two columns an export cannot be put back together into pairs,
        // and a restored backup would silently turn transfers into ordinary bookings.
        val out = lines(ExportData(accounts = emptyList(), bookings = listOf(booking)))
        val header = cells(out.first())
        val row = cells(out[1])

        assertEquals("TRANSFER", row[header.indexOf("booking_source")])
        assertEquals("grp-1", row[header.indexOf("booking_transfer_group_id")])
    }

    @Test
    fun anOrdinaryBookingLeavesTheTransferColumnEmpty() {
        val plain = booking.copy(source = BookingSource.MANUAL, transferGroupId = null)
        val out = lines(ExportData(accounts = emptyList(), bookings = listOf(plain)))
        val header = cells(out.first())
        val row = cells(out[1])

        assertEquals("MANUAL", row[header.indexOf("booking_source")])
        assertEquals("", row[header.indexOf("booking_transfer_group_id")])
    }

    @Test
    fun aDescriptionWithTheDelimiterIsQuoted() {
        val tricky = booking.copy(description = "Rewe; Netto")
        val out = CsvExporter.toCsv(ExportData(accounts = emptyList(), bookings = listOf(tricky)))

        assertTrue(out.contains("\"Rewe; Netto\""), "expected the description to be quoted, got: $out")
    }
}
