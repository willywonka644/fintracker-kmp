package io.github.willywonka644.fintracker.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respondError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Issue #91. The point of these tests is the failure mapping, not the happy path:
 * a phone talking to a box behind a home router fails for several unrelated
 * reasons, and the UI can only name the right one if the client keeps them apart.
 * A mock engine lets that be pinned here instead of tried out against a real Pi.
 */
class SyncClientTest {

    private val base = "http://10.0.2.2:8080"
    private val key = "s3cr3t"

    /** Requests the client sent, in order — for asserting headers and bodies. */
    private val sent = mutableListOf<HttpRequestData>()

    private fun clientFor(
        handle: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ) = SyncClient(
        HttpClient(
            MockEngine { request ->
                sent += request
                handle(request)
            }
        )
    )

    /** Extension, not a plain method: `respond` only exists on the handler's receiver. */
    private fun MockRequestHandleScope.json(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun bodyTextOf(request: HttpRequestData): String? = (request.body as? TextContent)?.text

    // ── what a good exchange looks like ────────────────────────────────────────

    @Test
    fun pull_handsBackTheBodyUntouched() = runTest {
        val payload = """{"accounts":[],"bookings":[]}"""
        val result = clientFor { json(payload) }.pull(base, key)

        assertIs<SyncCallResult.Ok<String>>(result)
        // Verbatim: importing is the caller's job, so the confirmation dialog
        // still sits between the server and the database.
        assertEquals(payload, result.value)
        assertEquals("$base/sync/pull", sent.single().url.toString())
    }

    @Test
    fun pull_carriesTheKeyAsABearerToken() = runTest {
        clientFor { json("{}") }.pull(base, key)

        assertEquals("Bearer $key", sent.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun push_sendsThePayloadAsJsonAndReportsTheCounters() = runTest {
        val payload = """{"bookings":[{"id":"a"}]}"""
        val result = clientFor {
            json("""{"newCount":3,"updatedCount":2,"unchangedCount":1,"skippedCount":0,"warnings":["hm"]}""")
        }.push(base, key, payload)

        assertIs<SyncCallResult.Ok<PushSummary>>(result)
        assertEquals(3, result.value.newCount)
        assertEquals(2, result.value.updatedCount)
        assertEquals(1, result.value.unchangedCount)
        assertEquals(listOf("hm"), result.value.warnings)

        val request = sent.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("$base/sync/push", request.url.toString())
        assertEquals(payload, bodyTextOf(request))
    }

    /** A server that grows a counter later must not break an older phone. */
    @Test
    fun push_toleratesUnknownAndMissingFields() = runTest {
        val result = clientFor { json("""{"newCount":1,"somethingNew":"later"}""") }
            .push(base, key, "{}")

        assertIs<SyncCallResult.Ok<PushSummary>>(result)
        assertEquals(1, result.value.newCount)
        assertEquals(0, result.value.skippedCount)
        assertTrue(result.value.warnings.isEmpty())
    }

    /**
     * `/health` is the one route the server leaves open, and asking it without a
     * key is what makes "answers but rejects the key" separable from "nothing
     * there". Sending the key anyway would throw that distinction away.
     */
    @Test
    fun health_asksWithoutTheKey() = runTest {
        val result = clientFor { respond("OK", HttpStatusCode.OK) }.health(base)

        assertIs<SyncCallResult.Ok<Unit>>(result)
        assertEquals("$base/health", sent.single().url.toString())
        assertNull(sent.single().headers[HttpHeaders.Authorization])
    }

    // ── the failures the UI has to tell apart ──────────────────────────────────

    @Test
    fun aRejectedKeyIsNotAConnectionProblem() = runTest {
        val result = clientFor { respondError(HttpStatusCode.Unauthorized, "UNAUTHORIZED") }
            .pull(base, key)

        assertIs<SyncCallResult.Failed>(result)
        assertEquals(SyncCallError.UNAUTHORIZED, result.error)
        assertEquals("UNAUTHORIZED", result.detail)
    }

    @Test
    fun aMalformedPayloadIsTheClientsFault() = runTest {
        val result = clientFor { respondError(HttpStatusCode.BadRequest, "payload not readable") }
            .push(base, key, "not json")

        assertIs<SyncCallResult.Failed>(result)
        assertEquals(SyncCallError.REJECTED, result.error)
    }

    @Test
    fun aFailedWriteIsTheServersFault() = runTest {
        val result = clientFor { respondError(HttpStatusCode.InternalServerError, "write failed") }
            .push(base, key, "{}")

        assertIs<SyncCallResult.Failed>(result)
        assertEquals(SyncCallError.SERVER_ERROR, result.error)
    }

    /**
     * Typing the router's address instead of the Pi's lands on some other web
     * server. That is neither our 401 nor our 500, and calling it a server error
     * would point the user at the wrong machine.
     */
    @Test
    fun someOtherWebServerIsNamedSeparately() = runTest {
        val result = clientFor { respondError(HttpStatusCode.NotFound, "<html>Not Found</html>") }
            .pull(base, key)

        assertIs<SyncCallResult.Failed>(result)
        assertEquals(SyncCallError.UNEXPECTED_STATUS, result.error)
    }

    @Test
    fun noAnswerAtAllIsUnreachable() = runTest {
        val result = clientFor { throw IllegalStateException("connection refused") }
            .pull(base, key)

        assertIs<SyncCallResult.Failed>(result)
        assertEquals(SyncCallError.UNREACHABLE, result.error)
        assertEquals("connection refused", result.detail)
    }

    @Test
    fun aTimeoutIsNotTheSameAsUnreachable() = runTest {
        val result = clientFor { throw ConnectTimeoutException("connect timed out") }
            .pull(base, key)

        assertIs<SyncCallResult.Failed>(result)
        assertEquals(SyncCallError.TIMEOUT, result.error)
    }

    /**
     * A captive portal or a proxy answers 200 with a login page. The status says
     * everything is fine, the body says otherwise, and only the parse notices.
     */
    @Test
    fun aTwoHundredThatIsNotOurAnswerIsMalformed() = runTest {
        val result = clientFor { json("<html>Please sign in</html>") }.push(base, key, "{}")

        assertIs<SyncCallResult.Failed>(result)
        assertEquals(SyncCallError.MALFORMED_RESPONSE, result.error)
    }
}
