package io.github.willywonka644.fintracker.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #97 — the sidebar gives way instead of holding its 232 dp.
 *
 * The failure these lock down is not cosmetic: the sidebar never yielded, so every
 * pixel a narrowing window lost came out of the content area alone, until the
 * dashboard heading was rendered one character per line.
 *
 * The threshold itself (`SIDEBAR_COLLAPSE_BELOW`) is private, so the tests bracket
 * it with widths that are unambiguously above and below it rather than reading the
 * constant.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopShellLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private var destination = DesktopNavDestination.DASHBOARD

    private fun showShell(width: Dp, unsyncedCount: Int = 0, lastSyncedAt: Long = 0L) {
        compose.setContent {
            FinTrackerTheme {
                Box(Modifier.requiredSize(width, 700.dp)) {
                    DesktopShell(
                        selectedDest = destination,
                        onDestChange = { destination = it },
                        unsyncedCount = unsyncedCount,
                        lastSyncedAt = lastSyncedAt,
                    ) {
                        Box(Modifier.fillMaxSize().testTag(CONTENT))
                    }
                }
            }
        }
    }

    @Test
    fun aWideWindowKeepsTheLabelledSidebar() {
        showShell(1200.dp)

        compose.onNodeWithText("FinTracker").assertExists()
        compose.onNodeWithText("Buchungen").assertExists()
        compose.onNodeWithText("Kontoabgleich").assertExists()
        compose.onNodeWithText("Server-Sync").assertExists()
    }

    @Test
    fun theSyncCardNamesTheDirectionItKnowsAbout() {
        showShell(1200.dp)

        // "Nichts offen" wurde als "alles auf demselben Stand" gelesen, obwohl die
        // Karte nur weiss, was dieser Rechner noch nicht gesendet hat (#131).
        compose.onNodeWithText("Nichts zu senden").assertExists()
        compose.onNodeWithText("Nichts offen").assertDoesNotExist()
    }

    @Test
    fun aDeviceThatNeverSyncedSaysSoRatherThanShowing1970() {
        showShell(1200.dp)

        compose.onNodeWithText("noch nie abgeglichen").assertExists()
    }

    @Test
    fun pendingChangesAreCountedAsSomethingToSend() {
        showShell(1200.dp, unsyncedCount = 3)

        compose.onNodeWithText("3 Änderungen zu senden").assertExists()
    }

    @Test
    fun aNarrowWindowDropsTheLabels() {
        showShell(700.dp)

        compose.onNodeWithText("Buchungen").assertDoesNotExist()
        compose.onNodeWithText("Kontoabgleich").assertDoesNotExist()
        compose.onNodeWithText("ÜBERSICHT").assertDoesNotExist()
    }

    @Test
    fun theCollapsedRailStillNavigates() {
        showShell(700.dp)

        compose.onNodeWithTag(DesktopShellTestTags.nav(DesktopNavDestination.AUSWERTUNGEN))
            .performClick()

        assertEquals(DesktopNavDestination.AUSWERTUNGEN, destination)
    }

    @Test
    fun theCollapsedRailGivesTheWidthBackToTheContent() {
        showShell(700.dp)
        val collapsed = compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().width

        // The rail is 60 dp. Keeping more than 600 of a 700 dp window is only
        // possible if the sidebar actually yielded — with the old fixed 232 dp the
        // content was left with roughly 467.
        assertTrue(
            collapsed > 600.dp,
            "content should keep most of a 700 dp window, was $collapsed",
        )
    }

    @Test
    fun theWideLayoutStillPaysForTheFullSidebar() {
        showShell(1200.dp)
        val wide = compose.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().width

        // Guards the other direction: the collapse must not leak into normal use.
        assertTrue(
            wide < 1000.dp,
            "the labelled sidebar should still take its 232 dp, content was $wide",
        )
    }

    private companion object {
        const val CONTENT = "shell_content"
    }

    /**
     * #114 — every destination is actually listed.
     *
     * The sidebar is a hand-written list, not a loop over the enum, so adding a value to
     * DesktopNavDestination leaves it unreachable and nothing complains. That is exactly
     * what happened to KONTEN: the screen existed, the branch existed, and there was no
     * way to get there.
     */
    @Test
    fun everyDestinationHasAnEntryInTheSidebar() {
        showShell(width = 1200.dp)

        DesktopNavDestination.entries.forEach { destination ->
            compose.onNodeWithText(destination.label).assertExists(
                "${destination.name} is missing from the sidebar"
            )
        }
    }

}
