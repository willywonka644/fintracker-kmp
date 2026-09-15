package io.github.willywonka644.fintracker.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.AppServices
import io.github.willywonka644.fintracker.Services
import io.github.willywonka644.fintracker.sync.PushSummary
import io.github.willywonka644.fintracker.sync.SyncClient
import io.github.willywonka644.fintracker.sync.SyncExchange
import io.github.willywonka644.fintracker.sync.SyncPreview
import io.github.willywonka644.fintracker.sync.SyncResult
import io.github.willywonka644.fintracker.sync.describe
import io.github.willywonka644.fintracker.sync.runSyncExchange
import io.github.willywonka644.fintracker.sync.syncHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One exchange with the sync server, from the desktop (#92).
 *
 * The counterpart of the Android sync screen and, apart from the widgets, the
 * same thing: [runSyncExchange] decides the order of the legs, this file renders
 * the states and asks before writing.
 *
 * The confirmation is the reason this is a dialog with two buttons rather than a
 * single action. It comes from Phase 5 — nothing writes to a device unannounced
 * — and the server path makes it more necessary rather than less, because a Pi
 * that is always up removes the accident of timing that the Wi-Fi path relied on.
 */
private sealed interface DesktopSyncState {
    data object Idle : DesktopSyncState
    data class Running(val step: String) : DesktopSyncState
    data class Confirm(val preview: SyncPreview, val pushed: PushSummary) : DesktopSyncState
    data class Done(val pushed: PushSummary, val newCount: Int, val updatedCount: Int) : DesktopSyncState
    data class Failed(val message: String) : DesktopSyncState
}

@Composable
fun ServerSyncDialog(
    onDismiss: () -> Unit,
    onSyncConfirmed: () -> Unit,
    onOpenSettings: () -> Unit,
    services: Services = AppServices,
    // Seam for tests (#92). The dialog owns the client's lifetime — it closes it
    // in the DisposableEffect below — so it takes a factory rather than a ready
    // client. A test hands in one backed by MockEngine; nothing else changes.
    httpClientFactory: () -> HttpClient = { syncHttpClient() },
) {
    val scope = rememberCoroutineScope()
    val config = remember { services.serverSyncStore.current() }

    val http = remember { httpClientFactory() }
    DisposableEffect(Unit) { onDispose { http.close() } }
    val client = remember(http) { SyncClient(http) }

    var state by remember { mutableStateOf<DesktopSyncState>(DesktopSyncState.Idle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Server-Sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!config.isComplete) {
                    Text("Noch kein Server eingerichtet.", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Adresse und Schlüssel fehlen. Ohne beides kann sich dieser Rechner nicht abgleichen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(config.url, style = MaterialTheme.typography.bodySmall)

                    when (val s = state) {
                        is DesktopSyncState.Idle -> Text(
                            "Sendet die Daten dieses Rechners und holt danach den Stand des Servers.",
                            style = MaterialTheme.typography.bodySmall,
                        )

                        is DesktopSyncState.Running -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(s.step, style = MaterialTheme.typography.bodySmall)
                        }

                        is DesktopSyncState.Confirm -> ConfirmBlock(s)

                        is DesktopSyncState.Done -> Text(
                            "Abgleich abgeschlossen.\n" +
                                "Gesendet: ${s.pushed.newCount} neu, ${s.pushed.updatedCount} aktualisiert.\n" +
                                "Empfangen: ${s.newCount} neu, ${s.updatedCount} aktualisiert.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        is DesktopSyncState.Failed -> Text(
                            s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                !config.isComplete -> Button(onClick = onOpenSettings) { Text("Einrichten") }

                state is DesktopSyncState.Confirm -> Button(onClick = {
                    val confirming = state as DesktopSyncState.Confirm
                    scope.launch {
                        state = DesktopSyncState.Running("Änderungen werden gespeichert …")
                        state = withContext(Dispatchers.IO) {
                            when (val written = services.syncWriter.write(confirming.preview)) {
                                is SyncResult.Success -> DesktopSyncState.Done(
                                    pushed = confirming.pushed,
                                    newCount = written.newCount,
                                    updatedCount = written.updatedCount,
                                )

                                is SyncResult.Error -> DesktopSyncState.Failed(describe(written.error))
                            }
                        }
                        if (state is DesktopSyncState.Done) onSyncConfirmed()
                    }
                }) { Text("Bestätigen") }

                else -> Button(
                    onClick = {
                        scope.launch {
                            state = DesktopSyncState.Running("Abgleich wird vorbereitet …")
                            val result = withContext(Dispatchers.IO) {
                                runSyncExchange(
                                    client = client,
                                    config = config,
                                    exportPayload = services.syncExporter::export,
                                    importPayload = services.syncImporter::import,
                                    onStep = { state = DesktopSyncState.Running(it) },
                                )
                            }
                            state = when (result) {
                                is SyncExchange.Failed -> DesktopSyncState.Failed(result.message)
                                is SyncExchange.NeedsConfirmation ->
                                    DesktopSyncState.Confirm(result.preview, result.pushed)
                            }
                        }
                    },
                    enabled = state !is DesktopSyncState.Running,
                ) { Text("Jetzt abgleichen") }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                // From the confirmation this is a real refusal, not just closing a
                // window: nothing has been written, and saying so is the point.
                if (state is DesktopSyncState.Confirm) state = DesktopSyncState.Idle else onDismiss()
            }) {
                Text(if (state is DesktopSyncState.Confirm) "Abbrechen" else "Schließen")
            }
        },
    )
}

@Composable
private fun ConfirmBlock(state: DesktopSyncState.Confirm) {
    val preview = state.preview

    Text("Vorschau (lokal)", style = MaterialTheme.typography.titleSmall)
    Text(
        "Der Server hat ${state.pushed.newCount} neue und ${state.pushed.updatedCount} geänderte " +
            "Einträge von diesem Rechner übernommen. Zurück kämen:",
        style = MaterialTheme.typography.bodySmall,
    )

    PreviewRow("Neu", preview.newCount)
    PreviewRow("Aktualisiert", preview.updatedCount)
    PreviewRow("Unverändert", preview.unchangedCount)
    if (preview.skippedCount > 0) PreviewRow("Übersprungen", preview.skippedCount)

    if (preview.warnings.isNotEmpty()) {
        HorizontalDivider()
        preview.warnings.take(5).forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
        if (preview.warnings.size > 5) {
            Text(
                "… und ${preview.warnings.size - 5} weitere Warnungen",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PreviewRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text("$count", style = MaterialTheme.typography.bodySmall)
    }
}
