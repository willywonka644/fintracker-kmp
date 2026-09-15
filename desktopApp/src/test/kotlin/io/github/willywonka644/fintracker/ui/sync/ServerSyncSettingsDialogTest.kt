package io.github.willywonka644.fintracker.ui.sync

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.willywonka644.fintracker.TestServices
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * #92 — address and key for the sync server on the desktop.
 *
 * The trap this pins down was a real one on Android first (see the field notes on
 * #91): the connection test works on the *fields*, a sync works on the *store*,
 * so a green test that was never saved makes the next sync use an address the
 * user believes they changed. The dialog says so; these tests make sure it keeps
 * saying so.
 */
@OptIn(ExperimentalTestApi::class)
class ServerSyncSettingsDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val services = TestServices()
    private var dismissed = false

    private val unsavedHint =
        "Noch nicht gespeichert — der Abgleich benutzt weiterhin die gespeicherten Werte."

    /** Never reached by these tests; the connection test is not what they cover. */
    private val idleClient = HttpClient(MockEngine { respond("OK", HttpStatusCode.OK) })

    private fun visible(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun showDialog(storedUrl: String? = null, storedKey: String = "") {
        if (storedUrl != null) services.serverSyncStore.save(storedUrl, storedKey)
        compose.setContent {
            FinTrackerTheme {
                ServerSyncSettingsDialog(
                    onDismiss = { dismissed = true },
                    services = services,
                    httpClientFactory = { idleClient },
                )
            }
        }
        // The fields are filled from the store in a LaunchedEffect, not on the
        // first frame. Waiting for the stored address to appear is the only
        // honest signal that the effect has run — the unsaved hint is absent both
        // before and after, so waiting on it would prove nothing.
        if (!storedUrl.isNullOrBlank()) {
            compose.waitUntil(timeoutMillis = 5_000) { visible(storedUrl) }
        } else {
            compose.waitForIdle()
        }
    }

    @Test
    fun theFieldsShowWhatIsStored() {
        showDialog(storedUrl = "http://pi.local:8080", storedKey = "geheim")

        compose.onNodeWithTag(ServerSyncSettingsTestTags.URL).assertTextContains("http://pi.local:8080")
        compose.onNodeWithTag(ServerSyncSettingsTestTags.API_KEY).assertTextContains("geheim")
        compose.onNodeWithText(unsavedHint).assertDoesNotExist()
    }

    @Test
    fun changingAFieldWarnsThatTheChangeIsNotStoredYet() {
        showDialog(storedUrl = "http://pi.local:8080", storedKey = "geheim")

        compose.onNodeWithTag(ServerSyncSettingsTestTags.URL).performTextInput("x")

        compose.onNodeWithText(unsavedHint).assertExists()
        assertEquals(
            "http://pi.local:8080",
            services.serverSyncStore.current().url,
            "typing must not touch the store",
        )
    }

    @Test
    fun savingStoresTheValuesAndClearsTheWarning() {
        showDialog(storedUrl = "http://pi.local:8080", storedKey = "geheim")

        compose.onNodeWithTag(ServerSyncSettingsTestTags.API_KEY).performTextClearance()
        compose.onNodeWithTag(ServerSyncSettingsTestTags.API_KEY).performTextInput("neuerschluessel")
        compose.onNodeWithText(unsavedHint).assertExists()

        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Gespeichert.").assertExists()
        compose.onNodeWithText(unsavedHint).assertDoesNotExist()
        assertEquals("neuerschluessel", services.serverSyncStore.current().apiKey)
    }

    @Test
    fun savingRepairsTheAddressAndShowsWhatItStored() {
        showDialog(storedUrl = "", storedKey = "")

        compose.onNodeWithTag(ServerSyncSettingsTestTags.URL).performTextInput("192.168.1.50")
        compose.onNodeWithTag(ServerSyncSettingsTestTags.API_KEY).performTextInput("geheim")
        compose.onNodeWithText("Speichern").performClick()

        // Scheme and port are added, and the field is updated to match — the
        // repair has to be visible, not silent.
        val stored = services.serverSyncStore.current().url
        assertEquals("http://192.168.1.50:8080", stored)
        compose.onNodeWithTag(ServerSyncSettingsTestTags.URL).assertTextContains(stored)
        compose.onNodeWithText(unsavedHint).assertDoesNotExist()
    }

    @Test
    fun anAddressItCannotRepairIsRejectedAndNothingIsStored() {
        showDialog(storedUrl = "", storedKey = "")

        compose.onNodeWithTag(ServerSyncSettingsTestTags.URL).performTextInput("ftp://pi.local")
        compose.onNodeWithText("Speichern").performClick()

        compose.onNodeWithText("Gespeichert.").assertDoesNotExist()
        assertEquals("", services.serverSyncStore.current().url, "a rejected address is not stored")
    }
}
