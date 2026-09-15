package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import kotlinx.serialization.Serializable

@Serializable
data class SyncPayload(
    val version: String,
    val exportedAt: String,
    val deviceId: String,
    val accounts: List<Account>,
    val bookings: List<Booking>,
    val categories: List<Category>,
    val recurringRules: List<RecurringRule>
)
