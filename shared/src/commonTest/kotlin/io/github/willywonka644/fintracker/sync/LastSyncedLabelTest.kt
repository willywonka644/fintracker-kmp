package io.github.willywonka644.fintracker.sync

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The wording of "when did this device last sync" (#131).
 *
 * A fixed zone rather than the system default: the label is built from a local date,
 * so a test that takes the machine's zone would pass here and fail on a build agent
 * three hours away — the same trap the booking data already taught us.
 */
class LastSyncedLabelTest {

    private val tz = TimeZone.of("Europe/Berlin")

    private fun ms(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(tz).toEpochMilliseconds()

    private val now = ms(2026, 9, 13, 12, 30)

    @Test
    fun aSyncFromThisMorningIsCalledToday() {
        assertEquals("heute, 08:14", lastSyncedLabel(ms(2026, 9, 13, 8, 14), now, tz))
    }

    @Test
    fun theDayBeforeIsCalledYesterday() {
        assertEquals("gestern, 21:03", lastSyncedLabel(ms(2026, 9, 12, 21, 3), now, tz))
    }

    @Test
    fun anythingOlderCarriesItsDate() {
        assertEquals("08.09.2026, 07:05", lastSyncedLabel(ms(2026, 9, 8, 7, 5), now, tz))
    }

    @Test
    fun neverSyncedIsNotATime() {
        // 0 heisst "noch nie", nicht "01.01.1970" — die Worte dafuer gehoeren der
        // Oberflaeche, nicht dieser Funktion.
        assertNull(lastSyncedLabel(0L, now, tz))
        assertNull(lastSyncedLabel(-1L, now, tz))
    }

    @Test
    fun aStampFromTheFutureIsNotDressedUpAsToday() {
        // Kommt von einer verstellten Uhr. Es soll auffallen, nicht verschwinden.
        assertEquals("14.09.2026, 09:00", lastSyncedLabel(ms(2026, 9, 14, 9, 0), now, tz))
    }

    @Test
    fun midnightKeepsItsLeadingZeroes() {
        assertEquals("heute, 00:05", lastSyncedLabel(ms(2026, 9, 13, 0, 5), now, tz))
    }
}
