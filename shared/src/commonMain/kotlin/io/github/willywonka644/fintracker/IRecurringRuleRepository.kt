package io.github.willywonka644.fintracker

interface IRecurringRuleRepository {
    fun loadRules(): List<RecurringRule>
    /** Includes soft-deleted tombstones — for sync export/merge only. */
    fun loadAllRulesForSync(): List<RecurringRule>
    fun reload(): List<RecurringRule>
    fun saveRules(rules: List<RecurringRule>)
    /** Marks the rule deleted (sync tombstone) instead of removing the row. */
    fun softDeleteRule(id: String, deletedAt: Long)
    fun getRawRulesJson(): String?
    fun replaceRulesJson(json: String?): Boolean
    fun serializeRules(rules: List<RecurringRule>): String
}
