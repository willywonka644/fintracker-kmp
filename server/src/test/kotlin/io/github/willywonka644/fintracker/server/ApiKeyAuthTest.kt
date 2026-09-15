package io.github.willywonka644.fintracker.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
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
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.security.SecureRandom
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Issue #89. The third layer: no request without the key.
 *
 * The layer below it (VPN, #94) decides which *device* may connect at all; these
 * tests are about what happens once something is connected. The case that costs
 * money is `/sync/push`, because the merge decides by timestamp — a payload with
 * timestamps set into the future wins on every device at once. So the assertions
 * are not only "401 came back" but "and the database is unchanged": a rejection
 * that arrives after the write would look identical from the outside.
 *
 * The key is generated per test run. Not because a fixed test string would be
 * dangerous in itself, but because #89 asks for no key-shaped constant anywhere
 * in this repository, including obviously fake ones — those get copied.
 */
class ApiKeyAuthTest {

    private fun newKey(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
    }

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

    private fun account(id: String, name: String, modifiedAt: Long) =
        Account(id = id, name = name, type = AccountType.GIRO, lastModifiedAt = modifiedAt)

    /** A payload as a client would send it, exported from its own database. */
    private fun clientPayload(fill: (FintrackerDatabase) -> Unit): String {
        val clientDb = newDb()
        fill(clientDb)
        return exporterOver(clientDb).export()
    }

    /** A payload that would overwrite the server's account if it ever got through. */
    private fun intrudingPayload() = clientPayload { db ->
        SqlAccountRepository(db).saveAccounts(listOf(account("acc-1", "Eingeschleust", 9_999L)))
    }

    // ---- the write path ----

    @Test
    fun pushWithoutAKeyIsRejectedAndTheDatabaseIsUntouched() = testApplication {
        val serverDb = newDb()
        SqlAccountRepository(serverDb).saveAccounts(listOf(account("acc-1", "Unberuehrt", 100L)))
        application {
            fintrackerServer(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb), newKey())
        }

        val response = client.post("/sync/push") { setBody(intrudingPayload()) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(
            "Unberuehrt",
            SqlAccountRepository(serverDb).loadAccounts().single().name,
            "the merge ran despite a rejected request",
        )
    }

    @Test
    fun pushWithAWrongKeyIsRejectedAndTheDatabaseIsUntouched() = testApplication {
        val serverDb = newDb()
        SqlAccountRepository(serverDb).saveAccounts(listOf(account("acc-1", "Unberuehrt", 100L)))
        application {
            fintrackerServer(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb), newKey())
        }

        val response = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer ${newKey()}")
            setBody(intrudingPayload())
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(
            "Unberuehrt",
            SqlAccountRepository(serverDb).loadAccounts().single().name,
            "the merge ran despite a rejected request",
        )
    }

    @Test
    fun pushWithTheCorrectKeyWorksAsBefore() = testApplication {
        val key = newKey()
        val serverDb = newDb()
        application {
            fintrackerServer(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb), key)
        }

        val payload = clientPayload { db ->
            SqlAccountRepository(db).saveAccounts(listOf(account("acc-1", "Musterkonto", 100L)))
        }

        val response = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $key")
            setBody(payload)
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(
            "Musterkonto",
            SqlAccountRepository(serverDb).loadAccounts().single().name,
            "an authorised push did not reach the database",
        )
    }

    // ---- the read path ----

    @Test
    fun pullWithoutAKeyLeaksNothing() = testApplication {
        val serverDb = newDb()
        SqlAccountRepository(serverDb).saveAccounts(listOf(account("acc-1", "Geheimkonto", 100L)))
        application {
            fintrackerServer(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb), newKey())
        }

        val response = client.get("/sync/pull")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(
            !response.bodyAsText().contains("Geheimkonto"),
            "the rejection carried the data with it: ${response.bodyAsText()}",
        )
    }

    @Test
    fun pullWithTheCorrectKeyStillAnswers() = testApplication {
        val key = newKey()
        val serverDb = newDb()
        SqlAccountRepository(serverDb).saveAccounts(listOf(account("acc-1", "Musterkonto", 100L)))
        application {
            fintrackerServer(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb), key)
        }

        val response = client.get("/sync/pull") { header(HttpHeaders.Authorization, "Bearer $key") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Musterkonto"))
    }

    // ---- the exemption, and what it is allowed to say ----

    /**
     * #93 wants this in a systemd health check without the key in the unit file,
     * and #94 needs an unambiguous probe for "not reachable without the VPN".
     */
    @Test
    fun healthAnswersWithoutAKey() = testApplication {
        val serverDb = newDb()
        application {
            fintrackerServer(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb), newKey())
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText(), "/health started saying more than that it is alive")
    }

    // ---- shape of the rejection ----

    @Test
    fun anAuthorizationHeaderThatIsNotBearerIsRejected() = testApplication {
        val key = newKey()
        val serverDb = newDb()
        application {
            fintrackerServer(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb), key)
        }

        val response = client.get("/sync/pull") { header(HttpHeaders.Authorization, key) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    /**
     * Default-deny: unknown paths are rejected too, so an unauthenticated caller
     * cannot map which endpoints exist, and a route added later is protected
     * without anyone having to remember it.
     */
    @Test
    fun anUnknownPathIsUnauthorisedRatherThanNotFound() = testApplication {
        val serverDb = newDb()
        application {
            fintrackerServer(exporterOver(serverDb), importerOver(serverDb), SyncWriter(serverDb), newKey())
        }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/sync/gibtsnicht").status)
    }

    // ---- the start-up rule ----

    /**
     * The expensive failure this prevents: a typo in the systemd unit leaves the
     * variable unset, the server starts anyway, everything appears to work, and
     * nobody finds out the door was open.
     */
    @Test
    fun withoutTheEnvironmentVariableThereIsNoKeyToStartWith() {
        assertFailsWith<IllegalStateException> { apiKeyFromEnvironment { null } }
        assertFailsWith<IllegalStateException> { apiKeyFromEnvironment { "   " } }
    }

    @Test
    fun theEnvironmentVariableIsRead() {
        val key = newKey()
        assertEquals(key, apiKeyFromEnvironment { name -> if (name == API_KEY_ENV) key else null })
    }
}
