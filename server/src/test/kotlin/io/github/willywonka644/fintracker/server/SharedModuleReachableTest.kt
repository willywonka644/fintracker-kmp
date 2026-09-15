package io.github.willywonka644.fintracker.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.willywonka644.fintracker.SqlAccountRepository
import io.github.willywonka644.fintracker.SqlBookingRepository
import io.github.willywonka644.fintracker.SqlRecurringRuleRepository
import io.github.willywonka644.fintracker.SqlSettingsRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import io.github.willywonka644.fintracker.sync.SyncExporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #88. Proves the claim the health test does **not** prove: that this
 * plain-JVM module really reaches into the shared multiplatform library, at
 * runtime and not just on the compile classpath.
 *
 * The distinction matters. `implementation(project(":shared"))` makes Gradle
 * pick a variant when it assembles the classpath, so a missing jvm target would
 * already fail the build — but nothing in `ServerApp.kt` imports from `shared`,
 * so the generated database code, the JDBC driver and the sync machinery were
 * never loaded or executed. That is exactly the part that has to work before
 * `/sync/pull` and `/sync/push` are worth writing.
 */
class SharedModuleReachableTest {

    private fun newDb(): FintrackerDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FintrackerDatabase.Schema.create(driver)
        return createFintrackerDatabase(driver)
    }

    /**
     * Asserted here as well as in the shared suite, because the server creates its
     * own database file on the Pi and has to create it at the same version the
     * clients speak. Raise both together when a .sqm is added — this assertion
     * failing after a migration means the server would hand out an older schema
     * than the devices expect.
     */
    @Test
    fun theServerSeesTheCurrentSchemaVersion() {
        assertEquals(6L, FintrackerDatabase.Schema.version)
    }

    @Test
    fun theServerCanOpenADatabaseOfItsOwn() {
        val db = newDb()

        // Empty, but reachable: the generated queries run and the driver works.
        assertEquals(0, SqlAccountRepository(db).loadAccounts().size)
    }

    /**
     * The whole point of reusing `shared`: the server exports with the same code
     * the clients do, so the merge rules exist once in the project instead of
     * twice (see the design note on #88).
     */
    @Test
    fun theServerCanExportWithTheSharedSyncMachinery() {
        val db = newDb()
        val exporter = SyncExporter(
            accountRepository = SqlAccountRepository(db),
            bookingRepository = SqlBookingRepository(db),
            categoryRepository = SqlCategoryRepository(db),
            recurringRuleRepository = SqlRecurringRuleRepository(db),
            settingsRepository = SqlSettingsRepository(db),
        )

        val json = exporter.export()

        // Checked against the field names of SyncPayload rather than just "not
        // empty", so the test cannot pass on any old string.
        for (key in listOf("version", "exportedAt", "deviceId", "accounts", "bookings", "categories", "recurringRules")) {
            assertTrue(json.contains("\"$key\""), "export is missing $key: $json")
        }
    }
}
