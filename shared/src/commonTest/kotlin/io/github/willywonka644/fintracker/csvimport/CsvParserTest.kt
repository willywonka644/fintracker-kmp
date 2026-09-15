package io.github.willywonka644.fintracker.csvimport

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tier-1d tests (Phase 6.5) — CSV import parsing and duplicate detection.
 *
 * The parsing paths are pure. duplicateKey reduces a timestamp to a date in
 * the system zone, so its fixtures build the timestamp in that same zone —
 * which keeps the assertions independent of where the test runs.
 */
class CsvParserTest {

    // ── tryParseAmount ─────────────────────────────────────────────────────────

    @Test
    fun tryParseAmount_germanFormat() {
        assertEquals(1234.56, CsvParser.tryParseAmount("1.234,56"))
        assertEquals(1234.56, CsvParser.tryParseAmount("1.234,56 €"))
        assertEquals(-50.0, CsvParser.tryParseAmount("-50,00"))
    }

    @Test
    fun tryParseAmount_internationalFormat() {
        assertEquals(1234.56, CsvParser.tryParseAmount("1,234.56"))
        assertEquals(123.45, CsvParser.tryParseAmount("123.45"))
    }

    @Test
    fun tryParseAmount_stripsCurrencySymbolAndCode() {
        assertEquals(100.0, CsvParser.tryParseAmount("100,00 €"))
        assertEquals(50.0, CsvParser.tryParseAmount("50.00 EUR"))
    }

    @Test
    fun tryParseAmount_rejectsNonNumeric() {
        assertNull(CsvParser.tryParseAmount("abc"))
        assertNull(CsvParser.tryParseAmount(""))
    }

    // ── tryParseDate ───────────────────────────────────────────────────────────

    @Test
    fun tryParseDate_acceptsCommonFormats() {
        assertEquals(LocalDate(2026, 1, 5), CsvParser.tryParseDate("2026-01-05"))
        assertEquals(LocalDate(2026, 1, 5), CsvParser.tryParseDate("05.01.2026"))
        assertEquals(LocalDate(2026, 1, 5), CsvParser.tryParseDate("5.1.2026"))
        assertEquals(LocalDate(2026, 1, 5), CsvParser.tryParseDate("05/01/2026"))
    }

    @Test
    fun tryParseDate_rejectsImpossibleAndGarbageDates() {
        assertNull(CsvParser.tryParseDate("31.02.2026")) // Feb 31 does not exist
        assertNull(CsvParser.tryParseDate("hello"))
    }

    // ── parse: delimiter detection + auto-mapping ──────────────────────────────

    @Test
    fun parse_detectsSemicolonAndMapsGermanBankHeaders() {
        val csv = """
            Datum;Betrag;Verwendungszweck
            05.01.2026;-12,50;REWE Markt
            06.01.2026;1.000,00;Gehalt
        """.trimIndent()

        val result = CsvParser.parse(csv)

        assertEquals(';', result.detectedDelimiter)
        assertEquals(listOf("Datum", "Betrag", "Verwendungszweck"), result.headers)
        assertEquals(2, result.rows.size)
        assertEquals(CsvColumnRole.DATE, result.autoMapping[0])
        assertEquals(CsvColumnRole.AMOUNT, result.autoMapping[1])
        assertEquals(CsvColumnRole.DESCRIPTION, result.autoMapping[2])
        assertEquals(listOf("05.01.2026", "-12,50", "REWE Markt"), result.rows[0])
    }

    @Test
    fun parse_detectsCommaAndMapsEnglishHeaders() {
        val csv = """
            date,amount,description
            2026-03-01,-45.50,Grocery shopping
            2026-03-02,2500.00,Salary
        """.trimIndent()

        val result = CsvParser.parse(csv)

        assertEquals(',', result.detectedDelimiter)
        assertEquals(listOf("date", "amount", "description"), result.headers)
        assertEquals(2, result.rows.size)
        assertEquals(CsvColumnRole.DATE, result.autoMapping[0])
        assertEquals(CsvColumnRole.AMOUNT, result.autoMapping[1])
        assertEquals(CsvColumnRole.DESCRIPTION, result.autoMapping[2])
    }

    @Test
    fun parse_handlesQuotedFieldContainingDelimiter() {
        val csv = "date,amount,desc\n2026-01-05,-12.50,\"Hello, World\""
        val result = CsvParser.parse(csv)

        assertEquals(',', result.detectedDelimiter)
        // The quoted comma must NOT split the field.
        assertEquals("Hello, World", result.rows[0][2])
    }

    @Test
    fun parse_mapsRolesFromContentWhenHeadersSayNothing() {
        // Headers match no keyword — only the data content reveals the roles.
        val csv = """
            col_a;col_b;col_c
            01.03.2026;-45,50;Einkauf Rewe
            02.03.2026;2500,00;Gehalt März
        """.trimIndent()

        val result = CsvParser.parse(csv)

        assertEquals(CsvColumnRole.DATE, result.autoMapping[0])
        assertEquals(CsvColumnRole.AMOUNT, result.autoMapping[1])
        assertEquals(CsvColumnRole.DESCRIPTION, result.autoMapping[2])
    }

    @Test
    fun parse_emptyInputYieldsNoHeadersAndNoRows() {
        val result = CsvParser.parse("")

        assertTrue(result.headers.isEmpty())
        assertTrue(result.rows.isEmpty())
    }

    // ── duplicateKey ───────────────────────────────────────────────────────────

    private fun booking(id: String, amount: Double, desc: String) = Booking(
        id = id, accountId = "acc", amount = amount, description = desc, timestamp = 1_000_000L
    )

    /**
     * Booking pinned to a calendar day. duplicateKey reduces the timestamp to a
     * date in the system zone, so the fixture has to build it in that same zone.
     */
    private fun bookingOn(
        date: LocalDate,
        amount: Double,
        desc: String,
        accountId: String = "acc",
        id: String = "id1"
    ) = Booking(
        id = id,
        accountId = accountId,
        amount = amount,
        description = desc,
        timestamp = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds(),
        source = BookingSource.IMPORT
    )

    @Test
    fun duplicateKey_ignoresIdButReflectsCoreFields() {
        val a = booking("1", -12.5, "REWE")
        val b = booking("2", -12.5, "REWE") // same core fields, different id
        val c = booking("3", -99.9, "REWE") // different amount

        assertEquals(CsvParser.duplicateKey(a), CsvParser.duplicateKey(b))
        assertNotEquals(CsvParser.duplicateKey(a), CsvParser.duplicateKey(c))
    }

    @Test
    fun duplicateKey_differentDateMeansDifferentKey() {
        val a = bookingOn(LocalDate(2026, 3, 1), -45.50, "Einkauf Rewe")
        val b = bookingOn(LocalDate(2026, 3, 2), -45.50, "Einkauf Rewe")

        assertNotEquals(CsvParser.duplicateKey(a), CsvParser.duplicateKey(b))
    }

    @Test
    fun duplicateKey_differentDescriptionMeansDifferentKey() {
        val a = bookingOn(LocalDate(2026, 3, 1), -45.50, "Einkauf Rewe")
        val b = bookingOn(LocalDate(2026, 3, 1), -45.50, "Einkauf Aldi")

        assertNotEquals(CsvParser.duplicateKey(a), CsvParser.duplicateKey(b))
    }

    @Test
    fun duplicateKey_differentAccountMeansDifferentKey() {
        val a = bookingOn(LocalDate(2026, 3, 1), -45.50, "Einkauf Rewe", accountId = "acc1")
        val b = bookingOn(LocalDate(2026, 3, 1), -45.50, "Einkauf Rewe", accountId = "acc2")

        assertNotEquals(CsvParser.duplicateKey(a), CsvParser.duplicateKey(b))
    }

    @Test
    fun duplicateKey_descriptionIsCaseInsensitiveAndTrimmed() {
        val a = bookingOn(LocalDate(2026, 3, 1), -45.50, "Einkauf Rewe")
        val lowercased = bookingOn(LocalDate(2026, 3, 1), -45.50, "einkauf rewe")
        val padded = bookingOn(LocalDate(2026, 3, 1), -45.50, "  Einkauf Rewe  ")

        assertEquals(CsvParser.duplicateKey(a), CsvParser.duplicateKey(lowercased))
        assertEquals(CsvParser.duplicateKey(a), CsvParser.duplicateKey(padded))
    }

    // ── buildDuplicateKeySet ───────────────────────────────────────────────────

    @Test
    fun buildDuplicateKeySet_matchesExistingBookingsOnly() {
        val existing = listOf(
            bookingOn(LocalDate(2026, 3, 1), -45.50, "Einkauf Rewe"),
            bookingOn(LocalDate(2026, 3, 2), 2500.00, "Gehalt")
        )

        val keySet = CsvParser.buildDuplicateKeySet(existing)
        assertEquals(2, keySet.size)

        // Same core fields, fresh id → still recognised as a duplicate.
        val duplicate = bookingOn(LocalDate(2026, 3, 1), -45.50, "Einkauf Rewe", id = "newId")
        assertTrue(CsvParser.duplicateKey(duplicate) in keySet)

        val unique = bookingOn(LocalDate(2026, 3, 3), -20.00, "Tankstelle")
        assertFalse(CsvParser.duplicateKey(unique) in keySet)
    }

    @Test
    fun buildDuplicateKeySet_emptyListYieldsEmptySet() {
        assertTrue(CsvParser.buildDuplicateKeySet(emptyList()).isEmpty())
    }
}
