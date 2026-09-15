package io.github.willywonka644.fintracker.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.ui.components.DesktopMoneyText
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)
private val dayTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)

private fun day(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().format(dayFormatter)

private fun dayTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dayTimeFormatter)

private fun sameDay(a: Long, b: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}

/**
 * Everything one booking knows, without a single input field (#129).
 *
 * A click used to land straight in the edit form, so looking something up was a write
 * waiting to go wrong. Editing and deleting are two deliberate steps from here — delete
 * hands over to the confirmation the app already has, rather than asking twice.
 *
 * **The receipt is missing on purpose.** `SyncExporter` strips `attachmentPath`, so the
 * image lives on the phone and never travels; this window cannot even tell whether one
 * exists. Decided in #129: say nothing rather than claim something.
 */
@Composable
fun BookingDetailDialog(
    booking: Booking,
    accountName: String,
    /** The other side's account, for a transfer — it explains the second half. */
    counterpartAccountName: String?,
    categoryName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val finColors = FinTheme.colors
    val isTransfer = booking.source == BookingSource.TRANSFER
    val isRecurring = booking.recurringRuleId != null && booking.installmentGroupId == null
    val isInstallment = booking.installmentGroupId != null
    val isKorrektur = booking.source == BookingSource.RECONCILIATION
    val isScheduled = booking.status == BookingStatus.SCHEDULED

    val fields = buildList {
        add("Konto" to accountName)
        if (counterpartAccountName != null) add("Gegenkonto" to counterpartAccountName)
        add("Kategorie" to categoryName.ifBlank { "Ohne Kategorie" })
        add("Status" to if (isScheduled) "Geplant" else "Gebucht")
        add("Buchungsdatum" to day(booking.timestamp))
        val effective = booking.effectiveDate
        if (effective != null && !sameDay(effective, booking.timestamp)) {
            add("Wertstellung" to day(effective))
        }
        booking.merchantName?.takeIf { it.isNotBlank() }?.let { add("Händler" to it) }
        booking.paymentReference?.takeIf { it.isNotBlank() }?.let { add("Verwendungszweck" to it) }
        booking.rawDescription
            ?.takeIf { it.isNotBlank() && it != booking.description }
            ?.let { add("Ursprungstext" to it) }
        if (booking.lastModifiedAt > 0L) {
            // Kein "erfasst am" im Modell — dieser Zeitstempel stammt aus dem Sync und
            // sagt, wann zuletzt etwas geaendert wurde.
            add("Zuletzt geändert" to dayTime(booking.lastModifiedAt))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buchung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.width(420.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Kopf ──────────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DesktopMoneyText(
                        amount = booking.amount,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = booking.description.takeIf { it.isNotBlank() }
                            ?: booking.merchantName?.takeIf { it.isNotBlank() }
                            ?: "Keine Beschreibung",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isScheduled) Badge("Geplant", MaterialTheme.colorScheme.primary)
                        if (isTransfer) Badge("Umbuchung", MaterialTheme.colorScheme.primary)
                        if (isRecurring) Badge("Dauerauftrag", MaterialTheme.colorScheme.primary)
                        if (isInstallment) Badge("Rate", MaterialTheme.colorScheme.primary)
                        if (isKorrektur) Badge("Korrektur", finColors.textSub)
                        if (booking.isVerified) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Geprüft",
                                tint = finColors.income,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                // ── Felder ────────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                ) {
                    fields.forEachIndexed { index, (label, value) ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                color = finColors.textSub,
                                modifier = Modifier.width(130.dp),
                            )
                            if (label == "Kategorie") {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape)
                                        .background(finColors.category(value))
                                )
                                Spacer(Modifier.width(2.dp))
                            }
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onEdit) { Text("Bearbeiten") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Löschen", color = finColors.expense)
                }
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        },
    )
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
