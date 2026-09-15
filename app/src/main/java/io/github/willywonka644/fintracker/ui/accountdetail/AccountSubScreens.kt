package io.github.willywonka644.fintracker.ui.accountdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.text.input.KeyboardType
import io.github.willywonka644.fintracker.ui.components.FinCard
import io.github.willywonka644.fintracker.ui.components.MoneyText
import io.github.willywonka644.fintracker.ui.components.SectionHeader
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatryk.vico.compose.axis.axisLabelComponent
import com.patrykandpatryk.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatryk.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatryk.vico.compose.chart.Chart
import com.patrykandpatryk.vico.compose.chart.column.columnChart
import com.patrykandpatryk.vico.compose.chart.line.lineChart
import com.patrykandpatryk.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatryk.vico.compose.m3.style.m3ChartStyle
import com.patrykandpatryk.vico.compose.style.ProvideChartStyle
import com.patrykandpatryk.vico.core.axis.AxisPosition
import com.patrykandpatryk.vico.core.axis.AxisItemPlacer
import com.patrykandpatryk.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatryk.vico.core.chart.column.ColumnChart
import com.patrykandpatryk.vico.core.chart.line.LineChart
import com.patrykandpatryk.vico.core.component.marker.MarkerComponent
import com.patrykandpatryk.vico.core.component.shape.LineComponent
import com.patrykandpatryk.vico.core.component.shape.ShapeComponent
import com.patrykandpatryk.vico.core.component.shape.Shapes
import com.patrykandpatryk.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatryk.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatryk.vico.core.component.text.TextComponent
import com.patrykandpatryk.vico.core.dimensions.MutableDimensions
import com.patrykandpatryk.vico.core.chart.layout.HorizontalLayout
import com.patrykandpatryk.vico.core.entry.FloatEntry
import com.patrykandpatryk.vico.core.entry.entryModelOf
import com.patrykandpatryk.vico.core.marker.Marker
import com.patrykandpatryk.vico.core.marker.MarkerLabelFormatter
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.analytics.BalanceDataPoint
import io.github.willywonka644.fintracker.analytics.CategoryBreakdownEntry
import io.github.willywonka644.fintracker.analytics.MonthlyIncomeExpense
import io.github.willywonka644.fintracker.analytics.calculateBalanceOverTime
import io.github.willywonka644.fintracker.analytics.calculateCategoryBreakdown
import io.github.willywonka644.fintracker.analytics.calculateMonthlyIncomeExpense
import io.github.willywonka644.fintracker.analytics.currentMonthFilter
import io.github.willywonka644.fintracker.analytics.customRangeFilter
import io.github.willywonka644.fintracker.analytics.filterBookingsForAccount
import io.github.willywonka644.fintracker.analytics.calculateBillingCycleIncomeExpense
import io.github.willywonka644.fintracker.analytics.generateBillingCyclePeriods
import io.github.willywonka644.fintracker.analytics.last90DaysFilter
import io.github.willywonka644.fintracker.analytics.lastMonthFilter
import io.github.willywonka644.fintracker.analytics.sumExpenses
import io.github.willywonka644.fintracker.analytics.sumIncome
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.reconciliation.ReconciliationService
import io.github.willywonka644.fintracker.util.MoneyFormat
import java.time.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KontoabgleichScreen(
    accountId: String,
    accountName: String,
    allBookings: List<Booking>,
    onNavigateBack: () -> Unit,
    onCreateCorrectionBooking: (Booking) -> Unit
) {
    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy", java.util.Locale.GERMANY) }

    // Input state
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var actualBalanceText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<ReconciliationResult?>(null) }

    // Reconciliation history
    val history = remember(allBookings, accountId) {
        ReconciliationService.getReconciliationHistory(allBookings, accountId)
    }

    // Expected balance at the selected date
    val expectedBalance = remember(allBookings, accountId, selectedDate) {
        ReconciliationService.computeExpectedBalance(allBookings, accountId, selectedDate.toKotlinLocalDate())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kontoabgleich") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        val finColors = FinTheme.colors
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Account name header
            item {
                Text(
                    text = accountName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Explanation
            item {
                FinCard {
                    Text(
                        text = "Gib deinen tatsächlichen Kontostand laut Bankauszug ein. " +
                            "FinTracker erstellt bei Bedarf eine Korrektur-Buchung, um die Differenz auszugleichen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = finColors.textSub,
                    )
                }
            }

            // Stichtag row
            item {
                FinCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Stichtag", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { showDatePicker = true }) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(selectedDate.format(dateFormatter))
                        }
                    }
                }
            }

            // Expected balance display
            item {
                FinCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Erwarteter Saldo",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        MoneyText(
                            amount = expectedBalance,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            // Actual balance input
            item {
                OutlinedTextField(
                    value = actualBalanceText,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { c -> c.isDigit() || c == '-' || c == ',' || c == '.' }
                        actualBalanceText = filtered
                    },
                    label = { Text("Tatsächlicher Kontostand (EUR)") },
                    placeholder = { Text("z.B. 1842,50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Difference preview + Reconcile button
            item {
                val parsedAmount = remember(actualBalanceText) {
                    actualBalanceText.replace(",", ".").toDoubleOrNull()
                }
                val difference = if (parsedAmount != null) parsedAmount - expectedBalance else null

                if (parsedAmount != null && difference != null) {
                    val isMatch = kotlin.math.abs(difference) < 0.005
                    FinCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "Differenz", style = MaterialTheme.typography.bodyMedium)
                            MoneyText(
                                amount = difference,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isMatch) {
                        Text(
                            text = "Der Saldo stimmt bereits überein — keine Korrektur nötig.",
                            style = MaterialTheme.typography.bodySmall,
                            color = finColors.income,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                val canReconcile = run {
                    val p = actualBalanceText.replace(",", ".").toDoubleOrNull()
                    p != null && kotlin.math.abs(p - expectedBalance) >= 0.005
                }

                Button(
                    onClick = { showConfirmation = true },
                    enabled = canReconcile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CompareArrows, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abgleich durchführen")
                }
            }

            // Last result feedback
            item {
                lastResult?.let { result ->
                    FinCard {
                        Text(
                            text = "Abgleich erfolgreich",
                            style = MaterialTheme.typography.titleSmall,
                            color = finColors.income,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Korrektur: ${MoneyFormat.currencySigned(result.correctionAmount)} am ${result.date.format(dateFormatter)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // Reconciliation history
            if (history.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionHeader(title = "Abgleich-Verlauf")
                }
                items(history.size) { index ->
                    val entry = history[index]
                    FinCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = entry.date.toJavaLocalDate().format(dateFormatter),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            MoneyText(
                                amount = entry.correctionAmount,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Erwartet: ${MoneyFormat.currency(entry.expectedBalance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = finColors.textSub,
                            )
                            Text(
                                text = "Tatsächlich: ${MoneyFormat.currency(entry.actualBalance)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = finColors.textSub,
                            )
                        }
                    }
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        val initialMillis = selectedDate
            .atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Confirmation dialog
    if (showConfirmation) {
        val parsedAmount = actualBalanceText.replace(",", ".").toDoubleOrNull() ?: 0.0
        val difference = parsedAmount - expectedBalance

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Abgleich bestätigen") },
            text = {
                Column {
                    Text("Soll eine Korrektur-Buchung erstellt werden?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Stichtag: ${selectedDate.format(dateFormatter)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Erwartet: ${MoneyFormat.currency(expectedBalance)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Tatsächlich: ${MoneyFormat.currency(parsedAmount)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Korrektur: ${MoneyFormat.currencySigned(difference)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val correctionBooking = ReconciliationService.createCorrectionBooking(
                        accountId = accountId,
                        date = selectedDate.toKotlinLocalDate(),
                        expectedBalance = expectedBalance,
                        actualBalance = parsedAmount,
                        idProvider = { java.util.UUID.randomUUID().toString() }
                    )
                    if (correctionBooking != null) {
                        onCreateCorrectionBooking(correctionBooking)
                        lastResult = ReconciliationResult(
                            date = selectedDate,
                            correctionAmount = correctionBooking.amount
                        )
                        actualBalanceText = ""
                    }
                    showConfirmation = false
                }) { Text("Bestätigen") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) { Text("Abbrechen") }
            }
        )
    }
}

private data class ReconciliationResult(
    val date: LocalDate,
    val correctionAmount: Double
)
