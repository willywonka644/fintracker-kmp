package io.github.willywonka644.fintracker.ui.accountdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.components.CategoryChip
import io.github.willywonka644.fintracker.ui.components.FinCard
import io.github.willywonka644.fintracker.ui.components.MoneyText
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    accountName: String,
    bookings: List<Booking>,
    categories: List<Category>,
    onToggleVerified: (Booking) -> Unit,
    onNavigateBack: () -> Unit
) {
    val verifiedCount = remember(bookings) { bookings.count { it.isVerified } }
    val totalCount = bookings.size
    val categoryMap = remember(categories) { categories.associateBy { it.name } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Audit — $accountName",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (totalCount > 0) {
                            Text(
                                text = "$verifiedCount von $totalCount geprüft",
                                style = MaterialTheme.typography.labelSmall,
                                color = FinTheme.colors.textSub,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (bookings.isEmpty()) {
            EmptyStateMessage(
                text = "Keine Buchungen im aktuellen Zeitraum.",
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = bookings, key = { it.id }) { booking ->
                    AuditBookingRow(
                        booking = booking,
                        category = categoryMap[booking.category],
                        onToggle = { onToggleVerified(booking) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuditBookingRow(
    booking: Booking,
    category: Category?,
    onToggle: () -> Unit
) {
    val finColors = FinTheme.colors
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY) }
    val dateText = dateFormatter.format(Date(booking.timestamp))

    FinCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (category != null) {
                CategoryChip(category = category, size = 36.dp)
            }

            Column(modifier = Modifier.weight(1f)) {
                val descriptionText = when {
                    booking.description.isNotBlank() -> booking.description
                    !booking.merchantName.isNullOrBlank() -> booking.merchantName!!
                    else -> "Keine Beschreibung"
                }
                Text(
                    text = descriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
            }

            MoneyText(
                amount = booking.amount,
                style = MaterialTheme.typography.bodyMedium,
            )

            Icon(
                imageVector = if (booking.isVerified) Icons.Default.CheckCircle
                else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (booking.isVerified) "Geprüft" else "Nicht geprüft",
                tint = if (booking.isVerified) finColors.income
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onToggle),
            )
        }
    }
}
