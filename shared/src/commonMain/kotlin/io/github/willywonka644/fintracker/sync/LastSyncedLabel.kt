package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.util.toGermanDateString
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * When this device last synced, in words — "heute, 08:14" (#131).
 *
 * The stamp has been kept since #110/#114 but was only ever counted against, never
 * shown. That left the sync card saying "Nichts offen", which reads as "everything is
 * in sync" although it only knows one direction: no request goes to the server until
 * someone asks for one. A device can honestly say when it last looked; from that the
 * reader can judge the rest.
 *
 * Returns `null` when there has never been a sync — the caller words that, because
 * "never" and "a while ago" are different sentences, not different times.
 */
fun lastSyncedLabel(lastSyncedAt: Long, now: Long, tz: TimeZone): String? {
    if (lastSyncedAt <= 0L) return null

    val stamp = Instant.fromEpochMilliseconds(lastSyncedAt).toLocalDateTime(tz)
    val today = Instant.fromEpochMilliseconds(now).toLocalDateTime(tz).date
    val time = "${stamp.hour.toString().padStart(2, '0')}:${stamp.minute.toString().padStart(2, '0')}"

    return when (stamp.date) {
        today -> "heute, $time"
        today.minus(1, DateTimeUnit.DAY) -> "gestern, $time"
        // Ein Datum in der Zukunft landet ebenfalls hier. Das kommt von einer
        // verstellten Uhr und soll als das dastehen, was es ist, statt "heute"
        // zu behaupten.
        else -> "${stamp.date.toGermanDateString()}, $time"
    }
}
