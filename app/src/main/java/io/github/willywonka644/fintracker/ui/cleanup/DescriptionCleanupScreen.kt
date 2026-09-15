package io.github.willywonka644.fintracker.ui.cleanup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.booking.DescriptionCleanup
import io.github.willywonka644.fintracker.ui.theme.FinTheme

/**
 * Datenbankpflege: Buchungen, die dasselbe meinen und verschieden heißen (#135).
 *
 * Jede Gruppe wird für sich entschieden, nie alle auf einmal — dieselbe Zurückhaltung wie
 * bei den Umbuchungs-Vorschlägen (#122), und aus demselben Grund: was zwei Texte meinen,
 * weiß nur der Nutzer. Eine abgelehnte Gruppe kommt nicht wieder.
 *
 * Der Zieltext ist ein Feld, kein fester Vorschlag. Die Regel findet, *dass* mehrere
 * Schreibweisen zusammengehören; wie das Ergebnis heißen soll, ist eine andere Frage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DescriptionCleanupScreen(
    groups: List<DescriptionCleanup.Group>,
    onRename: (DescriptionCleanup.Group, String) -> Unit,
    onReject: (DescriptionCleanup.Group) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beschreibungen aufräumen") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nichts gefunden. Entweder heißen gleiche Dinge schon gleich, oder " +
                        "die Gruppen sind abgelehnt — abgelehnte kommen nicht wieder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = finColors.textSub,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        ) {
            item(key = "intro") {
                val bookings = groups.sumOf { it.totalUses }
                Text(
                    text = "${groups.size} ${if (groups.size == 1) "Gruppe" else "Gruppen"}, " +
                        "$bookings ${if (bookings == 1) "Buchung" else "Buchungen"}. " +
                        "Gefunden werden nur Schreibweisen desselben Textes und Verschreiber — " +
                        "dass zwei verschiedene Texte dasselbe meinen, kann nur jemand wissen, " +
                        "der die Buchungen gemacht hat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = finColors.textSub,
                )
            }

            items(groups, key = { it.key }) { group ->
                GroupCard(
                    group = group,
                    onRename = { target -> onRename(group, target) },
                    onReject = { onReject(group) },
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: DescriptionCleanup.Group,
    onRename: (String) -> Unit,
    onReject: () -> Unit,
) {
    val finColors = FinTheme.colors
    var target by remember(group.key) { mutableStateOf(group.suggestedTarget) }
    val affected = remember(group, target) {
        group.variants.filter { it.description.trim() != target.trim() }.sumOf { it.uses }
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (group.reason) {
                    DescriptionCleanup.Reason.SPELLING -> "Gleicher Text, andere Schreibweise"
                    DescriptionCleanup.Reason.TYPO -> "Wahrscheinlich ein Verschreiber"
                },
                style = MaterialTheme.typography.labelSmall,
                color = finColors.textSub,
            )

            Spacer(Modifier.height(8.dp))

            group.variants.forEach { variant ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = variant.description,
                        style = MaterialTheme.typography.bodyMedium,
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
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        // Das ist der Teil, der nicht nur die Optik kostet: dieselbe Sache
                        // steht in zwei Kategorien und damit in zwei Balken der Auswertung.
                        text = "Diese Buchungen liegen in verschiedenen Kategorien: " +
                            group.categories.entries
                                .sortedByDescending { it.value }
                                .joinToString(", ") { "${it.key} (${it.value})" } +
                            ". Das Umbenennen ändert daran nichts — die Kategorie bleibt, " +
                            "wie sie ist.",
                        style = MaterialTheme.typography.labelSmall,
                        color = finColors.textSub,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Alle künftig nennen") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                    Text("Gehört nicht zusammen")
                }
                Button(
                    onClick = { onRename(target.trim()) },
                    enabled = target.isNotBlank() && affected > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (affected > 0) "Umbenennen ($affected)" else "Nichts zu tun")
                }
            }
        }
    }
}
