package io.github.willywonka644.fintracker.sync

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.ISettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// ── State machine ─────────────────────────────────────────────────────────────

private enum class SyncFlow { PUSH, PULL }

private sealed class WifiSyncState {
    object Idle : WifiSyncState()
    data class Found(val host: String, val port: Int) : WifiSyncState()
    data class DesktopSyncRequest(val host: String, val port: Int, val token: String) : WifiSyncState()
    object Syncing : WifiSyncState()
    data class Preview(
        val host: String,
        val port: Int,
        val flow: SyncFlow,
        val newCount: Int,
        val updatedCount: Int,
        val unchangedCount: Int,
        val skippedCount: Int,
        val warnings: List<String>,
        val localPreview: SyncPreview? = null,
        val followWithPull: Boolean = false,
    ) : WifiSyncState()
    data class Success(val newCount: Int, val updatedCount: Int) : WifiSyncState()
    data class Error(val message: String, val foundState: Found?) : WifiSyncState()
}

// Mirrors SyncPreviewResponse from the desktop server
@Serializable
private data class ServerPreviewResponse(
    val newCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val skippedCount: Int,
    val warnings: List<String>,
)

private val json = Json { ignoreUnknownKeys = true }

private class WrongTokenException : Exception("WRONG_TOKEN")

// ── HTTP helpers ──────────────────────────────────────────────────────────────

private fun httpGet(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 5_000
    conn.readTimeout = 15_000
    val code = conn.responseCode
    if (code !in 200..299) throw IOException("HTTP $code")
    return conn.inputStream.bufferedReader().readText()
}

private fun httpPost(url: String, body: String = ""): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.connectTimeout = 5_000
    conn.readTimeout = 15_000
    if (body.isNotEmpty()) {
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "text/plain; charset=UTF-8")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }
    val code = conn.responseCode
    if (code == 403) throw WrongTokenException()
    if (code !in 200..299) {
        val errBody = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
        throw IOException("HTTP $code${if (errBody != null) ": $errBody" else ""}")
    }
    return conn.inputStream.bufferedReader().readText()
}

private fun tokenKey(host: String) = "wifi_token_$host"

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiFiSyncScreen(
    context: Context,
    syncExporter: SyncExporter,
    syncImporter: SyncImporter,
    syncWriter: SyncWriter,
    settingsRepository: ISettingsRepository,
    onDismiss: () -> Unit,
    onSyncComplete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<WifiSyncState>(WifiSyncState.Idle) }
    val discovery = remember { WiFiSyncDiscovery(context) }

    DisposableEffect(Unit) {
        discovery.discoverDesktop(
            onFound = { host, port ->
                if (state is WifiSyncState.Idle) {
                    state = WifiSyncState.Found(host, port)
                }
            },
            onLost = {
                if (state is WifiSyncState.Found || state is WifiSyncState.Idle) {
                    state = WifiSyncState.Idle
                }
            },
        )
        onDispose { discovery.stopDiscovery() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WLAN-Sync") },
                navigationIcon = {
                    IconButton(onClick = {
                        discovery.stopDiscovery()
                        onDismiss()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val s = state) {
                // ── IDLE ──────────────────────────────────────────────────
                WifiSyncState.Idle -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 48.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Suche nach FinTracker Desktop im Netzwerk…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = {
                            discovery.stopDiscovery()
                            onDismiss()
                        }) {
                            Text("Abbrechen")
                        }
                    }
                }

                // ── FOUND ─────────────────────────────────────────────────
                is WifiSyncState.Found -> {
                    var storedToken by remember(s.host) {
                        mutableStateOf(settingsRepository.get(tokenKey(s.host)))
                    }

                    if (storedToken == null) {
                        // Token entry — first connection to this host
                        var tokenInput by remember { mutableStateOf("") }
                        var verifying by remember { mutableStateOf(false) }
                        var verifyError by remember { mutableStateOf<String?>(null) }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Gefunden: ${s.host}:${s.port}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Gib den 8-stelligen Kopplungscode ein, der in der Desktop-App angezeigt wird.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedTextField(
                                value = tokenInput,
                                onValueChange = {
                                    tokenInput = it.uppercase().take(8)
                                    verifyError = null
                                },
                                label = { Text("Kopplungscode") },
                                isError = verifyError != null,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (verifyError != null) {
                                Text(
                                    text = verifyError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            Button(
                                onClick = {
                                    // Verify the code against the desktop BEFORE storing it:
                                    // /sync/ping answers encrypted with the desktop's token —
                                    // only the correct code can decrypt it.
                                    verifying = true
                                    verifyError = null
                                    scope.launch {
                                        val response = try {
                                            withContext(Dispatchers.IO) {
                                                httpGet("http://${s.host}:${s.port}/sync/ping")
                                            }
                                        } catch (e: Exception) {
                                            verifying = false
                                            verifyError = "Desktop nicht erreichbar (${e.message ?: "Netzwerkfehler"})"
                                            return@launch
                                        }
                                        val ok = try {
                                            withContext(Dispatchers.Default) {
                                                decryptSyncPayload(response, tokenInput) == "PONG"
                                            }
                                        } catch (e: Exception) {
                                            false
                                        }
                                        verifying = false
                                        if (ok) {
                                            settingsRepository.set(tokenKey(s.host), tokenInput)
                                            // Flip the branch directly — the old
                                            // "state = Idle; state = s" re-entry trick collapses
                                            // within one frame and never triggered a switch.
                                            storedToken = tokenInput
                                        } else {
                                            verifyError = "Kopplungscode falsch — bitte mit der Desktop-App vergleichen."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = tokenInput.length == 8 && !verifying,
                            ) {
                                Text(if (verifying) "Prüfe Verbindung…" else "Verbinden")
                            }
                            TextButton(
                                onClick = {
                                    discovery.stopDiscovery()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Abbrechen")
                            }
                        }
                    } else {
                        // Token known — show push/pull choice
                        val capturedToken = storedToken!!

                        // Poll for a Desktop-initiated "Sync Now" request.
                        // Cancelled automatically when leaving this state (composable disposed).
                        LaunchedEffect(s.host, capturedToken) {
                            while (true) {
                                try {
                                    val response = withContext(Dispatchers.IO) {
                                        httpGet("http://${s.host}:${s.port}/sync/request")
                                    }
                                    if (response.trim() == "YES" && state is WifiSyncState.Found) {
                                        state = WifiSyncState.DesktopSyncRequest(s.host, s.port, capturedToken)
                                        break
                                    }
                                } catch (_: Exception) {
                                    // Server unreachable — keep trying
                                }
                                delay(5_000)
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Verbunden mit ${s.host}:${s.port}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    state = WifiSyncState.Syncing
                                    scope.launch {
                                        runPushFlow(s, storedToken ?: return@launch, syncExporter, settingsRepository, state = { state = it })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Meine Daten auf Desktop übertragen")
                            }
                            OutlinedButton(
                                onClick = {
                                    state = WifiSyncState.Syncing
                                    scope.launch {
                                        runPullFlow(s, storedToken ?: return@launch, syncImporter, settingsRepository, state = { state = it })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Daten vom Desktop holen")
                            }
                            TextButton(
                                onClick = {
                                    discovery.stopDiscovery()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Abbrechen")
                            }
                            TextButton(
                                onClick = {
                                    settingsRepository.set(tokenKey(s.host), "")
                                    storedToken = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Kopplungscode ändern")
                            }
                        }
                    }
                }

                // ── DESKTOP SYNC REQUEST ─────────────────────────────────
                is WifiSyncState.DesktopSyncRequest -> {
                    AlertDialog(
                        onDismissRequest = { state = WifiSyncState.Found(s.host, s.port) },
                        title = { Text("Synchronisieren?") },
                        text = { Text("Desktop möchte synchronisieren. Jetzt synchronisieren?") },
                        confirmButton = {
                            Button(onClick = {
                                val found = WifiSyncState.Found(s.host, s.port)
                                state = WifiSyncState.Syncing
                                scope.launch {
                                    runPushFlow(
                                        found, s.token, syncExporter, settingsRepository,
                                        followWithPull = true,
                                        state = { state = it },
                                    )
                                }
                            }) { Text("Ja") }
                        },
                        dismissButton = {
                            TextButton(onClick = { state = WifiSyncState.Found(s.host, s.port) }) {
                                Text("Nein")
                            }
                        },
                    )
                }

                // ── SYNCING ───────────────────────────────────────────────
                WifiSyncState.Syncing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 48.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Synchronisiere…", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // ── PREVIEW ───────────────────────────────────────────────
                is WifiSyncState.Preview -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            if (s.flow == SyncFlow.PUSH) "Vorschau (Desktop-Seite)" else "Vorschau (lokal)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        PreviewRow("Neu", s.newCount)
                        PreviewRow("Aktualisiert", s.updatedCount)
                        PreviewRow("Unverändert", s.unchangedCount)
                        if (s.skippedCount > 0) PreviewRow("Übersprungen", s.skippedCount)

                        if (s.warnings.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            s.warnings.take(5).forEach { w ->
                                Text("⚠ $w", style = MaterialTheme.typography.bodySmall)
                            }
                            if (s.warnings.size > 5) {
                                Text(
                                    "… und ${s.warnings.size - 5} weitere Warnungen",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                state = WifiSyncState.Syncing
                                scope.launch {
                                    confirmPreview(s, syncWriter, state = { state = it })
                                    if (s.followWithPull && state is WifiSyncState.Success) {
                                        state = WifiSyncState.Syncing
                                        val token = settingsRepository.get(tokenKey(s.host))
                                        if (token != null) {
                                            runPullFlow(
                                                WifiSyncState.Found(s.host, s.port),
                                                token,
                                                syncImporter,
                                                settingsRepository,
                                                state = { state = it },
                                            )
                                        }
                                    }
                                    if (state is WifiSyncState.Success) onSyncComplete()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Bestätigen")
                        }
                        TextButton(
                            onClick = { state = WifiSyncState.Found(s.host, s.port) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Abbrechen")
                        }
                    }
                }

                // ── SUCCESS ───────────────────────────────────────────────
                is WifiSyncState.Success -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 48.dp),
                    ) {
                        Text(
                            "Sync abgeschlossen. ${s.newCount} neu, ${s.updatedCount} aktualisiert.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = {
                            discovery.stopDiscovery()
                            onDismiss()
                        }) {
                            Text("Fertig")
                        }
                    }
                }

                // ── ERROR ─────────────────────────────────────────────────
                is WifiSyncState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 48.dp),
                    ) {
                        Text(
                            s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val found = s.foundState
                            if (found != null) {
                                Button(onClick = { state = found }) { Text("Erneut versuchen") }
                            }
                            TextButton(onClick = {
                                discovery.stopDiscovery()
                                onDismiss()
                            }) {
                                Text("Abbrechen")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Flow helpers (suspend, run on IO) ────────────────────────────────────────

private suspend fun runPushFlow(
    found: WifiSyncState.Found,
    token: String,
    syncExporter: SyncExporter,
    settingsRepository: ISettingsRepository,
    followWithPull: Boolean = false,
    state: (WifiSyncState) -> Unit,
) {
    runCatching {
        val exportJson = withContext(Dispatchers.Default) { syncExporter.export() }
        val ciphertext = withContext(Dispatchers.Default) { encryptSyncPayload(exportJson, token) }
        val responseBody = withContext(Dispatchers.IO) {
            httpPost("http://${found.host}:${found.port}/sync/import", ciphertext)
        }
        val previewJson = withContext(Dispatchers.Default) { decryptSyncPayload(responseBody, token) }
        val response = json.decodeFromString<ServerPreviewResponse>(previewJson)
        state(
            WifiSyncState.Preview(
                host = found.host,
                port = found.port,
                flow = SyncFlow.PUSH,
                newCount = response.newCount,
                updatedCount = response.updatedCount,
                unchangedCount = response.unchangedCount,
                skippedCount = response.skippedCount,
                warnings = response.warnings,
                followWithPull = followWithPull,
            )
        )
    }.onFailure { e ->
        if (e is WrongTokenException) {
            settingsRepository.set(tokenKey(found.host), "")
            state(WifiSyncState.Error(
                "Kopplungscode falsch. Bitte gib den Code erneut ein.",
                WifiSyncState.Found(found.host, found.port)
            ))
        } else {
            state(WifiSyncState.Error("Fehler beim Übertragen: ${e.message}", found))
        }
    }
}

private suspend fun runPullFlow(
    found: WifiSyncState.Found,
    token: String,
    syncImporter: SyncImporter,
    settingsRepository: ISettingsRepository,
    state: (WifiSyncState) -> Unit,
) {
    runCatching {
        val ciphertext = withContext(Dispatchers.IO) {
            httpGet("http://${found.host}:${found.port}/sync/export")
        }
        val exportJson = withContext(Dispatchers.Default) {
            decryptSyncPayload(ciphertext, token)
        }
        val result = withContext(Dispatchers.Default) { syncImporter.import(exportJson) }
        when (result) {
            is SyncImportResult.Error -> state(
                WifiSyncState.Error("Import-Fehler: ${result.error}", found)
            )
            is SyncImportResult.Preview -> {
                val p = result.preview
                state(
                    WifiSyncState.Preview(
                        host = found.host,
                        port = found.port,
                        flow = SyncFlow.PULL,
                        newCount = p.newCount,
                        updatedCount = p.updatedCount,
                        unchangedCount = p.unchangedCount,
                        skippedCount = p.skippedCount,
                        warnings = p.warnings,
                        localPreview = p,
                    )
                )
            }
        }
    }.onFailure { e ->
        if (e is WrongTokenException || e is IllegalArgumentException || e.message?.contains("Ciphertext") == true) {
            settingsRepository.set(tokenKey(found.host), "")
            state(WifiSyncState.Error(
                "Kopplungscode falsch. Bitte gib den Code erneut ein.",
                WifiSyncState.Found(found.host, found.port)
            ))
        } else {
            state(WifiSyncState.Error("Fehler beim Abrufen: ${e.message}", found))
        }
    }
}

private suspend fun confirmPreview(
    preview: WifiSyncState.Preview,
    syncWriter: SyncWriter,
    state: (WifiSyncState) -> Unit,
) {
    runCatching {
        when (preview.flow) {
            SyncFlow.PUSH -> {
                withContext(Dispatchers.IO) {
                    httpPost("http://${preview.host}:${preview.port}/sync/confirm")
                }
                state(WifiSyncState.Success(preview.newCount, preview.updatedCount))
            }
            SyncFlow.PULL -> {
                val local = requireNotNull(preview.localPreview)
                val result = withContext(Dispatchers.Default) { syncWriter.write(local) }
                when (result) {
                    is SyncResult.Success -> {
                        runCatching { withContext(Dispatchers.IO) { httpPost("http://${preview.host}:${preview.port}/sync/confirm") } }
                        state(WifiSyncState.Success(result.newCount, result.updatedCount))
                    }
                    is SyncResult.Error -> state(
                        WifiSyncState.Error(
                            "Schreibfehler: ${result.error}",
                            WifiSyncState.Found(preview.host, preview.port),
                        )
                    )
                }
            }
        }
    }.onFailure { e ->
        state(
            WifiSyncState.Error(
                "Fehler beim Bestätigen: ${e.message}",
                WifiSyncState.Found(preview.host, preview.port),
            )
        )
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

@Composable
private fun PreviewRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium)
    }
}
