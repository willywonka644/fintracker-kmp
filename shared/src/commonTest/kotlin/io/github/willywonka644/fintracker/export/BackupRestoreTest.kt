package io.github.willywonka644.fintracker.export

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tier-1d tests (Phase 6.5) — backup parsing + restore rollback.
 *
 * The restore rollback path is the important one: a partial write must never
 * leave the app with half-restored data — it has to roll back to the previous
 * state and report failure.
 */
class BackupRestoreTest {

    // ── BackupParser.parse: schema version resolution ──────────────────────────

    @Test
    fun parse_explicitSchemaVersionIsAccepted() {
        val result = BackupParser.parse("""{"schemaVersion":3,"accounts":[],"bookings":[]}""")
        assertIs<BackupParseResult.Success>(result)
        assertEquals(3, result.backup.schemaVersion)
    }

    @Test
    fun parse_legacyVersionStringMapsToSchema1() {
        val result = BackupParser.parse("""{"version":"0.1.0","accounts":[],"bookings":[]}""")
        assertIs<BackupParseResult.Success>(result)
        assertEquals(1, result.backup.schemaVersion)
    }

    @Test
    fun parse_unsupportedSchemaVersionIsRejected() {
        val result = BackupParser.parse("""{"schemaVersion":99,"accounts":[],"bookings":[]}""")
        assertIs<BackupParseResult.Error>(result)
        assertEquals(BackupParseError.UNSUPPORTED_VERSION, result.error)
    }

    @Test
    fun parse_noVersionInfoIsRejected() {
        val result = BackupParser.parse("""{"accounts":[],"bookings":[]}""")
        assertIs<BackupParseResult.Error>(result)
        assertEquals(BackupParseError.UNSUPPORTED_VERSION, result.error)
    }

    @Test
    fun parse_readsBackWhatJsonExporterWrote() {
        val data = ExportData(
            accounts = listOf(
                Account(
                    id = "1",
                    name = "Kreditkarte",
                    type = AccountType.CREDIT_CARD,
                    billingStartDay = 18,
                    spendingLimit = 1000.0
                )
            ),
            bookings = listOf(
                Booking(
                    id = "b1",
                    accountId = "1",
                    amount = -10.0,
                    description = "Test",
                    timestamp = 1000L,
                    category = "Essen"
                )
            )
        )

        val result = BackupParser.parse(JsonExporter.toJson(data))

        assertIs<BackupParseResult.Success>(result)
        // Current export format — bump this together with JsonExporter.SCHEMA_VERSION.
        assertEquals(5, result.backup.schemaVersion)
        assertEquals(data.accounts, result.backup.accounts)
        assertEquals(data.bookings, result.backup.bookings)
    }

    @Test
    fun parse_invalidJsonIsReported() {
        val result = BackupParser.parse("{ this is broken")
        assertIs<BackupParseResult.Error>(result)
        assertEquals(BackupParseError.INVALID_JSON, result.error)
    }

    // ── RestoreService: success + rollback ─────────────────────────────────────

    /** In-memory storage. [failOn] forces one write channel to fail. */
    private class FakeStorage(private val failOn: String? = null) : BackupStorage {
        var accounts: String? = "OLD_ACC"
        var bookings: String? = "OLD_BK"
        var categories: String? = "OLD_CAT"
        var rules: String? = "OLD_RULES"

        override fun readAccountsJson() = accounts
        override fun readBookingsJson() = bookings
        override fun readCategoriesJson() = categories
        override fun readRecurringRulesJson() = rules

        override fun writeAccountsJson(json: String?): Boolean {
            if (failOn == "acc") return false; accounts = json; return true
        }
        override fun writeBookingsJson(json: String?): Boolean {
            if (failOn == "bk") return false; bookings = json; return true
        }
        override fun writeCategoriesJson(json: String?): Boolean {
            if (failOn == "cat") return false; categories = json; return true
        }
        override fun writeRecurringRulesJson(json: String?): Boolean {
            if (failOn == "rules") return false; rules = json; return true
        }

        override fun serializeAccounts(accounts: List<Account>) = "ACC:${accounts.size}"
        override fun serializeBookings(bookings: List<Booking>) = "BK:${bookings.size}"
        override fun serializeCategories(categories: List<Category>) = "CAT:${categories.size}"
        override fun serializeRecurringRules(rules: List<RecurringRule>) = "RULES:${rules.size}"
    }

    private fun sampleBackup() = BackupData(
        schemaVersion = 1,
        accounts = listOf(Account(id = "a", name = "Giro", type = AccountType.GIRO)),
        bookings = listOf(Booking(id = "b", accountId = "a", amount = -5.0, description = "x", timestamp = 1L))
    )

    @Test
    fun restore_writesAllDataOnSuccess() {
        val storage = FakeStorage()
        val result = RestoreService.restore(sampleBackup(), storage)

        assertIs<RestoreResult.Success>(result)
        assertEquals("ACC:1", storage.accounts)
        assertEquals("BK:1", storage.bookings)
    }

    @Test
    fun restore_rollsBackToPreviousStateWhenAWriteFails() {
        val storage = FakeStorage(failOn = "bk") // bookings write always fails
        val result = RestoreService.restore(sampleBackup(), storage)

        assertIs<RestoreResult.Error>(result)
        assertEquals(RestoreError.WRITE_FAILED, result.error)
        // Accounts were written then rolled back to the original value.
        assertEquals("OLD_ACC", storage.accounts)
        assertEquals("OLD_BK", storage.bookings)
    }
}
