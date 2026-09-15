package io.github.willywonka644.fintracker

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 6.5 — the upgrade path from an old database to the current one.
 *
 * This is the one failure that cannot be repaired after the fact: a broken
 * migration runs on the user's own data during an update, and by the time
 * anything looks wrong the original rows are already gone. Every other test in
 * this project guards behaviour that can be fixed in the next release.
 *
 * `Schema.create()` always builds the newest schema, so it cannot exercise this
 * path. The v1 tables are therefore written out by hand below — the current
 * definitions minus everything the .sqm files add.
 */
class DatabaseMigrationTest {

    private val giroType = AccountType.GIRO.name
    private val posted = BookingStatus.POSTED.name
    private val manual = BookingSource.MANUAL.name
    private val monthly = Frequency.MONTHLY.name

    /**
     * The schema as it shipped before any migration existed: no lastModifiedAt,
     * no deleted, no app_settings.
     */
    private fun openV1Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        listOf(
            """
            CREATE TABLE account (
                id              TEXT    NOT NULL PRIMARY KEY,
                name            TEXT    NOT NULL,
                type            TEXT    NOT NULL,
                billingStartDay INTEGER,
                spendingLimit   REAL
            )
            """,
            """
            CREATE TABLE booking (
                id                 TEXT    NOT NULL PRIMARY KEY,
                accountId          TEXT    NOT NULL,
                amount             REAL    NOT NULL,
                description        TEXT    NOT NULL,
                timestamp          INTEGER NOT NULL,
                category           TEXT,
                merchantName       TEXT,
                rawDescription     TEXT,
                recurringRuleId    TEXT,
                status             TEXT    NOT NULL,
                effectiveDate      INTEGER,
                source             TEXT    NOT NULL,
                importBatchId      TEXT,
                paymentReference   TEXT,
                attachmentPath     TEXT,
                installmentGroupId TEXT,
                ocrRawText         TEXT,
                isVerified         INTEGER NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE category (
                id        TEXT    NOT NULL PRIMARY KEY,
                name      TEXT    NOT NULL,
                iconName  TEXT    NOT NULL,
                color     INTEGER NOT NULL,
                isDefault INTEGER NOT NULL DEFAULT 0
            )
            """,
            """
            CREATE TABLE recurringRule (
                id                  TEXT    NOT NULL PRIMARY KEY,
                amount              REAL    NOT NULL,
                description         TEXT    NOT NULL,
                accountId           TEXT    NOT NULL,
                frequency           TEXT    NOT NULL,
                nextExecutionDate   TEXT    NOT NULL,
                remainingExecutions INTEGER,
                category            TEXT,
                excludedDates       TEXT    NOT NULL DEFAULT ''
            )
            """,
        ).forEach { driver.execute(null, it.trimIndent(), 0) }
        return driver
    }

    /** A small but representative set of rows, as a real user would have had. */
    private fun seedV1Data(driver: SqlDriver) {
        driver.execute(
            null,
            "INSERT INTO account VALUES ('acc-1', 'Girokonto', '$giroType', 1, NULL)", 0
        )
        driver.execute(
            null,
            "INSERT INTO booking (id, accountId, amount, description, timestamp, status, source, isVerified) " +
                "VALUES ('b1', 'acc-1', 2000.0, 'Gehalt', 1600000000000, '$posted', '$manual', 0)", 0
        )
        driver.execute(
            null,
            "INSERT INTO booking (id, accountId, amount, description, timestamp, status, source, isVerified) " +
                "VALUES ('b2', 'acc-1', -799.5, 'Miete', 1600000000000, '$posted', '$manual', 0)", 0
        )
        driver.execute(
            null,
            "INSERT INTO category VALUES ('cat-1', 'Wohnen', 'Home', 4294951168, 1)", 0
        )
        driver.execute(
            null,
            "INSERT INTO recurringRule (id, amount, description, accountId, frequency, nextExecutionDate) " +
                "VALUES ('r1', -799.5, 'Miete', 'acc-1', '$monthly', '2026-09-01')", 0
        )
    }

    /** v1 database with data, migrated all the way to the current version. */
    private fun migratedDatabase(): FintrackerDatabase {
        val driver = openV1Database()
        seedV1Data(driver)
        FintrackerDatabase.Schema.migrate(driver, 1L, FintrackerDatabase.Schema.version)
        return createFintrackerDatabase(driver)
    }

    @Test
    fun schemaVersionMatchesTheMigrationFiles() {
        // 1.sqm .. 5.sqm ⇒ target version 6. If this ever changes without a
        // new .sqm, the upgrade path has a hole in it.
        assertEquals(6L, FintrackerDatabase.Schema.version)
    }

    @Test
    fun everyRowSurvivesTheUpgradeWithItsValues() {
        val db = migratedDatabase()

        val account = SqlAccountRepository(db).loadAccounts().single()
        assertEquals("acc-1", account.id)
        assertEquals("Girokonto", account.name)
        assertEquals(AccountType.GIRO, account.type)
        assertEquals(1, account.billingStartDay)

        val bookings = SqlBookingRepository(db).loadBookings().sortedBy { it.id }
        assertEquals(listOf("b1", "b2"), bookings.map { it.id })
        assertEquals(2000.0, bookings[0].amount, 0.001)
        assertEquals(-799.5, bookings[1].amount, 0.001)
        assertEquals("Miete", bookings[1].description)

        val rule = SqlRecurringRuleRepository(db).loadRules().single()
        assertEquals("r1", rule.id)
        assertEquals(Frequency.MONTHLY, rule.frequency)
        assertEquals(kotlinx.datetime.LocalDate(2026, 9, 1), rule.nextExecutionDate)
    }

    @Test
    fun nothingIsTombstonedByTheUpgrade() {
        val db = migratedDatabase()

        // selectAll filters on deleted = 0. Had the new column defaulted to 1,
        // the app would come up empty after an update while the rows sit there.
        assertEquals(1, SqlAccountRepository(db).loadAccounts().size)
        assertEquals(2, SqlBookingRepository(db).loadBookings().size)
        assertEquals(1, SqlRecurringRuleRepository(db).loadRules().size)

        // And the sync views agree — no row is hidden as a tombstone either.
        assertEquals(1, SqlAccountRepository(db).loadAllAccountsForSync().size)
        assertEquals(2, SqlBookingRepository(db).loadAllBookingsForSync().size)
        assertEquals(1, SqlRecurringRuleRepository(db).loadAllRulesForSync().size)

        // Categories joined the tombstone scheme in 4.sqm. The seeded "Wohnen" has to
        // survive it — and ensureSeeded() must not mistake the upgraded database for a
        // fresh one and bury it under the default set.
        val categories = SqlCategoryRepository(db).loadCategories()
        assertTrue(
            categories.any { it.id == "cat-1" && it.name == "Wohnen" },
            "expected the migrated category, got ${categories.map { it.id }}",
        )
        assertTrue(categories.none { it.deleted }, "the upgrade must not tombstone anything")
    }

    @Test
    fun lastModifiedAtIsBackfilledSoSyncCannotOverwriteOldRows() {
        val db = migratedDatabase()

        // 1.sqm stamps the current time into the new column. Left at 0, every
        // migrated row would lose the last-write-wins comparison against
        // anything the other device holds — silently, on the first sync.
        val stamps = buildList {
            addAll(SqlAccountRepository(db).loadAccounts().map { it.lastModifiedAt })
            addAll(SqlBookingRepository(db).loadBookings().map { it.lastModifiedAt })
            addAll(SqlRecurringRuleRepository(db).loadRules().map { it.lastModifiedAt })
        }
        assertEquals(4, stamps.size, "one account, two bookings, one rule")
        assertTrue(stamps.all { it > 0L }, "expected a backfilled timestamp, got $stamps")
    }

    @Test
    fun transferGroupIdArrivesEmptyAndIsWritable() {
        // 5.sqm adds the column a transfer's two halves are tied together by. An
        // upgraded row cannot know about a transfer, so it has to come through as
        // null rather than as anything the analytics would then act on.
        val db = migratedDatabase()
        val repo = SqlBookingRepository(db)

        val migrated = repo.loadBookings()
        assertTrue(
            migrated.all { it.transferGroupId == null },
            "an upgraded booking cannot belong to a transfer, got ${migrated.map { it.transferGroupId }}",
        )
        assertTrue(migrated.all { it.source == BookingSource.MANUAL })

        // saveBookings replaces the whole table, so both rows go back in.
        repo.saveBookings(
            migrated.map {
                if (it.id == "b1") it.copy(source = BookingSource.TRANSFER, transferGroupId = "grp-1") else it
            }
        )
        val written = repo.loadBookings().first { it.id == "b1" }
        assertEquals(BookingSource.TRANSFER, written.source)
        assertEquals("grp-1", written.transferGroupId)
        assertNull(repo.loadBookings().first { it.id == "b2" }.transferGroupId)
    }

    @Test
    fun theSettingsTableExistsAfterTheUpgrade() {
        val db = migratedDatabase()

        // app_settings arrives with 1.sqm and carries the PIN and the device id.
        val settings = SqlSettingsRepository(db)
        settings.set("pin_enabled", "true")
        assertEquals("true", settings.get("pin_enabled"))
        assertTrue(settings.getDeviceId().isNotBlank())
    }
}
