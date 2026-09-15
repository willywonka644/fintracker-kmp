package io.github.willywonka644.fintracker.ui.reconciliation

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.reconciliation.ReconciliationService
import io.github.willywonka644.fintracker.ui.common.DesktopDatePickerField
import io.github.willywonka644.fintracker.util.MoneyFormat
import io.github.willywonka644.fintracker.util.parseGermanDate
import io.github.willywonka644.fintracker.util.toGermanDateString
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Semantics tags for UI tests. Text-based finders would break on the German
 * labels (and on the locale-formatted amounts), so the few nodes the tests
 * interact with carry stable tags instead.
 */
object ReconciliationTestTags {
    const val EXPECTED_BALANCE = "recon_expected_balance"
    const val ACTUAL_INPUT = "recon_actual_input"
    const val SUBMIT = "recon_submit"
    const val RESULT_MESSAGE = "recon_result"
}

@Composable
fun ReconciliationDialog(
    accountId: String,
    accountName: String,
    allBookings: List<Booking>,
    nextBookingId: () -> String,
    onDismiss: () -> Unit,
    onCreateCorrectionBooking: (Booking) -> Unit,
) {
    var selectedDate by remember { mutableStateOf(Clock.System.todayIn(TimeZone.currentSystemDefault())) }
    var dateText by remember { mutableStateOf(selectedDate.toGermanDateString()) }
    var dateError by remember { mutableStateOf<String?>(null) }
    var actualBalanceText by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val expectedBalance = remember(allBookings, accountId, selectedDate) {
        ReconciliationService.computeExpectedBalance(allBookings, accountId, selectedDate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kontoabgleich — $accountName") },
        text = {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.widthIn(min = 360.dp).heightIn(max = 480.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(end = 12.dp),
            ) {
                Text(
                    text = "Gib deinen tatsächlichen Kontostand laut Bankauszug ein.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Date field
                DesktopDatePickerField(
                    value = dateText,
                    onValueChange = { raw ->
                        dateText = raw
                        val parsed = parseGermanDate(raw)
                        if (parsed != null) { selectedDate = parsed; dateError = null }
                        else dateError = "Format: TT.MM.JJJJ"
                    },
                    label = "Stichtag (TT.MM.JJJJ)",
                    isError = dateError != null,
                    selectedDate = selectedDate,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dateError != null) Text(dateError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                // Expected balance display
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("  Erwarteter Saldo", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = MoneyFormat.currency(expectedBalance) + "  ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag(ReconciliationTestTags.EXPECTED_BALANCE),
                        )
                    }
                }

                // Actual balance input
                OutlinedTextField(
                    value = actualBalanceText,
                    onValueChange = { actualBalanceText = it.filter { c -> c.isDigit() || c == '-' || c == ',' || c == '.' } },
                    label = { Text("Tatsächlicher Kontostand (EUR)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag(ReconciliationTestTags.ACTUAL_INPUT),
                )

                if (resultMessage != null) {
                    HorizontalDivider()
                    Text(
                        text = resultMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(ReconciliationTestTags.RESULT_MESSAGE),
                    )
                }

                Button(
                    onClick = {
                        val actualBalance = actualBalanceText.replace(',', '.').toDoubleOrNull()
                        if (actualBalance == null) { resultMessage = "Ungültiger Betrag"; return@Button }
                        val correction = ReconciliationService.createCorrectionBooking(
                            accountId = accountId,
                            date = selectedDate,
                            expectedBalance = expectedBalance,
                            actualBalance = actualBalance,
                            idProvider = nextBookingId,
                        )
                        if (correction == null) {
                            resultMessage = "Kein Unterschied — kein Korrekturbuchung notwendig."
                        } else {
                            onCreateCorrectionBooking(correction)
                            resultMessage = "Korrektur erstellt: ${MoneyFormat.currencySigned(correction.amount)}"
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag(ReconciliationTestTags.SUBMIT),
                ) {
                    Text("Abgleich durchführen")
                }

                // History
                val history = remember(allBookings, accountId) {
                    ReconciliationService.getReconciliationHistory(allBookings, accountId)
                }
                if (history.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Verlauf", style = MaterialTheme.typography.labelMedium)
                    history.take(5).forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(entry.date.toString(), style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = MoneyFormat.currencySigned(entry.correctionAmount),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
