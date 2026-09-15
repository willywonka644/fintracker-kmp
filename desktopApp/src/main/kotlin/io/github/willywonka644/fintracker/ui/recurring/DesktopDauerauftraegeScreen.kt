package io.github.willywonka644.fintracker.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.components.DesktopFinCard
import io.github.willywonka644.fintracker.ui.components.DesktopMoneyText
import io.github.willywonka644.fintracker.ui.theme.FinTheme

/** Semantics tags for UI tests. */
object DauerauftraegeTestTags {
    const val ADD_FAB = "dauerauftrag_add"
}

private enum class DisplayMode { FILTER, GROUP }

private fun frequencyLabel(f: Frequency) = when (f) {
    Frequency.WEEKLY -> "Wöchentlich"
    Frequency.MONTHLY -> "Monatlich"
    Frequency.YEARLY -> "Jährlich"
}

@Composable
fun DesktopDauerauftraegeScreen(
    rules: List<RecurringRule>,
    accounts: List<Account>,
    categories: List<Category>,
    onSaveRule: (RecurringRule) -> Unit,
    onDeleteRule: (RecurringRule) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RecurringRule?>(null) }
    var displayMode by remember { mutableStateOf(DisplayMode.FILTER) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    val finColors = FinTheme.colors

    val accountsWithRules = remember(rules, accounts) {
        accounts.filter { acc -> rules.any { r -> r.accountId == acc.id } }
            .sortedBy { it.name }
    }

    val displayedRules = remember(rules, displayMode, selectedAccountId) {
        if (selectedAccountId == null) rules
        else rules.filter { it.accountId == selectedAccountId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Daueraufträge",
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${rules.size} aktive Regel${if (rules.size != 1) "n" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = finColors.textSub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // ── Mode toggle ───────────────────────────────────────────────
                if (rules.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = displayMode == DisplayMode.FILTER,
                            onClick = { displayMode = DisplayMode.FILTER },
                            label = { Text("Gefiltert", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                        FilterChip(
                            selected = displayMode == DisplayMode.GROUP,
                            onClick = {
                                displayMode = DisplayMode.GROUP
                                selectedAccountId = null
                            },
                            label = { Text("Gruppiert", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            // ── Account filter chips (FILTER mode only) ───────────────────────
            if (rules.isNotEmpty() && displayMode == DisplayMode.FILTER && accountsWithRules.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = selectedAccountId == null,
                        onClick = { selectedAccountId = null },
                        label = { Text("Alle", style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                    accountsWithRules.forEach { acc ->
                        FilterChip(
                            selected = selectedAccountId == acc.id,
                            onClick = { selectedAccountId = acc.id },
                            label = { Text(acc.name, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
            }

            // ── Content ───────────────────────────────────────────────────────
            if (rules.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Repeat, contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = finColors.textFaint,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Noch keine Daueraufträge angelegt",
                            style = MaterialTheme.typography.bodyLarge,
                            color = finColors.textSub,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tippe + um einen neuen Dauerauftrag zu erstellen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = finColors.textFaint,
                        )
                    }
                }
            } else if (displayMode == DisplayMode.FILTER) {
                if (displayedRules.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Keine Daueraufträge für dieses Konto",
                            style = MaterialTheme.typography.bodyMedium,
                            color = finColors.textSub,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        item {
                            DesktopFinCard(modifier = Modifier.fillMaxWidth()) {
                                displayedRules.forEachIndexed { index, rule ->
                                    val accountName = accounts.firstOrNull { it.id == rule.accountId }?.name ?: "—"
                                    DauerauftragRow(
                                        rule = rule,
                                        accountName = accountName,
                                        onClick = { editingRule = rule },
                                    )
                                    if (index < displayedRules.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 0.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // GROUP mode
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    accountsWithRules.forEach { acc ->
                        val groupRules = rules.filter { it.accountId == acc.id }
                        item(key = "header-${acc.id}") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    acc.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = finColors.textSub,
                                )
                                Text(
                                    "(${groupRules.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = finColors.textFaint,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                )
                            }
                        }
                        item(key = "card-${acc.id}") {
                            DesktopFinCard(modifier = Modifier.fillMaxWidth()) {
                                groupRules.forEachIndexed { index, rule ->
                                    DauerauftragRow(
                                        rule = rule,
                                        accountName = acc.name,
                                        onClick = { editingRule = rule },
                                    )
                                    if (index < groupRules.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 0.dp),
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── FAB ───────────────────────────────────────────────────────────────
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag(DauerauftraegeTestTags.ADD_FAB),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Neuer Dauerauftrag")
        }
    }

    if (showAddDialog) {
        AddEditRecurringRuleDialog(
            initialRule = null,
            accounts = accounts,
            categories = categories,
            onDismiss = { showAddDialog = false },
            onSave = { rule -> onSaveRule(rule); showAddDialog = false },
        )
    }

    editingRule?.let { rule ->
        AddEditRecurringRuleDialog(
            initialRule = rule,
            accounts = accounts,
            categories = categories,
            onDismiss = { editingRule = null },
            onSave = { updated -> onSaveRule(updated); editingRule = null },
            onDelete = { toDelete -> onDeleteRule(toDelete); editingRule = null },
        )
    }
}

@Composable
private fun DauerauftragRow(
    rule: RecurringRule,
    accountName: String,
    onClick: () -> Unit,
) {
    val finColors = FinTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.description.ifBlank { "Ohne Beschreibung" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${frequencyLabel(rule.frequency)} · $accountName" +
                    (rule.nextExecutionDate?.let { " · Nächste: $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
            )
        }

        DesktopMoneyText(amount = rule.amount, style = MaterialTheme.typography.bodyMedium)

        androidx.compose.material3.TextButton(onClick = onClick) { Text("Bearbeiten") }
    }
}
