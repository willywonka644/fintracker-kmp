package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.serialization.AppJson
import kotlinx.datetime.Clock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class SqlAccountRepository(private val db: FintrackerDatabase) : IAccountRepository {

    private val queries = db.accountsQueries

    override fun loadAccounts(): List<Account> =
        queries.selectAll().executeAsList().map { row ->
            Account(
                id = row.id,
                name = row.name,
                type = row.type,
                billingStartDay = row.billingStartDay?.toInt(),
                spendingLimit = row.spendingLimit,
                lastModifiedAt = row.lastModifiedAt,
                deleted = row.deleted
            )
        }

    override fun loadAllAccountsForSync(): List<Account> =
        queries.selectAllIncludingDeleted().executeAsList().map { row ->
            Account(
                id = row.id,
                name = row.name,
                type = row.type,
                billingStartDay = row.billingStartDay?.toInt(),
                spendingLimit = row.spendingLimit,
                lastModifiedAt = row.lastModifiedAt,
                deleted = row.deleted
            )
        }

    override fun reload(): List<Account> = loadAccounts()

    override fun saveAccounts(accounts: List<Account>) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            // Preserve tombstones: callers pass the live in-memory list, which no longer
            // contains soft-deleted accounts — re-insert them so the sync deletion survives.
            val softDeleted = queries.selectAllIncludingDeleted().executeAsList()
                .filter { it.deleted }
            queries.deleteAll()
            accounts.forEach { account ->
                queries.insert(
                    id = account.id,
                    name = account.name,
                    type = account.type,
                    billingStartDay = account.billingStartDay?.toLong(),
                    spendingLimit = account.spendingLimit,
                    lastModifiedAt = if (account.lastModifiedAt == 0L) now else account.lastModifiedAt,
                    deleted = account.deleted
                )
            }
            softDeleted.forEach { row ->
                queries.insert(
                    id = row.id,
                    name = row.name,
                    type = row.type,
                    billingStartDay = row.billingStartDay,
                    spendingLimit = row.spendingLimit,
                    lastModifiedAt = row.lastModifiedAt,
                    deleted = row.deleted
                )
            }
        }
    }

    override fun softDeleteAccount(id: String, deletedAt: Long) {
        queries.softDelete(lastModifiedAt = deletedAt, id = id)
    }

    override fun getRawAccountsJson(): String? {
        val accounts = loadAccounts()
        return if (accounts.isEmpty()) null else serializeAccounts(accounts)
    }

    override fun replaceAccountsJson(json: String?): Boolean {
        return try {
            val accounts = if (json == null) emptyList()
            else AppJson.decodeFromString<List<Account>>(json)
            saveAccounts(accounts)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun serializeAccounts(accounts: List<Account>): String =
        AppJson.encodeToString(accounts)
}
