package io.github.willywonka644.fintracker.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #92. The connection check was verified by hand on a phone for #91 and
 * never pinned down; moving it into `shared` for the desktop is the moment to
 * fix that.
 *
 * What matters here is not the wording but which cause gets named, and that the
 * network is not touched at all when the address alone already settles it.
 */
class SyncConnectionCheckTest {

    private val key = "s3cr3t"

    private val paths = mutableListOf<String>()

    /** Answers per path, so `/health` and `/sync/pull` can disagree. */
    private fun clientFor(
        health: HttpStatusCode = HttpStatusCode.OK,
        pull: HttpStatusCode = HttpStatusCode.OK,
    ) = SyncClient(
        HttpClient(
            MockEngine { request: HttpRequestData ->
                paths += request.url.encodedPath
                val status = if (request.url.encodedPath == "/health") health else pull
                if (status == HttpStatusCode.OK) respond("{}", HttpStatusCode.OK)
                else respondError(status, "nope")
            }
        )
    )

    @Test
    fun aWorkingServerAndKeyReportSuccess() = runTest {
        val result = checkSyncConnection(clientFor(), "10.0.2.2", key)

        assertTrue(result.ok)
        // The normalised address is in the message, so the user sees what was tried.
        assertTrue(result.message.contains("http://10.0.2.2:8080"), result.message)
        // Health first, then the authenticated route — that order is the point.
        assertEquals(listOf("/health", "/sync/pull"), paths)
    }

    @Test
    fun aRejectedKeyIsReportedAsReachableButRefused() = runTest {
        val result = checkSyncConnection(
            clientFor(health = HttpStatusCode.OK, pull = HttpStatusCode.Unauthorized),
            "10.0.2.2",
            key,
        )

        assertFalse(result.ok)
        assertTrue(result.message.contains("erreichbar"), result.message)
        assertTrue(result.message.contains("Schlüssel"), result.message)
    }

    @Test
    fun anUnreachableServerIsNotBlamedOnTheKey() = runTest {
        val client = SyncClient(HttpClient(MockEngine { throw IllegalStateException("refused") }))
        val result = checkSyncConnection(client, "10.0.2.2", key)

        assertFalse(result.ok)
        assertFalse(result.message.contains("Schlüssel"), result.message)
    }

    /** A typo is settled without a request — no server needs to exist to spot it. */
    @Test
    fun aBadAddressIsNamedWithoutTouchingTheNetwork() = runTest {
        val result = checkSyncConnection(clientFor(), "htp://10.0.2.2", key)

        assertFalse(result.ok)
        assertTrue(paths.isEmpty(), "expected no request, got $paths")
    }

    @Test
    fun aMissingKeyIsNamedWithoutTouchingTheNetwork() = runTest {
        val result = checkSyncConnection(clientFor(), "10.0.2.2", "   ")

        assertFalse(result.ok)
        assertTrue(paths.isEmpty(), "expected no request, got $paths")
    }
}
