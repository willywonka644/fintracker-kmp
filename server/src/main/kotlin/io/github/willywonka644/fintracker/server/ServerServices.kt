package io.github.willywonka644.fintracker.server

import io.github.willywonka644.fintracker.SqlAccountRepository
import io.github.willywonka644.fintracker.SqlBookingRepository
import io.github.willywonka644.fintracker.SqlRecurringRuleRepository
import io.github.willywonka644.fintracker.SqlSettingsRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.DatabaseDriverFactory
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import io.github.willywonka644.fintracker.sync.SyncExporter
import io.github.willywonka644.fintracker.sync.SyncImporter
import io.github.willywonka644.fintracker.sync.SyncWriter
import java.io.File

/**
 * The server's database and the sync machinery around it (#88).
 *
 * Mirrors `AppServices` on the desktop, with one difference that is the whole
 * point: the database file is **the server's own**, never the user's. On a
 * machine where the desktop app also runs, opening the default desktop database
 * from here would have the server and the app writing the same file through two
 * connections — and the sync would be comparing a device against itself.
 *
 * The path is configurable through [DB_PATH_ENV] so the Pi can put the database
 * somewhere other than its SD card: cards wear out under writes, and a sync
 * database is written on every exchange (#93).
 */
class ServerServices(databaseFile: File = defaultDatabaseFile()) {

    private val db = createFintrackerDatabase(DatabaseDriverFactory(databaseFile).createDriver())

    private val accounts = SqlAccountRepository(db)
    private val bookings = SqlBookingRepository(db)
    private val categories = SqlCategoryRepository(db)
    private val rules = SqlRecurringRuleRepository(db)
    private val settings = SqlSettingsRepository(db)

    val exporter: SyncExporter = SyncExporter(
        accountRepository = accounts,
        bookingRepository = bookings,
        categoryRepository = categories,
        recurringRuleRepository = rules,
        settingsRepository = settings,
    )

    val importer: SyncImporter = SyncImporter(
        accountRepository = accounts,
        bookingRepository = bookings,
        categoryRepository = categories,
        recurringRuleRepository = rules,
    )

    /**
     * Writes a merge result. Kept alongside the importer because [SyncImporter]
     * only *computes* the merge — it returns a preview and stores nothing. A push
     * route that called `import` alone would answer 200 and persist nothing.
     */
    val writer: SyncWriter = SyncWriter(db)

    companion object {
        const val DB_PATH_ENV = "FINTRACKER_SERVER_DB"

        /**
         * Deliberately a different directory from the desktop app's, so the two
         * can never collide by accident on a machine that runs both.
         */
        fun defaultDatabaseFile(): File =
            System.getenv(DB_PATH_ENV)?.takeIf { it.isNotBlank() }?.let { File(it) }
                ?: File(File(System.getProperty("user.home"), "fintracker-server"), "fintracker.db")
    }
}
