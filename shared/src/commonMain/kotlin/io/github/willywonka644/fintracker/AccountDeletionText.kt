package io.github.willywonka644.fintracker

/**
 * The wording of the account-deletion confirmation (#113).
 *
 * Pulled out of the dialog so the parts that can actually be wrong — plural forms and
 * the sentences that must only appear when they are true — are testable without a
 * Compose harness. Moved to `shared` with #114: the desktop asks the same question about
 * the same cascade, and a second wording would be a second thing to get wrong. The pattern follows the one the installment deletion already uses:
 * name what goes, how much of it, and that it is final.
 */

private fun bookingsPhrase(count: Int) = if (count == 1) "1 Buchung" else "$count Buchungen"

private fun rulesPhrase(count: Int, dative: Boolean) = when {
    count == 1 -> "1 Dauerauftrag"
    dative -> "$count Daueraufträgen"
    else -> "$count Daueraufträge"
}

/** "127 Buchungen und 3 Daueraufträgen" (dative, follows "mit") or the nominative form. */
private fun impactPhrase(impact: AccountDeletionImpact, dative: Boolean): String =
    listOfNotNull(
        impact.bookings.takeIf { it > 0 }?.let { bookingsPhrase(it) },
        impact.recurringRules.takeIf { it > 0 }?.let { rulesPhrase(it, dative) },
    ).joinToString(" und ")

fun accountDeletionMessage(accountName: String, impact: AccountDeletionImpact): String {
    if (impact.isEmpty) {
        return "„$accountName“ wird gelöscht. Auf diesem Konto liegen keine Buchungen " +
            "und keine Daueraufträge."
    }

    val builder = StringBuilder(
        "„$accountName“ wird mit ${impactPhrase(impact, dative = true)} gelöscht"
    )

    // Only when something actually happened already — for a purely planned account the
    // clause would claim a loss of history that does not exist.
    if (impact.postedBookings > 0) builder.append(" (auch bereits gebuchte)")
    builder.append(".")

    // Bookings can be restored from a JSON backup, receipt files cannot — they are
    // removed from storage, not tombstoned. Said only when there are any, otherwise it
    // is a threat into the void and stops being read.
    if (impact.attachments > 0) {
        val belege =
            if (impact.attachments == 1) "1 Beleg wird" else "${impact.attachments} Belege werden"
        builder.append(" $belege unwiderruflich entfernt.")
    }

    builder.append(" Das kann nicht rückgängig gemacht werden.")
    return builder.toString()
}

/** Label of the checkbox that arms the delete button. Carries the number again on purpose. */
fun accountDeletionConfirmLabel(impact: AccountDeletionImpact): String {
    val single = impact.bookings + impact.recurringRules == 1
    val verb = if (single) "wird" else "werden"
    return "Mir ist klar, dass ${impactPhrase(impact, dative = false)} mitgelöscht $verb"
}
