package io.github.willywonka644.fintracker.ui.overview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.util.MoneyFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddBookingSheet(
    accounts: List<Account>,
    accountBalances: Map<String, Double>,
    onDismiss: () -> Unit,
    onSelectOneTime: (accountId: String) -> Unit,
    onSelectInstallment: (accountId: String) -> Unit,
    onSelectRecurring: (accountId: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedAccountId by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            if (selectedAccountId == null) {
                // Step 1: Account selection
                Text(
                    text = "Konto auswählen",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                accounts.forEach { account ->
                    val balance = accountBalances[account.id] ?: 0.0
                    AccountSelectionCard(
                        account = account,
                        balance = balance,
                        onClick = { selectedAccountId = account.id }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                // Step 2: Booking type selection
                Text(
                    text = "Buchungsart wählen",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                BookingTypeCard(
                    icon = Icons.Default.Add,
                    title = "Einmalige Buchung",
                    description = "Eine einzelne Buchung erstellen",
                    onClick = { onSelectOneTime(selectedAccountId!!) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                BookingTypeCard(
                    icon = Icons.Default.Payments,
                    title = "Ratenzahlung",
                    description = "Betrag auf mehrere Raten aufteilen",
                    onClick = { onSelectInstallment(selectedAccountId!!) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                BookingTypeCard(
                    icon = Icons.Default.Repeat,
                    title = "Dauerauftrag",
                    description = "Regelmäßig wiederkehrende Buchung",
                    onClick = { onSelectRecurring(selectedAccountId!!) }
                )
            }
        }
    }
}

@Composable
private fun AccountSelectionCard(
    account: Account,
    balance: Double,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (account.type) {
                        AccountType.GIRO -> "Girokonto"
                        AccountType.CREDIT_CARD -> "Kreditkarte"
                        AccountType.SPARKONTO -> "Sparkonto"
                        AccountType.TAGESGELD -> "Tagesgeldkonto"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = MoneyFormat.currencySigned(balance),
                style = MaterialTheme.typography.bodyMedium,
                color = if (balance >= 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun BookingTypeCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
