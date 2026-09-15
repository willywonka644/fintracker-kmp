package io.github.willywonka644.fintracker.csvimport

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.BookingStatus
import io.github.willywonka644.fintracker.category.Category
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toKotlinLocalDate
import java.util.Locale
import java.util.UUID

private data class ImportCandidate(
    val booking: Booking,
    val isDuplicate: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    accountId: String,
    accountName: String,
    categories: List<Category>,
    existingBookings: List<Booking>,
    onNavigateBack: () -> Unit,
    onImportBookings: (List<Booking>) -> Unit
) {
    val context = LocalContext.current

    var parseResult by remember { mutableStateOf<CsvParseResult?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    val columnMapping = remember { mutableStateMapOf<Int, CsvColumnRole>() }
    var importCandidates by remember { mutableStateOf<List<ImportCandidate>?>(null) }
    var showSummaryDialog by remember { mutableStateOf<ImportSummary?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                val csvText = reader.readText()
                reader.close()

                val result = CsvParser.parse(csvText)
                parseResult = result
                columnMapping.clear()
                columnMapping.putAll(result.autoMapping)
                importCandidates = null

                // Extract file name from URI
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex("_display_name")
                        if (nameIndex >= 0) {
                            fileName = it.getString(nameIndex)
                        }
                    }
                }
                if (fileName == null) {
                    fileName = uri.lastPathSegment ?: "import.csv"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler beim Lesen: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Summary dialog
    showSummaryDialog?.let { summary ->
        ImportSummaryDialog(
            summary = summary,
            onDismiss = {
                showSummaryDialog = null
                onNavigateBack()
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("CSV importieren") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Konto: $accountName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 1: File picker
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. CSV-Datei auswählen",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (fileName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(fileName ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            filePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*"))
                        }
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (fileName == null) "Datei auswählen" else "Andere Datei wählen")
                    }
                }
            }

            if (parseResult != null) {
                val result = parseResult!!

                Spacer(modifier = Modifier.height(16.dp))

                // Info
                Text(
                    text = "${result.rows.size} Zeilen gefunden (Trennzeichen: '${delimiterLabel(result.detectedDelimiter)}')",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: Column mapping
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "2. Spalten zuordnen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ordne jede Spalte einer Bedeutung zu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        result.headers.forEachIndexed { index, header ->
                            ColumnMappingRow(
                                columnIndex = index,
                                headerName = header,
                                currentRole = columnMapping[index],
                                onRoleSelected = { role ->
                                    if (role == null) {
                                        columnMapping.remove(index)
                                    } else {
                                        // Remove any other column with the same role (except IGNORE)
                                        if (role != CsvColumnRole.IGNORE) {
                                            columnMapping.entries
                                                .filter { it.value == role && it.key != index }
                                                .forEach { columnMapping.remove(it.key) }
                                        }
                                        columnMapping[index] = role
                                    }
                                    // Reset candidates when mapping changes
                                    importCandidates = null
                                }
                            )
                            if (index < result.headers.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }

                // Validation
                val hasDate = columnMapping.values.contains(CsvColumnRole.DATE)
                val hasAmount = columnMapping.values.contains(CsvColumnRole.AMOUNT)
                val hasDescription = columnMapping.values.contains(CsvColumnRole.DESCRIPTION)
                val isValid = hasDate && hasAmount && hasDescription

                if (!isValid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildString {
                                append("Fehlende Zuordnung: ")
                                val missing = mutableListOf<String>()
                                if (!hasDate) missing.add("Datum")
                                if (!hasAmount) missing.add("Betrag")
                                if (!hasDescription) missing.add("Beschreibung")
                                append(missing.joinToString(", "))
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Analyze button — converts rows and detects duplicates
                if (isValid && importCandidates == null) {
                    Button(
                        onClick = {
                            val bookings = convertToBookings(
                                result = result,
                                mapping = columnMapping,
                                accountId = accountId,
                                categories = categories
                            )
                            if (bookings.isEmpty()) {
                                Toast.makeText(context, "Keine gültigen Zeilen gefunden.", Toast.LENGTH_SHORT).show()
                            } else {
                                val duplicateKeys = CsvParser.buildDuplicateKeySet(
                                    existingBookings.filter { it.accountId == accountId }
                                )
                                importCandidates = bookings.map { booking ->
                                    ImportCandidate(
                                        booking = booking,
                                        isDuplicate = CsvParser.duplicateKey(booking) in duplicateKeys
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Vorschau & Duplikate prüfen")
                    }
                }

                // Step 3: Full preview with duplicate detection
                importCandidates?.let { candidates ->
                    val duplicateCount = candidates.count { it.isDuplicate }
                    val newCount = candidates.size - duplicateCount

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "3. Vorschau & Duplikaterkennung",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Summary stats
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                StatChip(
                                    label = "Gesamt",
                                    count = candidates.size,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                )
                                StatChip(
                                    label = "Neu",
                                    count = newCount,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                )
                                if (duplicateCount > 0) {
                                    StatChip(
                                        label = "Duplikate",
                                        count = duplicateCount,
                                        color = MaterialTheme.colorScheme.errorContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Scrollable booking preview list (max height so it doesn't eat the whole screen)
                            val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
                            val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((candidates.size.coerceAtMost(8) * 56 + 8).dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                candidates.forEachIndexed { index, candidate ->
                                    ImportCandidateRow(
                                        candidate = candidate,
                                        dateFormatter = dateFormatter,
                                        currencyFormat = currencyFormat
                                    )
                                    if (index < candidates.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 2.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Import actions
                    if (duplicateCount > 0) {
                        // Two options when duplicates exist
                        Button(
                            onClick = {
                                val toImport = candidates.filter { !it.isDuplicate }.map { it.booking }
                                if (toImport.isNotEmpty()) {
                                    onImportBookings(toImport)
                                }
                                showSummaryDialog = ImportSummary(
                                    imported = toImport.size,
                                    skipped = duplicateCount
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Duplikate überspringen ($newCount importieren)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val allBookings = candidates.map { it.booking }
                                onImportBookings(allBookings)
                                showSummaryDialog = ImportSummary(
                                    imported = allBookings.size,
                                    skipped = 0
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Trotzdem alle importieren (${candidates.size})")
                        }
                    } else {
                        // No duplicates — simple import
                        Button(
                            onClick = {
                                val allBookings = candidates.map { it.booking }
                                onImportBookings(allBookings)
                                showSummaryDialog = ImportSummary(
                                    imported = allBookings.size,
                                    skipped = 0
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${candidates.size} Buchungen importieren")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun ImportCandidateRow(
    candidate: ImportCandidate,
    dateFormatter: DateTimeFormatter,
    currencyFormat: NumberFormat
) {
    val booking = candidate.booking
    val date = Instant.ofEpochMilli(booking.timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (candidate.isDuplicate) {
                    Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Duplicate indicator
        if (candidate.isDuplicate) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Duplikat",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        // Date
        Text(
            text = date.format(dateFormatter),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
            color = if (candidate.isDuplicate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )

        // Description
        Text(
            text = booking.description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (candidate.isDuplicate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Amount
        Text(
            text = currencyFormat.format(booking.amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            color = if (candidate.isDuplicate) {
                MaterialTheme.colorScheme.error
            } else if (booking.amount >= 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

private data class ImportSummary(
    val imported: Int,
    val skipped: Int
)

@Composable
private fun ImportSummaryDialog(
    summary: ImportSummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text("Import abgeschlossen")
        },
        text = {
            Column {
                Text("${summary.imported} Buchungen importiert.")
                if (summary.skipped > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${summary.skipped} Duplikate übersprungen.")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
private fun ColumnMappingRow(
    columnIndex: Int,
    headerName: String,
    currentRole: CsvColumnRole?,
    onRoleSelected: (CsvColumnRole?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = headerName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(
                    text = roleLabel(currentRole),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("-- Nicht zuordnen --") },
                    onClick = {
                        onRoleSelected(null)
                        expanded = false
                    }
                )
                CsvColumnRole.entries.forEach { role ->
                    DropdownMenuItem(
                        text = { Text(roleLabel(role)) },
                        onClick = {
                            onRoleSelected(role)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun convertToBookings(
    result: CsvParseResult,
    mapping: Map<Int, CsvColumnRole>,
    accountId: String,
    categories: List<Category>
): List<Booking> {
    val dateCol = mapping.entries.firstOrNull { it.value == CsvColumnRole.DATE }?.key ?: return emptyList()
    val amountCol = mapping.entries.firstOrNull { it.value == CsvColumnRole.AMOUNT }?.key ?: return emptyList()
    val descCol = mapping.entries.firstOrNull { it.value == CsvColumnRole.DESCRIPTION }?.key ?: return emptyList()
    val categoryCol = mapping.entries.firstOrNull { it.value == CsvColumnRole.CATEGORY }?.key

    val batchId = UUID.randomUUID().toString()
    val categoryMap = categories.associateBy { it.name.lowercase() }

    return result.rows.mapNotNull { row ->
        val dateStr = row.getOrNull(dateCol) ?: return@mapNotNull null
        val amountStr = row.getOrNull(amountCol) ?: return@mapNotNull null
        val description = row.getOrNull(descCol)?.trim() ?: return@mapNotNull null

        val date = CsvParser.tryParseDate(dateStr) ?: return@mapNotNull null
        val amount = CsvParser.tryParseAmount(amountStr) ?: return@mapNotNull null

        if (description.isBlank()) return@mapNotNull null

        val categoryName = categoryCol?.let { row.getOrNull(it)?.trim() }
        val matchedCategory = categoryName?.let {
            categoryMap[it.lowercase()]?.name ?: it.takeIf { it.isNotBlank() }
        }

        val timestamp = date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val status = if (date > today) BookingStatus.SCHEDULED else BookingStatus.POSTED

        Booking(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            amount = amount,
            description = description,
            timestamp = timestamp,
            category = matchedCategory,
            status = status,
            source = BookingSource.IMPORT,
            importBatchId = batchId,
            lastModifiedAt = Clock.System.now().toEpochMilliseconds()
        )
    }
}

private fun roleLabel(role: CsvColumnRole?): String = when (role) {
    CsvColumnRole.DATE -> "Datum"
    CsvColumnRole.AMOUNT -> "Betrag"
    CsvColumnRole.DESCRIPTION -> "Beschreibung"
    CsvColumnRole.CATEGORY -> "Kategorie"
    CsvColumnRole.IGNORE -> "Ignorieren"
    null -> "-- Wählen --"
}

private fun delimiterLabel(delimiter: Char): String = when (delimiter) {
    ';' -> "Semikolon"
    ',' -> "Komma"
    '\t' -> "Tab"
    '|' -> "Pipe"
    else -> delimiter.toString()
}
