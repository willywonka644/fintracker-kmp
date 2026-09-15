package io.github.willywonka644.fintracker.sync

import io.github.willywonka644.fintracker.ISettingsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.willywonka644.fintracker.security.secureRandomBytes
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import org.slf4j.LoggerFactory

@Serializable
data class SyncPreviewResponse(
    val newCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val skippedCount: Int,
    val warnings: List<String>
)

class KtorSyncServer(
    private val syncExporter: SyncExporter,
    private val syncImporter: SyncImporter,
    private val syncWriter: SyncWriter,
    private val settingsRepository: ISettingsRepository
) {
    private val log = LoggerFactory.getLogger(KtorSyncServer::class.java)

    val port: Int = 54321

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _pendingPreview = MutableStateFlow<SyncPreview?>(null)
    val pendingPreview: StateFlow<SyncPreview?> = _pendingPreview.asStateFlow()

    private val _desktopConfirmed = MutableStateFlow(false)

    private val _syncRequestPending = MutableStateFlow(false)

    private val _syncCompletedCount = MutableStateFlow(0)
    val syncCompletedCount: StateFlow<Int> = _syncCompletedCount.asStateFlow()

    private val _pairingToken = MutableStateFlow<String?>(null)
    val pairingToken: StateFlow<String?> = _pairingToken.asStateFlow()

    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var jmdns: JmDNS? = null

    private val serializationJson = Json { ignoreUnknownKeys = true }

    /** Always the CURRENT pairing code — routes must read this per request, never
     *  capture a token in a closure: "Regenerate code" swaps it while the server runs. */
    private fun currentToken(): String =
        _pairingToken.value ?: settingsRepository.get("wifi_pairing_token") ?: ""

    fun start() {
        if (_isRunning.value) return

        val token = settingsRepository.get("wifi_pairing_token") ?: run {
            val newToken = generateToken()
            settingsRepository.set("wifi_pairing_token", newToken)
            newToken
        }
        _pairingToken.value = token

        engine = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(serializationJson)
            }
            routing {
                // Lightweight pairing check: the response is encrypted with the server's
                // token, so only a phone holding the SAME code can decrypt "PONG".
                // Lets the phone validate the entered code immediately on "Verbinden".
                get("/sync/ping") {
                    call.respondText(encryptSyncPayload("PONG", currentToken()))
                }

                get("/sync/export") {
                    val exportJson = syncExporter.export()
                    call.respondText(encryptSyncPayload(exportJson, currentToken()))
                }

                post("/sync/import") {
                    log.info("[/sync/import] called — _pendingPreview=${_pendingPreview.value != null}, _desktopConfirmed=${_desktopConfirmed.value}")
                    val body = call.receiveText()
                    val decryptedJson = try {
                        decryptSyncPayload(body, currentToken())
                    } catch (e: Exception) {
                        log.warn("[/sync/import] decryption failed (wrong token?): ${e.message}")
                        call.respond(HttpStatusCode.Forbidden, "WRONG_TOKEN")
                        return@post
                    }
                    when (val result = syncImporter.import(decryptedJson)) {
                        is SyncImportResult.Error -> {
                            log.error("[/sync/import] import returned Error: ${result.error}")
                            call.respond(HttpStatusCode.BadRequest, result.error.toString())
                        }
                        is SyncImportResult.Preview -> {
                            _pendingPreview.value = result.preview
                            log.info("[/sync/import] preview stored — new=${result.preview.newCount}, updated=${result.preview.updatedCount}, unchanged=${result.preview.unchangedCount}, skipped=${result.preview.skippedCount}")
                            val previewResponse = SyncPreviewResponse(
                                newCount = result.preview.newCount,
                                updatedCount = result.preview.updatedCount,
                                unchangedCount = result.preview.unchangedCount,
                                skippedCount = result.preview.skippedCount,
                                warnings = result.preview.warnings
                            )
                            val previewJson = serializationJson.encodeToString(previewResponse)
                            log.info("[/sync/import] responding 200 with preview JSON")
                            call.respondText(encryptSyncPayload(previewJson, currentToken()))
                        }
                    }
                }

                get("/sync/request") {
                    val pending = _syncRequestPending.value
                    if (pending) _syncRequestPending.value = false
                    call.respondText(if (pending) "YES" else "NO")
                }

                post("/sync/confirm") {
                    val preview = _pendingPreview.value
                    log.info("[/sync/confirm] called — _pendingPreview=${preview != null}, _desktopConfirmed=${_desktopConfirmed.value}")
                    if (preview == null) {
                        if (_desktopConfirmed.value) {
                            log.info("[/sync/confirm] desktop already confirmed, clearing flag → responding 200 OK")
                            _desktopConfirmed.value = false
                        }
                        _syncCompletedCount.value++
                        call.respondText("OK", status = HttpStatusCode.OK)
                        return@post
                    }
                    log.info("[/sync/confirm] preview present but desktop not yet confirmed — calling syncWriter.write()")
                    when (val writeResult = syncWriter.write(preview)) {
                        is SyncResult.Success -> {
                            _pendingPreview.value = null
                            _syncCompletedCount.value++
                            log.info("[/sync/confirm] syncWriter.write() succeeded → responding 200 OK")
                            call.respondText("OK", status = HttpStatusCode.OK)
                        }
                        is SyncResult.Error -> {
                            log.error("[/sync/confirm] syncWriter.write() failed: $writeResult → responding 500")
                            call.respondText("Write failed", status = HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }
        }.start(wait = false)

        jmdns = JmDNS.create().also { dns ->
            val serviceInfo = ServiceInfo.create(
                "_fintracker._tcp.local.",
                "FinTracker Desktop",
                port,
                "version=1"
            )
            dns.registerService(serviceInfo)
        }

        _isRunning.value = true
    }

    fun requestSync() {
        log.info("[requestSync] sync request queued for Android")
        _syncRequestPending.value = true
    }

    fun regenerateToken() {
        val newToken = generateToken()
        settingsRepository.set("wifi_pairing_token", newToken)
        _pairingToken.value = newToken
    }

    fun confirmImportedOnDesktop() {
        log.info("[confirmImportedOnDesktop] called — _pendingPreview=${_pendingPreview.value != null}, _desktopConfirmed=${_desktopConfirmed.value}")
        _pendingPreview.value = null
        _desktopConfirmed.value = true
        log.info("[confirmImportedOnDesktop] _pendingPreview cleared, _desktopConfirmed set to true")
    }

    fun discardPendingPreview() {
        log.info("[discardPendingPreview] called — clearing _pendingPreview and _desktopConfirmed")
        _pendingPreview.value = null
        _desktopConfirmed.value = false
    }

    fun stop() {
        jmdns?.apply {
            unregisterAllServices()
            close()
        }
        jmdns = null

        engine?.stop(gracePeriodMillis = 0, timeoutMillis = 3_000)
        engine = null

        _pendingPreview.value = null
        _desktopConfirmed.value = false
        _syncRequestPending.value = false
        _syncCompletedCount.value = 0
        _isRunning.value = false
    }

    private fun generateToken(): String {
        // Always exactly 8 chars: base64-stripping used to yield 7-char codes whenever
        // a '+' or '/' landed in the encoding — and the phone's Verbinden button
        // requires length == 8, locking the user out entirely.
        // 32-char alphabet (no I/O/0/1 to avoid confusion); 256 % 32 == 0 → unbiased.
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return secureRandomBytes(8)
            .map { alphabet[(it.toInt() and 0xFF) % alphabet.length] }
            .joinToString("")
    }
}
