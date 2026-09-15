package io.github.willywonka644.fintracker.ui.installments

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.R
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.form.FieldState
import io.github.willywonka644.fintracker.form.hasContentWorthKeeping
import kotlinx.coroutines.launch
import io.github.willywonka644.fintracker.ui.booking.CategoryChipPicker
import io.github.willywonka644.fintracker.ui.booking.DirectionToggle
import io.github.willywonka644.fintracker.ui.booking.GradientButton
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

private val MIN_DATE_MILLIS = LocalDate.of(2026, 1, 1)
    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

data class InstallmentInput(
    val totalAmount: Double,
    val installmentCount: Int,
    val description: String,
    val startDate: LocalDate,
    val frequency: Frequency,
    val category: String?
)

/**
 * Create sheet for installment plans, styled like the AddBookingSheet.
 * The amount is entered as an absolute value; the Ausgabe/Einnahme toggle
 * carries the sign (no more hand-typed minus).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInstallmentDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (InstallmentInput) -> Unit,
    onAddCategory: (Category) -> Unit = {}
) {
    val finColors = FinTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isExpense by remember { mutableStateOf(true) }
    var totalAmountInput by remember { mutableStateOf("") }
    var installmentCountInput by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedStartDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var selectedFrequency by remember { mutableStateOf(Frequency.MONTHLY) }
    // Keine stille Vorauswahl: Die erste Kategorie im Picker ist
    // "Kreditkartenabrechnung", und eine Ratenzahlung, die niemand kategorisiert hat,
    // landete dort — falsch in der Kategorieverteilung, und unsichtbar falsch,
    // weil das Feld ausgefüllt aussieht. Früher war es schlimmer: Die alte
    // Namensprüfung warf genau diese Kategorie aus jeder Auswertung.
    // Leer heißt: Der Picker zeigt "Kategorie wählen".
    var selectedCategory by remember { mutableStateOf("") }

    var totalAmountError by remember { mutableStateOf<String?>(null) }
    var countError by remember { mutableStateOf<String?>(null) }
    var descriptionError by remember { mutableStateOf<String?>(null) }
    var startDateError by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }

    // ── Schutz vor versehentlichem Verwerfen ─────────────────────────────────
    // Hier steht am meisten auf dem Spiel: Betrag, Ratenzahl, Beschreibung,
    // Startdatum und Intervall. Muss beim Schließen gerechnet werden — Material
    // friert einen vorab berechneten Boolean im Rückruf für die Wischgeste ein.
    val baselineAmount = remember { totalAmountInput }
    val baselineCount = remember { installmentCountInput }
    val baselineDescription = remember { description }
    val baselineStartDate = remember { selectedStartDate?.toString().orEmpty() }
    val baselineFrequency = remember { selectedFrequency.name }
    val baselineCategory = remember { selectedCategory }

    fun hasUnsavedInput(): Boolean = hasContentWorthKeeping(
        openedWithScannedData = false,
        hasNewAttachment = false,
        fields = listOf(
            FieldState(totalAmountInput, baselineAmount),
            FieldState(installmentCountInput, baselineCount),
            FieldState(description, baselineDescription),
            FieldState(selectedStartDate?.toString().orEmpty(), baselineStartDate),
            FieldState(selectedFrequency.name, baselineFrequency),
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

    fun signedAmount(abs: Double): Double = if (isExpense) -abs else abs

    // Per-rate preview
    val absAmountParsed = totalAmountInput.replace(',', '.').toDoubleOrNull()
    val countParsed = installmentCountInput.trim().toIntOrNull()
    val previewText = if (absAmountParsed != null && absAmountParsed > 0 && countParsed != null && countParsed > 0) {
        val perRate = signedAmount(absAmountParsed) / countParsed
        stringResource(R.string.installments_preview, countParsed, MoneyFormat.currencySigned(perRate))
    } else {
        null
    }

    fun validateAndSave() {
        var hasError = false

        val absAmount = totalAmountInput.replace(',', '.').toDoubleOrNull()
        if (absAmount == null || absAmount <= 0) {
            totalAmountError = "Bitte einen gültigen Betrag eingeben"
            hasError = true
        } else {
            totalAmountError = null
        }

        val count = installmentCountInput.trim().toIntOrNull()
        if (count == null || count < 2) {
            countError = "Mindestens 2 Raten"
            hasError = true
        } else {
            countError = null
        }

        if (description.isBlank()) {
            descriptionError = "Beschreibung darf nicht leer sein"
            hasError = true
        } else {
            descriptionError = null
        }

        if (selectedStartDate == null) {
            startDateError = "Bitte ein Datum wählen"
            hasError = true
        } else {
            startDateError = null
        }

        if (!hasError && absAmount != null && count != null && selectedStartDate != null) {
            onSave(
                InstallmentInput(
                    totalAmount = signedAmount(absAmount),
                    installmentCount = count,
                    description = description.trim(),
                    startDate = selectedStartDate!!,
                    frequency = selectedFrequency,
                    category = selectedCategory.ifBlank { null }
                )
            )
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
                text = stringResource(R.string.installments_create_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            DirectionToggle(
                isExpense = isExpense,
                onIsExpenseChange = { isExpense = it },
                finColors = finColors,
            )

            Spacer(Modifier.height(10.dp))

            // Total amount + count side by side
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = totalAmountInput,
                    onValueChange = { totalAmountInput = it },
                    label = { Text("Gesamtbetrag") },
                    isError = totalAmountError != null,
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = installmentCountInput,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() }) {
                            installmentCountInput = newValue
                        }
                    },
                    label = { Text("Anzahl Raten") },
                    placeholder = { Text("z. B. 3") },
                    isError = countError != null,
                    singleLine = true,
                    shape = fieldShape,
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            if (totalAmountError != null) {
                Text(totalAmountError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            if (countError != null) {
                Text(countError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            if (previewText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.installments_description)) },
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

            // Frequency
            Text(
                text = stringResource(R.string.installments_frequency),
                style = MaterialTheme.typography.labelMedium,
                color = finColors.textSub,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Frequency.entries.forEach { freq ->
                    FilterChip(
                        selected = selectedFrequency == freq,
                        onClick = { selectedFrequency = freq },
                        label = {
                            Text(
                                when (freq) {
                                    Frequency.WEEKLY -> "Wöchentlich"
                                    Frequency.MONTHLY -> "Monatlich"
                                    Frequency.YEARLY -> "Jährlich"
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Start date
            OutlinedTextField(
                value = selectedStartDate?.format(DATE_FORMAT) ?: "",
                onValueChange = {},
                label = { Text(stringResource(R.string.installments_start_date)) },
                placeholder = { Text("Datum wählen") },
                isError = startDateError != null,
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
            if (startDateError != null) {
                Text(startDateError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(10.dp))

            // Category
            Text(
                text = "Kategorie",
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
                text = "Ratenzahlung speichern",
                onClick = { validateAndSave() },
            )
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val initialMillis = selectedStartDate?.let {
            it.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
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
                        selectedStartDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        startDateError = null
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
}
