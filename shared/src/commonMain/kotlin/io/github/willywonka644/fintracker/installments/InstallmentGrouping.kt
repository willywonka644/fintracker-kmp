package io.github.willywonka644.fintracker.installments

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Represents a group of installment bookings sharing the same [groupId].
 */
data class InstallmentGroup(
    val groupId: String,
    val accountId: String,
    val description: String,
    val totalAmount: Double,
    val installmentAmount: Double,
    val totalCount: Int,
    val paidCount: Int,
    val remainingCount: Int,
    val nextDate: LocalDate?,
    val category: String?
)

/**
 * Builds installment groups from bookings that have [Booking.installmentGroupId] set.
 */
fun buildInstallmentGroups(bookings: List<Booking>, accountId: String): List<InstallmentGroup> {
    val tz = TimeZone.currentSystemDefault()
    val installmentBookings = bookings.filter {
        it.accountId == accountId && it.installmentGroupId != null
    }

    return installmentBookings
        .groupBy { it.installmentGroupId!! }
        .map { (groupId, groupBookings) ->
            val sorted = groupBookings.sortedBy { it.effectiveDate ?: it.timestamp }
            val paidCount = sorted.count { it.status == BookingStatus.POSTED }
            val totalCount = sorted.size
            val nextScheduled = sorted
                .filter { it.status == BookingStatus.SCHEDULED }
                .minByOrNull { it.effectiveDate ?: it.timestamp }

            val nextDate: LocalDate? = nextScheduled?.let {
                val millis = it.effectiveDate ?: it.timestamp
                Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz).date
            }

            InstallmentGroup(
                groupId = groupId,
                accountId = accountId,
                description = sorted.first().description,
                totalAmount = sorted.sumOf { it.amount },
                installmentAmount = sorted.first().amount,
                totalCount = totalCount,
                paidCount = paidCount,
                remainingCount = totalCount - paidCount,
                nextDate = nextDate,
                category = sorted.first().category
            )
        }
        .sortedByDescending { it.remainingCount > 0 }
}
