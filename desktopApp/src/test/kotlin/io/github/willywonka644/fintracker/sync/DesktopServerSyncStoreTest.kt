package io.github.willywonka644.fintracker.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.willywonka644.fintracker.SqlAccountRepository
import io.github.willywonka644.fintracker.SqlBookingRepository
import io.github.willywonka644.fintracker.SqlRecurringRuleRepository
import io.github.willywonka644.fintracker.SqlSettingsRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #107, asked for in #89 and written down in the KDoc of
 * [DesktopServerSyncStore] long before it existed.
 *
 * The invariant: the API key must never travel in a sync payload. If it did, it
 * would ride to the server on the next exchange and from there onto every other
 * device — quietly, because nothing along that path looks at it.
 *
 * It holds today. What was missing is anything that keeps it holding. The
 * failure mode is not someone deciding to send the key; it is that
 * [SyncExporter] already takes an `ISettingsRepository` — today only to read the
 * device id — and the key lives in that same table on this platform. Adding a
 * second field from settings would be a plausible-looking change that leaks it,
 * and nothing would object.
 *
 * These tests belong here rather than next to the exporter, for the reason the
 * KDoc gives: the exporter does nothing suspicious. What makes it matter is
 * *where this store puts the key*. On Android it lives in the DataStore, out of
 * the exporter's reach entirely.
 */
class DesktopServerSyncStoreTest {

    /** Unmistakable on purpose: a substring search for it cannot match by accident. */
    private val apiKey = "SCHLUESSEL-DER-NIEMALS-WANDERN-DARF-8f3a1c"

    private fun newDb(): FintrackerDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FintrackerDatabase.Schema.create(driver)
        return createFintrackerDatabase(driver)
    }

    private fun exporterOver(db: FintrackerDatabase) = SyncExporter(
        accountRepository = SqlAccountRepository(db),
        bookingRepository = SqlBookingRepository(db),
        categoryRepository = SqlCategoryRepository(db),
        recurringRuleRepository = SqlRecurringRuleRepository(db),
        settingsRepository = SqlSettingsRepository(db),
    )

    @Test
    fun theApiKeyDoesNotTravelInTheSyncPayload() {
        val db = newDb()
        DesktopServerSyncStore(SqlSettingsRepository(db)).save("http://10.0.0.1:8080", apiKey)

        val payload = exporterOver(db).export()

        assertFalse(
            payload.contains(apiKey),
            "the API key must never leave in a payload — it would reach every device",
        )
    }

    /**
     * The server address is not a secret, but it is not the other devices'
     * business either: each one is configured by hand, and a pushed address
     * would overwrite a working setup with one that may be wrong for that
     * device.
     */
    @Test
    fun theServerAddressDoesNotTravelEither() {
        val db = newDb()
        DesktopServerSyncStore(SqlSettingsRepository(db)).save("http://10.0.0.1:8080", apiKey)

        assertFalse(exporterOver(db).export().contains("10.0.0.1"))
    }

    /**
     * The wider net. The test above catches the key by name; this one catches
     * anything that is added to the payload later without someone thinking about
     * it — settings among them.
     *
     * Yes, this fails when the payload is extended on purpose. That is the point:
     * extending it should be a deliberate act, and this is the line that makes
     * someone look.
     */
    @Test
    fun thePayloadCarriesOnlyTheFieldsItIsMeantTo() {
        val db = newDb()
        DesktopServerSyncStore(SqlSettingsRepository(db)).save("http://10.0.0.1:8080", apiKey)

        val fields = Json.parseToJsonElement(exporterOver(db).export()).jsonObject.keys

        assertEquals(
            setOf("version", "exportedAt", "deviceId", "accounts", "bookings", "categories", "recurringRules"),
            fields,
            "a new top-level field reached the payload — is it meant to go to every device?",
        )
    }

    /**
     * Guards the test above from passing for the wrong reason: if the store did
     * not write anything, no payload could contain it.
     */
    @Test
    fun theKeyIsActuallyStoredSoTheOtherTestsMeanSomething() {
        val store = DesktopServerSyncStore(SqlSettingsRepository(newDb()))

        store.save("http://10.0.0.1:8080", apiKey)

        assertEquals(apiKey, store.apiKey)
        assertTrue(store.serverUrl.isNotBlank())
    }

    /**
     * #114 — the desktop has to remember when it last synced, across restarts.
     *
     * Without it the unsynced counter started at zero on every launch and reported
     * "nothing outstanding" even with unsynced rows sitting in the database — and the
     * question asked when closing the window stayed silent with it.
     */
    @Test
    fun aFreshInstallHasNeverSynced() {
        val store = DesktopServerSyncStore(SqlSettingsRepository(newDb()))

        // 0 rather than "now": everything in the database is then newer than the last
        // sync, which is exactly right for a device that has not synced yet.
        assertEquals(0L, store.lastSyncedAt)
    }

    @Test
    fun theSyncTimestampSurvivesANewStoreOverTheSameDatabase() {
        // A new store over the same database is what a restart looks like from here.
        val db = newDb()
        DesktopServerSyncStore(SqlSettingsRepository(db)).lastSyncedAt = 1_700_000_000_000

        assertEquals(1_700_000_000_000, DesktopServerSyncStore(SqlSettingsRepository(db)).lastSyncedAt)
    }

    @Test
    fun theSyncTimestampDoesNotTravelEither() {
        // Same reasoning as the key and the address: it says something about *this*
        // device, and pushed to the server it would claim the same for every other one.
        val db = newDb()
        DesktopServerSyncStore(SqlSettingsRepository(db)).lastSyncedAt = 1_700_000_000_000

        val payload = exporterOver(db).export()

        assertFalse(
            payload.contains("1700000000000"),
            "the last-synced stamp must not appear in the payload: $payload",
        )
        assertFalse(payload.contains("server_sync_last_synced_at"), payload)
    }
}
