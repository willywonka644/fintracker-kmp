package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.booking.BookingFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookingFactoryTest {
    @Test
    fun createManualBooking_trimsAndDefaults() {
        var nextId = 10
        val booking = BookingFactory.createManualBooking(
            idProvider = { (nextId++).toString() },
            accountId = "A",
            amount = -12.5,
            description = "  Kaffee ",
            category = "  Food ",
            merchantName = "  Cafe ",
            rawDescription = "  Latte macchiato  ",
            timestamp = 1234L
        )

        assertEquals("10", booking.id)
        assertEquals("A", booking.accountId)
        assertEquals(-12.5, booking.amount)
        assertEquals("Kaffee", booking.description)
        assertEquals("Food", booking.category)
        assertEquals("Cafe", booking.merchantName)
        assertEquals("Latte macchiato", booking.rawDescription)
        assertEquals(1234L, booking.timestamp)
    }

    @Test
    fun createManualBooking_emptyOptionalFieldsBecomeNull() {
        val booking = BookingFactory.createManualBooking(
            idProvider = { "1" },
            accountId = "A",
            amount = 1.0,
            description = " ",
            category = " ",
            merchantName = " ",
            rawDescription = "",
            timestamp = 1L
        )

        assertEquals("", booking.description)
        assertNull(booking.category)
        assertNull(booking.merchantName)
        assertNull(booking.rawDescription)
    }
}
