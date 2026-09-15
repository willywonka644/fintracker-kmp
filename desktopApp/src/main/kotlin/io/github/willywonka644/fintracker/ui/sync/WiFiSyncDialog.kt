package io.github.willywonka644.fintracker.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.willywonka644.fintracker.AppServices
import io.github.willywonka644.fintracker.Services
import io.github.willywonka644.fintracker.sync.SyncPreview
import io.github.willywonka644.fintracker.sync.SyncResult
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("WiFiSyncDialog")

@Composable
fun WiFiSyncDialog(
    onDismiss: () -> Unit,
    onSyncConfirmed: () -> Unit,
    /**
     * Wie bei [io.github.willywonka644.fintracker.AppContent]: Vorgabe sind die
     * Produktionsdienste, ein Test reicht eine Implementierung auf einer
     * In-Memory-Datenbank herein. Ohne diesen Parameter hinge jeder Test, der
     * den Dialog öffnet, am echten Sync-Server und an der echten Datenbank.
     */
    services: Services = AppServices,
) {
    val server = services.syncServer
    val isRunning by server.isRunning.collectAsState()
    val pendingPreview by server.pendingPreview.collectAsState()
    val pairingToken by server.pairingToken.collectAsState()
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi Sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isRunning) {
                    // ── Stopped state ────────────────────────────────────────
                    Text(
                        "Start the server so your Android device can connect.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            scope.launch { withContext(Dispatchers.IO) { server.start() } }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start Wi-Fi Sync Server")
                    }
                } else {
                    // ── Running state ─────────────────────────────────────────
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(FinTheme.colors.income)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Server running",
                            style = MaterialTheme.typography.bodyMedium,
                            color = FinTheme.colors.income,
                        )
                    }
                    Text(
                        "Port: ${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Address: ${getLocalIpAddress()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // ── Pairing code panel ────────────────────────────────────
                    if (pairingToken != null) {
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Pairing code",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                pairingToken!!,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp,
                            )
                            Text(
                                "Enter this code on your Android device when prompted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = { server.regenerateToken() },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text("Regenerate code")
                            }
                        }
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = {
                            scope.launch { withContext(Dispatchers.IO) { server.stop() } }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Stop Server")
                    }

                    HorizontalDivider()

                    // ── Import direction panel ────────────────────────────────
                    if (pendingPreview == null) {
                        Text(
                            "Waiting for Android to push sync…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { server.requestSync() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Sync Now")
                        }
                    } else {
                        PendingPreviewPanel(
                            preview = pendingPreview!!,
                            onConfirm = {
                                val snapshot = pendingPreview
                                log.debug("[WiFiSyncDialog] Confirm Import clicked — snapshot=${snapshot != null}")
                                scope.launch {
                                    if (snapshot == null) {
                                        log.warn("[WiFiSyncDialog] pendingPreview was null at confirm time, aborting")
                                        onDismiss()
                                        return@launch
                                    }
                                    val result = withContext(Dispatchers.Default) {
                                        services.syncWriter.write(snapshot)
                                    }
                                    log.debug("[WiFiSyncDialog] syncWriter.write result=$result")
                                    if (result is SyncResult.Success) {
                                        log.debug("[WiFiSyncDialog] write success — calling confirmImportedOnDesktop + onSyncConfirmed")
                                        server.confirmImportedOnDesktop()
                                        onSyncConfirmed()
                                    } else {
                                        log.warn("[WiFiSyncDialog] write failed ($result) — discarding preview")
                                        server.discardPendingPreview()
                                    }
                                    onDismiss()
                                }
                            },
                            onCancel = { server.discardPendingPreview() },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun PendingPreviewPanel(
    preview: SyncPreview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Incoming sync from Android", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(2.dp))
        PreviewRow("New", preview.newCount)
        PreviewRow("Updated", preview.updatedCount)
        PreviewRow("Unchanged", preview.unchangedCount)
        if (preview.skippedCount > 0) PreviewRow("Skipped", preview.skippedCount)

        if (preview.warnings.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            preview.warnings.take(5).forEach { w ->
                Text("⚠ $w", style = MaterialTheme.typography.bodySmall)
            }
            if (preview.warnings.size > 5) {
                Text(
                    "… and ${preview.warnings.size - 5} more warnings",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onConfirm) { Text("Confirm Import") }
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
        Text(count.toString(), style = MaterialTheme.typography.bodySmall)
    }
}

private fun getLocalIpAddress(): String =
    NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { !it.isLoopback && it.isUp }
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull()
        ?.hostAddress
        ?: "unknown"
