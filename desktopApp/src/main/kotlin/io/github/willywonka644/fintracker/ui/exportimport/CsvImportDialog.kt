package io.github.willywonka644.fintracker.ui.exportimport

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.willywonka644.fintracker.csvimport.CsvColumnRole
import io.github.willywonka644.fintracker.csvimport.CsvParseResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportDialog(
    parseResult: CsvParseResult,
    onDismiss: () -> Unit,
    onImport: (columnMapping: Map<Int, CsvColumnRole>) -> Unit,
) {
    var mapping by remember {
        mutableStateOf(parseResult.autoMapping.toMutableMap())
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(700.dp).fillMaxHeight(0.85f)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CSV Import — Map Columns", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Detected delimiter: '${parseResult.detectedDelimiter}' · ${parseResult.rows.size} data rows",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Column mapping table
                Text("Column mapping:", style = MaterialTheme.typography.titleSmall)
                val listState = rememberLazyListState()
                Box(modifier = Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                    items(parseResult.headers.indices.toList()) { colIdx ->
                        val header = parseResult.headers[colIdx]
                        val preview = parseResult.rows.take(3).mapNotNull { it.getOrNull(colIdx) }
                        var expanded by remember { mutableStateOf(false) }
                        val currentRole = mapping[colIdx] ?: CsvColumnRole.IGNORE

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(header, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    preview.joinToString(" / "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                modifier = Modifier.width(160.dp),
                            ) {
                                OutlinedTextField(
                                    value = currentRole.name,
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    textStyle = MaterialTheme.typography.bodySmall,
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    CsvColumnRole.entries.forEach { role ->
                                        DropdownMenuItem(
                                            text = { Text(role.name) },
                                            onClick = {
                                                mapping = mapping.toMutableMap().also { it[colIdx] = role }
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
                }

                // Preview of first rows
                if (parseResult.rows.isNotEmpty()) {
                    Text("Preview (first 3 rows):", style = MaterialTheme.typography.titleSmall)
                    parseResult.rows.take(3).forEach { row ->
                        Text(
                            row.joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = { onImport(mapping) },
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text("Import") }
                }
            }
        }
    }
}
