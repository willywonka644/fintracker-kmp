package io.github.willywonka644.fintracker.ui.accountdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.util.MoneyFormat

@Composable
fun AccountSummaryCard(
    account: Account,
    computedBalance: Double,
    periodSelectorContent: (@Composable () -> Unit)? = null
) {
    val formattedBalance = remember(computedBalance) { MoneyFormat.currency(computedBalance) }

    val balanceColor = when {
        computedBalance > 0 -> Color(0xFF2E7D32)
        computedBalance < 0 -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1) Account type
            Text(
                text = accountTypeLabel(account.type),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 2) Balance label + optional inline period selector
            if (periodSelectorContent != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (account.type == AccountType.CREDIT_CARD) "Saldo im Zeitraum" else "Saldo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    periodSelectorContent()
                }
            } else {
                Text(
                    text = if (account.type == AccountType.CREDIT_CARD) "Saldo im Zeitraum" else "Saldo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3) Balance value
            Text(
                text = formattedBalance,
                style = MaterialTheme.typography.headlineSmall,
                color = balanceColor
            )
        }
    }
}

private fun accountTypeLabel(type: AccountType): String =
    when (type) {
        AccountType.GIRO -> "Girokonto"
        AccountType.CREDIT_CARD -> "Kreditkarte"
        AccountType.SPARKONTO -> "Sparkonto"
        AccountType.TAGESGELD -> "Tagesgeldkonto"
    }
