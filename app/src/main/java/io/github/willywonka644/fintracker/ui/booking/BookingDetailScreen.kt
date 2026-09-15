package io.github.willywonka644.fintracker.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.attachment.AttachmentPreviewRow
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.components.CategoryChip
import io.github.willywonka644.fintracker.ui.components.FlagBadge
import io.github.willywonka644.fintracker.ui.components.MoneyText
import io.github.willywonka644.fintracker.ui.components.StatusPill
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMANY)
private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMANY)

private fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

private fun formatDateTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)

private fun sameDay(a: Long, b: Long): Boolean {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}

/**
 * Everything one booking knows, and nothing written (#129).
 *
 * A tap on a booking used to land in the edit form, so looking something up meant
 * opening a screen full of input fields — every look was a write that could go wrong.
 * This screen is what the tap opens now; editing and deleting are two deliberate steps
 * from here.
 *
 * **It shows only what the data holds.** There is no "created at" in the model — what
 * exists is the booking date, the value date and [Booking.lastModifiedAt], which came
 * with the sync. Inventing a fourth would mean a migration that leaves the existing
 * stock empty anyway, so the screen names the three it has.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    booking: Booking,
    accountName: String,
    category: Category?,
    /** The other side's account, for a transfer — it explains the second half. */
    counterpartAccountName: String?,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenAttachment: (String) -> Unit,
) {
    val finColors = FinTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }

    val isTransfer = booking.source == BookingSource.TRANSFER
    val isRecurring = booking.recurringRuleId != null && booking.installmentGroupId == null
    val isInstallment = booking.installmentGroupId != null
    val isKorrektur = booking.source == BookingSource.RECONCILIATION
    val isScheduled = booking.status == BookingStatus.SCHEDULED

    val installmentSuffix = if (isInstallment) {
        booking.description.split(" ").lastOrNull()?.takeIf { it.matches(Regex("\\d+/\\d+")) }
    } else null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Buchung") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Kopf: Betrag, Beschreibung, Abzeichen ─────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                if (category != null) {
                    CategoryChip(category = category, size = 56.dp)
                }
                MoneyText(
                    amount = booking.amount,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = booking.description.takeIf { it.isNotBlank() }
                        ?: booking.merchantName?.takeIf { it.isNotBlank() }
                        ?: "Keine Beschreibung",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isScheduled) StatusPill("Geplant", MaterialTheme.colorScheme.primary)
                    if (isTransfer) FlagBadge("Umbuchung", Icons.Default.SwapHoriz)
                    if (isRecurring) FlagBadge("Dauerauftrag", Icons.Default.Repeat)
                    if (isInstallment && installmentSuffix != null) FlagBadge("Rate $installmentSuffix")
                    if (isKorrektur) FlagBadge("Korrektur")
                    if (booking.isVerified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Geprüft",
                            tint = finColors.income,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // ── Felder ────────────────────────────────────────────────────────
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Als Liste gebaut, nicht als Folge von Zeilen: die meisten Felder
                    // sind bedingt, und nur so laesst sich der Trennstrich zwischen zwei
                    // Zeilen ziehen statt unter die letzte.
                    val fields = buildList {
                        add("Konto" to accountName)
                        if (counterpartAccountName != null) {
                            // Die zweite Haelfte bleibt eine eigene Buchung auf ihrem Konto
                            // (#118) — hier steht, wo sie liegt, damit die Bewegung ganz ist.
                            add("Gegenkonto" to counterpartAccountName)
                        }
                        add("Kategorie" to (booking.category?.takeIf { it.isNotBlank() } ?: "Ohne Kategorie"))
                        add("Status" to if (isScheduled) "Geplant" else "Gebucht")
                        add("Buchungsdatum" to formatDate(booking.timestamp))
                        val effective = booking.effectiveDate
                        if (effective != null && !sameDay(effective, booking.timestamp)) {
                            add("Wertstellung" to formatDate(effective))
                        }
                        booking.merchantName?.takeIf { it.isNotBlank() }?.let { add("Händler" to it) }
                        booking.paymentReference?.takeIf { it.isNotBlank() }?.let { add("Verwendungszweck" to it) }
                        booking.rawDescription
                            ?.takeIf { it.isNotBlank() && it != booking.description }
                            ?.let { add("Ursprungstext" to it) }
                        if (booking.lastModifiedAt > 0L) {
                            // Kein "erfasst am": das gibt es im Modell nicht. Dieser Zeitstempel
                            // stammt aus dem Sync und sagt, wann zuletzt etwas geaendert wurde.
                            add("Zuletzt geändert" to formatDateTime(booking.lastModifiedAt))
                        }
                    }
                    fields.forEachIndexed { index, (label, value) ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                        DetailRow(label, value)
                    }
                }
            }

            // ── Beleg ─────────────────────────────────────────────────────────
            val attachment = booking.attachmentPath
            if (!attachment.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Beleg",
                        style = MaterialTheme.typography.labelMedium,
                        color = finColors.textSub,
                    )
                    AttachmentPreviewRow(
                        path = attachment,
                        onClick = { onOpenAttachment(attachment) },
                    )
                }
            }

            // ── Aktionen ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Bearbeiten")
                }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = finColors.expense),
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Löschen")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Buchung löschen?") },
            text = {
                Text(
                    if (isTransfer) {
                        // Eine Haelfte allein zu loeschen liesse auf einem Konto Geld
                        // stehen, das nirgendwo herkommt (#118).
                        "Das ist eine Umbuchung. Beide Seiten werden gelöscht — auch die auf " +
                            (counterpartAccountName ?: "dem anderen Konto") + "."
                    } else {
                        "Die Buchung wird gelöscht. Das lässt sich nicht rückgängig machen."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Löschen", color = finColors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val finColors = FinTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = finColors.textSub,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.6f),
        )
    }
}
