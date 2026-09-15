package io.github.willywonka644.fintracker.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlin.system.exitProcess

/**
 * Entry point for the JAR that runs on the Pi.
 *
 * The listening address comes from `FINTRACKER_BIND_HOST` and defaults to
 * loopback (#94). On the Pi it is the numeric Tailscale address, which is what
 * makes the server reachable over the VPN and nowhere else — see
 * [bindHostFromEnvironment] for why the wildcard is refused rather than
 * discouraged.
 *
 * The database lives at `~/fintracker-server/fintracker.db` unless
 * `FINTRACKER_SERVER_DB` says otherwise — never the desktop app's database.
 *
 * The API key comes from `FINTRACKER_API_KEY` and there is no default (#89):
 * without it the process exits instead of serving unprotected.
 *
 * Both settings are read together, before the database is opened, so a
 * misconfigured start leaves no trace to clean up — and so a start that gets
 * the key right but the address wrong fails at the same moment as one that gets
 * neither right, instead of after a successful-looking boot.
 *
 * Then the database is checked before it is opened (#96). A corrupted server is
 * worse than a missing one: it hands its damage to both clients on the next
 * exchange. See [integrityProblems].
 *
 * Three refusals to start, one shape: no key (#89), a wildcard address (#94), a
 * damaged database (#96). Each of them would otherwise keep working in a way
 * that looks entirely normal.
 */
fun main() {
    val (apiKey, bindHost) = try {
        apiKeyFromEnvironment() to bindHostFromEnvironment()
    } catch (e: IllegalStateException) {
        System.err.println(e.message)
        exitProcess(1)
    }

    val databaseFile = ServerServices.defaultDatabaseFile()
    val problems = integrityProblems(databaseFile)
    if (problems.isNotEmpty()) {
        System.err.println(integrityFailureMessage(databaseFile, problems))
        exitProcess(1)
    }

    val services = ServerServices(databaseFile)

    embeddedServer(Netty, port = 8080, host = bindHost) {
        fintrackerServer(services, apiKey)
    }.start(wait = true)
}
