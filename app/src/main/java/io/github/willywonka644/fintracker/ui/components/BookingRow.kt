package io.github.willywonka644.fintracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BookingDayCard(
    bookings: List<Booking>,
    categoryMap: Map<String, Category>,
    shortDateFormatter: DateTimeFormatter,
    onOpen: (Booking) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            bookings.forEachIndexed { index, booking ->
                BookingRow(
                    booking = booking,
                    category = categoryMap[booking.category],
                    shortDateFormatter = shortDateFormatter,
                    onOpen = { onOpen(booking) },
                )
                if (index < bookings.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookingRow(
    booking: Booking,
    category: Category?,
    shortDateFormatter: DateTimeFormatter,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val isRecurring = booking.recurringRuleId != null && booking.installmentGroupId == null
    val isInstallment = booking.installmentGroupId != null
    val isKorrektur = booking.source == BookingSource.RECONCILIATION
    // Both halves of a transfer stay visible as their own row — each belongs to its own
    // account, and folding them into one line would leave a balance unexplained. The
    // badge is what says the two are the same move.
    val isUmbuchung = booking.source == BookingSource.TRANSFER
    val isScheduled = booking.status == BookingStatus.SCHEDULED

    val installmentSuffix = if (isInstallment) {
        booking.description.split(" ").lastOrNull()
            ?.takeIf { it.matches(Regex("\\d+/\\d+")) }
    } else null

    val displayTitle = when {
        isInstallment && installmentSuffix != null ->
            booking.description.substringBeforeLast(" ").ifBlank { booking.description }
        booking.description.isNotBlank() -> booking.description
        !booking.merchantName.isNullOrBlank() -> booking.merchantName!!
        !booking.rawDescription.isNullOrBlank() -> booking.rawDescription!!
        else -> "Keine Beschreibung"
    }

    val dateStr = remember(booking.effectiveDate, booking.timestamp) {
        val epochMs = booking.effectiveDate ?: booking.timestamp
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(shortDateFormatter)
    }
    val categoryLabel = booking.category?.takeIf { it.isNotBlank() } ?: "—"

    Row(
        modifier = modifier
            .fillMaxWidth()
            // Tap opens the booking for editing; long-press does the same so the old
            // gesture keeps working.
            .combinedClickable(onClick = onOpen, onLongClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (category != null) {
            CategoryChip(category = category, size = 36.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    color = if (isScheduled) finColors.textSub else MaterialTheme.colorScheme.onSurface,
                )
                if (isScheduled) StatusPill(text = "Geplant", color = MaterialTheme.colorScheme.primary)
                if (isRecurring) FlagBadge(text = "Dauerauftrag", icon = Icons.Default.Repeat)
                if (isInstallment && installmentSuffix != null) FlagBadge(text = "Rate $installmentSuffix")
                if (isKorrektur) FlagBadge(text = "Korrektur")
                if (isUmbuchung) FlagBadge(text = "Umbuchung", icon = Icons.Default.SwapHoriz)
            }
            Text(
                text = "$categoryLabel · $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
                maxLines = 1,
            )
        }

        if (booking.isVerified) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Geprüft",
                tint = finColors.income,
                modifier = Modifier.size(16.dp),
            )
        }

        MoneyText(
            amount = booking.amount,
            style = MaterialTheme.typography.bodyMedium,
            emphasize = !isScheduled,
        )
    }
}
