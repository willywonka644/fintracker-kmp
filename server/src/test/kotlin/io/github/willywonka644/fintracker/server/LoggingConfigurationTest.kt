package io.github.willywonka644.fintracker.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #105. Pins that the module actually carries a Logback configuration and
 * that it says what it is meant to say.
 *
 * The success criterion in the issue is a human one — start the server by hand
 * and see three Ktor lines instead of forty from Netty. This is the part of it a
 * machine can check: without a `logback.xml` on the classpath the module falls
 * back to DEBUG for everything, and that fallback is invisible. Nothing fails,
 * the log is merely enormous, and on the Pi it is enormous onto an SD card.
 *
 * Reads the levels back from the configured `LoggerContext` rather than parsing
 * the XML: the question is not whether a file exists but whether it took effect.
 */
class LoggingConfigurationTest {

    private fun logger(name: String) = LoggerFactory.getLogger(name) as Logger

    @Test
    fun theRootLoggerIsAtInfoRatherThanLogbacksDebugFallback() {
        // The literal name rather than the constant: ROOT_LOGGER_NAME is a field on
        // the slf4j interface, and reaching it through the Logback subclass is the
        // kind of Kotlin/Java interop detail that breaks for no useful reason.
        assertEquals(Level.INFO, logger("ROOT").level)
    }

    /**
     * The noisy one. Netty's startup chatter is what #105 was actually about.
     */
    @Test
    fun nettyIsQuietedToWarn() {
        assertEquals(Level.WARN, logger("io.netty").level)
    }

    /**
     * Deliberately *not* quieted: Ktor's startup lines name the address that was
     * bound, which after #94 is the one line worth having in the log.
     */
    @Test
    fun ktorKeepsItsStartupLines() {
        assertTrue(
            logger("io.ktor").isInfoEnabled,
            "io.ktor must stay at INFO — its startup lines say which address was bound",
        )
    }
}
