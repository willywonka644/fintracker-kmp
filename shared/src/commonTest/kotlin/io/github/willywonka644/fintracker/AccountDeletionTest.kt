package io.github.willywonka644.fintracker

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a confirmation is allowed to promise before an account is deleted (#113).
 *
 * The deletion propagates as tombstones, so the copy on the other device goes with it —
 * only a backup can undo this. The numbers here are what the user is told beforehand,
 * which makes them worth pinning.
 */
class AccountDeletionTest {

    private fun booking(
        id: String,
        accountId: String = "acc",
        status: BookingStatus = BookingStatus.POSTED,
        attachment: String? = null,
        deleted: Boolean = false,
    ) = Booking(
        id = id,
        accountId = accountId,
        amount = -10.0,
        description = "Buchung $id",
        timestamp = 0L,
        status = status,
        attachmentPath = attachment,
        deleted = deleted,
    )

    private fun rule(id: String, accountId: String = "acc", deleted: Boolean = false) = RecurringRule(
        id = id,
        amount = -10.0,
        description = "Regel $id",
        accountId = accountId,
        frequency = Frequency.MONTHLY,
        nextExecutionDate = LocalDate(2026, 1, 1),
        deleted = deleted,
    )

    @Test
    fun countsOnlyTheAccountBeingDeleted() {
        val impact = accountDeletionImpact(
            accountId = "acc",
            bookings = listOf(booking("a"), booking("b"), booking("c", accountId = "other")),
            recurringRules = listOf(rule("r1"), rule("r2", accountId = "other")),
        )

        assertEquals(2, impact.bookings)
        assertEquals(1, impact.recurringRules)
    }

    @Test
    fun separatesWhatAlreadyHappenedFromWhatWasOnlyPlanned() {
        // "auch bereits gebuchte" is the part of the sentence that carries the weight —
        // a scheduled booking is a plan, a posted one is money that moved.
        val impact = accountDeletionImpact(
            accountId = "acc",
            bookings = listOf(
                booking("a"),
                booking("b"),
                booking("c", status = BookingStatus.SCHEDULED),
            ),
            recurringRules = emptyList(),
        )

        assertEquals(3, impact.bookings)
        assertEquals(2, impact.postedBookings)
    }

    @Test
    fun countsReceiptsSeparatelyBecauseNoBackupBringsThemBack() {
        val impact = accountDeletionImpact(
            accountId = "acc",
            bookings = listOf(
                booking("a", attachment = "/data/beleg-a.jpg"),
                booking("b", attachment = ""),
                booking("c"),
            ),
            recurringRules = emptyList(),
        )

        // A blank path is no receipt — it would otherwise promise a loss that cannot happen.
        assertEquals(1, impact.attachments)
    }

    @Test
    fun tombstonesDoNotCount() {
        // They are already gone. Counting them would tell the user they lose something twice.
        val impact = accountDeletionImpact(
            accountId = "acc",
            bookings = listOf(booking("a"), booking("b", deleted = true)),
            recurringRules = listOf(rule("r1"), rule("r2", deleted = true)),
        )

        assertEquals(1, impact.bookings)
        assertEquals(1, impact.recurringRules)
    }

    @Test
    fun anEmptyAccountReportsNothingAtStake() {
        val impact = accountDeletionImpact("acc", emptyList(), emptyList())

        assertEquals(0, impact.bookings)
        assertEquals(0, impact.postedBookings)
        assertEquals(0, impact.recurringRules)
        assertEquals(0, impact.attachments)
        assertTrue(impact.isEmpty)
    }

    @Test
    fun anAccountWithOnlyARuleIsNotEmpty() {
        val impact = accountDeletionImpact("acc", emptyList(), listOf(rule("r1")))
        assertFalse(impact.isEmpty)
    }
}
