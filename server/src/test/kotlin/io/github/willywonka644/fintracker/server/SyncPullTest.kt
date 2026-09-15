package io.github.willywonka644.fintracker.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.SqlAccountRepository
import io.github.willywonka644.fintracker.SqlBookingRepository
import io.github.willywonka644.fintracker.SqlRecurringRuleRepository
import io.github.willywonka644.fintracker.SqlSettingsRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.DatabaseDriverFactory
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import io.github.willywonka644.fintracker.sync.SyncExporter
import io.github.willywonka644.fintracker.sync.SyncImporter
import io.github.willywonka644.fintracker.sync.SyncWriter
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Issue #88, step 2. `/sync/pull` over a database the test owns.
 *
 * The dependencies are handed to the route rather than fetched from a global,
 * which is what lets these run against an in-memory database instead of whatever
 * file the server would open in production.
 */
class SyncPullTest {

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

    private fun importerOver(db: FintrackerDatabase) = SyncImporter(
        accountRepository = SqlAccountRepository(db),
        bookingRepository = SqlBookingRepository(db),
        categoryRepository = SqlCategoryRepository(db),
        recurringRuleRepository = SqlRecurringRuleRepository(db),
    )

    @Test
    fun pullAnswersWithASyncPayload() = testApplication {
        val db = newDb()
        application { syncRoutes(exporterOver(db), importerOver(db), SyncWriter(db)) }

        val response = client.get("/sync/pull")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        for (key in listOf("version", "exportedAt", "deviceId", "accounts", "bookings", "categories", "recurringRules")) {
            assertTrue(body.contains("\"$key\""), "pull is missing $key: $body")
        }
    }

    /**
     * The assertion that actually matters: the route hands out what is in the
     * server's own database, not an empty shell that merely has the right shape.
     */
    @Test
    fun pullReturnsWhatIsInTheServersDatabase() = testApplication {
        val db = newDb()
        SqlAccountRepository(db).saveAccounts(
            listOf(Account(id = "acc-1", name = "Musterkonto", type = AccountType.GIRO, lastModifiedAt = 42L))
        )
        application { syncRoutes(exporterOver(db), importerOver(db), SyncWriter(db)) }

        val body = client.get("/sync/pull").bodyAsText()

        assertTrue(body.contains("Musterkonto"), "the exported account is missing: $body")
    }

    /**
     * Tombstones have to leave the server too. A deletion that stays behind
     * would be undone by the next client that still holds the live row — the
     * failure mode the tombstones exist to prevent.
     */
    @Test
    fun pullIncludesDeletedRowsSoDeletionsPropagate() = testApplication {
        val db = newDb()
        val accounts = SqlAccountRepository(db)
        accounts.saveAccounts(
            listOf(Account(id = "acc-gone", name = "Geloeschtes Konto", type = AccountType.SPARKONTO, lastModifiedAt = 1L))
        )
        accounts.softDeleteAccount("acc-gone", deletedAt = 99L)
        application { syncRoutes(exporterOver(db), importerOver(db), SyncWriter(db)) }

        val body = client.get("/sync/pull").bodyAsText()

        assertTrue(body.contains("acc-gone"), "the tombstone is missing from the export: $body")
    }

    /**
     * Guards a hazard rather than a behaviour: the server must never open the
     * desktop app's database. On a machine running both, that would have two
     * connections writing one file and a device syncing against itself.
     */
    @Test
    fun theServerDatabaseIsNotTheDesktopDatabase() {
        assertNotEquals(
            DatabaseDriverFactory.defaultDatabaseFile().absolutePath,
            ServerServices.defaultDatabaseFile().absolutePath,
        )
    }
}
