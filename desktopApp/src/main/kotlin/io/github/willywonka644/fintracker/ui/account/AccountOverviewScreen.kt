package io.github.willywonka644.fintracker.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.accountDeletionImpact
import io.github.willywonka644.fintracker.accountDeletionMessage
import io.github.willywonka644.fintracker.accountDeletionConfirmLabel
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.analytics.currentBillingCyclePeriod
import io.github.willywonka644.fintracker.analytics.filterBookingsForAccount
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat

@Composable
fun AccountOverviewScreen(
    accounts: List<Account>,
    bookings: List<Booking>,
    recurringRules: List<RecurringRule>,
    selectedAccount: Account?,
    onAccountSelected: (Account) -> Unit,
    onAccountSaved: (Account) -> Unit,
    onAccountDeleted: (Account) -> Unit,
    detailContent: @Composable (Account, List<Booking>) -> Unit,
    unsyncedCount: Int = 0,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxSize()) {
        AccountSidebar(
            accounts = accounts,
            bookings = bookings,
            selectedAccount = selectedAccount,
            onAccountSelected = onAccountSelected,
            onAddClick = { showAddDialog = true },
            unsyncedCount = unsyncedCount,
        )
        VerticalDivider()
        AccountRightPanel(
            account = selectedAccount,
            bookings = bookings,
            onEditClick = { showEditDialog = true },
            onDeleteClick = { showDeleteConfirm = true },
            detailContent = detailContent,
        )
    }

    if (showAddDialog) {
        AccountFormDialog(
            onDismiss = { showAddDialog = false },
            onSave = { account ->
                showAddDialog = false
                onAccountSaved(account)
            },
        )
    }

    if (showEditDialog && selectedAccount != null) {
        AccountFormDialog(
            existing = selectedAccount,
            onDismiss = { showEditDialog = false },
            onSave = { account ->
                showEditDialog = false
                onAccountSaved(account)
            },
        )
    }

    if (showDeleteConfirm && selectedAccount != null) {
        val account = selectedAccount
        // Beziffert, was mitgeht, und laesst es bestaetigen (#113, jetzt auch hier).
        // Vorher stand hier ein Satz ohne Zahlen — bei der destruktivsten Aktion der App,
        // deren Kaskade als Tombstones auf alle Geraete propagiert.
        val impact = remember(account.id, bookings, recurringRules) {
            accountDeletionImpact(account.id, bookings, recurringRules)
        }
        var acknowledged by remember(account.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Konto löschen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(accountDeletionMessage(account.name, impact))
                    // Nur wo es etwas zu verlieren gibt: bei einem leeren Konto waere das
                    // Kaestchen eine Huerde ohne Anlass.
                    if (!impact.isEmpty) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { acknowledged = !acknowledged },
                        ) {
                            Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                            Text(accountDeletionConfirmLabel(impact))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onAccountDeleted(account)
                    },
                    enabled = impact.isEmpty || acknowledged,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun AccountRightPanel(
    account: Account?,
    bookings: List<Booking>,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    detailContent: @Composable (Account, List<Booking>) -> Unit,
) {
    if (account == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Konto in der Seitenleiste auswählen",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var showMenu by remember { mutableStateOf(false) }

    val accountBookings = bookings.filter { it.accountId == account.id }
    val balance = if (account.type == AccountType.CREDIT_CARD) {
        val period = currentBillingCyclePeriod(account.billingStartDay ?: 18)
        filterBookingsForAccount(bookings, account.id, period).sumOf { it.amount }
    } else {
        accountBookings.filter { it.status == BookingStatus.POSTED }.sumOf { it.amount }
    }

    Column(Modifier.fillMaxSize()) {
        // Account header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(account.type.displayName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                MoneyFormat.currency(balance),
                style = MaterialTheme.typography.titleMedium,
                color = if (balance >= 0) FinTheme.colors.income else FinTheme.colors.expense,
                modifier = Modifier.padding(end = 8.dp),
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Account options")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Bearbeiten") },
                        onClick = { showMenu = false; onEditClick() },
                    )
                    DropdownMenuItem(
                        text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDeleteClick() },
                    )
                }
            }
        }
        HorizontalDivider()

        // Detail/booking content area
        detailContent(account, accountBookings)
    }
}

@Composable
private fun AccountSidebar(
    accounts: List<Account>,
    bookings: List<Booking>,
    selectedAccount: Account?,
    onAccountSelected: (Account) -> Unit,
    onAddClick: () -> Unit,
    unsyncedCount: Int = 0,
) {
    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Konten",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            IconButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add account")
            }
        }
        HorizontalDivider()
        if (unsyncedCount > 0) {
            Text(
                text = "$unsyncedCount nicht synchronisierte${if (unsyncedCount == 1) " Änderung" else " Änderungen"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (accounts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Noch keine Konten",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn {
                items(accounts, key = { it.id }) { account ->
                    val balance = if (account.type == AccountType.CREDIT_CARD) {
                        val period = currentBillingCyclePeriod(account.billingStartDay ?: 18)
                        filterBookingsForAccount(bookings, account.id, period).sumOf { it.amount }
                    } else {
                        bookings.filter { it.accountId == account.id && it.status == BookingStatus.POSTED }.sumOf { it.amount }
                    }
                    AccountRow(
                        account = account,
                        balance = balance,
                        isSelected = selectedAccount?.id == account.id,
                        onClick = { onAccountSelected(account) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    balance: Double,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(account.type.displayName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = MoneyFormat.currency(balance),
            style = MaterialTheme.typography.bodyMedium,
            color = if (balance >= 0) FinTheme.colors.income else FinTheme.colors.expense,
        )
    }
}

fun AccountType.displayName(): String = when (this) {
    AccountType.GIRO -> "Girokonto"
    AccountType.CREDIT_CARD -> "Kreditkarte"
    AccountType.SPARKONTO -> "Sparkonto"
    AccountType.TAGESGELD -> "Tagesgeldkonto"
}
