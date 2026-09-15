package io.github.willywonka644.fintracker.ui.auswertungen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.patrykandpatryk.vico.compose.axis.axisLabelComponent
import com.patrykandpatryk.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatryk.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatryk.vico.compose.chart.Chart
import com.patrykandpatryk.vico.compose.chart.column.columnChart
import com.patrykandpatryk.vico.compose.chart.line.lineChart
import com.patrykandpatryk.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatryk.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatryk.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatryk.vico.compose.style.ProvideChartStyle
import com.patrykandpatryk.vico.core.axis.AxisItemPlacer
import com.patrykandpatryk.vico.core.axis.AxisPosition
import com.patrykandpatryk.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatryk.vico.core.chart.column.ColumnChart
import com.patrykandpatryk.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatryk.vico.core.chart.line.LineChart
import com.patrykandpatryk.vico.core.component.marker.MarkerComponent
import com.patrykandpatryk.vico.core.component.shape.LineComponent
import com.patrykandpatryk.vico.core.component.shape.ShapeComponent
import com.patrykandpatryk.vico.core.component.shape.Shapes
import com.patrykandpatryk.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatryk.vico.core.component.text.TextComponent
import com.patrykandpatryk.vico.core.dimensions.MutableDimensions
import com.patrykandpatryk.vico.core.entry.FloatEntry
import com.patrykandpatryk.vico.core.entry.entryModelOf
import com.patrykandpatryk.vico.core.marker.Marker
import com.patrykandpatryk.vico.core.marker.MarkerLabelFormatter
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.analytics.BalanceDataPoint
import io.github.willywonka644.fintracker.analytics.BreakdownSearch
import io.github.willywonka644.fintracker.analytics.CategoryBreakdownEntry
import io.github.willywonka644.fintracker.analytics.MonthlyIncomeExpense
import io.github.willywonka644.fintracker.analytics.SawthoothBalanceResult
import io.github.willywonka644.fintracker.analytics.calculateBalanceOverTime
import io.github.willywonka644.fintracker.analytics.CategoryBreakdownSide
import io.github.willywonka644.fintracker.analytics.calculateCategoryBreakdown
import io.github.willywonka644.fintracker.analytics.excludeTransfers
import io.github.willywonka644.fintracker.analytics.calculateMonthlyIncomeExpense
import io.github.willywonka644.fintracker.analytics.calculateSawthoothBalance
import io.github.willywonka644.fintracker.analytics.currentMonthFilter
import io.github.willywonka644.fintracker.analytics.customRangeFilter
import io.github.willywonka644.fintracker.analytics.overviewBalances
import io.github.willywonka644.fintracker.analytics.PeriodChoice
import io.github.willywonka644.fintracker.analytics.availableAbsolutePeriods
import io.github.willywonka644.fintracker.analytics.toFilter
import io.github.willywonka644.fintracker.analytics.periodTotals
import io.github.willywonka644.fintracker.analytics.generateBillingCyclePeriods
import io.github.willywonka644.fintracker.analytics.SelectablePeriod
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.components.FilterChipRow
import io.github.willywonka644.fintracker.ui.components.SectionHeader
import io.github.willywonka644.fintracker.ui.theme.FinColors
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.pointerInput

private enum class CcMultiPeriod(val label: String, val days: Int) {
    DAYS_30("30 Tage", 30),
    MONTHS_3("3 Monate", 90),
    MONTHS_6("6 Monate", 180),
    YEAR("Jahr", 365),
    CUSTOM("Benutzerdefiniert", -1),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuswertungenTabScreen(
    bookings: List<Booking>,
    categories: List<Category>,
    accounts: List<Account>,
    /** Fuehrt in die Buchungsansicht (#129) — die Listen hier waren bisher tote Zeilen. */
    onBookingClick: (Booking) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(tz) }

    // ── Account selection ─────────────────────────────────────────────────────
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }
    val selectedAccount = remember(selectedAccountId, accounts) {
        accounts.firstOrNull { it.id == selectedAccountId }
    }
    val isCcAccount = selectedAccount?.type == AccountType.CREDIT_CARD

    // ── Non-CC period selection ───────────────────────────────────────────────
    // null heißt „kein Filter" und ist der Ausgangszustand: erst alles, dann einschränken.
    // Ein zweiter Klick auf denselben Chip führt dorthin zurück.
    var selectedPeriod by remember { mutableStateOf<PeriodChoice?>(null) }
    var customFromDate by remember { mutableStateOf<LocalDate?>(null) }
    var customToDate by remember { mutableStateOf<LocalDate?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    // ── CC billing period selection ───────────────────────────────────────────
    val ccBillingPeriods = remember(selectedAccount) {
        if (isCcAccount) generateBillingCyclePeriods(selectedAccount!!.billingStartDay ?: 18)
        else emptyList()
    }
    var ccBillingPeriodIdx by remember(selectedAccountId) {
        mutableIntStateOf(ccBillingPeriods.lastIndex.coerceAtLeast(0))
    }
    // null = billing-period mode; non-null = multi-period sawtooth mode
    var ccMultiPeriod by remember(selectedAccountId) { mutableStateOf<CcMultiPeriod?>(null) }
    var ccCustomFromDate by remember(selectedAccountId) { mutableStateOf<LocalDate?>(null) }
    var ccCustomToDate by remember(selectedAccountId) { mutableStateOf<LocalDate?>(null) }
    var ccShowFromPicker by remember { mutableStateOf(false) }
    var ccShowToPicker by remember { mutableStateOf(false) }

    // ── Base bookings (posted, not deleted) ────────────────────────────────────
    val allPosted = remember(bookings.toList()) {
        bookings.filter { !it.deleted && it.status == BookingStatus.POSTED }
    }
    val accountFiltered = remember(allPosted, selectedAccountId) {
        if (selectedAccountId == null) allPosted
        else allPosted.filter { it.accountId == selectedAccountId }
    }

    // ── Kontostand / Gesamtvermögen ───────────────────────────────────────────
    // Aus derselben Quelle wie die Übersicht (#101), damit dieselbe Frage auf beiden
    // Bildschirmen dieselbe Antwort bekommt: ein Einzelkonto in voller Höhe, eine Karte
    // über ihren laufenden Zyklus, "Alle Konten" als Gesamtvermögen mit vollem
    // Kartenbestand. Unabhängig vom Zeitfilter — anders als die drei Zahlen darunter.
    val balances = remember(accounts.toList(), bookings.toList()) {
        overviewBalances(accounts, bookings)
    }
    val currentBalance = if (selectedAccountId == null) {
        balances.total
    } else {
        balances.perAccount[selectedAccountId] ?: 0.0
    }

    // ── Effective period filter ────────────────────────────────────────────────
    // Eine Regel für alle vier Bildschirme, aus shared (#114). Vorher rechnete jeder
    // seine Zeiträume selbst, mit eigenem Enum und abweichenden Spannen.
    val nonCcPeriodFilter = remember(selectedPeriod, customFromDate, customToDate, today, tz) {
        val chosen = selectedPeriod
        if (chosen is PeriodChoice.Custom || (chosen == null && customFromDate != null && customToDate != null)) {
            if (customFromDate != null && customToDate != null) {
                customRangeFilter(customFromDate!!, customToDate!!, tz)
            } else {
                chosen.toFilter(tz, today)
            }
        } else {
            chosen.toFilter(tz, today)
        }
    }

    // ── Period-filtered bookings ───────────────────────────────────────────────
    val ccSinglePeriod = ccBillingPeriods.getOrNull(ccBillingPeriodIdx)
    val periodFiltered = remember(
        accountFiltered, isCcAccount, ccBillingPeriodIdx, ccMultiPeriod,
        nonCcPeriodFilter, ccBillingPeriods, selectedPeriod, customFromDate, customToDate, today,
        ccCustomFromDate, ccCustomToDate
    ) {
        when {
            isCcAccount && ccMultiPeriod == CcMultiPeriod.CUSTOM -> {
                if (ccCustomFromDate == null || ccCustomToDate == null) accountFiltered
                else {
                    // Swapped input (Von after Bis) is normalized instead of yielding an empty chart.
                    val start = minOf(ccCustomFromDate!!, ccCustomToDate!!)
                    val end = maxOf(ccCustomFromDate!!, ccCustomToDate!!)
                    val from = start.atStartOfDayIn(tz).toEpochMilliseconds()
                    val to = end.atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L
                    accountFiltered.filter { b -> val t = b.effectiveDate ?: b.timestamp; t >= from && t < to }
                }
            }
            isCcAccount && ccMultiPeriod != null -> {
                val p = ccMultiPeriod!!
                val from = today.minus(p.days - 1, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds()
                val to = today.atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L
                accountFiltered.filter { b -> val t = b.effectiveDate ?: b.timestamp; t >= from && t < to }
            }
            isCcAccount && ccSinglePeriod != null -> {
                val from = ccSinglePeriod.filter.fromInclusive ?: return@remember accountFiltered
                val to = ccSinglePeriod.filter.toExclusive ?: return@remember accountFiltered
                accountFiltered.filter { b -> val t = b.effectiveDate ?: b.timestamp; t >= from && t < to }
            }
            else -> {
                val from = nonCcPeriodFilter.fromInclusive
                val to = nonCcPeriodFilter.toExclusive
                accountFiltered.filter { b ->
                    val t = b.effectiveDate ?: b.timestamp
                    (from == null || t >= from) && (to == null || t < to)
                }
            }
        }
    }

    // ── KPI bookings: exclude RECONCILIATION ──────────────────────────────────
    val kpiBookings = remember(periodFiltered) {
        periodFiltered.filter { it.source != BookingSource.RECONCILIATION }
    }
    // Shared with the desktop (#100) so the rule cannot exist twice and drift apart again —
    // which is exactly what had happened: Android was right, the desktop was not.
    val totals = remember(periodFiltered, selectedAccountId, isCcAccount) {
        periodTotals(periodFiltered, excludeTransfers = selectedAccountId == null || isCcAccount)
    }
    val totalIncome = totals.income
    val totalExpense = totals.expense
    val periodResult = totals.result

    // ── Category breakdown ────────────────────────────────────────────────────
    // Beide Seiten der Buecher (#127): dieselbe Aufschluesselung beantwortet "wofuer ist
    // es weggegangen" und "woher ist es gekommen". Die Seite ist eine Wahl, kein zweites
    // Diagramm — die Rechnung dahinter liegt in shared und ist dieselbe.
    var categorySide by remember { mutableStateOf(CategoryBreakdownSide.EXPENSES) }
    val categoryIsIncome = categorySide == CategoryBreakdownSide.INCOME
    val breakdown = remember(kpiBookings, selectedAccountId, categorySide) {
        calculateCategoryBreakdown(
            kpiBookings,
            filterTransfers = (selectedAccountId == null),
            side = categorySide,
        )
    }
    val categoryLookup = remember(categories) { categories.associateBy { it.name } }

    // ── Monthly bar chart data (all posted, for trend view) ───────────────────
    // Kontoabgleich-Korrekturen sind keine echten Einnahmen/Ausgaben — sie korrigieren
    // nur den Saldo und bleiben deshalb aus dem Balkendiagramm draußen.
    // Abrechnungs-Regel (einheitlich mit den KPI-Kacheln): Abrechnungen zählen nur in
    // der Einzelkonto-Sicht eines Nicht-CC-Kontos (Giro: echtes Geld raus). Auf der
    // Kreditkarte ist die Ausgleichs-Gutschrift eine Tilgung, keine Einnahme; bei
    // "Alle Konten" würde dieselbe Zahlung doppelt zählen.
    // Folgt dem Zeitraum, der oben gewaehlt ist (Nutzerentscheidung, 13.09.2026) — wie am Desktop
    // seit jeher. Vorher nahm das Diagramm die letzten sechs Monate des Kontos, ganz
    // gleich was der Filter sagte: die Kacheln darueber und die Balken darunter
    // sprachen ueber verschiedene Zeitraeume, und die Liste zum Balken erst recht.
    val monthlyData = remember(kpiBookings, selectedAccountId, isCcAccount) {
        calculateMonthlyIncomeExpense(
            kpiBookings,
            filterTransfers = (selectedAccountId == null || isCcAccount),
        )
    }

    // ── Liste unter dem Balkendiagramm (#128) ─────────────────────────────────
    // Am Desktop oeffnet ein Klick auf "Einnahmen" / "Ausgaben" seit #114 eine Liste
    // unter dem Diagramm; auf dem Telefon fehlte sie. Gezeigt wird genau das, was auch
    // die Balken zeigen: derselbe Kontofilter, dieselbe Abrechnungsregel, dieselben
    // Monate. Sonst stuende unter dem Diagramm eine andere Wahrheit als darin.
    var barDrilldown by remember { mutableStateOf<BarDrilldown?>(null) }
    val barBookings = remember(kpiBookings, selectedAccountId, isCcAccount) {
        val base = if (selectedAccountId == null || isCcAccount) kpiBookings.excludeTransfers() else kpiBookings
        base.sortedByDescending { it.effectiveDate ?: it.timestamp }
    }

    // ── Balance over time (sawtooth for CC multi-period, normal otherwise) ────
    val ccBillingStartDay = selectedAccount?.billingStartDay ?: 18
    val sawthoothResult = remember(
        accountFiltered, isCcAccount, ccMultiPeriod, ccBillingStartDay, today, tz,
        ccCustomFromDate, ccCustomToDate
    ) {
        if (!isCcAccount || ccMultiPeriod == null) return@remember SawthoothBalanceResult(emptyList(), emptyList())
        val p = ccMultiPeriod!!
        val fromDate = when (p) {
            CcMultiPeriod.CUSTOM -> ccCustomFromDate ?: return@remember SawthoothBalanceResult(emptyList(), emptyList())
            else -> today.minus(p.days - 1, DateTimeUnit.DAY)
        }
        val toDate = when (p) {
            CcMultiPeriod.CUSTOM -> ccCustomToDate ?: return@remember SawthoothBalanceResult(emptyList(), emptyList())
            else -> today
        }
        // Normalize swapped custom input (Von after Bis) instead of returning an empty chart.
        calculateSawthoothBalance(
            accountFiltered, ccBillingStartDay,
            minOf(fromDate, toDate), maxOf(fromDate, toDate), tz,
        )
    }

    val balanceDataPoints = remember(
        accountFiltered, periodFiltered, isCcAccount, ccMultiPeriod,
        nonCcPeriodFilter, ccSinglePeriod, sawthoothResult
    ) {
        if (isCcAccount && ccMultiPeriod != null) return@remember sawthoothResult.points
        val from = when {
            isCcAccount && ccSinglePeriod != null -> ccSinglePeriod.filter.fromInclusive
            // For ALL_TIME (fromInclusive == null) use the earliest booking date
            else -> nonCcPeriodFilter.fromInclusive
                ?: accountFiltered.minOfOrNull { it.effectiveDate ?: it.timestamp }
        } ?: return@remember emptyList<BalanceDataPoint>()
        val to = when {
            isCcAccount && ccSinglePeriod != null -> ccSinglePeriod.filter.toExclusive
            else -> nonCcPeriodFilter.toExclusive
        }
        val tomorrowMs = today.atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L
        // CC single billing cycle: each cycle starts at 0 (same methodology as the
        // sawtooth chart, the Kontostand tile and the Übersicht card). Other accounts
        // carry their real cumulative balance into the period.
        val startingBalance = if (isCcAccount) 0.0 else accountFiltered.filter {
            (it.effectiveDate ?: it.timestamp) < from
        }.sumOf { it.amount }
        val inPeriod = accountFiltered.filter { b ->
            val t = b.effectiveDate ?: b.timestamp
            t >= from && (to == null || t < to)
        }
        if (inPeriod.isEmpty()) emptyList()
        else calculateBalanceOverTime(
            bookingsInPeriod = inPeriod,
            startingBalance = startingBalance,
            periodFromMillis = from,
            periodToMillis = minOf(to ?: tomorrowMs, tomorrowMs),
            timeZone = tz,
        )
    }

    // ── Drilldown state ───────────────────────────────────────────────────────
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    // Suche in der Kategorie-Aufschluesselung (#134). Die Liste ist nach Betrag sortiert
    // — richtig fuer "wofuer ist es weggegangen", unbrauchbar fuer "was war Versicherung".
    var categoryQuery by remember { mutableStateOf("") }
    // Wonach gesucht wird, ist eine sichtbare Wahl und kein stiller Schalter (#137):
    // die Bezeichnungssuche rechnet die Karte neu, die Kategoriesuche nicht.
    var categorySearchMode by remember { mutableStateOf(BreakdownSearch.Mode.CATEGORY) }

    // ── Date picker dialogs ───────────────────────────────────────────────────
    if (showFromPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = customFromDate?.atStartOfDayIn(tz)?.toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        customFromDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                    }
                    // Normalize the visible state too — the buttons must show what is computed.
                    val f = customFromDate; val t = customToDate
                    if (f != null && t != null && f > t) { customFromDate = t; customToDate = f }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = pickerState) }
    }
    if (showToPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = customToDate?.atStartOfDayIn(tz)?.toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        customToDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                    }
                    val f = customFromDate; val t = customToDate
                    if (f != null && t != null && f > t) { customFromDate = t; customToDate = f }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = pickerState) }
    }
    if (ccShowFromPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ccCustomFromDate?.atStartOfDayIn(tz)?.toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { ccShowFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        ccCustomFromDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                    }
                    val f = ccCustomFromDate; val t = ccCustomToDate
                    if (f != null && t != null && f > t) { ccCustomFromDate = t; ccCustomToDate = f }
                    ccShowFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { ccShowFromPicker = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = pickerState) }
    }
    if (ccShowToPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ccCustomToDate?.atStartOfDayIn(tz)?.toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { ccShowToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        ccCustomToDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
                    }
                    val f = ccCustomFromDate; val t = ccCustomToDate
                    if (f != null && t != null && f > t) { ccCustomFromDate = t; ccCustomToDate = f }
                    ccShowToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { ccShowToPicker = false }) { Text("Abbrechen") } },
        ) { DatePicker(state = pickerState) }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
    ) {
        // Header + account dropdown
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Auswertungen", style = MaterialTheme.typography.headlineLarge)
                if (accounts.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { showAccountMenu = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = selectedAccount?.name ?: "Alle Konten",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Alle Konten") },
                                onClick = { selectedAccountId = null; showAccountMenu = false },
                            )
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = { selectedAccountId = acc.id; showAccountMenu = false },
                                )
                            }
                        }
                    }
                }
            }
        }

        // Period filters (non-CC or no account selected)
        if (!isCcAccount) {
            item(key = "periods") {
                // Relative Spannen als Chips, Quartale und Jahre im Menü daneben (#114) —
                // dieselbe Aufteilung wie am Desktop. Die Chipreihe wächst damit nicht mit
                // jedem Jahr, das dazukommt, was auf einem Telefon zählt.
                val relativeLabels = PeriodChoice.Relative.entries.map { it.label } + "Benutzerdefiniert"
                val absolutePeriods = remember(bookings.toList()) { availableAbsolutePeriods(bookings, tz) }
                val selectedIdx = when (val p = selectedPeriod) {
                    is PeriodChoice.Relative -> PeriodChoice.Relative.entries.indexOf(p)
                    is PeriodChoice.Custom -> relativeLabels.lastIndex
                    else -> -1
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChipRow(
                        items = relativeLabels,
                        // -1 markiert keinen Chip — der Zustand „kein Filter".
                        selectedIndex = selectedIdx,
                        onSelect = { idx ->
                            selectedPeriod = when {
                                idx == relativeLabels.lastIndex ->
                                    PeriodChoice.Custom(customFromDate ?: today, customToDate ?: today)
                                idx == selectedIdx -> null
                                else -> PeriodChoice.Relative.entries[idx]
                            }
                        },
                    )
                    if (absolutePeriods.isNotEmpty()) {
                        var menuOpen by remember { mutableStateOf(false) }
                        val chosen = selectedPeriod
                            ?.takeIf { it is PeriodChoice.Quarter || it is PeriodChoice.Year }
                        Box {
                            OutlinedButton(
                                onClick = { menuOpen = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(
                                    1.dp,
                                    if (chosen != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                ),
                            ) {
                                Text(
                                    chosen?.label ?: "Quartal / Jahr",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (chosen != null) MaterialTheme.colorScheme.primary
                                    else finColors.textSub,
                                )
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (chosen != null) {
                                    DropdownMenuItem(
                                        text = { Text("Auswahl aufheben") },
                                        onClick = { selectedPeriod = null; menuOpen = false },
                                    )
                                }
                                absolutePeriods.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.label) },
                                        onClick = { selectedPeriod = p; menuOpen = false },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (selectedPeriod is PeriodChoice.Custom) {
                item(key = "customRange") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showFromPicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = customFromDate?.let { d ->
                                    "${d.dayOfMonth.toString().padStart(2, '0')}.${d.monthNumber.toString().padStart(2, '0')}.${d.year}"
                                } ?: "Von",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        OutlinedButton(
                            onClick = { showToPicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = customToDate?.let { d ->
                                    "${d.dayOfMonth.toString().padStart(2, '0')}.${d.monthNumber.toString().padStart(2, '0')}.${d.year}"
                                } ?: "Bis",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }

        // CC: multi-period chips + billing period dropdown / custom date range
        if (isCcAccount) {
            item(key = "cc_multiperiod") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CcMultiPeriod.entries.forEach { p ->
                        androidx.compose.material3.FilterChip(
                            selected = ccMultiPeriod == p,
                            onClick = { ccMultiPeriod = p },
                            label = { Text(p.label, style = MaterialTheme.typography.labelMedium) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                    androidx.compose.material3.FilterChip(
                        selected = ccMultiPeriod == null,
                        onClick = { ccMultiPeriod = null },
                        label = { Text("Einzelabrechnung", style = MaterialTheme.typography.labelMedium) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    )
                }
            }
            if (ccMultiPeriod == CcMultiPeriod.CUSTOM) {
                item(key = "cc_customRange") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { ccShowFromPicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = ccCustomFromDate?.let { d ->
                                    "${d.dayOfMonth.toString().padStart(2, '0')}.${d.monthNumber.toString().padStart(2, '0')}.${d.year}"
                                } ?: "Von",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        OutlinedButton(
                            onClick = { ccShowToPicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = ccCustomToDate?.let { d ->
                                    "${d.dayOfMonth.toString().padStart(2, '0')}.${d.monthNumber.toString().padStart(2, '0')}.${d.year}"
                                } ?: "Bis",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            } else if (ccMultiPeriod == null && ccBillingPeriods.isNotEmpty()) {
                item(key = "cc_billing_dropdown") {
                    CcBillingPeriodDropdown(
                        periods = ccBillingPeriods,
                        selectedIdx = ccBillingPeriodIdx,
                        onSelect = { ccBillingPeriodIdx = it },
                    )
                }
            }
        }

        // Kennzahlen als ein Block (#116, #117). Vorher standen Kontostand und
        // Einnahmen/Ausgaben in getrennten Fenstern, das Periodenergebnis als kleinste
        // Textzeile darunter — und bei "Alle Konten" fehlten Kontostand und Ergebnis
        // ganz, obwohl es für beide eine Zahl gibt.
        item(key = "kennzahlen") {
            KennzahlenBlock(
                headline = when {
                    selectedAccountId == null -> "Aktuelles Gesamtvermögen"
                    isCcAccount -> "Aktueller Kontostand · laufende Abrechnung"
                    else -> "Aktueller Kontostand"
                },
                headlineAmount = currentBalance,
                income = totalIncome,
                expense = totalExpense,
                result = periodResult,
                finColors = finColors,
            )
        }

        // Saldoverlauf
        item(key = "balance_card") {
            ChartCard {
                SectionHeader(
                    // Über alle Konten ist es kein Kontostand, sondern das Vermögen (#114) —
                    // dieselbe Unterscheidung wie bei der Kennzahlen-Überschrift darüber.
                    title = if (selectedAccountId == null) "Vermögensverlauf" else "Kontostandsverlauf",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                if (balanceDataPoints.size >= 2) {
                    BalanceLineChart(
                        dataPoints = balanceDataPoints,
                        finColors = finColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(bottom = 12.dp),
                    )
                } else {
                    EmptyChartMessage(
                        "Nicht genügend Daten für den Verlauf.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // Einnahmen vs. Ausgaben
        item(key = "bar_card") {
            ChartCard {
                SectionHeader(
                    title = "Einnahmen vs. Ausgaben",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                // Auch ein einzelner Monat wird gezeigt: bei "Dieser Monat" ist ein Paar
                // Balken die richtige Antwort, nicht "noch keine Daten".
                if (monthlyData.isNotEmpty()) {
                    IncomeExpenseChart(
                        data = monthlyData,
                        finColors = finColors,
                        selectedIsIncome = barDrilldown?.isIncome,
                        onSelectSide = { isIncome ->
                            barDrilldown = if (barDrilldown?.isIncome == isIncome) {
                                null
                            } else {
                                // Auf dem gewaehlten Zeitraum, nicht auf einem Monat daraus:
                                // welcher Zeitraum gilt, steht oben und ist die Wahl des
                                // Nutzers. Die Monatschips schraenken darin weiter ein.
                                BarDrilldown(isIncome = isIncome, monthIndex = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                } else {
                    EmptyChartMessage(
                        "Keine Buchungen im gewählten Zeitraum.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // Liste unter dem Balkendiagramm — eine Karte, die sich aktualisiert
        barDrilldown?.let { sel ->
            item(key = "bar_drilldown") {
                val point = sel.monthIndex?.let { monthlyData.getOrNull(it) }
                val shown = barBookings
                    .filter { if (sel.isIncome) it.amount > 0 else it.amount < 0 }
                    .filter { point == null || point.covers(it) }
                val what = if (sel.isIncome) "Einnahmen" else "Ausgaben"
                BarDrilldownCard(
                    title = "$what · ${point?.monthLabel ?: "im Zeitraum"}",
                    color = if (sel.isIncome) finColors.income else finColors.expense,
                    bookings = shown,
                    monthLabels = monthlyData.map { it.monthLabel },
                    selectedMonthIndex = sel.monthIndex,
                    onSelectMonth = { idx -> barDrilldown = sel.copy(monthIndex = idx) },
                    onClose = { barDrilldown = null },
                    onBookingClick = onBookingClick,
                    finColors = finColors,
                    emptyMessage = "Keine $what ${point?.let { "in " + it.monthLabel } ?: "im gewählten Zeitraum"}",
                )
            }
        }

        // Ausgaben bzw. Einnahmen nach Kategorie, mit Drilldown
        item(key = "category_card") {
            ChartCard {
                SectionHeader(
                    title = if (categoryIsIncome) "Einnahmen nach Kategorie" else "Ausgaben nach Kategorie",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SideChip(
                        label = "Ausgaben",
                        color = finColors.expense,
                        selected = !categoryIsIncome,
                        idleColor = finColors.textSub,
                        onClick = {
                            // Die aufgeklappten Kategorien gehoeren zur alten Seite; offen
                            // gelassen zeigten sie eine Liste, die es dort nicht mehr gibt.
                            categorySide = CategoryBreakdownSide.EXPENSES
                            expandedCategories = emptySet()
                        },
                    )
                    SideChip(
                        label = "Einnahmen",
                        color = finColors.income,
                        selected = categoryIsIncome,
                        idleColor = finColors.textSub,
                        onClick = {
                            categorySide = CategoryBreakdownSide.INCOME
                            expandedCategories = emptySet()
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (breakdown.isNotEmpty()) {
                    val search = remember(
                        kpiBookings, breakdown, categoryQuery, categorySearchMode,
                        categorySide, selectedAccountId,
                    ) {
                        BreakdownSearch.apply(
                            periodBookings = kpiBookings,
                            breakdown = breakdown,
                            query = categoryQuery,
                            mode = categorySearchMode,
                            side = categorySide,
                            filterTransfers = (selectedAccountId == null),
                        )
                    }
                    CategorySection(
                        entries = search.entries,
                        // Die Suche steht unter dem Donut, direkt ueber der Liste, die
                        // sie filtert — wie am Desktop. Vorher stand sie oben und ihre
                        // Chips klebten unter denen fuer Ausgaben/Einnahmen: zwei
                        // gleich aussehende Reihen, die Verschiedenes bedeuten.
                        searchRow = {
                            CategorySearchField(
                                query = categoryQuery,
                                onQueryChange = { categoryQuery = it },
                                mode = categorySearchMode,
                                onModeChange = { categorySearchMode = it; expandedCategories = emptySet() },
                                finColors = finColors,
                            )
                        },
                        // Im Bezeichnungs-Modus misst der Balken gegen die groesste
                        // *gefundene* Kategorie — die Karte handelt dann ja von den
                        // Treffern. Im Kategorie-Modus weiter gegen den ganzen Zeitraum,
                        // sonst saehe die kleinste gefundene aus wie die teuerste.
                        barScaleMax = if (search.recomputed) {
                            search.entries.maxOfOrNull { abs(it.amount) } ?: 1.0
                        } else {
                            breakdown.maxOfOrNull { abs(it.amount) } ?: 1.0
                        },
                        caption = breakdownCaption(
                            search = search,
                            query = categoryQuery,
                            totalCategories = breakdown.size,
                        ),
                        categoryLookup = categoryLookup,
                        finColors = finColors,
                        expandedCategories = expandedCategories,
                        onToggleCategory = { name ->
                            expandedCategories = if (name in expandedCategories)
                                expandedCategories - name else expandedCategories + name
                        },
                        periodBookings = search.bookings,
                        isIncome = categoryIsIncome,
                        onBookingClick = onBookingClick,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    )
                } else {
                    EmptyChartMessage(
                        if (categoryIsIncome) "Keine Einnahmen im gewählten Zeitraum."
                        else "Keine Ausgaben im gewählten Zeitraum.",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

// ── Billing period dropdown for CC Einzelabrechnung mode ─────────────────────

@Composable
private fun CcBillingPeriodDropdown(
    periods: List<SelectablePeriod>,
    selectedIdx: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (periods.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                text = periods.getOrNull(selectedIdx)?.label ?: "Zeitraum wählen",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            periods.forEachIndexed { idx, period ->
                DropdownMenuItem(
                    text = { Text(period.label) },
                    onClick = { onSelect(idx); expanded = false },
                )
            }
        }
    }
}

// ── Card shell ────────────────────────────────────────────────────────────────

@Composable
private fun ChartCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun EmptyChartMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = FinTheme.colors.textSub,
        modifier = modifier,
    )
}

// ── Balance line chart ────────────────────────────────────────────────────────

@Composable
private fun BalanceLineChart(
    dataPoints: List<BalanceDataPoint>,
    finColors: FinColors,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textFaintArgb = finColors.textFaint.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.inverseSurface.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.inverseOnSurface.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.toArgb()

    val chartEntryModel = remember(dataPoints) {
        entryModelOf(
            dataPoints.mapIndexed { i, pt -> FloatEntry(x = i.toFloat(), y = pt.balance.toFloat()) }
        )
    }

    val labelStep = remember(dataPoints) { maxOf(1, dataPoints.size / 4) }
    val bottomFormatter = remember(dataPoints) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val i = value.toInt()
            if (i in dataPoints.indices) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = dataPoints[i].dateMillis }
                "${cal.get(java.util.Calendar.DAY_OF_MONTH)}.${cal.get(java.util.Calendar.MONTH) + 1}."
            } else ""
        }
    }
    val bottomLabel = remember(textFaintArgb) {
        TextComponent.Builder().apply { color = textFaintArgb; textSizeSp = 10f }.build()
    }
    val bottomPlacer = remember(labelStep) {
        AxisItemPlacer.Horizontal.default(spacing = labelStep, addExtremeLabelPadding = true)
    }
    val startLabel = remember(textFaintArgb) {
        TextComponent.Builder().apply { color = textFaintArgb; textSizeSp = 10f }.build()
    }
    val startFormatter = remember {
        AxisValueFormatter<AxisPosition.Vertical.Start> { v, _ -> MoneyFormat.compactCurrency(v.toDouble()) }
    }
    val lineSpec = remember(primaryColor) {
        LineChart.LineSpec(
            lineColor = primaryColor.toArgb(),
            lineBackgroundShader = DynamicShaders.fromBrush(
                Brush.verticalGradient(
                    listOf(primaryColor.copy(alpha = 0.32f), primaryColor.copy(alpha = 0f))
                )
            )
        )
    }
    val marker = remember(dataPoints, surfaceColor, onSurfaceColor, outlineColor) {
        buildBalanceMarker(dataPoints, surfaceColor, onSurfaceColor, outlineColor)
    }

    ProvideChartStyle(m3ChartStyle()) {
        Chart(
            chart = lineChart(lines = listOf(lineSpec)),
            model = chartEntryModel,
            startAxis = rememberStartAxis(label = startLabel, valueFormatter = startFormatter),
            bottomAxis = rememberBottomAxis(
                label = bottomLabel,
                valueFormatter = bottomFormatter,
                labelRotationDegrees = 45f,
                itemPlacer = bottomPlacer,
            ),
            marker = marker,
            chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
            isZoomEnabled = false,
            horizontalLayout = HorizontalLayout.FullWidth(
                unscalableStartPaddingDp = 16f,
                unscalableEndPaddingDp = 16f,
            ),
            modifier = modifier,
        )
    }
}

private fun buildBalanceMarker(
    dataPoints: List<BalanceDataPoint>,
    surfaceColor: Int,
    onSurfaceColor: Int,
    outlineColor: Int,
): Marker {
    val fmt = java.text.SimpleDateFormat("dd.MM.", java.util.Locale.GERMANY)
    val label = TextComponent.Builder().apply {
        color = onSurfaceColor
        textSizeSp = 12f
        padding = MutableDimensions(10f, 6f, 10f, 6f)
        background = ShapeComponent(Shapes.roundedCornerShape(allPercent = 25), surfaceColor)
    }.build()
    return MarkerComponent(label = label, indicator = null, guideline = LineComponent(outlineColor, 1f)).also { m ->
        m.labelFormatter = MarkerLabelFormatter { entries, _ ->
            entries.firstOrNull()?.let { e ->
                val pt = dataPoints.getOrNull(e.entry.x.toInt()) ?: return@let ""
                "${fmt.format(java.util.Date(pt.dateMillis))} · ${MoneyFormat.currency(pt.balance)}"
            } ?: ""
        }
    }
}

// ── Income / Expense bar chart ────────────────────────────────────────────────

@Composable
private fun IncomeExpenseChart(
    data: List<MonthlyIncomeExpense>,
    finColors: FinColors,
    /** Welche Seite gerade aufgeklappt ist, oder `null` fuer keine. */
    selectedIsIncome: Boolean?,
    onSelectSide: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val incomeColor = finColors.income
    val expenseColor = finColors.expense
    val surfaceColor = MaterialTheme.colorScheme.inverseSurface.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.inverseOnSurface.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val textFaintArgb = finColors.textFaint.toArgb()

    val chartEntryModel = remember(data) {
        entryModelOf(
            data.mapIndexed { i, d -> FloatEntry(i.toFloat(), d.income.toFloat()) },
            data.mapIndexed { i, d -> FloatEntry(i.toFloat(), d.expense.toFloat()) },
        )
    }
    val bottomFormatter = remember(data) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { v, _ -> data.getOrNull(v.toInt())?.monthLabel ?: "" }
    }
    val axisLabel = remember(textFaintArgb) {
        TextComponent.Builder().apply { color = textFaintArgb; textSizeSp = 10f }.build()
    }
    val startFormatter = remember {
        AxisValueFormatter<AxisPosition.Vertical.Start> { v, _ -> MoneyFormat.compactCurrency(v.toDouble()) }
    }
    val incomeColumn = remember(incomeColor) {
        LineComponent(
            color = incomeColor.toArgb(),
            thicknessDp = 12f,
            shape = Shapes.roundedCornerShape(topLeftPercent = 30, topRightPercent = 30),
        )
    }
    val expenseColumn = remember(expenseColor) {
        LineComponent(
            color = expenseColor.toArgb(),
            thicknessDp = 12f,
            shape = Shapes.roundedCornerShape(topLeftPercent = 30, topRightPercent = 30),
        )
    }
    val marker = remember(data, surfaceColor, onSurfaceColor, outlineColor) {
        val lbl = TextComponent.Builder().apply {
            color = onSurfaceColor; textSizeSp = 11f
            padding = MutableDimensions(10f, 6f, 10f, 6f)
            background = ShapeComponent(Shapes.roundedCornerShape(allPercent = 25), surfaceColor)
        }.build()
        MarkerComponent(label = lbl, indicator = null, guideline = LineComponent(outlineColor, 1f)).also { m ->
            m.labelFormatter = MarkerLabelFormatter { entries, _ ->
                val i = entries.firstOrNull()?.entry?.x?.toInt() ?: return@MarkerLabelFormatter ""
                val pt = data.getOrNull(i) ?: return@MarkerLabelFormatter ""
                "${pt.monthLabel} · +${MoneyFormat.currency(pt.income)} / ${MoneyFormat.currency(pt.expense)}"
            }
        }
    }

    Column(modifier = modifier) {
        // Die Legende ist zugleich die Wahl — wie am Desktop. Eine zweite Reihe Knoepfe
        // haette dasselbe gesagt wie die Punkte, die ohnehin schon dastehen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SideChip(
                label = "Einnahmen",
                color = incomeColor,
                selected = selectedIsIncome == true,
                idleColor = finColors.textSub,
                onClick = { onSelectSide(true) },
            )
            Spacer(Modifier.width(12.dp))
            SideChip(
                label = "Ausgaben",
                color = expenseColor,
                selected = selectedIsIncome == false,
                idleColor = finColors.textSub,
                onClick = { onSelectSide(false) },
            )
        }
        ProvideChartStyle(m3ChartStyle()) {
            Chart(
                chart = columnChart(
                    columns = listOf(incomeColumn, expenseColumn),
                    mergeMode = ColumnChart.MergeMode.Grouped,
                    spacing = 20.dp,
                ),
                model = chartEntryModel,
                startAxis = rememberStartAxis(label = axisLabel, valueFormatter = startFormatter),
                bottomAxis = rememberBottomAxis(label = axisLabel, valueFormatter = bottomFormatter),
                marker = marker,
                // Bei einem langen Zeitraum wird geschoben statt gequetscht — zwanzig
                // Monatspaare auf Telefonbreite waeren Striche, keine Balken.
                chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = data.size > 6),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(200.dp),
            )
        }
    }
}

// ── Category search ───────────────────────────────────────────

/**
 * Sucht eine Kategorie in der Aufschluesselung (#134).
 *
 * Nur ein Filter fuer die Liste. Der Donut und die Prozente bleiben, was sie waren —
 * der Anteil am ganzen Zeitraum. Ein Anteil, der sich an drei Treffern neu berechnet,
 * beantwortet eine Frage, die niemand gestellt hat.
 */
@Composable
private fun CategorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    mode: BreakdownSearch.Mode,
    onModeChange: (BreakdownSearch.Mode) -> Unit,
    finColors: FinColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Suchen in",
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
            )
            SearchModeSegment(mode = mode, onModeChange = onModeChange, finColors = finColors)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                // Folgt dem Schalter: ein Feld, das "Kategorie suchen" sagt, waehrend
                // "Bezeichnung" gewaehlt ist, widerspricht sich selbst.
                Text(
                    text = when (mode) {
                        BreakdownSearch.Mode.CATEGORY -> "Kategorie suchen…"
                        BreakdownSearch.Mode.DESCRIPTION -> "Bezeichnung suchen…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = finColors.textFaint)
            },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Suche löschen", modifier = Modifier.size(18.dp), tint = finColors.textSub)
                    }
                }
            },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Der Schalter zwischen den zwei Suchfragen.
 *
 * Bewusst **kein** [SideChip]: dessen farbiger Punkt heisst "so ist diese Seite im
 * Diagramm eingefaerbt", und fuer einen Suchmodus gibt es keine solche Farbe. Ein Punkt,
 * der nichts bedeutet, waere die vierte Farbbedeutung, die #119 ausschliesst. Akzentblau
 * heisst hier nur "das ist gerade gewaehlt".
 */
@Composable
private fun SearchModeSegment(
    mode: BreakdownSearch.Mode,
    onModeChange: (BreakdownSearch.Mode) -> Unit,
    finColors: FinColors,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), MaterialTheme.shapes.small),
    ) {
        BreakdownSearch.Mode.entries.forEach { entry ->
            val active = entry == mode
            Text(
                text = when (entry) {
                    BreakdownSearch.Mode.CATEGORY -> "Kategorie"
                    BreakdownSearch.Mode.DESCRIPTION -> "Bezeichnung"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else finColors.textSub,
                modifier = Modifier
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .clickable { onModeChange(entry) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Was unter der Karte steht, solange gesucht wird.
 *
 * `null` heisst: nichts sagen. Eine Zeile, die dauerhaft das Offensichtliche wiederholt,
 * kostet Platz und Aufmerksamkeit.
 */
private fun breakdownCaption(
    search: BreakdownSearch.Result,
    query: String,
    totalCategories: Int,
): String? = when {
    query.isBlank() -> null
    search.entries.isEmpty() -> "Nichts passt zu „${query.trim()}“."
    // Neu gerechnet: die Zahl oben ist die Summe der Zeilen darunter, und das muss
    // dastehen — sonst wirkt der Prozentsatz wie ein Rechenfehler (#125).
    search.recomputed ->
        "${search.hits} ${if (search.hits == 1) "Buchung" else "Buchungen"} · " +
            "${MoneyFormat.currencyAbs(search.hitTotal)} · Prozente beziehen sich auf diese Treffer"
    else ->
        "${search.entries.size} von $totalCategories Kategorien · " +
            "Prozente und Donut gelten weiter für den ganzen Zeitraum"
}

// ── Category breakdown with drilldown ────────────────────────────────────────

@Composable
private fun CategorySection(
    /** Die zu zeichnenden Zeilen — auch die Quelle fuer Donut und Legende. */
    entries: List<CategoryBreakdownEntry>,
    /** Die Suchzeile. Sitzt zwischen Donut und Liste, weil sie die Liste filtert. */
    searchRow: @Composable () -> Unit,
    /** Wogegen die Balkenbreite misst. Siehe die Begruendung an der Aufrufstelle. */
    barScaleMax: Double,
    /** Zeile unter der Karte, solange gesucht wird; `null` = nichts sagen. */
    caption: String?,
    categoryLookup: Map<String, Category>,
    finColors: FinColors,
    expandedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    periodBookings: List<Booking>,
    /** Ob die Aufschluesselung die Einnahmenseite zeigt — sonst die Ausgaben. */
    isIncome: Boolean = false,
    onBookingClick: (Booking) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val segmentColors = remember(entries, categoryLookup) {
        entries.map { entry ->
            categoryLookup[entry.categoryName]?.let { Color(it.color) } ?: finColors.textFaint
        }
    }
    val segments = remember(entries, segmentColors) {
        entries.zip(segmentColors).map { (entry, color) -> color to entry.percentage.toFloat() }
    }

    var tappedIdx by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Donut + top-4 legend
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                DonutChart(
                    segments = segments,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                    onSegmentTap = { idx -> tappedIdx = if (idx == tappedIdx) null else idx },
                )
                tappedIdx?.let { i ->
                    val entry = entries.getOrNull(i) ?: return@let
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.widthIn(max = 62.dp),
                    ) {
                        Text(
                            entry.categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${(entry.percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            MoneyFormat.currencyAbs(entry.amount),
                            style = MaterialTheme.typography.labelSmall,
                            color = finColors.textSub,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.take(4).forEachIndexed { i, entry ->
                    val color = segmentColors.getOrElse(i) { finColors.textFaint }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                        Text(
                            text = entry.categoryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = finColors.textSub,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${(entry.percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        searchRow()

        // Full ranked list with progress bars + drilldown
        val maxAmount = if (barScaleMax > 0.0) barScaleMax else 1.0
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            entries.forEach { entry ->
                val color = categoryLookup[entry.categoryName]?.let { Color(it.color) } ?: finColors.textFaint
                val fraction = (abs(entry.amount) / maxAmount).toFloat().coerceIn(0f, 1f)
                val isExpanded = entry.categoryName in expandedCategories
                val categoryBookings = remember(periodBookings, entry.categoryName, isIncome) {
                    periodBookings.filter { b ->
                        (if (isIncome) b.amount > 0 else b.amount < 0) &&
                            (b.category?.trim()?.takeIf { it.isNotBlank() } ?: "Ohne Kategorie") == entry.categoryName
                    }.sortedByDescending { it.effectiveDate ?: it.timestamp }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleCategory(entry.categoryName) }
                        .padding(vertical = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                            Text(
                                text = entry.categoryName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "${(entry.percentage * 100).toInt()}%  ${MoneyFormat.currencyAbs(entry.amount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = finColors.textSub,
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = finColors.textFaint,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color),
                        )
                    }

                    if (isExpanded && categoryBookings.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                categoryBookings.forEach { booking ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onBookingClick(booking) }
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = booking.description.ifBlank { "—" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = finColors.textSub,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = MoneyFormat.currencySigned(booking.amount),
                                            style = MaterialTheme.typography.labelSmall,
                                            // Nach Vorzeichen, nicht nach Seite: auf der
                                            // Einnahmenseite stuende sonst jeder Betrag rot.
                                            color = if (booking.amount < 0) finColors.expense else finColors.income,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
private fun DonutChart(
    segments: List<Pair<Color, Float>>,
    trackColor: Color,
    modifier: Modifier = Modifier,
    onSegmentTap: ((Int?) -> Unit)? = null,
) {
    Canvas(
        modifier = if (onSegmentTap != null) {
            modifier.pointerInput(segments) {
                detectTapGestures { offset ->
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val minDim = minOf(size.width, size.height).toFloat()
                    val sw = minDim * 0.2f
                    val r = (minDim - sw) / 2f
                    val dx = offset.x - cx; val dy = offset.y - cy
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < r - sw / 2 || dist > r + sw / 2) {
                        onSegmentTap(null); return@detectTapGestures
                    }
                    val hitAngle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + 360f) % 360f
                    var startAngle = 0f
                    for (i in segments.indices) {
                        val sweep = 360f * segments[i].second.coerceAtLeast(0.001f)
                        if (hitAngle >= startAngle && hitAngle < startAngle + sweep) {
                            onSegmentTap(i); return@detectTapGestures
                        }
                        startAngle += sweep
                    }
                    onSegmentTap(null)
                }
            }
        } else modifier,
    ) {
        val strokeWidth = size.minDimension * 0.2f
        val radius = (size.minDimension - strokeWidth) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)

        drawCircle(
            color = trackColor,
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth),
        )
        var startAngle = -90f
        segments.forEach { (color, fraction) ->
            val sweep = 360f * fraction.coerceAtLeast(0.001f)
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                topLeft = arcTopLeft,
                size = arcSize,
            )
            startAngle += sweep
        }
    }
}

/**
 * Die drei Kennzahlen in einem Fenster, von oben nach unten (#117).
 *
 * Vorher standen sie in zwei getrennten Fenstern und einer Fußnote: Kontostand oben,
 * Einnahmen und Ausgaben darunter, und das Periodenergebnis als **kleinste Schrift des
 * Bildschirms** — für die Zahl, die die eigentliche Frage beantwortet. Bei „Alle Konten"
 * fehlten Kontostand und Ergebnis sogar ganz (#116), obwohl es für beide eine Zahl gibt.
 *
 * Die Reihenfolge trägt die Aussage: **was gerade da ist**, dann **was hinein- und
 * hinausging**, dann **was unter dem Strich blieb**. Die obere Zahl hängt nicht am
 * Zeitfilter, die drei darunter schon — deshalb die Trennlinie und der Hinweis dazwischen,
 * sonst liest man vier Zahlen als eine Rechnung, die nicht aufgeht.
 */
@Composable
private fun KennzahlenBlock(
    headline: String,
    headlineAmount: Double,
    income: Double,
    expense: Double,
    result: Double,
    finColors: io.github.willywonka644.fintracker.ui.theme.FinColors,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1 — was gerade da ist
            Text(
                text = headline,
                style = MaterialTheme.typography.labelMedium,
                color = finColors.textSub,
            )
            Text(
                text = MoneyFormat.currencySigned(headlineAmount),
                style = MaterialTheme.typography.headlineSmall,
                color = if (headlineAmount >= 0) finColors.income else finColors.expense,
                fontWeight = FontWeight.Bold,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline,
            )

            Text(
                text = "im gewählten Zeitraum",
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textFaint,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // 2 — was hinein- und hinausging
            Row(modifier = Modifier.fillMaxWidth()) {
                KennzahlSpalte(
                    label = "Einnahmen",
                    amount = income,
                    color = finColors.income,
                    icon = Icons.Filled.ArrowUpward,
                    finColors = finColors,
                    modifier = Modifier.weight(1f),
                )
                KennzahlSpalte(
                    label = "Ausgaben",
                    amount = expense,
                    color = finColors.expense,
                    icon = Icons.Filled.ArrowDownward,
                    finColors = finColors,
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline,
            )

            // 3 — was unter dem Strich blieb
            Text(
                text = "Periodenergebnis",
                style = MaterialTheme.typography.labelMedium,
                color = finColors.textSub,
            )
            Text(
                text = MoneyFormat.currencySigned(result),
                style = MaterialTheme.typography.titleLarge,
                color = if (result >= 0) finColors.income else finColors.expense,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Eine der beiden Spalten in der Mitte des Blocks. Beträge ohne Vorzeichen: die
 *  Richtung sagen Pfeil und Farbe, ein Minus daneben läse sich wie doppelt verneint. */
@Composable
private fun KennzahlSpalte(
    label: String,
    amount: Double,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    finColors: io.github.willywonka644.fintracker.ui.theme.FinColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
            )
            Text(
                text = MoneyFormat.currency(kotlin.math.abs(amount)),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Seitenwahl: Einnahmen oder Ausgaben ──────────────────────────────────────

/**
 * Ein Chip in der Farbe seiner Seite.
 *
 * Gruen und Rot heissen in dieser App Einnahme und Ausgabe — hier meint der Chip
 * genau das, also entsteht keine vierte Farbbedeutung (#119). Nicht gewaehlt bleibt
 * er farblos bis auf den Punkt, damit die Wahl sichtbar ist und nicht geraten wird.
 */
@Composable
private fun SideChip(
    label: String,
    color: Color,
    selected: Boolean,
    idleColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) color else idleColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Liste unter dem Balkendiagramm ───────────────────────────────────────────

/** Was unter dem Balkendiagramm steht: eine Seite und, wenn gewaehlt, ein Monat. */
private data class BarDrilldown(val isIncome: Boolean, val monthIndex: Int? = null)

@Composable
private fun BarDrilldownCard(
    title: String,
    color: Color,
    bookings: List<Booking>,
    monthLabels: List<String>,
    selectedMonthIndex: Int?,
    onSelectMonth: (Int?) -> Unit,
    onClose: () -> Unit,
    onBookingClick: (Booking) -> Unit,
    finColors: FinColors,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    ChartCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) {
                Text("Schließen", style = MaterialTheme.typography.labelSmall)
            }
        }
        // Ein Monat aus dem Zeitraum — dasselbe, was am Desktop ein Klick auf einen
        // Balken tut. Auf dem Telefon ist ein Balken zwoelf Punkt breit; ein Chip
        // trifft man. Bei einem einzigen Monat waere die Reihe eine Wahl ohne Wahl.
        if (monthLabels.size > 1) {
            FilterChipRow(
                items = listOf("Alle") + monthLabels,
                selectedIndex = selectedMonthIndex?.plus(1) ?: 0,
                onSelect = { idx -> onSelectMonth(if (idx == 0) null else idx - 1) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (bookings.isEmpty()) {
            EmptyChartMessage(emptyMessage, modifier = Modifier.padding(16.dp))
        } else {
            // Wie viele und wie viel, bevor die Liste kommt: die Liste beantwortet sonst
            // "welche" und laesst "wie viel" offen — obwohl das die Zahl ist, wegen der
            // man sie geoeffnet hat. Bezieht sich immer auf die gezeigte Auswahl.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${bookings.size} Buchung${if (bookings.size != 1) "en" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
                Text(
                    text = MoneyFormat.currencyAbs(bookings.sumOf { abs(it.amount) }),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                bookings.forEach { booking ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookingClick(booking) }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = shortDate(booking),
                            style = MaterialTheme.typography.labelSmall,
                            color = finColors.textFaint,
                        )
                        Text(
                            text = booking.description.takeIf { it.isNotBlank() }
                                ?: booking.merchantName?.takeIf { it.isNotBlank() }
                                ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = MoneyFormat.currencySigned(booking.amount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (booking.amount < 0) finColors.expense else finColors.income,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                }
            }
        }
    }
}

/** "04.09." — das Jahr steht im Monatschip darueber. */
private fun shortDate(booking: Booking): String {
    val date = Instant.fromEpochMilliseconds(booking.effectiveDate ?: booking.timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}."
}
