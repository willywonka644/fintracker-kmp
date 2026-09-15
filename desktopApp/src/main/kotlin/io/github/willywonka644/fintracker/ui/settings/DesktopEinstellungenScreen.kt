package io.github.willywonka644.fintracker.ui.settings

import io.github.willywonka644.fintracker.APP_VERSION
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.ui.components.DesktopFinCard
import io.github.willywonka644.fintracker.ui.theme.FinTheme

@Composable
fun DesktopEinstellungenScreen(
    pinEnabled: Boolean,
    onShowWiFiSync: () -> Unit,
    onShowServerSync: () -> Unit,
    /** The stored address, or empty. Shown as-is — see the row for why. */
    serverSyncUrl: String,
    onShowPinSettings: () -> Unit,
    onJsonBackup: () -> Unit,
    onJsonRestore: () -> Unit,
    onCsvExport: () -> Unit,
    onCsvImport: () -> Unit,
) {
    val finColors = FinTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            "Einstellungen",
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // ── Sync ──────────────────────────────────────────────────────────────
        //
        // Two paths on purpose (#92): the server is the normal one, Wi-Fi stays
        // as the fallback for when the Pi is off or being rebuilt. Which is
        // which has to be readable here rather than guessable — otherwise the
        // standing question becomes "why did my sync not arrive", and the answer
        // is "you used the other path".
        SettingsSection(title = "Synchronisation") {
            SettingsRow(
                icon = Icons.Filled.Cloud,
                title = "Server-Sync",
                // The address itself, not a verdict on it. "Eingerichtet" was
                // shown for any two non-empty fields, so a mistyped host like
                // `http://127.:8080` — syntactically valid, resolves nowhere —
                // read as working while every sync failed. Showing the address
                // lets the mistake be seen instead of judged.
                subtitle = serverSyncUrl.ifBlank { "Noch nicht eingerichtet" },
                trailingContent = {
                    Text(
                        if (serverSyncUrl.isBlank()) "Einrichten" else "Ändern",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (serverSyncUrl.isBlank()) finColors.expense else finColors.textSub,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                onClick = onShowServerSync,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            SettingsRow(
                icon = Icons.Outlined.Wifi,
                title = "WLAN-Sync (Notweg)",
                // Ohne den Zaehler (#124): ausstehende Aenderungen werden gegen den
                // letzten Server-Abgleich gemessen, und ein "Synchronisieren" auf dem
                // Notweg las sich wie die faellige Handlung. Der Stand steht in der
                // Seitenleiste, wo er auch den richtigen Weg oeffnet.
                subtitle = "Ohne Server, im selben WLAN — wenn der Pi aus ist",
                trailingContent = {
                    Text(
                        "Öffnen",
                        style = MaterialTheme.typography.labelMedium,
                        color = finColors.textSub,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                onClick = onShowWiFiSync,
            )
        }

        // ── Sicherheit ────────────────────────────────────────────────────────
        SettingsSection(title = "Sicherheit") {
            SettingsRow(
                icon = Icons.Filled.Lock,
                title = "PIN-Schutz",
                subtitle = if (pinEnabled) "PIN ist aktiviert" else "Kein PIN gesetzt",
                trailingContent = {
                    Switch(
                        checked = pinEnabled,
                        onCheckedChange = { onShowPinSettings() },
                    )
                },
                onClick = onShowPinSettings,
            )
        }

        // ── Daten ─────────────────────────────────────────────────────────────
        SettingsSection(title = "Daten") {
            SettingsRow(
                icon = Icons.Filled.Backup,
                title = "JSON Backup",
                subtitle = "Alle Daten exportieren",
                onClick = onJsonBackup,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            SettingsRow(
                icon = Icons.Filled.Restore,
                title = "JSON Wiederherstellen",
                subtitle = "Backup importieren (überschreibt aktuelle Daten)",
                onClick = onJsonRestore,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            SettingsRow(
                icon = Icons.Filled.FileUpload,
                title = "CSV Exportieren",
                subtitle = "Buchungen als CSV ausgeben",
                onClick = onCsvExport,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            SettingsRow(
                icon = Icons.Filled.FileDownload,
                title = "CSV Importieren",
                subtitle = "Buchungen aus CSV-Datei laden",
                onClick = onCsvImport,
            )
        }

        // ── App ───────────────────────────────────────────────────────────────
        SettingsSection(title = "App") {
            SettingsRow(
                icon = Icons.Filled.Info,
                title = "FinTracker Desktop",
                // Aus dem Build statt von Hand (#114). Hier stand "Phase 6 · Trust Blue
                // Design", während Phase 7 längst lief — genau die Drift, die der
                // Über-Dialog schon hinter sich hat und die APP_VERSION unmöglich macht.
                subtitle = "Version $APP_VERSION",
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = FinTheme.colors.textFaint,
            modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
        )
        DesktopFinCard(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val modifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textSub)
            }
        }
        trailingContent?.invoke()
    }
}
