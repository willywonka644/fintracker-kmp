package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource

/**
 * Builds the two halves of a transfer — money leaving one of the owner's accounts and
 * arriving on another (#118).
 *
 * Both halves are created together and share a [Booking.transferGroupId], because a
 * half transfer is worse than none: it moves the totals by the full amount in one
 * direction. Everything that later acts on a transfer — the analytics that drop it,
 * the deletion that takes both sides — keys off that id, never off amounts and dates.
 *
 * The two dates are deliberately independent. A credit-card settlement really does
 * land on the card and on the current account on different days — measured on the
 * real database, six to eleven days apart.
 */
object TransferFactory {

    /**
     * Why a transfer cannot be created as given. `null` means it can.
     *
     * Separate from [create] so a form can show the reason while the button is still
     * disabled, rather than finding out by way of an exception.
     */
    fun validate(
        fromAccountId: String?,
        toAccountId: String?,
        amount: Double?,
    ): String? = when {
        fromAccountId.isNullOrBlank() -> "Bitte ein Konto wählen, von dem gebucht wird."
        toAccountId.isNullOrBlank() -> "Bitte ein Konto wählen, auf das gebucht wird."
        fromAccountId == toAccountId -> "Eine Umbuchung braucht zwei verschiedene Konten."
        amount == null -> "Bitte einen Betrag eingeben."
        amount <= 0.0 -> "Der Betrag muss größer als null sein."
        else -> null
    }

    /**
     * The outgoing side first, the incoming side second.
     *
     * [amount] is the positive sum being moved; the signs are applied here so a caller
     * cannot get them the wrong way round. Status (POSTED or SCHEDULED) is decided per
     * side from its own date, the same way [BookingFactory] does it — a settlement
     * booked ahead on one account and already posted on the other is a real case.
     */
    fun create(
        idProvider: () -> String,
        groupIdProvider: () -> String,
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        description: String,
        fromDate: Long,
        toDate: Long,
    ): Pair<Booking, Booking> {
        validate(fromAccountId, toAccountId, amount)?.let { throw IllegalArgumentException(it) }

        val groupId = groupIdProvider()
        val out = BookingFactory.createManualBooking(
            idProvider = idProvider,
            accountId = fromAccountId,
            amount = -amount,
            description = description,
            // A transfer is not spending, so it carries no category — and with no category
            // there is nothing to rename that could switch its handling off.
            category = null,
            timestamp = fromDate,
            effectiveDate = fromDate,
            source = BookingSource.TRANSFER,
            transferGroupId = groupId,
        )
        val incoming = BookingFactory.createManualBooking(
            idProvider = idProvider,
            accountId = toAccountId,
            amount = amount,
            description = description,
            category = null,
            timestamp = toDate,
            effectiveDate = toDate,
            source = BookingSource.TRANSFER,
            transferGroupId = groupId,
        )
        return out to incoming
    }
}

/** Both sides of the transfer [groupId] belongs to, outgoing first. Deleted rows are skipped. */
fun List<Booking>.transferGroup(groupId: String): List<Booking> =
    filter { it.transferGroupId == groupId && !it.deleted }.sortedBy { it.amount }

/**
 * The other half of [booking], or null if it has none.
 *
 * Null is a real answer, not only a programming error: the stock carries settlements
 * whose counter-entry was never booked at all (#123), and #122 can mark one side of a
 * pair before the other exists.
 */
fun List<Booking>.transferCounterpart(booking: Booking): Booking? {
    val groupId = booking.transferGroupId ?: return null
    return firstOrNull { it.id != booking.id && it.transferGroupId == groupId && !it.deleted }
}
