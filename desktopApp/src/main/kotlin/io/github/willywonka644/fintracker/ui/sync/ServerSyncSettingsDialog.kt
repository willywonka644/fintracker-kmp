package io.github.willywonka644.fintracker.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.AppServices
import io.github.willywonka644.fintracker.Services
import io.github.willywonka644.fintracker.sync.ServerUrl
import io.github.willywonka644.fintracker.sync.SyncClient
import io.github.willywonka644.fintracker.sync.checkSyncConnection
import io.github.willywonka644.fintracker.sync.describe
import io.github.willywonka644.fintracker.sync.syncHttpClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Address and key for the sync server on the desktop (#92).
 *
 * The counterpart of the Android settings screen, deliberately down to the
 * wording: the fields, the two-stage check and every sentence it can produce
 * come from `shared`, so the two platforms cannot start disagreeing about what a
 * `401` means. What differs is only where the pair is kept — `app_settings`
 * here, DataStore there, in both cases wherever that platform keeps its PIN.
 */
/** Semantics tags for UI tests — the fields, which carry no unique text of their own. */
object ServerSyncSettingsTestTags {
    const val URL = "sync_settings_url"
    const val API_KEY = "sync_settings_api_key"
}

private sealed interface CheckState {
    data object Idle : CheckState
    data object Running : CheckState
    data class Done(val message: String, val ok: Boolean) : CheckState
}

@Composable
fun ServerSyncSettingsDialog(
    onDismiss: () -> Unit,
    services: Services = AppServices,
    // Seam for tests (#92). The dialog owns the client's lifetime — it closes it
    // in the DisposableEffect below — so it takes a factory rather than a ready
    // client. A test hands in one backed by MockEngine; nothing else changes.
    httpClientFactory: () -> HttpClient = { syncHttpClient() },
) {
    val store = services.serverSyncStore
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var storedUrl by remember { mutableStateOf("") }
    var storedKey by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var check by remember { mutableStateOf<CheckState>(CheckState.Idle) }

    // Carried over from the Android screen, where it was a real trap: testing
    // works on the fields, a sync works on the store, and a green test that was
    // never saved makes the next sync report an address the user believes they
    // set. See the field-test notes on #91.
    val unsaved = url.trim() != storedUrl || apiKey.trim() != storedKey

    val http = remember { httpClientFactory() }
    DisposableEffect(Unit) { onDispose { http.close() } }
    val client = remember(http) { SyncClient(http) }

    LaunchedEffect(Unit) {
        val current = store.current()
        url = current.url
        apiKey = current.apiKey
        storedUrl = current.url
        storedKey = current.apiKey
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync-Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Adresse und Schlüssel des Servers, mit dem sich dieser Rechner abgleicht.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; saved = false; check = CheckState.Idle },
                    label = { Text("Server-Adresse") },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    supportingText = {
                        Text("Ohne Port wird :${ServerUrl.DEFAULT_PORT} ergänzt, ohne Schema http://")
                    },
                    modifier = Modifier.fillMaxWidth().testTag(ServerSyncSettingsTestTags.URL),
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false; check = CheckState.Idle },
                    label = { Text("API-Schlüssel") },
                    singleLine = true,
                    // Shown rather than masked, for the reason it is on Android: the
                    // key is pasted, and a character lost on the way through the
                    // clipboard is invisible behind dots and surfaces much later as
                    // "Schlüssel falsch".
                    supportingText = { Text("Wird eingefügt. Sichtbar, damit ein Fehler beim Einfügen auffällt.") },
                    modifier = Modifier.fillMaxWidth().testTag(ServerSyncSettingsTestTags.API_KEY),
                )

                when {
                    unsaved -> Text(
                        "Noch nicht gespeichert — der Abgleich benutzt weiterhin die gespeicherten Werte.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )

                    saved -> Text(
                        "Gespeichert.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                when (val state = check) {
                    is CheckState.Idle -> Unit

                    is CheckState.Running -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Verbindung wird geprüft …")
                    }

                    is CheckState.Done -> Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.ok) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                when (val normalized = ServerUrl.normalize(url)) {
                    is ServerUrl.Result.Invalid ->
                        check = CheckState.Done(describe(normalized.reason), ok = false)

                    is ServerUrl.Result.Valid -> {
                        store.save(normalized.url, apiKey)
                        // Show what was stored, so the repair is visible rather than
                        // the field changing for no stated reason.
                        url = normalized.url
                        apiKey = apiKey.trim()
                        storedUrl = normalized.url
                        storedKey = apiKey.trim()
                        saved = true
                    }
                }
            }) { Text("Speichern") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        check = CheckState.Running
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                checkSyncConnection(client, url, apiKey)
                            }
                            check = CheckState.Done(result.message, result.ok)
                        }
                    },
                    enabled = check !is CheckState.Running,
                ) { Text("Verbindung testen") }

                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        },
    )
}
