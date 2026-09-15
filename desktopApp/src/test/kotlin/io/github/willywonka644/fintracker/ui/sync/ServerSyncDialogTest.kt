package io.github.willywonka644.fintracker.ui.sync

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.TestServices
import io.github.willywonka644.fintracker.sync.SyncCallError
import io.github.willywonka644.fintracker.sync.describe
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #92 — the desktop's sync dialog against a scripted server.
 *
 * The demand this answers is the one the issue calls the test that counts: the
 * dialog **stops at the preview and writes nothing** until it is confirmed. The
 * rule comes from Phase 5 — nothing writes to a device unannounced — and an
 * always-on server makes it more necessary rather than less, because it removes
 * the accident of timing the Wi-Fi path relied on.
 *
 * The server's answer to `pull` is not hand-written JSON. A second [TestServices]
 * is seeded and exported, so the payload is exactly what a real server would send
 * and the importer runs for real. Hand-written JSON would drift the moment the
 * export format changed, and would do it silently.
 */
@OptIn(ExperimentalTestApi::class)
class ServerSyncDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val services = TestServices()

    private var syncConfirmedCount = 0
    private var dismissed = false
    private var settingsOpened = false

    /** What the far side holds: one account this desktop has never seen. */
    private val serverPayload: String = TestServices()
        .also {
            it.accountRepository.saveAccounts(
                listOf(Account(id = "acc-9", name = "Servertagesgeld", type = AccountType.TAGESGELD))
            )
        }
        .syncExporter
        .export()

    private val pushSummaryJson =
        """{"newCount":2,"updatedCount":1,"unchangedCount":0,"skippedCount":0,"warnings":[]}"""

    private fun localAccountNames(): List<String> =
        services.accountRepository.loadAccounts().filter { !it.deleted }.map { it.name }

    /** True without throwing — the node finders raise when nothing matches. */
    private fun visible(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Answers the three routes the way the real server does. */
    private fun mockClient(pushStatus: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine { request ->
            val json = headersOf(HttpHeaders.ContentType, "application/json")
            when {
                request.url.encodedPath.endsWith("/health") -> respond("OK", HttpStatusCode.OK)

                request.url.encodedPath.endsWith("/sync/push") ->
                    if (pushStatus.isSuccess()) respond(pushSummaryJson, HttpStatusCode.OK, json)
                    else respondError(pushStatus)

                request.url.encodedPath.endsWith("/sync/pull") ->
                    respond(serverPayload, HttpStatusCode.OK, json)

                else -> respondError(HttpStatusCode.NotFound)
            }
        }
    )

    private fun showDialog(
        client: HttpClient = mockClient(),
        configured: Boolean = true,
    ) {
        if (configured) services.serverSyncStore.save("http://pi.local:8080", "geheim")
        compose.setContent {
            FinTrackerTheme {
                ServerSyncDialog(
                    onDismiss = { dismissed = true },
                    onSyncConfirmed = { syncConfirmedCount++ },
                    onOpenSettings = { settingsOpened = true },
                    services = services,
                    httpClientFactory = { client },
                )
            }
        }
    }

    private fun syncUntilPreview() {
        compose.onNodeWithText("Jetzt abgleichen").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { visible("Vorschau (lokal)") }
    }

    @Test
    fun withoutAServerItOffersToSetOneUpRatherThanToSync() {
        showDialog(configured = false)

        compose.onNodeWithText("Noch kein Server eingerichtet.").assertExists()
        compose.onNodeWithText("Jetzt abgleichen").assertDoesNotExist()

        compose.onNodeWithText("Einrichten").performClick()
        assertTrue(settingsOpened, "the button should lead to the settings dialog")
    }

    @Test
    fun theExchangeStopsAtThePreviewAndWritesNothing() {
        showDialog()
        assertEquals(emptyList<String>(), localAccountNames(), "nothing stored before the exchange")

        syncUntilPreview()

        compose.onNodeWithText("Neu").assertExists()
        compose.onNodeWithText("Bestätigen").assertExists()

        // The point of the whole dialog.
        assertEquals(
            emptyList<String>(),
            localAccountNames(),
            "the preview must not have written anything yet",
        )
        assertEquals(0, syncConfirmedCount, "nothing is confirmed until the user confirms")
    }

    @Test
    fun cancellingFromThePreviewWritesNothingAndReturnsToTheStart() {
        showDialog()
        syncUntilPreview()

        compose.onNodeWithText("Abbrechen").performClick()

        // A refusal, not a closed window: the dialog stays open and offers another try.
        compose.onNodeWithText("Jetzt abgleichen").assertExists()
        compose.onNodeWithText("Vorschau (lokal)").assertDoesNotExist()
        assertFalse(dismissed, "cancelling the preview must not close the dialog")
        assertEquals(emptyList<String>(), localAccountNames(), "cancelling leaves the data alone")
        assertEquals(0, syncConfirmedCount)
    }

    @Test
    fun confirmingWritesWhatThePreviewAnnounced() {
        showDialog()
        syncUntilPreview()

        // Hand-cranked clock, and the reason is #103. Between the click and the
        // result the dialog shows a CircularProgressIndicator — an indeterminate
        // one, which is an endless animation. A composition that is always
        // animating never goes idle, and every `waitForIdle` beneath a node finder
        // then blocks with no timeout able to fire. Which is exactly the shape of
        // the hang: the test worker stuck in AWT's `invokeAndWait` under
        // `waitForIdle`, the event thread inside a Skia draw of a scene layer.
        //
        // `autoAdvance = false` stops the clock driving that animation, so the
        // frames are ours to pump. This is the documented way to test a screen
        // that animates forever, so it is a fix as much as an experiment.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Bestätigen").performClick()

        // Virtual frames for the composition, real time for the write, which runs
        // on Dispatchers.IO and does not care about our clock.
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && !visible("Abgleich abgeschlossen.")) {
            compose.mainClock.advanceTimeBy(16)
            Thread.sleep(10)
        }
        compose.mainClock.autoAdvance = true

        assertEquals(
            listOf("Servertagesgeld"),
            localAccountNames(),
            "the account the preview announced should now be stored",
        )
        assertEquals(1, syncConfirmedCount, "the caller is told once, after the write")
    }

    @Test
    fun aRefusedKeyIsNamedRatherThanReportedAsAGeneralFailure() {
        showDialog(client = mockClient(pushStatus = HttpStatusCode.Unauthorized))

        compose.onNodeWithText("Jetzt abgleichen").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { visible(describe(SyncCallError.UNAUTHORIZED)) }

        // Asserted through `describe` rather than a copied German sentence: the
        // wording lives in shared so both platforms say the same thing, and a test
        // that hard-coded it would need editing every time the wording improved.
        compose.onNodeWithText(describe(SyncCallError.UNAUTHORIZED)).assertExists()
        assertEquals(emptyList<String>(), localAccountNames(), "a failed exchange writes nothing")
    }
}
