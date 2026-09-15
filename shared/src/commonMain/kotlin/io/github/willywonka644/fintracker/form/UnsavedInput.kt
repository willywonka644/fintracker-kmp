package io.github.willywonka644.fintracker.form

/**
 * One editable field, as it is now against the value the sheet opened with.
 * Values are compared as text so dates and enums can take part without this
 * file having to know about them.
 */
data class FieldState(val current: String, val baseline: String) {
    val changed: Boolean get() = current != baseline
}

/**
 * Does the booking sheet hold anything the user would be sorry to lose?
 *
 * Tapping the scrim or swiping the sheet down dismisses it. Asking "discard?"
 * every single time trains people to dismiss the question, so a sheet that was
 * merely opened by accident has to close without a word — hence the comparison
 * against the values the sheet started with rather than against empty strings.
 *
 * [openedWithScannedData] is the part that comparison alone gets wrong. A sheet
 * prefilled from a receipt scan or a QR code starts out already holding content,
 * so nothing has "changed" and the sheet used to close silently — throwing away
 * the most expensive input in the app, since getting there costs a menu, an
 * account pick, a photo and a wait for recognition.
 *
 * Pass **every** field the user can set. A field left out of [fields] is a field
 * that vanishes without a question: category and date were missing at first, so
 * picking a category and swiping the sheet away lost it silently.
 */
fun hasContentWorthKeeping(
    openedWithScannedData: Boolean,
    hasNewAttachment: Boolean,
    fields: List<FieldState>,
): Boolean =
    openedWithScannedData || hasNewAttachment || fields.any { it.changed }
