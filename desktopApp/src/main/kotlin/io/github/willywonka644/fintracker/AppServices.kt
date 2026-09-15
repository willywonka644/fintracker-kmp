package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.category.ICategoryRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.db.DatabaseDriverFactory
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import io.github.willywonka644.fintracker.security.DesktopPinStore
import io.github.willywonka644.fintracker.sync.DesktopServerSyncStore
import io.github.willywonka644.fintracker.sync.KtorSyncServer
import io.github.willywonka644.fintracker.sync.SyncExporter
import io.github.willywonka644.fintracker.sync.SyncImporter
import io.github.willywonka644.fintracker.sync.SyncWriter

/**
 * Everything the desktop UI reaches for outside itself.
 *
 * [AppServices] is the production implementation and opens the real database in
 * the user's home directory. Keeping this behind an interface lets a test hand
 * the UI an in-memory database instead, so a UI test can never write into
 * `~/FinTracker/fintracker.db`.
 *
 * Only what the UI actually consumes is listed here. Zwei Einträge waren einmal
 * bloße Konstruktionsdetails und sind es nicht mehr geblieben: der Sync-Exporter
 * und -Importer seit #92, weil der Desktop seinen Abgleich aus der Oberfläche
 * heraus fährt, und die Einstellungen seit #135, weil die Oberfläche die
 * abgelehnten Umbenennungs-Gruppen selbst liest. In beiden Fällen wäre der Griff
 * an dieser Schnittstelle vorbei nach `AppServices` genau die Kopplung, die #78
 * entfernt hat.
 */
interface Services {
    val accountRepository: IAccountRepository
    val bookingRepository: IBookingRepository
    val categoryRepository: ICategoryRepository
    val recurringRuleRepository: IRecurringRuleRepository
    val syncExporter: SyncExporter
    val syncImporter: SyncImporter
    val syncWriter: SyncWriter
    val syncServer: KtorSyncServer
    val pinStore: DesktopPinStore

    /** Address and key for the sync server (#92). */
    val serverSyncStore: DesktopServerSyncStore

    /**
     * `app_settings`: gerätelokale Einstellungen, die nicht mitgesynct werden — die
     * abgelehnten Umbenennungs-Gruppen aus #135 zum Beispiel.
     */
    val settingsRepository: ISettingsRepository
}

object AppServices : Services {
    private val db by lazy {
        createFintrackerDatabase(DatabaseDriverFactory().createDriver())
    }
    override val accountRepository: IAccountRepository by lazy { SqlAccountRepository(db) }
    override val bookingRepository: IBookingRepository by lazy { SqlBookingRepository(db) }
    override val categoryRepository: ICategoryRepository by lazy { SqlCategoryRepository(db) }
    override val recurringRuleRepository: IRecurringRuleRepository by lazy { SqlRecurringRuleRepository(db) }
    override val settingsRepository: ISettingsRepository by lazy { SqlSettingsRepository(db) }
    override val syncExporter: SyncExporter by lazy {
        SyncExporter(
            accountRepository = accountRepository,
            bookingRepository = bookingRepository,
            categoryRepository = categoryRepository,
            recurringRuleRepository = recurringRuleRepository,
            settingsRepository = settingsRepository
        )
    }
    override val syncImporter: SyncImporter by lazy {
        SyncImporter(
            accountRepository = accountRepository,
            bookingRepository = bookingRepository,
            categoryRepository = categoryRepository,
            recurringRuleRepository = recurringRuleRepository
        )
    }
    override val syncWriter: SyncWriter by lazy { SyncWriter(db) }
    override val syncServer: KtorSyncServer by lazy {
        KtorSyncServer(syncExporter, syncImporter, syncWriter, settingsRepository)
    }
    override val pinStore: DesktopPinStore by lazy { DesktopPinStore(settingsRepository) }
    override val serverSyncStore: DesktopServerSyncStore by lazy {
        DesktopServerSyncStore(settingsRepository)
    }
}
