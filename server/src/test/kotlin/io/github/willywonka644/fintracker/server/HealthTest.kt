package io.github.willywonka644.fintracker.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue #88, first step. These tests answer one question: does this module build
 * and run at all, with `shared` resolved and Ktor wired up?
 *
 * `testApplication` calls the routes in-process, without opening a socket, so
 * this runs headless and belongs in CI from the start — unlike Phase 6.5, where
 * the test net had to be retrofitted onto finished code.
 */
class HealthTest {

    @Test
    fun healthAnswersOk() = testApplication {
        application { healthRoutes() }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    /**
     * Guards against the shape of mistake where a catch-all route answers
     * everything, which would make every later endpoint look like it works.
     */
    @Test
    fun anUnknownPathIsNotFound() = testApplication {
        application { healthRoutes() }

        assertEquals(HttpStatusCode.NotFound, client.get("/sync/pull").status)
    }
}
