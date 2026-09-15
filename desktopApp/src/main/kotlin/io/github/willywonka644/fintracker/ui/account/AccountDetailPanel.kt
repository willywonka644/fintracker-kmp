package io.github.willywonka644.fintracker.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.analytics.computeLimitUsage
import io.github.willywonka644.fintracker.analytics.limitWarningFor
import io.github.willywonka644.fintracker.analytics.overviewBalances
import io.github.willywonka644.fintracker.ui.components.DesktopFinCard
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What this account *is*, beside the Buchungen tab which says what happened on it (#114).
 *
 * The first version of this panel showed the booking list — the very same screen the
 * Buchungen tab already is, reached a different way. That made the whole Konten entry a
 * duplicate. The account's own properties had no home anywhere: the spending limit and the
 * Dispo were settable in the form dialog and then invisible, announced only by a warning
 * once 90 % was gone.
 *
 * So this answers the question the other screen cannot: what kind of account, how it
 * stands, how much of its limit is left, and — for a card — which billing cycle the figure
 * belongs to. The recent bookings at the bottom are context, deliberately without filters:
 * filtering is what the Buchungen tab is for.
 */
@Composable
fun AccountDetailPanel(
    account: Account,
    bookings: List<Booking>,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val balances = remember(account, bookings) {
        overviewBalances(listOf(account), bookings)
    }
    val balance = balances.perAccount[account.id] ?: 0.0
    val isCard = account.type == AccountType.CREDIT_CARD

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DesktopFinCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (isCard) "Kontostand · laufende Abrechnung" else "Kontostand",
                style = MaterialTheme.typography.labelMedium,
                color = finColors.textSub,
            )
            Text(
                text = MoneyFormat.currencySigned(balance),
                style = MaterialTheme.typography.headlineMedium,
                color = if (balance >= 0) finColors.income else finColors.expense,
                fontWeight = FontWeight.Bold,
            )

            val limit = account.spendingLimit
            if (limit != null && limit > 0.0) {
                val usage = computeLimitUsage(balance, limit)
                val warning = limitWarningFor(account, balance)
                Spacer(Modifier.height(12.dp))
                Text(
                    // Named after what it is on this account type: a card has a credit
                    // limit, a current account a Dispo. Same wording as the form dialog.
                    text = if (isCard) "Kreditlimit" else "Dispo-Rahmen",
                    style = MaterialTheme.typography.labelMedium,
                    color = finColors.textSub,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { usage.percentUsed.coerceIn(0.0, 1.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    // Rot ab der Warnschwelle, nicht erst wenn der Rahmen weg ist — sonst
                    // widersprechen sich der Balken und die Zeile darunter.
                    color = if (warning != null) finColors.expense else finColors.income,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${MoneyFormat.currency(usage.used)} von ${MoneyFormat.currency(limit)} " +
                        "genutzt · ${MoneyFormat.currency(usage.remaining.coerceAtLeast(0.0))} frei",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
                if (warning != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = warning.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = finColors.expense,
                    )
                }
            }
        }

        DesktopFinCard(modifier = Modifier.fillMaxWidth()) {
            Text("Konto", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            DetailRow("Art", typeLabel(account.type), finColors.textSub)
            if (isCard) {
                DetailRow(
                    "Abrechnung",
                    "ab dem ${account.billingStartDay ?: 18}. jeden Monats",
                    finColors.textSub,
                )
            }
            val own = bookings.filter { it.accountId == account.id && !it.deleted }
            DetailRow("Buchungen", "${own.count { it.status == BookingStatus.POSTED }} gebucht", finColors.textSub)
            val planned = own.count { it.status == BookingStatus.SCHEDULED }
            if (planned > 0) DetailRow("Geplant", "$planned", finColors.textSub)
        }

        val recent = remember(account.id, bookings) {
            bookings
                // Nur Gebuchtes: geplante Buchungen liegen in der Zukunft und stehen sonst
                // ganz oben, sodass "zuletzt" auf Raten zeigt, die noch gar nicht gelaufen
                // sind. Wie viele geplant sind, sagt der Block darüber.
                .filter { it.accountId == account.id && !it.deleted && it.status == BookingStatus.POSTED }
                .sortedByDescending { it.effectiveDate ?: it.timestamp }
                .take(5)
        }
        if (recent.isNotEmpty()) {
            DesktopFinCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Zuletzt",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    // Sagt, warum hier nicht gefiltert wird: dafuer gibt es den anderen Tab.
                    "Die fünf jüngsten Buchungen. Filtern und suchen im Buchungen-Tab.",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
                Spacer(Modifier.height(8.dp))
                recent.forEachIndexed { index, booking ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    RecentRow(booking, finColors)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, subColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = subColor)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RecentRow(
    booking: Booking,
    finColors: io.github.willywonka644.fintracker.ui.theme.FinColors,
) {
    val date = Instant.fromEpochMilliseconds(booking.effectiveDate ?: booking.timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                booking.description.ifBlank { "Keine Beschreibung" },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "${date.dayOfMonth.toString().padStart(2, '0')}." +
                    "${date.monthNumber.toString().padStart(2, '0')}.${date.year}",
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
            )
        }
        Text(
            MoneyFormat.currencySigned(booking.amount),
            style = MaterialTheme.typography.bodySmall,
            color = if (booking.amount >= 0) finColors.income else finColors.expense,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun typeLabel(type: AccountType): String = when (type) {
    AccountType.GIRO -> "Girokonto"
    AccountType.CREDIT_CARD -> "Kreditkarte"
    AccountType.SPARKONTO -> "Sparkonto"
    AccountType.TAGESGELD -> "Tagesgeldkonto"
}
