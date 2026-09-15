package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime

object BookingFactory {
    fun createManualBooking(
        idProvider: () -> String,
        accountId: String,
        amount: Double,
        description: String,
        category: String?,
        merchantName: String? = null,
        rawDescription: String? = null,
        timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        recurringRuleId: String? = null,
        effectiveDate: Long? = null,
        source: BookingSource = BookingSource.MANUAL,
        importBatchId: String? = null,
        paymentReference: String? = null,
        attachmentPath: String? = null,
        ocrRawText: String? = null,
        transferGroupId: String? = null
    ): Booking {
        val trimmedDescription = description.trim()
        val normalizedCategory = category?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedMerchant = merchantName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedRaw = rawDescription?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedReference = paymentReference?.trim()?.takeIf { it.isNotEmpty() }

        // Use effectiveDate for status determination when present, fall back to timestamp
        val dateForStatus = effectiveDate ?: timestamp
        val timeZone = TimeZone.currentSystemDefault()
        val statusDate = Instant.fromEpochMilliseconds(dateForStatus)
            .toLocalDateTime(timeZone)
            .date
        val status = if (statusDate > Clock.System.todayIn(timeZone)) {
            BookingStatus.SCHEDULED
        } else {
            BookingStatus.POSTED
        }

        return Booking(
            id = idProvider(),
            accountId = accountId,
            amount = amount,
            description = trimmedDescription,
            timestamp = timestamp,
            category = normalizedCategory,
            merchantName = normalizedMerchant,
            rawDescription = normalizedRaw,
            recurringRuleId = recurringRuleId,
            status = status,
            effectiveDate = effectiveDate,
            source = source,
            importBatchId = importBatchId,
            paymentReference = normalizedReference,
            attachmentPath = attachmentPath,
            ocrRawText = ocrRawText?.trim()?.takeIf { it.isNotEmpty() },
            transferGroupId = transferGroupId,
            lastModifiedAt = Clock.System.now().toEpochMilliseconds()
        )
    }
}
