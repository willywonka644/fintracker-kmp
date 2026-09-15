package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.serialization.LocalDateSerializer
import io.github.willywonka644.fintracker.serialization.LocalDateSetSerializer
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class Frequency {
    WEEKLY,
    MONTHLY,
    YEARLY
}

@Serializable
data class RecurringRule(
    val id: String,
    val amount: Double,
    val description: String,
    val accountId: String,
    val frequency: Frequency,
    @Serializable(with = LocalDateSerializer::class)
    val nextExecutionDate: LocalDate,
    val remainingExecutions: Int? = null,
    val category: String? = null,
    @Serializable(with = LocalDateSetSerializer::class)
    val excludedDates: Set<LocalDate> = emptySet(),
    val lastModifiedAt: Long = 0L,
    /** Sync tombstone: deleted rules keep a row so the deletion wins over
     *  the other device's live copy in the last-write-wins merge. */
    val deleted: Boolean = false
)
