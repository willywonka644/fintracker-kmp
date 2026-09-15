package io.github.willywonka644.fintracker.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.analytics.BalanceDataPoint
import io.github.willywonka644.fintracker.analytics.CategoryBreakdownEntry
import io.github.willywonka644.fintracker.analytics.calculateBalanceOverTime
import io.github.willywonka644.fintracker.analytics.calculateCategoryBreakdown
import io.github.willywonka644.fintracker.analytics.calculateMonthlyIncomeExpense
import io.github.willywonka644.fintracker.analytics.excludeTransfers
import io.github.willywonka644.fintracker.analytics.overviewBalances
import io.github.willywonka644.fintracker.analytics.SawthoothBalanceResult
import io.github.willywonka644.fintracker.analytics.calculateSawthoothBalance
import io.github.willywonka644.fintracker.analytics.limitWarnings
import io.github.willywonka644.fintracker.ui.auswertungen.AuswertungenLineChart
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.account.AccountFormDialog
import io.github.willywonka644.fintracker.ui.account.displayName
import io.github.willywonka644.fintracker.ui.components.DesktopFinCard
import io.github.willywonka644.fintracker.ui.components.DesktopHeroCard
import io.github.willywonka644.fintracker.ui.components.DesktopKpiCard
import io.github.willywonka644.fintracker.ui.components.DesktopMoneyText
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import io.github.willywonka644.fintracker.booking.BookingSearch

/**
 * Semantics tags for UI tests.
 *
 * The account name is the only thing that distinguishes an over-limit notification
 * (#98), and the same name is on the account list behind the dialog — so the rows
 * need a handle of their own to be addressable.
 */
object DesktopDashboardTestTags {
    const val NOTIFICATIONS_BUTTON = "dashboard_notifications"

    // Sits on the number itself, not on the Badge around it: Badge does not merge
    // its children's semantics, so a tag on the container would carry no text to
    // assert against.
    const val NOTIFICATION_BADGE = "dashboard_notification_badge"
    fun limitWarning(accountId: String) = "notif_limit_$accountId"
}

@Composable
fun DesktopDashboard(
    accounts: List<Account>,
    bookings: List<Booking>,
    categories: List<Category>,
    recurringRules: List<RecurringRule> = emptyList(),
    unsyncedCount: Int = 0,
    onAccountClick: (Account) -> Unit,
    /** Ein Buchungstreffer der Suche fuehrt zur Buchung selbst, nicht nur aufs Konto (#129). */
    onOpenBooking: (Booking) -> Unit = {},
    onAccountSaved: (Account) -> Unit,
    onAccountDeleted: (Account) -> Unit,
    onSyncClick: () -> Unit = {},
    onNavigateToDauerauftraege: () -> Unit = {},
    onAddBookingClick: () -> Unit = {},
) {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val thisMonthStart = LocalDate(today.year, today.month, 1)
    val last90Start = today.minus(90, DateTimeUnit.DAY)

    var showAddAccount by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Notification data
    val unauditedCount = remember(bookings) {
        bookings.count { !it.deleted && it.status == BookingStatus.POSTED && !it.isVerified }
    }
    val sevenDaysLater = remember { today.plus(7, DateTimeUnit.DAY) }
    val dueSoonRules = remember(recurringRules) {
        recurringRules.filter { it.nextExecutionDate <= sevenDaysLater }
    }

    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val filteredAccounts = remember(accounts, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else accounts.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val filteredBookings = remember(bookings, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        // Seit #137 derselbe Matcher wie auf der Buchungen-Seite. Vorher stand hier ein
        // eigenes `contains` — zwei Suchfelder, die auf dieselbe Eingabe verschieden
        // antworten, sind schlimmer als eines weniger. Neu dabei: "luf" findet "Lüfter".
        else BookingSearch.filter(bookings.filter { !it.deleted }, searchQuery).take(50)
    }

    val postedBookings = remember(bookings) {
        bookings.filter { it.status == BookingStatus.POSTED && !it.deleted }
    }

    // Rows and total from one function, shared with Android (#101). Card rows keep their
    // billing cycle; the total takes every card in full, because debt older than the 20th
    // is still owed. The gap between them is the closed, unsettled statements.
    val balances = remember(accounts, postedBookings) {
        overviewBalances(accounts, postedBookings)
    }
    val accountBalances = balances.perAccount
    val totalBalance = balances.total

    // Needs the balances, so it cannot live with the other notification data
    // further up. Checking only that a limit exists (what this did until #98)
    // announced every credit card as over its limit, whatever it had spent.
    // Since #99 this asks every account, not only cards, and warns from 90% on.
    // Wording and threshold live in shared so the two platforms cannot drift.
    val activeLimitWarnings = remember(accounts, accountBalances) {
        limitWarnings(accounts, accountBalances)
    }
    // One entry per affected account, as on Android: the panel shows one row per
    // account, so the badge has to count rows, not categories.
    val notificationBadge =
        (if (unauditedCount > 0) 1 else 0) +
        (if (unsyncedCount > 0) 1 else 0) +
        activeLimitWarnings.size +
        (if (dueSoonRules.isNotEmpty()) 1 else 0)

    val thisMonthFromMs = thisMonthStart.atStartOfDayIn(tz).toEpochMilliseconds()
    val thisMonthBookings = remember(postedBookings, thisMonthFromMs) {
        postedBookings.filter { (it.effectiveDate ?: it.timestamp) >= thisMonthFromMs }
    }
    val thisMonthIncome = remember(thisMonthBookings) { thisMonthBookings.filter { it.source != BookingSource.RECONCILIATION }.excludeTransfers().filter { it.amount > 0 }.sumOf { it.amount } }
    val thisMonthExpense = remember(thisMonthBookings) { thisMonthBookings.filter { it.source != BookingSource.RECONCILIATION }.excludeTransfers().filter { it.amount < 0 }.sumOf { -it.amount } }

    val last90FromMs = last90Start.atStartOfDayIn(tz).toEpochMilliseconds()
    val chartBookings = remember(postedBookings, last90FromMs) {
        postedBookings.filter { (it.effectiveDate ?: it.timestamp) >= last90FromMs }
    }
    val chartBookingsNoReconciliation = remember(chartBookings) {
        chartBookings.filter { it.source != BookingSource.RECONCILIATION }
    }

    val categoryBreakdown = remember(thisMonthBookings) {
        calculateCategoryBreakdown(
            thisMonthBookings.filter {
                it.source != BookingSource.RECONCILIATION && it.amount < 0
            }
        )
    }
    val categoryMap = remember(categories) { categories.associateBy { it.name } }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Page header ───────────────────────────────────────────────────────
        // Reflows rather than squeezes (#97). In one row the title column was the
        // only elastic part, so a narrow window drove it to zero width and Compose
        // broke "Dashboard" into one character per line. Below the threshold the
        // title takes its own row and the controls wrap among themselves.
        // Same reason as in DesktopShell (#103): measured after layout rather
        // than composed against, because BoxWithConstraints here left the
        // composition redrawing without end under test.
        var headerWidthPx by remember { mutableStateOf(0) }
        val headerDensity = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerWidthPx = it.width },
        ) {
            val stacked = headerWidthPx > 0 &&
                with(headerDensity) { headerWidthPx.toDp() } < HEADER_STACK_BELOW
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardHeaderTitle(accounts.size, Modifier.fillMaxWidth())
                    DashboardHeaderControls(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        notificationBadge = notificationBadge,
                        onNotificationsClick = { showNotifications = true },
                        onAddAccountClick = { showAddAccount = true },
                        onAddBookingClick = onAddBookingClick,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DashboardHeaderTitle(accounts.size, Modifier.weight(1f))
                    DashboardHeaderControls(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        notificationBadge = notificationBadge,
                        onNotificationsClick = { showNotifications = true },
                        onAddAccountClick = { showAddAccount = true },
                        onAddBookingClick = onAddBookingClick,
                    )
                }
            }
        }

        // ── Search results (replaces body when query is active) ───────────────
        if (searchQuery.isNotBlank()) {
            if (filteredAccounts.isEmpty() && filteredBookings.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Keine Ergebnisse für \"$searchQuery\"", color = FinTheme.colors.textSub)
                }
            } else {
                if (filteredAccounts.isNotEmpty()) {
                    Text("Konten", style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textFaint)
                    Spacer(Modifier.height(4.dp))
                    filteredAccounts.forEach { acc ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onAccountClick(acc) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(acc.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                when (acc.type) {
                                    AccountType.GIRO -> "Girokonto"
                                    // Says what the figure beside it means, so the gap to
                                    // the Gesamtvermögen above is explained (#101).
                                    AccountType.CREDIT_CARD -> "Kreditkarte · laufende Abrechnung"
                                    AccountType.SPARKONTO -> "Sparkonto"
                                    AccountType.TAGESGELD -> "Tagesgeldkonto"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = FinTheme.colors.textSub,
                            )
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = FinTheme.colors.textFaint, modifier = Modifier.size(16.dp))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
                if (filteredBookings.isNotEmpty()) {
                    if (filteredAccounts.isNotEmpty()) Spacer(Modifier.height(16.dp))
                    Text("Buchungen", style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textFaint)
                    Spacer(Modifier.height(4.dp))
                    filteredBookings.forEach { booking ->
                        val accName = accountMap[booking.accountId]?.name ?: ""
                        val dateStr = Instant.fromEpochMilliseconds(booking.timestamp).toLocalDateTime(tz).date.let { d ->
                            "${d.dayOfMonth.toString().padStart(2,'0')}.${d.monthNumber.toString().padStart(2,'0')}.${d.year.toString().takeLast(2)}"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { searchQuery = ""; onOpenBooking(booking) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(booking.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "$accName · $dateStr${if (booking.category != null) " · ${booking.category}" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FinTheme.colors.textSub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            DesktopMoneyText(amount = booking.amount, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }

        if (searchQuery.isBlank()) {

        // ── KPI row ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().height(148.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DesktopHeroCard(
                title = "Gesamtvermögen",
                amount = totalBalance,
                // Nicht "Dieser Monat" (#114): das Gesamtvermögen ist kein Monatswert,
                // es ist der Stand über alle Konten. Die beiden Karten daneben tragen den
                // Zusatz zu Recht — sie sind Monatszahlen, diese nicht.
                subtitle = "alle Konten",
                modifier = Modifier.weight(2f).fillMaxHeight(),
            )
            DesktopKpiCard(
                title = "Einnahmen",
                amount = thisMonthIncome,
                isIncome = true,
                subtitle = "Dieser Monat",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            DesktopKpiCard(
                title = "Ausgaben",
                amount = thisMonthExpense,
                isIncome = false,
                subtitle = "Dieser Monat",
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        // ── Two-column body ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Left: Konten list
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Konten", style = MaterialTheme.typography.titleMedium)
                }
                if (accounts.isEmpty()) {
                    DesktopFinCard(modifier = Modifier.fillMaxWidth(), onClick = { showAddAccount = true }) {
                        Text(
                            "Konto hinzufügen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    DesktopFinCard(modifier = Modifier.fillMaxWidth()) {
                        accounts.forEachIndexed { index, account ->
                            DashboardAccountRow(
                                account = account,
                                balance = accountBalances[account.id] ?: 0.0,
                                onClick = { onAccountClick(account) },
                            )
                            if (index < accounts.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }

            // Right: Charts
            Column(
                modifier = Modifier.weight(2f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardBalanceChart(
                    accounts = accounts,
                    allPostedBookings = postedBookings,
                    fromDate = last90Start,
                    toDate = today,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DashboardIncomeExpenseChart(
                        bookings = chartBookingsNoReconciliation,
                        modifier = Modifier.weight(1f),
                    )
                    DashboardCategoryDonut(
                        breakdown = categoryBreakdown,
                        categoryMap = categoryMap,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        } // end if (searchQuery.isBlank())
    } // end Column

    if (showAddAccount) {
        AccountFormDialog(
            onDismiss = { showAddAccount = false },
            onSave = { account ->
                showAddAccount = false
                onAccountSaved(account)
            },
        )
    }

    if (showNotifications) {
        AlertDialog(
            onDismissRequest = { showNotifications = false },
            title = { Text("Benachrichtigungen") },
            text = {
                if (notificationBadge == 0) {
                    Text("Keine Benachrichtigungen", color = FinTheme.colors.textSub)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        if (unauditedCount > 0) {
                            DashboardNotificationRow(
                                title = "Ungeprüfte Buchungen",
                                subtitle = "$unauditedCount Buchung${if (unauditedCount != 1) "en" else ""}",
                                onClick = { showNotifications = false },
                            )
                        }
                        if (unsyncedCount > 0) {
                            DashboardNotificationRow(
                                title = "Nicht synchronisiert",
                                subtitle = "$unsyncedCount Änderung${if (unsyncedCount != 1) "en" else ""}",
                                onClick = { showNotifications = false; onSyncClick() },
                            )
                        }
                        activeLimitWarnings.forEach { warning ->
                            val account = accounts.first { it.id == warning.accountId }
                            DashboardNotificationRow(
                                title = warning.title,
                                subtitle = warning.accountName,
                                onClick = { showNotifications = false; onAccountClick(account) },
                                modifier = Modifier.testTag(
                                    DesktopDashboardTestTags.limitWarning(warning.accountId)
                                ),
                            )
                        }
                        if (dueSoonRules.isNotEmpty()) {
                            DashboardNotificationRow(
                                title = "Fällige Daueraufträge",
                                subtitle = "${dueSoonRules.size} Regel${if (dueSoonRules.size != 1) "n" else ""} in 7 Tagen",
                                onClick = { showNotifications = false; onNavigateToDauerauftraege() },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotifications = false }) { Text("Schließen") }
            },
        )
    }
}

/** Content width below which the dashboard header stacks instead of squeezing. */
private val HEADER_STACK_BELOW = 780.dp

@Composable
private fun DashboardHeaderTitle(accountCount: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // maxLines is the backstop: whatever width the title ends up with, it
        // shortens with an ellipsis instead of collapsing into a letter column.
        Text(
            "Dashboard",
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$accountCount ${if (accountCount == 1) "Konto" else "Konten"}",
            style = MaterialTheme.typography.bodyMedium,
            color = FinTheme.colors.textSub,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardHeaderControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    notificationBadge: Int,
    onNotificationsClick: () -> Unit,
    onAddAccountClick: () -> Unit,
    onAddBookingClick: () -> Unit,
) {
    // FlowRow has no cross-axis alignment parameter, so the shorter controls are
    // centred one by one against the search field they share a row with.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Suchen…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            // widthIn, not width: a fixed 240 dp overflows a wrap row narrower
            // than that instead of shrinking with it.
            modifier = Modifier.widthIn(max = 240.dp),
        )
        BadgedBox(
            badge = {
                if (notificationBadge > 0) {
                    Badge {
                        Text(
                            "$notificationBadge",
                            modifier = Modifier.testTag(
                                DesktopDashboardTestTags.NOTIFICATION_BADGE
                            ),
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.CenterVertically),
        ) {
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.testTag(DesktopDashboardTestTags.NOTIFICATIONS_BUTTON),
            ) {
                Icon(Icons.Outlined.Notifications, "Benachrichtigungen")
            }
        }
        // Gefüllt statt umrandet (#114): das sind die zwei Aktionen der Kopfzeile, und
        // umrandet gingen sie neben der Suchleiste unter. Gleiche Form wie der
        // "Buchung"-Knopf im Buchungen-Tab, damit dieselbe Aktion gleich aussieht.
        Button(
            onClick = onAddAccountClick,
            modifier = Modifier.height(36.dp).align(Alignment.CenterVertically),
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Konto hinzufügen", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Button(
            onClick = onAddBookingClick,
            modifier = Modifier.height(36.dp).align(Alignment.CenterVertically),
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Buchung hinzufügen", style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun DashboardNotificationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = finColors.textSub)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = finColors.textFaint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DashboardAccountRow(account: Account, balance: Double, onClick: () -> Unit) {
    val finColors = FinTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(account.type.displayName(), style = MaterialTheme.typography.labelSmall, color = finColors.textSub)
        }
        DesktopMoneyText(amount = balance, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Balance over time chart ───────────────────────────────────────────────────

@Composable
private fun DashboardBalanceChart(
    accounts: List<Account>,
    allPostedBookings: List<Booking>,
    fromDate: LocalDate,
    toDate: LocalDate,
) {
    val tz = TimeZone.currentSystemDefault()
    val fromMs = fromDate.atStartOfDayIn(tz).toEpochMilliseconds()
    val toMs = toDate.atStartOfDayIn(tz).toEpochMilliseconds() + 86_400_000L

    val dailyData: List<BalanceDataPoint> = remember(accounts, allPostedBookings, fromMs, toMs) {
        if (allPostedBookings.isEmpty() || accounts.isEmpty()) return@remember emptyList()

        // Per-account daily balance series:
        // - CC accounts: sawtooth (balance resets to 0 at each billing cycle boundary, matching
        //   the Gesamtvermögen widget which also uses only the current billing cycle for CC)
        // - Normal accounts: full-history pre-window starting balance + 90-day running sum
        val accountSeries = accounts.mapNotNull { acc ->
            val accBookings = allPostedBookings.filter { it.accountId == acc.id }
            if (acc.type == AccountType.CREDIT_CARD) {
                val pts = calculateSawthoothBalance(
                    bookings = accBookings,
                    billingStartDay = acc.billingStartDay ?: 18,
                    fromDate = fromDate,
                    toDate = toDate,
                    tz = tz,
                ).points
                if (pts.isEmpty()) null else pts
            } else {
                val preWindowBalance = accBookings.sumOf { b ->
                    val t = b.effectiveDate ?: b.timestamp
                    if (t < fromMs) b.amount else 0.0
                }
                val windowBookings = accBookings.filter { b ->
                    val t = b.effectiveDate ?: b.timestamp
                    t >= fromMs && t < toMs
                }
                val pts = calculateBalanceOverTime(windowBookings, preWindowBalance, fromMs, toMs)
                when {
                    pts.isNotEmpty() -> pts
                    // Account has a balance but no bookings in window: anchor at period start
                    // so the balance is included at every date via forward-fill in the merge step
                    preWindowBalance != 0.0 -> listOf(BalanceDataPoint(fromMs, preWindowBalance))
                    else -> null
                }
            }
        }

        if (accountSeries.isEmpty()) return@remember emptyList()

        // Merge per-account series: union of all dates, each account forward-filled
        val allDates = accountSeries.flatMap { series -> series.map { it.dateMillis } }.toSet().sorted()
        allDates.map { dateMs ->
            val total = accountSeries.sumOf { series ->
                series.lastOrNull { it.dateMillis <= dateMs }?.balance ?: 0.0
            }
            BalanceDataPoint(dateMs, total)
        }
    }

    if (dailyData.isEmpty()) return

    DesktopFinCard {
        Text("Saldoverlauf", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Letzte 90 Tage", style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textSub)
        Spacer(Modifier.height(8.dp))
        AuswertungenLineChart(dailyData = dailyData, color = MaterialTheme.colorScheme.primary, height = 120)
    }
}

// ── Income vs Expense bar chart ───────────────────────────────────────────────

@Composable
private fun DashboardIncomeExpenseChart(bookings: List<Booking>, modifier: Modifier = Modifier) {
    val monthlyData = remember(bookings) { calculateMonthlyIncomeExpense(bookings) }
    if (monthlyData.isEmpty()) return

    val finColors = FinTheme.colors
    val n = monthlyData.size
    val maxVal = monthlyData.maxOf { maxOf(it.income, it.expense) }.coerceAtLeast(1.0).toFloat()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val density = LocalDensity.current

    var barHoveredBar by remember(monthlyData) { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var barHoverPos by remember { mutableStateOf(Offset.Zero) }
    var barCanvasW by remember { mutableStateOf(0f) }

    DesktopFinCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Einnahmen / Ausgaben", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Letzte 90 Tage", style = MaterialTheme.typography.labelSmall, color = finColors.textSub)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendDot("Ein.", finColors.income)
                LegendDot("Aus.", finColors.expense)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(100.dp)) {
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
                                        val gp = gw * 0.1f
                                        val bg = 2f
                                        val bw = ((gw - 2 * gp - bg) / 2).coerceAtLeast(2f)
                                        val groupIdx = (pos.x / gw).toInt().coerceIn(0, n - 1)
                                        val lx = pos.x - groupIdx * gw
                                        barHoveredBar = when {
                                            lx >= gp && lx < gp + bw -> Pair(groupIdx, true)
                                            lx >= gp + bw + bg && lx < gp + 2 * bw + bg -> Pair(groupIdx, false)
                                            else -> null
                                        }
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
                    val incHl = barHoveredBar?.let { it.first == i && it.second } == true
                    val expHl = barHoveredBar?.let { it.first == i && !it.second } == true
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
                    style = MaterialTheme.typography.labelSmall,
                    color = FinTheme.colors.textFaint,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Category donut chart ──────────────────────────────────────────────────────

@Composable
private fun DashboardCategoryDonut(
    breakdown: List<CategoryBreakdownEntry>,
    categoryMap: Map<String, Category>,
    modifier: Modifier = Modifier,
) {
    DesktopFinCard(modifier = modifier) {
        Text("Ausgaben nach Kategorie", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Dieser Monat", style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textSub)
        Spacer(Modifier.height(8.dp))
        if (breakdown.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Keine Ausgaben", style = MaterialTheme.typography.bodySmall, color = FinTheme.colors.textSub)
            }
        } else {
            val finColors = FinTheme.colors
            val top = breakdown.take(6)
            val sweeps = top.map { (it.percentage * 360f).toFloat() }

            var donutHoveredIdx by remember(top) { mutableStateOf<Int?>(null) }
            var donutCanvasW by remember { mutableStateOf(0f) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    Canvas(
                        modifier = Modifier.size(80.dp)
                            .onSizeChanged { donutCanvasW = it.width.toFloat() }
                            .pointerInput(top) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val pos = ev.changes.firstOrNull()?.position
                                        when (ev.type) {
                                            PointerEventType.Move -> if (pos != null && donutCanvasW > 0f) {
                                                val cx = donutCanvasW / 2f
                                                val cy = donutCanvasW / 2f
                                                val sw = donutCanvasW * 0.22f
                                                val r = (donutCanvasW - sw) / 2f
                                                val dx = pos.x - cx; val dy = pos.y - cy
                                                val dist = sqrt(dx * dx + dy * dy)
                                                if (dist < r - sw / 2 || dist > r + sw / 2) {
                                                    donutHoveredIdx = null
                                                } else {
                                                    val hitAngle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + 360f) % 360f
                                                    var startAngle = 0f
                                                    donutHoveredIdx = null
                                                    for (i in top.indices) {
                                                        val sweep = sweeps[i].coerceAtLeast(0.001f)
                                                        if (hitAngle >= startAngle && hitAngle < startAngle + sweep) {
                                                            donutHoveredIdx = i; break
                                                        }
                                                        startAngle += sweep
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
                        top.forEachIndexed { i, entry ->
                            val catColor = Color(
                                categoryMap[entry.categoryName]?.color?.toInt() ?: finColors.gradStart.hashCode()
                            )
                            val isHovered = donutHoveredIdx == i
                            drawArc(
                                color = if (isHovered) catColor.copy(alpha = 0.7f) else catColor,
                                startAngle = startAngle,
                                sweepAngle = sweeps[i],
                                useCenter = false,
                                style = Stroke(width = if (isHovered) size.width * 0.27f else size.width * 0.22f),
                            )
                            startAngle += sweeps[i]
                        }
                    }
                    donutHoveredIdx?.let { idx ->
                        val entry = top.getOrNull(idx) ?: return@let
                        val catColor = Color(
                            categoryMap[entry.categoryName]?.color?.toInt() ?: finColors.gradStart.hashCode()
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Text(
                                entry.categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = catColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${(entry.percentage * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = catColor,
                            )
                            Text(
                                MoneyFormat.currency(abs(entry.amount)),
                                style = MaterialTheme.typography.labelSmall,
                                color = finColors.textSub,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    top.forEach { entry ->
                        val catColor = Color(
                            categoryMap[entry.categoryName]?.color?.toInt() ?: finColors.gradStart.hashCode()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(catColor))
                            Text(
                                entry.categoryName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${(entry.percentage * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = FinTheme.colors.textSub,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textSub)
    }
}

private fun monthShort(m: Int) = when (m) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mär"; 4 -> "Apr"
    5 -> "Mai"; 6 -> "Jun"; 7 -> "Jul"; 8 -> "Aug"
    9 -> "Sep"; 10 -> "Okt"; 11 -> "Nov"; else -> "Dez"
}
