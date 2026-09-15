package io.github.willywonka644.fintracker.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.theme.ExpenseDark
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.ui.theme.IncomeDark

private val sampleCategory = Category(
    name = "Lebensmittel",
    iconName = "ShoppingCart",
    color = 0xFFFF9F45,
)

@Preview(name = "Components — Light", showBackground = true, backgroundColor = 0xFFEEF0F8)
@Preview(name = "Components — Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, backgroundColor = 0xFF0B0E16)
@Composable
private fun AllComponentsPreview() {
    FinTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // MoneyText
                SectionHeader(title = "MoneyText")
                MoneyText(amount = 1234.56, style = MaterialTheme.typography.titleLarge)
                MoneyText(amount = -567.89, style = MaterialTheme.typography.titleLarge)
                MoneyText(amount = 100.0, style = MaterialTheme.typography.titleLarge, emphasize = false)

                // FinCard
                SectionHeader(title = "FinCard")
                FinCard {
                    Text("Inhalt der Karte", style = MaterialTheme.typography.bodyMedium)
                }

                // CategoryChip
                SectionHeader(title = "CategoryChip")
                CategoryChip(category = sampleCategory, size = 40.dp)

                // BalanceHero
                SectionHeader(title = "BalanceHero")
                BalanceHero(
                    title = "Gesamtvermögen",
                    amount = 12345.67,
                    periodLabel = "Mai 2026 · 3 Konten",
                    income = 3200.0,
                    expense = 1450.50,
                )

                // FilterChipRow
                SectionHeader(title = "FilterChipRow")
                var selected by remember { mutableIntStateOf(0) }
                FilterChipRow(
                    items = listOf("Alle", "Ausgaben", "Einnahmen", "Daueraufträge"),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )

                // StatusPill + FlagBadge
                SectionHeader(title = "StatusPill / FlagBadge")
                val finColors = FinTheme.colors
                StatusPill(text = "Gebucht", color = IncomeDark)
                StatusPill(text = "Geplant", color = ExpenseDark)
                FlagBadge(text = "Dauerauftrag", icon = Icons.Filled.Repeat)
                FlagBadge(text = "Rate 2/12")

                // InOutSummary
                SectionHeader(title = "InOutSummary")
                InOutSummary(income = 3200.0, expense = 1450.50)
            }
        }
    }
}
