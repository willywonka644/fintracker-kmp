package io.github.willywonka644.fintracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlin.math.absoluteValue

/**
 * Two side-by-side FinCards showing income (↑ green) and expense (↓ red) totals.
 * Pass absolute values for both — the component applies sign semantics via color + icon.
 */
@Composable
fun InOutSummary(
    income: Double,
    expense: Double,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FinCard(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "Einnahmen",
                    tint = finColors.income,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(0.dp))
                Column {
                    Text(
                        text = "Einnahmen",
                        style = MaterialTheme.typography.labelSmall,
                        color = finColors.textSub,
                    )
                    Text(
                        text = MoneyFormat.currency(income.absoluteValue),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = finColors.income,
                    )
                }
            }
        }
        FinCard(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = "Ausgaben",
                    tint = finColors.expense,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(0.dp))
                Column {
                    Text(
                        text = "Ausgaben",
                        style = MaterialTheme.typography.labelSmall,
                        color = finColors.textSub,
                    )
                    Text(
                        text = MoneyFormat.currency(expense.absoluteValue),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = finColors.expense,
                    )
                }
            }
        }
    }
}
