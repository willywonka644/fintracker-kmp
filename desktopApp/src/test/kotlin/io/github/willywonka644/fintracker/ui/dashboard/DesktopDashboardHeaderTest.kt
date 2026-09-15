package io.github.willywonka644.fintracker.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

/**
 * #97 — the dashboard header reflows instead of being squeezed to nothing.
 *
 * The reported symptom was the heading rendered one character per line down the
 * whole window height. That happened because the title column was the only elastic
 * part of a single row that also held a 240 dp search field and two buttons; a
 * narrow window drove it to zero width and Compose broke the word per character.
 *
 * Sizes are forced with `requiredSize` so the assertions do not depend on whatever
 * surface the test host happens to provide.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopDashboardHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    private val giro = Account(id = "acc-1", name = "Girokonto", type = AccountType.GIRO)

    private fun showDashboard(width: Dp) {
        compose.setContent {
            FinTrackerTheme {
                Box(Modifier.requiredSize(width, 900.dp)) {
                    DesktopDashboard(
                        accounts = listOf(giro),
                        bookings = emptyList(),
                        categories = emptyList(),
                        onAccountClick = {},
                        onAccountSaved = {},
                        onAccountDeleted = {},
                    )
                }
            }
        }
    }

    @Test
    fun theHeadingStaysOnOneLineInANarrowWindow() {
        showDashboard(560.dp)

        // Letter-wrapped, "Dashboard" was nine lines tall; one line of headlineLarge
        // is roughly 40 dp, so anything past 80 dp means it broke apart again.
        val height = compose.onNodeWithText("Dashboard").getUnclippedBoundsInRoot().height
        assertTrue(height < 80.dp, "heading should stay on one line, was $height tall")
    }

    @Test
    fun theControlsMoveBelowTheHeadingWhenItGetsNarrow() {
        showDashboard(560.dp)

        val title = compose.onNodeWithText("Dashboard").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText("Konto hinzufügen").getUnclippedBoundsInRoot()
        assertTrue(
            button.top >= title.bottom,
            "controls should sit below the heading, heading ended at ${title.bottom} " +
                "and the button starts at ${button.top}",
        )
    }

    @Test
    fun theControlsStayBesideTheHeadingWhenThereIsRoom() {
        showDashboard(1400.dp)

        val title = compose.onNodeWithText("Dashboard").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText("Konto hinzufügen").getUnclippedBoundsInRoot()
        assertTrue(
            button.top < title.bottom,
            "wide layout should keep one row, heading ended at ${title.bottom} " +
                "and the button starts at ${button.top}",
        )
    }

    @Test
    fun everyHeaderControlSurvivesTheNarrowLayout() {
        showDashboard(560.dp)

        compose.onNodeWithText("Suchen…").assertExists()
        compose.onNodeWithText("Konto hinzufügen").assertExists()
        compose.onNodeWithText("Buchung hinzufügen").assertExists()
    }
}
