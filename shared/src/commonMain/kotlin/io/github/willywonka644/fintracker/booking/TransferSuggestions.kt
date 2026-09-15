package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.math.abs
import kotlin.math.round

/**
 * Finds pairs of existing bookings that look like the two halves of one transfer (#122).
 *
 * The stock predates [BookingSource.TRANSFER]: money moved between the owner's own accounts
 * was entered as an ordinary expense on one account and an ordinary income on the other, and
 * both then counted. Measured on the real database, that is 14.6% of reported spending and
 * 12.9% of income.
 *
 * This only ever *suggests*. A pair is a guess about someone's intent, and the guess is
 * confirmed one at a time — never in bulk.
 */
object TransferSuggestions {

    /**
     * How far apart the two halves may fall, in days.
     *
     * Measured against the real database on 10.09.2026, and this is the sharp edge in both
     * directions. Up to 14 days no booking has more than one possible partner, so the
     * matching is unambiguous rather than guessed. The first false match appears at 16 days
     * (a haircut and a window repair that happen to both be 22.00 €), and by 30 days it
     * collapses: 43 pairs, 30 of them ambiguous, because the monthly savings transfers start
     * matching each other across months.
     *
     * It cannot be tightened either: card settlements land on the card and on the current
     * account six to eleven days apart, so at ±3 days not one of them would be found.
     */
    const val TOLERANCE_DAYS = 14

    private const val DAY_MILLIS = 86_400_000L

    /** A booking's own date — [Booking.effectiveDate] where set, as everywhere else. */
    private fun Booking.dateMillis(): Long = effectiveDate ?: timestamp

    /** Stable identity of a pair, independent of which side was found first. */
    fun pairKey(idA: String, idB: String): String =
        if (idA <= idB) "$idA|$idB" else "$idB|$idA"

    fun parseRejected(raw: String?): Set<String> =
        raw?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    fun withRejection(raw: String?, pairKey: String): String =
        (parseRejected(raw) + pairKey).sorted().joinToString("\n")

    /**
     * Candidate pairs, closest in time first.
     *
     * A booking appears in at most one candidate: the closest match wins and both sides are
     * then spoken for. Without that, one booking could be confirmed into two different
     * transfers and end up with the wrong partner — which the measured data does not
     * currently produce, but a rule that relies on the data staying that way is not a rule.
     *
     * Skipped: bookings already marked [BookingSource.TRANSFER] (nothing to suggest),
     * deleted rows, and pairs whose key is in [rejected].
     *
     * [BookingSource.RECONCILIATION] is deliberately **not** skipped, though a correction is
     * not a movement of money. Measured on the real database: four settlement counter-entries
     * are typed as corrections — "Kreditkartenabrechnung Ausgleich" — evidently so they would
     * not count as income. They are transfers wearing the wrong type, exactly the workaround
     * #118 sets out to retire, and excluding them here would leave the only four the tool
     * cannot reach. The two genuine Kontoabgleich corrections have no mirrored partner and so
     * never surface. Candidates involving one are flagged with [TransferCandidate.wasCorrection]
     * so the confirmation shows what it is changing.
     */
    fun find(
        bookings: List<Booking>,
        toleranceDays: Int = TOLERANCE_DAYS,
        rejected: Set<String> = emptySet(),
    ): List<TransferCandidate> {
        val tolerance = toleranceDays * DAY_MILLIS
        val eligible = bookings.filter {
            !it.deleted &&
                it.source != BookingSource.TRANSFER &&
                it.amount != 0.0
        }
        val outgoing = eligible.filter { it.amount < 0.0 }
        val incoming = eligible.filter { it.amount > 0.0 }

        val matches = mutableListOf<TransferCandidate>()
        for (out in outgoing) {
            for (into in incoming) {
                if (out.accountId == into.accountId) continue
                if (round(abs(out.amount) * 100) != round(into.amount * 100)) continue
                val apart = abs(out.dateMillis() - into.dateMillis())
                if (apart > tolerance) continue
                if (pairKey(out.id, into.id) in rejected) continue
                matches += TransferCandidate(out, into, (apart / DAY_MILLIS).toInt())
            }
        }

        val taken = mutableSetOf<String>()
        return matches
            .sortedWith(compareBy({ it.daysApart }, { it.outgoing.dateMillis() }))
            .filter { candidate ->
                if (candidate.outgoing.id in taken || candidate.incoming.id in taken) {
                    false
                } else {
                    taken += candidate.outgoing.id
                    taken += candidate.incoming.id
                    true
                }
            }
            .sortedBy { it.outgoing.dateMillis() }
    }
}

/**
 * The two bookings as they look once the pair is confirmed: both marked
 * [BookingSource.TRANSFER], both carrying [groupId], both restamped with [now] so the
 * change wins the last-write-wins merge on the other devices.
 *
 * The category is deliberately left alone. A transfer created from scratch carries none,
 * but overwriting what is already there would throw away the only description some of
 * these rows have ("Finanzen/Investment" on the savings transfers) — and the analytics do
 * not read it either way, they go by the type.
 *
 * ⚠️ Until #123 has booked the four settlements that have no counterpart at all, the legacy
 * category check in `excludeTransfers()` has to stay: those four cannot be paired here, and
 * dropping the check early would let them count as ordinary expenses.
 */
fun TransferCandidate.confirm(groupId: String, now: Long): Pair<Booking, Booking> =
    outgoing.copy(
        source = BookingSource.TRANSFER,
        transferGroupId = groupId,
        lastModifiedAt = now,
    ) to incoming.copy(
        source = BookingSource.TRANSFER,
        transferGroupId = groupId,
        lastModifiedAt = now,
    )

/**
 * One suggested transfer: the side money left from, the side it arrived on, and how many
 * whole days lie between them.
 */
data class TransferCandidate(
    val outgoing: Booking,
    val incoming: Booking,
    val daysApart: Int,
) {
    val key: String get() = TransferSuggestions.pairKey(outgoing.id, incoming.id)
    val amount: Double get() = abs(outgoing.amount)

    /**
     * At least one side is currently typed as a Kontoabgleich correction.
     *
     * Worth showing before confirming: the booking is already being kept out of the figures,
     * just by the wrong means, and confirming changes what it claims to be.
     */
    val wasCorrection: Boolean
        get() = outgoing.source == BookingSource.RECONCILIATION ||
            incoming.source == BookingSource.RECONCILIATION
}
