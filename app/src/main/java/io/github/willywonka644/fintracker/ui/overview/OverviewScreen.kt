package io.github.willywonka644.fintracker.ui.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.analytics.computeLimitUsage
import io.github.willywonka644.fintracker.analytics.overviewBalances
import io.github.willywonka644.fintracker.analytics.limitWarningFor
import io.github.willywonka644.fintracker.analytics.limitWarnings
import io.github.willywonka644.fintracker.ui.components.BalanceHero
import io.github.willywonka644.fintracker.ui.components.FinCard
import io.github.willywonka644.fintracker.ui.components.MoneyText
import io.github.willywonka644.fintracker.ui.components.SectionHeader
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountOverviewScreen(
    accounts: List<Account>,
    bookings: List<Booking>,
    recurringRules: List<RecurringRule> = emptyList(),
    onAddAccountClick: () -> Unit,
    onAccountClick: (Account) -> Unit,
    onNavigateToAudit: (accountId: String) -> Unit = {},
    onNavigateToSync: () -> Unit = {},
    onNavigateToAccountDetail: (accountId: String) -> Unit = {},
    onNavigateToRecurring: (accountId: String) -> Unit = {},
    /** Ein Suchtreffer fuehrt in die Buchungsansicht (#129), nicht mehr nur aufs Konto. */
    onBookingClick: (Booking) -> Unit = {},
    unsyncedCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors

    // Rows and total come from one function, shared with the desktop (#101). The card rows
    // keep their billing cycle — that is the number to hold against the statement — but the
    // total takes every card in full: debt older than the 20th has not gone anywhere, and a
    // figure headed "Gesamtvermögen" that leaves it out flatters the picture. The two
    // therefore differ by whatever sits in closed, unsettled cycles, which is why the card
    // row says so underneath.
    val balances = remember(accounts.toList(), bookings.toList()) {
        overviewBalances(accounts, bookings)
    }
    val accountBalances = balances.perAccount
    val totalBalance = balances.total



    // ── Notification data ────────────────────────────────────────────────────
    val unauditedCount = remember(bookings.toList()) {
        bookings.count { !it.deleted && it.status == BookingStatus.POSTED && !it.isVerified }
    }
    val firstUnauditedAccountId = remember(bookings.toList(), accounts.toList()) {
        accounts.firstOrNull { acc ->
            bookings.any { !it.deleted && it.status == BookingStatus.POSTED && !it.isVerified && it.accountId == acc.id }
        }?.id
    }
    // Any account with a limit, not just cards (#99): the account deepest in the red
    // is the Girokonto, and it used to be the one this could not say anything about.
    // Wording and threshold come from shared so the desktop cannot word it differently.
    val activeLimitWarnings = remember(accounts.toList(), accountBalances) {
        limitWarnings(accounts, accountBalances)
    }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val sevenDaysLater = remember { today.plus(7, DateTimeUnit.DAY) }
    val dueSoonRules = remember(recurringRules.toList()) {
        recurringRules.filter { it.nextExecutionDate <= sevenDaysLater }
    }
    val firstDueSoonAccountId = remember(dueSoonRules) { dueSoonRules.firstOrNull()?.accountId }
    val notificationBadge =
        (if (unauditedCount > 0) 1 else 0) +
        activeLimitWarnings.size +
        (if (dueSoonRules.isNotEmpty()) 1 else 0)

    var showNotifications by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    // ── Empty state ──────────────────────────────────────────────────────────
    if (accounts.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Noch keine Konten angelegt", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onAddAccountClick) { Text("Konto hinzufügen") }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("Übersicht", style = MaterialTheme.typography.headlineLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverviewIconTile(icon = Icons.Outlined.Search, onClick = { showSearch = true })
                        BadgedBox(
                            badge = { if (notificationBadge > 0) Badge { Text("$notificationBadge") } },
                        ) {
                            OverviewIconTile(
                                icon = Icons.Outlined.Notifications,
                                onClick = { showNotifications = true },
                            )
                        }
                        // Sync (#110). The state is carried by the badge as well as the
                        // colour — colour alone leaves the button stateless for eyes that
                        // cannot tell the two apart. Goes straight to the server sync;
                        // the Wi-Fi path stays under Mehr, where a fallback belongs (#92).
                        BadgedBox(
                            badge = { if (unsyncedCount > 0) Badge { Text("$unsyncedCount") } },
                        ) {
                            OverviewIconTile(
                                icon = Icons.Outlined.Sync,
                                onClick = onNavigateToSync,
                                tint = if (unsyncedCount > 0) MaterialTheme.colorScheme.primary else null,
                            )
                        }
                    }
                }
            }

            // ── Balance hero ─────────────────────────────────────────────────
            item {
                // Just the total (#111). Income and expenses for a month were a second,
                // smaller answer to a question the Auswertungen answer properly — with a
                // period one can choose and an account one can pick.
                BalanceHero(
                    title = "Gesamtvermögen",
                    amount = totalBalance,
                )
            }

            // ── Konten ───────────────────────────────────────────────────────
            item {
                SectionHeader(title = "Konten")
            }
            items(accounts) { account ->
                OverviewAccountCard(
                    account = account,
                    balance = accountBalances[account.id] ?: 0.0,
                    onClick = { onAccountClick(account) },
                )
            }
        }
    }

    // ── Notification sheet ───────────────────────────────────────────────────
    if (showNotifications) {
        ModalBottomSheet(
            onDismissRequest = { showNotifications = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    "Benachrichtigungen",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (notificationBadge == 0) {
                    Text(
                        "Keine Benachrichtigungen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = finColors.textSub,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    if (unauditedCount > 0) {
                        NotificationRow(
                            title = "Ungeprüfte Buchungen",
                            subtitle = "$unauditedCount Buchung${if (unauditedCount != 1) "en" else ""}",
                            onClick = {
                                showNotifications = false
                                firstUnauditedAccountId?.let { onNavigateToAudit(it) }
                            },
                        )
                    }
                    activeLimitWarnings.forEach { warning ->
                        NotificationRow(
                            title = warning.title,
                            subtitle = warning.accountName,
                            onClick = {
                                showNotifications = false
                                onNavigateToAccountDetail(warning.accountId)
                            },
                        )
                    }
                    if (dueSoonRules.isNotEmpty()) {
                        NotificationRow(
                            title = "Fällige Daueraufträge",
                            subtitle = "${dueSoonRules.size} Regel${if (dueSoonRules.size != 1) "n" else ""} in den nächsten 7 Tagen",
                            onClick = {
                                showNotifications = false
                                firstDueSoonAccountId?.let { onNavigateToRecurring(it) }
                            },
                        )
                    }
                }
            }
        }
    }

    // ── Search dialog ────────────────────────────────────────────────────────
    if (showSearch) {
        SearchDialog(
            accounts = accounts,
            bookings = bookings,
            onDismiss = { showSearch = false },
            onAccountClick = { acc -> showSearch = false; onAccountClick(acc) },
            // Der Treffer oeffnete bisher nur das Konto — die gesuchte Buchung war
            // damit wieder irgendwo in einer Liste. Jetzt fuehrt er zu ihr selbst.
            onBookingClick = { booking ->
                showSearch = false
                onBookingClick(booking)
            },
        )
    }
}

@Composable
private fun NotificationRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = FinTheme.colors.textSub)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDialog(
    accounts: List<Account>,
    bookings: List<Booking>,
    onDismiss: () -> Unit,
    onAccountClick: (Account) -> Unit,
    onBookingClick: (Booking) -> Unit,
) {
    val finColors = FinTheme.colors
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }

    val filteredAccounts = remember(accounts, query) {
        if (query.isBlank()) emptyList()
        else accounts.filter { it.name.contains(query, ignoreCase = true) }
    }
    val filteredBookings = remember(bookings, query) {
        if (query.isBlank()) emptyList()
        else bookings.filter { b ->
            !b.deleted && (
                b.description.contains(query, ignoreCase = true) ||
                b.category?.contains(query, ignoreCase = true) == true ||
                MoneyFormat.currency(b.amount).contains(query, ignoreCase = true)
            )
        }.take(50)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Buchungen, Konten suchen…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                HorizontalDivider()

                when {
                    query.isBlank() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Suchbegriff eingeben…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = finColors.textSub,
                            )
                        }
                    }
                    filteredAccounts.isEmpty() && filteredBookings.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Keine Ergebnisse für \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = finColors.textSub,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            if (filteredAccounts.isNotEmpty()) {
                                item {
                                    Text(
                                        "Konten",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = finColors.textFaint,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                                items(filteredAccounts, key = { it.id }) { acc ->
                                    val typeLabel = when (acc.type) {
                                        AccountType.GIRO -> "Girokonto"
                                        // Says what the figure beside it means, so the gap to the Gesamtvermögen above is
        // explained rather than left to be worked out from the discrepancy (#101).
        AccountType.CREDIT_CARD -> "Kreditkarte · laufende Abrechnung"
                                        AccountType.SPARKONTO -> "Sparkonto"
                                        AccountType.TAGESGELD -> "Tagesgeldkonto"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onAccountClick(acc) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(acc.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = finColors.textSub)
                                        }
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = finColors.textFaint,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                }
                            }
                            if (filteredBookings.isNotEmpty()) {
                                item {
                                    Text(
                                        "Buchungen",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = finColors.textFaint,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    )
                                }
                                items(filteredBookings, key = { it.id }) { booking ->
                                    val accName = accountMap[booking.accountId]?.name ?: ""
                                    val dateStr = java.time.Instant.ofEpochMilli(booking.timestamp)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy", java.util.Locale.GERMANY))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onBookingClick(booking) }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                booking.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                buildString {
                                                    append(accName)
                                                    append(" · ")
                                                    append(dateStr)
                                                    if (booking.category != null) append(" · ${booking.category}")
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = finColors.textSub,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        MoneyText(
                                            amount = booking.amount,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun OverviewIconTile(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverviewAccountCard(
    account: Account,
    balance: Double,
    onClick: () -> Unit,
) {
    val finColors = FinTheme.colors
    val (icon, accentColor) = when (account.type) {
        AccountType.GIRO -> Icons.Outlined.AccountBalance to MaterialTheme.colorScheme.primary
        AccountType.CREDIT_CARD -> Icons.Filled.CreditCard to finColors.expense
        AccountType.SPARKONTO -> Icons.Filled.Savings to finColors.income
        AccountType.TAGESGELD -> Icons.Filled.Payments to MaterialTheme.colorScheme.primary
    }
    val typeLabel = when (account.type) {
        AccountType.GIRO -> "Girokonto"
        AccountType.CREDIT_CARD -> "Kreditkarte"
        AccountType.SPARKONTO -> "Sparkonto"
        AccountType.TAGESGELD -> "Tagesgeldkonto"
    }

    FinCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.titleSmall)
                Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = finColors.textSub)
            }
            MoneyText(amount = balance, style = MaterialTheme.typography.titleSmall)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = finColors.textFaint,
                modifier = Modifier.size(18.dp),
            )
        }

        // Limit bar — for a card's credit limit and a Girokonto's Dispo alike (#99)
        val spendingLimit = account.spendingLimit
        if (spendingLimit != null) {
            val usage = computeLimitUsage(balance, spendingLimit)
            val warning = limitWarningFor(account, balance)
            if (usage.percentUsed > 0 || usage.overLimit) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { usage.percentUsed.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    // Red from the warning threshold on, not only once it is gone:
                    // the bar and the line below it must not disagree.
                    color = if (warning != null) finColors.expense else finColors.income,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                if (warning != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = warning.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = finColors.expense,
                    )
                }
            }
        }
    }
}
