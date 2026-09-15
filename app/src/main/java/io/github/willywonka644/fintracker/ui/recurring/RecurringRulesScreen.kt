package io.github.willywonka644.fintracker.ui.recurring

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.R
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.components.CategoryChip
import io.github.willywonka644.fintracker.ui.components.FlagBadge
import io.github.willywonka644.fintracker.ui.components.FinCard
import io.github.willywonka644.fintracker.ui.components.MoneyText
import io.github.willywonka644.fintracker.ui.nav.GradientFAB
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.toJavaLocalDate

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

private enum class DisplayMode { FILTER, GROUP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringRulesScreen(
    rules: List<RecurringRule>,
    accounts: List<Account>,
    categories: List<Category> = emptyList(),
    initialAccountId: String? = null,
    onNavigateBack: () -> Unit,
    onAddRule: (selectedAccountId: String?) -> Unit,
    onEditRule: (RecurringRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryMap = remember(categories) { categories.associateBy { it.name } }
    var displayMode by remember { mutableStateOf(DisplayMode.FILTER) }
    var selectedAccountId by remember(initialAccountId) { mutableStateOf(initialAccountId) }
    var showAccountMenu by remember { mutableStateOf(false) }
    val selectedAccountName = accounts.firstOrNull { it.id == selectedAccountId }?.name

    // GROUP mode sections: only accounts that actually have rules
    val accountsWithRules = remember(rules, accounts) {
        accounts.filter { acc -> rules.any { r -> r.accountId == acc.id } }.sortedBy { it.name }
    }

    val displayedRules = remember(rules, displayMode, selectedAccountId) {
        if (displayMode == DisplayMode.FILTER && selectedAccountId != null)
            rules.filter { it.accountId == selectedAccountId }
        else rules
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        floatingActionButton = { GradientFAB(onClick = { onAddRule(selectedAccountId) }) },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // ── Mode toggle + summary ─────────────────────────────────────────
            if (rules.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${rules.size} Regel${if (rules.size != 1) "n" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FinTheme.colors.textSub,
                    )
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
                            onClick = { displayMode = DisplayMode.GROUP; selectedAccountId = null },
                            label = { Text("Gruppiert", style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
                // ── Account dropdown (FILTER mode) — same pattern as Auswertungen/Buchungen
                if (displayMode == DisplayMode.FILTER && accounts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showAccountMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = selectedAccountName ?: "Alle Konten",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Alle Konten") },
                                onClick = { selectedAccountId = null; showAccountMenu = false },
                            )
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = { selectedAccountId = acc.id; showAccountMenu = false },
                                )
                            }
                        }
                    }
                }
            }

            if (rules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Repeat, null, tint = FinTheme.colors.textFaint, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.recurring_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = FinTheme.colors.textSub,
                        )
                    }
                }
            } else if (displayMode == DisplayMode.FILTER) {
                if (displayedRules.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Keine Daueraufträge für dieses Konto",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FinTheme.colors.textSub,
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items = displayedRules, key = { it.id }) { rule ->
                            val accountName = accounts.firstOrNull { it.id == rule.accountId }?.name
                            RecurringRuleCard(
                                rule = rule,
                                accountName = accountName,
                                category = categoryMap[rule.category],
                                onClick = { onEditRule(rule) },
                            )
                        }
                    }
                }
            } else {
                // GROUP mode
                LazyColumn(
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    accountsWithRules.forEach { acc ->
                        val groupRules = rules.filter { it.accountId == acc.id }
                        item(key = "hdr-${acc.id}") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    acc.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = FinTheme.colors.textSub,
                                )
                                Text(
                                    "(${groupRules.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = FinTheme.colors.textFaint,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                )
                            }
                        }
                        items(items = groupRules, key = { "rule-${it.id}" }) { rule ->
                            RecurringRuleCard(
                                rule = rule,
                                accountName = acc.name,
                                category = categoryMap[rule.category],
                                onClick = { onEditRule(rule) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecurringRuleCard(
    rule: RecurringRule,
    accountName: String?,
    category: Category?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val formattedNextDate = rule.nextExecutionDate.toJavaLocalDate().format(DATE_FORMATTER)
    val frequencyLabel = frequencyDisplayName(rule.frequency)
    val isExhausted = rule.remainingExecutions.let { it != null && it <= 0 }

    FinCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (category != null) CategoryChip(category = category, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.description.ifBlank { stringResource(R.string.recurring_no_description) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (accountName != null) {
                    Text(text = accountName, style = MaterialTheme.typography.labelSmall, color = finColors.textSub)
                }
            }
            FlagBadge(text = frequencyLabel, icon = Icons.Filled.Repeat)
            MoneyText(amount = rule.amount, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Nächste Ausführung: $formattedNextDate",
                style = MaterialTheme.typography.bodySmall,
                color = finColors.textSub,
            )
            when {
                isExhausted -> Text(
                    text = stringResource(R.string.recurring_expired),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                rule.remainingExecutions != null -> Text(
                    text = "Noch ${rule.remainingExecutions}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
            }
        }
    }
}

internal fun frequencyDisplayName(frequency: Frequency): String =
    when (frequency) {
        Frequency.WEEKLY -> "Wöchentlich"
        Frequency.MONTHLY -> "Monatlich"
        Frequency.YEARLY -> "Jährlich"
    }
