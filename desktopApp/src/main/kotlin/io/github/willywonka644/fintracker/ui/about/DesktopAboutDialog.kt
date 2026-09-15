package io.github.willywonka644.fintracker.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.APP_VERSION

@Composable
fun DesktopAboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Über FinTracker") },
        text = {
            Column(
                modifier = Modifier.widthIn(min = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("App-Name", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("FinTracker", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(4.dp))

                Text("Version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Version $APP_VERSION", style = MaterialTheme.typography.titleMedium)

                Spacer(Modifier.height(4.dp))

                Text(
                    "Alle Daten bleiben auf deinem Gerät. Keine Bankverbindung, kein Cloud-Sync, keine Drittanbieter.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
