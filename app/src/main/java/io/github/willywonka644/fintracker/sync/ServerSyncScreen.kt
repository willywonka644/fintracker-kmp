package io.github.willywonka644.fintracker.sync

import android.content.Context
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Push and pull against the sync server, in one press (#91).
 *
 * The order of the legs and the reason the exchange stops before writing live in
 * [runSyncExchange], which the desktop uses too (#92). This file only renders the
 * states that function produces — and [SyncState.Confirm] is the one that
 * matters: reaching it means the server has answered and nothing has been
 * written locally yet.
 */
private sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val step: String) : SyncState

    /** Waiting for the user. Nothing has been written locally at this point. */
    data class Confirm(val preview: SyncPreview, val pushed: PushSummary) : SyncState

    data class Done(val pushed: PushSummary, val newCount: Int, val updatedCount: Int) : SyncState
    data class Failed(val message: String) : SyncState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSyncScreen(
    context: Context,
    syncExporter: SyncExporter,
    syncImporter: SyncImporter,
    syncWriter: SyncWriter,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onSyncComplete: () -> Unit,
) {
    val store = remember { ServerSyncStore(context) }
    val scope = rememberCoroutineScope()

    val http = remember { syncHttpClient() }
    DisposableEffect(Unit) { onDispose { http.close() } }
    val client = remember(http) { SyncClient(http) }

    var state by remember { mutableStateOf<SyncState>(SyncState.Idle) }

    // Observed, not read once. The settings screen is drawn on top of this one
    // rather than replacing it, so this composition survives an edit and a
    // one-shot read would keep showing — and syncing against — the old address.
    val config by store.config.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server-Sync") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Einstellungen")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val current = config

            when {
                current == null -> Text("Einstellungen werden geladen …")

                !current.isComplete -> {
                    Text(
                        "Noch kein Server eingerichtet.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Adresse und Schlüssel fehlen. Ohne beides kann dieses Gerät sich nicht abgleichen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenSettings) { Text("Einrichten") }
                }

                else -> {
                    Text(
                        current.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    when (val s = state) {
                        is SyncState.Idle, is SyncState.Done, is SyncState.Failed -> {
                            Button(
                                onClick = {
                                    scope.launch {
                                        state = SyncState.Running("Abgleich wird vorbereitet …")
                                        state = exchange(
                                            client = client,
                                            config = current,
                                            exporter = syncExporter,
                                            importer = syncImporter,
                                            onStep = { state = SyncState.Running(it) },
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Jetzt abgleichen") }

                            when (s) {
                                is SyncState.Done -> DoneBlock(s)
                                is SyncState.Failed -> {
                                    Text(
                                        s.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    VpnReminder(s.message)
                                }
                                else -> Unit
                            }
                        }

                        is SyncState.Running -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(s.step)
                        }

                        is SyncState.Confirm -> ConfirmBlock(
                            state = s,
                            onConfirm = {
                                scope.launch {
                                    state = SyncState.Running("Änderungen werden gespeichert …")
                                    state = withContext(Dispatchers.IO) {
                                        when (val written = syncWriter.write(s.preview)) {
                                            is SyncResult.Success -> SyncState.Done(
                                                pushed = s.pushed,
                                                newCount = written.newCount,
                                                updatedCount = written.updatedCount,
                                            )

                                            is SyncResult.Error -> SyncState.Failed(describe(written.error))
                                        }
                                    }
                                    if (state is SyncState.Done) onSyncComplete()
                                }
                            },
                            onCancel = { state = SyncState.Idle },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConfirmBlock(
    state: SyncState.Confirm,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val preview = state.preview

    Text("Vorschau (lokal)", style = MaterialTheme.typography.titleMedium)
    Text(
        "Der Server hat ${state.pushed.newCount} neue und ${state.pushed.updatedCount} geänderte " +
            "Einträge von diesem Gerät übernommen. Zurück kämen:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    PreviewRow("Neu", preview.newCount)
    PreviewRow("Aktualisiert", preview.updatedCount)
    PreviewRow("Unverändert", preview.unchangedCount)
    if (preview.skippedCount > 0) PreviewRow("Übersprungen", preview.skippedCount)

    if (preview.warnings.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        preview.warnings.take(5).forEach { warning ->
            Text("⚠ $warning", style = MaterialTheme.typography.bodySmall)
        }
        if (preview.warnings.size > 5) {
            Text(
                "… und ${preview.warnings.size - 5} weitere Warnungen",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    Spacer(Modifier.height(4.dp))
    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("Bestätigen") }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
}

@Composable
private fun DoneBlock(state: SyncState.Done) {
    Text(
        "Abgleich abgeschlossen.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        "Gesendet: ${state.pushed.newCount} neu, ${state.pushed.updatedCount} aktualisiert.\n" +
            "Empfangen: ${state.newCount} neu, ${state.updatedCount} aktualisiert.",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun PreviewRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$count", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Renders what [runSyncExchange] decided; the order of the legs lives there. */
private suspend fun exchange(
    client: SyncClient,
    config: ServerSyncConfig,
    exporter: SyncExporter,
    importer: SyncImporter,
    onStep: (String) -> Unit,
): SyncState = when (
    val result = withContext(Dispatchers.IO) {
        runSyncExchange(client, config, exporter::export, importer::import, onStep)
    }
) {
    is SyncExchange.Failed -> SyncState.Failed(result.message)
    is SyncExchange.NeedsConfirmation -> SyncState.Confirm(result.preview, result.pushed)
}
