package io.github.willywonka644.fintracker.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Issue #92. The order of an exchange was previously only asserted in prose and
 * in a device test; moving it into `shared` is the moment to pin it down.
 *
 * The two properties that matter are structural, not cosmetic: **push before
 * pull**, so one exchange leaves both sides holding the union, and **no local
 * write before a human agrees**, which is the Phase 5 dialog's whole reason to
 * exist.
 */
class SyncExchangeTest {

    private val config = ServerSyncConfig(url = "10.0.2.2", apiKey = "s3cr3t")

    private val requested = mutableListOf<String>()

    private val emptyPreview = SyncPreview(
        newCount = 2,
        updatedCount = 1,
        unchangedCount = 0,
        skippedCount = 0,
        warnings = emptyList(),
        resolvedAccounts = emptyList(),
        resolvedBookings = emptyList(),
        resolvedCategories = emptyList(),
        resolvedRecurringRules = emptyList(),
    )

    private fun clientFor(
        push: HttpStatusCode = HttpStatusCode.OK,
        pull: HttpStatusCode = HttpStatusCode.OK,
    ) = SyncClient(
        HttpClient(
            MockEngine { request ->
                requested += request.url.encodedPath
                val status = if (request.url.encodedPath == "/sync/push") push else pull
                when {
                    status != HttpStatusCode.OK -> respondError(status, "nope")
                    request.url.encodedPath == "/sync/push" ->
                        respond("""{"newCount":5,"updatedCount":4}""", HttpStatusCode.OK)
                    else -> respond("""{"accounts":[]}""", HttpStatusCode.OK)
                }
            }
        )
    )

    @Test
    fun theLocalDataGoesUpBeforeTheMergedResultComesDown() = runTest {
        val result = runSyncExchange(
            client = clientFor(),
            config = config,
            exportPayload = { """{"bookings":[]}""" },
            importPayload = { SyncImportResult.Preview(emptyPreview) },
        )

        assertIs<SyncExchange.NeedsConfirmation>(result)
        assertEquals(listOf("/sync/push", "/sync/pull"), requested)
        // The server's own counters survive into the result, so the UI can say
        // what was sent as well as what would come back.
        assertEquals(5, result.pushed.newCount)
        assertEquals(2, result.preview.newCount)
    }

    /**
     * A push that did not arrive must not be followed by a pull: the server would
     * answer with a state that does not contain this device's changes, and
     * confirming it would look like a successful sync.
     */
    @Test
    fun aFailedPushStopsBeforePulling() = runTest {
        var imported = false

        val result = runSyncExchange(
            client = clientFor(push = HttpStatusCode.InternalServerError),
            config = config,
            exportPayload = { "{}" },
            importPayload = { imported = true; SyncImportResult.Preview(emptyPreview) },
        )

        assertIs<SyncExchange.Failed>(result)
        assertEquals(listOf("/sync/push"), requested)
        assertTrue(!imported, "import must not run after a failed push")
    }

    @Test
    fun aRejectedKeyIsReportedInWordsTheUserCanActOn() = runTest {
        val result = runSyncExchange(
            client = clientFor(push = HttpStatusCode.Unauthorized),
            config = config,
            exportPayload = { "{}" },
            importPayload = { SyncImportResult.Preview(emptyPreview) },
        )

        assertIs<SyncExchange.Failed>(result)
        assertTrue(result.message.contains("Schlüssel"), result.message)
    }

    /** A merge failure must not be worded like a transport failure. */
    @Test
    fun anUnreadableAnswerIsNamedAsSuchRatherThanAsAConnectionFault() = runTest {
        val result = runSyncExchange(
            client = clientFor(),
            config = config,
            exportPayload = { "{}" },
            importPayload = { SyncImportResult.Error(SyncError.InvalidJson) },
        )

        assertIs<SyncExchange.Failed>(result)
        assertEquals(describe(SyncError.InvalidJson), result.message)
    }

    @Test
    fun anUnusableAddressNeverReachesTheNetwork() = runTest {
        val result = runSyncExchange(
            client = clientFor(),
            config = ServerSyncConfig(url = "htp://nope", apiKey = "k"),
            exportPayload = { "{}" },
            importPayload = { SyncImportResult.Preview(emptyPreview) },
        )

        assertIs<SyncExchange.Failed>(result)
        assertTrue(requested.isEmpty(), "expected no request, got $requested")
    }
}
