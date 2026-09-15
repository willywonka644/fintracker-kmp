package io.github.willywonka644.fintracker.ui.installments

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.R
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.form.FieldState
import io.github.willywonka644.fintracker.form.hasContentWorthKeeping
import kotlinx.coroutines.launch
import io.github.willywonka644.fintracker.installments.InstallmentGroup
import io.github.willywonka644.fintracker.ui.booking.CategoryChipPicker
import io.github.willywonka644.fintracker.ui.booking.GradientButton
import io.github.willywonka644.fintracker.ui.components.CategoryChip
import io.github.willywonka644.fintracker.ui.components.FinCard
import io.github.willywonka644.fintracker.ui.components.MoneyText
import io.github.willywonka644.fintracker.ui.components.StatusPill
import io.github.willywonka644.fintracker.ui.nav.GradientFAB
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.toJavaLocalDate

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

private enum class StatusFilter { ACTIVE, COMPLETED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentsScreen(
    groups: List<InstallmentGroup>,
    accounts: List<Account> = emptyList(),
    categories: List<Category> = emptyList(),
    initialAccountId: String? = null,
    onNavigateBack: () -> Unit,
    onAddInstallment: (selectedAccountId: String?) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onUpdateGroup: (groupId: String, description: String, category: String?, rateAmount: Double, totalCount: Int) -> Unit = { _, _, _, _, _ -> },
    onAddCategory: (Category) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val categoryMap = remember(categories) { categories.associateBy { it.name } }
    var statusFilter by remember { mutableStateOf(StatusFilter.ACTIVE) }
    var selectedAccountId by remember(initialAccountId) { mutableStateOf(initialAccountId) }
    var showAccountMenu by remember { mutableStateOf(false) }
    val selectedAccountName = accounts.firstOrNull { it.id == selectedAccountId }?.name
    // Deleting a group removes ALL its bookings — never do that on a bare tap.
    var groupToDelete by remember { mutableStateOf<InstallmentGroup?>(null) }
    // Tapping a card opens the editor; deleting is an explicit option inside it.
    var groupToEdit by remember { mutableStateOf<InstallmentGroup?>(null) }

    val filteredGroups = remember(groups, selectedAccountId, statusFilter) {
        groups
            .filter { g -> selectedAccountId == null || g.accountId == selectedAccountId }
            .filter { g ->
                when (statusFilter) {
                    StatusFilter.ACTIVE -> g.remainingCount > 0
                    StatusFilter.COMPLETED -> g.remainingCount <= 0
                }
            }
    }

    val activeCount = remember(groups) { groups.count { it.remainingCount > 0 } }
    val completedCount = remember(groups) { groups.count { it.remainingCount <= 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.installments_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
        floatingActionButton = { GradientFAB(onClick = { onAddInstallment(selectedAccountId) }) },
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (groups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // Summary line
                Text(
                    text = "$activeCount laufend · $completedCount abgeschlossen",
                    style = MaterialTheme.typography.bodySmall,
                    color = FinTheme.colors.textSub,
                )
                Spacer(Modifier.height(8.dp))
                // Status filter chips: exactly one of Offen/Abgeschlossen is active
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        StatusFilter.ACTIVE to "Offen",
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
                // Account dropdown — same pattern as Auswertungen/Buchungen
                if (accounts.isNotEmpty()) {
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

            if (groups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Payments, null, tint = FinTheme.colors.textFaint, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.installments_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = FinTheme.colors.textSub,
                        )
                    }
                }
            } else if (filteredGroups.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Keine Ratenzahlungen für diese Auswahl",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FinTheme.colors.textSub,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = filteredGroups, key = { it.groupId }) { group ->
                        InstallmentGroupCard(
                            group = group,
                            category = categoryMap[group.category],
                            onOpen = { groupToEdit = group },
                        )
                    }
                }
            }
        }
    }

    // Editor sheet: modify description/category; deleting is an explicit option at the bottom.
    groupToEdit?.let { group ->
        val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var editDescription by remember(group.groupId) { mutableStateOf(group.description) }
        var editCategory by remember(group.groupId) { mutableStateOf(group.category ?: "") }
        var editDescriptionError by remember(group.groupId) { mutableStateOf<String?>(null) }
        var editAmountInput by remember(group.groupId) {
            mutableStateOf(abs(group.installmentAmount).toString())
        }
        var editAmountError by remember(group.groupId) { mutableStateOf<String?>(null) }
        var editCountInput by remember(group.groupId) { mutableStateOf(group.totalCount.toString()) }
        var editCountError by remember(group.groupId) { mutableStateOf<String?>(null) }
        val paidCount = group.totalCount - group.remainingCount
        val minCount = max(1, paidCount)
        val finColors = FinTheme.colors

        // ── Schutz vor versehentlichem Verwerfen ─────────────────────────────
        // Muss beim Schließen gerechnet werden — Material friert einen vorab
        // berechneten Boolean im Rückruf für die Wischgeste ein.
        val baselineDescription = remember(group.groupId) { editDescription }
        val baselineCategory = remember(group.groupId) { editCategory }
        val baselineAmount = remember(group.groupId) { editAmountInput }
        val baselineCount = remember(group.groupId) { editCountInput }

        fun hasUnsavedInput(): Boolean = hasContentWorthKeeping(
            openedWithScannedData = false,
            hasNewAttachment = false,
            fields = listOf(
                FieldState(editDescription, baselineDescription),
                FieldState(editCategory, baselineCategory),
                FieldState(editAmountInput, baselineAmount),
                FieldState(editCountInput, baselineCount),
            ),
        )

        var showDiscardConfirm by remember(group.groupId) { mutableStateOf(false) }
        val discardScope = rememberCoroutineScope()

        /** Material blendet das Sheet vor onDismissRequest bereits aus — zurückholen. */
        fun keepEditing() {
            showDiscardConfirm = false
            discardScope.launch { editSheetState.show() }
        }

        if (showDiscardConfirm) {
            AlertDialog(
                onDismissRequest = { keepEditing() },
                title = { Text("Änderungen verwerfen?") },
                text = { Text("Was du bisher geändert hast, geht dabei verloren.") },
                confirmButton = {
                    TextButton(onClick = { showDiscardConfirm = false; groupToEdit = null }) {
                        Text("Verwerfen", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { keepEditing() }) { Text("Weiter bearbeiten") }
                }
            )
        }

        ModalBottomSheet(
            onDismissRequest = {
                if (hasUnsavedInput()) showDiscardConfirm = true else groupToEdit = null
            },
            sheetState = editSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text(
                    text = "Ratenzahlung bearbeiten",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                Text(
                    text = "$paidCount von ${group.totalCount} Raten gebucht · Beschreibung und Kategorie " +
                        "gelten für alle Raten, Betrag nur für offene",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text("Beschreibung") },
                    isError = editDescriptionError != null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = finColors.surfaceHi,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (editDescriptionError != null) {
                    Text(
                        editDescriptionError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Rate amount (open rates) + total count side by side
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editAmountInput,
                        onValueChange = { editAmountInput = it },
                        label = { Text("Ratenbetrag") },
                        supportingText = { Text("nur offene Raten") },
                        isError = editAmountError != null,
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = finColors.surfaceHi,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = editCountInput,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) editCountInput = newValue
                        },
                        label = { Text("Anzahl Raten") },
                        supportingText = { Text("min. $minCount") },
                        isError = editCountError != null,
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = finColors.surfaceHi,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (editAmountError != null) {
                    Text(editAmountError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                if (editCountError != null) {
                    Text(editCountError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Kategorie",
                    style = MaterialTheme.typography.labelMedium,
                    color = finColors.textSub,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                CategoryChipPicker(
                    categories = categories,
                    selectedName = editCategory,
                    onSelect = { editCategory = it },
                    onAddCategory = onAddCategory,
                )

                Spacer(Modifier.height(20.dp))

                GradientButton(
                    text = "Änderungen speichern",
                    onClick = {
                        val absAmount = editAmountInput.replace(',', '.').toDoubleOrNull()
                        val count = editCountInput.toIntOrNull()
                        var hasError = false
                        if (editDescription.isBlank()) {
                            editDescriptionError = "Beschreibung darf nicht leer sein"
                            hasError = true
                        } else editDescriptionError = null
                        if (absAmount == null || absAmount <= 0) {
                            editAmountError = "Bitte einen gültigen Betrag eingeben"
                            hasError = true
                        } else editAmountError = null
                        if (count == null || count < minCount) {
                            editCountError = "Mindestens $minCount ($paidCount bereits gebucht)"
                            hasError = true
                        } else editCountError = null
                        if (!hasError && absAmount != null && count != null) {
                            // The sign of the group (expense vs. income) is kept as-is.
                            val sign = if (group.installmentAmount < 0) -1.0 else 1.0
                            onUpdateGroup(
                                group.groupId,
                                editDescription.trim(),
                                editCategory.ifBlank { null },
                                sign * absAmount,
                                count,
                            )
                            groupToEdit = null
                        }
                    },
                )

                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = {
                        groupToDelete = group
                        groupToEdit = null
                    }) {
                        Text("Ratenzahlung löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    // Confirmation before deleting a whole installment group (destructive!)
    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("Ratenzahlung löschen?") },
            text = {
                Text(
                    "„${group.description}“ wird mit allen ${group.totalCount} Raten-Buchungen " +
                        "gelöscht (auch bereits gebuchte). Das kann nicht rückgängig gemacht werden."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteGroup(group.groupId)
                    groupToDelete = null
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
fun InstallmentGroupCard(
    group: InstallmentGroup,
    category: Category?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val isCompleted = group.remainingCount <= 0
    val paidCount = group.totalCount - group.remainingCount
    val progress = if (group.totalCount > 0) paidCount.toFloat() / group.totalCount else 1f
    val progressColor = if (isCompleted) finColors.income else MaterialTheme.colorScheme.primary

    FinCard(onClick = onOpen, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (category != null) CategoryChip(category = category, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = "Gesamt: ${MoneyFormat.currencySigned(group.totalAmount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
            }
            if (isCompleted) StatusPill(text = "Abgeschlossen", color = finColors.income)
            MoneyText(amount = group.installmentAmount, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(10.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isCompleted) "Alle Raten bezahlt" else
                    stringResource(R.string.installments_remaining, group.remainingCount, group.totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = if (isCompleted) finColors.income else finColors.textSub,
            )
            val nextDate = group.nextDate
            if (nextDate != null && !isCompleted) {
                Text(
                    text = "Nächste Rate: ${nextDate.toJavaLocalDate().format(DATE_FORMATTER)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = finColors.textSub,
                )
            }
        }
    }
}
