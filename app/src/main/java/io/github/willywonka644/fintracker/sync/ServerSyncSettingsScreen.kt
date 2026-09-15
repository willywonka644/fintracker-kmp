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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Address and key for the sync server, with a test that says what is wrong (#91).
 *
 * The screen exists because of one sentence in the issue: a sync button that
 * says nothing is worse than none. Every mistake here — a typo in the address,
 * a key that lost a character on the way through the clipboard, a Pi that is
 * asleep — produces the same silence at the other end, so the job of this screen
 * is to turn that silence into a sentence that names the actual cause.
 */
private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Done(val message: String, val ok: Boolean) : TestState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSyncSettingsScreen(
    context: Context,
    onDismiss: () -> Unit,
) {
    val store = remember { ServerSyncStore(context) }
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<TestState>(TestState.Idle) }

    // What is actually in the store, to compare the fields against.
    //
    // Testing and saving are separate acts, and the test works on what is typed
    // while a sync works on what is stored. Without this the two can disagree
    // silently: a connection tested green, the screen left, and the sync then
    // reports "server not reachable" for an address the user believes they set.
    var storedUrl by remember { mutableStateOf("") }
    var storedKey by remember { mutableStateOf("") }
    val unsaved = loaded && (url.trim() != storedUrl || apiKey.trim() != storedKey)

    // One client for the lifetime of the screen. Closing it matters: the OkHttp
    // engine keeps a connection pool and dispatcher threads alive, and a screen
    // that is opened and left a few times would otherwise pile them up.
    val http = remember { syncHttpClient() }
    DisposableEffect(Unit) { onDispose { http.close() } }
    val client = remember(http) { SyncClient(http) }

    LaunchedEffect(Unit) {
        val current = store.current()
        url = current.url
        apiKey = current.apiKey
        storedUrl = current.url
        storedKey = current.apiKey
        loaded = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync-Server") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
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
            Text(
                "Adresse und Schlüssel des Servers, mit dem sich dieses Gerät abgleicht.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; saved = false; test = TestState.Idle },
                label = { Text("Server-Adresse") },
                placeholder = { Text("192.168.1.50") },
                singleLine = true,
                enabled = loaded,
                supportingText = {
                    Text("Ohne Port wird :${ServerUrl.DEFAULT_PORT} ergänzt, ohne Schema http://")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false; test = TestState.Idle },
                label = { Text("API-Schlüssel") },
                singleLine = true,
                enabled = loaded,
                supportingText = {
                    // Shown rather than masked on purpose: the key is pasted, and the
                    // failure it causes — a character lost on the way through the
                    // clipboard — is invisible behind dots and surfaces much later as
                    // "Schlüssel falsch".
                    Text("Wird eingefügt, nicht abgetippt. Sichtbar, damit ein Fehler beim Einfügen auffällt.")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        when (val normalized = ServerUrl.normalize(url)) {
                            is ServerUrl.Result.Invalid -> {
                                test = TestState.Done(describe(normalized.reason), ok = false)
                            }

                            is ServerUrl.Result.Valid -> scope.launch {
                                store.save(normalized.url, apiKey)
                                // Show what was actually stored, so the user sees the
                                // repair rather than wondering why the field changed.
                                url = normalized.url
                                apiKey = apiKey.trim()
                                storedUrl = normalized.url
                                storedKey = apiKey.trim()
                                saved = true
                            }
                        }
                    },
                    enabled = loaded && test !is TestState.Running,
                ) { Text("Speichern") }

                OutlinedButton(
                    onClick = {
                        test = TestState.Running
                        scope.launch { test = runTest(client, url, apiKey) }
                    },
                    enabled = loaded && test !is TestState.Running,
                ) { Text("Verbindung testen") }
            }

            // The warning wins over the confirmation: "Gespeichert." next to fields
            // that have since been edited is worse than no message at all.
            when {
                unsaved -> Text(
                    "Noch nicht gespeichert — der Abgleich benutzt weiterhin die gespeicherten Werte.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )

                saved -> Text(
                    "Gespeichert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            when (val state = test) {
                is TestState.Idle -> Unit

                is TestState.Running -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("Verbindung wird geprüft …")
                }

                is TestState.Done -> {
                    Text(
                        state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        overflow = TextOverflow.Clip,
                    )
                    VpnReminder(state.message)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Renders what [checkSyncConnection] decided; the order of causes lives there. */
private suspend fun runTest(client: SyncClient, rawUrl: String, apiKey: String): TestState.Done {
    val result = withContext(Dispatchers.IO) { checkSyncConnection(client, rawUrl, apiKey) }
    return TestState.Done(result.message, ok = result.ok)
}

