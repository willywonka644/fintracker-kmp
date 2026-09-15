package io.github.willywonka644.fintracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.willywonka644.fintracker.util.parseGermanDate
import io.github.willywonka644.fintracker.util.toGermanDateString
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * OutlinedTextField augmented with a calendar icon that opens a date picker dialog.
 * Text input (TT.MM.JJJJ) remains available as a fallback.
 */
@Composable
fun DesktopDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    /**
     * Was im leeren Feld steht — hier das Format.
     *
     * Material laesst das Label bei gefuelltem Feld nach oben schweben und verkleinert es.
     * Ein "Von (TT.MM.JJJJ)" dort sieht danach gequetscht aus und sagt ohnehin nichts mehr,
     * sobald ein Datum drinsteht. Als Platzhalter erscheint das Format, solange es hilft,
     * und verschwindet, sobald es nicht mehr gebraucht wird.
     */
    placeholder: String? = null,
    isError: Boolean = false,
    selectedDate: LocalDate? = null,
    textStyle: TextStyle = TextStyle.Default,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
) {
    var showPicker by remember { mutableStateOf(false) }
    val tz = TimeZone.currentSystemDefault()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        textStyle = textStyle,
        supportingText = supportingText,
        trailingIcon = {
            IconButton(
                onClick = { showPicker = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "Kalender öffnen",
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        modifier = modifier,
    )

    if (showPicker) {
        DesktopCalendarDialog(
            initialDate = selectedDate
                ?: parseGermanDate(value)
                ?: Clock.System.todayIn(tz),
            onDateSelected = { date ->
                onValueChange(date.toGermanDateString())
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Full-month calendar dialog for desktop date selection. */
@Composable
fun DesktopCalendarDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    var displayYear  by remember { mutableStateOf(initialDate.year) }
    var displayMonth by remember { mutableStateOf(initialDate.month) }
    var selectedDate by remember { mutableStateOf(initialDate) }

    fun firstOfDisplay() = LocalDate(displayYear, displayMonth, 1)

    // Month navigation via DatePeriod (avoids import conflicts with DateTimeUnit)
    fun prevMonth() {
        val prev = firstOfDisplay().plus(DatePeriod(months = -1))
        displayYear  = prev.year
        displayMonth = prev.month
    }

    fun nextMonth() {
        val next = firstOfDisplay().plus(DatePeriod(months = 1))
        displayYear  = next.year
        displayMonth = next.month
    }

    // Days in the displayed month: advance to next month, back one day
    fun daysInDisplay(): Int {
        val firstOfNext = firstOfDisplay().plus(DatePeriod(months = 1))
        return firstOfNext.plus(DatePeriod(days = -1)).dayOfMonth
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(288.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {

                // ── Month / year navigation ──────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { prevMonth() }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Vorheriger Monat")
                    }
                    Text(
                        text = "${monthName(displayMonth)} $displayYear",
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(onClick = { nextMonth() }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Nächster Monat")
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Weekday header (Mon–Sun) ─────────────────────────────────
                Row(Modifier.fillMaxWidth()) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { d ->
                        Text(
                            text = d,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Day grid ─────────────────────────────────────────────────
                val totalDays = daysInDisplay()
                // dayOfWeek.value: 1=Mon … 7=Sun (java.time.DayOfWeek.getValue())
                val firstWeekdayOffset = firstOfDisplay().dayOfWeek.value - 1  // 0=Mon
                val rows = (firstWeekdayOffset + totalDays + 6) / 7

                repeat(rows) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { col ->
                            val dayNum = row * 7 + col - firstWeekdayOffset + 1
                            if (dayNum < 1 || dayNum > totalDays) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val date       = LocalDate(displayYear, displayMonth, dayNum)
                                val isSelected = date == selectedDate
                                val isToday    = date == today

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedDate = date
                                            onDateSelected(date)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday    -> MaterialTheme.colorScheme.primary
                                            else       -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (isToday && !isSelected) FontWeight.Bold
                                                     else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // ── Dismiss ──────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                }
            }
        }
    }
}

private fun monthName(month: Month): String = when (month) {
    Month.JANUARY   -> "Januar"
    Month.FEBRUARY  -> "Februar"
    Month.MARCH     -> "März"
    Month.APRIL     -> "April"
    Month.MAY       -> "Mai"
    Month.JUNE      -> "Juni"
    Month.JULY      -> "Juli"
    Month.AUGUST    -> "August"
    Month.SEPTEMBER -> "September"
    Month.OCTOBER   -> "Oktober"
    Month.NOVEMBER  -> "November"
    Month.DECEMBER  -> "Dezember"
    else            -> month.name
}
