package io.github.willywonka644.fintracker.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import java.security.MessageDigest

/**
 * The environment variable the server reads its key from.
 *
 * Deliberately not a file inside the JAR and not a constant in this source tree:
 * the JAR gets copied, backed up and rebuilt, and a secret that travels with it
 * ends up in all three. The key belongs to the machine, not to the program —
 * same reasoning as [ServerServices.DB_PATH_ENV].
 */
const val API_KEY_ENV = "FINTRACKER_API_KEY"

/** The only path that answers without a key. See [apiKeyGuard] for why. */
private val OPEN_PATHS = setOf("/health")

/**
 * Reads the API key from the environment, or fails loudly (#89).
 *
 * There is deliberately no fallback. A server that starts unprotected when the
 * variable is missing is the expensive mistake here: a typo in the systemd unit
 * would leave the door open and everything would still *appear* to work, so
 * nobody would ever look. Refusing to start makes that failure loud instead of
 * silent.
 *
 * Takes [lookup] rather than calling `System.getenv` directly so the behaviour
 * can be tested without setting environment variables on the test JVM.
 */
fun apiKeyFromEnvironment(lookup: (String) -> String? = { name -> System.getenv(name) }): String =
    lookup(API_KEY_ENV)?.takeIf { it.isNotBlank() }
        ?: error(
            "$API_KEY_ENV is not set. The server refuses to start without an API key — " +
                "generate one with `openssl rand -base64 32` and pass it in the systemd unit."
        )

/**
 * Rejects every request that does not carry the API key (#89).
 *
 * This is the third of the four layers Phase 7 rests on: no open port (#90),
 * VPN membership (#94), **this key**, and a backup for whatever gets through
 * anyway (#96). It answers a different question from the VPN: the VPN decides
 * which *device* may connect at all, the key decides which *program* may write.
 * Being on the tailnet is not the same as being trusted — an old device that was
 * never removed, or a borrowed laptop, would otherwise stand in front of
 * `/sync/push` unhindered.
 *
 * `/sync/push` is where that matters, because it is the write path and the merge
 * decides by timestamp. A pushed payload with timestamps set into the future
 * wins on *every* device at once and is practically only undoable from a backup.
 *
 * ## Default-deny
 *
 * The check runs in the `Plugins` phase, before routing, and rejects everything
 * that is not in [openPaths] — including paths that do not exist. So a route
 * added later is protected because it is new, not because someone remembered to
 * protect it. It also means an unauthenticated caller cannot tell an existing
 * endpoint from a made-up one; both answer 401.
 *
 * ## Why `/health` is exempt
 *
 * The liveness check has to work without credentials, for the same reason it
 * does not touch the database (see [healthRoutes]): a check that can fail for
 * several reasons tells you nothing about which one happened. #93 wants it in a
 * systemd/curl health check without putting the key in the unit file, and #94's
 * success criterion is "*not* reachable from the home network without the VPN" —
 * a probe that answers 401 instead of connecting would leave that ambiguous.
 * Its answer stays a bare `OK`: it confirms something is alive without saying
 * what.
 */
fun Application.apiKeyGuard(apiKey: String, openPaths: Set<String> = OPEN_PATHS) {
    require(apiKey.isNotBlank()) { "refusing to install the guard with a blank API key" }

    intercept(ApplicationCallPipeline.ApplicationPhase.Plugins) {
        if (call.request.path() in openPaths) return@intercept

        val presented = bearerToken(call.request.header(HttpHeaders.Authorization))
        if (presented == null || !constantTimeEquals(presented, apiKey)) {
            // A bare marker, no detail. "Wrong key" and "no key" are the same
            // answer on purpose — the difference is only useful to a caller who
            // is guessing.
            call.respondText("UNAUTHORIZED", status = HttpStatusCode.Unauthorized)
            finish()
        }
    }
}

/**
 * Pulls the key out of `Authorization: Bearer <key>`.
 *
 * Returns null for anything that is not a bearer header, so a client that sends
 * Basic auth or nothing at all lands in the same rejection as a wrong key.
 */
private fun bearerToken(headerValue: String?): String? {
    val prefix = "Bearer "
    if (headerValue == null || !headerValue.startsWith(prefix, ignoreCase = true)) return null
    return headerValue.substring(prefix.length).trim().takeIf { it.isNotEmpty() }
}

/**
 * Compares without leaking how far the comparison got.
 *
 * An ordinary `==` stops at the first differing character, so a nearly correct
 * key takes measurably longer to reject than a wrong first character. Someone
 * who measures response times can use that to guess the key one character at a
 * time instead of trying every combination. Over a network the signal is buried
 * in noise and the attack is hard — but the fix is one line, so there is no
 * reason to leave it out.
 *
 * [MessageDigest.isEqual] is the JDK's time-constant comparison. It still
 * compares lengths first, which leaks the *length* of the key; that is
 * deliberate and harmless here, because the key length is fixed by how we
 * generate it and is not a secret.
 */
private fun constantTimeEquals(presented: String, expected: String): Boolean =
    MessageDigest.isEqual(presented.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
