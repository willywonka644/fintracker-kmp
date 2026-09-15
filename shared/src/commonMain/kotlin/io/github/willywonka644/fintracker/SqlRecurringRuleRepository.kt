package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.serialization.AppJson
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class SqlRecurringRuleRepository(private val db: FintrackerDatabase) : IRecurringRuleRepository {

    private val queries = db.recurring_rulesQueries

    override fun loadRules(): List<RecurringRule> =
        queries.selectAll().executeAsList().map { row ->
            RecurringRule(
                id = row.id,
                amount = row.amount,
                description = row.description,
                accountId = row.accountId,
                frequency = row.frequency,
                nextExecutionDate = row.nextExecutionDate,
                remainingExecutions = row.remainingExecutions?.toInt(),
                category = row.category,
                excludedDates = row.excludedDates,
                lastModifiedAt = row.lastModifiedAt,
                deleted = row.deleted
            )
        }

    override fun loadAllRulesForSync(): List<RecurringRule> =
        queries.selectAllIncludingDeleted().executeAsList().map { row ->
            RecurringRule(
                id = row.id,
                amount = row.amount,
                description = row.description,
                accountId = row.accountId,
                frequency = row.frequency,
                nextExecutionDate = row.nextExecutionDate,
                remainingExecutions = row.remainingExecutions?.toInt(),
                category = row.category,
                excludedDates = row.excludedDates,
                lastModifiedAt = row.lastModifiedAt,
                deleted = row.deleted
            )
        }

    override fun reload(): List<RecurringRule> = loadRules()

    override fun saveRules(rules: List<RecurringRule>) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            // Preserve tombstones: callers pass the live in-memory list, which no longer
            // contains soft-deleted rules — re-insert them so the sync deletion survives.
            val softDeleted = queries.selectAllIncludingDeleted().executeAsList()
                .filter { it.deleted }
            queries.deleteAll()
            rules.forEach { rule ->
                queries.insert(
                    id = rule.id,
                    amount = rule.amount,
                    description = rule.description,
                    accountId = rule.accountId,
                    frequency = rule.frequency,
                    nextExecutionDate = rule.nextExecutionDate,
                    remainingExecutions = rule.remainingExecutions?.toLong(),
                    category = rule.category,
                    excludedDates = rule.excludedDates,
                    lastModifiedAt = if (rule.lastModifiedAt == 0L) now else rule.lastModifiedAt,
                    deleted = rule.deleted
                )
            }
            softDeleted.forEach { row ->
                queries.insert(
                    id = row.id,
                    amount = row.amount,
                    description = row.description,
                    accountId = row.accountId,
                    frequency = row.frequency,
                    nextExecutionDate = row.nextExecutionDate,
                    remainingExecutions = row.remainingExecutions,
                    category = row.category,
                    excludedDates = row.excludedDates,
                    lastModifiedAt = row.lastModifiedAt,
                    deleted = row.deleted
                )
            }
        }
    }

    override fun softDeleteRule(id: String, deletedAt: Long) {
        queries.softDelete(lastModifiedAt = deletedAt, id = id)
    }

    override fun getRawRulesJson(): String? {
        val rules = loadRules()
        return if (rules.isEmpty()) null else serializeRules(rules)
    }

    override fun replaceRulesJson(json: String?): Boolean {
        return try {
            val rules = if (json == null) emptyList()
            else AppJson.decodeFromString<List<RecurringRule>>(json)
            saveRules(rules)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun serializeRules(rules: List<RecurringRule>): String =
        AppJson.encodeToString(rules)
}
