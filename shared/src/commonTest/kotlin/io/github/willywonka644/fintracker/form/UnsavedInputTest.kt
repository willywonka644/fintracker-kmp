package io.github.willywonka644.fintracker.form

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F1 (Phase 6.5) stopped the booking sheet from throwing away typed input when it
 * was dismissed. It compared against the values the sheet opened with, which left
 * two holes: a sheet prefilled from a receipt scan opens with content already in
 * it, so nothing had "changed"; and category and date were never compared at all.
 */
class UnsavedInputTest {

    /** Mirrors the sheet: five fields, all starting out equal to their baseline. */
    private fun check(
        scanned: Boolean = false,
        attachment: Boolean = false,
        amount: Pair<String, String> = "" to "",
        description: Pair<String, String> = "" to "",
        installments: Pair<String, String> = "" to "",
        category: Pair<String, String> = "" to "",
        date: Pair<String, String> = "2026-08-16" to "2026-08-16",
    ) = hasContentWorthKeeping(
        openedWithScannedData = scanned,
        hasNewAttachment = attachment,
        fields = listOf(amount, description, installments, category, date)
            .map { (current, baseline) -> FieldState(current, baseline) },
    )

    @Test
    fun anEmptySheetOpenedByAccidentClosesWithoutAsking() {
        assertFalse(check())
    }

    @Test
    fun typedAmountCounts() {
        assertTrue(check(amount = "12,50" to ""))
    }

    @Test
    fun typedDescriptionCounts() {
        assertTrue(check(description = "Rewe" to ""))
    }

    @Test
    fun installmentCountCounts() {
        assertTrue(check(installments = "6" to ""))
    }

    @Test
    fun pickedCategoryCounts() {
        // Was missing from the comparison: picking a category and swiping the
        // sheet away lost the choice without a word.
        assertTrue(check(category = "Lebensmittel" to ""))
    }

    @Test
    fun changedDateCounts() {
        assertTrue(check(date = "2026-08-01" to "2026-08-16"))
    }

    @Test
    fun aFreshlyAttachedPhotoCounts() {
        // The photo is already copied into app storage at this point; dismissing
        // without asking would strand the file as well as lose the booking.
        assertTrue(check(attachment = true))
    }

    @Test
    fun openingAnExistingBookingAndChangingNothingClosesWithoutAsking() {
        // Edit mode seeds the baselines from the stored booking, so an untouched
        // sheet has to look empty to this rule even though the fields are full.
        assertFalse(
            check(
                amount = "40,00" to "40,00",
                description = "Miete" to "Miete",
                category = "Wohnen" to "Wohnen",
            )
        )
    }

    @Test
    fun editingAnExistingBookingCounts() {
        assertTrue(
            check(
                amount = "45,00" to "40,00",
                description = "Miete" to "Miete",
                category = "Wohnen" to "Wohnen",
            )
        )
    }

    @Test
    fun recategorisingAnExistingBookingCounts() {
        assertTrue(
            check(
                amount = "40,00" to "40,00",
                description = "Miete" to "Miete",
                category = "Sonstiges" to "Wohnen",
            )
        )
    }

    @Test
    fun scannedDataCountsEvenThoughNothingWasTypedAfterwards() {
        // The regression this rule exists for. Getting here costs a menu, an
        // account pick, a photo and a wait — losing it silently is the worst
        // outcome of any dismissal in the app.
        assertTrue(
            check(
                scanned = true,
                amount = "12,50" to "12,50",
                description = "Rewe" to "Rewe",
            )
        )
    }

    @Test
    fun scannedDataCountsEvenIfTheParserFoundOnlyOneField() {
        // The scan prefills whatever it recognised; a merchant without an amount
        // is still work the user does not want to repeat.
        assertTrue(check(scanned = true, description = "Rewe" to "Rewe"))
    }
}
