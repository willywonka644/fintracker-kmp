package io.github.willywonka644.fintracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlin.math.absoluteValue

/**
 * Gradient hero card showing a total balance, an optional label for it, an optional
 * subtitle, and an optional income / expense pill row.
 *
 * [periodLabel] is optional since #111: the overview shows a plain all-time total and
 * any period word under it would have described something else. Where it is passed, it
 * belongs to [amount].
 */
@Composable
fun BalanceHero(
    title: String,
    amount: Double,
    periodLabel: String? = null,
    sub: String? = null,
    income: Double? = null,
    expense: Double? = null,
    modifier: Modifier = Modifier,
) {
    val gradient = FinTheme.colors.heroGradient

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .drawBehind {
                // Two decorative semi-transparent white circles
                drawCircle(
                    color = Color.White.copy(alpha = 0.09f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 1.05f, -size.height * 0.25f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.06f),
                    radius = size.width * 0.38f,
                    center = Offset(-size.width * 0.08f, size.height * 1.1f),
                )
            }
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = MoneyFormat.currency(amount),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontFeatureSettings = "tnum",
                ),
                color = Color.White,
            )
            if (periodLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
            if (income != null || expense != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (income != null) HeroPill(income, isIncome = true)
                    if (expense != null) HeroPill(expense, isIncome = false)
                }
            }
        }
    }
}

@Composable
private fun HeroPill(amount: Double, isIncome: Boolean) {
    val finColors = FinTheme.colors
    val accentColor = if (isIncome) finColors.income else finColors.expense
    val icon = if (isIncome) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
    val label = if (isIncome) "Einnahmen" else "Ausgaben"

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = MoneyFormat.currency(amount.absoluteValue),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}
