package io.github.willywonka644.fintracker.ui.auswertungen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.analytics.BalanceDataPoint
import io.github.willywonka644.fintracker.analytics.BreakdownSearch
import io.github.willywonka644.fintracker.analytics.SelectablePeriod
import io.github.willywonka644.fintracker.analytics.calculateBalanceOverTime
import io.github.willywonka644.fintracker.analytics.CategoryBreakdownSide
import io.github.willywonka644.fintracker.analytics.calculateCategoryBreakdown
import io.github.willywonka644.fintracker.analytics.calculateMonthlyIncomeExpense
import io.github.willywonka644.fintracker.analytics.PeriodChoice
import io.github.willywonka644.fintracker.analytics.availableAbsolutePeriods
import io.github.willywonka644.fintracker.analytics.overviewBalances
import io.github.willywonka644.fintracker.analytics.periodTotals
import io.github.willywonka644.fintracker.analytics.toFilter
import io.github.willywonka644.fintracker.analytics.generateBillingCyclePeriods
import io.github.willywonka644.fintracker.analytics.SawthoothBalanceResult
import io.github.willywonka644.fintracker.analytics.calculateSawthoothBalance
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.account.displayName
import io.github.willywonka644.fintracker.ui.common.DesktopDatePickerField
import io.github.willywonka644.fintracker.ui.components.DesktopFinCard
import io.github.willywonka644.fintracker.ui.components.DesktopMoneyText
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import io.github.willywonka644.fintracker.util.parseGermanDate
import io.github.willywonka644.fintracker.util.toGermanDateString
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Multi-period filter options shown for credit card accounts (mutually exclusive with billing period mode)
private enum class CcMultiPeriod(val label: String, val days: Int) {
    DAYS_30("30 Tage", 30),
    MONTHS_3("3 Monate", 90),
    MONTHS_6("6 Monate", 180),
    YEAR("Jahr", 365),
    CUSTOM("Benutzerdefiniert", -1),
}

@Composable
fun DesktopAuswertungenScreen(
    bookings: List<Booking>,
    categories: List<Category>,
    accounts: List<Account> = emptyList(),
    /** Fuehrt in die Buchungsansicht (#129) — diese Listen waren bisher tote Zeilen. */
    onOpenBooking: (Booking) -> Unit = {},
) {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.todayIn(tz)
    val relativePeriods = PeriodChoice.Relative.entries

    // ── Period + custom date state ────────────────────────────────────────────
    // null heisst "kein Filter", und das ist der Ausgangszustand: erst alles, dann
    // einschraenken. Ein zweiter Klick auf denselben Chip fuehrt dorthin zurueck.
    var selectedPeriod by remember { mutableStateOf<PeriodChoice?>(null) }
    var customFromText by remember { mutableStateOf("") }
    var customToText   by remember { mutableStateOf("") }
    var customFromDate by remember { mutableStateOf<LocalDate?>(null) }
    var customToDate   by remember { mutableStateOf<LocalDate?>(null) }
    val isCustom = selectedPeriod is PeriodChoice.Custom
    val customRangeValid = customFromDate != null && customToDate != null &&
            customFromDate!! <= customToDate!!

    // ── Account state ─────────────────────────────────────────────────────────
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var showAccountMenu   by remember { mutableStateOf(false) }
    val selectedAccount = remember(accounts, selectedAccountId) {
        accounts.firstOrNull { it.id == selectedAccountId }
    }

    // ── Billing period state (credit card accounts only) ──────────────────────
    var selectedBillingPeriod by remember { mutableStateOf<SelectablePeriod?>(null) }
    val billingPeriods = remember(selectedAccount) {
        if (selectedAccount?.type == AccountType.CREDIT_CARD)
            generateBillingCyclePeriods(selectedAccount.billingStartDay ?: 18).takeLast(6).reversed()
        else emptyList()
    }
    val isCreditCard = selectedAccount?.type == AccountType.CREDIT_CARD
    // null = billing-period mode (single cycle); 0..4 = one of the CcMultiPeriod entries
    var selectedCcMultiPeriodIdx by remember { mutableStateOf<Int?>(null) }
    val isCcMultiPeriod = isCreditCard && selectedCcMultiPeriodIdx != null
    val isCcCustom = isCcMultiPeriod && CcMultiPeriod.entries.getOrNull(selectedCcMultiPeriodIdx!!) == CcMultiPeriod.CUSTOM
    // On account change: clear billing period for non-CC accounts; auto-select first period for CC
    remember(selectedAccountId) {
        if (billingPeriods.isEmpty()) selectedBillingPeriod = null
        else if (selectedBillingPeriod == null) selectedBillingPeriod = billingPeriods.firstOrNull()
        selectedCcMultiPeriodIdx = null
    }

    // ── Drilldown state ───────────────────────────────────────────────────────
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    // Eine Auswahl statt zweier Schalter (#114). Mit zwei Booleans liess sich nicht
    // umschalten: wer bei offener Einnahmenliste auf "Ausgaben" tippte, musste erst
    // "Einnahmen" wieder schliessen. Und ein Klick auf einen Balken hat keinen Platz in
    // einem Schalter — er meint eine Art UND einen Monat.
    var drilldown by remember { mutableStateOf<Drilldown?>(null) }
    fun toggleDrilldown(next: Drilldown) {
        drilldown = if (drilldown == next) null else next
    }
    // Welche Seite die Kategorie-Aufschluesselung zeigt (#127). Die Frage
    // "woher kam es" hat dieselbe Form wie "wohin ging es", und die Daten lagen schon da.
    var categorySide by remember { mutableStateOf(CategoryBreakdownSide.EXPENSES) }
    val categoryIsIncome = categorySide == CategoryBreakdownSide.INCOME
    fun switchCategorySide(next: CategoryBreakdownSide) {
        if (next == categorySide) return
        categorySide = next
        // Aufgeklappte Kategorien gehoeren zur alten Seite.
        expandedCategories = emptySet()
    }

    // ── Account-filtered bookings (no date filter) ────────────────────────────
    val postedBookings = remember(bookings, selectedAccountId) {
        bookings.filter {
            it.status == BookingStatus.POSTED && !it.deleted &&
                    (selectedAccountId == null || it.accountId == selectedAccountId)
        }
    }

    // ── Effective date bounds ─────────────────────────────────────────────────
    val isAllTime = selectedPeriod == null

    val effectiveFromDate: LocalDate? = remember(selectedPeriod, today, customFromDate, customToDate, selectedBillingPeriod, postedBookings, selectedCcMultiPeriodIdx) {
        when {
            selectedBillingPeriod != null -> {
                val ms = selectedBillingPeriod!!.filter.fromInclusive ?: return@remember null
                Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
            }
            isCcMultiPeriod -> {
                val p = CcMultiPeriod.entries[selectedCcMultiPeriodIdx!!]
                if (p == CcMultiPeriod.CUSTOM) { if (customRangeValid) customFromDate else null }
                else today.minus(p.days, DateTimeUnit.DAY)
            }
            isAllTime -> postedBookings
                .minOfOrNull { it.effectiveDate ?: it.timestamp }
                ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date }
            // Eine Regel für alle Zeiträume, aus shared (#114) — vorher rechnete jeder
            // der vier Bildschirme sie selbst, mit je eigenem Enum.
            else -> selectedPeriod.toFilter(tz, today).fromInclusive
                ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date }
        }
    }
    val effectiveToDate: LocalDate = remember(selectedPeriod, today, customToDate, selectedBillingPeriod, selectedCcMultiPeriodIdx) {
        when {
            selectedBillingPeriod != null -> {
                val ms = selectedBillingPeriod!!.filter.toExclusive ?: return@remember today
                Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date.minus(1, DateTimeUnit.DAY)
            }
            isCcCustom && customToDate != null && customRangeValid -> customToDate!!
            isCustom && customToDate != null && customRangeValid -> customToDate!!
            // Ein Quartal endet am Quartalsende, nicht heute — vorher lief jeder Zeitraum
            // bis zum aktuellen Tag, was für relative Spannen stimmt und für absolute nicht.
            else -> {
                val end = selectedPeriod.toFilter(tz, today).toExclusive
                    ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date.minus(1, DateTimeUnit.DAY) }
                    ?: today
                if (selectedPeriod is PeriodChoice.Relative) minOf(end, today) else end
            }
        }
    }

    // ── Date-filtered bookings ────────────────────────────────────────────────
    val filteredBookings = remember(postedBookings, effectiveFromDate, effectiveToDate, isAllTime) {
        if (isAllTime && selectedBillingPeriod == null) return@remember postedBookings
        val from = effectiveFromDate ?: return@remember emptyList()
        val fromMs = from.atStartOfDayIn(tz).toEpochMilliseconds()
        val toMs   = effectiveToDate.atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L
        postedBookings.filter { (it.effectiveDate ?: it.timestamp) in fromMs until toMs }
    }

    // Settlement rule (same as Android): settlements count only in the single-account view
    // of a non-CC account — on the Giro they are real money leaving; on the card the credit
    // is debt repayment, not income; across all accounts they would double-count.
    val excludeTransfersHere = selectedAccountId == null || isCreditCard
    // All three from one function, shared with Android (#100). The third tile used to sum
    // every booking untouched while the two beside it dropped corrections and settlements —
    // on the real database that made it contradict its neighbours by 3,111.10 €, which is
    // exactly those two kinds of booking. "Periodenergebnis" now means what it says:
    // income minus expense, on the same basis as the tiles it stands next to.
    val totals = remember(filteredBookings, excludeTransfersHere) {
        periodTotals(filteredBookings, excludeTransfers = excludeTransfersHere)
    }
    val income = totals.income
    val expense = totals.expense
    val saldo = totals.result

    // True current balance for the selected account(s), independent of the time filter.
    // Matches the chart endpoint and the balance shown in Buchungs-Ansicht.
    // Aus derselben Quelle wie Übersicht und Dashboard (#101/#114): ein Einzelkonto in
    // voller Höhe, eine Karte über ihren laufenden Zyklus, "Alle Konten" als
    // Gesamtvermögen mit vollem Kartenbestand. Vorher summierte dieser Bildschirm alles
    // alltime und wich damit bei Karten von den beiden anderen ab.
    val overview = remember(accounts, postedBookings) { overviewBalances(accounts, postedBookings) }
    val currentBalance = if (selectedAccountId == null) {
        overview.total
    } else {
        overview.perAccount[selectedAccountId] ?: 0.0
    }

    // Kontoabgleich-Korrekturen gehören in keine Kategorie (sie korrigieren nur den Saldo).
    // Kreditkartenabrechnungen dagegen bleiben sichtbar, sobald ein einzelnes Konto gewählt
    // ist — nur in der "Alle Konten"-Sicht fliegen sie raus, weil dieselbe Zahlung sonst
    // doppelt zählt (Ausgabe auf der Karte + Abbuchung vom Girokonto).
    val categoryBreakdown = remember(filteredBookings, excludeTransfersHere, categorySide) {
        val side = filteredBookings.filter {
            if (categoryIsIncome) it.amount > 0 else it.amount < 0
        }
        calculateCategoryBreakdown(
            side.filter { it.source != BookingSource.RECONCILIATION },
            filterTransfers = excludeTransfersHere,
            side = categorySide,
        )
    }
    val categoryMap = remember(categories) { categories.associateBy { it.name } }

    // Pre-compute drilldown lists outside any loop
    val top = remember(categoryBreakdown) { categoryBreakdown.take(8) }
    // Suche in der Aufschluesselung (#134/#137). Ohne Suchtext bleibt es bei den acht
    // groessten — mit Suchtext geht sie ueber den *ganzen* Bestand, sonst waere alles
    // ab Rang neun weiterhin unerreichbar, und genau das war der Anlass.
    var categoryQuery by remember { mutableStateOf("") }
    // Wonach gesucht wird, ist eine sichtbare Wahl und kein stiller Schalter (#137):
    // die Bezeichnungssuche rechnet die Karte neu, die Kategoriesuche nicht.
    var categorySearchMode by remember { mutableStateOf(BreakdownSearch.Mode.CATEGORY) }
    // Dieselbe Vorauswahl, aus der auch `categoryBreakdown` entsteht — sonst zaehlte die
    // Suche Zeilen mit, die das Diagramm darueber gar nicht kennt.
    val breakdownSource = remember(filteredBookings) {
        filteredBookings.filter { it.source != BookingSource.RECONCILIATION }
    }
    val categorySearch = remember(
        breakdownSource, categoryBreakdown, categoryQuery, categorySearchMode,
        categorySide, excludeTransfersHere,
    ) {
        BreakdownSearch.apply(
            periodBookings = breakdownSource,
            breakdown = categoryBreakdown,
            query = categoryQuery,
            mode = categorySearchMode,
            side = categorySide,
            filterTransfers = excludeTransfersHere,
        )
    }
    val listedCategories = remember(categorySearch, categoryQuery, top) {
        if (categoryQuery.isBlank()) top else categorySearch.entries
    }
    val categoryBookingsMap = remember(categorySearch, listedCategories, categorySide) {
        listedCategories.associate { entry ->
            entry.categoryName to categorySearch.bookings
                // Nach Vorzeichen wie die Prozente darueber: ohne diesen Filter stand
                // unter einem Balken, der Ausgaben misst, auch eine Erstattung derselben
                // Kategorie — und die Liste ging gegen ihre eigene Summe nicht auf.
                .filter { if (categoryIsIncome) it.amount > 0 else it.amount < 0 }
                .filter { (it.category?.trim()?.takeIf { c -> c.isNotBlank() } ?: "Ohne Kategorie") == entry.categoryName }
                .sortedByDescending { it.effectiveDate ?: it.timestamp }
        }
    }
    val incomeDrilldown = remember(filteredBookings) {
        filteredBookings.filter { it.amount > 0 }.sortedByDescending { it.effectiveDate ?: it.timestamp }
    }
    val expenseDrilldown = remember(filteredBookings) {
        filteredBookings.filter { it.amount < 0 }.sortedByDescending { it.effectiveDate ?: it.timestamp }
    }

    val chartStartingBalance = remember(postedBookings, effectiveFromDate, isAllTime, isCreditCard) {
        when {
            isCreditCard -> 0.0  // each billing period starts fresh — no carryover
            isAllTime || effectiveFromDate == null -> 0.0
            else -> {
                val fromMs = effectiveFromDate.atStartOfDayIn(tz).toEpochMilliseconds()
                postedBookings.filter { (it.effectiveDate ?: it.timestamp) < fromMs }.sumOf { it.amount }
            }
        }
    }
    // Sawtooth balance series for CC multi-period mode (computed before balanceData)
    val sawthoothResult = remember(postedBookings, effectiveFromDate, effectiveToDate, selectedCcMultiPeriodIdx, selectedAccountId) {
        val from = effectiveFromDate ?: return@remember SawthoothBalanceResult(emptyList(), emptyList())
        if (!isCcMultiPeriod) return@remember SawthoothBalanceResult(emptyList(), emptyList())
        calculateSawthoothBalance(
            bookings       = postedBookings,
            billingStartDay = selectedAccount?.billingStartDay ?: 18,
            fromDate       = from,
            toDate         = effectiveToDate,
            tz             = tz,
        )
    }
    val balanceData = remember(postedBookings, effectiveFromDate, effectiveToDate, chartStartingBalance, selectedCcMultiPeriodIdx) {
        if (isCcMultiPeriod) return@remember sawthoothResult.points
        val from = effectiveFromDate ?: return@remember emptyList()
        if (postedBookings.isEmpty()) emptyList()
        else calculateBalanceOverTime(
            postedBookings, chartStartingBalance,
            from.atStartOfDayIn(tz).toEpochMilliseconds(),
            effectiveToDate.atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L,
        )
    }
    // Reconciliation corrections are not real income/expense; settlement rule as above.
    val monthlyData = remember(filteredBookings, excludeTransfersHere) {
        calculateMonthlyIncomeExpense(
            filteredBookings.filter { it.source != BookingSource.RECONCILIATION },
            filterTransfers = excludeTransfersHere,
        )
    }

    val finColors = FinTheme.colors
    val density = LocalDensity.current

    var barHoveredBar   by remember(monthlyData) { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var barHoverPos     by remember { mutableStateOf(Offset.Zero) }
    var barCanvasW      by remember { mutableStateOf(0f) }
    var donutHoveredIdx by remember(categoryBreakdown) { mutableStateOf<Int?>(null) }
    var donutCanvasW    by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Header: title + account dropdown ──────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Auswertungen",
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (accounts.isNotEmpty()) {
                Box {
                    Row(
                        modifier = Modifier
                            .clickable { showAccountMenu = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                selectedAccount?.name ?: "Alle Konten",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                selectedAccount?.type?.displayName() ?: "Alle Buchungen",
                                style = MaterialTheme.typography.bodySmall,
                                color = finColors.textSub,
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(20.dp), tint = finColors.textSub)
                    }
                    DropdownMenu(
                        expanded = showAccountMenu,
                        onDismissRequest = { showAccountMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Alle Konten", style = MaterialTheme.typography.bodyMedium)
                                    Text("Alle Buchungen", style = MaterialTheme.typography.labelSmall, color = finColors.textSub)
                                }
                            },
                            onClick = { selectedAccountId = null; showAccountMenu = false },
                        )
                        HorizontalDivider()
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(acc.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(acc.type.displayName(), style = MaterialTheme.typography.labelSmall, color = finColors.textSub)
                                    }
                                },
                                onClick = { selectedAccountId = acc.id; showAccountMenu = false },
                            )
                        }
                    }
                }
            }
        }

        // ── Period filter chips (hidden for credit card accounts) ────────────
        // Relative Spannen als Chips, absolute im Menü daneben (#114): "die letzten 30
        // Tage" ist eine Gewohnheit und gehört auf einen Klick, "Q2 2026" ist eine
        // gezielte Frage und verträgt ein Menü. Und die Chipreihe wächst nicht mit
        // jedem Jahr, das dazukommt.
        if (!isCreditCard) {
            val absolutePeriods = remember(bookings) { availableAbsolutePeriods(bookings, tz) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                relativePeriods.forEach { p ->
                    FilterChip(
                        selected = p == selectedPeriod && selectedBillingPeriod == null,
                        // Nochmal auf denselben Chip heisst "Filter weg": zurück zum
                        // Zustand ohne Auswahl, in dem alles gezeigt wird.
                        onClick = {
                            val alreadyOn = p == selectedPeriod && selectedBillingPeriod == null
                            selectedPeriod = if (alreadyOn) null else p
                            selectedBillingPeriod = null
                        },
                        label = { Text(p.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
                if (absolutePeriods.isNotEmpty()) {
                    var menuOpen by remember { mutableStateOf(false) }
                    val chosen = selectedPeriod?.takeIf { it !is PeriodChoice.Relative && it !is PeriodChoice.Custom }
                    Box {
                        FilterChip(
                            selected = chosen != null,
                            onClick = { menuOpen = true },
                            label = {
                                Text(
                                    chosen?.label ?: "Quartal / Jahr",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (chosen != null) {
                                DropdownMenuItem(
                                    text = { Text("Auswahl aufheben") },
                                    onClick = { selectedPeriod = null; menuOpen = false },
                                )
                                HorizontalDivider()
                            }
                            absolutePeriods.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text(p.label) },
                                    onClick = {
                                        selectedPeriod = p
                                        selectedBillingPeriod = null
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── CC: multi-period chips row + billing-period bar ──────────────────
        if (isCreditCard) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CcMultiPeriod.entries.forEachIndexed { idx, p ->
                    FilterChip(
                        selected = selectedCcMultiPeriodIdx == idx,
                        onClick  = { selectedCcMultiPeriodIdx = idx; selectedBillingPeriod = null },
                        label    = { Text(p.label, style = MaterialTheme.typography.labelMedium) },
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
                // "Einzelabrechnung" chip switches back to single-billing-period mode
                FilterChip(
                    selected = selectedCcMultiPeriodIdx == null,
                    onClick  = {
                        selectedCcMultiPeriodIdx = null
                        if (selectedBillingPeriod == null) selectedBillingPeriod = billingPeriods.firstOrNull()
                    },
                    label    = { Text("Einzelabrechnung", style = MaterialTheme.typography.labelMedium) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor     = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            if (selectedCcMultiPeriodIdx == null) {
                AbrechnungszeitraumBar(
                    periods = billingPeriods,
                    selectedPeriod = selectedBillingPeriod,
                    onPeriodSelected = { selectedBillingPeriod = it },
                )
            }
        }

        // ── Custom date range inputs ──────────────────────────────────────────
        if ((isCustom && !isCreditCard) || isCcCustom) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DesktopDatePickerField(
                        value = customFromText,
                        onValueChange = { raw ->
                            customFromText = raw
                            customFromDate = parseGermanDate(raw)
                        },
                        label = "Von",
                    placeholder = "TT.MM.JJJJ",
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        isError = customFromText.isNotEmpty() && customFromDate == null,
                        selectedDate = customFromDate,
                    )
                    DesktopDatePickerField(
                        value = customToText,
                        onValueChange = { raw ->
                            customToText = raw
                            customToDate = parseGermanDate(raw)
                        },
                        label = "Bis",
                    placeholder = "TT.MM.JJJJ",
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        isError = customToText.isNotEmpty() && customToDate == null,
                        selectedDate = customToDate,
                    )
                }
                when {
                    customFromDate != null && customToDate != null && customFromDate!! > customToDate!! ->
                        Text(
                            "Das Von-Datum muss vor oder gleich dem Bis-Datum liegen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    (isCustom || isCcCustom) && !customRangeValid && (customFromText.isNotEmpty() || customToText.isNotEmpty()) ->
                        Text(
                            "Bitte beide Daten im Format TT.MM.JJJJ eingeben.",
                            style = MaterialTheme.typography.bodySmall,
                            color = finColors.textSub,
                        )
                }
            }
        }

        // ── Kontostand header (independent of time filter) ────────────────────
        // Auch für Kreditkarten (#114): vorher stand hier bei einer Karte nichts, obwohl
        // es eine Zahl gibt — die Buchungen-Seite und die Übersicht zeigen sie längst.
        run {
            DesktopFinCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            when {
                                selectedAccountId == null -> "Gesamtvermögen"
                                isCreditCard -> "Kontostand · laufende Abrechnung"
                                else -> "Kontostand"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = finColors.textSub,
                        )
                        Text(
                            selectedAccount?.name ?: "Alle Konten",
                            style = MaterialTheme.typography.bodySmall,
                            color = finColors.textSub,
                        )
                    }
                    DesktopMoneyText(
                        currentBalance,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }

        // ── KPI row ───────────────────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesktopFinCard(modifier = Modifier.weight(1f)) {
                Text("Einnahmen", style = MaterialTheme.typography.labelMedium, color = finColors.textSub)
                Spacer(Modifier.height(4.dp))
                DesktopMoneyText(income, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            DesktopFinCard(modifier = Modifier.weight(1f)) {
                Text("Ausgaben", style = MaterialTheme.typography.labelMedium, color = finColors.textSub)
                Spacer(Modifier.height(4.dp))
                DesktopMoneyText(-expense, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            DesktopFinCard(modifier = Modifier.weight(1f)) {
                Text("Periodenergebnis", style = MaterialTheme.typography.labelMedium, color = finColors.textSub)
                if (isCcMultiPeriod) {
                    Text("Gesamtzeitraum", style = MaterialTheme.typography.labelSmall, color = finColors.textSub)
                }
                Spacer(Modifier.height(4.dp))
                DesktopMoneyText(saldo, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        }

        // ── Two-column layout ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // ── Left column ───────────────────────────────────────────────────
            Column(modifier = Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Kontostandsverlauf
                DesktopFinCard {
                    // Ueber alle Konten ist es kein Kontostand (#114) — gleiche Unterscheidung
                    // wie bei der Kennzahlen-Ueberschrift darueber.
                    Text(
                        if (selectedAccountId == null) "Vermögensverlauf" else "Kontostandsverlauf",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    effectiveFromDate?.let { ef ->
                        val multiMonth = ef.year != effectiveToDate.year || ef.monthNumber != effectiveToDate.monthNumber
                        val fromLabel = "${ef.dayOfMonth}. ${monthShort(ef.monthNumber)}${if (multiMonth) " ${ef.year}" else ""}"
                        val toLabel   = "${effectiveToDate.dayOfMonth}. ${monthShort(effectiveToDate.monthNumber)}${if (multiMonth) " ${effectiveToDate.year}" else ""}"
                        Text("Zeitraum: $fromLabel – $toLabel", style = MaterialTheme.typography.labelSmall, color = finColors.textSub)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (balanceData.isEmpty()) EmptyChart(if (effectiveFromDate == null) "Zeitraum auswählen" else "Keine Daten")
                    else AuswertungenLineChart(
                        dailyData = balanceData,
                        color = MaterialTheme.colorScheme.primary,
                        height = 160,
                        billingBoundaryMillis = if (isCcMultiPeriod) sawthoothResult.billingBoundaryMillis else emptyList(),
                    )
                }

                // Einnahmen vs. Ausgaben — legend labels are clickable drilldown toggles
                DesktopFinCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Einnahmen vs. Ausgaben", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToggleLegendChip(
                                label = "Einnahmen",
                                color = finColors.income,
                                active = drilldown?.isIncome == true,
                                onClick = { toggleDrilldown(Drilldown(isIncome = true)) },
                            )
                            ToggleLegendChip(
                                label = "Ausgaben",
                                color = finColors.expense,
                                active = drilldown?.isIncome == false,
                                onClick = { toggleDrilldown(Drilldown(isIncome = false)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (monthlyData.isEmpty()) {
                        EmptyChart(if (effectiveFromDate == null) "Zeitraum auswählen" else "Keine Daten")
                    } else {
                        val n = monthlyData.size
                        val maxVal = monthlyData.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0).toFloat()
                        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        Box(Modifier.fillMaxWidth().height(140.dp)) {
                            Canvas(
                                Modifier.fillMaxSize()
                                    .onSizeChanged { barCanvasW = it.width.toFloat() }
                                    .pointerInput(monthlyData) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val ev = awaitPointerEvent()
                                                val pos = ev.changes.firstOrNull()?.position
                                                when (ev.type) {
                                                    PointerEventType.Move -> if (pos != null && barCanvasW > 0f) {
                                                        barHoverPos = pos
                                                        val gw = barCanvasW / n
                                                        val gp = gw * 0.1f; val bg = 2f
                                                        val bw = ((gw - 2 * gp - bg) / 2).coerceAtLeast(2f)
                                                        val gi = (pos.x / gw).toInt().coerceIn(0, n - 1)
                                                        val lx = pos.x - gi * gw
                                                        barHoveredBar = when {
                                                            lx >= gp && lx < gp + bw -> Pair(gi, true)
                                                            lx >= gp + bw + bg && lx < gp + 2 * bw + bg -> Pair(gi, false)
                                                            else -> null
                                                        }
                                                    }
                                                    // Klick auf einen Balken (#114): oeffnet die
                                                    // Liste darunter, auf diesen Monat und diese
                                                    // Art gefiltert. Nochmal derselbe Balken
                                                    // schliesst sie wieder.
                                                    PointerEventType.Press -> barHoveredBar?.let { (gi, isIncome) ->
                                                        toggleDrilldown(Drilldown(isIncome = isIncome, monthIndex = gi))
                                                    }
                                                    PointerEventType.Exit -> barHoveredBar = null
                                                }
                                            }
                                        }
                                    },
                            ) {
                                val w = size.width; val h = size.height
                                val gw = w / n; val gp = gw * 0.1f; val bg = 2f
                                val bw = ((gw - 2 * gp - bg) / 2).coerceAtLeast(2f)
                                for (s in 0..3) drawLine(gridColor, Offset(0f, h * (1f - s / 3f)), Offset(w, h * (1f - s / 3f)), 1f)
                                monthlyData.forEachIndexed { i, d ->
                                    val gl = i * gw + gp
                                    val iH = (d.income / maxVal).toFloat() * h
                                    val eH = (d.expense / maxVal).toFloat() * h
                                    val selHere = drilldown?.monthIndex == i
                                    val incHl = barHoveredBar?.let { it.first == i && it.second } == true ||
                                        (selHere && drilldown?.isIncome == true)
                                    val expHl = barHoveredBar?.let { it.first == i && !it.second } == true ||
                                        (selHere && drilldown?.isIncome == false)
                                    drawRect(if (incHl) finColors.income.copy(alpha = 0.7f) else finColors.income, Offset(gl, h - iH), Size(bw, iH))
                                    drawRect(if (expHl) finColors.expense.copy(alpha = 0.7f) else finColors.expense, Offset(gl + bw + bg, h - eH), Size(bw, eH))
                                }
                            }
                            barHoveredBar?.let { (idx, isIncome) ->
                                val d = monthlyData[idx]
                                val value = if (isIncome) d.income else d.expense
                                val color = if (isIncome) finColors.income else finColors.expense
                                val label = if (isIncome) "+${MoneyFormat.currency(value)}" else MoneyFormat.currency(-value)
                                with(density) {
                                    Box(
                                        Modifier
                                            .absoluteOffset(
                                                x = (barHoverPos.x.toDp() - 52.dp).coerceAtLeast(0.dp),
                                                y = (barHoverPos.y.toDp() - 44.dp).coerceAtLeast(0.dp),
                                            )
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                    ) {
                                        Column {
                                            Text(d.monthLabel, style = MaterialTheme.typography.labelSmall, color = finColors.textSub)
                                            Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(Modifier.fillMaxWidth()) {
                            val le = ((n + 7) / 8).coerceAtLeast(1)
                            monthlyData.forEachIndexed { i, d ->
                                Text(
                                    if (n <= 6 || i % le == 0 || i == n - 1) d.monthLabel else "",
                                    style = MaterialTheme.typography.labelSmall, color = finColors.textFaint,
                                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // ── Drilldown: eine Liste, die sich aktualisiert ──────────────
                // Vorher zwei getrennte Karten, die nebeneinander offen stehen konnten.
                // Jetzt zeigt sie, was gerade gewählt ist — Art und, wenn ein Balken
                // angetippt wurde, dessen Monat.
                drilldown?.let { sel ->
                    val point = sel.monthIndex?.let { monthlyData.getOrNull(it) }
                    val base = if (sel.isIncome) incomeDrilldown else expenseDrilldown
                    val shown = if (point == null) base else base.filter { point.covers(it) }
                    val what = if (sel.isIncome) "Einnahmen" else "Ausgaben"
                    val where = point?.monthLabel ?: "im Zeitraum"
                    DrilldownCard(
                        title = if (point == null) "$what $where" else "$what · $where",
                        color = if (sel.isIncome) finColors.income else finColors.expense,
                        bookings = shown,
                        emptyMessage = "Keine $what ${point?.let { "in " + it.monthLabel } ?: "im gewählten Zeitraum"}",
                        onOpenBooking = onOpenBooking,
                    )
                }
            }

            // ── Right column: Ausgaben nach Kategorie ─────────────────────────
            Column(modifier = Modifier.weight(2f)) {
                DesktopFinCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (categoryIsIncome) "Einnahmen nach Kategorie" else "Ausgaben nach Kategorie",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SideChip(
                                label = "Ausgaben",
                                color = finColors.expense,
                                active = !categoryIsIncome,
                                onClick = { switchCategorySide(CategoryBreakdownSide.EXPENSES) },
                            )
                            SideChip(
                                label = "Einnahmen",
                                color = finColors.income,
                                active = categoryIsIncome,
                                onClick = { switchCategorySide(CategoryBreakdownSide.INCOME) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (top.isEmpty()) {
                        EmptyChart(
                            when {
                                effectiveFromDate == null -> "Zeitraum auswählen"
                                categoryIsIncome -> "Keine Einnahmen im Zeitraum"
                                else -> "Keine Ausgaben im Zeitraum"
                            }
                        )
                    } else {
                        // Donut chart (unchanged)
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            Canvas(
                                modifier = Modifier.size(120.dp)
                                    .onSizeChanged { donutCanvasW = it.width.toFloat() }
                                    .pointerInput(listedCategories) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val ev = awaitPointerEvent()
                                                val pos = ev.changes.firstOrNull()?.position
                                                when (ev.type) {
                                                    PointerEventType.Move -> if (pos != null && donutCanvasW > 0f) {
                                                        val cx = donutCanvasW / 2f; val cy = donutCanvasW / 2f
                                                        val sw = donutCanvasW * 0.2f
                                                        val r = (donutCanvasW - sw) / 2f
                                                        val dx = pos.x - cx; val dy = pos.y - cy
                                                        val dist = sqrt(dx * dx + dy * dy)
                                                        if (dist < r - sw / 2 || dist > r + sw / 2) {
                                                            donutHoveredIdx = null
                                                        } else {
                                                            val hitAngle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + 360f) % 360f
                                                            var sa = 0f; donutHoveredIdx = null
                                                            for (i in listedCategories.indices) {
                                                                val sweep = (listedCategories[i].percentage * 360f).toFloat().coerceAtLeast(0.001f)
                                                                if (hitAngle >= sa && hitAngle < sa + sweep) { donutHoveredIdx = i; break }
                                                                sa += sweep
                                                            }
                                                        }
                                                    }
                                                    PointerEventType.Exit -> donutHoveredIdx = null
                                                }
                                            }
                                        }
                                    },
                            ) {
                                var startAngle = -90f
                                listedCategories.forEachIndexed { idx, entry ->
                                    val catColor = Color(categoryMap[entry.categoryName]?.color?.toInt() ?: finColors.gradStart.hashCode())
                                    val sweep = (entry.percentage * 360f).toFloat()
                                    val isHovered = donutHoveredIdx == idx
                                    drawArc(
                                        color = if (isHovered) catColor.copy(alpha = 0.7f) else catColor,
                                        startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                                        style = Stroke(width = if (isHovered) size.width * 0.25f else size.width * 0.2f, cap = StrokeCap.Butt),
                                    )
                                    startAngle += sweep
                                }
                            }
                            donutHoveredIdx?.let { idx ->
                                val entry = listedCategories.getOrNull(idx) ?: return@let
                                val catColor = Color(categoryMap[entry.categoryName]?.color?.toInt() ?: finColors.gradStart.hashCode())
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.size(68.dp),
                                ) {
                                    Text(entry.categoryName, style = MaterialTheme.typography.labelSmall, color = catColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${(entry.percentage * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = catColor)
                                    Text(MoneyFormat.currency(abs(entry.amount)), style = MaterialTheme.typography.labelSmall, color = finColors.textSub, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        DesktopCategorySearchField(
                            query = categoryQuery,
                            onQueryChange = { categoryQuery = it },
                            mode = categorySearchMode,
                            onModeChange = { categorySearchMode = it; expandedCategories = emptySet() },
                            finColors = finColors,
                        )
                        val categoryHint = when {
                            categoryQuery.isBlank() && categoryBreakdown.size > top.size ->
                                "Die ${top.size} größten von ${categoryBreakdown.size} Kategorien · der Rest über die Suche"
                            categoryQuery.isBlank() -> ""
                            listedCategories.isEmpty() -> "Nichts passt zu „${categoryQuery.trim()}“."
                            // Neu gerechnet: die Zahl oben ist die Summe der Zeilen
                            // darunter, und das muss dastehen (#125).
                            categorySearch.recomputed ->
                                "${categorySearch.hits} ${if (categorySearch.hits == 1) "Buchung" else "Buchungen"} · " +
                                    "${MoneyFormat.currency(categorySearch.hitTotal)} · Prozente beziehen sich auf diese Treffer"
                            else -> "${listedCategories.size} von ${categoryBreakdown.size} Kategorien · Prozente und Donut gelten weiter für den ganzen Zeitraum"
                        }
                        if (categoryHint.isNotEmpty()) {
                            Text(
                                text = categoryHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = finColors.textSub,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }

                        // Category list — each row clickable, drilldown inline below
                        listedCategories.forEachIndexed { idx, entry ->
                            if (idx > 0) HorizontalDivider(
                                modifier = Modifier.padding(vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            )
                            val catColor = Color(categoryMap[entry.categoryName]?.color?.toInt() ?: finColors.gradStart.hashCode())
                            val isExpanded = entry.categoryName in expandedCategories
                            val catBookings = categoryBookingsMap[entry.categoryName] ?: emptyList()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        expandedCategories = if (isExpanded)
                                            expandedCategories - entry.categoryName
                                        else
                                            expandedCategories + entry.categoryName
                                    }
                                    .background(
                                        if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        else Color.Transparent
                                    )
                                    .padding(vertical = 2.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(catColor))
                                Text(
                                    entry.categoryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    MoneyFormat.currency(abs(entry.amount)),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = finColors.expense,
                                )
                                Text(
                                    "${(entry.percentage * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = finColors.textSub,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.End,
                                )
                                Text(
                                    if (isExpanded) "▾" else "▸",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isExpanded) catColor else finColors.textFaint,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { entry.percentage.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.small),
                                color = catColor,
                                trackColor = catColor.copy(alpha = 0.15f),
                            )

                            // Inline drilldown
                            if (isExpanded) {
                                Spacer(Modifier.height(8.dp))
                                if (catBookings.isEmpty()) {
                                    Text(
                                        "Keine Buchungen in diesem Zeitraum",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = finColors.textSub,
                                        modifier = Modifier.padding(start = 18.dp, bottom = 4.dp),
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                        verticalArrangement = Arrangement.spacedBy(0.dp),
                                    ) {
                                        items(catBookings, key = { it.id }) { booking ->
                                            CompactBookingRow(booking, tz, onClick = { onOpenBooking(booking) })
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Drilldown card (income/expense) ───────────────────────────────────────────

@Composable
private fun DrilldownCard(
    title: String,
    color: Color,
    bookings: List<Booking>,
    emptyMessage: String,
    onOpenBooking: (Booking) -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    DesktopFinCard {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(8.dp))
        if (bookings.isEmpty()) {
            Text(emptyMessage, style = MaterialTheme.typography.bodySmall, color = FinTheme.colors.textSub)
        } else {
            // Die Summe dessen, was hier steht (#114): die Liste beantwortet sonst "welche"
            // und lässt "wie viel" offen — obwohl genau das die Zahl ist, wegen der man
            // sie aufgeklappt hat. Bezieht sich immer auf die gezeigte Auswahl, also bei
            // einem angetippten Balken auf dessen Monat.
            val total = bookings.sumOf { kotlin.math.abs(it.amount) }
            // Die Summe steht rechts, in der Farbe der Liste — dann fluchtet sie mit den
            // Beträgen darunter und man liest eine Spalte statt zweier Stellen.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${bookings.size} Buchung${if (bookings.size != 1) "en" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = FinTheme.colors.textSub,
                )
                Text(
                    MoneyFormat.currency(total),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(bookings, key = { it.id }) { booking ->
                    CompactBookingRow(booking, tz, onClick = { onOpenBooking(booking) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ── Compact booking row for drilldowns ────────────────────────────────────────

@Composable
private fun CompactBookingRow(booking: Booking, tz: TimeZone, onClick: (() -> Unit)? = null) {
    val finColors = FinTheme.colors
    val date = Instant.fromEpochMilliseconds(booking.effectiveDate ?: booking.timestamp).toLocalDateTime(tz).date
    val dateStr = "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
    val title = booking.description.takeIf { it.isNotBlank() }
        ?: booking.merchantName?.takeIf { it.isNotBlank() }
        ?: "—"
    Row(
        modifier = Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(dateStr, style = MaterialTheme.typography.bodySmall, color = finColors.textSub, modifier = Modifier.width(72.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        DesktopMoneyText(amount = booking.amount, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

// ── Clickable legend chip (Einnahmen / Ausgaben drilldown toggles) ────────────

@Composable
private fun ToggleLegendChip(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    val finColors = FinTheme.colors
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (active) color.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) color else finColors.textSub,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(if (active) "▾" else "▸", style = MaterialTheme.typography.labelSmall, color = if (active) color else finColors.textFaint)
    }
}

// ── Seitenwahl der Kategorie-Aufschluesselung ────────────────────────────────

/**
 * Wie [ToggleLegendChip], aber ohne das Auf-/Zu-Zeichen: dieser Chip oeffnet nichts,
 * er wechselt die Seite. Gruen und Rot heissen hier, was sie ueberall heissen.
 */
@Composable
private fun SideChip(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    val finColors = FinTheme.colors
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (active) color.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) color else finColors.textSub,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Line chart ────────────────────────────────────────────────────────────────

@Composable
internal fun AuswertungenLineChart(
    dailyData: List<BalanceDataPoint>,
    color: Color,
    height: Int,
    billingBoundaryMillis: List<Long> = emptyList(),
) {
    val tz = TimeZone.currentSystemDefault()
    val density = LocalDensity.current
    val finColors = FinTheme.colors
    val balances = dailyData.map { it.balance }
    val n = balances.size
    val minBal = balances.min()
    val maxBal = balances.max()
    val yPad = if (maxBal - minBal < 0.01) 1.0 else (maxBal - minBal) * 0.15
    val yMin = minBal - yPad
    val yRange = (maxBal + yPad) - yMin
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val fillColor = color.copy(alpha = 0.15f)

    var hoveredIdx by remember(dailyData) { mutableStateOf<Int?>(null) }
    var hoverPos by remember { mutableStateOf(Offset.Zero) }
    var canvasW by remember { mutableStateOf(0f) }

    val yAxisWidth = 62.dp

    Row(Modifier.fillMaxWidth()) {
        // Y-axis labels (top = max, bottom = min), aligned with grid lines
        Column(
            Modifier.width(yAxisWidth).height(height.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            for (step in 3 downTo 0) {
                val value = yMin + yRange * step / 3
                Text(
                    formatYAxisLabel(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textFaint,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
                )
            }
        }

        // Chart canvas + hover tooltip
        Box(Modifier.weight(1f).height(height.dp)) {
            Canvas(
                modifier = Modifier.fillMaxSize()
                    .onSizeChanged { canvasW = it.width.toFloat() }
                    .pointerInput(dailyData) {
                        awaitPointerEventScope {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pos = ev.changes.firstOrNull()?.position
                                when (ev.type) {
                                    PointerEventType.Move -> if (pos != null && canvasW > 0f) {
                                        hoverPos = pos
                                        hoveredIdx = (pos.x / (canvasW / n)).toInt().coerceIn(0, n - 1)
                                    }
                                    PointerEventType.Exit -> hoveredIdx = null
                                }
                            }
                        }
                    },
            ) {
                val w = size.width; val h = size.height
                for (step in 0..3) drawLine(gridColor, Offset(0f, h * (1f - step / 3f)), Offset(w, h * (1f - step / 3f)), 1f)
                fun balToY(b: Double) = (h - ((b - yMin) / yRange * h)).toFloat().coerceIn(0f, h)
                val pts = balances.mapIndexed { i, b -> Offset((i + 0.5f) * (w / n), balToY(b)) }
                if (n >= 2) {
                    val fillPath = Path().apply {
                        moveTo(pts[0].x, h); lineTo(pts[0].x, pts[0].y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                        lineTo(pts.last().x, h); close()
                    }
                    drawPath(fillPath, fillColor)
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        pts.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, color, style = Stroke(2.5f, cap = StrokeCap.Round))
                }
                // Vertical billing-period boundary lines (sawtooth mode)
                billingBoundaryMillis.forEach { boundaryMs ->
                    val boundaryIdx = dailyData.indexOfFirst { it.dateMillis >= boundaryMs }
                    if (boundaryIdx in 0 until n) {
                        val x = (boundaryIdx + 0.5f) * (w / n)
                        drawLine(color.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                    }
                }
                hoveredIdx?.let { i -> pts.getOrNull(i)?.let { pt -> drawCircle(color, 6f, pt); drawCircle(Color.White, 3f, pt) } }
            }
            hoveredIdx?.let { idx ->
                val dp = dailyData[idx]
                val date = Instant.fromEpochMilliseconds(dp.dateMillis).toLocalDateTime(tz).date
                with(density) {
                    Box(
                        Modifier
                            .absoluteOffset(
                                x = (hoverPos.x.toDp() - 50.dp).coerceAtLeast(0.dp),
                                y = (hoverPos.y.toDp() - 44.dp).coerceAtLeast(0.dp),
                            )
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Column {
                            Text("${date.dayOfMonth}. ${monthShort(date.monthNumber)} ${date.year}", style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textSub)
                            Text(MoneyFormat.currency(dp.balance), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(2.dp))
    // X-axis: absolute-position labels based on the tracked canvas width so that month
    // names are never clipped regardless of how many data points are in the series.
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(yAxisWidth))
        Box(Modifier.weight(1f).height(16.dp)) {
            if (canvasW > 0f) {
                val labelEvery = ((n + 7) / 8).coerceAtLeast(1)
                with(density) {
                    dailyData.forEachIndexed { i, dp ->
                        if (n <= 10 || i % labelEvery == 0 || i == n - 1) {
                            val date = Instant.fromEpochMilliseconds(dp.dateMillis).toLocalDateTime(TimeZone.currentSystemDefault()).date
                            val xPx = canvasW * (i + 0.5f) / n
                            Text(
                                "${date.dayOfMonth}. ${monthShort(date.monthNumber)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FinTheme.colors.textFaint,
                                maxLines = 1,
                                modifier = Modifier.absoluteOffset(x = (xPx.toDp() - 20.dp).coerceAtLeast(0.dp)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatYAxisLabel(v: Double): String = when {
    abs(v) >= 10_000 -> "${(v / 1_000).roundToInt()} k€"
    else -> "${v.roundToInt()} €"
}

@Composable
private fun EmptyChart(message: String) {
    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = FinTheme.colors.textSub)
    }
}

@Composable
private fun AbrechnungszeitraumBar(
    periods: List<SelectablePeriod>,
    selectedPeriod: SelectablePeriod?,
    onPeriodSelected: (SelectablePeriod) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Abrechnungszeitraum:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            FilterChip(
                selected = selectedPeriod != null,
                onClick = { expanded = true },
                label = {
                    Text(
                        selectedPeriod?.label ?: "Zeitraum wählen",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                periods.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(period.label, style = MaterialTheme.typography.bodySmall) },
                        onClick = { onPeriodSelected(period); expanded = false },
                    )
                }
            }
        }
    }
}

private fun monthShort(m: Int) = when (m) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mär"; 4 -> "Apr"
    5 -> "Mai"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
    9 -> "Sep"; 10 -> "Okt"; 11 -> "Nov"; else -> "Dez"
}

/**
 * Was die Liste unter dem Balkendiagramm gerade zeigt (#114).
 *
 * [monthIndex] `null` heisst "der ganze gewaehlte Zeitraum" — so wirken die zwei
 * Legenden-Chips. Ein Klick auf einen Balken setzt ihn und schraenkt damit auf dessen
 * Monat ein. Weil beides dasselbe Objekt ist, schaltet ein Klick auf etwas anderes
 * einfach um, statt erst das Vorherige schliessen zu muessen.
 */
private data class Drilldown(val isIncome: Boolean, val monthIndex: Int? = null)

// ── Kategoriesuche ──────────────────────────────────────────

/**
 * Sucht eine Kategorie in der Aufschluesselung (#134).
 *
 * Filtert nur die Liste. Der Donut zeigt weiter die acht groessten, und die Prozente
 * bleiben Anteile am ganzen Zeitraum — ein an drei Treffern neu gerechneter Anteil
 * beantwortet eine Frage, die niemand gestellt hat.
 */
@Composable
private fun DesktopCategorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    mode: BreakdownSearch.Mode,
    onModeChange: (BreakdownSearch.Mode) -> Unit,
    finColors: io.github.willywonka644.fintracker.ui.theme.FinColors,
) {
    // Eine Zeile, durch eine Linie vom Donut getrennt. Vorher hingen zwei Chips frei
    // unter dem Diagramm und sahen aus wie eine zweite Legende — sie trugen sogar
    // deren farbigen Punkt, weil hier faelschlich `SideChip` wiederverwendet wurde.
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Suchen in",
            style = MaterialTheme.typography.labelSmall,
            color = finColors.textSub,
        )
        SearchModeSegment(mode = mode, onModeChange = onModeChange, finColors = finColors)
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
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp), tint = finColors.textSub) },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, "Suche löschen", Modifier.size(16.dp), tint = finColors.textSub)
                    }
                }
            },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f).widthIn(min = 120.dp),
        )
    }
}

/**
 * Der Schalter zwischen den zwei Suchfragen.
 *
 * Bewusst **kein** [SideChip]: dessen farbiger Punkt heisst "so ist diese Seite im
 * Diagramm eingefaerbt", und fuer einen Suchmodus gibt es keine solche Farbe. Ein Punkt,
 * der nichts bedeutet, ist die vierte Farbbedeutung, die #119 ausschliesst. Akzentblau
 * heisst hier nur "das ist gerade gewaehlt" — die eine Bedeutung, die es haben darf.
 */
@Composable
private fun SearchModeSegment(
    mode: BreakdownSearch.Mode,
    onModeChange: (BreakdownSearch.Mode) -> Unit,
    finColors: io.github.willywonka644.fintracker.ui.theme.FinColors,
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

