package io.github.willywonka644.fintracker.export

import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category

data class ExportData(
    val accounts: List<Account>,
    val bookings: List<Booking>,
    val categories: List<Category> = emptyList(),
    val recurringRules: List<RecurringRule> = emptyList()
)
