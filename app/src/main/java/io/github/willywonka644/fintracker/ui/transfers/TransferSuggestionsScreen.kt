package io.github.willywonka644.fintracker.ui.transfers

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.booking.TransferCandidate
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

/**
 * One-off cleanup: the bookings that predate [io.github.willywonka644.fintracker.BookingSource.TRANSFER]
 * and look like the two halves of one transfer (#122).
 *
 * Everything here is a suggestion. The matching rule is good — measured, no booking has more
 * than one possible partner — but it is still a guess about what someone meant, so each pair
 * is decided on its own and nothing is ever confirmed in bulk. A rejection is remembered, or
 * the same pair would be put up again on every visit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferSuggestionsScreen(
    candidates: List<TransferCandidate>,
    accounts: List<Account>,
    onConfirm: (TransferCandidate) -> Unit,
    onReject: (TransferCandidate) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val accountName = { id: String -> accounts.firstOrNull { it.id == id }?.name ?: "Unbekanntes Konto" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Umbuchungen erkennen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        if (candidates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Keine offenen Vorschläge. Was hier stand, ist entweder bestätigt " +
                        "oder abgelehnt — abgelehnte Paare kommen nicht wieder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = finColors.textSub,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = "${candidates.size} mögliche ${if (candidates.size == 1) "Umbuchung" else "Umbuchungen"}. " +
                        "Bestätigte Paare zählen nicht mehr als Einnahme und Ausgabe — die Kontostände " +
                        "bleiben, wie sie sind.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = finColors.textSub,
                )
            }

            items(candidates, key = { it.key }) { candidate ->
                CandidateCard(
                    candidate = candidate,
                    accountName = accountName,
                    onConfirm = { onConfirm(candidate) },
                    onReject = { onReject(candidate) },
                )
            }
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: TransferCandidate,
    accountName: (String) -> String,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
) {
    val finColors = FinTheme.colors
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = MoneyFormat.currency(candidate.amount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when (candidate.daysApart) {
                    0 -> "am selben Tag"
                    1 -> "einen Tag auseinander"
                    else -> "${candidate.daysApart} Tage auseinander"
                },
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
            )

            Spacer(Modifier.height(12.dp))

            SideRow(
                booking = candidate.outgoing,
                accountName = accountName(candidate.outgoing.accountId),
                isOutgoing = true,
            )
            Spacer(Modifier.height(6.dp))
            SideRow(
                booking = candidate.incoming,
                accountName = accountName(candidate.incoming.accountId),
                isOutgoing = false,
            )

            if (candidate.wasCorrection) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Eine Seite ist bisher als Kontoabgleich-Korrektur gebucht. " +
                            "Bestätigen macht daraus eine Umbuchung.",
                        style = MaterialTheme.typography.labelSmall,
                        color = finColors.textSub,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Text("Keine Umbuchung")
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text("Umbuchung")
                }
            }
        }
    }
}

/** One side of the pair: where it sits, when, and what it says. */
@Composable
private fun SideRow(
    booking: Booking,
    accountName: String,
    isOutgoing: Boolean,
) {
    val finColors = FinTheme.colors
    val date = Instant.ofEpochMilli(booking.effectiveDate ?: booking.timestamp)
        .atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isOutgoing) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = if (isOutgoing) "Abgang" else "Zugang",
            tint = if (isOutgoing) finColors.expense else finColors.income,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = accountName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = listOfNotNull(
                    date,
                    booking.description.takeIf { it.isNotBlank() },
                    booking.category?.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
            )
        }
    }
}
