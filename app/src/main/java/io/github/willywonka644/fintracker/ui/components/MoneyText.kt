package io.github.willywonka644.fintracker.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat

/**
 * Renders a EUR amount in German locale ("1.234,56 €") with tabular digits.
 * When [emphasize] is true the text is colored income-green (≥ 0) or expense-red (< 0).
 * When false it renders in the default onSurface color (use for neutral displays).
 */
@Composable
fun MoneyText(
    amount: Double,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    emphasize: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val color = when {
        !emphasize -> MaterialTheme.colorScheme.onSurface
        amount >= 0 -> finColors.income
        else -> finColors.expense
    }
    Text(
        text = MoneyFormat.currency(amount),
        style = style.copy(
            color = color,
            fontFeatureSettings = "tnum",
        ),
        modifier = modifier,
    )
}
