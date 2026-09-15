package io.github.willywonka644.fintracker.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

/**
 * Opens the database, creating or migrating it as needed.
 *
 * The file is a parameter so the Pi sync server (#88) can point at its own
 * database instead of the user's. The default is unchanged, so every existing
 * call site keeps behaving exactly as before — and the create/migrate dance with
 * `PRAGMA user_version` stays in one place rather than being written a second
 * time for the server, where a subtle difference would show up as a migration
 * that silently does not run.
 */
class DatabaseDriverFactory(private val dbFile: File = defaultDatabaseFile()) {

    companion object {
        fun defaultDatabaseFile(): File =
            File(File(System.getProperty("user.home"), "FinTracker"), "fintracker.db")
    }

    fun createDriver(): SqlDriver {
        dbFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val isNew = !dbFile.exists()

        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (isNew) {
            FintrackerDatabase.Schema.create(driver)
            driver.execute(null, "PRAGMA user_version = ${FintrackerDatabase.Schema.version}", 0, null)
        } else {
            val currentVersion = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor -> QueryResult.Value(cursor.getLong(0) ?: 0L) },
                parameters = 0,
                binders = null
            ).value
            if (currentVersion < FintrackerDatabase.Schema.version) {
                FintrackerDatabase.Schema.migrate(
                    driver = driver,
                    oldVersion = currentVersion,
                    newVersion = FintrackerDatabase.Schema.version
                )
                driver.execute(null, "PRAGMA user_version = ${FintrackerDatabase.Schema.version}", 0, null)
            }
        }
        return driver
    }
}
