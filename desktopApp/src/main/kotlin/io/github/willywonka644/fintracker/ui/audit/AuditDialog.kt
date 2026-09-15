package io.github.willywonka644.fintracker.ui.audit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun AuditDialog(
    accountName: String,
    bookings: List<Booking>,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onToggleVerified: (Booking) -> Unit,
) {
    val verifiedCount = remember(bookings) { bookings.count { it.isVerified } }
    val totalCount = bookings.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Audit — $accountName")
                if (totalCount > 0) {
                    Text(
                        text = "$verifiedCount von $totalCount geprüft",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            if (bookings.isEmpty()) {
                Text(
                    text = "Keine Buchungen vorhanden.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.widthIn(min = 420.dp).height(400.dp)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(bookings.sortedByDescending { it.timestamp }, key = { it.id }) { booking ->
                            AuditBookingRow(
                                booking = booking,
                                categories = categories,
                                onToggle = { onToggleVerified(booking) },
                            )
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun AuditBookingRow(
    booking: Booking,
    categories: List<Category>,
    onToggle: () -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(booking.timestamp).toLocalDateTime(tz).date.toString()
    val finColors = FinTheme.colors
    val amountColor = when {
        booking.amount > 0 -> finColors.income
        booking.amount < 0 -> finColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val categoryColor = booking.category?.let { catName ->
        categories.firstOrNull { it.name == catName }?.color?.let { Color(it.toInt()) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (categoryColor != null) {
                Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color = categoryColor) }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                val desc = booking.description.ifBlank { booking.merchantName ?: "Keine Beschreibung" }
                Text(desc, style = MaterialTheme.typography.bodyMedium)
                Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = MoneyFormat.currencySigned(booking.amount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = amountColor,
                modifier = Modifier.padding(end = 8.dp),
            )
            Icon(
                imageVector = if (booking.isVerified) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (booking.isVerified) "Geprüft" else "Nicht geprüft",
                tint = if (booking.isVerified) finColors.income else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp).clickable(onClick = onToggle),
            )
        }
    }
}
