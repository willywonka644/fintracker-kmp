package io.github.willywonka644.fintracker.ui.installments

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.installments.InstallmentGroup
import io.github.willywonka644.fintracker.installments.buildInstallmentGroups
import io.github.willywonka644.fintracker.ui.components.DesktopFinCard
import io.github.willywonka644.fintracker.ui.components.DesktopMoneyText
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat

private enum class DisplayMode { FILTER, GROUP }
private enum class StatusFilter { ALL, ACTIVE, COMPLETED }

@Composable
fun DesktopRatenzahlungenScreen(
    accounts: List<Account>,
    bookings: List<Booking>,
    categories: List<Category>,
    nextBookingId: () -> String,
    onSaveBookings: (List<Booking>) -> Unit,
    onDeleteGroup: (String) -> Unit,
) {
    val finColors = FinTheme.colors
    var showAddDialog by remember { mutableStateOf(false) }
    var addForAccountId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteGroupId by remember { mutableStateOf<String?>(null) }
    var displayMode by remember { mutableStateOf(DisplayMode.FILTER) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var statusFilter by remember { mutableStateOf(StatusFilter.ALL) }

    val allGroups = remember(bookings, accounts) {
        accounts.flatMap { acc -> buildInstallmentGroups(bookings, acc.id) }
            .sortedBy { it.description }
    }

    val accountsWithGroups = remember(allGroups, accounts) {
        accounts.filter { acc -> allGroups.any { g -> g.accountId == acc.id } }
            .sortedBy { it.name }
    }

    val filteredGroups = remember(allGroups, selectedAccountId, statusFilter) {
        allGroups
            .filter { g -> selectedAccountId == null || g.accountId == selectedAccountId }
            .filter { g ->
                when (statusFilter) {
                    StatusFilter.ALL -> true
                    StatusFilter.ACTIVE -> g.remainingCount > 0
                    StatusFilter.COMPLETED -> g.remainingCount <= 0
                }
            }
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
                        "Ratenzahlungen",
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${allGroups.count { it.remainingCount > 0 }} laufend · ${allGroups.count { it.remainingCount <= 0 }} abgeschlossen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = finColors.textSub,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // ── Mode toggle ───────────────────────────────────────────────
                if (allGroups.isNotEmpty()) {
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

            // ── Status filter chips ───────────────────────────────────────────
            if (allGroups.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        StatusFilter.ALL to "Alle",
                        StatusFilter.ACTIVE to "Laufend",
                        StatusFilter.COMPLETED to "Abgeschlossen",
                    ).forEach { (filter, label) ->
                        FilterChip(
                            selected = statusFilter == filter,
                            onClick = { statusFilter = filter },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        )
                    }
                }
            }

            // ── Account filter chips (FILTER mode only) ───────────────────────
            if (allGroups.isNotEmpty() && displayMode == DisplayMode.FILTER && accountsWithGroups.size > 1) {
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
                    accountsWithGroups.forEach { acc ->
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
            if (allGroups.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Payments, null, modifier = Modifier.size(48.dp), tint = finColors.textFaint)
                        Spacer(Modifier.height(12.dp))
                        Text("Noch keine Ratenzahlungen angelegt", style = MaterialTheme.typography.bodyLarge, color = finColors.textSub)
                        Spacer(Modifier.height(4.dp))
                        Text("Tippe + um eine neue Ratenzahlung zu erstellen", style = MaterialTheme.typography.bodyMedium, color = finColors.textFaint)
                    }
                }
            } else if (displayMode == DisplayMode.FILTER) {
                if (filteredGroups.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "Keine Ratenzahlungen für diese Auswahl",
                            style = MaterialTheme.typography.bodyMedium,
                            color = finColors.textSub,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filteredGroups.chunked(2)) { rowGroups ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowGroups.forEach { group ->
                                    RatenzahlungCard(
                                        group = group,
                                        onDelete = { confirmDeleteGroupId = group.groupId },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowGroups.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            } else {
                // GROUP mode: status filter applies, then grouped by account
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    accountsWithGroups.forEach { acc ->
                        val groupsForAcc = filteredGroups.filter { it.accountId == acc.id }
                        if (groupsForAcc.isEmpty()) return@forEach

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
                                    "(${groupsForAcc.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = finColors.textFaint,
                                )
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                )
                            }
                        }
                        items(groupsForAcc.chunked(2), key = { "row-${acc.id}-${it.first().groupId}" }) { rowGroups ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowGroups.forEach { group ->
                                    RatenzahlungCard(
                                        group = group,
                                        onDelete = { confirmDeleteGroupId = group.groupId },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowGroups.size == 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                addForAccountId = accounts.firstOrNull()?.id
                showAddDialog = accounts.isNotEmpty()
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Neue Ratenzahlung")
        }
    }

    // ── Add dialog ────────────────────────────────────────────────────────────
    if (showAddDialog && addForAccountId != null) {
        AddInstallmentDialog(
            accountId = addForAccountId!!,
            categories = categories,
            nextBookingId = nextBookingId,
            onDismiss = { showAddDialog = false },
            onSave = { newBookings -> onSaveBookings(newBookings); showAddDialog = false },
        )
    }

    // ── Delete confirm ────────────────────────────────────────────────────────
    confirmDeleteGroupId?.let { groupId ->
        AlertDialog(
            onDismissRequest = { confirmDeleteGroupId = null },
            title = { Text("Ratenzahlung löschen?") },
            text = { Text("Alle Buchungen dieser Ratenzahlung werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = { onDeleteGroup(groupId); confirmDeleteGroupId = null }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteGroupId = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun RatenzahlungCard(
    group: InstallmentGroup,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val isCompleted = group.remainingCount <= 0
    val progress = if (group.totalCount > 0) {
        (group.totalCount - group.remainingCount).toFloat() / group.totalCount.toFloat()
    } else 1f
    val barColor = if (isCompleted) finColors.income else MaterialTheme.colorScheme.primary

    DesktopFinCard(modifier = modifier) {
        Text(group.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                MoneyFormat.currency(group.installmentAmount) + " / Rate",
                style = MaterialTheme.typography.bodySmall,
                color = finColors.textSub,
            )
            DesktopMoneyText(
                amount = group.totalAmount,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = barColor.copy(alpha = 0.15f),
        )
        Spacer(Modifier.height(6.dp))

        if (isCompleted) {
            Text("Abgeschlossen", style = MaterialTheme.typography.labelSmall, color = finColors.income)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${group.remainingCount} von ${group.totalCount} Raten offen",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
                group.nextDate?.let {
                    Text("Nächste: $it", style = MaterialTheme.typography.labelSmall, color = finColors.textFaint)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
            Text("Löschen", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
    }
}
