package io.github.willywonka644.fintracker

interface IBookingRepository {
    fun loadBookings(): List<Booking>
    fun loadAllBookingsForSync(): List<Booking>
    fun reload(): List<Booking>
    fun saveBookings(bookings: List<Booking>)
    fun softDeleteBooking(id: String, deletedAt: Long)
    fun getRawBookingsJson(): String?
    fun replaceBookingsJson(json: String?): Boolean
    fun serializeBookings(bookings: List<Booking>): String
}
