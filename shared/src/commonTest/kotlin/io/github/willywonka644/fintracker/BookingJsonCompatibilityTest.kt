package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.serialization.AppJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.decodeFromString

/**
 * Verifies that a minimal JSON payload (missing newer optional fields) can still be
 * deserialized into a Booking with those fields defaulting to null/false.
 */
class BookingJsonCompatibilityTest {
    @Test
    fun booking_missingNewFields_defaultsApplied() {
        val minimalJson = """
            {
              "id": "1",
              "accountId": "A",
              "amount": -10.0,
              "description": "Old",
              "timestamp": 1000
            }
        """.trimIndent()

        val booking = AppJson.decodeFromString<Booking>(minimalJson)

        assertEquals("1", booking.id)
        assertEquals("A", booking.accountId)
        assertEquals(-10.0, booking.amount)
        assertEquals("Old", booking.description)
        assertEquals(1000L, booking.timestamp)
        assertNull(booking.category)
        assertNull(booking.merchantName)
        assertNull(booking.rawDescription)
        assertNull(booking.recurringRuleId)
        assertEquals(BookingSource.MANUAL, booking.source)
        assertNull(booking.importBatchId)
        assertNull(booking.transferGroupId)
    }

    @Test
    fun booking_transferSurvivesARoundTrip() {
        val json = """
            {
              "id": "t1",
              "accountId": "A",
              "amount": -400.0,
              "description": "Umbuchung",
              "timestamp": 1000,
              "source": "TRANSFER",
              "transferGroupId": "grp-1"
            }
        """.trimIndent()

        val booking = AppJson.decodeFromString<Booking>(json)

        assertEquals(BookingSource.TRANSFER, booking.source)
        assertEquals("grp-1", booking.transferGroupId)
    }

    @Test
    fun booking_anUnknownSourceIsSilentlyDowngradedToManual() {
        // Not a wish — a warning, pinned so nobody has to rediscover it. AppJson runs with
        // coerceInputValues, so a build that does not know a source value does not fail on
        // it: the booking comes back as MANUAL, the unknown field is dropped, and the
        // timestamp is kept. Because the sync merge compares with strictly-greater, the
        // corrected version can then never propagate again. This is the whole reason the
        // server has to be updated before any device writes a TRANSFER (#120).
        val json = """
            {
              "id": "t1",
              "accountId": "A",
              "amount": -400.0,
              "description": "Umbuchung",
              "timestamp": 1000,
              "source": "SOMETHING_A_LATER_VERSION_ADDED",
              "someFieldFromTheFuture": "x"
            }
        """.trimIndent()

        val booking = AppJson.decodeFromString<Booking>(json)

        assertEquals(BookingSource.MANUAL, booking.source)
        assertEquals(1000L, booking.timestamp)
    }
}
