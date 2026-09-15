package io.github.willywonka644.fintracker.sync

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * The extra line Android adds under a "cannot reach the server" message (#109).
 *
 * The shared wording in `SyncMessages` is good — it names causes one can act on
 * — but on this platform it names the two least likely ones first. The Pi runs
 * as a service and comes back after every reboot; the address sits in settings
 * and does not change. What changes constantly is the VPN switch in someone's
 * pocket, because Android allows only one VPN connection at a time and the
 * decision here was to keep tracking protection and connect Tailscale only for a
 * sync.
 *
 * So the message sends you to the Pi while the fault is in your hand. That is
 * exactly the misdirection those texts were written to avoid.
 *
 * ## Why this lives here and not in `SyncMessages`
 *
 * The shared text is shared on purpose: four screens across two platforms must
 * not develop four wordings for one failure. On the desktop Tailscale stays
 * connected, so the same hint would be the wrong first suspect there. The
 * difference is not in the failure, it is in how the platform is operated — so
 * it belongs in the platform's UI, not in the shared vocabulary.
 *
 * ## Why matching on the text is not as fragile as it looks
 *
 * The comparison values come from `describe()` itself, so there is no second
 * copy of a sentence that could drift. Rewording a message in `SyncMessages`
 * keeps this working; only *removing* one of those error cases would need a
 * change here, and that would not compile.
 *
 * `startsWith` rather than equality because `checkSyncConnection` appends the
 * URL to the message it reports.
 */
private val reachabilityMessages: List<String> = listOf(
    describe(SyncCallError.UNREACHABLE),
    describe(SyncCallError.TIMEOUT),
)

/** Whether [message] is one where the VPN is worth checking first. */
internal fun mentionsReachability(message: String): Boolean =
    reachabilityMessages.any { message.startsWith(it) }

/**
 * Renders the hint, or nothing at all. Takes the message rather than a boolean
 * so no caller has to remember to ask the question first.
 */
@Composable
internal fun VpnReminder(message: String) {
    if (!mentionsReachability(message)) return

    Text(
        "Zuerst prüfen: Ist Tailscale auf diesem Gerät verbunden? " +
            "Ohne VPN ist der Server nicht erreichbar.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
