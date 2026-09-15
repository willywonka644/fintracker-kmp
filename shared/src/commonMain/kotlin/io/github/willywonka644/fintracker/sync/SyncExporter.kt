package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.IAccountRepository
import io.github.willywonka644.fintracker.IBookingRepository
import io.github.willywonka644.fintracker.IRecurringRuleRepository
import io.github.willywonka644.fintracker.ISettingsRepository
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.ICategoryRepository
import io.github.willywonka644.fintracker.serialization.AppJson
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SyncExporter(
    private val accountRepository: IAccountRepository,
    private val bookingRepository: IBookingRepository,
    private val categoryRepository: ICategoryRepository,
    private val recurringRuleRepository: IRecurringRuleRepository,
    private val settingsRepository: ISettingsRepository
) {
    companion object {
        const val VERSION = "1.0"
        private val syncJson = Json(AppJson) { prettyPrint = true }
    }

    fun export(): String {
        val bookingsWithoutAttachments = bookingRepository.loadAllBookingsForSync()
            .map { it.copy(attachmentPath = null) }
        // Include soft-deleted tombstones so a deletion on this device wins over
        // the other device's live copy in the last-writer-wins merge.
        val recurringRules = recurringRuleRepository.loadAllRulesForSync()

        // Include soft-deleted categories so a deletion propagates, same as accounts and rules.
        val knownCategories = categoryRepository.loadAllCategoriesForSync()
        // Deliberately counts tombstoned names as known. Bookings reference categories by
        // name, so a deleted category whose name is still in use would otherwise look like
        // an orphan and get resurrected below under a "recovered-" id — undoing the deletion
        // on every export.
        val knownNames = knownCategories.map { it.name }.toSet()

        // Collect category names referenced by bookings or recurring rules that
        // have no matching row in the category table (orphans from migration or
        // a bug where a booking was saved with a free-text name that was never
        // persisted as a Category record). Tombstones don't spawn categories.
        val orphanNames = (
            bookingsWithoutAttachments.filter { !it.deleted }.mapNotNull { it.category } +
            recurringRules.filter { !it.deleted }.mapNotNull { it.category }
        ).filter { it !in knownNames }.toSet()

        val syntheticCategories = orphanNames.map { name ->
            Category(
                // Deterministic ID so the same orphan exported from two devices
                // always gets the same key and merges correctly on import.
                id = "recovered-${name.lowercase().replace(Regex("[^a-z0-9]"), "-")}",
                name = name,
                iconName = "MoreHoriz",
                color = 0xFF8D6E63, // neutral brown — same as default "Sonstiges"
                isDefault = false,
                lastModifiedAt = 0L
            )
        }

        val payload = SyncPayload(
            version = VERSION,
            exportedAt = Clock.System.now().toString(),
            deviceId = settingsRepository.getDeviceId(),
            accounts = accountRepository.loadAllAccountsForSync(),
            bookings = bookingsWithoutAttachments,
            categories = knownCategories + syntheticCategories,
            recurringRules = recurringRules
        )

        return syncJson.encodeToString(payload)
    }
}
