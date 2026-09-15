package io.github.willywonka644.fintracker.server

import io.github.willywonka644.fintracker.db.DatabaseDriverFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Issue #96. The check that keeps a damaged server from handing its damage to
 * both clients on the next exchange.
 *
 * These run against real files rather than an in-memory database, because the
 * thing under test is what SQLite says about *bytes on disk* — an in-memory
 * database cannot be corrupted, which is exactly the case that matters.
 */
class DatabaseIntegrityTest {

    private val workingDirectory: File = Files.createTempDirectory("fintracker-integrity").toFile()

    @AfterTest
    fun cleanUp() {
        workingDirectory.deleteRecursively()
    }

    /**
     * Built through the production path rather than by hand, so a healthy file
     * here is the same shape as a healthy file on the Pi.
     */
    private fun healthyDatabase(name: String = "fintracker.db"): File {
        val file = File(workingDirectory, name)
        DatabaseDriverFactory(file).createDriver().close()
        return file
    }

    @Test
    fun aHealthyDatabaseReportsNothing() {
        assertEquals(emptyList<String>(), integrityProblems(healthyDatabase()))
    }

    /**
     * A first start on a new machine. Creating the file is
     * `DatabaseDriverFactory`'s job; the missing-mount reading of an absent file
     * is answered by `RequiresMountsFor` in the systemd unit (#93), not here.
     */
    @Test
    fun aMissingFileIsNotAProblem() {
        assertEquals(emptyList<String>(), integrityProblems(File(workingDirectory, "never-created.db")))
    }

    /**
     * The case the whole check exists for. Overwrites the pages after the header
     * so the file still opens as a database and the damage sits in the content —
     * which is how failing flash storage presents, rather than as a file that
     * has plainly vanished.
     */
    @Test
    fun aDamagedDatabaseIsReported() {
        val file = healthyDatabase("damaged.db")

        RandomAccessFile(file, "rw").use { raw ->
            val garbage = ByteArray(4096) { 0x5A }
            var offset = 4096L
            while (offset < raw.length()) {
                raw.seek(offset)
                raw.write(garbage, 0, minOf(garbage.size.toLong(), raw.length() - offset).toInt())
                offset += garbage.size
            }
        }

        val problems = integrityProblems(file)

        assertTrue(
            problems.isNotEmpty(),
            "a database whose pages were overwritten must not pass as healthy",
        )
    }

    /**
     * Not a database at all — the shape a truncated copy or an interrupted
     * restore takes. It must land in the same answer as corruption: unusable is
     * unusable, and the caller should not need to tell two kinds apart.
     */
    @Test
    fun aFileThatIsNotADatabaseIsReported() {
        val file = File(workingDirectory, "not-a-database.db")
        file.writeText("Dies ist kein SQLite, sondern eine halbe Sicherung.")

        assertTrue(integrityProblems(file).isNotEmpty())
    }

    /**
     * The message has to name the file. On the Pi the interesting question is
     * *which* database was checked — the one on the USB stick, or one that
     * appeared elsewhere because a mount was missing.
     */
    @Test
    fun theFailureMessageNamesTheFileAndTheConsequence() {
        val file = File(workingDirectory, "fintracker.db")

        val message = integrityFailureMessage(file, listOf("row 42 missing from index"))

        assertTrue(message.contains(file.absolutePath), "the message must name the file: $message")
        assertTrue(message.contains("row 42 missing from index"), "it must carry the finding: $message")
        assertTrue(message.contains("verteilen"), "it must say why starting is refused: $message")
    }

    /**
     * `PRAGMA integrity_check` can answer with thousands of lines. The point of
     * the message is to be read, so it stays bounded and says what it left out.
     */
    @Test
    fun aFloodOfFindingsIsTruncatedRatherThanDumped() {
        val many = (1..500).map { "Fehler $it" }

        val message = integrityFailureMessage(File(workingDirectory, "fintracker.db"), many)

        assertTrue(message.contains("Fehler 1"))
        assertTrue(message.contains("und 480 weitere Meldungen"), "it must say how much was left out: $message")
        assertTrue(!message.contains("Fehler 500"), "it must not dump everything")
    }
}
