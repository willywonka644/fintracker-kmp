package io.github.willywonka644.fintracker.db

import app.cash.sqldelight.ColumnAdapter
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.Frequency
import kotlinx.datetime.LocalDate

typealias ExcludedDates = Set<LocalDate>

internal val accountTypeAdapter = object : ColumnAdapter<AccountType, String> {
    override fun decode(databaseValue: String) = AccountType.valueOf(databaseValue)
    override fun encode(value: AccountType) = value.name
}

internal val bookingStatusAdapter = object : ColumnAdapter<BookingStatus, String> {
    override fun decode(databaseValue: String) = BookingStatus.valueOf(databaseValue)
    override fun encode(value: BookingStatus) = value.name
}

internal val bookingSourceAdapter = object : ColumnAdapter<BookingSource, String> {
    override fun decode(databaseValue: String) = BookingSource.valueOf(databaseValue)
    override fun encode(value: BookingSource) = value.name
}

internal val frequencyAdapter = object : ColumnAdapter<Frequency, String> {
    override fun decode(databaseValue: String) = Frequency.valueOf(databaseValue)
    override fun encode(value: Frequency) = value.name
}

internal val localDateAdapter = object : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String) = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate) = value.toString()
}

internal val excludedDatesAdapter = object : ColumnAdapter<ExcludedDates, String> {
    override fun decode(databaseValue: String): ExcludedDates =
        if (databaseValue.isBlank()) emptySet()
        else databaseValue.split(",").map { LocalDate.parse(it.trim()) }.toSet()

    override fun encode(value: ExcludedDates): String =
        value.joinToString(",") { it.toString() }
}

internal val booleanAdapter = object : ColumnAdapter<Boolean, Long> {
    override fun decode(databaseValue: Long) = databaseValue != 0L
    override fun encode(value: Boolean) = if (value) 1L else 0L
}
