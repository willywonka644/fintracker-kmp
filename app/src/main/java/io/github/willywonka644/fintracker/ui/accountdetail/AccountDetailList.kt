package io.github.willywonka644.fintracker.ui.accountdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.analytics.SelectablePeriod
import io.github.willywonka644.fintracker.util.MoneyFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookingListItem(
    booking: Booking,
    isPlanned: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {}
) {
    val formattedAmount = remember(booking.amount) { MoneyFormat.currencySigned(booking.amount) }

    val isReconciliation = booking.source == BookingSource.RECONCILIATION
    val effectivelyPlanned = isPlanned || booking.status == BookingStatus.SCHEDULED
    val plannedAlpha = if (effectivelyPlanned) 0.5f else 1f

    val amountColor = when {
        effectivelyPlanned -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isReconciliation -> Color(0xFF1565C0)
        booking.amount > 0 -> Color(0xFF2E7D32)
        booking.amount < 0 -> Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY) }
    val dateText = dateFormatter.format(Date(booking.timestamp))

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* later */ },
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = when {
            effectivelyPlanned -> CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            isReconciliation -> CardDefaults.cardColors(
                containerColor = Color(0xFF1565C0).copy(alpha = 0.08f)
            )
            else -> CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val descriptionText = when {
                    booking.description.isNotBlank() -> booking.description
                    !booking.merchantName.isNullOrBlank() -> booking.merchantName!!
                    !booking.rawDescription.isNullOrBlank() -> booking.rawDescription!!
                    else -> "Keine Beschreibung"
                }
                Text(
                    text = descriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = plannedAlpha)
                )
                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = plannedAlpha)
                    )
                    if (effectivelyPlanned) {
                        Text(
                            text = "Geplant",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                    if (isReconciliation) {
                        Text(
                            text = "Korrektur",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1565C0)
                        )
                    }
                    if (booking.recurringRuleId != null && booking.installmentGroupId == null) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Dauerauftrag",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = plannedAlpha)
                        )
                    }
                    if (!booking.attachmentPath.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "Beleg angehängt",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = plannedAlpha)
                        )
                    }
                }

                if (!booking.category.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Kategorie: ${booking.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = plannedAlpha)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (booking.isVerified) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Geprüft",
                        modifier = Modifier
                            .size(15.dp)
                            .padding(end = 4.dp),
                        tint = Color(0xFF4CAF50)
                    )
                }
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = amountColor
                )
            }
        }
    }
}

/**
 * Month-based period selector for Giro, Sparkonto, Tagesgeld accounts.
 * Displays months like "Januar 2026", "Februar 2026", etc.
 */
@Composable
fun MonthPeriodSelector(
    periods: List<SelectablePeriod>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = periods.getOrNull(selectedIndex)?.label ?: ""

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            periods.forEachIndexed { index, period ->
                DropdownMenuItem(
                    text = { Text(period.label) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Billing-cycle-based period selector for Credit Card accounts.
 * Displays cycles like "18.01.–17.02.2026", "18.02.–17.03.2026", etc.
 */
@Composable
fun BillingCyclePeriodSelector(
    periods: List<SelectablePeriod>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = periods.getOrNull(selectedIndex)?.label ?: ""

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(label) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            periods.forEachIndexed { index, period ->
                DropdownMenuItem(
                    text = { Text(period.label) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Flexible period selector for non-credit-card accounts.
 * Options: Dieser Monat, Letzter Monat, Letzte 90 Tage, Individueller Zeitraum.
 */
enum class FlexiblePeriodOption(val label: String) {
    THIS_MONTH("Dieser Monat"),
    LAST_MONTH("Letzter Monat"),
    LAST_90_DAYS("Letzte 90 Tage"),
    CUSTOM("Individueller Zeitraum")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlexiblePeriodSelector(
    selectedOption: FlexiblePeriodOption,
    customFromDate: LocalDate?,
    customToDate: LocalDate?,
    onOptionSelected: (FlexiblePeriodOption) -> Unit,
    onCustomRangeSelected: (from: LocalDate, to: LocalDate) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showFromDatePicker by remember { mutableStateOf(false) }
    var showToDatePicker by remember { mutableStateOf(false) }
    var pendingFromDate by remember { mutableStateOf<LocalDate?>(null) }

    val dateFormat = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY) }

    val displayLabel = when (selectedOption) {
        FlexiblePeriodOption.CUSTOM -> {
            if (customFromDate != null && customToDate != null) {
                "${customFromDate.format(dateFormat)} – ${customToDate.format(dateFormat)}"
            } else selectedOption.label
        }
        else -> selectedOption.label
    }

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(displayLabel) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FlexiblePeriodOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        if (option == FlexiblePeriodOption.CUSTOM) {
                            showFromDatePicker = true
                        } else {
                            onOptionSelected(option)
                        }
                    }
                )
            }
        }
    }

    if (showFromDatePicker) {
        val minDateMillis = LocalDate.of(2026, 1, 1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val initialMillis = (customFromDate ?: LocalDate.now().minusMonths(1))
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val fromPickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = 2026..2100,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minDateMillis
                override fun isSelectableYear(year: Int): Boolean = year >= 2026
            }
        )

        DatePickerDialog(
            onDismissRequest = { showFromDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    fromPickerState.selectedDateMillis?.let { millis ->
                        pendingFromDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showFromDatePicker = false
                    showToDatePicker = true
                }) { Text("Weiter") }
            },
            dismissButton = {
                TextButton(onClick = { showFromDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            Column {
                Text(
                    text = "Von-Datum wählen",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                )
                DatePicker(state = fromPickerState)
            }
        }
    }

    if (showToDatePicker && pendingFromDate != null) {
        val fromMillis = pendingFromDate!!.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val initialToMillis = (customToDate ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val toPickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialToMillis,
            yearRange = 2026..2100,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= fromMillis
                override fun isSelectableYear(year: Int): Boolean = year >= 2026
            }
        )

        DatePickerDialog(
            onDismissRequest = {
                showToDatePicker = false
                pendingFromDate = null
            },
            confirmButton = {
                TextButton(onClick = {
                    toPickerState.selectedDateMillis?.let { millis ->
                        val toDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        onCustomRangeSelected(pendingFromDate!!, toDate)
                    }
                    showToDatePicker = false
                    pendingFromDate = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showToDatePicker = false
                    pendingFromDate = null
                }) { Text("Abbrechen") }
            }
        ) {
            Column {
                Text(
                    text = "Bis-Datum wählen",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                )
                DatePicker(state = toPickerState)
            }
        }
    }
}

@Composable
fun CategoryDropdown(
    selected: String?,
    categories: List<String>,
    onSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = selected ?: "Alle Kategorien"

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Kategorie: $label")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Alle Kategorien") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )

            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat) },
                    onClick = {
                        onSelected(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyStateMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
