package io.github.willywonka644.fintracker.ui.booking

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.analytics.currentBillingCyclePeriod
import io.github.willywonka644.fintracker.analytics.filterBookingsForAccount
import io.github.willywonka644.fintracker.ui.account.displayName
import io.github.willywonka644.fintracker.ui.common.DesktopDatePickerField
import io.github.willywonka644.fintracker.ui.components.DesktopMoneyText
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.analytics.SelectablePeriod
import io.github.willywonka644.fintracker.analytics.PeriodChoice
import io.github.willywonka644.fintracker.analytics.availableAbsolutePeriods
import io.github.willywonka644.fintracker.analytics.toFilter
import io.github.willywonka644.fintracker.analytics.generateBillingCyclePeriods
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.category.Category
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
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import io.github.willywonka644.fintracker.booking.BookingSearch

/**
 * The column skeleton of the list, in one place (#125).
 *
 * Three parts have to agree on it — the column header, the day heading with its
 * total, and the rows — and they agreed by coincidence before. "Datum" and
 * "Status" are gone from it: the date is now the heading of its day, and a status
 * that reads "Gebucht" in 580 of 588 rows says nothing. What is left of the status
 * is the "Geplant" pill, which sits in the description where the other badges are.
 */
private const val WEIGHT_DESCRIPTION = 4f
private const val WEIGHT_ACCOUNT = 1.5f
private const val WEIGHT_CATEGORY = 1.5f
private const val WEIGHT_AMOUNT = 1.2f
private const val WEIGHT_ACTIONS = 0.7f

/** "Freitag, 4. September 2026" — the year stays because a filtered range may span two. */
private val dayHeadingFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)

private fun LocalDate.toGermanDayHeading(): String =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth).format(dayHeadingFormatter)

private fun BookingStatus.displayName() = when (this) {
    BookingStatus.POSTED    -> "Gebucht"
    BookingStatus.SCHEDULED -> "Geplant"
}

/**
 * Semantics tags for UI tests — see [io.github.willywonka644.fintracker.ui.reconciliation.ReconciliationTestTags].
 * Only the nodes the tests need to address unambiguously; booking rows are found
 * by their description text.
 */
object BookingListTestTags {
    const val SEARCH = "booking_list_search"
    const val ACCOUNT_HEADER = "booking_account_header"
    const val ADD_BUTTON = "booking_add"
    const val BALANCE = "booking_balance"
    const val EMPTY_STATE = "booking_empty"

    /** "Alle Konten" entry of the header dropdown. */
    const val ACCOUNT_OPTION_ALL = "booking_account_option_all"

    /**
     * One dropdown entry per account. Tagged per id because the account name
     * also appears in the rows' account column whenever no account is selected.
     */
    fun accountOption(accountId: String) = "booking_account_option_$accountId"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookingListScreen(
    bookings: List<Booking>,
    categories: List<Category>,
    onAddClick: () -> Unit,
    onEditBooking: (Booking) -> Unit,
    onDeleteBooking: (Booking) -> Unit,
    /** Ein Klick auf die Zeile oeffnet die Ansicht (#129) — die Links bleiben der schnelle Weg. */
    onOpenBooking: (Booking) -> Unit = {},
    account: Account? = null,
    accounts: List<Account> = emptyList(),
    onAccountSelected: (Account?) -> Unit = {},
) {
    // Sorted by the date the row shows, not by when it was typed in. The list groups
    // by that date now, and a booking whose Wertstellung differs from its entry time
    // would otherwise open a second heading for a day that had already passed.
    val sorted = bookings.sortedByDescending { it.effectiveDate ?: it.timestamp }

    // ── Filter state ──────────────────────────────────────────────────────────
    var fromDateText by remember { mutableStateOf("") }
    var toDateText   by remember { mutableStateOf("") }
    var fromDate     by remember { mutableStateOf<LocalDate?>(null) }
    var toDate       by remember { mutableStateOf<LocalDate?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedStatus   by remember { mutableStateOf<BookingStatus?>(BookingStatus.POSTED) }
    var selectedPreset   by remember { mutableStateOf<PeriodChoice?>(null) }
    var showCustomPicker by remember { mutableStateOf(false) }
    var selectedBillingPeriod by remember { mutableStateOf<SelectablePeriod?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }
    // Suche nach Bezeichnung oder Kategorie (#137). Ein Feld fuer beides — die Frage im
    // Kopf ist nicht getrennt, und wer erst waehlen muss, wonach er sucht, weiss die
    // Antwort meist schon.
    var searchQuery by remember { mutableStateOf("") }

    val tz = TimeZone.currentSystemDefault()

    val billingPeriods = remember(account?.type, account?.billingStartDay) {
        if (account?.type == AccountType.CREDIT_CARD)
            generateBillingCyclePeriods(account.billingStartDay ?: 18).takeLast(6).reversed()
        else emptyList()
    }

    /**
     * Setzt den Zeitraum — oder hebt ihn auf, wenn derselbe nochmal gewaehlt wird (#114).
     *
     * `null` heisst "kein Filter": die Felder werden geleert und alles gezeigt. Die Spanne
     * selbst rechnet [toFilter] in shared aus, dieselbe Funktion wie in den Auswertungen;
     * vorher hatte jeder der vier Bildschirme seine eigene Rechnung und sein eigenes Enum.
     */
    fun applyPreset(preset: PeriodChoice?) {
        val alreadyOn = preset != null && preset == selectedPreset && selectedBillingPeriod == null
        val next = if (alreadyOn) null else preset
        selectedPreset = next
        selectedBillingPeriod = null
        if (next == null) {
            fromDate = null; toDate = null
            fromDateText = ""; toDateText = ""
            return
        }
        val today = Clock.System.now().toLocalDateTime(tz).date
        val filter = next.toFilter(tz, today)
        val from = filter.fromInclusive?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date }
        val to = filter.toExclusive
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date.minus(1, DateTimeUnit.DAY) }
            ?: today
        fromDate = from
        toDate = to
        fromDateText = from?.toGermanDateString() ?: ""
        toDateText = to.toGermanDateString()
    }

    fun applyBillingPeriod(period: SelectablePeriod) {
        selectedBillingPeriod = period
        selectedPreset = null
        val f = period.filter
        val from = f.fromInclusive?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date }
        val to   = f.toExclusive?.let  { Instant.fromEpochMilliseconds(it).toLocalDateTime(tz).date.minus(1, DateTimeUnit.DAY) }
        fromDate = from
        toDate = to
        fromDateText = from?.toGermanDateString() ?: ""
        toDateText   = to?.toGermanDateString()   ?: ""
    }

    val filtered = remember(sorted, fromDate, toDate, selectedCategory, selectedStatus, searchQuery) {
        val byFilters = sorted.filter { booking ->
            val t = booking.effectiveDate ?: booking.timestamp
            val afterFrom = fromDate?.let { t >= it.atStartOfDayIn(tz).toEpochMilliseconds() } ?: true
            val beforeTo  = toDate?.let  { t < it.atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L } ?: true
            val catMatch  = selectedCategory?.let { booking.category == it } ?: true
            val statMatch = selectedStatus?.let { booking.status == it } ?: true
            afterFrom && beforeTo && catMatch && statMatch
        }
        // Die Suche sitzt am Ende der Kette: sie schraenkt ein, was die Filter uebrig
        // gelassen haben, statt an ihnen vorbei zu suchen. Sonst zeigte ein Treffer eine
        // Buchung, die der Statusfilter darueber gerade ausschliesst.
        BookingSearch.filter(byFilters, searchQuery)
    }

    // One heading per day instead of the date in every row (#125) — the same shape
    // Android has had since #111. `filtered` is already ordered by that date, so
    // groupBy keeps the days in order and each day appears exactly once.
    val grouped = remember(filtered, tz) {
        filtered.groupBy {
            Instant.fromEpochMilliseconds(it.effectiveDate ?: it.timestamp).toLocalDateTime(tz).date
        }
    }

    val isFiltered = fromDate != null || toDate != null || selectedCategory != null ||
        selectedStatus != null || searchQuery.isNotBlank()

    // The status filter starts at POSTED, so on its own it must not count as the
    // user having narrowed anything — otherwise the empty list always blames the
    // filters and "Noch keine Buchungen" can never appear. The clear-filters
    // button still keys off [isFiltered] so it stays reachable for the status.
    val hasNarrowedResults = fromDate != null || toDate != null || selectedCategory != null ||
        searchQuery.isNotBlank() ||
        (selectedStatus != null && selectedStatus != BookingStatus.POSTED)

    val allBookingsForBalance = remember(bookings) { bookings.filter { it.status == BookingStatus.POSTED } }
    val accountBalance = remember(allBookingsForBalance, account) {
        if (account == null) null
        else if (account.type == AccountType.CREDIT_CARD) {
            val period = currentBillingCyclePeriod(account.billingStartDay ?: 18)
            filterBookingsForAccount(allBookingsForBalance, account.id, period).sumOf { it.amount }
        } else {
            allBookingsForBalance.filter { it.accountId == account.id }.sumOf { it.amount }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Account header ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .clickable { showAccountMenu = true }
                        .testTag(BookingListTestTags.ACCOUNT_HEADER),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                account?.name ?: "Alle Konten",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(22.dp), tint = FinTheme.colors.textSub)
                        }
                        Text(
                            account?.type?.displayName() ?: "Alle Buchungen",
                            style = MaterialTheme.typography.bodySmall,
                            color = FinTheme.colors.textSub,
                        )
                    }
                }
                DropdownMenu(
                    expanded = showAccountMenu,
                    onDismissRequest = { showAccountMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Alle Konten", style = MaterialTheme.typography.bodyMedium)
                                Text("Alle Buchungen", style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textSub)
                            }
                        },
                        onClick = { onAccountSelected(null); showAccountMenu = false },
                        modifier = Modifier.testTag(BookingListTestTags.ACCOUNT_OPTION_ALL),
                    )
                    HorizontalDivider()
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(acc.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(acc.type.displayName(), style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textSub)
                                }
                            },
                            onClick = { onAccountSelected(acc); showAccountMenu = false },
                            modifier = Modifier.testTag(BookingListTestTags.accountOption(acc.id)),
                        )
                    }
                }
            }
            if (accountBalance != null) {
                DesktopMoneyText(
                    amount = accountBalance,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag(BookingListTestTags.BALANCE),
                )
            }
            if (account != null) {
                Button(
                    onClick = onAddClick,
                    modifier = Modifier.height(36.dp).testTag(BookingListTestTags.ADD_BUTTON),
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Buchung", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

        // ── Date preset bar ───────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Bezeichnung oder Kategorie suchen…", style = MaterialTheme.typography.bodySmall) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(16.dp), tint = FinTheme.colors.textSub) },
            trailingIcon = if (searchQuery.isEmpty()) null else {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, "Suche löschen", Modifier.size(16.dp), tint = FinTheme.colors.textSub)
                    }
                }
            },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                .testTag(BookingListTestTags.SEARCH),
        )

        DatePresetBar(
            selectedPreset = selectedPreset,
            absolutePeriods = remember(bookings) { availableAbsolutePeriods(bookings, tz) },
            onPresetSelected = { applyPreset(it) },
            onCustomRequested = { showCustomPicker = true },
        )

        if (billingPeriods.isNotEmpty()) {
            BillingPeriodBar(
                periods = billingPeriods,
                selectedPeriod = selectedBillingPeriod,
                onPeriodSelected = { applyBillingPeriod(it) },
            )
        }

        if (showCustomPicker) {
            CustomRangePicker(
                initialFrom = fromDateText,
                initialTo = toDateText,
                onConfirm = { f, t ->
                    fromDate = f; toDate = t
                    fromDateText = f?.toGermanDateString() ?: ""; toDateText = t?.toGermanDateString() ?: ""
                    showCustomPicker = false
                },
                onDismiss = {
                    showCustomPicker = false
                    if (fromDate == null && toDate == null) selectedPreset = null
                },
            )
        }

        // ── Filter bar ────────────────────────────────────────────────────────
        FilterBar(
            fromDateText = fromDateText,
            onFromDateTextChange = { raw ->
                fromDateText = raw
                fromDate = parseGermanDate(raw)
                selectedPreset = null; selectedBillingPeriod = null
            },
            toDateText = toDateText,
            onToDateTextChange = { raw ->
                toDateText = raw
                toDate = parseGermanDate(raw)
                selectedPreset = null; selectedBillingPeriod = null
            },
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            selectedStatus = selectedStatus,
            onStatusSelected = { selectedStatus = it },
            isFiltered = isFiltered,
            onClearFilters = {
                fromDateText = ""; toDateText = ""
                fromDate = null; toDate = null
                selectedCategory = null; selectedStatus = null
                selectedPreset = null; selectedBillingPeriod = null
            },
        )

        HorizontalDivider()
        BookingListHeader(showAccountColumn = account == null)
        HorizontalDivider()

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (hasNarrowedResults) "Keine Buchungen entsprechen den aktuellen Filtern."
                    else "Noch keine Buchungen",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(BookingListTestTags.EMPTY_STATE),
                )
            }
        } else {
            val lazyListState = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (date, dayBookings) ->
                        stickyHeader(key = "day_$date") {
                            BookingDayHeader(
                                date = date,
                                // The total of what is on screen, not of the day: with the
                                // status filter on "Gebucht" a planned booking is not part
                                // of the rows underneath, so counting it would leave a sum
                                // that cannot be recomputed from what is visible.
                                total = dayBookings.sumOf { it.amount },
                                showAccountColumn = account == null,
                            )
                        }
                        items(dayBookings, key = { it.id }) { booking ->
                            BookingRow(
                                booking = booking,
                                categoryName = categories
                                    .firstOrNull { it.id == booking.category || it.name == booking.category }
                                    ?.name ?: booking.category.orEmpty(),
                                accountName = if (account == null)
                                    accounts.firstOrNull { it.id == booking.accountId }?.name
                                else null,
                                onEdit = { onEditBooking(booking) },
                                onDelete = { onDeleteBooking(booking) },
                                onOpen = { onOpenBooking(booking) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(lazyListState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

// ── Date preset bar composable ────────────────────────────────────────────────

@Composable
private fun DatePresetBar(
    selectedPreset: PeriodChoice?,
    absolutePeriods: List<PeriodChoice>,
    onPresetSelected: (PeriodChoice) -> Unit,
    onCustomRequested: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Zeitraum:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Dieselbe Aufteilung wie in den Auswertungen (#114): relative Spannen als Chips,
        // absolute im Menue daneben, damit die Reihe nicht mit jedem Jahr waechst.
        PeriodChoice.Relative.entries.forEach { preset ->
            FilterChip(
                selected = selectedPreset == preset,
                onClick = { onPresetSelected(preset) },
                label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
            )
        }
        if (absolutePeriods.isNotEmpty()) {
            var menuOpen by remember { mutableStateOf(false) }
            val chosen = selectedPreset?.takeIf { it is PeriodChoice.Quarter || it is PeriodChoice.Year }
            Box {
                FilterChip(
                    selected = chosen != null,
                    onClick = { menuOpen = true },
                    label = {
                        Text(
                            chosen?.label ?: "Quartal / Jahr",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp)) },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Ohne diesen Eintrag kommt man aus einer Quartalswahl nur heraus,
                    // indem man einen anderen Filter waehlt und den dann abwaehlt.
                    if (chosen != null) {
                        DropdownMenuItem(
                            text = { Text("Auswahl aufheben") },
                            onClick = { onPresetSelected(chosen); menuOpen = false },
                        )
                        HorizontalDivider()
                    }
                    absolutePeriods.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            onClick = { onPresetSelected(p); menuOpen = false },
                        )
                    }
                }
            }
        }
        FilterChip(
            selected = selectedPreset is PeriodChoice.Custom,
            onClick = onCustomRequested,
            label = { Text("Benutzerdefiniert", style = MaterialTheme.typography.labelSmall) },
        )
    }
}

// ── Billing period bar (Kreditkarte only) ─────────────────────────────────────

@Composable
private fun BillingPeriodBar(
    periods: List<SelectablePeriod>,
    selectedPeriod: SelectablePeriod?,
    onPeriodSelected: (SelectablePeriod) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
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
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
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

// ── Custom range date picker dialog ───────────────────────────────────────────

@Composable
private fun CustomRangePicker(
    initialFrom: String,
    initialTo: String,
    onConfirm: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var fromText by remember { mutableStateOf(initialFrom) }
    var toText   by remember { mutableStateOf(initialTo) }
    val parsedFrom = parseGermanDate(fromText)
    val parsedTo   = parseGermanDate(toText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Benutzerdefinierter Zeitraum") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopDatePickerField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = "Von",
                    placeholder = "TT.MM.JJJJ",
                    isError = fromText.isNotEmpty() && parsedFrom == null,
                    textStyle = MaterialTheme.typography.bodySmall,
                    selectedDate = parsedFrom,
                )
                DesktopDatePickerField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = "Bis",
                    placeholder = "TT.MM.JJJJ",
                    isError = toText.isNotEmpty() && parsedTo == null,
                    textStyle = MaterialTheme.typography.bodySmall,
                    selectedDate = parsedTo,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parsedFrom, parsedTo) }) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

// ── Filter bar composable ─────────────────────────────────────────────────────

@Composable
private fun FilterBar(
    fromDateText: String,
    onFromDateTextChange: (String) -> Unit,
    toDateText: String,
    onToDateTextChange: (String) -> Unit,
    categories: List<Category>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    selectedStatus: BookingStatus?,
    onStatusSelected: (BookingStatus?) -> Unit,
    isFiltered: Boolean,
    onClearFilters: () -> Unit,
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var statusExpanded   by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            DesktopDatePickerField(
                value = fromDateText,
                onValueChange = onFromDateTextChange,
                label = "Von",
                    placeholder = "TT.MM.JJJJ",
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
                isError = fromDateText.isNotEmpty() && parseGermanDate(fromDateText) == null,
            )
            DesktopDatePickerField(
                value = toDateText,
                onValueChange = onToDateTextChange,
                label = "Bis",
                    placeholder = "TT.MM.JJJJ",
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall,
                isError = toDateText.isNotEmpty() && parseGermanDate(toDateText) == null,
            )
            Box {
                OutlinedButton(
                    onClick = { categoryExpanded = true },
                    modifier = Modifier.widthIn(min = 140.dp),
                ) {
                    Text(
                        selectedCategory ?: "Alle Kategorien",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Alle Kategorien") },
                        onClick = { onCategorySelected(null); categoryExpanded = false },
                    )
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = { onCategorySelected(cat.name); categoryExpanded = false },
                        )
                    }
                }
            }
            Box {
                OutlinedButton(
                    onClick = { statusExpanded = true },
                    modifier = Modifier.widthIn(min = 110.dp),
                ) {
                    Text(
                        selectedStatus?.displayName() ?: "Alle Status",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                DropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Alle Status") },
                        onClick = { onStatusSelected(null); statusExpanded = false },
                    )
                    BookingStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayName()) },
                            onClick = { onStatusSelected(status); statusExpanded = false },
                        )
                    }
                }
            }
            if (isFiltered) {
                TextButton(onClick = onClearFilters) {
                    Text("Zurücksetzen", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ── List header ───────────────────────────────────────────────────────────────

@Composable
private fun BookingListHeader(showAccountColumn: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Beschreibung", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FinTheme.colors.textSub, modifier = Modifier.weight(WEIGHT_DESCRIPTION))
        if (showAccountColumn) {
            Text("Konto", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FinTheme.colors.textSub, modifier = Modifier.weight(WEIGHT_ACCOUNT))
        }
        Text("Kategorie",    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FinTheme.colors.textSub, modifier = Modifier.weight(WEIGHT_CATEGORY))
        Text("Betrag",       style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = FinTheme.colors.textSub, modifier = Modifier.weight(WEIGHT_AMOUNT), textAlign = TextAlign.End)
        Box(Modifier.weight(WEIGHT_ACTIONS))
    }
}

// ── Day heading ───────────────────────────────────────────────────────────────

/**
 * The date, once, with the total of the rows below it.
 *
 * Sticks to the top while its day scrolls past, so the answer to "which day am I
 * looking at" never leaves the window — the reason the date could be taken out of
 * the rows at all.
 */
@Composable
private fun BookingDayHeader(date: LocalDate, total: Double, showAccountColumn: Boolean) {
    val leftWeight = WEIGHT_DESCRIPTION + WEIGHT_CATEGORY + (if (showAccountColumn) WEIGHT_ACCOUNT else 0f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Zwei Schichten, und die untere ist deckend: die Ueberschrift bleibt beim
            // Scrollen stehen, und eine halbdurchsichtige Flaeche laesst die Zeilen
            // hindurchwandern — im Bild lag "Leuchtmittel Prime" im Datum. Die zweite
            // Schicht haelt den Farbton, den die Zeile vorher hatte.
            .background(MaterialTheme.colorScheme.background)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = remember(date) { date.toGermanDayHeading() },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(leftWeight),
        )
        DesktopMoneyText(
            amount = total,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
            ),
            modifier = Modifier.weight(WEIGHT_AMOUNT),
        )
        Box(Modifier.weight(WEIGHT_ACTIONS))
    }
}

// ── Booking row ───────────────────────────────────────────────────────────────

@Composable
private fun BookingRow(
    booking: Booking,
    categoryName: String,
    accountName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
) {
    val finColors = FinTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            // Die Zeile selbst fuehrt in die Ansicht (#129); "Bearb." und "Loesch."
            // bleiben daneben, weil sie mit der Maus der kuerzere Weg sind.
            .clickable(onClick = onOpen)
            .background(if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(WEIGHT_DESCRIPTION),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val title = booking.description.takeIf { it.isNotBlank() }
                ?: booking.merchantName?.takeIf { it.isNotBlank() }
                ?: "—"
            Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (booking.isVerified) {
                Icon(Icons.Default.CheckCircle, "Geprüft", tint = finColors.income, modifier = Modifier.size(14.dp))
            }
            // Only the status that says something. "Gebucht" was true of nearly every
            // row and was read in every one of them.
            if (booking.status == BookingStatus.SCHEDULED) {
                StatusPill("Geplant", MaterialTheme.colorScheme.primary)
            }
            if (booking.recurringRuleId != null && booking.installmentGroupId == null) {
                StatusPill("Dauer", MaterialTheme.colorScheme.primary)
            }
            // Beide Seiten bleiben eigene Zeilen — jede gehoert zu ihrem Konto, und eine
            // Zeile daraus zu machen liesse einen Kontostand unerklaert. Das Abzeichen sagt,
            // dass die zwei dieselbe Bewegung sind (#114).
            if (booking.source == BookingSource.TRANSFER) {
                StatusPill("Umbuchung", MaterialTheme.colorScheme.primary)
            }
        }
        if (accountName != null) {
            Text(
                accountName,
                style = MaterialTheme.typography.bodySmall,
                color = finColors.textSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(WEIGHT_ACCOUNT),
            )
        }
        Row(modifier = Modifier.weight(WEIGHT_CATEGORY), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(
                remember(categoryName) { categoryDotColor(categoryName, finColors) }
            ))
            Text(categoryName.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = finColors.textSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DesktopMoneyText(
            amount = booking.amount,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
            modifier = Modifier.weight(WEIGHT_AMOUNT),
        )
        // Two links that are almost never used but were read in every row. The column
        // keeps its width whether they show or not, so nothing moves under the pointer
        // when they appear.
        Row(Modifier.weight(WEIGHT_ACTIONS), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isHovered) {
                ClickableLabel("Bearb.", onEdit, MaterialTheme.colorScheme.primary)
                ClickableLabel("Lösch.", onDelete, FinTheme.colors.expense)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private fun categoryDotColor(name: String, finColors: io.github.willywonka644.fintracker.ui.theme.FinColors): Color =
    finColors.category(name)

@Composable
private fun ClickableLabel(text: String, onClick: () -> Unit, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ).padding(4.dp),
    )
}
