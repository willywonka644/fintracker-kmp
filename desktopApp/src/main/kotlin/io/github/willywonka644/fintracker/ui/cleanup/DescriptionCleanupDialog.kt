package io.github.willywonka644.fintracker.ui.cleanup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.willywonka644.fintracker.booking.DescriptionCleanup
import io.github.willywonka644.fintracker.ui.theme.FinTheme

/**
 * Datenbankpflege am Desktop (#135) — das Gegenstück zu `DescriptionCleanupScreen` auf
 * Android, auf derselben Regel aus `shared`.
 *
 * Jede Gruppe wird für sich entschieden. Der Zieltext ist ein Feld, kein fester Vorschlag:
 * die Regel findet, *dass* mehrere Schreibweisen zusammengehören; wie das Ergebnis heißen
 * soll, ist eine andere Frage.
 */
@Composable
fun DescriptionCleanupDialog(
    groups: List<DescriptionCleanup.Group>,
    onRename: (DescriptionCleanup.Group, String) -> Unit,
    onReject: (DescriptionCleanup.Group) -> Unit,
    onDismiss: () -> Unit,
) {
    val finColors = FinTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Beschreibungen aufräumen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                if (groups.isEmpty()) {
                    Text(
                        text = "Nichts gefunden. Entweder heißen gleiche Dinge schon gleich, " +
                            "oder die Gruppen sind abgelehnt — abgelehnte kommen nicht wieder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = finColors.textSub,
                    )
                } else {
                    val bookings = groups.sumOf { it.totalUses }
                    Text(
                        text = "${groups.size} ${if (groups.size == 1) "Gruppe" else "Gruppen"}, " +
                            "$bookings ${if (bookings == 1) "Buchung" else "Buchungen"}. " +
                            "Gefunden werden nur Schreibweisen desselben Textes und Verschreiber — " +
                            "dass zwei verschiedene Texte dasselbe meinen, kann nur jemand wissen, " +
                            "der die Buchungen gemacht hat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = finColors.textSub,
                    )
                    Column(
                        modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        groups.forEachIndexed { index, group ->
                            if (index > 0) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                            }
                            GroupBlock(
                                group = group,
                                onRename = { target -> onRename(group, target) },
                                onReject = { onReject(group) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            }
        }
    }
}

@Composable
private fun GroupBlock(
    group: DescriptionCleanup.Group,
    onRename: (String) -> Unit,
    onReject: () -> Unit,
) {
    val finColors = FinTheme.colors
    var target by remember(group.key) { mutableStateOf(group.suggestedTarget) }
    val affected = remember(group, target) {
        group.variants.filter { it.description.trim() != target.trim() }.sumOf { it.uses }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = when (group.reason) {
                DescriptionCleanup.Reason.SPELLING -> "Gleicher Text, andere Schreibweise"
                DescriptionCleanup.Reason.TYPO -> "Wahrscheinlich ein Verschreiber"
            },
            style = MaterialTheme.typography.labelSmall,
            color = finColors.textSub,
        )
        Spacer(Modifier.height(6.dp))

        group.variants.forEach { variant ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = variant.description,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (variant.description == group.suggestedTarget) FontWeight.SemiBold
                                 else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${variant.uses}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
            }
        }

        if (group.categoriesDisagree) {
            Spacer(Modifier.height(6.dp))
            Text(
                // Der Teil, der nicht nur die Optik kostet: dieselbe Sache steht in zwei
                // Kategorien und damit in zwei Balken der Auswertung.
                text = "Verschiedene Kategorien: " +
                    group.categories.entries
                        .sortedByDescending { it.value }
                        .joinToString(", ") { "${it.key} (${it.value})" } +
                    ". Das Umbenennen ändert daran nichts.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Alle künftig nennen") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onReject) { Text("Gehört nicht zusammen") }
            Button(
                onClick = { onRename(target.trim()) },
                enabled = target.isNotBlank() && affected > 0,
            ) {
                Text(if (affected > 0) "Umbenennen ($affected)" else "Nichts zu tun")
            }
        }
    }
}
