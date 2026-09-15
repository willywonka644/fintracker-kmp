package io.github.willywonka644.fintracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.R

@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onPinClick: () -> Unit,
    onRestoreJson: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onHelpClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onPinClick) {
                    Text(stringResource(R.string.pin_settings_title))
                }
                Button(onClick = onRestoreJson) {
                    Text(stringResource(R.string.restore_json))
                }
                Button(onClick = onExportCsv) {
                    Text(stringResource(R.string.export_csv))
                }
                Button(onClick = onExportJson) {
                    Text(stringResource(R.string.export_json))
                }
                Button(onClick = onHelpClick) {
                    Text(stringResource(R.string.settings_help))
                }
                Button(onClick = onAboutClick) {
                    Text(stringResource(R.string.settings_about))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
