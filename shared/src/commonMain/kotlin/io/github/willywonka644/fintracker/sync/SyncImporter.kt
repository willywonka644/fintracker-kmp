package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.IAccountRepository
import io.github.willywonka644.fintracker.IBookingRepository
import io.github.willywonka644.fintracker.IRecurringRuleRepository
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.ICategoryRepository
import io.github.willywonka644.fintracker.serialization.AppJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class SyncImporter(
    private val accountRepository: IAccountRepository,
    private val bookingRepository: IBookingRepository,
    private val categoryRepository: ICategoryRepository,
    private val recurringRuleRepository: IRecurringRuleRepository
) {
    companion object {
        private val SUPPORTED_VERSIONS = setOf("1.0")
        private val parseJson = Json(AppJson) { isLenient = true }
    }

    // Nullable-field wrapper used only for validation — all fields optional so we
    // can produce the correct SyncError before attempting a full decode.
    @Serializable
    private data class RawSyncFile(
        val version: String? = null,
        val exportedAt: String? = null,
        val deviceId: String? = null,
        val accounts: List<Account>? = null,
        val bookings: List<Booking>? = null,
        val categories: List<Category>? = null,
        val recurringRules: List<RecurringRule>? = null
    )

    fun import(json: String): SyncImportResult {
        // ── 1. Parse ──────────────────────────────────────────────────────────
        val raw = try {
            parseJson.decodeFromString<RawSyncFile>(json)
        } catch (e: SerializationException) {
            return SyncImportResult.Error(SyncError.InvalidJson)
        } catch (e: IllegalArgumentException) {
            return SyncImportResult.Error(SyncError.InvalidJson)
        }

        // ── 2. Validate version ────────────────────────────────────────────────
        if (raw.version == null || raw.version !in SUPPORTED_VERSIONS) {
            return SyncImportResult.Error(SyncError.UnknownVersion)
        }

        // ── 3. Validate required fields ────────────────────────────────────────
        if (raw.exportedAt == null) return SyncImportResult.Error(SyncError.MissingField("exportedAt"))
        if (raw.deviceId == null)   return SyncImportResult.Error(SyncError.MissingField("deviceId"))
        if (raw.accounts == null)   return SyncImportResult.Error(SyncError.MissingField("accounts"))
        if (raw.bookings == null)   return SyncImportResult.Error(SyncError.MissingField("bookings"))
        if (raw.categories == null) return SyncImportResult.Error(SyncError.MissingField("categories"))
        if (raw.recurringRules == null) return SyncImportResult.Error(SyncError.MissingField("recurringRules"))

        // ── 4. Load local data ─────────────────────────────────────────────────
        // Tombstones MUST be part of the merge base: without them an incoming
        // older live copy would look "new" and resurrect the deleted record.
        val localAccounts  = accountRepository.loadAllAccountsForSync().associateBy { it.id }
        val localBookings  = bookingRepository.loadAllBookingsForSync().associateBy { it.id }
        val localCategories = categoryRepository.loadAllCategoriesForSync().associateBy { it.id }
        val localRules     = recurringRuleRepository.loadAllRulesForSync().associateBy { it.id }

        val counters = MergeCounters()
        val warnings = mutableListOf<String>()

        // ── 5. Merge categories ────────────────────────────────────────────────
        val resolvedCategories = mergeEntities(
            localById = localCategories,
            incoming = raw.categories,
            getId = { it.id },
            getLastModifiedAt = { it.lastModifiedAt },
            counters = counters
        )
        // Tombstoned names stay in the set on purpose. This guard only exists to catch
        // references to categories that never existed; treating a deleted-but-still-referenced
        // category as unknown would silently strip the category off the user's bookings.
        val knownCategoryNames = resolvedCategories.map { it.name }.toSet()

        // ── 6. Merge accounts ──────────────────────────────────────────────────
        val resolvedAccounts = mergeEntities(
            localById = localAccounts,
            incoming = raw.accounts,
            getId = { it.id },
            getLastModifiedAt = { it.lastModifiedAt },
            counters = counters
        )
        // Only LIVE accounts are valid targets for incoming live bookings —
        // bookings referencing a (locally or remotely) deleted account are skipped.
        val knownAccountIds = resolvedAccounts.filter { !it.deleted }.map { it.id }.toSet()

        // ── 7. Validate and merge bookings ────────────────────────────────────
        var skippedCount = 0
        val incomingBookingsValidated = mutableListOf<Booking>()

        for (booking in raw.bookings) {
            if (booking.deleted) {
                // Deleted bookings bypass account/category validation — just propagate the deletion
                incomingBookingsValidated.add(booking.copy(attachmentPath = null))
                continue
            }
            // Unknown accountId → skip entirely
            if (booking.accountId !in knownAccountIds) {
                skippedCount++
                warnings.add(
                    "Booking \"${booking.description}\" skipped: " +
                    "account '${booking.accountId}' not found"
                )
                continue
            }
            // Unknown category name → null it out, warn
            val sanitized = if (booking.category != null && booking.category !in knownCategoryNames) {
                warnings.add(
                    "Booking \"${booking.description}\": " +
                    "category '${booking.category}' not found — set to none"
                )
                booking.copy(category = null)
            } else {
                booking
            }
            // Strip attachmentPath on import
            incomingBookingsValidated.add(sanitized.copy(attachmentPath = null))
        }

        val resolvedBookings = mergeEntities(
            localById = localBookings,
            incoming = incomingBookingsValidated,
            getId = { it.id },
            getLastModifiedAt = { it.lastModifiedAt },
            counters = counters
        )

        // ── 8. Validate and merge recurring rules ─────────────────────────────
        val incomingRulesValidated = raw.recurringRules.map { rule ->
            if (!rule.deleted && rule.category != null && rule.category !in knownCategoryNames) {
                warnings.add(
                    "Recurring rule \"${rule.description}\": " +
                    "category '${rule.category}' not found — set to none"
                )
                rule.copy(category = null)
            } else {
                // Tombstones bypass validation — just propagate the deletion.
                rule
            }
        }

        val resolvedRecurringRules = mergeEntities(
            localById = localRules,
            incoming = incomingRulesValidated,
            getId = { it.id },
            getLastModifiedAt = { it.lastModifiedAt },
            counters = counters
        )

        // ── 9. Build preview ──────────────────────────────────────────────────
        return SyncImportResult.Preview(
            SyncPreview(
                newCount = counters.newCount,
                updatedCount = counters.updatedCount,
                unchangedCount = counters.unchangedCount,
                skippedCount = skippedCount,
                warnings = warnings,
                resolvedAccounts = resolvedAccounts,
                resolvedBookings = resolvedBookings,
                resolvedCategories = resolvedCategories,
                resolvedRecurringRules = resolvedRecurringRules
            )
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private class MergeCounters {
        var newCount = 0
        var updatedCount = 0
        var unchangedCount = 0
    }

    private fun <T> mergeEntities(
        localById: Map<String, T>,
        incoming: List<T>,
        getId: (T) -> String,
        getLastModifiedAt: (T) -> Long,
        counters: MergeCounters
    ): List<T> {
        val result = localById.toMutableMap()
        for (record in incoming) {
            val id = getId(record)
            val existing = result[id]
            when {
                existing == null -> {
                    result[id] = record
                    counters.newCount++
                }
                getLastModifiedAt(record) > getLastModifiedAt(existing) -> {
                    result[id] = record
                    counters.updatedCount++
                }
                else -> counters.unchangedCount++
            }
        }
        return result.values.toList()
    }
}
