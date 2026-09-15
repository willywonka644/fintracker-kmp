package io.github.willywonka644.fintracker

import android.net.Uri
import android.os.Bundle
import java.util.UUID
import android.widget.Toast
import io.github.willywonka644.fintracker.db.DatabaseDriverFactory
import io.github.willywonka644.fintracker.db.createFintrackerDatabase
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import io.github.willywonka644.fintracker.attachment.AttachmentPreviewRow
import io.github.willywonka644.fintracker.attachment.AttachmentSourceDialog
import io.github.willywonka644.fintracker.attachment.AttachmentStorage
import io.github.willywonka644.fintracker.attachment.ImageViewerScreen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlinx.datetime.toKotlinLocalDate
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.willywonka644.fintracker.ui.nav.FinTrackerBottomBar
import io.github.willywonka644.fintracker.ui.nav.GradientFAB
import io.github.willywonka644.fintracker.ui.konten.KontenScreen
import io.github.willywonka644.fintracker.ui.mehr.MehrScreen
import io.github.willywonka644.fintracker.analytics.DisplayBooking
import io.github.willywonka644.fintracker.analytics.allTimePeriod
import io.github.willywonka644.fintracker.analytics.autoPostDueBookings
import io.github.willywonka644.fintracker.analytics.generateInitialBookings
import io.github.willywonka644.fintracker.analytics.computeLimitUsage
import io.github.willywonka644.fintracker.analytics.limitKindFor
import io.github.willywonka644.fintracker.analytics.currentBillingCyclePeriod
import io.github.willywonka644.fintracker.analytics.currentMonthFilter
import io.github.willywonka644.fintracker.analytics.customRangeFilter
import io.github.willywonka644.fintracker.analytics.filterBookingsForAccount
import io.github.willywonka644.fintracker.analytics.generateBillingCyclePeriods
import io.github.willywonka644.fintracker.analytics.last90DaysFilter
import io.github.willywonka644.fintracker.analytics.lastMonthFilter
import io.github.willywonka644.fintracker.booking.BookingFactory
import io.github.willywonka644.fintracker.booking.DescriptionCleanup
import io.github.willywonka644.fintracker.booking.DescriptionSuggestions
import io.github.willywonka644.fintracker.booking.TransferFactory
import io.github.willywonka644.fintracker.ui.transfers.TransferSuggestionsScreen
import io.github.willywonka644.fintracker.booking.confirm
import io.github.willywonka644.fintracker.booking.TransferCandidate
import io.github.willywonka644.fintracker.booking.TransferSuggestions
import io.github.willywonka644.fintracker.booking.transferCounterpart
import io.github.willywonka644.fintracker.SqlAccountRepository
import io.github.willywonka644.fintracker.SqlBookingRepository
import io.github.willywonka644.fintracker.SqlRecurringRuleRepository
import io.github.willywonka644.fintracker.category.SqlCategoryRepository
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.ui.about.AboutScreen
import io.github.willywonka644.fintracker.ui.category.CategoryManagementScreen
import io.github.willywonka644.fintracker.ui.cleanup.DescriptionCleanupScreen
import io.github.willywonka644.fintracker.ui.help.HelpScreen
import io.github.willywonka644.fintracker.ui.overview.AccountListItem
import io.github.willywonka644.fintracker.ui.overview.AccountOverviewScreen
import io.github.willywonka644.fintracker.ui.overview.QuickAddBookingSheet
import io.github.willywonka644.fintracker.ui.accountdetail.AuditScreen
import io.github.willywonka644.fintracker.ui.accountdetail.KontoabgleichScreen
import io.github.willywonka644.fintracker.ui.accountdetail.SpendingLimitDialog
import io.github.willywonka644.fintracker.ui.buchungen.BuchungenScreen
import io.github.willywonka644.fintracker.ui.booking.AddBookingSheet
import io.github.willywonka644.fintracker.ui.booking.BookingDetailScreen
import io.github.willywonka644.fintracker.ui.auswertungen.AuswertungenTabScreen
import io.github.willywonka644.fintracker.ui.accountdetail.EmptyStateMessage
import io.github.willywonka644.fintracker.ui.accountdetail.BillingCyclePeriodSelector
import io.github.willywonka644.fintracker.ui.accountdetail.FlexiblePeriodOption
import io.github.willywonka644.fintracker.ui.accountdetail.FlexiblePeriodSelector
import io.github.willywonka644.fintracker.ui.components.BalanceHero
import io.github.willywonka644.fintracker.ui.components.BookingDayCard
import io.github.willywonka644.fintracker.ui.components.SectionHeader
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.ui.recurring.AddEditRecurringRuleDialog
import io.github.willywonka644.fintracker.ui.recurring.RecurringRulesScreen
import io.github.willywonka644.fintracker.ui.installments.AddInstallmentDialog
import io.github.willywonka644.fintracker.ui.installments.InstallmentInput
import io.github.willywonka644.fintracker.ui.installments.InstallmentsScreen
import io.github.willywonka644.fintracker.installments.buildInstallmentGroups
import io.github.willywonka644.fintracker.security.PinStore
import io.github.willywonka644.fintracker.export.BackupParseError
import io.github.willywonka644.fintracker.export.BackupParseResult
import io.github.willywonka644.fintracker.export.BackupParser
import io.github.willywonka644.fintracker.export.BackupData
import io.github.willywonka644.fintracker.export.ExportService
import io.github.willywonka644.fintracker.sync.ServerSyncStore
import io.github.willywonka644.fintracker.sync.SyncExporter
import io.github.willywonka644.fintracker.sync.countUnsyncedSince
import kotlinx.coroutines.flow.first
import io.github.willywonka644.fintracker.sync.SyncImporter
import io.github.willywonka644.fintracker.sync.SyncError
import io.github.willywonka644.fintracker.sync.SyncImportResult
import io.github.willywonka644.fintracker.sync.SyncPreview
import io.github.willywonka644.fintracker.export.RestoreResult
import io.github.willywonka644.fintracker.export.RestoreService
import io.github.willywonka644.fintracker.export.restoreWithDb
import io.github.willywonka644.fintracker.csvimport.CsvImportScreen
import io.github.willywonka644.fintracker.qrscan.EpcParser
import io.github.willywonka644.fintracker.qrscan.QrScanScreen
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import io.github.willywonka644.fintracker.ui.lock.PinLockScreen
import io.github.willywonka644.fintracker.ui.lock.PinSettingsDialog
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import java.time.Instant
import java.time.ZoneId

// Routes
private const val ROUTE_ACCOUNTS_LIST = "accounts_list"
private const val ROUTE_DAUERAUFTRAEGE = "dauerauftraege"
private const val ROUTE_RATENZAHLUNG = "ratenzahlung"
private const val ROUTE_KONTOABGLEICH = "kontoabgleich"
private const val ROUTE_HELP = "help"
private const val ROUTE_ABOUT = "about"
private const val ROUTE_CATEGORIES = "categories"
private const val ROUTE_KONTEN = "konten"
private const val ROUTE_TRANSFER_SUGGESTIONS = "transfer_suggestions"
private const val ROUTE_DESCRIPTION_CLEANUP = "description_cleanup"

/**
 * Pairs the user has said are not a transfer (#122).
 *
 * Kept in app_settings, which is device-local: the table is not part of the sync payload,
 * only the device id is read from it. A rejection is a decision about this cleanup, not
 * data — putting it into the merge would mean a new column in the middle of the one thing
 * that must not grow sideways.
 */
private const val SETTING_REJECTED_TRANSFERS = "transfer_pairs_rejected"
private const val SETTING_REJECTED_CLEANUP = "description_groups_rejected"
private const val ROUTE_CSV_IMPORT = "csv_import"
private const val ROUTE_QR_SCAN = "qr_scan"
private const val ROUTE_OCR_SCAN = "ocr_scan"
private const val ROUTE_IMAGE_VIEWER = "image_viewer"
private const val ROUTE_AUDIT = "audit"
private const val ROUTE_BOOKING_DETAIL = "booking_detail"
private const val ROUTE_BUCHUNGEN_TAB = "buchungen_tab"
private const val ROUTE_AUSWERTUNGEN_TAB = "auswertungen_tab"
private const val ROUTE_MEHR_TAB = "mehr_tab"


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                lightScrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )

        val db = createFintrackerDatabase(DatabaseDriverFactory(this).createDriver())

        setContent {
            FinTrackerTheme {
                FinTrackerRoot(db)
            }
        }
    }
}

@Composable
private fun FinTrackerRoot(db: FintrackerDatabase) {
    val context = LocalContext.current
    val pinStore = remember { PinStore(context) }
    val pinEnabled by pinStore.pinEnabled.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val pinWrongMessage = stringResource(R.string.pin_error_wrong)

    var isLocked by remember { mutableStateOf(true) }
    var lastPinEnabled by remember { mutableStateOf<Boolean?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pinEnabled) {
        if (!pinEnabled) {
            isLocked = false
        } else if (lastPinEnabled != true) {
            isLocked = true
        }
        lastPinEnabled = pinEnabled
    }

    DisposableEffect(lifecycleOwner, pinEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (pinEnabled && (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_RESUME)) {
                isLocked = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (pinEnabled && isLocked) {
        PinLockScreen(
            errorMessage = pinError,
            onUnlockRequested = { pin ->
                scope.launch {
                    val ok = pinStore.verifyPin(pin)
                    if (ok) {
                        pinError = null
                        isLocked = false
                    } else {
                        pinError = pinWrongMessage
                    }
                }
            }
        )
    } else {
        FinTrackerApp(
            db = db,
            pinStore = pinStore,
            pinEnabled = pinEnabled
        )
    }
}

/** Scan result (QR/OCR) carried back to the global Add-Booking sheet, prefilled. */
private data class ScanPrefill(
    val accountId: String,
    val amount: String?,
    val description: String?,
)

private fun accountTypeLabel(type: AccountType): String =
    when (type) {
        AccountType.GIRO -> "Girokonto"
        AccountType.CREDIT_CARD -> "Kreditkarte"
        AccountType.SPARKONTO -> "Sparkonto"
        AccountType.TAGESGELD -> "Tagesgeldkonto"
    }

private fun readTextFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().use { it.readText() }
        }
    } catch (_: Exception) {
        null
    }
}

private fun promoteScheduledBookings(
    bookings: MutableList<Booking>,
    bookingsRepository: IBookingRepository
) {
    val now = java.time.LocalDate.now()
    var changed = false
    for (i in bookings.indices) {
        val b = bookings[i]
        if (b.status == BookingStatus.SCHEDULED) {
            // Same date semantics as when the status was assigned: effectiveDate wins.
            val bookingDate = Instant.ofEpochMilli(b.effectiveDate ?: b.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            if (!bookingDate.isAfter(now)) {
                bookings[i] = b.copy(status = BookingStatus.POSTED, lastModifiedAt = Clock.System.now().toEpochMilliseconds())
                changed = true
            }
        }
    }
    if (changed) {
        bookingsRepository.saveBookings(bookings)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinTrackerApp(
    db: FintrackerDatabase,
    pinStore: PinStore,
    pinEnabled: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SqlAccountRepository(db) }
    val bookingsRepository = remember { SqlBookingRepository(db) }
    val recurringRepository = remember { SqlRecurringRuleRepository(db) }
    val categoriesRepository = remember { SqlCategoryRepository(db) }
    val settingsRepository = remember { SqlSettingsRepository(db) }
    val serverSyncStore = remember { ServerSyncStore(context) }
    val syncWriter = remember { io.github.willywonka644.fintracker.sync.SyncWriter(db) }
    val syncExporter = remember {
        SyncExporter(
            accountRepository = repository,
            bookingRepository = bookingsRepository,
            categoryRepository = categoriesRepository,
            recurringRuleRepository = recurringRepository,
            settingsRepository = settingsRepository
        )
    }
    val syncImporter = remember {
        SyncImporter(
            accountRepository = repository,
            bookingRepository = bookingsRepository,
            categoryRepository = categoriesRepository,
            recurringRuleRepository = recurringRepository
        )
    }
    var pendingSyncPreview by remember { mutableStateOf<SyncPreview?>(null) }
    var pendingRestoreBackup by remember { mutableStateOf<BackupData?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = readTextFromUri(context, uri)
        if (json == null) {
            Toast.makeText(context, context.getString(R.string.restore_read_error), Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        when (val parseResult = BackupParser.parse(json)) {
            is BackupParseResult.Success -> {
                pendingRestoreBackup = parseResult.backup
                showRestoreConfirm = true
            }
            is BackupParseResult.Error -> {
                val message = when (parseResult.error) {
                    BackupParseError.INVALID_JSON -> R.string.restore_invalid_json
                    BackupParseError.UNSUPPORTED_VERSION -> R.string.restore_unsupported_version
                    BackupParseError.MISSING_FIELDS -> R.string.restore_missing_fields
                }
                Toast.makeText(context, context.getString(message), Toast.LENGTH_LONG).show()
            }
        }
    }


    val accounts = remember { mutableStateListOf<Account>() }
    var nextId by remember { mutableIntStateOf(1) }

    val bookings = remember { mutableStateListOf<Booking>() }

    val recurringRules = remember { mutableStateListOf<RecurringRule>() }
    var nextRecurringId by remember { mutableIntStateOf(1) }

    val categories by categoriesRepository.categoriesFlow.collectAsState()

    // Vorrat fuer die Beschreibungs-Vorschlaege (#136). Einmal je Bestand, nicht je
    // Tastendruck: das Zaehlen laeuft ueber alle Buchungen.
    val descriptionSuggestionIndex = remember(bookings.toList()) {
        DescriptionSuggestions.index(bookings.toList())
    }

    fun reloadData() {
        val loadedAccounts = repository.reload()
        accounts.clear()
        accounts.addAll(loadedAccounts)
        val maxId = loadedAccounts.maxOfOrNull { it.id.toIntOrNull() ?: 0 } ?: 0
        nextId = maxId + 1

        val loadedBookings = bookingsRepository.reload()
        bookings.clear()
        bookings.addAll(loadedBookings)

        val loadedRules = recurringRepository.reload()
        recurringRules.clear()
        recurringRules.addAll(loadedRules)
        // IDs must never collide with soft-deleted tombstones: a colliding INSERT OR
        // REPLACE would let the tombstone overwrite the brand-new rule on save.
        val maxRuleId = recurringRepository.loadAllRulesForSync()
            .maxOfOrNull { it.id.removePrefix("r").toIntOrNull() ?: 0 } ?: 0
        nextRecurringId = maxRuleId + 1

        // Categories are driven by categoriesRepository.categoriesFlow —
        // reload() refreshes the flow which updates the UI automatically.
        categoriesRepository.reload()

        // Promote SCHEDULED bookings whose date has arrived
        promoteScheduledBookings(bookings, bookingsRepository)
    }

    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    var autoPostDone by remember { mutableStateOf(false) }
    var unsyncedCount by remember { mutableIntStateOf(0) }

    // ── Umbuchungen (#121) ───────────────────────────────────────────────────
    // Defined once rather than at each sheet: all three operations have to treat the
    // pair as one thing, and a copy of that rule per call site is a copy that can drift.

    fun saveTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        description: String,
        fromDate: Long,
        toDate: Long,
    ) {
        val (outgoing, incoming) = TransferFactory.create(
            idProvider = { UUID.randomUUID().toString() },
            groupIdProvider = { UUID.randomUUID().toString() },
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            description = description,
            fromDate = fromDate,
            toDate = toDate,
        )
        bookings.add(outgoing)
        bookings.add(incoming)
        bookingsRepository.saveBookings(bookings)
        unsyncedCount += 2
    }

    fun updateTransfer(
        outgoingId: String,
        incomingId: String,
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        description: String,
        fromDate: Long,
        toDate: Long,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        // The ids and the group stay — rewriting them would leave the old rows behind as
        // orphans on the other device, which is exactly the half transfer this type exists
        // to prevent. Only the fields the form can change are touched.
        fun rewrite(id: String, accountId: String, signedAmount: Double, date: Long) {
            val index = bookings.indexOfFirst { it.id == id }
            if (index < 0) return
            bookings[index] = bookings[index].copy(
                accountId = accountId,
                amount = signedAmount,
                description = description.trim(),
                timestamp = date,
                effectiveDate = date,
                status = if (java.time.Instant.ofEpochMilli(date)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                        .isAfter(java.time.LocalDate.now())
                ) BookingStatus.SCHEDULED else BookingStatus.POSTED,
                lastModifiedAt = now,
            )
        }
        rewrite(outgoingId, fromAccountId, -amount, fromDate)
        rewrite(incomingId, toAccountId, amount, toDate)
        bookingsRepository.saveBookings(bookings)
        unsyncedCount += 2
    }

    // Rejections live in app_settings, which is device-local and not part of the sync —
    // the tool is a one-off cleanup, and its "no thanks" has no business travelling to the
    // other devices as a new column in the middle of the merge.
    var rejectedTransferPairs by remember {
        mutableStateOf(TransferSuggestions.parseRejected(settingsRepository.get(SETTING_REJECTED_TRANSFERS)))
    }
    val transferCandidates: List<TransferCandidate> = remember(bookings.toList(), rejectedTransferPairs) {
        TransferSuggestions.find(bookings, rejected = rejectedTransferPairs)
    }

    fun confirmTransferCandidate(candidate: TransferCandidate) {
        val (outgoing, incoming) = candidate.confirm(
            groupId = UUID.randomUUID().toString(),
            now = Clock.System.now().toEpochMilliseconds(),
        )
        listOf(outgoing, incoming).forEach { updated ->
            val index = bookings.indexOfFirst { it.id == updated.id }
            if (index >= 0) bookings[index] = updated
        }
        bookingsRepository.saveBookings(bookings)
        unsyncedCount += 2
    }

    fun rejectTransferCandidate(candidate: TransferCandidate) {
        val raw = TransferSuggestions.withRejection(
            settingsRepository.get(SETTING_REJECTED_TRANSFERS),
            candidate.key,
        )
        settingsRepository.set(SETTING_REJECTED_TRANSFERS, raw)
        rejectedTransferPairs = TransferSuggestions.parseRejected(raw)
    }

    // ---- Beschreibungen aufraeumen (#135) -----------------------------------
    // Ablehnungen liegen wie bei den Umbuchungen in app_settings: geraetelokal und aus
    // dem Abgleich heraus. Ein "nein danke" hat im Merge nichts zu suchen.
    var rejectedCleanupGroups by remember {
        mutableStateOf(DescriptionCleanup.parseRejected(settingsRepository.get(SETTING_REJECTED_CLEANUP)))
    }
    val cleanupGroups: List<DescriptionCleanup.Group> =
        remember(bookings.toList(), rejectedCleanupGroups) {
            DescriptionCleanup.suggest(bookings, rejected = rejectedCleanupGroups)
        }

    fun renameDescriptionGroup(group: DescriptionCleanup.Group, target: String) {
        val changed = DescriptionCleanup.affectedCount(bookings, group, target)
        if (changed == 0) return
        val updated = DescriptionCleanup.apply(
            bookings.toList(), group, target,
            now = Clock.System.now().toEpochMilliseconds(),
        )
        bookings.clear()
        bookings.addAll(updated)
        bookingsRepository.saveBookings(bookings)
        unsyncedCount += changed
    }

    fun rejectDescriptionGroup(group: DescriptionCleanup.Group) {
        val raw = DescriptionCleanup.withRejection(
            settingsRepository.get(SETTING_REJECTED_CLEANUP),
            group.key,
        )
        settingsRepository.set(SETTING_REJECTED_CLEANUP, raw)
        rejectedCleanupGroups = DescriptionCleanup.parseRejected(raw)
    }

    fun deleteTransfer(groupId: String) {
        // Soft-delete, never removeAll+save: a hard removal would let the other device
        // resurrect both halves on the next sync. Same rule as the instalment groups.
        val now = System.currentTimeMillis()
        bookings.filter { it.transferGroupId == groupId }.forEach { b ->
            AttachmentStorage.deleteAttachment(b.attachmentPath)
            bookingsRepository.softDeleteBooking(b.id, now)
            unsyncedCount++
        }
        bookings.removeAll { it.transferGroupId == groupId }
    }

    /**
     * Loescht eine Buchung — und bei einer Umbuchung beide Haelften (#118).
     *
     * Lag vorher im Bearbeiten-Sheet des Buchungen-Tabs. Seit die Ansicht (#129) auch
     * loeschen kann, braucht die Regel eine Stelle statt zweier: eine einseitig
     * geloeschte Umbuchung verschiebt die Summen um den vollen Betrag.
     */
    fun deleteBooking(booking: Booking) {
        val groupId = booking.transferGroupId
        if (groupId != null) {
            deleteTransfer(groupId)
        } else {
            AttachmentStorage.deleteAttachment(booking.attachmentPath)
            bookings.removeAll { it.id == booking.id }
            bookingsRepository.softDeleteBooking(booking.id, System.currentTimeMillis())
            unsyncedCount++
        }
    }

    // Wann dieses Geraet zuletzt abgeglichen hat (#131). Lag im Store und wurde
    // nirgends gezeigt, also stand im Menue nur die eine Richtung — was noch nicht
    // gesendet ist — und das wurde als "alles auf demselben Stand" gelesen.
    var lastSyncedAt by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        reloadData()
        // Seeded from the data, not started at zero: the sync button claims a state
        // permanently, and a counter in memory would report "nothing outstanding" after
        // every app start (#110). The ForSync lists are the ones the exporter sends, so
        // tombstones are counted too.
        lastSyncedAt = serverSyncStore.lastSyncedAt.first()
        unsyncedCount = countUnsyncedSince(
            lastSyncedAt = lastSyncedAt,
            accounts = repository.loadAllAccountsForSync(),
            bookings = bookingsRepository.loadAllBookingsForSync(),
            recurringRules = recurringRepository.loadAllRulesForSync(),
            categories = categoriesRepository.loadAllCategoriesForSync(),
        )
    }

    // Auto-post due recurring bookings after data is loaded (no dialog)
    LaunchedEffect(recurringRules.toList(), autoPostDone) {
        if (!autoPostDone && recurringRules.isNotEmpty()) {
            val result = autoPostDueBookings(
                rules = recurringRules,
                existingBookings = bookings,
                idProvider = { UUID.randomUUID().toString() }
            )

            if (result.newBookings.isNotEmpty()) {
                bookings.addAll(result.newBookings)
                bookingsRepository.saveBookings(bookings)
            }

            // Update rules with advanced dates
            for (updatedRule in result.updatedRules) {
                val index = recurringRules.indexOfFirst { it.id == updatedRule.id }
                if (index != -1) {
                    recurringRules[index] = updatedRule
                }
            }

            // Remove exhausted rules (tombstoned so sync can't resurrect them)
            if (result.exhaustedRuleIds.isNotEmpty()) {
                val now = System.currentTimeMillis()
                result.exhaustedRuleIds.forEach { recurringRepository.softDeleteRule(it, now) }
                recurringRules.removeAll { it.id in result.exhaustedRuleIds }
            }

            if (result.updatedRules.isNotEmpty()) {
                recurringRepository.saveRules(recurringRules)
            }

            autoPostDone = true
        }
    }

    var isDialogOpen by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<Account?>(null) }

    var showPinSettingsDialog by remember { mutableStateOf(false) }
    var showQuickAddSheet by remember { mutableStateOf(false) }
    // Das Bearbeiten-Sheet liegt seit #129 hier oben statt im Buchungen-Tab: die
    // Ansicht oeffnet es ebenfalls, und zwei Abschriften desselben Formulars sind
    // zwei Stellen, an denen das Speichern auseinanderlaufen kann.
    var editBooking by remember { mutableStateOf<Booking?>(null) }
    var showWiFiSyncScreen by remember { mutableStateOf(false) }
    var showServerSyncScreen by remember { mutableStateOf(false) }
    var showServerSyncSettings by remember { mutableStateOf(false) }
    // When set, shows an account picker; the chosen account id is passed to this action.
    var accountPickerAction by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var accountPickerTitle by remember { mutableStateOf("") }
    // When set, shows the limit dialog for this account.
    var limitDialogAccount by remember { mutableStateOf<Account?>(null) }
    // When set, shows the global Add-Booking sheet prefilled from a QR/OCR scan.
    var scanPrefill by remember { mutableStateOf<ScanPrefill?>(null) }
    // Preselected account for the Buchungen tab (set when tapping an account in the overview).
    var buchungenInitialAccountId by remember { mutableStateOf<String?>(null) }

    val tabRoutes = listOf(ROUTE_ACCOUNTS_LIST, ROUTE_BUCHUNGEN_TAB, ROUTE_AUSWERTUNGEN_TAB, ROUTE_MEHR_TAB)
    val isOnTab = currentRoute in tabRoutes
    val selectedTab = tabRoutes.indexOf(currentRoute).coerceAtLeast(0)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isOnTab) {
                FinTrackerBottomBar(
                    selectedTab = selectedTab,
                    onTabSelect = { idx ->
                        // Tapping the Buchungen tab directly clears any per-account prefilter.
                        if (tabRoutes[idx] == ROUTE_BUCHUNGEN_TAB) buchungenInitialAccountId = null
                        navController.navigate(tabRoutes[idx]) {
                            popUpTo(ROUTE_ACCOUNTS_LIST) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if ((currentRoute == ROUTE_ACCOUNTS_LIST || currentRoute == ROUTE_BUCHUNGEN_TAB) && accounts.isNotEmpty()) {
                GradientFAB(onClick = { showQuickAddSheet = true })
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_ACCOUNTS_LIST,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
        composable(route = ROUTE_ACCOUNTS_LIST) {
            var showCloseDialog by remember { mutableStateOf(false) }
            BackHandler(enabled = unsyncedCount > 0) {
                showCloseDialog = true
            }
            AccountOverviewScreen(
                accounts = accounts,
                bookings = bookings,
                recurringRules = recurringRules,
                unsyncedCount = unsyncedCount,
                onAddAccountClick = {
                    accountToEdit = null
                    isDialogOpen = true
                },
                onAccountClick = { account ->
                    buchungenInitialAccountId = account.id
                    navController.navigate(ROUTE_BUCHUNGEN_TAB) {
                        popUpTo(ROUTE_ACCOUNTS_LIST) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToAudit = { accountId ->
                    navController.navigate("$ROUTE_AUDIT/$accountId")
                },
                onNavigateToSync = { showServerSyncScreen = true },
                onNavigateToAccountDetail = { accountId ->
                    buchungenInitialAccountId = accountId
                    navController.navigate(ROUTE_BUCHUNGEN_TAB) {
                        popUpTo(ROUTE_ACCOUNTS_LIST) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToRecurring = { accountId ->
                    navController.navigate("$ROUTE_DAUERAUFTRAEGE?accountId=$accountId")
                },
                onBookingClick = { booking ->
                    navController.navigate("$ROUTE_BOOKING_DETAIL/${booking.id}")
                },
            )

            if (showQuickAddSheet && accounts.isNotEmpty()) {
                val accountBalances = remember(accounts, bookings.toList()) {
                    accounts.associate { account ->
                        val balancePeriod = when (account.type) {
                            AccountType.CREDIT_CARD -> currentBillingCyclePeriod(
                                billingStartDay = account.billingStartDay ?: 18
                            )
                            else -> allTimePeriod()
                        }
                        account.id to filterBookingsForAccount(
                            allBookings = bookings,
                            accountId = account.id,
                            period = balancePeriod
                        ).sumOf { it.amount }
                    }
                }
                AddBookingSheet(
                    accounts = accounts,
                    accountBalances = accountBalances,
                    categories = categories,
                    descriptionSuggestions = descriptionSuggestionIndex,
                    onDismiss = { showQuickAddSheet = false },
                    onSaveTransfer = ::saveTransfer,
                    onSaveOneTime = { accountId, amount, desc, category, timestamp, effectiveDate, path ->
                        val dateForStatus = effectiveDate ?: timestamp
                        val bookingDate = java.time.Instant.ofEpochMilli(dateForStatus)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val status = if (bookingDate.isAfter(java.time.LocalDate.now())) BookingStatus.SCHEDULED else BookingStatus.POSTED
                        bookings.add(Booking(
                            id = UUID.randomUUID().toString(),
                            accountId = accountId, amount = amount, description = desc,
                            category = category, timestamp = timestamp, effectiveDate = effectiveDate,
                            status = status, attachmentPath = path,
                            lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                        ))
                        bookingsRepository.saveBookings(bookings)
                        unsyncedCount++
                    },
                    onAddCategory = { newCategory ->
                        categoriesRepository.saveCategories(categories + newCategory)
                        unsyncedCount++
                    },
                    onNavigateToImageViewer = { path ->
                        val encoded = Uri.encode(path)
                        navController.navigate("$ROUTE_IMAGE_VIEWER/$encoded")
                    },
                )
            }

            // The reminder opens the path its number is about (#124): the count is
            // measured against the last *server* sync, so "erst synchronisieren" has
            // to be the server sync. Wi-Fi stays as the fallback, reachable from "Mehr".
            if (showCloseDialog) {
                AlertDialog(
                    onDismissRequest = { showCloseDialog = false },
                    title = { Text("Ungespeicherte Änderungen") },
                    text = { Text("Es gibt $unsyncedCount nicht synchronisierte Änderung${if (unsyncedCount == 1) "" else "en"}. Sie sind noch nicht beim Server angekommen. Trotzdem schließen?") },
                    confirmButton = {
                        TextButton(onClick = {
                            showCloseDialog = false
                            (context as? android.app.Activity)?.finish()
                        }) { Text("Ja, schließen") }
                    },
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = {
                                showCloseDialog = false
                                showServerSyncScreen = true
                            }) { Text("Server-Sync") }
                            TextButton(onClick = { showCloseDialog = false }) { Text("Abbrechen") }
                        }
                    },
                )
            }
        }

        // ── Buchungsansicht (#129) ────────────────────────────────────────────
        // Ueber die Id statt ueber das Objekt: nach einer Bearbeitung steht hier sonst
        // der Stand von vorhin. Ist die Buchung inzwischen geloescht, gibt es nichts
        // mehr zu zeigen und der Schirm geht zurueck.
        composable(
            route = "$ROUTE_BOOKING_DETAIL/{bookingId}",
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId")
            val booking = bookings.firstOrNull { it.id == bookingId }
            if (booking == null) {
                LaunchedEffect(bookingId) { navController.popBackStack() }
            } else {
                val counterpart = booking.transferGroupId?.let { group ->
                    bookings.firstOrNull { it.transferGroupId == group && it.id != booking.id }
                }
                BookingDetailScreen(
                    booking = booking,
                    accountName = accounts.firstOrNull { it.id == booking.accountId }?.name ?: "Unbekanntes Konto",
                    category = categories.firstOrNull { it.name == booking.category },
                    counterpartAccountName = counterpart?.let { c ->
                        accounts.firstOrNull { it.id == c.accountId }?.name
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onEdit = { editBooking = booking },
                    onDelete = {
                        deleteBooking(booking)
                        navController.popBackStack()
                    },
                    onOpenAttachment = { path ->
                        navController.navigate("$ROUTE_IMAGE_VIEWER/${Uri.encode(path)}")
                    },
                )
            }
        }

        composable(route = ROUTE_HELP) {
            HelpScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(route = ROUTE_ABOUT) {
            AboutScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(route = ROUTE_KONTEN) {
            // Same balance rule as everywhere else: a card over its current billing
            // cycle, everything else all-time.
            val kontenBalances = remember(accounts.toList(), bookings.toList()) {
                accounts.associate { account ->
                    val period = when (account.type) {
                        AccountType.CREDIT_CARD -> currentBillingCyclePeriod(
                            billingStartDay = account.billingStartDay ?: 18
                        )
                        else -> allTimePeriod()
                    }
                    account.id to filterBookingsForAccount(
                        allBookings = bookings,
                        accountId = account.id,
                        period = period,
                    ).sumOf { it.amount }
                }
            }
            KontenScreen(
                accounts = accounts,
                accountBalances = kontenBalances,
                onNavigateBack = { navController.popBackStack() },
                onAddAccount = { accountToEdit = null; isDialogOpen = true },
                onEditAccount = { account -> accountToEdit = account; isDialogOpen = true },
            )
        }

        composable(route = ROUTE_TRANSFER_SUGGESTIONS) {
            TransferSuggestionsScreen(
                candidates = transferCandidates,
                accounts = accounts,
                onConfirm = { confirmTransferCandidate(it) },
                onReject = { rejectTransferCandidate(it) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(route = ROUTE_DESCRIPTION_CLEANUP) {
            DescriptionCleanupScreen(
                groups = cleanupGroups,
                onRename = { group, target -> renameDescriptionGroup(group, target) },
                onReject = { rejectDescriptionGroup(it) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(route = ROUTE_CATEGORIES) {
            val bookingCounts = remember(bookings.toList()) {
                bookings.groupingBy { it.category ?: "" }.eachCount().filterKeys { it.isNotBlank() }
            }
            CategoryManagementScreen(
                categories = categories,
                bookingCounts = bookingCounts,
                onNavigateBack = { navController.popBackStack() },
                onAddCategory = { newCategory ->
                    categoriesRepository.saveCategories(categories + newCategory)
                    unsyncedCount++
                },
                onUpdateCategory = { updated ->
                    categoriesRepository.saveCategories(
                        categories.map { if (it.id == updated.id) updated else it }
                    )
                    unsyncedCount++
                },
                onDeleteCategory = { toDelete ->
                    // Soft delete: dropping the row would let the other device's live copy
                    // win the next merge and bring the category straight back.
                    categoriesRepository.softDeleteCategory(
                        toDelete.id,
                        Clock.System.now().toEpochMilliseconds(),
                    )
                    unsyncedCount++
                }
            )
        }

        composable(
            route = "$ROUTE_DAUERAUFTRAEGE?accountId={accountId}",
            arguments = listOf(navArgument("accountId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId")

            // Compute display-friendly next execution dates for ALL rules
            val displayRules = recurringRules.map { rule ->
                val nextScheduledDate = bookings
                    .filter { it.recurringRuleId == rule.id && it.status == BookingStatus.SCHEDULED }
                    .minByOrNull { it.timestamp }
                    ?.let {
                        Instant.ofEpochMilli(it.timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    }
                if (nextScheduledDate != null) rule.copy(nextExecutionDate = nextScheduledDate.toKotlinLocalDate())
                else rule
            }

            var showAddRuleDialog by remember { mutableStateOf(false) }
            var editingRule by remember { mutableStateOf<RecurringRule?>(null) }
            // Account preselected in the add dialog: the screen's dropdown choice wins,
            // falling back to the (optional) route account.
            var addRuleAccountId by remember { mutableStateOf<String?>(null) }

            // Reorder accounts so the preselected one is first (pre-selected in dialog)
            val preselectId = addRuleAccountId ?: accountId
            val reorderedAccounts = buildList {
                accounts.firstOrNull { it.id == preselectId }?.let { add(it) }
                addAll(accounts.filter { it.id != preselectId })
            }

            RecurringRulesScreen(
                rules = displayRules,
                accounts = accounts,
                categories = categories,
                initialAccountId = accountId,
                onNavigateBack = { navController.popBackStack() },
                onAddRule = { selectedAccId ->
                    addRuleAccountId = selectedAccId
                    showAddRuleDialog = true
                },
                onEditRule = { displayRule ->
                    editingRule = recurringRules.firstOrNull { it.id == displayRule.id }
                },
            )

            if (showAddRuleDialog) {
                AddEditRecurringRuleDialog(
                    initialRule = null,
                    accounts = reorderedAccounts,
                    categories = categories,
                    onAddCategory = { newCategory ->
                        categoriesRepository.saveCategories(categories + newCategory)
                        unsyncedCount++
                    },
                    onDismiss = { showAddRuleDialog = false },
                    onSave = { rule ->
                        val newRule = rule.copy(id = "r${nextRecurringId}")
                        nextRecurringId++
                        val result = generateInitialBookings(
                            rule = newRule,
                            idProvider = { UUID.randomUUID().toString() }
                        )
                        if (result.bookings.isNotEmpty()) {
                            bookings.addAll(result.bookings)
                            bookingsRepository.saveBookings(bookings)
                        }
                        val updatedRule = result.updatedRule
                        val rem = updatedRule.remainingExecutions
                        if (rem == null || rem > 0) {
                            recurringRules.add(updatedRule)
                        }
                        recurringRepository.saveRules(recurringRules)
                        unsyncedCount++
                        showAddRuleDialog = false
                    }
                )
            }

            if (editingRule != null) {
                AddEditRecurringRuleDialog(
                    initialRule = editingRule,
                    accounts = reorderedAccounts,
                    categories = categories,
                    onAddCategory = { newCategory ->
                        categoriesRepository.saveCategories(categories + newCategory)
                        unsyncedCount++
                    },
                    onDismiss = { editingRule = null },
                    onSave = { updatedRule ->
                        val index = recurringRules.indexOfFirst { it.id == updatedRule.id }
                        if (index != -1) {
                            recurringRules[index] = updatedRule
                            recurringRepository.saveRules(recurringRules)
                            unsyncedCount++
                        }
                        editingRule = null
                    },
                    onDelete = { ruleToDelete ->
                        // Deleting a rule removes the rule and its SCHEDULED bookings only.
                        // POSTED bookings are history — real money that moved — and must
                        // survive (deleting them once cost the user 900 EUR of ledger truth).
                        val now = System.currentTimeMillis()
                        bookings.filter {
                            it.recurringRuleId == ruleToDelete.id && it.status == BookingStatus.SCHEDULED
                        }.forEach { b ->
                            AttachmentStorage.deleteAttachment(b.attachmentPath)
                            bookingsRepository.softDeleteBooking(b.id, now)
                        }
                        bookings.removeAll {
                            it.recurringRuleId == ruleToDelete.id && it.status == BookingStatus.SCHEDULED
                        }
                        recurringRepository.softDeleteRule(ruleToDelete.id, now)
                        recurringRules.removeAll { it.id == ruleToDelete.id }
                        unsyncedCount++
                        editingRule = null
                    }
                )
            }
        }

        composable(
            route = "$ROUTE_KONTOABGLEICH/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            val account = accounts.firstOrNull { it.id == accountId }
            if (account != null) {
                KontoabgleichScreen(
                    accountId = accountId,
                    accountName = account.name,
                    allBookings = bookings,
                    onNavigateBack = { navController.popBackStack() },
                    onCreateCorrectionBooking = { correctionBooking ->
                        bookings.add(correctionBooking)
                        bookingsRepository.saveBookings(bookings)
                        unsyncedCount++
                    }
                )
            }
        }

        composable(
            route = "$ROUTE_AUDIT/{accountId}?periodFrom={periodFrom}&periodTo={periodTo}",
            arguments = listOf(
                navArgument("accountId") { type = NavType.StringType },
                navArgument("periodFrom") { type = NavType.LongType; defaultValue = 0L },
                navArgument("periodTo") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            val account = accounts.firstOrNull { it.id == accountId } ?: return@composable
            val periodFrom = backStackEntry.arguments?.getLong("periodFrom") ?: 0L
            val periodTo = backStackEntry.arguments?.getLong("periodTo") ?: 0L

            val auditPeriod = if (periodFrom > 0L && periodTo > 0L) {
                io.github.willywonka644.fintracker.analytics.PeriodFilter(
                    type = io.github.willywonka644.fintracker.analytics.PeriodType.CUSTOM,
                    fromInclusive = periodFrom,
                    toExclusive = periodTo
                )
            } else {
                allTimePeriod()
            }

            val auditBookings = remember(bookings.toList(), accountId, auditPeriod) {
                filterBookingsForAccount(
                    allBookings = bookings,
                    accountId = accountId,
                    period = auditPeriod
                ).sortedByDescending { it.timestamp }
            }

            AuditScreen(
                accountName = account.name,
                bookings = auditBookings,
                categories = categories,
                onToggleVerified = { booking ->
                    val index = bookings.indexOfFirst { it.id == booking.id }
                    if (index != -1) {
                        bookings[index] = bookings[index].copy(isVerified = !bookings[index].isVerified, lastModifiedAt = Clock.System.now().toEpochMilliseconds())
                        bookingsRepository.saveBookings(bookings)
                        unsyncedCount++
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "$ROUTE_RATENZAHLUNG?accountId={accountId}",
            arguments = listOf(navArgument("accountId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId")

            val installmentGroups = remember(bookings.toList(), accounts) {
                accounts.flatMap { acc -> buildInstallmentGroups(bookings, acc.id) }
                    .sortedBy { it.description }
            }

            var showAddDialog by remember { mutableStateOf(false) }
            // Account the new installment is booked on: the screen's dropdown selection,
            // or picked via the account sheet when "Alle Konten" is active.
            var addDialogAccountId by remember { mutableStateOf<String?>(null) }

            InstallmentsScreen(
                groups = installmentGroups,
                accounts = accounts,
                categories = categories,
                initialAccountId = accountId,
                onNavigateBack = { navController.popBackStack() },
                onAddInstallment = { selectedAccId ->
                    if (selectedAccId != null) {
                        addDialogAccountId = selectedAccId
                        showAddDialog = true
                    } else {
                        accountPickerTitle = "Konto für neue Ratenzahlung"
                        accountPickerAction = { id ->
                            addDialogAccountId = id
                            showAddDialog = true
                        }
                    }
                },
                onDeleteGroup = { groupId ->
                    // Soft-delete each booking (sync tombstone!) — a hard removeAll+save would
                    // resurrect the bookings from the other device on the next Wi-Fi sync.
                    val now = System.currentTimeMillis()
                    bookings.filter { it.installmentGroupId == groupId }.forEach { b ->
                        AttachmentStorage.deleteAttachment(b.attachmentPath)
                        bookingsRepository.softDeleteBooking(b.id, now)
                    }
                    bookings.removeAll { it.installmentGroupId == groupId }
                    unsyncedCount++
                },
                onUpdateGroup = { groupId, newDescription, newCategory, newRateAmount, newTotalCount ->
                    val nowMs = Clock.System.now().toEpochMilliseconds()
                    val zoneId = ZoneId.systemDefault()
                    fun groupSorted() = bookings
                        .filter { it.installmentGroupId == groupId }
                        .sortedBy { it.effectiveDate ?: it.timestamp }

                    val current = groupSorted()
                    if (current.isNotEmpty()) {
                        // Schritt 1 - Shrink: drop surplus SCHEDULED rates from the end. POSTED rates are
                        //    history and are never touched (UI enforces count >= paid rates).
                        val surplus = current.size - newTotalCount
                        if (surplus > 0) {
                            val removable = current
                                .filter { it.status == BookingStatus.SCHEDULED }
                                .takeLast(surplus)
                            removable.forEach { b ->
                                AttachmentStorage.deleteAttachment(b.attachmentPath)
                                bookingsRepository.softDeleteBooking(b.id, System.currentTimeMillis())
                            }
                            val removeIds = removable.map { it.id }.toSet()
                            bookings.removeAll { it.id in removeIds }
                        }

                        // Schritt 2 - Grow: append rates continuing the group's date pattern. The interval
                        //    is inferred from the gap between the last two rates (it is not stored).
                        val afterTrim = groupSorted()
                        val missing = newTotalCount - afterTrim.size
                        if (missing > 0 && afterTrim.isNotEmpty()) {
                            val dates = afterTrim.map { b ->
                                Instant.ofEpochMilli(b.effectiveDate ?: b.timestamp)
                                    .atZone(zoneId).toLocalDate()
                            }
                            val gapDays = if (dates.size >= 2)
                                dates.last().toEpochDay() - dates[dates.size - 2].toEpochDay()
                            else 30L
                            var d = dates.last()
                            val today = java.time.LocalDate.now()
                            repeat(missing) {
                                d = when (gapDays) {
                                    in 5L..10L -> d.plusWeeks(1)
                                    in 350L..380L -> d.plusYears(1)
                                    else -> d.plusMonths(1)
                                }
                                val ts = d.atStartOfDay(zoneId).toInstant().toEpochMilli()
                                bookings.add(Booking(
                                    id = UUID.randomUUID().toString(),
                                    accountId = afterTrim.first().accountId,
                                    amount = newRateAmount,
                                    description = newDescription, // renumbered below
                                    timestamp = ts,
                                    category = newCategory,
                                    status = if (d.isAfter(today)) BookingStatus.SCHEDULED else BookingStatus.POSTED,
                                    effectiveDate = ts,
                                    installmentGroupId = groupId,
                                    lastModifiedAt = nowMs,
                                ))
                            }
                        }

                        // Schritt 3 - Description/category for all rates, new amount for open (SCHEDULED)
                        //    rates only, and a clean " i/n" renumbering in date order.
                        val finalSorted = groupSorted()
                        val total = finalSorted.size
                        finalSorted.forEachIndexed { idx, b ->
                            val i = bookings.indexOfFirst { it.id == b.id }
                            if (i != -1) {
                                bookings[i] = b.copy(
                                    description = "$newDescription ${idx + 1}/$total",
                                    category = newCategory,
                                    amount = if (b.status == BookingStatus.SCHEDULED) newRateAmount else b.amount,
                                    lastModifiedAt = nowMs,
                                )
                            }
                        }
                        bookingsRepository.saveBookings(bookings)
                        unsyncedCount++
                    }
                },
                onAddCategory = { newCategory ->
                    categoriesRepository.saveCategories(categories + newCategory)
                    unsyncedCount++
                },
            )

            val targetAccountId = addDialogAccountId
            if (showAddDialog && targetAccountId != null) {
                AddInstallmentDialog(
                    categories = categories,
                    onDismiss = { showAddDialog = false },
                    onAddCategory = { newCategory ->
                        categoriesRepository.saveCategories(categories + newCategory)
                    },
                    onSave = { input ->
                        val groupId = "inst_${System.currentTimeMillis()}"
                        val perRate = input.totalAmount / input.installmentCount
                        val today = java.time.LocalDate.now()
                        val zoneId = ZoneId.systemDefault()

                        var currentDate = input.startDate
                        for (i in 0 until input.installmentCount) {
                            val dateForStatus = currentDate
                            val status = if (dateForStatus.isAfter(today)) {
                                BookingStatus.SCHEDULED
                            } else {
                                BookingStatus.POSTED
                            }
                            val timestamp = currentDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
                            val effectiveDateMillis = timestamp

                            val booking = Booking(
                                id = UUID.randomUUID().toString(),
                                accountId = targetAccountId,
                                amount = perRate,
                                description = "${input.description} ${i + 1}/${input.installmentCount}",
                                timestamp = timestamp,
                                category = input.category,
                                status = status,
                                effectiveDate = effectiveDateMillis,
                                installmentGroupId = groupId,
                                lastModifiedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                            )
                            bookings.add(booking)

                            // Advance date by frequency
                            currentDate = when (input.frequency) {
                                Frequency.WEEKLY -> currentDate.plusWeeks(1)
                                Frequency.MONTHLY -> currentDate.plusMonths(1)
                                Frequency.YEARLY -> currentDate.plusYears(1)
                            }
                        }
                        bookingsRepository.saveBookings(bookings)
                        unsyncedCount++
                        showAddDialog = false
                    }
                )
            }
        }

        composable(
            route = "$ROUTE_CSV_IMPORT/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            val account = accounts.firstOrNull { it.id == accountId } ?: return@composable
            CsvImportScreen(
                accountId = accountId,
                accountName = account.name,
                categories = categories,
                existingBookings = bookings.toList(),
                onNavigateBack = { navController.popBackStack() },
                onImportBookings = { importedBookings ->
                    importedBookings.forEach { booking ->
                        bookings.add(booking)
                    }
                    bookingsRepository.saveBookings(bookings)
                    unsyncedCount++
                }
            )
        }

        composable(
            route = "$ROUTE_QR_SCAN/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            QrScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onEpcScanned = { epcData ->
                    val baseDesc = epcData.text ?: epcData.reference ?: ""
                    val merchant = epcData.recipientName
                    val desc = if (merchant.isNotBlank() && baseDesc.isNotBlank()) {
                        "$baseDesc – $merchant"
                    } else {
                        baseDesc.ifBlank { merchant }
                    }
                    scanPrefill = ScanPrefill(
                        accountId = accountId,
                        amount = epcData.amount?.let { String.format("-%.2f", it) },
                        description = desc,
                    )
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "$ROUTE_OCR_SCAN/{accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            io.github.willywonka644.fintracker.ocr.OcrScanScreen(
                onNavigateBack = { navController.popBackStack() },
                onOcrResult = { ocrResult ->
                    scanPrefill = ScanPrefill(
                        accountId = accountId,
                        amount = ocrResult.amount?.let { String.format("-%.2f", it) },
                        description = ocrResult.merchantName,
                    )
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = "$ROUTE_IMAGE_VIEWER/{encodedPath}",
            arguments = listOf(navArgument("encodedPath") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("encodedPath") ?: return@composable
            val imagePath = Uri.decode(encodedPath)
            ImageViewerScreen(
                imagePath = imagePath,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_BUCHUNGEN_TAB) {
            // The account the list is currently filtered to. Null means "Alle Konten",
            // and then the sheet still has to ask (#112).
            var buchungenActiveAccountId by remember { mutableStateOf<String?>(null) }

            if (showQuickAddSheet && accounts.isNotEmpty()) {
                val accountBalances = remember(accounts, bookings.toList()) {
                    accounts.associate { account ->
                        val balancePeriod = when (account.type) {
                            AccountType.CREDIT_CARD -> currentBillingCyclePeriod(
                                billingStartDay = account.billingStartDay ?: 18
                            )
                            else -> allTimePeriod()
                        }
                        account.id to filterBookingsForAccount(
                            allBookings = bookings,
                            accountId = account.id,
                            period = balancePeriod
                        ).sumOf { it.amount }
                    }
                }
                AddBookingSheet(
                    accounts = accounts,
                    accountBalances = accountBalances,
                    categories = categories,
                    descriptionSuggestions = descriptionSuggestionIndex,
                    initialAccountId = buchungenActiveAccountId,
                    onDismiss = { showQuickAddSheet = false },
                    onSaveTransfer = ::saveTransfer,
                    onSaveOneTime = { accountId, amount, desc, category, timestamp, effectiveDate, path ->
                        val dateForStatus = effectiveDate ?: timestamp
                        val bookingDate = java.time.Instant.ofEpochMilli(dateForStatus)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val status = if (bookingDate.isAfter(java.time.LocalDate.now())) BookingStatus.SCHEDULED else BookingStatus.POSTED
                        bookings.add(Booking(
                            id = UUID.randomUUID().toString(),
                            accountId = accountId, amount = amount, description = desc,
                            category = category, timestamp = timestamp, effectiveDate = effectiveDate,
                            status = status, attachmentPath = path,
                            lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                        ))
                        bookingsRepository.saveBookings(bookings)
                        unsyncedCount++
                    },
                    onAddCategory = { newCategory ->
                        categoriesRepository.saveCategories(categories + newCategory)
                        unsyncedCount++
                    },
                    onNavigateToImageViewer = { path ->
                        val encoded = Uri.encode(path)
                        navController.navigate("$ROUTE_IMAGE_VIEWER/$encoded")
                    },
                )
            }

            BuchungenScreen(
                bookings = bookings,
                categories = categories,
                accounts = accounts,
                // Fuehrt in die Ansicht, nicht ins Formular (#129).
                onBookingClick = { booking ->
                    navController.navigate("$ROUTE_BOOKING_DETAIL/${booking.id}")
                },
                initialAccountId = buchungenInitialAccountId,
                onSelectedAccountChange = { buchungenActiveAccountId = it },
            )

        }

        composable(ROUTE_AUSWERTUNGEN_TAB) {
            AuswertungenTabScreen(
                bookings = bookings,
                categories = categories,
                accounts = accounts,
                onBookingClick = { booking ->
                    navController.navigate("$ROUTE_BOOKING_DETAIL/${booking.id}")
                },
            )
        }

        composable(ROUTE_MEHR_TAB) {
            MehrScreen(
                pinEnabled = pinEnabled,
                unsyncedCount = unsyncedCount,
                onPinClick = { showPinSettingsDialog = true },
                transferSuggestionCount = transferCandidates.size,
                onTransferSuggestionsClick = { navController.navigate(ROUTE_TRANSFER_SUGGESTIONS) },
                descriptionCleanupCount = cleanupGroups.size,
                onDescriptionCleanupClick = { navController.navigate(ROUTE_DESCRIPTION_CLEANUP) },
                onKontenClick = { navController.navigate(ROUTE_KONTEN) },
                onCategoriesClick = { navController.navigate(ROUTE_CATEGORIES) },
                onDauerauftraegeClick = { navController.navigate(ROUTE_DAUERAUFTRAEGE) },
                onRatenzahlungenClick = { navController.navigate(ROUTE_RATENZAHLUNG) },
                onAuditClick = {
                    accountPickerTitle = "Konto für Audit"
                    accountPickerAction = { id -> navController.navigate("$ROUTE_AUDIT/$id") }
                },
                onKontoabgleichClick = {
                    accountPickerTitle = "Konto für Kontoabgleich"
                    accountPickerAction = { id -> navController.navigate("$ROUTE_KONTOABGLEICH/$id") }
                },
                onCsvImportClick = {
                    accountPickerTitle = "Konto für CSV-Import"
                    accountPickerAction = { id -> navController.navigate("$ROUTE_CSV_IMPORT/$id") }
                },
                onLimitClick = {
                    accountPickerTitle = "Konto für Limit"
                    accountPickerAction = { id -> limitDialogAccount = accounts.firstOrNull { it.id == id } }
                },
                onBelegScannenClick = {
                    accountPickerTitle = "Konto für Beleg-Scan"
                    accountPickerAction = { id -> navController.navigate("$ROUTE_OCR_SCAN/$id") }
                },
                onQrScannenClick = {
                    accountPickerTitle = "Konto für QR-Scan"
                    accountPickerAction = { id -> navController.navigate("$ROUTE_QR_SCAN/$id") }
                },
                onExportJson = { scope.launch { runCatching { ExportService.exportAsJson(context, db) } } },
                onRestoreJson = { restoreLauncher.launch(arrayOf("application/json")) },
                lastSyncedAt = lastSyncedAt,
                onSyncWiFi = { showWiFiSyncScreen = true },
                onServerSyncClick = { showServerSyncScreen = true },
                onHelpClick = { navController.navigate(ROUTE_HELP) },
                onAboutClick = { navController.navigate(ROUTE_ABOUT) },
            )
        }
    }
    } // end outer Scaffold content

    if (isDialogOpen) {
        AddEditAccountDialog(
            initialAccount = accountToEdit,
            deletionImpact = accountToEdit
                ?.let { accountDeletionImpact(it.id, bookings, recurringRules) }
                ?: AccountDeletionImpact(0, 0, 0, 0),
            onDismiss = { isDialogOpen = false },
            onSave = { partial ->
                if (accountToEdit == null) {
                    val newAccount = partial.copy(id = nextId.toString())
                    nextId++
                    accounts.add(newAccount)
                } else {
                    val index = accounts.indexOfFirst { it.id == accountToEdit!!.id }
                    if (index != -1) {
                        accounts[index] = partial.copy(id = accountToEdit!!.id)
                    }
                }
                repository.saveAccounts(accounts)
                unsyncedCount++
                isDialogOpen = false
            },
            onDelete = { account ->
                // Cascade soft-delete (sync tombstones!): account, its bookings and its
                // rules keep tombstone rows so the deletion wins on the next Wi-Fi sync
                // instead of being resurrected by the other device's live copies.
                val now = System.currentTimeMillis()
                bookings.filter { it.accountId == account.id }.forEach { booking ->
                    AttachmentStorage.deleteAttachment(booking.attachmentPath)
                    bookingsRepository.softDeleteBooking(booking.id, now)
                }
                bookings.removeAll { it.accountId == account.id }
                recurringRules.filter { it.accountId == account.id }.forEach { rule ->
                    recurringRepository.softDeleteRule(rule.id, now)
                }
                recurringRules.removeAll { it.accountId == account.id }
                repository.softDeleteAccount(account.id, now)
                accounts.removeAll { it.id == account.id }
                unsyncedCount++
                isDialogOpen = false
            }
        )
    }

    if (showPinSettingsDialog) {
        val scope = rememberCoroutineScope()
        PinSettingsDialog(
            pinEnabled = pinEnabled,
            onDismiss = { showPinSettingsDialog = false },
            onSetPin = { pin ->
                scope.launch {
                    pinStore.setPin(pin)
                    showPinSettingsDialog = false
                }
            },
            onDisablePin = {
                scope.launch {
                    pinStore.disablePin()
                    showPinSettingsDialog = false
                }
            }
        )
    }

    // ── Bearbeiten, von ueberall erreichbar (#129) ────────────────────────────
    editBooking?.let { booking ->
        val balancesForEdit = remember(accounts, bookings.toList()) {
            accounts.associate { account ->
                val bp = when (account.type) {
                    AccountType.CREDIT_CARD -> currentBillingCyclePeriod(billingStartDay = account.billingStartDay ?: 18)
                    else -> allTimePeriod()
                }
                account.id to filterBookingsForAccount(allBookings = bookings, accountId = account.id, period = bp).sumOf { it.amount }
            }
        }
        AddBookingSheet(
            accounts = accounts,
            accountBalances = balancesForEdit,
            categories = categories,
            descriptionSuggestions = descriptionSuggestionIndex,
            initialBooking = booking,
            initialTransferCounterpart = bookings.transferCounterpart(booking),
            onUpdateTransfer = { outId, inId, from, to, amount, desc, fromDate, toDate ->
                updateTransfer(outId, inId, from, to, amount, desc, fromDate, toDate)
                editBooking = null
            },
            onDismiss = { editBooking = null },
            onSaveOneTime = { _, amount, desc, category, timestamp, effectiveDate, path ->
                if (booking.attachmentPath != path) AttachmentStorage.deleteAttachment(booking.attachmentPath)
                val dateForStatus = effectiveDate ?: timestamp
                val bookingDate = java.time.Instant.ofEpochMilli(dateForStatus)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                val newStatus = if (bookingDate.isAfter(java.time.LocalDate.now())) BookingStatus.SCHEDULED else BookingStatus.POSTED
                val updated = booking.copy(
                    amount = amount, description = desc, category = category,
                    timestamp = timestamp, effectiveDate = effectiveDate,
                    status = newStatus, attachmentPath = path,
                    lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                )
                val index = bookings.indexOfFirst { it.id == updated.id }
                if (index != -1) {
                    bookings[index] = updated
                    bookingsRepository.saveBookings(bookings)
                    unsyncedCount++
                }
                editBooking = null
            },
            onDeleteBooking = {
                deleteBooking(booking)
                editBooking = null
                // Die Ansicht zeigte gerade diese Buchung — ohne sie hat sie nichts mehr.
                navController.popBackStack()
            },
            onAddCategory = { newCategory ->
                categoriesRepository.saveCategories(categories + newCategory)
                unsyncedCount++
            },
            onNavigateToImageViewer = { path ->
                navController.navigate("$ROUTE_IMAGE_VIEWER/${Uri.encode(path)}")
            },
        )
    }

    if (showWiFiSyncScreen) {
        io.github.willywonka644.fintracker.sync.WiFiSyncScreen(
            context = context,
            syncExporter = syncExporter,
            syncImporter = syncImporter,
            syncWriter = syncWriter,
            settingsRepository = settingsRepository,
            onDismiss = { showWiFiSyncScreen = false },
            onSyncComplete = {
                unsyncedCount = 0
                scope.launch {
                    val now = System.currentTimeMillis()
                    serverSyncStore.markSynced(now)
                    lastSyncedAt = now
                }
                reloadData()
            },
        )
    }

    // Settings sit on top of the sync screen rather than beside it: the address
    // is only ever needed because of a sync, and a wrong one is discovered there.
    if (showServerSyncScreen) {
        io.github.willywonka644.fintracker.sync.ServerSyncScreen(
            context = context,
            syncExporter = syncExporter,
            syncImporter = syncImporter,
            syncWriter = syncWriter,
            onOpenSettings = { showServerSyncSettings = true },
            onDismiss = { showServerSyncScreen = false },
            onSyncComplete = {
                unsyncedCount = 0
                scope.launch {
                    val now = System.currentTimeMillis()
                    serverSyncStore.markSynced(now)
                    lastSyncedAt = now
                }
                reloadData()
            },
        )
    }

    if (showServerSyncSettings) {
        io.github.willywonka644.fintracker.sync.ServerSyncSettingsScreen(
            context = context,
            onDismiss = { showServerSyncSettings = false },
        )
    }

    // Global Add-Booking sheet, prefilled from a QR/OCR scan started via "Mehr" → scannen
    scanPrefill?.let { prefill ->
        val scanAccountBalances = remember(accounts, bookings.toList()) {
            accounts.associate { account ->
                val balancePeriod = when (account.type) {
                    AccountType.CREDIT_CARD -> currentBillingCyclePeriod(
                        billingStartDay = account.billingStartDay ?: 18
                    )
                    else -> allTimePeriod()
                }
                account.id to filterBookingsForAccount(
                    allBookings = bookings,
                    accountId = account.id,
                    period = balancePeriod
                ).sumOf { it.amount }
            }
        }
        AddBookingSheet(
            accounts = accounts,
            accountBalances = scanAccountBalances,
            categories = categories,
            descriptionSuggestions = descriptionSuggestionIndex,
            initialAccountId = prefill.accountId,
            prefillAmount = prefill.amount,
            prefillDescription = prefill.description,
            onDismiss = { scanPrefill = null },
            onSaveOneTime = { accountId, amount, desc, category, timestamp, effectiveDate, path ->
                val dateForStatus = effectiveDate ?: timestamp
                val bookingDate = java.time.Instant.ofEpochMilli(dateForStatus)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                val status = if (bookingDate.isAfter(java.time.LocalDate.now())) BookingStatus.SCHEDULED else BookingStatus.POSTED
                bookings.add(Booking(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId, amount = amount, description = desc,
                    category = category, timestamp = timestamp, effectiveDate = effectiveDate,
                    status = status, attachmentPath = path,
                    lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                ))
                bookingsRepository.saveBookings(bookings)
                unsyncedCount++
                scanPrefill = null
            },
            onAddCategory = { newCategory ->
                categoriesRepository.saveCategories(categories + newCategory)
                unsyncedCount++
            },
            onNavigateToImageViewer = { path ->
                val encoded = Uri.encode(path)
                navController.navigate("$ROUTE_IMAGE_VIEWER/$encoded")
            },
        )
    }

    // Limit dialog opened from the "Mehr" → Limit tool. Any account, not only cards,
    // and the wording follows the account type (#99).
    limitDialogAccount?.let { acc ->
        SpendingLimitDialog(
            kind = limitKindFor(acc.type),
            currentLimit = acc.spendingLimit,
            onDismiss = { limitDialogAccount = null },
            onSave = { newLimit ->
                val index = accounts.indexOfFirst { it.id == acc.id }
                if (index != -1) {
                    accounts[index] = acc.copy(
                        spendingLimit = newLimit,
                        lastModifiedAt = Clock.System.now().toEpochMilliseconds(),
                    )
                    repository.saveAccounts(accounts)
                    unsyncedCount++
                }
                limitDialogAccount = null
            },
        )
    }

    // Account picker for the "Mehr" tools (Daueraufträge / Ratenzahlungen / Audit / CSV / Limit)
    if (accountPickerAction != null) {
        ModalBottomSheet(
            onDismissRequest = { accountPickerAction = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    accountPickerTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                val pickerAccounts = accounts
                if (pickerAccounts.isEmpty()) {
                    Text(
                        "Keine Konten vorhanden",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinTheme.colors.textSub,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    pickerAccounts.forEach { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val action = accountPickerAction
                                    accountPickerAction = null
                                    action?.invoke(account.id)
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(account.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    accountTypeLabel(account.type),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FinTheme.colors.textSub,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = FinTheme.colors.textFaint,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            }
        }
    }

    pendingSyncPreview?.let { preview ->
        SyncPreviewDialog(
            preview = preview,
            onConfirm = {
                pendingSyncPreview = null
                scope.launch {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        syncWriter.write(preview)
                    }
                    if (result is io.github.willywonka644.fintracker.sync.SyncResult.Success) {
                        unsyncedCount = 0
                        val syncedAt = System.currentTimeMillis()
                        serverSyncStore.markSynced(syncedAt)
                        lastSyncedAt = syncedAt
                        reloadData()
                        Toast.makeText(
                            context,
                            context.getString(R.string.sync_import_success, result.newCount, result.updatedCount),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.sync_import_error_write_failed), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { pendingSyncPreview = null }
        )
    }

    if (showRestoreConfirm && pendingRestoreBackup != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val backup = pendingRestoreBackup
                        if (backup != null) {
                            when (RestoreService.restoreWithDb(db, backup)) {
                                RestoreResult.Success -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.restore_success),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    reloadData()
                                }
                                is RestoreResult.Error -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.restore_failed),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                        pendingRestoreBackup = null
                        showRestoreConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

}

@Composable
fun SyncPreviewDialog(
    preview: SyncPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_import_preview_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.sync_import_new))
                    Text(preview.newCount.toString(), fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.sync_import_updated))
                    Text(preview.updatedCount.toString(), fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.sync_import_unchanged))
                    Text(preview.unchangedCount.toString(), fontWeight = FontWeight.SemiBold)
                }
                if (preview.skippedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.sync_import_skipped))
                        Text(preview.skippedCount.toString(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (preview.warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    preview.warnings.take(5).forEach { warning ->
                        Text(
                            text = "⚠ $warning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (preview.warnings.size > 5) {
                        Text(
                            text = stringResource(R.string.sync_import_more_warnings, preview.warnings.size - 5),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.sync_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun AddEditAccountDialog(
    initialAccount: Account?,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    onDelete: (Account) -> Unit,
    /** What deleting this account would take with it (#113). Unused while creating one. */
    deletionImpact: AccountDeletionImpact = AccountDeletionImpact(0, 0, 0, 0),
) {
    var name by remember { mutableStateOf(initialAccount?.name ?: "") }
    var type by remember { mutableStateOf(initialAccount?.type ?: AccountType.GIRO) }

    var billingStartDayInput by remember { mutableStateOf(initialAccount?.billingStartDay?.toString() ?: "1") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var billingDayError by remember { mutableStateOf<String?>(null) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun validateAndSave() {
        var hasError = false

        if (name.isBlank()) {
            nameError = "Name darf nicht leer sein"
            hasError = true
        } else nameError = null

        var billingStartDay: Int? = null

        when (type) {
            AccountType.GIRO -> {
                val parsed = billingStartDayInput.trim().toIntOrNull()
                if (parsed == null || parsed !in 1..28) {
                    billingDayError = "Bitte einen Tag zwischen 1 und 28 eingeben"
                    hasError = true
                } else {
                    billingDayError = null
                    billingStartDay = parsed
                }
            }

            AccountType.CREDIT_CARD -> {
                // Editable per card: issuers bill on different days of the month.
                // Never force a fixed day — that used to silently reset existing cards to 18.
                val parsed = billingStartDayInput.trim().toIntOrNull()
                if (parsed == null || parsed !in 1..28) {
                    billingDayError = "Bitte einen Tag zwischen 1 und 28 eingeben"
                    hasError = true
                } else {
                    billingDayError = null
                    billingStartDay = parsed
                }
            }

            AccountType.SPARKONTO, AccountType.TAGESGELD -> {
                billingDayError = null
                billingStartDay = null
            }
        }

        if (!hasError) {
            val result = Account(
                id = initialAccount?.id ?: "",
                name = name.trim(),
                type = type,
                billingStartDay = billingStartDay,
                spendingLimit = initialAccount?.spendingLimit,
                lastModifiedAt = Clock.System.now().toEpochMilliseconds()
            )
            onSave(result)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialAccount == null) "Konto hinzufügen" else "Konto bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    isError = nameError != null,
                    singleLine = true
                )
                if (nameError != null) {
                    Text(
                        text = nameError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("Kontotyp")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val allTypes = listOf(
                        AccountType.GIRO,
                        AccountType.CREDIT_CARD,
                        AccountType.SPARKONTO,
                        AccountType.TAGESGELD
                    )

                    allTypes.forEach { candidate ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = type == candidate,
                                onClick = {
                                    type = candidate
                                    if (candidate == AccountType.GIRO && billingStartDayInput.isBlank()) {
                                        billingStartDayInput = "1"
                                    }
                                    if (candidate == AccountType.CREDIT_CARD && billingStartDayInput.isBlank()) {
                                        billingStartDayInput = "18"
                                    }
                                }
                            )
                            Text(accountTypeLabel(candidate))
                        }
                    }
                }

                when (type) {
                    AccountType.GIRO -> {
                        OutlinedTextField(
                            value = billingStartDayInput,
                            onValueChange = { billingStartDayInput = it },
                            label = { Text("Abrechnungstag (1–28)") },
                            isError = billingDayError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        if (billingDayError != null) {
                            Text(
                                text = billingDayError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    AccountType.CREDIT_CARD -> {
                        Column {
                            OutlinedTextField(
                                value = billingStartDayInput,
                                onValueChange = { billingStartDayInput = it },
                                label = { Text("Abrechnungstag (1–28)") },
                                isError = billingDayError != null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            if (billingDayError != null) {
                                Text(
                                    text = billingDayError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            val previewDay = billingStartDayInput.trim().toIntOrNull()
                            if (previewDay != null && previewDay in 1..28) {
                                val hint = if (previewDay == 1) "Abrechnungszeitraum: 1. bis Monatsende"
                                else "Abrechnungszeitraum: $previewDay. bis ${previewDay - 1}. des Folgemonats"
                                Text(
                                    text = hint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AccountType.SPARKONTO, AccountType.TAGESGELD -> {
                        Text(
                            "Für dieses Konto wird kein Abrechnungszeitraum verwendet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (initialAccount != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Konto löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { validateAndSave() }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )

    if (showDeleteConfirm && initialAccount != null) {
        // Remembered inside the branch on purpose: closing the dialog forgets the tick,
        // so a second attempt has to be confirmed again.
        var deletionUnderstood by remember { mutableStateOf(false) }
        // An empty account has nothing to lose, so it gets the plain sentence and no
        // checkbox — a hurdle in front of nothing only teaches people to click past it.
        val needsConfirmation = !deletionImpact.isEmpty
        val canDelete = !needsConfirmation || deletionUnderstood

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Konto löschen?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(accountDeletionMessage(initialAccount.name, deletionImpact))
                    if (needsConfirmation) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                deletionUnderstood = !deletionUnderstood
                            },
                        ) {
                            Checkbox(
                                checked = deletionUnderstood,
                                onCheckedChange = { deletionUnderstood = it },
                            )
                            Text(
                                text = accountDeletionConfirmLabel(deletionImpact),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canDelete,
                    onClick = {
                        onDelete(initialAccount)
                        showDeleteConfirm = false
                    }
                ) {
                    // The error colour has to fade with the disabled state, otherwise the
                    // button keeps looking pressable while it is not.
                    Text(
                        text = "Löschen",
                        color = if (canDelete) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AccountOverviewPreview() {
    val previewAccounts = listOf(
        Account(
            id = "1",
            name = "Giro Hauptkonto",
            type = AccountType.GIRO,
            billingStartDay = 1
        ),
        Account(
            id = "2",
            name = "Kreditkarte",
            type = AccountType.CREDIT_CARD,
            billingStartDay = 18,
            spendingLimit = 1500.0
        )
    )

    val previewBookings = emptyList<Booking>()

    FinTrackerTheme {
        AccountOverviewScreen(
            accounts = previewAccounts,
            bookings = previewBookings,
            onAddAccountClick = {},
            onAccountClick = {},
        )
    }
}
