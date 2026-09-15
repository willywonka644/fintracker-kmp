package io.github.willywonka644.fintracker.ui.mehr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.ui.components.SectionHeader
import io.github.willywonka644.fintracker.sync.lastSyncedLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import io.github.willywonka644.fintracker.ui.theme.FinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MehrScreen(
    pinEnabled: Boolean = false,
    unsyncedCount: Int,
    onPinClick: () -> Unit,
    onKontenClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onDauerauftraegeClick: () -> Unit,
    onRatenzahlungenClick: () -> Unit,
    onAuditClick: () -> Unit,
    onKontoabgleichClick: () -> Unit,
    onCsvImportClick: () -> Unit,
    onLimitClick: () -> Unit,
    transferSuggestionCount: Int = 0,
    onTransferSuggestionsClick: () -> Unit = {},
    descriptionCleanupCount: Int = 0,
    onDescriptionCleanupClick: () -> Unit = {},
    onBelegScannenClick: () -> Unit,
    onQrScannenClick: () -> Unit,
    onExportJson: () -> Unit,
    onRestoreJson: () -> Unit,
    /** Wann dieses Geraet zuletzt abgeglichen hat, 0 = noch nie (#131). */
    lastSyncedAt: Long = 0L,
    onSyncWiFi: () -> Unit,
    onServerSyncClick: () -> Unit,
    onHelpClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mehr") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            SectionHeader(title = "Stammdaten")
            SettingsCard {
                MenuRow(
                    icon = Icons.Outlined.AccountBalance,
                    label = "Konten",
                    subtitle = "Anlegen, bearbeiten, löschen",
                    onClick = onKontenClick,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.AutoMirrored.Outlined.Label,
                    label = "Kategorien",
                    onClick = onCategoriesClick,
                )
            }

            Spacer(Modifier.height(4.dp))

            SectionHeader(title = "Kontobezogen")
            SettingsCard {
                MenuRow(
                    icon = Icons.Filled.Repeat,
                    label = "Daueraufträge",
                    onClick = onDauerauftraegeClick,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Filled.Payments,
                    label = "Ratenzahlungen",
                    onClick = onRatenzahlungenClick,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Filled.PlaylistAddCheck,
                    label = "Audit",
                    subtitle = "Konto wählen",
                    onClick = onAuditClick,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Filled.CompareArrows,
                    label = "Kontoabgleich",
                    subtitle = "Konto wählen",
                    onClick = onKontoabgleichClick,
                )
            }

            Spacer(Modifier.height(4.dp))

            SectionHeader(title = "Werkzeuge")
            SettingsCard {
                MenuRow(
                    icon = Icons.Outlined.TableChart,
                    label = "CSV-Import",
                    subtitle = "Konto wählen",
                    onClick = onCsvImportClick,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Filled.CreditCard,
                    label = "Limit / Dispo",
                    subtitle = "Konto wählen",
                    onClick = onLimitClick,
                )
                // Nur wenn es etwas zu entscheiden gibt. Der Bestand ist seit #122 bereinigt,
                // und eine Zeile, die dauerhaft "keine offen" sagt, beantwortet nichts —
                // dieselbe Begründung, mit der #111 die Übersicht entschlackt hat. Das
                // Werkzeug bleibt: ein CSV-Import, ein Beleg-Scan oder eine versehentlich
                // zweifach erfasste Umbuchung erzeugen neue Paare, und dann meldet sich
                // die Zeile von selbst wieder, statt gesucht werden zu müssen.
                if (transferSuggestionCount > 0) {
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Filled.SwapHoriz,
                        label = "Umbuchungen erkennen",
                        subtitle = "$transferSuggestionCount ${if (transferSuggestionCount == 1) "Vorschlag" else "Vorschläge"}",
                        onClick = onTransferSuggestionsClick,
                    )
                }
                // Aus demselben Grund nur bei Bedarf: ist aufgeraeumt oder abgelehnt,
                // beantwortet die Zeile nichts mehr. Ein CSV-Import bringt neue
                // Schreibweisen mit, und dann meldet sie sich von selbst wieder.
                if (descriptionCleanupCount > 0) {
                    MenuDivider()
                    MenuRow(
                        icon = Icons.Filled.Spellcheck,
                        label = "Beschreibungen aufräumen",
                        subtitle = "$descriptionCleanupCount ${if (descriptionCleanupCount == 1) "Gruppe" else "Gruppen"}",
                        onClick = onDescriptionCleanupClick,
                    )
                }
                MenuDivider()
                MenuRow(
                    icon = Icons.Filled.Receipt,
                    label = "Beleg scannen",
                    subtitle = "Konto wählen",
                    onClick = onBelegScannenClick,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Filled.QrCode2,
                    label = "QR-Code scannen",
                    subtitle = "Konto wählen",
                    onClick = onQrScannenClick,
                )
            }

            Spacer(Modifier.height(4.dp))

            SectionHeader(title = "Daten")
            SettingsCard {
                MenuRow(
                    icon = Icons.Outlined.Upload,
                    label = "JSON-Backup exportieren",
                    onClick = onExportJson,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Outlined.Download,
                    label = "JSON-Backup wiederherstellen",
                    onClick = onRestoreJson,
                )
                MenuDivider()
                // Which path is the normal one has to be readable here, not guessable (#124):
                // the server is the way the data travels, the WLAN-Sync stays as the Notweg for
                // when the Pi is off. The pending count is measured against the last server sync,
                // so it belongs on the server row — on the Wi-Fi row it made the fallback look
                // like the thing that owes the work.
                // Sagt beides und vermischt es nicht (#131): was noch nicht gesendet
                // ist, und wann zuletzt abgeglichen wurde. Der Zaehler misst nur die
                // eine Richtung — ob auf dem Server etwas Neues liegt, weiss das Geraet
                // erst, wenn es fragt, und gefragt wird nur auf Knopfdruck.
                val lastSynced = remember(lastSyncedAt) {
                    lastSyncedLabel(
                        lastSyncedAt,
                        Clock.System.now().toEpochMilliseconds(),
                        TimeZone.currentSystemDefault(),
                    )
                }
                MenuRow(
                    icon = Icons.Outlined.Cloud,
                    label = "Server-Sync",
                    subtitle = buildString {
                        append(
                            if (unsyncedCount > 0)
                                "$unsyncedCount Änderung${if (unsyncedCount != 1) "en" else ""} zu senden"
                            else
                                "Nichts zu senden"
                        )
                        append(" · ")
                        append(lastSynced?.let { "zuletzt: $it" } ?: "noch nie abgeglichen")
                    },
                    onClick = onServerSyncClick,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Outlined.Wifi,
                    label = "WLAN-Sync (Notweg)",
                    subtitle = "Ohne Server, im selben WLAN",
                    onClick = onSyncWiFi,
                )
            }

            Spacer(Modifier.height(4.dp))
            SectionHeader(title = "App")
            SettingsCard {
                MenuRow(
                    icon = Icons.Outlined.Lock,
                    label = "PIN-Schutz",
                    onClick = onPinClick,
                    trailing = {
                        Switch(
                            checked = pinEnabled,
                            onCheckedChange = { onPinClick() },
                        )
                    },
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    label = "Hilfe",
                    onClick = onHelpClick,
                )
                MenuDivider()
                MenuRow(
                    icon = Icons.Outlined.Info,
                    label = "Über FinTracker",
                    onClick = onAboutClick,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val finColors = FinTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(finColors.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = finColors.textFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    )
}
