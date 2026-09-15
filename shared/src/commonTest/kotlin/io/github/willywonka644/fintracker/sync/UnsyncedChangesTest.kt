package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the overview's sync button is allowed to claim (#110).
 *
 * The point of deriving this instead of counting edits in memory is that it survives an
 * app restart, so these tests are mostly about the boundary and about the records that
 * are easy to forget: tombstones, and the state before the first sync ever ran.
 */
class UnsyncedChangesTest {

    private fun account(at: Long) =
        Account(id = "a$at", name = "Konto", type = AccountType.GIRO, lastModifiedAt = at)

    private fun booking(at: Long, deleted: Boolean = false) = Booking(
        id = "b$at", accountId = "a", amount = -1.0, description = "x",
        timestamp = 0L, lastModifiedAt = at, deleted = deleted,
    )

    private fun rule(at: Long) = RecurringRule(
        id = "r$at", amount = -1.0, description = "x", accountId = "a",
        frequency = Frequency.MONTHLY, nextExecutionDate = LocalDate(2026, 1, 1),
        lastModifiedAt = at,
    )

    private fun category(at: Long) =
        Category(id = "c$at", name = "K", iconName = "Star", color = 0L, lastModifiedAt = at)

    private fun count(
        lastSyncedAt: Long,
        accounts: List<Account> = emptyList(),
        bookings: List<Booking> = emptyList(),
        rules: List<RecurringRule> = emptyList(),
        categories: List<Category> = emptyList(),
    ) = countUnsyncedSince(lastSyncedAt, accounts, bookings, rules, categories)

    @Test
    fun countsOnlyWhatChangedAfterTheLastSync() {
        val n = count(
            lastSyncedAt = 1000L,
            bookings = listOf(booking(999L), booking(1001L), booking(2000L)),
        )
        assertEquals(2, n)
    }

    @Test
    fun aRecordSavedInTheSameMillisecondCountsAsSynced() {
        // The marker is written after the sync wrote its rows, so equal means "was part
        // of it". Using >= here would make every sync leave its own changes outstanding.
        assertEquals(0, count(lastSyncedAt = 1000L, bookings = listOf(booking(1000L))))
    }

    @Test
    fun tombstonesCount() {
        // A deletion still has to travel, and it is the change whose loss is least
        // visible — the callers hand in the same lists the exporter sends.
        assertEquals(1, count(lastSyncedAt = 1000L, bookings = listOf(booking(2000L, deleted = true))))
    }

    @Test
    fun addsUpAcrossAllFourKinds() {
        val n = count(
            lastSyncedAt = 100L,
            accounts = listOf(account(200L), account(50L)),
            bookings = listOf(booking(200L)),
            rules = listOf(rule(300L)),
            categories = listOf(category(400L), category(99L)),
        )
        assertEquals(4, n)
    }

    @Test
    fun beforeTheFirstSyncEverythingIsOutstanding() {
        // No marker stored yet means 0, and every record is newer than that.
        val n = count(
            lastSyncedAt = 0L,
            accounts = listOf(account(1L)),
            bookings = listOf(booking(1L)),
            rules = listOf(rule(1L)),
            categories = listOf(category(1L)),
        )
        assertEquals(4, n)
    }

    @Test
    fun aRecordWithoutATimestampIsNotReportedAfterASync() {
        // lastModifiedAt defaults to 0; seeded categories deliberately carry a fixed
        // date in the past (#108) and must not show up as outstanding forever.
        assertEquals(0, count(lastSyncedAt = 1000L, categories = listOf(category(0L), category(1L))))
    }

    @Test
    fun nothingStoredMeansNothingToReport() {
        assertEquals(0, count(lastSyncedAt = 1000L))
    }
}
