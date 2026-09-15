package io.github.willywonka644.fintracker.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.sync.lastSyncedLabel
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/** Semantics tags for UI tests — one per sidebar destination. */
object DesktopShellTestTags {
    fun nav(dest: DesktopNavDestination) = "nav_${dest.name}"
}

enum class DesktopNavDestination(
    val label: String,
    val icon: ImageVector,
) {
    DASHBOARD("Dashboard", Icons.Filled.GridView),
    // #114: den Bildschirm gab es schon, er hing nur an nichts — ohne ihn liess sich am
    // Desktop kein Konto loeschen, obwohl die Oberflaeche dafuer fertig dalag.
    KONTEN("Konten", Icons.Outlined.AccountBalance),
    BUCHUNGEN("Buchungen", Icons.AutoMirrored.Filled.List),
    AUSWERTUNGEN("Auswertungen", Icons.Filled.BarChart),
    DAUERAUFTRAEGE("Daueraufträge", Icons.Filled.Repeat),
    RATENZAHLUNGEN("Ratenzahlungen", Icons.Filled.Payments),
    EINSTELLUNGEN("Einstellungen", Icons.Filled.Settings),
}

private val SIDEBAR_WIDTH = 232.dp

/**
 * Icon-only width for the collapsed sidebar (#97).
 *
 * The old fixed 232 dp never yielded, so every pixel the window lost came out of
 * the content area alone. Below the threshold the labels go and the rail keeps
 * the same icons in the same order, which is the state a user can widen out of.
 */
private val SIDEBAR_RAIL_WIDTH = 60.dp

/** Window width below which the sidebar drops to icons. */
private val SIDEBAR_COLLAPSE_BELOW = 1000.dp

@Composable
fun DesktopShell(
    selectedDest: DesktopNavDestination,
    onDestChange: (DesktopNavDestination) -> Unit,
    unsyncedCount: Int = 0,
    onSyncClick: () -> Unit = {},
    /** Wann dieses Geraet zuletzt abgeglichen hat, 0 = noch nie (#131). */
    lastSyncedAt: Long = 0L,
    onCategoriesClick: () -> Unit = {},
    /** Wie viele Umbenennungs-Gruppen offen sind (#135); 0 blendet den Eintrag aus. */
    cleanupGroupCount: Int = 0,
    onCleanupClick: () -> Unit = {},
    onAuditClick: () -> Unit = {},
    onReconciliationClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    // Measured with `onSizeChanged`, not with `BoxWithConstraints`, and #103 is
    // why. The subcomposition BoxWithConstraints performs left the composition
    // redrawing without end in CI: `waitForIdle` never returned, and the event
    // thread was busy drawing frame after frame rather than deadlocked. Two runs
    // on the same branch settled it — without it, five attempts out of five
    // passed; with it back and everything else identical, the hang returned.
    //
    // This reads the size after layout instead of composing against it. The first
    // frame is laid out as if wide and corrects itself on the next, which nobody
    // can see and no test minds. It cannot loop: what is measured here is the
    // container, whose size does not depend on the decision taken from it.
    var widthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val collapsed = widthPx > 0 && with(density) { widthPx.toDp() } < SIDEBAR_COLLAPSE_BELOW

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { widthPx = it.width }) {
        Row(modifier = Modifier.fillMaxSize()) {
            DesktopSidebar(
                collapsed = collapsed,
                selectedDest = selectedDest,
                onDestChange = onDestChange,
                unsyncedCount = unsyncedCount,
                onSyncClick = onSyncClick,
                lastSyncedAt = lastSyncedAt,
                onCategoriesClick = onCategoriesClick,
                cleanupGroupCount = cleanupGroupCount,
                onCleanupClick = onCleanupClick,
                onAuditClick = onAuditClick,
                onReconciliationClick = onReconciliationClick,
                onHelpClick = onHelpClick,
                onAboutClick = onAboutClick,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 1400.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DesktopSidebar(
    collapsed: Boolean,
    selectedDest: DesktopNavDestination,
    onDestChange: (DesktopNavDestination) -> Unit,
    unsyncedCount: Int,
    onSyncClick: () -> Unit,
    lastSyncedAt: Long,
    onCategoriesClick: () -> Unit,
    cleanupGroupCount: Int,
    onCleanupClick: () -> Unit,
    onAuditClick: () -> Unit,
    onReconciliationClick: () -> Unit,
    onHelpClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    val finColors = FinTheme.colors

    Column(
        modifier = Modifier
            .width(if (collapsed) SIDEBAR_RAIL_WIDTH else SIDEBAR_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // ── FT Logo ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = if (collapsed) 14.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(finColors.heroGradient),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "FT",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (!collapsed) {
                Text(
                    text = "FinTracker",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ── Nav items ────────────────────────────────────────────────────────
        // Not scrollable, deliberately. A `verticalScroll` here (added with #97,
        // removed again after CI kept hanging) is the only change that touches
        // what `AppContentTest` clicks, and that test went from stable to hanging
        // in two runs out of three once it landed — a scroll container that keeps
        // re-measuring never lets the composition go idle, and `performClick`
        // waits for idle forever.
        //
        // What it was for: at a very short window the rail's eleven entries can
        // push the sync card past the bottom edge. That is a cosmetic problem at
        // a size nobody uses; a test suite that hangs is not. See the follow-up
        // issue before putting it back.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            if (!collapsed) NavGroupLabel("Übersicht")
            NavItem(DesktopNavDestination.DASHBOARD, selectedDest, onDestChange, collapsed)
            NavItem(DesktopNavDestination.KONTEN, selectedDest, onDestChange, collapsed)
            NavItem(DesktopNavDestination.BUCHUNGEN, selectedDest, onDestChange, collapsed)
            NavItem(DesktopNavDestination.AUSWERTUNGEN, selectedDest, onDestChange, collapsed)

            Spacer(Modifier.height(8.dp))
            if (!collapsed) NavGroupLabel("Werkzeuge")
            NavItem(DesktopNavDestination.DAUERAUFTRAEGE, selectedDest, onDestChange, collapsed)
            NavItem(DesktopNavDestination.RATENZAHLUNGEN, selectedDest, onDestChange, collapsed)
            SidebarActionItem("Kategorien", Icons.Filled.Label, onCategoriesClick, collapsed)
            // Nur wenn es etwas zu entscheiden gibt — wie die Umbuchungs-Zeile auf
            // Android. Eine Zeile, die dauerhaft "nichts gefunden" sagt, beantwortet nichts.
            if (cleanupGroupCount > 0) {
                SidebarActionItem("Aufräumen ($cleanupGroupCount)", Icons.Filled.Spellcheck, onCleanupClick, collapsed)
            }
            SidebarActionItem("Audit", Icons.Filled.FactCheck, onAuditClick, collapsed)
            SidebarActionItem("Kontoabgleich", Icons.Filled.AccountBalance, onReconciliationClick, collapsed)

            Spacer(Modifier.height(8.dp))
            if (!collapsed) NavGroupLabel("Einstellungen")
            NavItem(DesktopNavDestination.EINSTELLUNGEN, selectedDest, onDestChange, collapsed)
            SidebarActionItem("Hilfe", Icons.AutoMirrored.Outlined.HelpOutline, onHelpClick, collapsed)
            SidebarActionItem("Über FinTracker", Icons.Outlined.Info, onAboutClick, collapsed)
        }

        // ── Sync card ────────────────────────────────────────────────────────
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SyncStatusCard(
            unsyncedCount = unsyncedCount,
            lastSyncedAt = lastSyncedAt,
            onClick = onSyncClick,
            collapsed = collapsed,
        )
    }
}

@Composable
private fun NavGroupLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = FinTheme.colors.textFaint,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun NavItem(
    dest: DesktopNavDestination,
    selectedDest: DesktopNavDestination,
    onDestChange: (DesktopNavDestination) -> Unit,
    collapsed: Boolean = false,
) {
    val finColors = FinTheme.colors
    val selected = dest == selectedDest
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else finColors.textSub
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .clickable { onDestChange(dest) }
                .testTag(DesktopShellTestTags.nav(dest))
                .padding(horizontal = if (collapsed) 0.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = dest.icon,
                // Carries the name once the label is gone, so the destination stays
                // reachable for screen readers and findable in the UI tests.
                contentDescription = dest.label,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            if (!collapsed) {
                Text(
                    text = dest.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Left accent bar for active item
        if (selected) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun SidebarActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    collapsed: Boolean = false,
) {
    val finColors = FinTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = if (collapsed) 0.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = finColors.textSub, modifier = Modifier.size(18.dp))
        if (!collapsed) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = finColors.textSub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SyncStatusCard(
    unsyncedCount: Int,
    lastSyncedAt: Long,
    onClick: () -> Unit,
    collapsed: Boolean = false,
) {
    val finColors = FinTheme.colors
    val hasUnsynced = unsyncedCount > 0
    val lastSynced = remember(lastSyncedAt) {
        lastSyncedLabel(lastSyncedAt, Clock.System.now().toEpochMilliseconds(), TimeZone.currentSystemDefault())
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = if (collapsed) 0.dp else 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(10.dp),
    ) {
        // Names the path it actually opens (#92): this card led to the Wi-Fi
        // dialog until the server became the normal way, and a button labelled
        // after the wrong one is worse than an unlabelled button.
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = "Server-Sync",
            tint = if (hasUnsynced) finColors.expense else finColors.income,
            modifier = Modifier.size(18.dp),
        )
        // On the rail only the icon is left; its colour keeps carrying the state.
        if (!collapsed) {
            Column {
                Text(
                    text = "Server-Sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Says what it knows, and only that (#131). "Nichts offen" wurde als
                // "alles auf demselben Stand" gelesen — die Zahl misst aber nur die
                // eine Richtung: was dieser Rechner noch nicht gesendet hat. Ueber den
                // Server weiss die Karte nichts und kann es nicht wissen, denn vor dem
                // Knopfdruck geht keine Anfrage hinaus.
                Text(
                    text = if (hasUnsynced) "$unsyncedCount Änderungen zu senden" else "Nichts zu senden",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnsynced) finColors.expense else finColors.income,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Der Zeitstempel lag seit #110/#114 im Store und wurde nirgends
                // gezeigt. Aus ihm schliesst der Leser selbst, was die Karte nicht
                // wissen kann.
                Text(
                    text = lastSynced?.let { "zuletzt: $it" } ?: "noch nie abgeglichen",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
