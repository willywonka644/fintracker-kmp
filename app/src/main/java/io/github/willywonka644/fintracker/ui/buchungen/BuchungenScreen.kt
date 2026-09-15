package io.github.willywonka644.fintracker.ui.buchungen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.booking.BookingSearch
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.analytics.SelectablePeriod
import io.github.willywonka644.fintracker.analytics.currentMonthFilter
import io.github.willywonka644.fintracker.analytics.PeriodChoice
import io.github.willywonka644.fintracker.analytics.availableAbsolutePeriods
import io.github.willywonka644.fintracker.analytics.toFilter
import io.github.willywonka644.fintracker.analytics.customRangeFilter
import io.github.willywonka644.fintracker.analytics.generateBillingCyclePeriods
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.components.BookingDayCard
import io.github.willywonka644.fintracker.ui.components.FilterChipRow
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BuchungenScreen(
    bookings: List<Booking>,
    categories: List<Category>,
    accounts: List<Account>,
    onBookingClick: (Booking) -> Unit,
    initialAccountId: String? = null,
    /** Reports the account the list is filtered to, so the + button can prefill it (#112). */
    onSelectedAccountChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val tz = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.todayIn(tz) }

    // ── Account selection ─────────────────────────────────────────────────────
    // Keyed on initialAccountId so navigating in with a preselected account resets the filter.
    var selectedAccountId by remember(initialAccountId) { mutableStateOf(initialAccountId) }
    var showAccountMenu by remember { mutableStateOf(false) }
    // Reported rather than lifted: the filter belongs to this screen, only the value has
    // to leave it. Covers the seed from initialAccountId as well as every later switch.
    LaunchedEffect(selectedAccountId) { onSelectedAccountChange(selectedAccountId) }
    val selectedAccount = remember(selectedAccountId, accounts) {
        accounts.firstOrNull { it.id == selectedAccountId }
    }
    val isCcAccount = selectedAccount?.type == AccountType.CREDIT_CARD

    // ── CC billing period bar ─────────────────────────────────────────────────
    val ccBillingPeriods = remember(selectedAccount) {
        if (isCcAccount) generateBillingCyclePeriods(selectedAccount!!.billingStartDay ?: 18)
        else emptyList()
    }
    var ccBillingPeriodIdx by remember(selectedAccountId) {
        mutableIntStateOf(ccBillingPeriods.lastIndex.coerceAtLeast(0))
    }

    // ── Non-CC date preset chips ───────────────────────────────────────────────
    // Ein Typ für alle vier Bildschirme (#114), und null heißt „kein Filter" — der
    // Ausgangszustand. Vorher stand hier „Dieser Monat" vorgewählt und ließ sich nicht
    // abwählen, während dieselben Spannen in den Auswertungen anders hießen und anders
    // gerechnet wurden.
    val presetLabels = PeriodChoice.Relative.entries.map { it.label } + "Benutzerdefiniert"
    var selectedPreset by remember { mutableStateOf<PeriodChoice?>(null) }
    var customFromDate by remember { mutableStateOf<LocalDate?>(null) }
    var customToDate by remember { mutableStateOf<LocalDate?>(null) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }


    // ── Type filter chips ─────────────────────────────────────────────────────
    val typeFilters = listOf("Alle", "Ausgaben", "Einnahmen", "Daueraufträge")
    var typeFilterIndex by remember { mutableIntStateOf(0) }

    // ── Category filter ───────────────────────────────────────────────────────
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    // ── Status filter ─────────────────────────────────────────────────────────
    val statusFilters = listOf("Alle Status", "Gebucht", "Geplant")
    // Startet auf "Gebucht" (Nutzerentscheidung, 13.09.2026), wie der Desktop es schon tut: die
    // Frage beim Aufschlagen ist, was tatsaechlich durch ist. Geplante Buchungen
    // sind eine Vorschau und stehen eine Wahl weiter. Der Filterchip zeigt sich
    // dabei als aktiv -- das ist richtig, hier wird ja etwas ausgeblendet (#119).
    var statusFilterIdx by remember { mutableIntStateOf(1) }
    var showStatusMenu by remember { mutableStateOf(false) }

    val categoryMap = remember(categories) { categories.associateBy { it.name } }

    // ── Compute period-filtered bookings ──────────────────────────────────────
    val periodFiltered = remember(
        bookings.toList(), selectedAccountId, isCcAccount,
        ccBillingPeriodIdx, ccBillingPeriods, selectedPreset, customFromDate, customToDate, today
    ) {
        val base = bookings.filter { !it.deleted }
        val accountBase = if (selectedAccountId == null) base else base.filter { it.accountId == selectedAccountId }
        when {
            isCcAccount && ccBillingPeriods.isNotEmpty() -> {
                val cp = ccBillingPeriods.getOrNull(ccBillingPeriodIdx)
                if (cp != null) {
                    val from = cp.filter.fromInclusive ?: return@remember accountBase
                    val to = cp.filter.toExclusive ?: return@remember accountBase
                    accountBase.filter { b -> val t = b.effectiveDate ?: b.timestamp; t >= from && t < to }
                } else accountBase
            }
            else -> {
                val filter = if (selectedPreset is PeriodChoice.Custom &&
                    customFromDate != null && customToDate != null
                ) {
                    customRangeFilter(customFromDate!!, customToDate!!, tz)
                } else {
                    selectedPreset.toFilter(tz, today)
                }
                val from = filter.fromInclusive
                val to = filter.toExclusive
                accountBase.filter { b ->
                    val t = b.effectiveDate ?: b.timestamp
                    (from == null || t >= from) && (to == null || t < to)
                }
            }
        }
    }

    // Suche nach Bezeichnung oder Kategorie (#137). Ein Feld fuer beides — die Frage im
    // Kopf ist nicht getrennt, und wer erst waehlen muss, wonach er sucht, weiss die
    // Antwort meist schon.
    var searchQuery by remember { mutableStateOf("") }

    // ── Apply type / category / status filters ─────────────────────────────────
    val displayFiltered = remember(periodFiltered, typeFilterIndex, selectedCategoryName, statusFilterIdx, searchQuery) {
        var result = periodFiltered
        result = when (typeFilterIndex) {
            1 -> result.filter { it.amount < 0 }
            2 -> result.filter { it.amount > 0 }
            3 -> result.filter { it.recurringRuleId != null }
            else -> result
        }
        if (selectedCategoryName != null) {
            result = result.filter { b ->
                (b.category?.trim()?.takeIf { it.isNotBlank() } ?: "Ohne Kategorie") == selectedCategoryName
            }
        }
        result = when (statusFilterIdx) {
            1 -> result.filter { it.status == BookingStatus.POSTED }
            2 -> result.filter { it.status == BookingStatus.SCHEDULED }
            else -> result
        }
        // Die Suche sitzt am Ende der Kette: sie schraenkt ein, was die Filter
        // uebrig gelassen haben, statt an ihnen vorbei zu suchen. Sonst zeigte ein
        // Treffer eine Buchung, die der Statusfilter darueber gerade ausschliesst.
        BookingSearch.filter(result, searchQuery)
    }

    val zone = ZoneId.systemDefault()
    val grouped = remember(displayFiltered) {
        displayFiltered
            .sortedByDescending { it.effectiveDate ?: it.timestamp }
            .groupBy { b ->
                java.time.Instant.ofEpochMilli(b.effectiveDate ?: b.timestamp).atZone(zone).toLocalDate()
            }
            .entries.toList()
    }

    val dateGroupFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY) }
    val shortDateFormatter = remember { DateTimeFormatter.ofPattern("dd. MMM", Locale.GERMANY) }

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 100.dp),
    ) {
        // Header — title and count only; the account moved down into the filter stack
        item(key = "header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Buchungen", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "${displayFiltered.size} Buchung${if (displayFiltered.size != 1) "en" else ""}" +
                        if (searchQuery.isNotBlank()) " für „${searchQuery.trim()}“" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = finColors.textSub,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Bezeichnung oder Kategorie suchen…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (searchQuery.isEmpty()) null else {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Suche löschen", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Konto
        if (accounts.isNotEmpty()) {
            item(key = "f_konto") {
                FilterBlock(label = "Konto", active = selectedAccountId != null) {
                    Box {
                        FilterDropdownButton(
                            text = selectedAccount?.name ?: "Alle Konten",
                            active = selectedAccountId != null,
                            onClick = { showAccountMenu = true },
                        )
                        DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Alle Konten") },
                                onClick = { selectedAccountId = null; showAccountMenu = false },
                                trailingIcon = if (selectedAccountId == null) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = { selectedAccountId = acc.id; showAccountMenu = false },
                                    trailingIcon = if (selectedAccountId == acc.id) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Zeitraum — billing cycle bar for credit cards, presets for everything else.
        // Stays neutral: unlike the others this filter has no "Alle" state, something
        // is always picked. Only a hand-set range is worth flagging.
        item(key = "f_zeitraum") {
            FilterBlock(
                label = "Zeitraum",
                active = !(isCcAccount && ccBillingPeriods.isNotEmpty()) && selectedPreset != null,
            ) {
                if (isCcAccount && ccBillingPeriods.isNotEmpty()) {
                    BillingPeriodBar(
                        periods = ccBillingPeriods,
                        selectedIdx = ccBillingPeriodIdx,
                        onSelect = { ccBillingPeriodIdx = it },
                    )
                } else {
                    val absolutePeriods = remember(bookings.toList()) {
                        availableAbsolutePeriods(bookings, tz)
                    }
                    val selectedIdx = when (val p = selectedPreset) {
                        is PeriodChoice.Relative -> PeriodChoice.Relative.entries.indexOf(p)
                        is PeriodChoice.Custom -> presetLabels.lastIndex
                        else -> -1
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChipRow(
                            items = presetLabels,
                            // -1 markiert keinen Chip: kein Filter, alles wird gezeigt.
                            selectedIndex = selectedIdx,
                            onSelect = { idx ->
                                selectedPreset = when {
                                    idx == presetLabels.lastIndex ->
                                        PeriodChoice.Custom(customFromDate ?: today, customToDate ?: today)
                                    idx == selectedIdx -> null
                                    else -> PeriodChoice.Relative.entries[idx]
                                }
                            },
                        )
                        if (absolutePeriods.isNotEmpty()) {
                            var menuOpen by remember { mutableStateOf(false) }
                            val chosen = selectedPreset
                                ?.takeIf { it is PeriodChoice.Quarter || it is PeriodChoice.Year }
                            Box {
                                FilterDropdownButton(
                                    text = chosen?.label ?: "Quartal / Jahr",
                                    active = chosen != null,
                                    onClick = { menuOpen = true },
                                )
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (chosen != null) {
                                        DropdownMenuItem(
                                            text = { Text("Auswahl aufheben") },
                                            onClick = { selectedPreset = null; menuOpen = false },
                                        )
                                    }
                                    absolutePeriods.forEach { p ->
                                        DropdownMenuItem(
                                            text = { Text(p.label) },
                                            onClick = { selectedPreset = p; menuOpen = false },
                                        )
                                    }
                                }
                            }
                        }
                        if (selectedPreset is PeriodChoice.Custom) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = { showFromPicker = true }, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = customFromDate?.let { d ->
                                            "${d.dayOfMonth.toString().padStart(2, '0')}.${d.monthNumber.toString().padStart(2, '0')}.${d.year}"
                                        } ?: "Von",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                OutlinedButton(onClick = { showToPicker = true }, modifier = Modifier.weight(1f)) {
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
            }
        }

        // Art
        item(key = "f_art") {
            FilterBlock(label = "Art", active = typeFilterIndex != 0) {
                FilterChipRow(
                    items = typeFilters,
                    selectedIndex = typeFilterIndex,
                    onSelect = { typeFilterIndex = it },
                )
            }
        }

        // Kategorie + Status — two half-width blocks on one line
        item(key = "f_kategorie_status") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterBlock(
                    label = "Kategorie",
                    active = selectedCategoryName != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Box {
                        FilterDropdownButton(
                            text = selectedCategoryName ?: "Alle Kategorien",
                            active = selectedCategoryName != null,
                            onClick = { showCategoryMenu = true },
                        )
                        DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Alle Kategorien") },
                                onClick = { selectedCategoryName = null; showCategoryMenu = false },
                                trailingIcon = if (selectedCategoryName == null) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                            categories.sortedBy { it.name }.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = { selectedCategoryName = cat.name; showCategoryMenu = false },
                                    trailingIcon = if (selectedCategoryName == cat.name) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                            // Bookings without a category are matched via the "Ohne Kategorie"
                            // pseudo-name in the filter — offer it in the dropdown too.
                            DropdownMenuItem(
                                text = { Text("Ohne Kategorie") },
                                onClick = { selectedCategoryName = "Ohne Kategorie"; showCategoryMenu = false },
                                trailingIcon = if (selectedCategoryName == "Ohne Kategorie") {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }
                FilterBlock(
                    label = "Status",
                    active = statusFilterIdx != 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Box {
                        FilterDropdownButton(
                            text = statusFilters[statusFilterIdx],
                            active = statusFilterIdx != 0,
                            onClick = { showStatusMenu = true },
                        )
                        DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                            statusFilters.forEachIndexed { idx, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { statusFilterIdx = idx; showStatusMenu = false },
                                    trailingIcon = if (idx == statusFilterIdx) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (displayFiltered.isEmpty()) {
            item(key = "empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Keine Buchungen für diese Auswahl",
                        style = MaterialTheme.typography.bodyMedium,
                        color = finColors.textSub,
                    )
                }
            }
        } else {
            grouped.forEach { (date, dayBookings) ->
                stickyHeader(key = "hdr_$date") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        Text(
                            text = date.format(dateGroupFormatter),
                            style = MaterialTheme.typography.labelMedium,
                            color = finColors.textSub,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
                item(key = "grp_$date") {
                    BookingDayCard(
                        bookings = dayBookings,
                        categoryMap = categoryMap,
                        shortDateFormatter = shortDateFormatter,
                        onOpen = onBookingClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun BillingPeriodBar(
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
                text = periods.getOrNull(selectedIdx)?.label ?: "Abrechnungszeitraum wählen",
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

/**
 * One labelled filter block: a small caption over whatever does the filtering.
 *
 * The caption carries the accent while the filter is narrowing the list. Colour here
 * means "this one is active", never "this is the category filter" — hue is already
 * spoken for three times over (income/expense, the selected-chip blue, and the nine
 * category colours that show up in the very list below), so a fourth code would
 * mislead rather than separate. The captions do the separating.
 */
@Composable
private fun FilterBlock(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val finColors = FinTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label.uppercase(Locale.GERMAN),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else finColors.textSub,
        )
        content()
    }
}

/** Full-width dropdown trigger for a filter block; picks up the accent border when active. */
@Composable
private fun FilterDropdownButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
    }
}
