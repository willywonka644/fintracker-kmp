package io.github.willywonka644.fintracker.sync

/**
 * Turns what the user typed into an address the sync client can call (#91).
 *
 * The server address is free-text input, and the failure it causes is
 * indistinguishable from a real one: a missing scheme, a trailing space from a
 * paste, or a forgotten port all end as "server not reachable", which sends the
 * user looking at the Pi instead of at the field they just filled in. Normalising
 * up front turns most of those into either a working address or a message that
 * names the actual problem.
 *
 * Lives in `shared` because the desktop needs exactly the same treatment in #92,
 * and two implementations of "what counts as a valid address" would drift.
 */
object ServerUrl {

    /**
     * The port the server listens on. Appended when the user gives none.
     *
     * Opinionated on purpose: "typed the address, forgot the port" is the most
     * likely single mistake, and its symptom is the misleading "not reachable".
     * Someone who really wants port 80 or a reverse proxy can type `:80` — that
     * is a deliberate act, while omitting the port is not.
     */
    const val DEFAULT_PORT = 8080

    sealed interface Result {
        /** Ready to use: scheme, host, port, no trailing slash. */
        data class Valid(val url: String) : Result

        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        /** Nothing entered. */
        EMPTY,

        /** Contains a space or similar — usually a stray character from a paste. */
        WHITESPACE,

        /** Something other than http/https, e.g. a typo like `htp://`. */
        BAD_SCHEME,

        /** No host at all, e.g. `http://` or `http://:8080`. */
        NO_HOST,

        /** Port is not a number, or outside 1–65535. */
        BAD_PORT,

        /** Bracketed IPv6 that never closes, and similar. */
        MALFORMED,
    }

    /**
     * Normalises [raw], or names why it cannot be used.
     *
     * What it does: trims the ends, adds `http://` when no scheme is given, adds
     * [DEFAULT_PORT] when no port is given, lowercases scheme and host, and drops
     * trailing slashes so the caller can append `/sync/pull` without producing a
     * double slash.
     *
     * A path is kept rather than rejected — someone may eventually put the server
     * behind a reverse proxy under a prefix — but a trailing slash is not, since
     * that is how the double slash gets in.
     */
    fun normalize(raw: String, defaultPort: Int = DEFAULT_PORT): Result {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.Invalid(Reason.EMPTY)
        if (trimmed.any { it.isWhitespace() }) return Result.Invalid(Reason.WHITESPACE)

        val separator = trimmed.indexOf("://")
        val scheme = if (separator < 0) "http" else trimmed.substring(0, separator).lowercase()
        if (scheme != "http" && scheme != "https") return Result.Invalid(Reason.BAD_SCHEME)

        val rest = if (separator < 0) trimmed else trimmed.substring(separator + 3)
        if (rest.isEmpty()) return Result.Invalid(Reason.NO_HOST)

        val pathStart = rest.indexOf('/')
        val authority = if (pathStart < 0) rest else rest.substring(0, pathStart)
        val path = if (pathStart < 0) "" else rest.substring(pathStart)

        val (host, port) = when (val split = splitAuthority(authority)) {
            null -> return Result.Invalid(Reason.MALFORMED)
            else -> split
        }
        if (host.isEmpty() || host == "[]") return Result.Invalid(Reason.NO_HOST)

        val resolvedPort = when {
            port == null -> defaultPort
            else -> port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return Result.Invalid(Reason.BAD_PORT)
        }

        return Result.Valid("$scheme://${host.lowercase()}:$resolvedPort${path.trimEnd('/')}")
    }

    /**
     * Splits `host`, `host:port` or `[v6]:port` into its two parts. Null means the
     * authority is malformed.
     *
     * IPv6 needs the bracket case of its own: a plain "split at the last colon"
     * would tear `[fd7a::1]` apart at the wrong place, and Tailscale hands out
     * IPv6 addresses as a matter of course (#94).
     */
    private fun splitAuthority(authority: String): Pair<String, String?>? {
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            val host = authority.substring(0, close + 1)
            val after = authority.substring(close + 1)
            return when {
                after.isEmpty() -> host to null
                after.startsWith(":") -> host to after.substring(1)
                else -> null
            }
        }

        val colon = authority.lastIndexOf(':')
        if (colon < 0) return authority to null
        // A bare IPv6 address without brackets: more than one colon and no way to
        // tell address from port. Rejecting beats guessing wrong.
        if (authority.indexOf(':') != colon) return null
        return authority.substring(0, colon) to authority.substring(colon + 1)
    }
}
