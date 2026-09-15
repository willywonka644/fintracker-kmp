package io.github.willywonka644.fintracker.sync

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #91. The server address is user input, and every mistake in it surfaces
 * as the same misleading symptom — "server not reachable" — which sends the user
 * looking at the Pi instead of at the field they just filled in. These tests pin
 * down which inputs are silently repaired and which are named.
 */
class ServerUrlTest {

    private fun valid(raw: String): String =
        when (val result = ServerUrl.normalize(raw)) {
            is ServerUrl.Result.Valid -> result.url
            is ServerUrl.Result.Invalid -> error("expected '$raw' to be valid, got ${result.reason}")
        }

    private fun reason(raw: String): ServerUrl.Reason =
        when (val result = ServerUrl.normalize(raw)) {
            is ServerUrl.Result.Invalid -> result.reason
            is ServerUrl.Result.Valid -> error("expected '$raw' to be rejected, got ${result.url}")
        }

    // ---- what gets repaired ----

    @Test
    fun aBareHostGetsSchemeAndPort() {
        assertEquals("http://192.168.1.50:8080", valid("192.168.1.50"))
    }

    @Test
    fun aForgottenSchemeIsAdded() {
        assertEquals("http://192.168.1.50:8080", valid("192.168.1.50:8080"))
    }

    /** The single most likely mistake, and the one with the most misleading symptom. */
    @Test
    fun aForgottenPortBecomesTheDefault() {
        assertEquals("http://pi.local:8080", valid("http://pi.local"))
    }

    @Test
    fun surroundingWhitespaceFromAPasteIsTrimmed() {
        assertEquals("http://192.168.1.50:8080", valid("  http://192.168.1.50:8080  "))
    }

    /** So the caller can append "/sync/pull" without producing a double slash. */
    @Test
    fun aTrailingSlashIsDropped() {
        assertEquals("http://192.168.1.50:8080", valid("http://192.168.1.50:8080/"))
    }

    @Test
    fun schemeAndHostAreLowercased() {
        assertEquals("http://pi.local:8080", valid("HTTP://Pi.Local:8080"))
    }

    @Test
    fun httpsIsKept() {
        assertEquals("https://pi.example.org:443", valid("https://pi.example.org:443"))
    }

    /** A reverse proxy may serve the API under a prefix, so a path survives. */
    @Test
    fun aPathIsKept() {
        assertEquals("http://pi.local:8080/fintracker", valid("http://pi.local:8080/fintracker/"))
    }

    /** Tailscale hands out IPv6 as a matter of course (#94). */
    @Test
    fun bracketedIpv6IsUnderstood() {
        assertEquals("http://[fd7a:115c::1]:8080", valid("http://[fd7a:115C::1]:8080"))
        assertEquals("http://[fd7a:115c::1]:8080", valid("[fd7a:115C::1]"))
    }

    /** The emulator reaches a server on the host machine here — used all through #91. */
    @Test
    fun theEmulatorLoopbackAddressWorks() {
        assertEquals("http://10.0.2.2:8080", valid("10.0.2.2:8080"))
    }

    // ---- what gets named instead of guessed at ----

    @Test
    fun anEmptyFieldIsNamedAsSuch() {
        assertEquals(ServerUrl.Reason.EMPTY, reason(""))
        assertEquals(ServerUrl.Reason.EMPTY, reason("   "))
    }

    @Test
    fun anInnerSpaceIsNamedRatherThanSilentlyRemoved() {
        assertEquals(ServerUrl.Reason.WHITESPACE, reason("http://192.168.1.50 :8080"))
    }

    @Test
    fun aMistypedSchemeIsNamed() {
        assertEquals(ServerUrl.Reason.BAD_SCHEME, reason("htp://192.168.1.50:8080"))
        assertEquals(ServerUrl.Reason.BAD_SCHEME, reason("ftp://192.168.1.50:8080"))
    }

    @Test
    fun aMissingHostIsNamed() {
        assertEquals(ServerUrl.Reason.NO_HOST, reason("http://"))
        assertEquals(ServerUrl.Reason.NO_HOST, reason("http://:8080"))
    }

    @Test
    fun aPortThatIsNotANumberOrOutOfRangeIsNamed() {
        assertEquals(ServerUrl.Reason.BAD_PORT, reason("http://pi.local:achttausend"))
        assertEquals(ServerUrl.Reason.BAD_PORT, reason("http://pi.local:0"))
        assertEquals(ServerUrl.Reason.BAD_PORT, reason("http://pi.local:70000"))
    }

    /**
     * An unbracketed IPv6 address cannot be split into host and port without
     * guessing, and guessing wrong would produce a plausible-looking address that
     * never connects. Naming it beats that.
     */
    @Test
    fun anUnbracketedIpv6AddressIsRejectedRatherThanGuessedAt() {
        assertEquals(ServerUrl.Reason.MALFORMED, reason("http://fd7a:115c::1:8080"))
        assertEquals(ServerUrl.Reason.MALFORMED, reason("http://[fd7a:115c::1:8080"))
    }
}
