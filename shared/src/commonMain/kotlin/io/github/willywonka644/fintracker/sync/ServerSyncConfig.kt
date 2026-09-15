package io.github.willywonka644.fintracker.sync

/**
 * Address and key together, as a sync needs them (#91, #92).
 *
 * Shared because both platforms store the pair and both must read it in one go:
 * a sync started with the address from before an edit and the key from after it
 * fails in a way that looks like a wrong key. Where the pair is *kept* differs
 * per platform — DataStore on Android, `app_settings` on the desktop, in both
 * cases wherever that platform already keeps its PIN — but what the pair is does
 * not.
 */
data class ServerSyncConfig(val url: String, val apiKey: String) {
    val isComplete: Boolean get() = url.isNotBlank() && apiKey.isNotBlank()
}
