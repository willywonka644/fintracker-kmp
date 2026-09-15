package io.github.willywonka644.fintracker.server

import java.io.File
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Checks the server's database before anything is served from it (#96).
 *
 * ## Why this exists at all
 *
 * A *lost* server is not the dangerous case. Android and desktop each hold a
 * complete copy, deletions travel as tombstones rather than as gaps, and a fresh
 * Pi refills from the first push. That is a recovery job, not an emergency.
 *
 * A *corrupted* server is the dangerous case. A Pi that hands out half-read rows
 * or scrambled timestamps distributes them to **both** clients on the next
 * exchange, and where those timestamps look newer the damage wins against the
 * good data — last-writer-wins does not know the difference. One broken device
 * becomes a broken dataset on three. The database lives on removable storage,
 * and flash storage does not fail cleanly; it starts returning wrong bytes
 * quietly.
 *
 * That is not hypothetical here: this project has already lost data once, when a
 * delete cascade took booked payments with it, and what saved it was a backup —
 * not the second copy on the other device.
 *
 * ## Why before SQLDelight opens the file
 *
 * The check runs on its own JDBC connection, before [ServerServices] hands the
 * file to SQLDelight. Otherwise a damaged file would first go through the
 * create/migrate logic in `DatabaseDriverFactory` — writing to a database whose
 * pages are already suspect is the last thing that should happen to it.
 *
 * ## A missing file is not a problem
 *
 * Returning "no problems" for a file that does not exist is deliberate: creating
 * it is `DatabaseDriverFactory`'s job, and a first start on a new machine is
 * normal. What guards against the *other* reading of a missing file — the USB
 * stick failing to mount, so the server quietly opens an empty database and both
 * clients merge against it — is `RequiresMountsFor=/srv/fintracker` in the
 * systemd unit (#93). That is a different failure and it is answered where it
 * happens.
 */
fun integrityProblems(databaseFile: File): List<String> {
    if (!databaseFile.exists()) return emptyList()

    return try {
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA integrity_check").use { rows ->
                    val lines = buildList {
                        while (rows.next()) rows.getString(1)?.let { add(it) }
                    }
                    // SQLite answers a healthy database with exactly one row
                    // reading "ok". Anything else — several rows, a different
                    // word, or nothing at all — is a finding.
                    if (lines == listOf("ok")) emptyList() else lines.ifEmpty {
                        listOf("PRAGMA integrity_check lieferte keine Antwort.")
                    }
                }
            }
        }
    } catch (e: SQLException) {
        // A file so damaged that it cannot be opened is *more* broken, not less.
        // Folding this into the same return value keeps the caller from having
        // to decide that two kinds of unusable mean two kinds of action.
        listOf("Die Datenbank lässt sich nicht öffnen: ${e.message}")
    }
}

/**
 * The message the server exits with. Kept next to the check so the wording and
 * the condition cannot drift apart.
 *
 * Names the file, because on the Pi the interesting question is *which*
 * database was checked — the one on the USB stick, or one that appeared
 * somewhere else because a mount was missing.
 */
fun integrityFailureMessage(databaseFile: File, problems: List<String>): String =
    buildString {
        append("Die Server-Datenbank ${databaseFile.absolutePath} ist beschädigt. ")
        append("Der Server startet nicht, weil er den Schaden sonst beim nächsten ")
        append("Abgleich an alle Geräte verteilen würde.\n")
        problems.take(20).forEach { append("  $it\n") }
        if (problems.size > 20) append("  … und ${problems.size - 20} weitere Meldungen\n")
        append("Eine Sicherung zurückspielen und den Dienst neu starten.")
    }
