package io.github.willywonka644.fintracker

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.SEED_LAST_MODIFIED_AT
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tier-1e tests (Phase 6.5) — SQL repositories against a real in-memory SQLite DB.
 *
 * These lock in the persistence-layer invariants the DKB data-loss fix relies on:
 *  - soft-deleting a recurring rule must NOT touch its posted booking history
 *  - save must preserve tombstones so a deletion can never be resurrected
 *  - categories follow the same scheme (issue #77), with one deliberate exception
 *
 * Runs on the desktop target because the JDBC SQLite driver is desktop-specific.
 */
class SqlRepositoriesTest {

    private fun newDb(): FintrackerDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FintrackerDatabase.Schema.create(driver)
        return createFintrackerDatabase(driver)
    }

    private fun rule(id: String, deleted: Boolean = false, modifiedAt: Long = 1L) = RecurringRule(
        id = id, amount = -50.0, description = "Abo $id", accountId = "acc",
        frequency = Frequency.MONTHLY, nextExecutionDate = LocalDate(2026, 1, 1),
        lastModifiedAt = modifiedAt, deleted = deleted
    )

    private fun booking(
        id: String, ruleId: String?, status: BookingStatus, modifiedAt: Long = 1L
    ) = Booking(
        id = id, accountId = "acc", amount = -50.0, description = "Buchung $id",
        timestamp = 1_000L, recurringRuleId = ruleId, status = status, lastModifiedAt = modifiedAt
    )

    // ── Rules ──────────────────────────────────────────────────────────────────

    @Test
    fun rules_saveAndLoadRoundTrip() {
        val repo = SqlRecurringRuleRepository(newDb())
        repo.saveRules(listOf(rule("r1"), rule("r2")))

        assertEquals(setOf("r1", "r2"), repo.loadRules().map { it.id }.toSet())
    }

    @Test
    fun rules_softDeleteHidesFromLiveButKeepsSyncTombstone() {
        val repo = SqlRecurringRuleRepository(newDb())
        repo.saveRules(listOf(rule("r1")))

        repo.softDeleteRule("r1", deletedAt = 12345L)

        assertTrue(repo.loadRules().isEmpty(), "Soft-deleted rule must not appear in the live list")
        val tombstone = repo.loadAllRulesForSync().single { it.id == "r1" }
        assertTrue(tombstone.deleted)
        assertEquals(12345L, tombstone.lastModifiedAt)
    }

    @Test
    fun rules_saveDoesNotResurrectTombstones() {
        val repo = SqlRecurringRuleRepository(newDb())
        repo.saveRules(listOf(rule("r1")))
        repo.softDeleteRule("r1", deletedAt = 500L)

        // Save a fresh live list that no longer contains r1 (as the UI would).
        repo.saveRules(listOf(rule("r2")))

        // r1's tombstone must survive; live list has only r2.
        assertEquals(setOf("r2"), repo.loadRules().map { it.id }.toSet())
        val all = repo.loadAllRulesForSync().associateBy { it.id }
        assertTrue(all.getValue("r1").deleted, "r1 tombstone must survive the save")
        assertFalse(all.getValue("r2").deleted)
    }

    // ── Bookings: the DKB invariant ────────────────────────────────────────────

    @Test
    fun deletingRuleScheduledBookings_keepsPostedHistory() {
        // Both repos must share the same database instance.
        val db = newDb()
        val rules = SqlRecurringRuleRepository(db)
        val bookings = SqlBookingRepository(db)

        rules.saveRules(listOf(rule("r1")))
        bookings.saveBookings(
            listOf(
                booking("posted", ruleId = "r1", status = BookingStatus.POSTED),
                booking("scheduled", ruleId = "r1", status = BookingStatus.SCHEDULED)
            )
        )

        // Simulate the DKB-safe rule deletion: remove the rule + its SCHEDULED
        // occurrence, but never the POSTED history.
        rules.softDeleteRule("r1", deletedAt = 999L)
        bookings.softDeleteBooking("scheduled", deletedAt = 999L)

        // The posted booking is still live; the scheduled one is gone from the live list.
        val live = bookings.loadBookings().map { it.id }.toSet()
        assertTrue("posted" in live, "Posted history must survive rule deletion")
        assertFalse("scheduled" in live)

        // Both remain as sync records; only the scheduled one is a tombstone.
        val sync = bookings.loadAllBookingsForSync().associateBy { it.id }
        assertFalse(sync.getValue("posted").deleted)
        assertTrue(sync.getValue("scheduled").deleted)
    }

    @Test
    fun bookings_saveDoesNotResurrectTombstones() {
        val db = newDb()
        val repo = SqlBookingRepository(db)
        repo.saveBookings(listOf(booking("b1", ruleId = null, status = BookingStatus.POSTED)))
        repo.softDeleteBooking("b1", deletedAt = 500L)

        repo.saveBookings(listOf(booking("b2", ruleId = null, status = BookingStatus.POSTED)))

        assertEquals(setOf("b2"), repo.loadBookings().map { it.id }.toSet())
        val all = repo.loadAllBookingsForSync().associateBy { it.id }
        assertTrue(all.getValue("b1").deleted)
        assertFalse(all.getValue("b2").deleted)
    }

    // ── Categories: tombstones (issue #77) ─────────────────────────────────────
    //
    // A fresh repository seeds the eleven default categories, so these tests always
    // scope their assertions to the ids they created themselves.

    private fun category(id: String, name: String, deleted: Boolean = false) = Category(
        id = id, name = name, iconName = "MoreHoriz", color = 0xFF8D6E63,
        isDefault = false, lastModifiedAt = 1L, deleted = deleted
    )

    // ── Categories: the seed must lose every merge (issue #108) ────────────────

    @Test
    fun categories_seedIsDatedAncientSoItNeverWinsAMerge() {
        // The bug this pins down was measured, not feared: a server restored from
        // empty stamped its eleven defaults with the restore's own date, and they
        // then beat the incoming versions from the device holding the real data.
        val repo = SqlCategoryRepository(newDb())

        val seeded = repo.loadCategories().filter { it.id.startsWith("default-") }

        assertTrue(seeded.isNotEmpty(), "a fresh repository is expected to seed defaults")
        seeded.forEach {
            assertEquals(
                SEED_LAST_MODIFIED_AT, it.lastModifiedAt,
                "${it.id} must carry the seed timestamp, or it outranks real data",
            )
        }
    }

    @Test
    fun categories_seedSurvivesTheSaveThatWouldStampItWithNow() {
        // The trap, and the reason the constant is 1 rather than 0: saveCategories
        // reads 0L as "no timestamp set" and replaces it with the current time. A
        // seed dated to the epoch would land in the database as brand new and the
        // fix would silently do nothing — visible only at the next restore.
        val repo = SqlCategoryRepository(newDb())

        // Save the live list straight back, as the management dialog does after
        // any edit. This is the path that rewrites timestamps.
        repo.saveCategories(repo.loadCategories())

        val settlement = repo.loadCategories().single { it.name.trim() == "Kreditkartenabrechnung" }
        assertEquals(
            SEED_LAST_MODIFIED_AT, settlement.lastModifiedAt,
            "an untouched default must not be re-dated by an unrelated save",
        )
    }

    @Test
    fun categories_editingASeededOneGivesItARealTimestamp() {
        // The other half of the rule. Losing every merge is only correct for a
        // default nobody touched; once it is edited the change has to win.
        val repo = SqlCategoryRepository(newDb())
        val before = repo.loadCategories().single { it.id == "default-lebensmittel" }

        repo.saveCategories(
            repo.loadCategories().map {
                if (it.id == "default-lebensmittel") it.copy(name = "Einkauf", lastModifiedAt = 0L) else it
            }
        )

        val after = repo.loadCategories().single { it.id == "default-lebensmittel" }
        assertEquals("Einkauf", after.name)
        assertTrue(
            after.lastModifiedAt > before.lastModifiedAt,
            "a rename must outrank the seed, otherwise it cannot propagate",
        )
    }

    @Test
    fun categories_softDeleteHidesFromLiveButKeepsSyncTombstone() {
        val repo = SqlCategoryRepository(newDb())
        repo.saveCategories(repo.loadCategories() + category("c1", "Hobbys"))

        repo.softDeleteCategory("c1", deletedAt = 12345L)

        assertTrue(repo.loadCategories().none { it.id == "c1" }, "must not appear in the live list")
        val tombstone = repo.loadAllCategoriesForSync().single { it.id == "c1" }
        assertTrue(tombstone.deleted)
        assertEquals(12345L, tombstone.lastModifiedAt)
    }

    @Test
    fun categories_saveDoesNotResurrectTombstones() {
        val repo = SqlCategoryRepository(newDb())
        repo.saveCategories(repo.loadCategories() + category("c1", "Hobbys"))
        repo.softDeleteCategory("c1", deletedAt = 500L)

        // Save the live list back, as the management dialog does after any edit.
        repo.saveCategories(repo.loadCategories())

        assertTrue(repo.loadCategories().none { it.id == "c1" })
        val tombstone = repo.loadAllCategoriesForSync().single { it.id == "c1" }
        assertTrue(tombstone.deleted, "the tombstone must survive an unrelated save")
    }

    @Test
    fun categories_saveResurrectsWhenTheCallerPassesTheIdBack() {
        // Deliberate difference from the account repository: an explicit save wins over
        // the tombstone, so a category the user restores keeps the id it had rather than
        // being blocked by its own deletion.
        val repo = SqlCategoryRepository(newDb())
        repo.saveCategories(repo.loadCategories() + category("c1", "Hobbys"))
        repo.softDeleteCategory("c1", deletedAt = 500L)

        repo.saveCategories(repo.loadCategories() + category("c1", "Hobbys"))

        assertTrue(repo.loadCategories().any { it.id == "c1" }, "explicit save must win")
        assertFalse(repo.loadAllCategoriesForSync().single { it.id == "c1" }.deleted)
    }

    @Test
    fun categories_deletingADuplicateLeavesTheSettlementNameIntact() {
        // The issue #77 cleanup: two categories carry the name "Kreditkartenabrechnung".
        // Bookings reference categories by name, not by id — so removing the redundant row
        // must leave the name behind, or six bookings point at nothing.
        val repo = SqlCategoryRepository(newDb())
        val duplicate = category("dupe", "Kreditkartenabrechnung")
        repo.saveCategories(repo.loadCategories() + duplicate)

        repo.softDeleteCategory("dupe", deletedAt = 900L)

        val live = repo.loadCategories()
        assertEquals(
            1, live.count { it.name == "Kreditkartenabrechnung" },
            "exactly one settlement category must remain, got ${live.filter { it.name == "Kreditkartenabrechnung" }}",
        )
        assertTrue(live.any { it.id == "default-kreditkartenabrechnung" }, "the seeded one is the keeper")
    }
}
