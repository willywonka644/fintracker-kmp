package io.github.willywonka644.fintracker

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.booking.DescriptionCleanup
import io.github.willywonka644.fintracker.booking.DescriptionSuggestions
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.account.AccountDetailPanel
import io.github.willywonka644.fintracker.ui.account.AccountOverviewScreen
import io.github.willywonka644.fintracker.ui.booking.BookingDetailDialog
import io.github.willywonka644.fintracker.ui.booking.BookingFormDialog
import io.github.willywonka644.fintracker.ui.booking.BookingListScreen
import io.github.willywonka644.fintracker.ui.category.CategoryManagementDialog
import io.github.willywonka644.fintracker.ui.cleanup.DescriptionCleanupDialog
import io.github.willywonka644.fintracker.ui.common.ConfirmDialog
import io.github.willywonka644.fintracker.ui.exportimport.CsvImportDialog
import io.github.willywonka644.fintracker.ui.exportimport.exportCsvAllAccounts
import io.github.willywonka644.fintracker.ui.exportimport.exportCsvSingleAccount
import io.github.willywonka644.fintracker.ui.exportimport.exportJson
import io.github.willywonka644.fintracker.ui.exportimport.importCsvBookings
import io.github.willywonka644.fintracker.ui.exportimport.parseCsvFile
import io.github.willywonka644.fintracker.ui.exportimport.restoreJson
import io.github.willywonka644.fintracker.ui.exportimport.showOpenDialog
import io.github.willywonka644.fintracker.ui.exportimport.showSaveDialog
import io.github.willywonka644.fintracker.ui.auswertungen.DesktopAuswertungenScreen
import io.github.willywonka644.fintracker.ui.dashboard.DesktopDashboard
import io.github.willywonka644.fintracker.ui.installments.DesktopRatenzahlungenScreen
import io.github.willywonka644.fintracker.ui.recurring.DesktopDauerauftraegeScreen
import io.github.willywonka644.fintracker.ui.settings.DesktopEinstellungenScreen
import io.github.willywonka644.fintracker.ui.shell.DesktopNavDestination
import io.github.willywonka644.fintracker.ui.account.displayName
import io.github.willywonka644.fintracker.ui.shell.DesktopShell
import io.github.willywonka644.fintracker.ui.sync.ServerSyncDialog
import io.github.willywonka644.fintracker.ui.sync.ServerSyncSettingsDialog
import io.github.willywonka644.fintracker.ui.sync.WiFiSyncDialog
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.util.UUID
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import io.github.willywonka644.fintracker.sync.countUnsyncedSince
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FinTrackerDesktop")

/** Smallest window the desktop layout is built to survive (#97), in pixels. */
private const val MIN_WINDOW_WIDTH = 640
private const val MIN_WINDOW_HEIGHT = 560

/**
 * Abgelehnte Umbenennungs-Gruppen (#135). Geraetelokal in `app_settings`, denselben Weg
 * wie die abgelehnten Umbuchungs-Paare auf Android — der Schluessel muss auf beiden
 * Plattformen derselbe sein, damit ein Abgleich ihn nicht als neues Feld missversteht.
 */
private const val SETTING_REJECTED_CLEANUP = "description_groups_rejected"


fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1200.dp, 800.dp))

    // ── PIN lock state ────────────────────────────────────────────────────────
    var isLocked by remember { mutableStateOf(AppServices.pinStore.isPinEnabled) }
    var showReconciliation by remember { mutableStateOf(false) }
    var showAudit by remember { mutableStateOf(false) }
    var showWiFiSync by remember { mutableStateOf(false) }
    var showServerSyncSettings by remember { mutableStateOf(false) }
    var showServerSync by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var showPinSettings by remember { mutableStateOf(false) }

    // Aus den Daten gespeist, nicht bei null gestartet (#114): ein Zaehler im Speicher
    // meldet nach jedem Programmstart "nichts ausstehend", auch wenn ungesyncte Zeilen in
    // der Datenbank liegen — und dann schweigt auch die Nachfrage beim Schliessen.
    // Dieselbe Begruendung wie auf Android (#110); die Funktion liegt seither in shared.
    fun countUnsynced(): Int = countUnsyncedSince(
        lastSyncedAt = AppServices.serverSyncStore.lastSyncedAt,
        accounts = AppServices.accountRepository.loadAllAccountsForSync(),
        bookings = AppServices.bookingRepository.loadAllBookingsForSync(),
        recurringRules = AppServices.recurringRuleRepository.loadAllRulesForSync(),
        categories = AppServices.categoryRepository.loadAllCategoriesForSync(),
    )

    var unsyncedCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { unsyncedCount = withContext(Dispatchers.Default) { countUnsynced() } }
    var showCloseDialog by remember { mutableStateOf(false) }

    var selectedAccount by remember { mutableStateOf<Account?>(null) }
    var showCategories by remember { mutableStateOf(false) }
    var pendingCsvImportText by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun doInBackground(block: suspend () -> Unit) = scope.launch { withContext(Dispatchers.Default) { block() } }

    Window(
        onCloseRequest = { if (unsyncedCount > 0) showCloseDialog = true else exitApplication() },
        title = "FinTracker Desktop",
        state = windowState,
        icon = painterResource("icon.png"),
    ) {
        // A floor, not a fix (#97): the layout is meant to hold together down to
        // here, and the collapsing sidebar plus the reflowing dashboard header are
        // what make that true. This only stops the window being dragged past the
        // point anything was designed for.
        LaunchedEffect(Unit) { window.minimumSize = Dimension(MIN_WINDOW_WIDTH, MIN_WINDOW_HEIGHT) }

        FinTrackerTheme {
            if (AppServices.pinStore.isPinEnabled && isLocked) {
                io.github.willywonka644.fintracker.ui.lock.DesktopPinLockScreen(
                    errorMessage = pinError,
                    onUnlockRequested = { pin ->
                        if (AppServices.pinStore.verifyPin(pin)) {
                            isLocked = false
                            pinError = null
                        } else {
                            pinError = "Falsche PIN"
                        }
                    }
                )
            } else {
            AppContent(
                selectedAccount = selectedAccount,
                onSelectedAccountChange = { selectedAccount = it },
                showCategories = showCategories,
                onHideCategories = { showCategories = false },
                onShowCategories = { showCategories = true },
                pendingCsvImportText = pendingCsvImportText,
                onCsvImportDismiss = { pendingCsvImportText = null },
                showRestoreConfirm = showRestoreConfirm,
                pendingRestoreJson = pendingRestoreJson,
                onRestoreDismiss = { showRestoreConfirm = false; pendingRestoreJson = null },
                showPinSettings = showPinSettings,
                onPinSettingsDismiss = { showPinSettings = false; isLocked = AppServices.pinStore.isPinEnabled },
                showReconciliation = showReconciliation,
                onReconciliationDismiss = { showReconciliation = false },
                showAudit = showAudit,
                onAuditDismiss = { showAudit = false },
                showWiFiSync = showWiFiSync,
                onWiFiSyncDismiss = { showWiFiSync = false },
                onShowWiFiSync = { showWiFiSync = true },
                showServerSyncSettings = showServerSyncSettings,
                onServerSyncSettingsDismiss = { showServerSyncSettings = false },
                onShowServerSyncSettings = { showServerSyncSettings = true },
                showServerSync = showServerSync,
                onServerSyncDismiss = { showServerSync = false },
                onShowServerSync = { showServerSync = true },
                onShowPinSettings = { showPinSettings = true },
                onShowAudit = { showAudit = true },
                onShowReconciliation = { showReconciliation = true },
                showHelp = showHelp,
                onHelpDismiss = { showHelp = false },
                onShowHelp = { showHelp = true },
                showAbout = showAbout,
                onAboutDismiss = { showAbout = false },
                onShowAbout = { showAbout = true },
                onInitiateRestore = {
                    doInBackground {
                        val file = showOpenDialog("Select backup", "json", "JSON backup") ?: return@doInBackground
                        pendingRestoreJson = file.readText()
                        showRestoreConfirm = true
                    }
                },
                onInitiateCsvImport = {
                    doInBackground {
                        val file = showOpenDialog("Import CSV", "csv", "CSV file") ?: return@doInBackground
                        pendingCsvImportText = file.readText()
                    }
                },
                unsyncedCount = unsyncedCount,
                onMarkChanged = { unsyncedCount++ },
                onSyncCompleted = {
                    log.debug("[Window] onSyncCompleted — stamping lastSyncedAt (was $unsyncedCount unsynced)")
                    // Zeitstempel schreiben statt nur den Zaehler nullen: sonst ist der
                    // Abgleich nach dem naechsten Programmstart wieder vergessen.
                    AppServices.serverSyncStore.lastSyncedAt = System.currentTimeMillis()
                    unsyncedCount = 0
                },
            )
            } // end PIN lock else

            // Die Nachfrage oeffnet den Weg, von dem ihre Zahl stammt (#124): gezaehlt wird
            // gegen den letzten *Server*-Abgleich, also muss die Nachfrage beim Schliessen auch den
            // Server-Sync oeffnen. Der WLAN-Sync bleibt der Notweg und steht in den
            // Einstellungen. Deutsch wie der Rest des Fensters.
            if (showCloseDialog) {
                AlertDialog(
                    onDismissRequest = { showCloseDialog = false },
                    title = { Text("Ungespeicherte Änderungen") },
                    text = { Text("Es gibt $unsyncedCount nicht synchronisierte Änderung${if (unsyncedCount == 1) "" else "en"}. Sie sind noch nicht beim Server angekommen. Trotzdem schließen?") },
                    confirmButton = {
                        TextButton(onClick = { exitApplication() }) { Text("Ja, schließen") }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { showCloseDialog = false; showServerSync = true }) { Text("Server-Sync") }
                            TextButton(onClick = { showCloseDialog = false }) { Text("Abbrechen") }
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun AppContent(
    /**
     * Everything outside the UI. Defaults to the production services, so the
     * call in [main] stays unchanged; tests pass an implementation backed by an
     * in-memory database.
     */
    services: Services = AppServices,
    selectedAccount: Account?,
    onSelectedAccountChange: (Account?) -> Unit,
    showCategories: Boolean,
    onHideCategories: () -> Unit,
    onShowCategories: () -> Unit = {},
    pendingCsvImportText: String?,
    onCsvImportDismiss: () -> Unit,
    showRestoreConfirm: Boolean,
    pendingRestoreJson: String?,
    onRestoreDismiss: () -> Unit,
    showPinSettings: Boolean,
    onPinSettingsDismiss: () -> Unit,
    showReconciliation: Boolean,
    onReconciliationDismiss: () -> Unit,
    showAudit: Boolean,
    onAuditDismiss: () -> Unit,
    showWiFiSync: Boolean,
    onWiFiSyncDismiss: () -> Unit,
    onShowWiFiSync: () -> Unit = {},
    showServerSyncSettings: Boolean = false,
    onServerSyncSettingsDismiss: () -> Unit = {},
    onShowServerSyncSettings: () -> Unit = {},
    showServerSync: Boolean = false,
    onServerSyncDismiss: () -> Unit = {},
    onShowServerSync: () -> Unit = {},
    onShowPinSettings: () -> Unit = {},
    onShowAudit: () -> Unit = {},
    onShowReconciliation: () -> Unit = {},
    showHelp: Boolean = false,
    onHelpDismiss: () -> Unit = {},
    onShowHelp: () -> Unit = {},
    showAbout: Boolean = false,
    onAboutDismiss: () -> Unit = {},
    onShowAbout: () -> Unit = {},
    onInitiateRestore: () -> Unit = {},
    onInitiateCsvImport: () -> Unit = {},
    unsyncedCount: Int = 0,
    onMarkChanged: () -> Unit = {},
    onSyncCompleted: () -> Unit = {},
) {
    var accounts by remember { mutableStateOf(emptyList<Account>()) }
    var bookings by remember { mutableStateOf(emptyList<Booking>()) }
    var categories by remember { mutableStateOf(emptyList<Category>()) }
    var recurringRules by remember { mutableStateOf(emptyList<io.github.willywonka644.fintracker.RecurringRule>()) }
    // Vorrat fuer die Beschreibungs-Vorschlaege (#136). Einmal je Bestand, nicht je
    // Tastendruck: das Zaehlen laeuft ueber alle Buchungen.
    val descriptionSuggestionIndex = remember(bookings) { DescriptionSuggestions.index(bookings) }
    // Beschreibungen aufraeumen (#135). Ablehnungen liegen geraetelokal in app_settings
    // und aus dem Abgleich heraus — ein "nein danke" hat im Merge nichts zu suchen.
    var showDescriptionCleanup by remember { mutableStateOf(false) }
    var rejectedCleanupGroups by remember {
        mutableStateOf(
            DescriptionCleanup.parseRejected(
                services.settingsRepository.get(SETTING_REJECTED_CLEANUP)
            )
        )
    }
    val cleanupGroups = remember(bookings, rejectedCleanupGroups) {
        DescriptionCleanup.suggest(bookings, rejected = rejectedCleanupGroups)
    }
    var showAddBooking by remember { mutableStateOf(false) }
    var editingBooking by remember { mutableStateOf<Booking?>(null) }
    var deletingBooking by remember { mutableStateOf<Booking?>(null) }
    // Die Ansicht (#129) steht vor beiden: ein Klick auf eine Buchung zeigt sie erst,
    // bearbeiten und loeschen sind von dort zwei bewusste Schritte.
    var detailBooking by remember { mutableStateOf<Booking?>(null) }
    var selectedTab by remember { mutableStateOf(DesktopNavDestination.DASHBOARD) }
    var showDashboardBookingAccountPicker by remember { mutableStateOf(false) }
    var showSidebarAuditPicker by remember { mutableStateOf(false) }
    var showSidebarReconciliationPicker by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val syncCompletedCount by services.syncServer.syncCompletedCount.collectAsState()

    fun reload() {
        scope.launch {
            withContext(Dispatchers.Default) {
                accounts = services.accountRepository.loadAccounts()
                bookings = services.bookingRepository.loadBookings()
                categories = services.categoryRepository.loadCategories()
                recurringRules = services.recurringRuleRepository.loadRules()
            }
            onSelectedAccountChange(
                selectedAccount?.let { sel -> accounts.firstOrNull { it.id == sel.id } }
            )
        }
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(syncCompletedCount) {
        log.debug("[AppContent] syncCompletedCount changed to $syncCompletedCount")
        if (syncCompletedCount > 0) {
            log.debug("[AppContent] server-side sync complete — calling onSyncCompleted + reload")
            onSyncCompleted()
            reload()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        DesktopShell(
            selectedDest = selectedTab,
            onDestChange = { selectedTab = it },
            unsyncedCount = unsyncedCount,
            // Bei jeder Neuzeichnung aus dem Store gelesen, nicht gemerkt: nach einem
            // Abgleich steht dort sonst der Zeitpunkt von vorhin. Die Neuzeichnung
            // kommt zuverlaessig, weil derselbe Abgleich auch unsyncedCount nullt.
            lastSyncedAt = services.serverSyncStore.lastSyncedAt,
            // The server is the normal path now (#92); Wi-Fi stays reachable from
            // the settings screen as the fallback it was decided to remain.
            onSyncClick = onShowServerSync,
            onCategoriesClick = onShowCategories,
            cleanupGroupCount = cleanupGroups.size,
            onCleanupClick = { showDescriptionCleanup = true },
            onAuditClick = { showSidebarAuditPicker = true },
            onReconciliationClick = { showSidebarReconciliationPicker = true },
            onHelpClick = onShowHelp,
            onAboutClick = onShowAbout,
        ) {
            val saveAccount: (Account) -> Unit = { account ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val current = services.accountRepository.loadAccounts().toMutableList()
                        val idx = current.indexOfFirst { it.id == account.id }
                        if (idx >= 0) current[idx] = account else current.add(account)
                        services.accountRepository.saveAccounts(current)
                    }
                    onMarkChanged()
                    reload()
                }
            }
            val deleteAccount: (Account) -> Unit = { account ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        // Cascade soft-delete (sync tombstones!) — a hard save-without-
                        // the-row would let the other device resurrect everything.
                        // Also tombstone the account's rules (previously forgotten here).
                        val now = System.currentTimeMillis()
                        services.bookingRepository.loadBookings()
                            .filter { it.accountId == account.id }
                            .forEach { services.bookingRepository.softDeleteBooking(it.id, now) }
                        services.recurringRuleRepository.loadRules()
                            .filter { it.accountId == account.id }
                            .forEach { services.recurringRuleRepository.softDeleteRule(it.id, now) }
                        services.accountRepository.softDeleteAccount(account.id, now)
                    }
                    onMarkChanged()
                    onSelectedAccountChange(null)
                    reload()
                }
            }

            when (selectedTab) {
                DesktopNavDestination.DASHBOARD -> DesktopDashboard(
                    accounts = accounts,
                    bookings = bookings,
                    categories = categories,
                    recurringRules = recurringRules,
                    unsyncedCount = unsyncedCount,
                    onSyncClick = onShowServerSync,
                    onNavigateToDauerauftraege = { selectedTab = DesktopNavDestination.DAUERAUFTRAEGE },
                    onAddBookingClick = { showDashboardBookingAccountPicker = true },
                    onAccountClick = {
                        onSelectedAccountChange(it)
                        selectedTab = DesktopNavDestination.BUCHUNGEN
                    },
                    onOpenBooking = { detailBooking = it },
                    onAccountSaved = saveAccount,
                    onAccountDeleted = deleteAccount,
                )

                DesktopNavDestination.KONTEN -> AccountOverviewScreen(
                    accounts = accounts,
                    bookings = bookings,
                    recurringRules = recurringRules,
                    selectedAccount = selectedAccount,
                    onAccountSelected = { onSelectedAccountChange(it) },
                    onAccountSaved = saveAccount,
                    onAccountDeleted = deleteAccount,
                    unsyncedCount = unsyncedCount,
                    // Rechts die Buchungen des gewaehlten Kontos, mit denselben Aktionen wie
                    // im Buchungen-Tab — der Bildschirm soll Konten verwalten, nicht eine
                    // zweite, aermere Buchungsansicht sein.
                    // Was das Konto IST, nicht was darauf passiert ist — sonst waere der
                    // Reiter eine zweite Buchungsansicht neben der, die es schon gibt.
                    detailContent = { account, _ ->
                        AccountDetailPanel(account = account, bookings = bookings)
                    },
                )
                DesktopNavDestination.BUCHUNGEN -> {
                    val account = selectedAccount
                    val displayedBookings = remember(bookings, account?.id) {
                        if (account != null) bookings.filter { it.accountId == account.id }
                        else bookings
                    }
                    BookingListScreen(
                        bookings = displayedBookings,
                        categories = categories,
                        onAddClick = { showAddBooking = true },
                        onEditBooking = { editingBooking = it },
                        onDeleteBooking = { deletingBooking = it },
                        onOpenBooking = { detailBooking = it },
                        account = account,
                        accounts = accounts,
                        onAccountSelected = { onSelectedAccountChange(it) },
                    )
                }
                DesktopNavDestination.AUSWERTUNGEN -> DesktopAuswertungenScreen(
                    bookings = bookings,
                    categories = categories,
                    accounts = accounts,
                    onOpenBooking = { detailBooking = it },
                )
                DesktopNavDestination.DAUERAUFTRAEGE -> DesktopDauerauftraegeScreen(
                    rules = recurringRules,
                    accounts = accounts,
                    categories = categories,
                    onSaveRule = { rule ->
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                val current = services.recurringRuleRepository.loadRules().toMutableList()
                                val idx = current.indexOfFirst { it.id == rule.id }
                                val nextId = services.recurringRuleRepository.loadAllRulesForSync()
                                    .maxOfOrNull { it.id.removePrefix("r").toIntOrNull() ?: 0 }?.plus(1) ?: 1
                                val toSave = if (rule.id.isBlank()) rule.copy(id = "r$nextId") else rule
                                if (idx >= 0) current[idx] = toSave else current.add(toSave)
                                services.recurringRuleRepository.saveRules(current)
                            }
                            onMarkChanged()
                            reload()
                        }
                    },
                    onDeleteRule = { rule ->
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                // Soft-delete rule + its SCHEDULED bookings only (sync tombstones!).
                                // POSTED bookings are history and must survive.
                                val now = System.currentTimeMillis()
                                services.bookingRepository.loadBookings()
                                    .filter { it.recurringRuleId == rule.id && it.status == BookingStatus.SCHEDULED }
                                    .forEach { services.bookingRepository.softDeleteBooking(it.id, now) }
                                services.recurringRuleRepository.softDeleteRule(rule.id, now)
                            }
                            onMarkChanged()
                            reload()
                        }
                    },
                )
                DesktopNavDestination.RATENZAHLUNGEN -> DesktopRatenzahlungenScreen(
                    accounts = accounts,
                    bookings = bookings,
                    categories = categories,
                    nextBookingId = { UUID.randomUUID().toString() },
                    onSaveBookings = { newBookings ->
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                val current = services.bookingRepository.loadBookings().toMutableList()
                                current.addAll(newBookings)
                                services.bookingRepository.saveBookings(current)
                            }
                            onMarkChanged()
                            reload()
                        }
                    },
                    onDeleteGroup = { groupId ->
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                services.bookingRepository.saveBookings(
                                    services.bookingRepository.loadBookings().filter { it.installmentGroupId != groupId }
                                )
                            }
                            onMarkChanged()
                            reload()
                        }
                    },
                )
                DesktopNavDestination.EINSTELLUNGEN -> DesktopEinstellungenScreen(
                    pinEnabled = services.pinStore.isPinEnabled,
                    onShowWiFiSync = onShowWiFiSync,
                    onShowServerSync = onShowServerSyncSettings,
                    // Read on every recomposition rather than remembered: the
                    // dialog writes through this same store, and a cached value
                    // would leave the row showing the address from before the edit.
                    serverSyncUrl = services.serverSyncStore.serverUrl,
                    onShowPinSettings = onShowPinSettings,
                    onJsonBackup = {
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                val json = io.github.willywonka644.fintracker.ui.exportimport.exportJson(
                                    services.accountRepository,
                                    services.bookingRepository,
                                    services.categoryRepository,
                                    services.recurringRuleRepository,
                                ).getOrElse { return@withContext }
                                val file = io.github.willywonka644.fintracker.ui.exportimport.showSaveDialog("Save backup", "json", "JSON backup") ?: return@withContext
                                file.writeText(json)
                            }
                        }
                    },
                    onJsonRestore = onInitiateRestore,
                    onCsvExport = {
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                val csv = io.github.willywonka644.fintracker.ui.exportimport.exportCsvAllAccounts(
                                    services.accountRepository,
                                    services.bookingRepository,
                                ).getOrElse { return@withContext }
                                val file = io.github.willywonka644.fintracker.ui.exportimport.showSaveDialog("Export all accounts", "csv", "CSV file") ?: return@withContext
                                file.writeText(csv)
                            }
                        }
                    },
                    onCsvImport = onInitiateCsvImport,
                )
            }
        }
    }

    // ── Dashboard "Buchung hinzufügen" — account picker ──────────────────────
    if (showDashboardBookingAccountPicker) {
        AlertDialog(
            onDismissRequest = { showDashboardBookingAccountPicker = false },
            title = { Text("Bitte ein Konto auswählen") },
            text = {
                Column {
                    accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDashboardBookingAccountPicker = false
                                    onSelectedAccountChange(acc)
                                    showAddBooking = true
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(acc.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    acc.type.displayName(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDashboardBookingAccountPicker = false }) {
                    Text("Abbrechen")
                }
            },
        )
    }

    // ── Add booking ──────────────────────────────────────────────────────────
    if (showAddBooking && selectedAccount != null) {
        BookingFormDialog(
            accountId = selectedAccount!!.id,
            categories = categories,
            accounts = accounts,
            descriptionSuggestions = descriptionSuggestionIndex,
            onDismiss = { showAddBooking = false },
            // Beide Seiten in einem Zug (#114). Eine halbe Umbuchung waere schlimmer als
            // keine: sie verschoebe die Summen um den vollen Betrag in eine Richtung.
            onSaveTransfer = { outgoing, incoming ->
                showAddBooking = false
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val current = services.bookingRepository.loadBookings().toMutableList()
                        current.add(outgoing)
                        current.add(incoming)
                        services.bookingRepository.saveBookings(current)
                    }
                    onMarkChanged()
                    reload()
                }
            },
            onSave = { booking ->
                showAddBooking = false
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val current = services.bookingRepository.loadBookings().toMutableList()
                        current.add(booking)
                        services.bookingRepository.saveBookings(current)
                    }
                    onMarkChanged()
                    reload()
                }
            },
        )
    }

    // ── Edit booking ─────────────────────────────────────────────────────────
    // ── Buchungsansicht (#129) ───────────────────────────────────────────────
    detailBooking?.let { selected ->
        // Ueber die Id aus der aktuellen Liste geholt: nach einer Bearbeitung stuende
        // hier sonst der Stand von vorhin.
        val booking = bookings.firstOrNull { it.id == selected.id } ?: selected
        val counterpart = booking.transferGroupId?.let { group ->
            bookings.firstOrNull { it.transferGroupId == group && it.id != booking.id }
        }
        BookingDetailDialog(
            booking = booking,
            accountName = accounts.firstOrNull { it.id == booking.accountId }?.name ?: "Unbekanntes Konto",
            counterpartAccountName = counterpart?.let { c -> accounts.firstOrNull { it.id == c.accountId }?.name },
            categoryName = categories.firstOrNull { it.id == booking.category || it.name == booking.category }
                ?.name ?: booking.category.orEmpty(),
            onEdit = { detailBooking = null; editingBooking = booking },
            // Nicht selbst nachfragen: die Rueckfrage gibt es schon, samt der Regel fuer
            // beide Haelften einer Umbuchung.
            onDelete = { detailBooking = null; deletingBooking = booking },
            onDismiss = { detailBooking = null },
        )
    }

    editingBooking?.let { booking ->
        BookingFormDialog(
            accountId = booking.accountId,
            categories = categories,
            existing = booking,
            descriptionSuggestions = descriptionSuggestionIndex,
            onDismiss = { editingBooking = null },
            onSave = { updated ->
                editingBooking = null
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val current = services.bookingRepository.loadBookings().toMutableList()
                        val idx = current.indexOfFirst { it.id == updated.id }
                        if (idx >= 0) current[idx] = updated else current.add(updated)
                        services.bookingRepository.saveBookings(current)
                    }
                    onMarkChanged()
                    reload()
                }
            },
        )
    }

    // ── Delete booking ───────────────────────────────────────────────────────
    deletingBooking?.let { booking ->
        val transferGroupId = booking.transferGroupId
        ConfirmDialog(
            title = if (transferGroupId != null) "Umbuchung löschen" else "Buchung löschen",
            message = if (transferGroupId != null) {
                "Eine Umbuchung besteht aus zwei Buchungen. Beide Seiten werden gelöscht — " +
                    "der Abgang und der Zugang. Das kann nicht rückgängig gemacht werden."
            } else {
                "„${booking.description}“ löschen? Das kann nicht rückgängig gemacht werden."
            },
            onConfirm = {
                deletingBooking = null
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val now = System.currentTimeMillis()
                        if (transferGroupId != null) {
                            // Beide Seiten, oder die Buecher behalten eine einseitige
                            // Bewegung, die die Summen um den vollen Betrag verschiebt.
                            services.bookingRepository.loadBookings()
                                .filter { it.transferGroupId == transferGroupId }
                                .forEach { services.bookingRepository.softDeleteBooking(it.id, now) }
                        } else {
                            services.bookingRepository.softDeleteBooking(booking.id, now)
                        }
                    }
                    onMarkChanged()
                    reload()
                }
            },
            onDismiss = { deletingBooking = null },
        )
    }

    // ── Beschreibungen aufraeumen (#135) ────────────────────────────────
    if (showDescriptionCleanup) {
        DescriptionCleanupDialog(
            groups = cleanupGroups,
            onDismiss = { showDescriptionCleanup = false },
            onRename = { group, target ->
                val changed = DescriptionCleanup.affectedCount(bookings, group, target)
                if (changed > 0) {
                    scope.launch {
                        withContext(Dispatchers.Default) {
                            // Gegen den frisch geladenen Bestand, nicht gegen die Kopie in
                            // der Oberflaeche: dazwischen kann ein Abgleich gelaufen sein.
                            val current = services.bookingRepository.loadBookings()
                            services.bookingRepository.saveBookings(
                                DescriptionCleanup.apply(
                                    current, group, target,
                                    now = Clock.System.now().toEpochMilliseconds(),
                                )
                            )
                        }
                        onMarkChanged()
                        reload()
                    }
                }
            },
            onReject = { group ->
                val raw = DescriptionCleanup.withRejection(
                    services.settingsRepository.get(SETTING_REJECTED_CLEANUP),
                    group.key,
                )
                services.settingsRepository.set(SETTING_REJECTED_CLEANUP, raw)
                rejectedCleanupGroups = DescriptionCleanup.parseRejected(raw)
            },
        )
    }

    // ── Category management ──────────────────────────────────────────────────
    if (showCategories) {
        CategoryManagementDialog(
            categories = categories,
            onDismiss = onHideCategories,
            onSave = { cat ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val current = services.categoryRepository.loadCategories().toMutableList()
                        val idx = current.indexOfFirst { it.id == cat.id }
                        if (idx >= 0) current[idx] = cat else current.add(cat)
                        services.categoryRepository.saveCategories(current)
                    }
                    onMarkChanged()
                    reload()
                }
            },
            onDelete = { cat ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        // Soft delete: dropping the row would let the other device's live copy
                        // win the next merge and bring the category straight back.
                        services.categoryRepository.softDeleteCategory(
                            cat.id,
                            Clock.System.now().toEpochMilliseconds(),
                        )
                    }
                    onMarkChanged()
                    reload()
                }
            },
        )
    }

    // ── CSV Import ───────────────────────────────────────────────────────────
    pendingCsvImportText?.let { csvText ->
        val parseResult = remember(csvText) { parseCsvFile(csvText) }
        val targetAccount = selectedAccount
        CsvImportDialog(
            parseResult = parseResult,
            onDismiss = onCsvImportDismiss,
            onImport = { mapping ->
                onCsvImportDismiss()
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val accountId = targetAccount?.id
                            ?: services.accountRepository.loadAccounts().firstOrNull()?.id
                            ?: return@withContext
                        importCsvBookings(parseResult, mapping, accountId, services.bookingRepository)
                    }
                    onMarkChanged()
                    reload()
                }
            },
        )
    }

    // ── PIN settings ─────────────────────────────────────────────────────────
    if (showPinSettings) {
        io.github.willywonka644.fintracker.ui.lock.DesktopPinSettingsDialog(
            pinEnabled = services.pinStore.isPinEnabled,
            onDismiss = onPinSettingsDismiss,
            onSetPin = { pin ->
                services.pinStore.setPin(pin)
                onPinSettingsDismiss()
            },
            onDisablePin = {
                services.pinStore.disablePin()
                onPinSettingsDismiss()
            },
        )
    }

    // ── Reconciliation ────────────────────────────────────────────────────────
    if (showReconciliation && selectedAccount != null) {
        io.github.willywonka644.fintracker.ui.reconciliation.ReconciliationDialog(
            accountId = selectedAccount!!.id,
            accountName = selectedAccount!!.name,
            allBookings = bookings,
            nextBookingId = { UUID.randomUUID().toString() },
            onDismiss = onReconciliationDismiss,
            onCreateCorrectionBooking = { correction ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val current = services.bookingRepository.loadBookings().toMutableList()
                        current.add(correction)
                        services.bookingRepository.saveBookings(current)
                    }
                    onMarkChanged()
                    reload()
                }
            },
        )
    }

    // ── Audit ─────────────────────────────────────────────────────────────────
    if (showAudit && selectedAccount != null) {
        io.github.willywonka644.fintracker.ui.audit.AuditDialog(
            accountName = selectedAccount!!.name,
            bookings = bookings.filter {
                it.accountId == selectedAccount!!.id &&
                (it.effectiveDate ?: it.timestamp) <= kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            },
            categories = categories,
            onDismiss = onAuditDismiss,
            onToggleVerified = { booking ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        val current = services.bookingRepository.loadBookings().toMutableList()
                        val idx = current.indexOfFirst { it.id == booking.id }
                        if (idx >= 0) {
                            current[idx] = current[idx].copy(
                                isVerified = !current[idx].isVerified,
                                lastModifiedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                            )
                            services.bookingRepository.saveBookings(current)
                        }
                    }
                    onMarkChanged()
                    reload()
                }
            },
        )
    }

    // ── Wi-Fi sync dialog ─────────────────────────────────────────────────────
    if (showServerSyncSettings) {
        ServerSyncSettingsDialog(
            services = services,
            onDismiss = onServerSyncSettingsDismiss,
        )
    }

    if (showServerSync) {
        ServerSyncDialog(
            services = services,
            onDismiss = onServerSyncDismiss,
            onOpenSettings = { onServerSyncDismiss(); onShowServerSyncSettings() },
            onSyncConfirmed = {
                onSyncCompleted()
                reload()
            },
        )
    }

    if (showWiFiSync) {
        WiFiSyncDialog(
            services = services,
            onDismiss = onWiFiSyncDismiss,
            onSyncConfirmed = {
                log.debug("[AppContent] onSyncConfirmed from WiFiSyncDialog — calling onSyncCompleted + reload")
                onSyncCompleted()
                reload()
            },
        )
    }

    // ── Hilfe ─────────────────────────────────────────────────────────────────
    if (showHelp) {
        io.github.willywonka644.fintracker.ui.help.DesktopHelpDialog(onDismiss = onHelpDismiss)
    }

    // ── Über FinTracker ───────────────────────────────────────────────────────
    if (showAbout) {
        io.github.willywonka644.fintracker.ui.about.DesktopAboutDialog(onDismiss = onAboutDismiss)
    }

    // ── Sidebar Audit account picker ──────────────────────────────────────────
    if (showSidebarAuditPicker) {
        AlertDialog(
            onDismissRequest = { showSidebarAuditPicker = false },
            title = { Text("Bitte ein Konto auswählen") },
            text = {
                Column {
                    accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSidebarAuditPicker = false
                                    onSelectedAccountChange(acc)
                                    onShowAudit()
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(acc.name, style = MaterialTheme.typography.bodyMedium)
                                Text(acc.type.displayName(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSidebarAuditPicker = false }) { Text("Abbrechen") }
            },
        )
    }

    // ── Sidebar Kontoabgleich account picker ──────────────────────────────────
    if (showSidebarReconciliationPicker) {
        AlertDialog(
            onDismissRequest = { showSidebarReconciliationPicker = false },
            title = { Text("Bitte ein Konto auswählen") },
            text = {
                Column {
                    accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSidebarReconciliationPicker = false
                                    onSelectedAccountChange(acc)
                                    onShowReconciliation()
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(acc.name, style = MaterialTheme.typography.bodyMedium)
                                Text(acc.type.displayName(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSidebarReconciliationPicker = false }) { Text("Abbrechen") }
            },
        )
    }

    // ── JSON Restore confirmation ─────────────────────────────────────────────
    if (showRestoreConfirm && pendingRestoreJson != null) {
        ConfirmDialog(
            title = "Restore backup",
            message = "This will replace ALL current data with the backup. Continue?",
            confirmLabel = "Restore",
            onConfirm = {
                val json = pendingRestoreJson!!
                onRestoreDismiss()
                scope.launch {
                    withContext(Dispatchers.Default) {
                        restoreJson(
                            json,
                            services.accountRepository,
                            services.bookingRepository,
                            services.categoryRepository,
                            services.recurringRuleRepository,
                        )
                    }
                    log.debug("[AppContent] JSON restore success — calling onSyncCompleted")
                    onSyncCompleted()
                    onSelectedAccountChange(null)
                    reload()
                }
            },
            onDismiss = onRestoreDismiss,
        )
    }
}

