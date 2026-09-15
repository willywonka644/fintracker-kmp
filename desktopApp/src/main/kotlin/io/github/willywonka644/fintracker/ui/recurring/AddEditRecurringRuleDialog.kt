package io.github.willywonka644.fintracker.ui.recurring

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.common.DesktopDatePickerField
import io.github.willywonka644.fintracker.util.parseGermanDate
import io.github.willywonka644.fintracker.util.toGermanDateString
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private fun frequencyLabel(f: Frequency) = when (f) {
    Frequency.WEEKLY -> "Wöchentlich"
    Frequency.MONTHLY -> "Monatlich"
    Frequency.YEARLY -> "Jährlich"
}

// ── Add/edit dialog ──────────────────────────────────────────────────────────

/**
 * Semantics tags for UI tests. "Abbrechen" and "Löschen" appear in both this
 * dialog and its delete-confirmation, so the tests address nodes by tag.
 */
object RecurringRuleDialogTestTags {
    const val AMOUNT = "rule_amount"
    const val DESCRIPTION = "rule_description"
    const val SAVE = "rule_save"
    const val AMOUNT_ERROR = "rule_amount_error"
    const val DESCRIPTION_ERROR = "rule_description_error"
    const val DELETE = "rule_delete"
    const val DELETE_CONFIRM = "rule_delete_confirm"
    const val DELETE_CONFIRM_TEXT = "rule_delete_confirm_text"
}

@Composable
fun AddEditRecurringRuleDialog(
    initialRule: RecurringRule?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (RecurringRule) -> Unit,
    onDelete: ((RecurringRule) -> Unit)? = null,
) {
    val isEdit = initialRule != null

    var amountInput by remember {
        mutableStateOf(initialRule?.amount?.let { if (it == 0.0) "" else it.toString() } ?: "")
    }
    var description by remember { mutableStateOf(initialRule?.description ?: "") }
    var selectedAccountId by remember {
        mutableStateOf(initialRule?.accountId ?: accounts.firstOrNull()?.id ?: "")
    }
    var selectedFrequency by remember { mutableStateOf(initialRule?.frequency ?: Frequency.MONTHLY) }
    var selectedNextDate by remember {
        mutableStateOf(initialRule?.nextExecutionDate ?: Clock.System.todayIn(TimeZone.currentSystemDefault()))
    }
    var selectedCategory by remember {
        mutableStateOf(initialRule?.category ?: "")
    }

    var amountError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun validateAndSave() {
        val amount = amountInput.replace(',', '.').toDoubleOrNull()
        if (amount == null) { amountError = "Gültigen Betrag eingeben"; return }
        amountError = null
        if (description.isBlank()) { descriptionError = "Beschreibung darf nicht leer sein"; return }
        descriptionError = null
        onSave(
            RecurringRule(
                id = initialRule?.id ?: "",
                amount = amount,
                description = description.trim(),
                accountId = selectedAccountId,
                frequency = selectedFrequency,
                nextExecutionDate = selectedNextDate,
                // "Begrenzt" was removed from the UI: finite series are what
                // Ratenzahlungen are for. Editing keeps an existing value.
                remainingExecutions = initialRule?.remainingExecutions,
                category = selectedCategory.ifBlank { null },
                lastModifiedAt = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 360.dp, max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Title
                Text(
                    text = if (isEdit) "Dauerauftrag bearbeiten" else "Dauerauftrag erstellen",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                // Scrollable content — never taller than 480dp so buttons always stay visible
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.heightIn(max = 480.dp).weight(1f, fill = false)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { amountInput = it },
                            label = { Text("Betrag") },
                            isError = amountError != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag(RecurringRuleDialogTestTags.AMOUNT),
                        )
                        if (amountError != null) Text(
                            amountError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(RecurringRuleDialogTestTags.AMOUNT_ERROR),
                        )

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Beschreibung") },
                            isError = descriptionError != null,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag(RecurringRuleDialogTestTags.DESCRIPTION),
                        )
                        if (descriptionError != null) Text(
                            descriptionError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(RecurringRuleDialogTestTags.DESCRIPTION_ERROR),
                        )

                        // Account picker
                        Text("Konto", style = MaterialTheme.typography.labelMedium)
                        Box {
                            OutlinedButton(onClick = { accountDropdownExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(accounts.firstOrNull { it.id == selectedAccountId }?.name ?: "Konto wählen")
                            }
                            DropdownMenu(expanded = accountDropdownExpanded, onDismissRequest = { accountDropdownExpanded = false }) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(text = { Text(acc.name) }, onClick = { selectedAccountId = acc.id; accountDropdownExpanded = false })
                                }
                            }
                        }

                        // Frequency
                        Text("Frequenz", style = MaterialTheme.typography.labelMedium)
                        Frequency.entries.forEach { freq ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedFrequency = freq }) {
                                RadioButton(selected = selectedFrequency == freq, onClick = { selectedFrequency = freq })
                                Text(frequencyLabel(freq))
                            }
                        }

                        // Next date (simple text field for desktop)
                        DesktopDateField(
                            label = "Nächste Ausführung (TT.MM.JJJJ)",
                            date = selectedNextDate,
                            onDateChange = { selectedNextDate = it },
                        )

                        // Category
                        if (categories.isNotEmpty()) {
                            HorizontalDivider()
                            Text("Kategorie (optional)", style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedCategory = "" }) {
                                RadioButton(selected = selectedCategory.isBlank(), onClick = { selectedCategory = "" })
                                Text("Keine")
                            }
                            categories.forEach { cat ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedCategory = cat.name }) {
                                    RadioButton(selected = selectedCategory == cat.name, onClick = { selectedCategory = cat.name })
                                    Text(cat.name)
                                }
                            }
                        }

                        if (isEdit && onDelete != null) {
                            HorizontalDivider()
                            TextButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.testTag(RecurringRuleDialogTestTags.DELETE),
                            ) {
                                Text("Dauerauftrag löschen", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }

                // Buttons — always visible, outside the scrollable area
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { validateAndSave() },
                        modifier = Modifier.testTag(RecurringRuleDialogTestTags.SAVE),
                    ) { Text("Speichern") }
                }
            }
        }
    }

    if (showDeleteConfirm && initialRule != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Dauerauftrag löschen?") },
            text = {
                Text(
                    "\"${initialRule.description}\" wird mit allen geplanten Buchungen gelöscht. Bereits gebuchte Zahlungen bleiben erhalten.",
                    modifier = Modifier.testTag(RecurringRuleDialogTestTags.DELETE_CONFIRM_TEXT),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(initialRule); showDeleteConfirm = false },
                    modifier = Modifier.testTag(RecurringRuleDialogTestTags.DELETE_CONFIRM),
                ) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") } },
        )
    }
}

/** Date entry with calendar picker for desktop. */
@Composable
private fun DesktopDateField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    var text by remember(date) { mutableStateOf(date.toGermanDateString()) }
    var error by remember { mutableStateOf<String?>(null) }

    DesktopDatePickerField(
        value = text,
        onValueChange = { raw ->
            text = raw
            val parsed = parseGermanDate(raw)
            if (parsed != null) { onDateChange(parsed); error = null }
            else error = "Format: TT.MM.JJJJ"
        },
        label = label,
        isError = error != null,
        selectedDate = date,
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}
