package io.github.willywonka644.fintracker.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.SqlAccountRepository
import io.github.willywonka644.fintracker.SqlBookingRepository
import io.github.willywonka644.fintracker.SqlRecurringRuleRepository
import io.github.willywonka644.fintracker.SqlSettingsRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import io.github.willywonka644.fintracker.sync.SyncExporter
import io.github.willywonka644.fintracker.sync.SyncImporter
import io.github.willywonka644.fintracker.sync.SyncWriter
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #88, step 3. `/sync/push` — the write path, and the one where mistakes
 * cost money.
 *
 * The payloads a "client" sends are produced by exporting from a **second**
 * in-memory database rather than written by hand. That way the test exercises
 * the real round trip and cannot drift from the payload format if it ever
 * changes.
 */
class SyncPushTest {

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

    /** A payload as a client would send it: whatever [fill] puts in its database. */
    private fun clientPayload(fill: (FintrackerDatabase) -> Unit): String {
        val clientDb = newDb()
        fill(clientDb)
        return exporterOver(clientDb).export()
    }

    private fun account(id: String, name: String, modifiedAt: Long) =
        Account(id = id, name = name, type = AccountType.GIRO, lastModifiedAt = modifiedAt)

    /** The plain case: what a client pushes is what a later pull hands back. */
    @Test
    fun pushedDataComesBackOnPull() = testApplication {
        val serverDb = newDb()
        application { syncRoutes(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb)) }

        val payload = clientPayload { db ->
            SqlAccountRepository(db).saveAccounts(listOf(account("acc-1", "Musterkonto", 100L)))
            SqlBookingRepository(db).saveBookings(
                listOf(
                    Booking(
                        id = "b-1",
                        accountId = "acc-1",
                        amount = -12.34,
                        description = "Musterbuchung",
                        timestamp = 1_000L,
                        status = BookingStatus.POSTED,
                        lastModifiedAt = 100L,
                    )
                )
            )
        }

        val pushed = client.post("/sync/push") { setBody(payload) }
        assertEquals(HttpStatusCode.OK, pushed.status, pushed.bodyAsText())

        val pulled = client.get("/sync/pull").bodyAsText()
        assertTrue(pulled.contains("Musterkonto"), "the account did not survive the push: $pulled")
        assertTrue(pulled.contains("Musterbuchung"), "the booking did not survive the push: $pulled")
    }

    /**
     * The merge rule the whole design rests on. An older copy must lose, or a
     * client that has been offline for a week would undo everything newer just
     * by syncing.
     */
    @Test
    fun anOlderCopyDoesNotOverwriteANewerOne() = testApplication {
        val serverDb = newDb()
        SqlAccountRepository(serverDb).saveAccounts(listOf(account("acc-1", "Neuer Name", 200L)))
        application { syncRoutes(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb)) }

        val stalePayload = clientPayload { db ->
            SqlAccountRepository(db).saveAccounts(listOf(account("acc-1", "Alter Name", 100L)))
        }

        assertEquals(HttpStatusCode.OK, client.post("/sync/push") { setBody(stalePayload) }.status)

        val pulled = client.get("/sync/pull").bodyAsText()
        assertTrue(pulled.contains("Neuer Name"), "the newer name was lost: $pulled")
        assertFalse(pulled.contains("Alter Name"), "the stale copy won: $pulled")
    }

    /**
     * A deletion has to travel. Without tombstones in the merge base an incoming
     * live copy looks new and brings the record back — which is what the
     * tombstones were introduced for in the first place.
     */
    @Test
    fun anIncomingTombstoneDeletesTheLiveRowAndItDoesNotComeBack() = testApplication {
        val serverDb = newDb()
        val serverAccounts = SqlAccountRepository(serverDb)
        serverAccounts.saveAccounts(listOf(account("acc-gone", "Wird geloescht", 100L)))
        application { syncRoutes(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb)) }

        val deletionPayload = clientPayload { db ->
            val clientAccounts = SqlAccountRepository(db)
            clientAccounts.saveAccounts(listOf(account("acc-gone", "Wird geloescht", 100L)))
            clientAccounts.softDeleteAccount("acc-gone", deletedAt = 200L)
        }

        assertEquals(HttpStatusCode.OK, client.post("/sync/push") { setBody(deletionPayload) }.status)

        assertTrue(
            serverAccounts.loadAccounts().none { it.id == "acc-gone" },
            "the account is still live on the server",
        )
        assertTrue(
            serverAccounts.loadAllAccountsForSync().any { it.id == "acc-gone" && it.deleted },
            "the tombstone was not kept, so the deletion cannot propagate further",
        )
    }

    /**
     * A malformed payload is the client's fault; a failed write would be the
     * server's. They must not collapse into one status, because over a mobile
     * connection the difference decides whether retrying can help.
     */
    @Test
    fun aMalformedPayloadIsRejectedWithoutTouchingTheDatabase() = testApplication {
        val serverDb = newDb()
        SqlAccountRepository(serverDb).saveAccounts(listOf(account("acc-1", "Unberuehrt", 100L)))
        application { syncRoutes(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb)) }

        val response = client.post("/sync/push") { setBody("{ das ist kein JSON") }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "Unberuehrt",
            SqlAccountRepository(serverDb).loadAccounts().single().name,
            "the database changed despite a rejected payload",
        )
    }

    @Test
    fun aPayloadOfAnUnknownVersionIsRejected() = testApplication {
        val serverDb = newDb()
        application { syncRoutes(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb)) }

        val body = """
            {"version":"99.0","exportedAt":"2026-08-17T12:00:00Z","deviceId":"d",
             "accounts":[],"bookings":[],"categories":[],"recurringRules":[]}
        """.trimIndent()

        val response = client.post("/sync/push") { setBody(body) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("UNKNOWN_VERSION", response.bodyAsText())
    }

    /** The counts a client would show the user after a push. */
    @Test
    fun theResponseReportsWhatTheMergeDid() = testApplication {
        val serverDb = newDb()
        application { syncRoutes(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb)) }

        val payload = clientPayload { db ->
            SqlAccountRepository(db).saveAccounts(listOf(account("acc-1", "Musterkonto", 100L)))
        }

        val body = client.post("/sync/push") { setBody(payload) }.bodyAsText()

        assertTrue(body.contains("\"newCount\""), "response is not a push result: $body")
        assertTrue(body.contains("\"warnings\""), "response is not a push result: $body")
    }
}
