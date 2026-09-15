package io.github.willywonka644.fintracker.db

import app.cash.sqldelight.db.SqlDriver

fun createFintrackerDatabase(driver: SqlDriver): FintrackerDatabase =
    FintrackerDatabase(
        driver = driver,
        accountAdapter = Account.Adapter(
            typeAdapter = accountTypeAdapter
        ),
        bookingAdapter = Booking.Adapter(
            statusAdapter = bookingStatusAdapter,
            sourceAdapter = bookingSourceAdapter
        ),
        recurringRuleAdapter = RecurringRule.Adapter(
            frequencyAdapter = frequencyAdapter,
            nextExecutionDateAdapter = localDateAdapter,
            excludedDatesAdapter = excludedDatesAdapter
        )
    )
