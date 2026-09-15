package io.github.willywonka644.fintracker

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Wording of the account-deletion confirmation (#113).
 *
 * The point of the dialog is that the number is read, so the sentences have to be right:
 * correct plural forms, and no clause that claims something which is not the case.
 */
class AccountDeletionTextTest {

    private fun impact(
        bookings: Int = 0,
        posted: Int = 0,
        rules: Int = 0,
        attachments: Int = 0,
    ) = AccountDeletionImpact(
        bookings = bookings,
        postedBookings = posted,
        recurringRules = rules,
        attachments = attachments,
    )

    // ── the full sentence ─────────────────────────────────────────────────────

    @Test
    fun namesBookingsAndRulesAndThatItIsFinal() {
        val text = accountDeletionMessage("Girokonto", impact(bookings = 127, posted = 120, rules = 3))

        assertEquals(
            "„Girokonto“ wird mit 127 Buchungen und 3 Daueraufträgen gelöscht " +
                "(auch bereits gebuchte). Das kann nicht rückgängig gemacht werden.",
            text,
        )
    }

    @Test
    fun singularFormsAreCorrect() {
        val text = accountDeletionMessage("Sparbuch", impact(bookings = 1, posted = 1, rules = 1))

        assertTrue(text.contains("mit 1 Buchung und 1 Dauerauftrag gelöscht"), text)
    }

    @Test
    fun aPurelyPlannedAccountIsNotToldItLosesHistory() {
        // Nothing has happened yet on this account, so "auch bereits gebuchte" would be a lie.
        val text = accountDeletionMessage("Neu", impact(bookings = 4, posted = 0))

        assertFalse(text.contains("bereits gebuchte"), text)
        assertTrue(text.contains("mit 4 Buchungen gelöscht."), text)
    }

    @Test
    fun rulesAloneAreNamedWithoutMentioningBookings() {
        val text = accountDeletionMessage("Konto", impact(rules = 2))

        assertTrue(text.contains("mit 2 Daueraufträgen gelöscht"), text)
        assertFalse(text.contains("Buchung"), text)
    }

    // ── the receipt sentence appears only when it is true ─────────────────────

    @Test
    fun receiptsAreCalledOutAsUnrecoverable() {
        val text = accountDeletionMessage("Konto", impact(bookings = 9, posted = 9, attachments = 2))

        assertTrue(text.contains("2 Belege werden unwiderruflich entfernt."), text)
    }

    @Test
    fun oneReceiptUsesTheSingular() {
        val text = accountDeletionMessage("Konto", impact(bookings = 9, posted = 9, attachments = 1))

        assertTrue(text.contains("1 Beleg wird unwiderruflich entfernt."), text)
    }

    @Test
    fun withoutReceiptsTheSentenceIsAbsent() {
        // A threat into the void stops being read; the real dataset carries no receipts today.
        val text = accountDeletionMessage("Konto", impact(bookings = 9, posted = 9))

        assertFalse(text.contains("unwiderruflich entfernt"), text)
    }

    // ── the empty account ─────────────────────────────────────────────────────

    @Test
    fun anEmptyAccountGetsAPlainSentenceWithoutNumbers() {
        val text = accountDeletionMessage("Leer", impact())

        assertEquals(
            "„Leer“ wird gelöscht. Auf diesem Konto liegen keine Buchungen " +
                "und keine Daueraufträge.",
            text,
        )
    }

    // ── the checkbox ──────────────────────────────────────────────────────────

    @Test
    fun checkboxRepeatsTheNumberInTheNominative() {
        assertEquals(
            "Mir ist klar, dass 127 Buchungen und 3 Daueraufträge mitgelöscht werden",
            accountDeletionConfirmLabel(impact(bookings = 127, posted = 120, rules = 3)),
        )
    }

    @Test
    fun checkboxAgreesWithASingleItem() {
        assertEquals(
            "Mir ist klar, dass 1 Buchung mitgelöscht wird",
            accountDeletionConfirmLabel(impact(bookings = 1, posted = 1)),
        )
    }
}
