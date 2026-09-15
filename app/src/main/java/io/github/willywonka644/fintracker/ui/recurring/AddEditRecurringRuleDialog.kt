package io.github.willywonka644.fintracker.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.R
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.form.FieldState
import io.github.willywonka644.fintracker.form.hasContentWorthKeeping
import io.github.willywonka644.fintracker.ui.booking.CategoryChipPicker
import io.github.willywonka644.fintracker.ui.booking.DirectionToggle
import io.github.willywonka644.fintracker.ui.booking.GradientButton
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

// Earliest selectable date: January 1, 2026
private val MIN_DATE_MILLIS = LocalDate.of(2026, 1, 1)
    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun localDateToEpochMillis(date: kotlinx.datetime.LocalDate): Long =
    date.toJavaLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun epochMillisToLocalDate(millis: Long): kotlinx.datetime.LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toKotlinLocalDate()

/**
 * Add/edit sheet for recurring rules, styled like the AddBookingSheet
 * (ModalBottomSheet, direction toggle, chip category picker, gradient save button).
 * Amounts are entered as absolute values; the Ausgabe/Einnahme toggle carries the sign.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecurringRuleDialog(
    initialRule: RecurringRule?,
    accounts: List<Account>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (RecurringRule) -> Unit,
    onDelete: ((RecurringRule) -> Unit)? = null,
    onAddCategory: (Category) -> Unit = {},
) {
    val isEdit = initialRule != null
    val finColors = FinTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isExpense by remember { mutableStateOf(initialRule?.let { it.amount < 0 } ?: true) }
    var amountInput by remember {
        mutableStateOf(
            initialRule?.amount?.let { if (it == 0.0) "" else abs(it).toString() } ?: ""
        )
    }
    var description by remember { mutableStateOf(initialRule?.description ?: "") }
    var selectedAccountId by remember {
        mutableStateOf(initialRule?.accountId ?: accounts.firstOrNull()?.id ?: "")
    }
    var selectedFrequency by remember {
        mutableStateOf(initialRule?.frequency ?: Frequency.MONTHLY)
    }
    var selectedNextDate by remember {
        mutableStateOf(initialRule?.nextExecutionDate)
    }
    // Keine stille Vorauswahl: Die erste Kategorie im Picker ist
    // "Kreditkartenabrechnung", und ein Dauerauftrag, den niemand kategorisiert hat,
    // landete dort — falsch in der Kategorieverteilung, und unsichtbar falsch,
    // weil das Feld ausgefüllt aussieht. Früher war es schlimmer: Die alte
    // Namensprüfung warf genau diese Kategorie aus jeder Auswertung.
    // Leer heißt: Der Picker zeigt "Kategorie wählen".
    var selectedCategory by remember {
        mutableStateOf(initialRule?.category ?: "")
    }

    // Error states
    var amountError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var nextDateError by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // ── Schutz vor versehentlichem Verwerfen ─────────────────────────────────
    // Gleiche Regel wie im Buchungs-Sheet. Muss beim Schließen gerechnet werden:
    // Material merkt sich für die Wischgeste einen Rückruf aus dem Aufbau des
    // Sheets, ein vorab berechneter Boolean bliebe darin für immer der von damals.
    val baselineAmount = remember { amountInput }
    val baselineDescription = remember { description }
    val baselineAccount = remember { selectedAccountId }
    val baselineFrequency = remember { selectedFrequency.name }
    val baselineNextDate = remember { selectedNextDate?.toString().orEmpty() }
    val baselineCategory = remember { selectedCategory }

    fun hasUnsavedInput(): Boolean = hasContentWorthKeeping(
        openedWithScannedData = false,
        hasNewAttachment = false,
        fields = listOf(
            FieldState(amountInput, baselineAmount),
            FieldState(description, baselineDescription),
            FieldState(selectedAccountId, baselineAccount),
            FieldState(selectedFrequency.name, baselineFrequency),
            FieldState(selectedNextDate?.toString().orEmpty(), baselineNextDate),
            FieldState(selectedCategory, baselineCategory),
        ),
    )

    var showDiscardConfirm by remember { mutableStateOf(false) }
    val discardScope = rememberCoroutineScope()

    /** Material blendet das Sheet vor onDismissRequest bereits aus — zurückholen. */
    fun keepEditing() {
        showDiscardConfirm = false
        discardScope.launch { sheetState.show() }
    }

    fun validateAndSave() {
        var hasError = false

        val absAmount = amountInput.replace(',', '.').toDoubleOrNull()
        if (absAmount == null || absAmount <= 0) {
            amountError = "Bitte einen gültigen Betrag eingeben"
            hasError = true
        } else {
            amountError = null
        }

        if (description.isBlank()) {
            descriptionError = "Beschreibung darf nicht leer sein"
            hasError = true
        } else {
            descriptionError = null
        }

        if (selectedAccountId.isBlank() || accounts.none { it.id == selectedAccountId }) {
            accountError = "Bitte ein Konto auswählen"
            hasError = true
        } else {
            accountError = null
        }

        if (selectedNextDate == null) {
            nextDateError = "Bitte ein Datum auswählen"
            hasError = true
        } else {
            nextDateError = null
        }

        if (!hasError && absAmount != null && selectedNextDate != null) {
            val rule = RecurringRule(
                id = initialRule?.id ?: "",
                amount = if (isExpense) -absAmount else absAmount,
                description = description.trim(),
                accountId = selectedAccountId,
                frequency = selectedFrequency,
                nextExecutionDate = selectedNextDate!!,
                // "Begrenzt" was removed from the UI: finite series are what
                // Ratenzahlungen are for (visible, group-editable, group-deletable).
                // Editing keeps a possibly imported value instead of silently unbounding it.
                remainingExecutions = initialRule?.remainingExecutions,
                // Leer heißt "keine Kategorie" — als null, nicht als "", damit die
                // Auswertungen es wie jede unkategorisierte Buchung behandeln.
                category = selectedCategory.ifBlank { null },
                lastModifiedAt = Clock.System.now().toEpochMilliseconds()
            )
            onSave(rule)
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = finColors.surfaceHi,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
    )
    val fieldShape = MaterialTheme.shapes.medium

    ModalBottomSheet(
        onDismissRequest = {
            if (hasUnsavedInput()) showDiscardConfirm = true else onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = if (isEdit) stringResource(R.string.recurring_edit_title)
                else stringResource(R.string.recurring_add_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            // Direction
            DirectionToggle(
                isExpense = isExpense,
                onIsExpenseChange = { isExpense = it },
                finColors = finColors,
            )

            Spacer(Modifier.height(10.dp))

            // Amount (absolute value; sign comes from the toggle)
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it },
                label = { Text("Betrag") },
                isError = amountError != null,
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (amountError != null) {
                Text(amountError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(10.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.recurring_description_label)) },
                isError = descriptionError != null,
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            if (descriptionError != null) {
                Text(descriptionError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(10.dp))

            // Account
            Text(
                text = stringResource(R.string.recurring_account_label),
                style = MaterialTheme.typography.labelMedium,
                color = finColors.textSub,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Box(modifier = Modifier.fillMaxWidth()) {
                val selectedAccountName = accounts.firstOrNull { it.id == selectedAccountId }?.name
                    ?: "Konto wählen"
                OutlinedButton(
                    onClick = { accountDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                ) {
                    Text(
                        text = selectedAccountName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = accountDropdownExpanded,
                    onDismissRequest = { accountDropdownExpanded = false },
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            onClick = {
                                selectedAccountId = account.id
                                accountDropdownExpanded = false
                            },
                        )
                    }
                }
            }
            if (accountError != null) {
                Text(accountError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(10.dp))

            // Frequency
            Text(
                text = stringResource(R.string.recurring_frequency_label),
                style = MaterialTheme.typography.labelMedium,
                color = finColors.textSub,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Frequency.entries.forEach { freq ->
                    FilterChip(
                        selected = selectedFrequency == freq,
                        onClick = { selectedFrequency = freq },
                        label = { Text(frequencyDisplayName(freq), style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Next execution date
            OutlinedTextField(
                value = selectedNextDate?.toJavaLocalDate()?.format(DATE_FORMAT) ?: "",
                onValueChange = {},
                label = { Text(stringResource(R.string.recurring_next_date_label)) },
                placeholder = { Text("Datum wählen") },
                isError = nextDateError != null,
                readOnly = true,
                singleLine = true,
                shape = fieldShape,
                colors = fieldColors,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Datum wählen")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (nextDateError != null) {
                Text(nextDateError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(10.dp))

            // Category
            Text(
                text = stringResource(R.string.recurring_category_label),
                style = MaterialTheme.typography.labelMedium,
                color = finColors.textSub,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            CategoryChipPicker(
                categories = categories,
                selectedName = selectedCategory,
                onSelect = { selectedCategory = it },
                onAddCategory = onAddCategory,
            )

            Spacer(Modifier.height(20.dp))

            GradientButton(
                text = if (isEdit) "Änderungen speichern" else "Dauerauftrag speichern",
                onClick = { validateAndSave() },
            )

            // Delete option (edit mode only)
            if (isEdit && onDelete != null) {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(
                            text = stringResource(R.string.recurring_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val initialMillis = selectedNextDate?.let { localDateToEpochMillis(it) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = 2026..2100,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis >= MIN_DATE_MILLIS
                }

                override fun isSelectableYear(year: Int): Boolean {
                    return year >= 2026
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedNextDate = epochMillisToLocalDate(millis)
                        nextDateError = null
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Delete confirmation dialog
    if (showDiscardConfirm) {
        AlertDialog(
            // Auch das Wegtippen dieses Dialogs bedeutet "weiter bearbeiten".
            onDismissRequest = { keepEditing() },
            title = { Text("Eingaben verwerfen?") },
            text = { Text("Was du bisher eingegeben hast, geht dabei verloren.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onDismiss() }) {
                    Text("Verwerfen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { keepEditing() }) { Text("Weiter bearbeiten") }
            }
        )
    }

    if (showDeleteConfirm && initialRule != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.recurring_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.recurring_delete_confirm_body,
                        initialRule.description
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(initialRule)
                        showDeleteConfirm = false
                    }
                ) {
                    Text(
                        stringResource(R.string.recurring_delete_confirm_action),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
