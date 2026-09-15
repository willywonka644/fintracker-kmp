package io.github.willywonka644.fintracker.server

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Issue #94. The success criterion of that issue is a negative one — "not
 * reachable from the home network without the VPN" — and the only part of it
 * that can be tested without a Pi is this: which address the server decides to
 * listen on, and what it does when told to listen on all of them.
 *
 * The environment lookup and the name resolution are both injected, so these
 * tests neither set variables on the test JVM nor touch DNS.
 */
class BindAddressTest {

    /** All the literals below resolve without DNS; only this one would. */
    private val neverResolves: (String) -> InetAddress = { host ->
        throw UnknownHostException(host)
    }

    private fun environment(value: String?): (String) -> String? = { name ->
        if (name == BIND_HOST_ENV) value else null
    }

    @Test
    fun anUnsetVariableMeansLoopback() {
        assertEquals(DEFAULT_BIND_HOST, bindHostFromEnvironment(lookup = environment(null)))
    }

    /**
     * An empty value in a systemd unit (`FINTRACKER_BIND_HOST=`) is the same
     * mistake as leaving the line out, and has to land in the same safe place.
     */
    @Test
    fun aBlankVariableMeansLoopback() {
        assertEquals(DEFAULT_BIND_HOST, bindHostFromEnvironment(lookup = environment("   ")))
    }

    @Test
    fun aTailscaleAddressIsUsedAsGiven() {
        assertEquals("100.101.102.103", bindHostFromEnvironment(lookup = environment("100.101.102.103")))
    }

    /** A trailing newline is what an env file written by `echo` ends up with. */
    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("100.101.102.103", bindHostFromEnvironment(lookup = environment(" 100.101.102.103\n")))
    }

    /**
     * The counterpart to `ServerUrl`, which *requires* brackets because its
     * address goes into a URL. Ktor wants the bare host, so a value copied out
     * of a URL has to work here too.
     */
    @Test
    fun aBracketedIpv6AddressLosesItsBrackets() {
        assertEquals("fd7a:115c:a1e0::1", bindHostFromEnvironment(lookup = environment("[fd7a:115c:a1e0::1]")))
    }

    @Test
    fun anUnbracketedIpv6AddressIsUsedAsGiven() {
        assertEquals("fd7a:115c:a1e0::1", bindHostFromEnvironment(lookup = environment("fd7a:115c:a1e0::1")))
    }

    /**
     * The whole point of #94. A wildcard bind keeps working over the VPN, so
     * nothing looks broken while the server sits open on the home LAN — the
     * failure this refusal exists to make loud.
     */
    @Test
    fun theIpv4WildcardIsRefused() {
        val failure = assertFailsWith<IllegalStateException> {
            bindHostFromEnvironment(lookup = environment("0.0.0.0"))
        }

        assertTrue(
            failure.message.orEmpty().contains("every interface"),
            "the message should say what is wrong, not just that something is: ${failure.message}",
        )
    }

    /** Same meaning, different spelling — caught by meaning, not by string. */
    @Test
    fun theIpv6WildcardIsRefused() {
        assertFailsWith<IllegalStateException> {
            bindHostFromEnvironment(lookup = environment("::"))
        }
    }

    @Test
    fun theWrittenOutIpv6WildcardIsRefused() {
        assertFailsWith<IllegalStateException> {
            bindHostFromEnvironment(lookup = environment("0:0:0:0:0:0:0:0"))
        }
    }

    /**
     * A MagicDNS name that does not resolve yet at boot fails here with its own
     * message, rather than deep inside Netty where it reads as a crash.
     */
    @Test
    fun anAddressThatDoesNotResolveIsNamed() {
        val failure = assertFailsWith<IllegalStateException> {
            bindHostFromEnvironment(lookup = environment("fintracker-pi.example.ts.net"), resolve = neverResolves)
        }

        assertTrue(
            failure.message.orEmpty().contains("fintracker-pi.example.ts.net"),
            "the message should quote what was configured: ${failure.message}",
        )
    }
}
