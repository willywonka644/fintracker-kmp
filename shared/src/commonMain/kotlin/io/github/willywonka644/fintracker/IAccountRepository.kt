package io.github.willywonka644.fintracker

interface IAccountRepository {
    fun loadAccounts(): List<Account>
    /** Includes soft-deleted tombstones — for sync export/merge only. */
    fun loadAllAccountsForSync(): List<Account>
    fun reload(): List<Account>
    fun saveAccounts(accounts: List<Account>)
    /** Marks the account deleted (sync tombstone) instead of removing the row. */
    fun softDeleteAccount(id: String, deletedAt: Long)
    fun getRawAccountsJson(): String?
    fun replaceAccountsJson(json: String?): Boolean
    fun serializeAccounts(accounts: List<Account>): String
}
