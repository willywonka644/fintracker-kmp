package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category

/**
 * How many local changes have not reached the server yet (#110).
 *
 * The overview used to keep this as a counter in memory, incremented on every edit and
 * reset on every sync. That was enough for a line of text one scrolls past; it is not
 * enough for a button that permanently claims a state, because the counter starts at
 * zero on every app start and would then report "nothing outstanding" while something
 * is. Derived from the data instead, against the timestamp of the last successful sync.
 *
 * **Tombstones count.** A deletion is a change that still has to travel, and it is the
 * one whose loss is least visible — so the callers hand in the `loadAll…ForSync()`
 * lists, the same ones the exporter sends, not the live ones.
 */
fun countUnsyncedSince(
    lastSyncedAt: Long,
    accounts: List<Account>,
    bookings: List<Booking>,
    recurringRules: List<RecurringRule>,
    categories: List<Category>,
): Int =
    accounts.count { it.lastModifiedAt > lastSyncedAt } +
        bookings.count { it.lastModifiedAt > lastSyncedAt } +
        recurringRules.count { it.lastModifiedAt > lastSyncedAt } +
        categories.count { it.lastModifiedAt > lastSyncedAt }
