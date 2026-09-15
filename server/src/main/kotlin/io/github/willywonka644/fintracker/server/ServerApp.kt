package io.github.willywonka644.fintracker.server

import io.github.willywonka644.fintracker.sync.SyncError
import io.github.willywonka644.fintracker.sync.SyncExporter
import io.github.willywonka644.fintracker.sync.SyncImportResult
import io.github.willywonka644.fintracker.sync.SyncImporter
import io.github.willywonka644.fintracker.sync.SyncResult
import io.github.willywonka644.fintracker.sync.SyncWriter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * What a client learns from a push: how the merge turned out on the server.
 *
 * Mirrors the counts the Wi-Fi sync already shows on the phone, so a later sync
 * screen can report "x neu, y aktualisiert" for both routes without a second
 * vocabulary.
 */
@Serializable
data class PushResponse(
    val newCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
    val skippedCount: Int,
    val warnings: List<String>,
)

private val responseJson = Json { prettyPrint = false }

/**
 * The whole server in one place: the guard, then the routes.
 *
 * Exists so there is exactly one wiring of "protection plus endpoints" that
 * production uses. [apiKeyGuard] is not part of [syncRoutes] because the merge
 * tests mount the routes bare on purpose — they are about last-writer-wins, not
 * about authentication, and threading a key through all of them would only add
 * noise. The cost of that split is that this function is the single place where
 * forgetting the guard would be possible, which is why it is three lines long
 * and why `ApiKeyAuthTest` exercises it rather than the routes directly.
 */
fun Application.fintrackerServer(services: ServerServices, apiKey: String) =
    fintrackerServer(services.exporter, services.importer, services.writer, apiKey)

/**
 * The same wiring with the pieces handed in, so the tests can point it at an
 * in-memory database instead of the file [ServerServices] would open. Same
 * reason the routes take their dependencies as parameters.
 */
fun Application.fintrackerServer(
    exporter: SyncExporter,
    importer: SyncImporter,
    writer: SyncWriter,
    apiKey: String,
) {
    apiKeyGuard(apiKey)
    healthRoutes()
    syncRoutes(exporter, importer, writer)
}

/**
 * The sync server that runs on the Raspberry Pi (#88).
 *
 * The server is not a new sync protocol. It is a third participant in the
 * existing one — same [SyncExporter], [SyncImporter] and last-writer-wins merge
 * from `shared`, pointed at its own database file.
 */
fun Application.healthRoutes() {
    routing {
        // Liveness only, which is exactly what the Phase 7 plan asks of it:
        // "Pi läuft und ist erreichbar". It deliberately does not touch the
        // database — a health check that fails for two different reasons cannot
        // tell you which one happened.
        get("/health") {
            call.respondText("OK")
        }
    }
}

/**
 * Pull and push.
 *
 * Both dependencies are handed in rather than fetched from a global, for the
 * same reason the desktop dialogs were changed in #78: otherwise a test that
 * calls these routes talks to the real database.
 */
fun Application.syncRoutes(
    exporter: SyncExporter,
    importer: SyncImporter,
    writer: SyncWriter,
) {
    routing {
        /**
         * The full dataset on every exchange, not "everything since the last
         * sync" as the Phase 7 plan words it. Incremental would mean new logic
         * in the merge path, which is where this project has already lost data
         * once; the dataset is a few hundred kilobytes, and the merge is
         * stateless anyway — last-writer-wins per record needs no cursor.
         */
        get("/sync/pull") {
            call.respondText(exporter.export(), ContentType.Application.Json)
        }

        /**
         * Takes a client's dataset, merges it and writes the result.
         *
         * Two steps on purpose, because [SyncImporter.import] **computes** the
         * merge and stores nothing — it returns a preview, which on the phone is
         * what the user confirms before anything is written. The Pi has nobody
         * to ask, so it writes immediately; but a route that called `import`
         * alone would answer 200 and persist nothing, and the mistake would only
         * surface as data quietly failing to arrive.
         *
         * The write itself is one transaction in [SyncWriter], so a request that
         * dies halfway leaves the database as it was rather than half-merged —
         * which matters over mobile networks, where an aborted push is normal
         * rather than exceptional (#94).
         */
        post("/sync/push") {
            val body = call.receiveText()

            when (val imported = importer.import(body)) {
                is SyncImportResult.Error -> call.respond(
                    HttpStatusCode.BadRequest,
                    describe(imported.error),
                )

                is SyncImportResult.Preview -> {
                    val preview = imported.preview
                    when (val written = writer.write(preview)) {
                        is SyncResult.Success -> call.respondText(
                            responseJson.encodeToString(
                                PushResponse(
                                    newCount = preview.newCount,
                                    updatedCount = preview.updatedCount,
                                    unchangedCount = preview.unchangedCount,
                                    skippedCount = preview.skippedCount,
                                    warnings = preview.warnings,
                                )
                            ),
                            ContentType.Application.Json,
                        )

                        is SyncResult.Error -> call.respond(
                            HttpStatusCode.InternalServerError,
                            describe(written.error),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A malformed payload is the client's fault and a failed write is the server's,
 * so they must not collapse into one status code — over a mobile connection the
 * difference decides whether retrying can possibly help.
 */
private fun describe(error: SyncError): String = when (error) {
    SyncError.InvalidJson -> "INVALID_JSON"
    SyncError.UnknownVersion -> "UNKNOWN_VERSION"
    is SyncError.MissingField -> "MISSING_FIELD:${error.fieldName}"
    SyncError.WriteFailed -> "WRITE_FAILED"
}
