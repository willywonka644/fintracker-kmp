package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.IAccountRepository
import io.github.willywonka644.fintracker.IBookingRepository
import io.github.willywonka644.fintracker.IRecurringRuleRepository
import io.github.willywonka644.fintracker.ISettingsRepository
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.ICategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tier-1a tests (Phase 6.5) — Sync merge + tombstone semantics.
 *
 * These lock in the last-writer-wins rules that Phase 7 (Raspberry-Pi server)
 * will build on top of. The importer is pure logic: it takes a JSON payload
 * plus the local repositories as the merge base and returns a preview — it
 * never writes, so read-only fakes are enough.
 */
class SyncMergeTest {

    // ── Fakes: read-only repositories seeded from constructor lists ────────────

    private class FakeAccountRepo(private val all: List<Account>) : IAccountRepository {
        override fun loadAccounts() = all.filter { !it.deleted }
        override fun loadAllAccountsForSync() = all
        override fun reload() = all
        override fun saveAccounts(accounts: List<Account>) {}
        override fun softDeleteAccount(id: String, deletedAt: Long) {}
        override fun getRawAccountsJson(): String? = null
        override fun replaceAccountsJson(json: String?) = false
        override fun serializeAccounts(accounts: List<Account>) = ""
    }

    private class FakeBookingRepo(private val all: List<Booking>) : IBookingRepository {
        override fun loadBookings() = all.filter { !it.deleted }
        override fun loadAllBookingsForSync() = all
        override fun reload() = all
        override fun saveBookings(bookings: List<Booking>) {}
        override fun softDeleteBooking(id: String, deletedAt: Long) {}
        override fun getRawBookingsJson(): String? = null
        override fun replaceBookingsJson(json: String?) = false
        override fun serializeBookings(bookings: List<Booking>) = ""
    }

    private class FakeRuleRepo(private val all: List<RecurringRule>) : IRecurringRuleRepository {
        override fun loadRules() = all.filter { !it.deleted }
        override fun loadAllRulesForSync() = all
        override fun reload() = all
        override fun saveRules(rules: List<RecurringRule>) {}
        override fun softDeleteRule(id: String, deletedAt: Long) {}
        override fun getRawRulesJson(): String? = null
        override fun replaceRulesJson(json: String?) = false
        override fun serializeRules(rules: List<RecurringRule>) = ""
    }

    private class FakeCategoryRepo(private val all: List<Category>) : ICategoryRepository {
        override val categoriesFlow: StateFlow<List<Category>> = MutableStateFlow(all)
        override fun loadCategories() = all.filter { !it.deleted }
        override fun loadAllCategoriesForSync() = all
        override fun reload() = all
        override fun saveCategories(categories: List<Category>) {}
        override fun softDeleteCategory(id: String, deletedAt: Long) {}
        override fun getRawCategoriesJson(): String? = null
        override fun replaceCategoriesJson(json: String?) = false
        override fun serializeCategories(categories: List<Category>) = ""
    }

    private class FakeSettingsRepo(private val deviceId: String) : ISettingsRepository {
        override fun getDeviceId() = deviceId
        override fun get(key: String): String? = null
        override fun set(key: String, value: String) {}
    }

    // ── Test data builders ─────────────────────────────────────────────────────

    private val foodCategory = Category(
        id = "cat-food", name = "Lebensmittel", iconName = "ShoppingCart",
        color = 0xFFFFA726, isDefault = true, lastModifiedAt = 1L
    )

    private fun account(id: String, modifiedAt: Long = 1L, deleted: Boolean = false) =
        Account(id = id, name = "Giro $id", type = AccountType.GIRO,
            billingStartDay = 1, lastModifiedAt = modifiedAt, deleted = deleted)

    private fun booking(id: String, accountId: String, modifiedAt: Long = 1L, deleted: Boolean = false) =
        Booking(id = id, accountId = accountId, amount = -9.99, description = "Kauf $id",
            timestamp = 1_000L, category = "Lebensmittel", lastModifiedAt = modifiedAt, deleted = deleted)

    private fun rule(id: String, accountId: String, modifiedAt: Long, deleted: Boolean = false) =
        RecurringRule(id = id, amount = -50.0, description = "Abo $id", accountId = accountId,
            frequency = Frequency.MONTHLY, nextExecutionDate = LocalDate(2026, 1, 1),
            lastModifiedAt = modifiedAt, deleted = deleted)

    private fun exporterFor(
        accounts: List<Account> = emptyList(),
        bookings: List<Booking> = emptyList(),
        categories: List<Category> = listOf(foodCategory),
        rules: List<RecurringRule> = emptyList(),
        deviceId: String = "device-A"
    ) = SyncExporter(
        FakeAccountRepo(accounts), FakeBookingRepo(bookings),
        FakeCategoryRepo(categories), FakeRuleRepo(rules), FakeSettingsRepo(deviceId)
    )

    private fun importerFor(
        accounts: List<Account> = emptyList(),
        bookings: List<Booking> = emptyList(),
        categories: List<Category> = listOf(foodCategory),
        rules: List<RecurringRule> = emptyList()
    ) = SyncImporter(
        FakeAccountRepo(accounts), FakeBookingRepo(bookings),
        FakeCategoryRepo(categories), FakeRuleRepo(rules)
    )

    private fun previewOf(result: SyncImportResult): SyncPreview {
        assertIs<SyncImportResult.Preview>(result)
        return result.preview
    }

    // ── Round-trip ─────────────────────────────────────────────────────────────

    @Test
    fun roundTrip_exportThenImportIntoEmptyDevice_bringsAllLiveData() {
        val acc = account("a1")
        val bk = booking("b1", "a1")
        val rl = rule("r1", "a1", modifiedAt = 5L)

        val json = exporterFor(
            accounts = listOf(acc), bookings = listOf(bk), rules = listOf(rl)
        ).export()

        // Empty device B receives the export.
        val preview = previewOf(importerFor(categories = emptyList()).import(json))

        // 1 category + 1 account + 1 booking + 1 rule = 4 new records.
        assertEquals(4, preview.newCount)
        assertEquals(0, preview.unchangedCount)
        assertEquals(0, preview.skippedCount)
        assertTrue(preview.resolvedAccounts.any { it.id == "a1" })
        assertTrue(preview.resolvedBookings.any { it.id == "b1" })
        assertTrue(preview.resolvedRecurringRules.any { it.id == "r1" })
    }

    // ── Tombstone precedence (last-writer-wins) ────────────────────────────────

    @Test
    fun import_newerTombstone_winsOverOlderLocalLiveRule() {
        // Local device still has the rule live (modified at t=100)...
        val localLive = rule("r1", "a1", modifiedAt = 100L)
        // ...incoming payload deleted it later (t=200).
        val incomingTombstone = rule("r1", "a1", modifiedAt = 200L, deleted = true)

        // Empty categories on both sides so only the rule contributes to counters.
        val json = exporterFor(rules = listOf(incomingTombstone), categories = emptyList()).export()
        val preview = previewOf(
            importerFor(rules = listOf(localLive), categories = emptyList()).import(json)
        )

        val merged = preview.resolvedRecurringRules.single { it.id == "r1" }
        assertTrue(merged.deleted, "Newer tombstone must win → rule stays deleted")
        assertEquals(1, preview.updatedCount)
    }

    @Test
    fun import_newerLiveEdit_winsOverOlderLocalTombstone() {
        // Local device deleted the rule earlier (t=100)...
        val localTombstone = rule("r1", "a1", modifiedAt = 100L, deleted = true)
        // ...but the other device made a live edit afterwards (t=200).
        val incomingLive = rule("r1", "a1", modifiedAt = 200L)

        val json = exporterFor(rules = listOf(incomingLive), categories = emptyList()).export()
        val preview = previewOf(
            importerFor(rules = listOf(localTombstone), categories = emptyList()).import(json)
        )

        val merged = preview.resolvedRecurringRules.single { it.id == "r1" }
        assertFalse(merged.deleted, "Newer live edit must win → rule is resurrected")
    }

    @Test
    fun import_olderTombstone_doesNotDeleteNewerLocalLiveRule() {
        // Regression guard: a stale deletion must not wipe a freshly edited record.
        val localLive = rule("r1", "a1", modifiedAt = 200L)
        val incomingTombstone = rule("r1", "a1", modifiedAt = 100L, deleted = true)

        val json = exporterFor(rules = listOf(incomingTombstone), categories = emptyList()).export()
        val preview = previewOf(
            importerFor(rules = listOf(localLive), categories = emptyList()).import(json)
        )

        val merged = preview.resolvedRecurringRules.single { it.id == "r1" }
        assertFalse(merged.deleted, "Stale tombstone must not delete a newer local edit")
        assertEquals(1, preview.unchangedCount)
    }

    // ── Category tombstones (issue #77) ────────────────────────────────────────

    @Test
    fun import_deletedCategory_propagatesInsteadOfComingBack() {
        // Before categories had tombstones the merge was a plain union by id, so a
        // category deleted on one device was handed straight back by the other.
        val localLive = foodCategory.copy(lastModifiedAt = 100L)
        val incomingTombstone = foodCategory.copy(lastModifiedAt = 200L, deleted = true)

        val json = exporterFor(categories = listOf(incomingTombstone)).export()
        val preview = previewOf(importerFor(categories = listOf(localLive)).import(json))

        val merged = preview.resolvedCategories.single { it.id == "cat-food" }
        assertTrue(merged.deleted, "Newer tombstone must win → category stays deleted")
    }

    @Test
    fun export_deletedCategoryStillReferencedByBookings_isNotResurrectedAsOrphan() {
        // Bookings point at categories by name. The exporter invents a "recovered-"
        // category for every referenced name it cannot find — counting a tombstoned
        // name as missing would undo the deletion on the very next export.
        val tombstone = foodCategory.copy(lastModifiedAt = 200L, deleted = true)
        val stillReferencing = booking("b1", "a1") // category = "Lebensmittel"

        val json = exporterFor(
            accounts = listOf(account("a1")),
            bookings = listOf(stillReferencing),
            categories = listOf(tombstone),
        ).export()

        assertFalse(
            json.contains("recovered-lebensmittel"),
            "the deleted category must not reappear under a recovered- id",
        )
    }

    @Test
    fun import_bookingOnDeletedCategory_keepsItsCategoryName() {
        // The unknown-category guard exists to catch references that never had a
        // category. Treating a tombstoned one as unknown would quietly strip the
        // category off the user's bookings instead.
        val tombstone = foodCategory.copy(lastModifiedAt = 200L, deleted = true)
        val json = exporterFor(
            accounts = listOf(account("a1")),
            bookings = listOf(booking("b1", "a1")),
            categories = listOf(tombstone),
        ).export()

        val preview = previewOf(
            importerFor(accounts = listOf(account("a1")), categories = emptyList()).import(json)
        )

        assertEquals("Lebensmittel", preview.resolvedBookings.single { it.id == "b1" }.category)
    }

    // ── Booking referential integrity ──────────────────────────────────────────

    @Test
    fun import_bookingForUnknownAccount_isSkippedWithWarning() {
        // Live booking references an account that exists on neither device.
        val orphan = booking("b1", accountId = "ghost")
        val json = exporterFor(bookings = listOf(orphan)).export()

        val preview = previewOf(
            importerFor(accounts = emptyList(), categories = emptyList()).import(json)
        )

        assertEquals(1, preview.skippedCount)
        assertFalse(preview.resolvedBookings.any { it.id == "b1" })
        assertTrue(preview.warnings.any { it.contains("ghost") })
    }

    @Test
    fun import_deletedBooking_bypassesAccountValidation() {
        // A tombstone booking for an unknown account should still propagate the
        // deletion (no skip) — otherwise deletions could never sync.
        val tombstone = booking("b1", accountId = "ghost", modifiedAt = 50L, deleted = true)
        val json = exporterFor(bookings = listOf(tombstone)).export()

        val preview = previewOf(importerFor(accounts = emptyList(), categories = emptyList()).import(json))

        assertEquals(0, preview.skippedCount)
        val merged = preview.resolvedBookings.single { it.id == "b1" }
        assertTrue(merged.deleted)
    }

    // ── Version / payload validation ───────────────────────────────────────────

    @Test
    fun import_invalidJson_returnsInvalidJsonError() {
        val result = importerFor().import("{ this is not valid json")
        assertIs<SyncImportResult.Error>(result)
        assertEquals(SyncError.InvalidJson, result.error)
    }

    @Test
    fun import_unknownVersion_returnsUnknownVersionError() {
        val json = """
            {"version":"9.9","exportedAt":"2026-07-24T10:00:00Z","deviceId":"d",
             "accounts":[],"bookings":[],"categories":[],"recurringRules":[]}
        """.trimIndent()
        val result = importerFor().import(json)
        assertIs<SyncImportResult.Error>(result)
        assertEquals(SyncError.UnknownVersion, result.error)
    }

    @Test
    fun import_missingVersion_returnsUnknownVersionError() {
        val result = importerFor().import("""{"exportedAt":"x"}""")
        assertIs<SyncImportResult.Error>(result)
        assertEquals(SyncError.UnknownVersion, result.error)
    }

    @Test
    fun import_missingRequiredField_reportsWhichFieldIsMissing() {
        // Valid version but everything else absent → first missing field is exportedAt.
        val result = importerFor().import("""{"version":"1.0"}""")
        assertIs<SyncImportResult.Error>(result)
        assertEquals(SyncError.MissingField("exportedAt"), result.error)
    }
}
