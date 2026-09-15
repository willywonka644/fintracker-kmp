package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.serialization.AppJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class SqlBookingRepository(private val db: FintrackerDatabase) : IBookingRepository {

    private val queries = db.bookingsQueries

    override fun loadBookings(): List<Booking> =
        queries.selectAll().executeAsList().map { row ->
            Booking(
                id = row.id,
                accountId = row.accountId,
                amount = row.amount,
                description = row.description,
                timestamp = row.timestamp,
                category = row.category,
                merchantName = row.merchantName,
                rawDescription = row.rawDescription,
                recurringRuleId = row.recurringRuleId,
                status = row.status,
                effectiveDate = row.effectiveDate,
                source = row.source,
                importBatchId = row.importBatchId,
                paymentReference = row.paymentReference,
                attachmentPath = row.attachmentPath,
                installmentGroupId = row.installmentGroupId,
                transferGroupId = row.transferGroupId,
                ocrRawText = row.ocrRawText,
                isVerified = row.isVerified,
                lastModifiedAt = row.lastModifiedAt,
                deleted = row.deleted
            )
        }

    override fun loadAllBookingsForSync(): List<Booking> =
        queries.selectAllIncludingDeleted().executeAsList().map { row ->
            Booking(
                id = row.id,
                accountId = row.accountId,
                amount = row.amount,
                description = row.description,
                timestamp = row.timestamp,
                category = row.category,
                merchantName = row.merchantName,
                rawDescription = row.rawDescription,
                recurringRuleId = row.recurringRuleId,
                status = row.status,
                effectiveDate = row.effectiveDate,
                source = row.source,
                importBatchId = row.importBatchId,
                paymentReference = row.paymentReference,
                attachmentPath = row.attachmentPath,
                installmentGroupId = row.installmentGroupId,
                transferGroupId = row.transferGroupId,
                ocrRawText = row.ocrRawText,
                isVerified = row.isVerified,
                lastModifiedAt = row.lastModifiedAt,
                deleted = row.deleted
            )
        }

    override fun reload(): List<Booking> = loadBookings()

    override fun saveBookings(bookings: List<Booking>) {
        db.transaction {
            val softDeleted = queries.selectAllIncludingDeleted().executeAsList()
                .filter { it.deleted }
            queries.deleteAll()
            bookings.forEach { b ->
                queries.insert(
                    id = b.id,
                    accountId = b.accountId,
                    amount = b.amount,
                    description = b.description,
                    timestamp = b.timestamp,
                    category = b.category,
                    merchantName = b.merchantName,
                    rawDescription = b.rawDescription,
                    recurringRuleId = b.recurringRuleId,
                    status = b.status,
                    effectiveDate = b.effectiveDate,
                    source = b.source,
                    importBatchId = b.importBatchId,
                    paymentReference = b.paymentReference,
                    attachmentPath = b.attachmentPath,
                    installmentGroupId = b.installmentGroupId,
                    transferGroupId = b.transferGroupId,
                    ocrRawText = b.ocrRawText,
                    isVerified = b.isVerified,
                    lastModifiedAt = b.lastModifiedAt,
                    deleted = b.deleted
                )
            }
            softDeleted.forEach { row ->
                queries.insert(
                    id = row.id,
                    accountId = row.accountId,
                    amount = row.amount,
                    description = row.description,
                    timestamp = row.timestamp,
                    category = row.category,
                    merchantName = row.merchantName,
                    rawDescription = row.rawDescription,
                    recurringRuleId = row.recurringRuleId,
                    status = row.status,
                    effectiveDate = row.effectiveDate,
                    source = row.source,
                    importBatchId = row.importBatchId,
                    paymentReference = row.paymentReference,
                    attachmentPath = row.attachmentPath,
                    installmentGroupId = row.installmentGroupId,
                    transferGroupId = row.transferGroupId,
                    ocrRawText = row.ocrRawText,
                    isVerified = row.isVerified,
                    lastModifiedAt = row.lastModifiedAt,
                    deleted = row.deleted
                )
            }
        }
    }

    override fun softDeleteBooking(id: String, deletedAt: Long) {
        queries.softDelete(lastModifiedAt = deletedAt, id = id)
    }

    override fun getRawBookingsJson(): String? {
        val bookings = loadBookings()
        return if (bookings.isEmpty()) null else serializeBookings(bookings)
    }

    override fun replaceBookingsJson(json: String?): Boolean {
        return try {
            val bookings = if (json == null) emptyList()
            else AppJson.decodeFromString<List<Booking>>(json)
            saveBookings(bookings)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun serializeBookings(bookings: List<Booking>): String =
        AppJson.encodeToString(bookings)
}
