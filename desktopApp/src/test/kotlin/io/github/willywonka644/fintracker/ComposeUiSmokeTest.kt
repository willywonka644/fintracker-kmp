package io.github.willywonka644.fintracker

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import org.junit.Rule
import org.junit.Test

/**
 * Phase 6.5 Tier 2 — infrastructure smoke test.
 *
 * Proves three things before any real UI test is written:
 * the Compose test runtime starts, the desktop module's composables are reachable
 * from `src/test`, and node finders work against rendered text.
 *
 * Runs only where a display is available (locally fine; CI needs xvfb).
 */
@OptIn(ExperimentalTestApi::class)
class ComposeUiSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun themeRendersAndTextIsFindable() {
        compose.setContent {
            FinTrackerTheme {
                Text("Tier-2-Smoke-Test")
            }
        }

        compose.onNodeWithText("Tier-2-Smoke-Test").assertIsDisplayed()
    }
}
