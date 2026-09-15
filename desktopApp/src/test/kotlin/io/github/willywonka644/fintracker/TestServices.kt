package io.github.willywonka644.fintracker

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.willywonka644.fintracker.category.ICategoryRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import io.github.willywonka644.fintracker.security.DesktopPinStore
import io.github.willywonka644.fintracker.sync.DesktopServerSyncStore
import io.github.willywonka644.fintracker.sync.KtorSyncServer
import io.github.willywonka644.fintracker.sync.SyncExporter
import io.github.willywonka644.fintracker.sync.SyncImporter
import io.github.willywonka644.fintracker.sync.SyncWriter

/**
 * [Services] backed by a fresh in-memory SQLite database — one per instance, so
 * tests never share state and never touch `~/FinTracker/fintracker.db`.
 *
 * The repositories are the real SQL ones on purpose: a UI test that talks to a
 * hand-written fake would not notice if the screen and the persistence layer
 * disagreed. Only the database *location* is swapped.
 *
 * [KtorSyncServer] is constructed but never started; its constructor only sets
 * up state flows and binds no port.
 */
internal class TestServices : Services {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        .also { FintrackerDatabase.Schema.create(it) }

    private val db = createFintrackerDatabase(driver)

    override val accountRepository: IAccountRepository = SqlAccountRepository(db)
    override val bookingRepository: IBookingRepository = SqlBookingRepository(db)
    override val categoryRepository: ICategoryRepository = SqlCategoryRepository(db)
    override val recurringRuleRepository: IRecurringRuleRepository = SqlRecurringRuleRepository(db)

    override val settingsRepository: ISettingsRepository = SqlSettingsRepository(db)

    override val syncWriter: SyncWriter = SyncWriter(db)

    override val syncExporter: SyncExporter = SyncExporter(
        accountRepository = accountRepository,
        bookingRepository = bookingRepository,
        categoryRepository = categoryRepository,
        recurringRuleRepository = recurringRuleRepository,
        settingsRepository = settingsRepository,
    )

    override val syncImporter: SyncImporter = SyncImporter(
        accountRepository = accountRepository,
        bookingRepository = bookingRepository,
        categoryRepository = categoryRepository,
        recurringRuleRepository = recurringRuleRepository,
    )

    override val syncServer: KtorSyncServer =
        KtorSyncServer(syncExporter, syncImporter, syncWriter, settingsRepository)

    override val pinStore: DesktopPinStore = DesktopPinStore(settingsRepository)

    override val serverSyncStore: DesktopServerSyncStore = DesktopServerSyncStore(settingsRepository)
}
