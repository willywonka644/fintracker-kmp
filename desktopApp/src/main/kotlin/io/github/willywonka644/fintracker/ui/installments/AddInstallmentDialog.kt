package io.github.willywonka644.fintracker.ui.installments

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.common.DesktopDatePickerField
import io.github.willywonka644.fintracker.util.MoneyFormat
import io.github.willywonka644.fintracker.util.parseGermanDate
import io.github.willywonka644.fintracker.util.toGermanDateString
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

// ── Add Installment Dialog ────────────────────────────────────────────────────

@Composable
fun AddInstallmentDialog(
    accountId: String,
    categories: List<Category>,
    nextBookingId: () -> String,
    onDismiss: () -> Unit,
    onSave: (List<Booking>) -> Unit,
) {
    var totalAmountInput by remember { mutableStateOf("") }
    var countInput by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startDateText by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault()).toGermanDateString()) }
    var selectedFrequency by remember { mutableStateOf(Frequency.MONTHLY) }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull()?.name ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun frequencyLabel(f: Frequency) = when (f) {
        Frequency.WEEKLY -> "Wöchentlich"
        Frequency.MONTHLY -> "Monatlich"
        Frequency.YEARLY -> "Jährlich"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ratenzahlung erstellen") },
        text = {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.widthIn(min = 360.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp),
            ) {
                OutlinedTextField(
                    value = totalAmountInput,
                    onValueChange = { totalAmountInput = it },
                    label = { Text("Gesamtbetrag") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = countInput,
                    onValueChange = { if (it.all(Char::isDigit)) countInput = it },
                    label = { Text("Anzahl Raten") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DesktopDatePickerField(
                    value = startDateText,
                    onValueChange = { startDateText = it },
                    label = "Erste Rate am (TT.MM.JJJJ)",
                    selectedDate = parseGermanDate(startDateText),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Frequenz", style = MaterialTheme.typography.labelMedium)
                Frequency.entries.forEach { freq ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedFrequency = freq }) {
                        RadioButton(selected = selectedFrequency == freq, onClick = { selectedFrequency = freq })
                        Text(frequencyLabel(freq))
                    }
                }

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

                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                // Preview
                val total = totalAmountInput.replace(',', '.').toDoubleOrNull()
                val count = countInput.toIntOrNull()
                if (total != null && count != null && count > 0) {
                    Text(
                        text = "Vorschau: $count Raten à ${MoneyFormat.currencySigned(total / count)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = totalAmountInput.replace(',', '.').toDoubleOrNull()
                val count = countInput.toIntOrNull()
                val startDate = parseGermanDate(startDateText)
                if (total == null || count == null || count < 1 || startDate == null || description.isBlank()) {
                    error = "Bitte alle Felder korrekt ausfüllen"; return@TextButton
                }
                val tz = TimeZone.currentSystemDefault()
                val today = Clock.System.todayIn(tz)
                val perRate = total / count
                val groupId = "inst_${Clock.System.now().toEpochMilliseconds()}"
                val newBookings = mutableListOf<Booking>()
                var currentDate: LocalDate = startDate!!
                for (i in 0 until count) {
                    val timestamp = currentDate.atStartOfDayIn(tz).toEpochMilliseconds()
                    val status = if (currentDate > today) BookingStatus.SCHEDULED else BookingStatus.POSTED
                    newBookings.add(
                        Booking(
                            id = nextBookingId(),
                            accountId = accountId,
                            amount = perRate,
                            description = "$description ${i + 1}/$count",
                            timestamp = timestamp,
                            category = selectedCategory.ifBlank { null },
                            status = status,
                            effectiveDate = timestamp,
                            installmentGroupId = groupId,
                            lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                    currentDate = when (selectedFrequency) {
                        Frequency.WEEKLY -> currentDate.plus(1, DateTimeUnit.WEEK)
                        Frequency.MONTHLY -> currentDate.plus(1, DateTimeUnit.MONTH)
                        Frequency.YEARLY -> currentDate.plus(1, DateTimeUnit.YEAR)
                    }
                }
                onSave(newBookings)
            }) { Text("Erstellen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
