package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category

data class SyncPreview(
    val newCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val skippedCount: Int,
    val warnings: List<String>,
    val resolvedAccounts: List<Account>,
    val resolvedBookings: List<Booking>,
    val resolvedCategories: List<Category>,
    val resolvedRecurringRules: List<RecurringRule>
)
