package io.github.willywonka644.fintracker.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tier-1d tests (Phase 6.5) — PIN hashing.
 * Relies on the per-platform crypto expect/actuals, so it runs on both targets.
 */
class PinHasherTest {

    @Test
    fun hashPin_isDeterministicForSamePinAndSalt() {
        val salt = PinHasher.generateSalt()
        val h1 = PinHasher.hashPin("1234", salt)
        val h2 = PinHasher.hashPin("1234", salt)
        assertEquals(h1, h2)
    }

    @Test
    fun hashPin_differsForDifferentPin() {
        val salt = PinHasher.generateSalt()
        assertNotEquals(PinHasher.hashPin("1234", salt), PinHasher.hashPin("9999", salt))
    }

    @Test
    fun hashPin_differsForDifferentSalt() {
        val h1 = PinHasher.hashPin("1234", PinHasher.generateSalt())
        val h2 = PinHasher.hashPin("1234", PinHasher.generateSalt())
        // Two independent salts practically never collide → different hashes.
        assertNotEquals(h1, h2)
    }

    @Test
    fun generateSalt_producesNonEmptyDistinctValues() {
        val s1 = PinHasher.generateSalt()
        val s2 = PinHasher.generateSalt()
        assertTrue(s1.isNotEmpty())
        assertNotEquals(s1, s2)
    }
}
