package io.github.willywonka644.fintracker

import kotlinx.serialization.Serializable

@Serializable
enum class BookingStatus { POSTED, SCHEDULED }

/**
 * Where a booking came from, and — for [TRANSFER] — what it means for the books.
 *
 * [RECONCILIATION] and [TRANSFER] are both kept out of income and expense totals, for the
 * same reason: neither is money entering or leaving. A reconciliation only corrects a
 * balance; a transfer moves money between accounts that both belong to the owner, so
 * counting it would inflate both sides at once.
 *
 * A credit-card settlement is the ordinary case of [TRANSFER], not a special one.
 */
@Serializable
enum class BookingSource { MANUAL, IMPORT, RECONCILIATION, TRANSFER }

@Serializable
data class Booking(
    val id: String,
    val accountId: String,
    val amount: Double,
    val description: String,
    val timestamp: Long,
    val category: String? = null,
    val merchantName: String? = null,
    val rawDescription: String? = null,
    val recurringRuleId: String? = null,
    val status: BookingStatus = BookingStatus.POSTED,
    val effectiveDate: Long? = null,
    val source: BookingSource = BookingSource.MANUAL,
    val importBatchId: String? = null,
    val paymentReference: String? = null,
    val attachmentPath: String? = null,
    val installmentGroupId: String? = null,
    /**
     * Ties the two halves of a transfer together — the outgoing side on one account and the
     * incoming side on the other carry the same value. Same job as [installmentGroupId], and
     * only ever set on bookings with [BookingSource.TRANSFER].
     */
    val transferGroupId: String? = null,
    val ocrRawText: String? = null,
    val isVerified: Boolean = false,
    val lastModifiedAt: Long = 0L,
    val deleted: Boolean = false
)
