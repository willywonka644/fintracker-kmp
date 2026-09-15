package io.github.willywonka644.fintracker

/**
 * What deleting an account takes with it (#113).
 *
 * The deletion cascades over the account's bookings and rules and propagates as
 * tombstones to every other device, so the other device is not a fallback — only a
 * backup is. The confirmation has to be able to say what is at stake, and that means
 * counting it before anything is written.
 */
data class AccountDeletionImpact(
    /** Live bookings on the account — what the user would lose. */
    val bookings: Int,
    /** Of those, the ones that already happened. These are history, not plans. */
    val postedBookings: Int,
    /** Live recurring rules on the account. */
    val recurringRules: Int,
    /** Bookings carrying a receipt file. Those files are deleted for good, not tombstoned. */
    val attachments: Int,
) {
    val isEmpty: Boolean get() = bookings == 0 && recurringRules == 0
}

/**
 * Counts what a deletion of [accountId] would take.
 *
 * Tombstones are left out on purpose: they are already gone, and the number is meant
 * to answer "what do I lose", not "how many rows are touched".
 */
fun accountDeletionImpact(
    accountId: String,
    bookings: List<Booking>,
    recurringRules: List<RecurringRule>,
): AccountDeletionImpact {
    val affected = bookings.filter { !it.deleted && it.accountId == accountId }
    return AccountDeletionImpact(
        bookings = affected.size,
        postedBookings = affected.count { it.status == BookingStatus.POSTED },
        recurringRules = recurringRules.count { !it.deleted && it.accountId == accountId },
        attachments = affected.count { !it.attachmentPath.isNullOrBlank() },
    )
}
