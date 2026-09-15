package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.db.FintrackerDatabase

/**
 * Commits a confirmed [SyncPreview] to the database in a single atomic transaction.
 * Uses INSERT OR REPLACE (upsert) for every record — no deleteAll, so local-only
 * records that do not appear in the sync file are never deleted.
 *
 * After a successful write the caller must reload in-memory state via
 * repository.reload() for each entity type.
 */
class SyncWriter(private val db: FintrackerDatabase) {

    fun write(preview: SyncPreview): SyncResult {
        return try {
            db.transaction {
                // ── Categories (must come before bookings / rules that reference them) ──
                for (category in preview.resolvedCategories) {
                    db.categoriesQueries.insert(
                        id             = category.id,
                        name           = category.name,
                        iconName       = category.iconName,
                        color          = category.color,
                        isDefault      = category.isDefault,
                        lastModifiedAt = category.lastModifiedAt,
                        deleted        = category.deleted
                    )
                }

                // ── Accounts ──────────────────────────────────────────────────
                for (account in preview.resolvedAccounts) {
                    db.accountsQueries.insert(
                        id             = account.id,
                        name           = account.name,
                        type           = account.type,
                        billingStartDay = account.billingStartDay?.toLong(),
                        spendingLimit  = account.spendingLimit,
                        lastModifiedAt = account.lastModifiedAt,
                        deleted        = account.deleted
                    )
                }

                // ── Bookings ──────────────────────────────────────────────────
                for (booking in preview.resolvedBookings) {
                    db.bookingsQueries.insert(
                        id                 = booking.id,
                        accountId          = booking.accountId,
                        amount             = booking.amount,
                        description        = booking.description,
                        timestamp          = booking.timestamp,
                        category           = booking.category,
                        merchantName       = booking.merchantName,
                        rawDescription     = booking.rawDescription,
                        recurringRuleId    = booking.recurringRuleId,
                        status             = booking.status,
                        effectiveDate      = booking.effectiveDate,
                        source             = booking.source,
                        importBatchId      = booking.importBatchId,
                        paymentReference   = booking.paymentReference,
                        attachmentPath     = booking.attachmentPath,
                        installmentGroupId = booking.installmentGroupId,
                        transferGroupId    = booking.transferGroupId,
                        ocrRawText         = booking.ocrRawText,
                        isVerified         = booking.isVerified,
                        lastModifiedAt     = booking.lastModifiedAt,
                        deleted            = booking.deleted
                    )
                }

                // ── Recurring rules ───────────────────────────────────────────
                for (rule in preview.resolvedRecurringRules) {
                    db.recurring_rulesQueries.insert(
                        id                  = rule.id,
                        amount              = rule.amount,
                        description         = rule.description,
                        accountId           = rule.accountId,
                        frequency           = rule.frequency,
                        nextExecutionDate   = rule.nextExecutionDate,
                        remainingExecutions = rule.remainingExecutions?.toLong(),
                        category            = rule.category,
                        excludedDates       = rule.excludedDates,
                        lastModifiedAt      = rule.lastModifiedAt,
                        deleted             = rule.deleted
                    )
                }
            }
            SyncResult.Success(
                newCount     = preview.newCount,
                updatedCount = preview.updatedCount
            )
        } catch (e: Exception) {
            SyncResult.Error(SyncError.WriteFailed)
        }
    }
}
