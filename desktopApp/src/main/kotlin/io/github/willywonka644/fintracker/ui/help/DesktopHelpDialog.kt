package io.github.willywonka644.fintracker.ui.help

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class HelpEntry(val title: String, val body: String)

private val HELP_ENTRIES = listOf(
    HelpEntry("Was ist ein Konto?", "Ein Konto ist ein Ort, an dem du Geld verwaltest, z. B. Girokonto, Kreditkarte oder Sparkonto."),
    HelpEntry("Was ist eine Buchung?", "Eine Buchung ist eine einzelne Einnahme oder Ausgabe, die du manuell einträgst."),
    HelpEntry("Positive und negative Beträge", "Positive Beträge sind Einnahmen. Negative Beträge sind Ausgaben."),
    HelpEntry("Kreditkarten-Abrechnungszyklen", "Kreditkarten werden in festen Zyklen abgerechnet, z. B. vom 20. bis zum 20. des nächsten Monats."),
    HelpEntry("Ausgabenlimits", "Ein Ausgabenlimit zeigt dir, wie viel du pro Abrechnungszeitraum ausgeben möchtest."),
    HelpEntry("Daueraufträge", "Daueraufträge erstellen automatisch wiederkehrende Buchungen (wöchentlich, monatlich, jährlich)."),
    HelpEntry("Kategorien", "Jede Buchung kann einer Kategorie zugeordnet werden. Kategorien haben eine eigene Farbe und können direkt beim Erstellen einer Buchung angelegt werden."),
    HelpEntry("Auswertungen", "Die Auswertungen zeigen Saldo-Verlauf und Einnahmen/Ausgaben als Diagramme. Du kannst den Zeitraum frei wählen."),
    HelpEntry("CSV-Import", "Importiere Buchungen aus CSV-Dateien (z. B. Kontoauszüge deiner Bank). Der Import erkennt Spalten automatisch."),
    HelpEntry("Ratenzahlungen", "Teile große Ausgaben in monatliche Raten auf. Die App erstellt alle Buchungen automatisch und zeigt den Fortschritt an."),
    HelpEntry("Kontoabgleich", "Gib deinen echten Kontostand ein — die App berechnet die Differenz und korrigiert den Basiswert automatisch."),
    HelpEntry("Buchungen prüfen", "Im Prüfmodus kannst du jede Buchung einzeln abhaken. Der Fortschritt wird als \"X von Y geprüft\" angezeigt."),
    HelpEntry("Backup & Wiederherstellung", "Exportiere alle Daten als JSON-Backup. Beim Wiederherstellen werden alle lokalen Daten ersetzt."),
)

@Composable
fun DesktopHelpDialog(onDismiss: () -> Unit) {
    val listState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hilfe") },
        text = {
            Box(modifier = Modifier.widthIn(min = 480.dp).height(440.dp)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(HELP_ENTRIES) { entry ->
                        HelpEntryCard(entry)
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun HelpEntryCard(entry: HelpEntry) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Schließen" else "Öffnen",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                Text(
                    text = entry.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
