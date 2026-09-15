package io.github.willywonka644.fintracker.server

import java.net.InetAddress
import java.net.UnknownHostException

/**
 * The environment variable that says which interface the server listens on.
 *
 * Machine-specific, like [API_KEY_ENV] and [ServerServices.DB_PATH_ENV]: the
 * VPN address belongs to the Pi, not to the program, and the same JAR has to
 * start on a laptop for development without carrying the Pi's address around.
 */
const val BIND_HOST_ENV = "FINTRACKER_BIND_HOST"

/**
 * Where the server listens when nothing says otherwise.
 *
 * Loopback, not the VPN address: an unset variable is a *misconfiguration*, and
 * the safe reading of a misconfiguration is the address that cannot be reached
 * from anywhere else. The failure mode is then "the phone cannot connect", which
 * sends someone looking — rather than "the server is open on the LAN", which
 * looks exactly like success.
 */
const val DEFAULT_BIND_HOST = "127.0.0.1"

/**
 * Reads the listening address from the environment, refusing the wildcard (#94).
 *
 * This is the second of Phase 7's four layers (no open port #90, **this**, the
 * API key #89, a backup #96), and the one that #90 actually decided: the sync
 * server is reachable over the VPN and nowhere else. On the Pi that means the
 * `100.x.y.z` address Tailscale hands out.
 *
 * ## Why `0.0.0.0` is refused instead of merely discouraged
 *
 * Binding to the wildcard puts the server on every interface the Pi has — which
 * today is Ethernet and Wi-Fi on the home LAN, both of which #90 ruled out. The
 * mistake is cheap to make (it is the value every tutorial prints) and invisible
 * once made: everything keeps working, because the VPN path still works. Nothing
 * fails, so nobody looks. Refusing to start is the only way that error announces
 * itself, and it is the same reasoning that leaves [apiKeyFromEnvironment]
 * without a fallback.
 *
 * The gate is [InetAddress.isAnyLocalAddress] rather than a string comparison,
 * so `::`, `0:0:0:0:0:0:0:0` and `0.0.0.0` are all caught by what they *mean*
 * instead of how they are spelled.
 *
 * ## Brackets
 *
 * Tailscale hands out IPv6 freely, and a bracketed address is what one copies
 * out of a URL. Ktor wants the bare host, so the brackets come off here.
 * `ServerUrl` on the client side does the opposite — it *requires* brackets,
 * because there the address goes into a URL where a bare colon is ambiguous.
 * Same address, two forms, two places that know which one they need.
 *
 * ## Prefer the numeric address to a MagicDNS name
 *
 * A name has to resolve before the socket can be bound, and at boot the service
 * may well come up before name resolution over the tailnet does. The numeric
 * address has no such ordering problem. A name that does not resolve fails here
 * with its own message rather than somewhere inside Netty.
 *
 * Takes [lookup] and [resolve] so the behaviour is testable without setting
 * environment variables on the test JVM or touching DNS.
 */
fun bindHostFromEnvironment(
    lookup: (String) -> String? = { name -> System.getenv(name) },
    resolve: (String) -> InetAddress = { host -> InetAddress.getByName(host) },
): String {
    val configured = lookup(BIND_HOST_ENV)?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_BIND_HOST

    val host = configured.removeSurrounding("[", "]")

    val address = try {
        resolve(host)
    } catch (e: UnknownHostException) {
        error(
            "$BIND_HOST_ENV is set to \"$configured\", which is not an address this machine can " +
                "resolve. Use the numeric Tailscale address of this host (`tailscale ip -4`)."
        )
    }

    if (address.isAnyLocalAddress) {
        error(
            "$BIND_HOST_ENV is set to \"$configured\", which listens on every interface. This " +
                "server is reachable over the VPN only (#90) — a wildcard bind would leave it open " +
                "on the home network as well, and nothing would look broken. Use the numeric " +
                "Tailscale address of this host (`tailscale ip -4`), or unset the variable to " +
                "listen on $DEFAULT_BIND_HOST."
        )
    }

    return host
}
