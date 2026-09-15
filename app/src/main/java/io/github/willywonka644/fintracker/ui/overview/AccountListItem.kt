package io.github.willywonka644.fintracker.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.R
import io.github.willywonka644.fintracker.analytics.LimitSeverity
import io.github.willywonka644.fintracker.analytics.computeLimitUsage
import io.github.willywonka644.fintracker.analytics.limitWarningFor
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AccountListItem(
    account: Account,
    currentBalance: Double,
    modifier: Modifier = Modifier
) {
    val formattedBalance = remember(currentBalance) { MoneyFormat.currency(currentBalance) }

    val balanceColor = when {
        currentBalance > 0 -> Color(0xFF2E7D32)
        currentBalance < 0 -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier,
        shape = CardDefaults.shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = account.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = accountTypeLabel(account.type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                }

                Text(
                    text = formattedBalance,
                    style = MaterialTheme.typography.titleMedium,
                    color = balanceColor
                )
            }

            val limitUsage = remember(currentBalance, account.spendingLimit) {
                account.spendingLimit?.let { computeLimitUsage(currentBalance, it) }
            }
            val warning = remember(currentBalance, account) {
                limitWarningFor(account, currentBalance)
            }
            // Any account carrying a limit, card or Dispo alike (#99)
            if (limitUsage != null) {
                val progress =
                    limitUsage.percentUsed.coerceIn(0.0, 1.0).toFloat()
                val isOverLimit =
                    limitUsage.overLimit || limitUsage.remaining < 0.0
                val indicatorColor =
                    if (isOverLimit) MaterialTheme.colorScheme.error
                    else Color(0xFF2E7D32)
                val spentText = MoneyFormat.currencyAbs(limitUsage.used)
                val limitText = MoneyFormat.currencyAbs(limitUsage.limit)
                val percentValue = (limitUsage.percentUsed * 100).roundToInt()
                val percentLabel = if (limitUsage.limit == 0.0 && limitUsage.used > 0.0) {
                    stringResource(R.string.placeholder_dash)
                } else {
                    "${percentValue}%"
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = indicatorColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$spentText / $limitText · $percentLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverLimit) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Wording from the shared warning, so a Girokonto reads "Dispo
                    // überzogen" here and not the card's word (#99). This line used to
                    // come from a string resource that only knew about limits.
                    if (warning != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        val overspentText = MoneyFormat.currencyAbs(abs(limitUsage.remaining))
                        Text(
                            text = when {
                                warning.severity != LimitSeverity.OVER -> warning.title
                                limitUsage.limit == 0.0 && limitUsage.used > 0.0 ->
                                    "${warning.title} um $overspentText"
                                else ->
                                    "${warning.title} um $overspentText ($percentValue%)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
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
