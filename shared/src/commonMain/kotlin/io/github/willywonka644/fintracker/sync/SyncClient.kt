package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.serialization.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

/**
 * Why a call did not deliver data (#91).
 *
 * The issue's central demand is that these stay apart. A phone talking to a box
 * behind a home router fails for several unrelated reasons, and a single
 * "sync failed" sends the user to check the wrong thing: the Pi when the key is
 * wrong, the key when the Pi is asleep.
 */
enum class SyncCallError {
    /** No answer at all: wrong address, server down, no network. */
    UNREACHABLE,

    /** An answer was expected but never arrived in time. */
    TIMEOUT,

    /** 401 — the server is there and refused the key. */
    UNAUTHORIZED,

    /** 400 — the server understood the request and rejected the payload. */
    REJECTED,

    /** 5xx — the server broke while handling a request it accepted. */
    SERVER_ERROR,

    /** Answered 2xx with something that is not the expected body. */
    MALFORMED_RESPONSE,

    /** A status the server is not documented to send — most likely not our server. */
    UNEXPECTED_STATUS,
}

sealed interface SyncCallResult<out T> {
    data class Ok<out T>(val value: T) : SyncCallResult<T>

    /** [detail] carries the server's own words, for the log, not for the label. */
    data class Failed(val error: SyncCallError, val detail: String? = null) : SyncCallResult<Nothing>
}

/**
 * What the server reports back after a push, mirroring its `PushResponse`.
 *
 * Every field defaults, so a server that grows a counter later answers an older
 * client without breaking it — [AppJson] ignores unknown keys for the same
 * reason.
 */
@Serializable
data class PushSummary(
    val newCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val skippedCount: Int = 0,
    val warnings: List<String> = emptyList(),
)

/**
 * Talks to the sync server (#91).
 *
 * Lives in `shared` for the reason [ServerUrl] does: the desktop needs the same
 * client in #92, and a second idea of what a `401` means would drift from this
 * one.
 *
 * ## What it deliberately does not do
 *
 * [pull] hands back the raw response body instead of importing it. The Phase 5
 * confirmation dialog exists so that no counterpart writes to the phone
 * unannounced, and a server that is always up makes that more important, not
 * less. So the caller runs the body through [SyncImporter] to get a preview,
 * shows it, and only then calls [SyncWriter]. A client that wrote by itself
 * would quietly remove that step.
 *
 * The [HttpClient] is handed in rather than built here, which is what lets the
 * error mapping be tested against a mock engine instead of a real Pi.
 */
class SyncClient(private val http: HttpClient) {

    /**
     * Liveness check, deliberately without the key.
     *
     * `/health` is the one route the server leaves open, and that is what makes
     * "server answers but rejects the key" distinguishable from "nothing there".
     * A UI that wants to say which of the two happened asks here first.
     */
    suspend fun health(baseUrl: String): SyncCallResult<Unit> = request(
        send = { http.get("$baseUrl/health") },
        onSuccess = { SyncCallResult.Ok(Unit) },
    )

    /** The server's full dataset, unparsed — see the class note on why. */
    suspend fun pull(baseUrl: String, apiKey: String): SyncCallResult<String> = request(
        send = { http.get("$baseUrl/sync/pull") { bearer(apiKey) } },
        onSuccess = { SyncCallResult.Ok(it.bodyAsText()) },
    )

    /** Sends [payload], the client's own export, and reports what the server made of it. */
    suspend fun push(baseUrl: String, apiKey: String, payload: String): SyncCallResult<PushSummary> = request(
        send = {
            http.post("$baseUrl/sync/push") {
                bearer(apiKey)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        },
        onSuccess = { response ->
            val body = response.bodyAsText()
            try {
                SyncCallResult.Ok(AppJson.decodeFromString<PushSummary>(body))
            } catch (e: SerializationException) {
                SyncCallResult.Failed(SyncCallError.MALFORMED_RESPONSE, body.take(DETAIL_LIMIT))
            } catch (e: IllegalArgumentException) {
                SyncCallResult.Failed(SyncCallError.MALFORMED_RESPONSE, body.take(DETAIL_LIMIT))
            }
        },
    )

    private suspend fun <T> request(
        send: suspend () -> HttpResponse,
        onSuccess: suspend (HttpResponse) -> SyncCallResult<T>,
    ): SyncCallResult<T> = try {
        val response = send()
        if (response.status.isSuccess()) onSuccess(response) else failureFor(response)
    } catch (e: CancellationException) {
        // The user left the screen. Not a sync failure, and swallowing it here
        // would leave the caller's coroutine looking successful.
        throw e
    } catch (e: HttpRequestTimeoutException) {
        SyncCallResult.Failed(SyncCallError.TIMEOUT, e.message)
    } catch (e: ConnectTimeoutException) {
        SyncCallResult.Failed(SyncCallError.TIMEOUT, e.message)
    } catch (e: SocketTimeoutException) {
        SyncCallResult.Failed(SyncCallError.TIMEOUT, e.message)
    } catch (e: Exception) {
        // Everything a socket can do to you differs per platform and engine, and
        // none of it is worth a separate label: from the user's side it is all
        // "no answer". The message is kept for the log.
        SyncCallResult.Failed(SyncCallError.UNREACHABLE, e.message)
    }

    private suspend fun failureFor(response: HttpResponse): SyncCallResult<Nothing> {
        val detail = response.bodyAsText().take(DETAIL_LIMIT).ifBlank { null }
        val error = when {
            response.status == HttpStatusCode.Unauthorized -> SyncCallError.UNAUTHORIZED
            response.status == HttpStatusCode.BadRequest -> SyncCallError.REJECTED
            response.status.value >= 500 -> SyncCallError.SERVER_ERROR
            else -> SyncCallError.UNEXPECTED_STATUS
        }
        return SyncCallResult.Failed(error, detail)
    }

    private fun HttpRequestBuilder.bearer(apiKey: String) {
        header(HttpHeaders.Authorization, "Bearer $apiKey")
    }

    private companion object {
        /** Enough of a server's answer to diagnose with, not enough to fill a log. */
        const val DETAIL_LIMIT = 200
    }
}

/**
 * The client the app and the desktop use in production.
 *
 * The engine comes from whatever is on the platform's classpath (OkHttp on both
 * targets today), so this stays free of platform code.
 *
 * The two timeouts are separate on purpose. Connecting to a Pi that is off fails
 * fast and should say so quickly; a large dataset over a slow uplink is not a
 * fault and needs room. One shared timeout would have to choose between cutting
 * off a working sync and making an unreachable server look like a hang.
 */
fun syncHttpClient(
    connectMillis: Long = 5_000,
    requestMillis: Long = 60_000,
): HttpClient = HttpClient {
    install(HttpTimeout) {
        connectTimeoutMillis = connectMillis
        requestTimeoutMillis = requestMillis
        socketTimeoutMillis = requestMillis
    }
}
