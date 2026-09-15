package io.github.willywonka644.fintracker.booking

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.BookingSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Umbenennungs-Vorschläge (#135).
 *
 * Die Fälle sind keine erfundenen: jeder Name hier stand am 15.09.2026 so in der echten
 * Datenbank. Die Gegenbeispiele auch — "Gehalt Juni"/"Gehalt Juli" und "PASSPORT 1"/
 * "PASSPORT 2" wurden von einer früheren Fassung der Regel tatsächlich vorgeschlagen.
 */
class DescriptionCleanupTest {

    private var seq = 0

    private fun booking(
        description: String,
        category: String? = "Sonstiges",
        installmentGroupId: String? = null,
        deleted: Boolean = false,
        timestamp: Long = 1_700_000_000_000 + (seq * 86_400_000L),
    ) = Booking(
        id = "b${seq++}",
        accountId = "acc-1",
        amount = -10.0,
        description = description,
        timestamp = timestamp,
        category = category,
        installmentGroupId = installmentGroupId,
        deleted = deleted,
        source = BookingSource.MANUAL,
    )

    // ── Stufe 1: Schreibweise ────────────────────────────────────────────────

    @Test
    fun `word order does not make two bookings`() {
        val groups = DescriptionCleanup.suggest(
            listOf(booking("ETF Sparrate"), booking("ETF Sparrate"), booking("Sparrate ETF"))
        )
        val group = groups.single()
        assertEquals(DescriptionCleanup.Reason.SPELLING, group.reason)
        assertEquals("ETF Sparrate", group.suggestedTarget)
        assertEquals(3, group.totalUses)
    }

    @Test
    fun `a hyphen does not make two bookings`() {
        val group = DescriptionCleanup.suggest(
            listOf(booking("Sparrate"), booking("Spar-Rate"))
        ).single()
        assertEquals(DescriptionCleanup.Reason.SPELLING, group.reason)
        assertEquals(setOf("Sparrate", "Spar-Rate"), group.variants.map { it.description }.toSet())
    }

    @Test
    fun `case and a doubled space do not make two bookings`() {
        assertEquals(1, DescriptionCleanup.suggest(listOf(booking("USB Kabel"), booking("Usb Kabel"))).size)
        assertEquals(
            1,
            DescriptionCleanup.suggest(
                listOf(booking("Bargeldabhebung Automat"), booking("Bargeldabhebung  Automat"))
            ).size,
        )
    }

    @Test
    fun `a trailing full stop does not make two bookings`() {
        assertEquals(
            1,
            DescriptionCleanup.suggest(
                listOf(booking("Lebensmittel Markt"), booking("Lebensmittel Markt."))
            ).size,
        )
    }

    // ── Stufe 2: Tippfehler ──────────────────────────────────────────────────

    @Test
    fun `one wrong letter is a typo, not a second booking`() {
        val group = DescriptionCleanup.suggest(
            listOf(
                booking("Bargeldabhebung Automat"), booking("Bargeldabhebung Automat"),
                booking("Bargeldabhenung Automat"),
            )
        ).single()
        assertEquals(DescriptionCleanup.Reason.TYPO, group.reason)
        assertEquals("Bargeldabhebung Automat", group.suggestedTarget)
    }

    @Test
    fun `a doubled final letter is a typo`() {
        val group = DescriptionCleanup.suggest(
            listOf(booking("Musikabo"), booking("Musikabo"), booking("Musikaboo"))
        ).single()
        assertEquals(DescriptionCleanup.Reason.TYPO, group.reason)
        assertEquals("Musikabo", group.suggestedTarget)
    }

    // ── Die Gegenbeispiele, an denen die Regel scharfgestellt wurde ──────────

    @Test
    fun `two months are not a typo for one another`() {
        assertTrue(DescriptionCleanup.suggest(listOf(booking("Gehalt Juni"), booking("Gehalt Juli"))).isEmpty())
        assertTrue(DescriptionCleanup.suggest(listOf(booking("Gehalt Mai"), booking("Gehalt März"))).isEmpty())
    }

    @Test
    fun `two numbers are not a typo for one another`() {
        assertTrue(
            DescriptionCleanup.suggest(
                listOf(booking("Bargeldabhebung PASSPORT 1"), booking("Bargeldabhebung PASSPORT 2"))
            ).isEmpty()
        )
        assertTrue(DescriptionCleanup.suggest(listOf(booking("Espresso 1"), booking("Espresso 2"))).isEmpty())
    }

    @Test
    fun `a wrong letter AND a different number stay two bookings`() {
        // Die Buchstaben allein waeren ein Verschreiber; die 1 gegen die 2 nicht.
        assertTrue(
            DescriptionCleanup.suggest(
                listOf(booking("Bargeldabhebung PASSPORT 2"), booking("Bargeldabhenung PASSPORT 1"))
            ).isEmpty()
        )
    }

    @Test
    fun `an extra word is not a typo`() {
        // "Sparrate" und "ETF Sparrate" meinen vermutlich dasselbe — das weiss aber nur
        // der Nutzer, und die Automatik, die es erraten wollte, hat 185 von 394 Buchungen
        // zu Klumpen verkettet.
        assertTrue(
            DescriptionCleanup.suggest(listOf(booking("Sparrate"), booking("ETF Sparrate"))).isEmpty()
        )
    }

    @Test
    fun `short words are left alone`() {
        // "Bank" und "Bond" liegen zwei Buchstaben auseinander — bei vier Zeichen ist das
        // kein Verschreiber mehr, sondern ein anderes Wort. Deshalb die Untergrenze.
        assertTrue(DescriptionCleanup.suggest(listOf(booking("Bank"), booking("Bond"))).isEmpty())
    }

    // ── Was draußen bleibt ───────────────────────────────────────────────────

    @Test
    fun `instalments are none of this`() {
        val groups = DescriptionCleanup.suggest(
            listOf(
                booking("Rudermaschine 1/12", installmentGroupId = "g1"),
                booking("Rudermaschine 2/12", installmentGroupId = "g1"),
                booking("rudermaschine 1/12", installmentGroupId = "g1"),
            )
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `deleted bookings do not vote`() {
        val groups = DescriptionCleanup.suggest(
            listOf(booking("Musikabo"), booking("Musikaboo", deleted = true))
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `an empty description is not a variant of anything`() {
        assertTrue(DescriptionCleanup.suggest(listOf(booking(""), booking("   "), booking("Miete"))).isEmpty())
    }

    // ── Kategorien ───────────────────────────────────────────────────────────

    @Test
    fun `a group whose categories disagree says so`() {
        val group = DescriptionCleanup.suggest(
            listOf(booking("Musikabo", category = "Hobbys"), booking("Musikaboo", category = "Freizeit"))
        ).single()
        assertTrue(group.categoriesDisagree)
        assertEquals(mapOf("Hobbys" to 1, "Freizeit" to 1), group.categories)
    }

    @Test
    fun `a group whose categories agree does not`() {
        val group = DescriptionCleanup.suggest(
            listOf(booking("Musikabo", category = "Hobbys"), booking("Musikaboo", category = "Hobbys"))
        ).single()
        assertFalse(group.categoriesDisagree)
    }

    // ── Anwenden ─────────────────────────────────────────────────────────────

    @Test
    fun `apply rewrites every variant and stamps lastModifiedAt`() {
        val bookings = listOf(
            booking("Musikabo"), booking("Musikabo"), booking("Musikaboo"), booking("Miete"),
        )
        val group = DescriptionCleanup.suggest(bookings).single()
        val after = DescriptionCleanup.apply(bookings, group, "Musikaboo.example", now = 999L)

        assertEquals(3, after.count { it.description == "Musikaboo.example" })
        assertTrue(after.filter { it.description == "Musikaboo.example" }.all { it.lastModifiedAt == 999L })
        // Unbeteiligte bleiben unberuehrt, auch im Zeitstempel.
        val miete = after.single { it.description == "Miete" }
        assertEquals(0L, miete.lastModifiedAt)
    }

    @Test
    fun `apply leaves the bookings that already carry the target alone`() {
        val bookings = listOf(booking("Musikabo"), booking("Musikabo"), booking("Musikaboo"))
        val group = DescriptionCleanup.suggest(bookings).single()
        val after = DescriptionCleanup.apply(bookings, group, "Musikabo", now = 999L)

        // Nur die eine falsch geschriebene wird angefasst; zwei Zeilen unnoetig als
        // geaendert zu markieren hiesse, sie beim naechsten Abgleich zu senden.
        assertEquals(1, after.count { it.lastModifiedAt == 999L })
        assertEquals(1, DescriptionCleanup.affectedCount(bookings, group, "Musikabo"))
    }

    @Test
    fun `apply keeps the category out of it`() {
        val bookings = listOf(
            booking("Musikabo", category = "Hobbys"),
            booking("Musikaboo", category = "Freizeit"),
        )
        val group = DescriptionCleanup.suggest(bookings).single()
        val after = DescriptionCleanup.apply(bookings, group, "Musikabo", now = 1L)
        assertEquals(setOf("Hobbys", "Freizeit"), after.mapNotNull { it.category }.toSet())
    }

    @Test
    fun `apply does not touch the rest of the stock`() {
        val bookings = listOf(booking("Musikabo"), booking("Musikaboo"), booking("Miete"), booking("Strom"))
        val group = DescriptionCleanup.suggest(bookings).single()
        val after = DescriptionCleanup.apply(bookings, group, "Musikabo", now = 1L)
        assertEquals(bookings.size, after.size)
        assertEquals(bookings.map { it.id }, after.map { it.id })
    }

    // ── Ablehnen ─────────────────────────────────────────────────────────────

    @Test
    fun `a rejected group stays gone`() {
        val bookings = listOf(booking("Musikabo"), booking("Musikaboo"))
        val group = DescriptionCleanup.suggest(bookings).single()
        val rejected = DescriptionCleanup.withRejection(null, group.key)
        assertTrue(
            DescriptionCleanup.suggest(bookings, DescriptionCleanup.parseRejected(rejected)).isEmpty()
        )
    }

    @Test
    fun `the group key does not depend on the order it was found in`() {
        assertEquals(
            DescriptionCleanup.groupKey(listOf("Musikabo", "Musikaboo")),
            DescriptionCleanup.groupKey(listOf("Musikaboo", "Musikabo")),
        )
        assertEquals(
            DescriptionCleanup.groupKey(listOf("USB Kabel")),
            DescriptionCleanup.groupKey(listOf("Usb Kabel")),
        )
    }

    // ── Die Ordnung der Vorschläge ───────────────────────────────────────────

    @Test
    fun `the biggest group comes first`() {
        val bookings = listOf(
            booking("Webhosting"), booking("Web Hosting"),
            booking("Musikabo"), booking("Musikabo"), booking("Musikabo"), booking("Musikaboo"),
        )
        val groups = DescriptionCleanup.suggest(bookings)
        assertEquals(2, groups.size)
        assertEquals("Musikabo", groups.first().suggestedTarget)
        assertEquals(4, groups.first().totalUses)
    }

    @Test
    fun `the most-used spelling is what gets suggested`() {
        val bookings = listOf(
            booking("Sparrate ETF"), booking("Sparrate ETF"), booking("Sparrate ETF"),
            booking("ETF Sparrate"),
        )
        assertEquals("Sparrate ETF", DescriptionCleanup.suggest(bookings).single().suggestedTarget)
    }

    @Test
    fun `nothing to clean means no proposals`() {
        assertTrue(
            DescriptionCleanup.suggest(listOf(booking("Miete"), booking("Strom"), booking("Gehalt"))).isEmpty()
        )
        assertTrue(DescriptionCleanup.suggest(emptyList()).isEmpty())
        assertTrue(DescriptionCleanup.suggest(listOf(booking("Miete"))).isEmpty())
    }

    // ── Levenshtein ──────────────────────────────────────────────────────────

    @Test
    fun `levenshtein counts what it says it counts`() {
        assertTrue(DescriptionCleanup.levenshteinAtMost("musikabo", "musikaboo", 2))
        assertTrue(DescriptionCleanup.levenshteinAtMost("musikabo", "musikabo", 2))
        assertTrue(DescriptionCleanup.levenshteinAtMost("bargeldabhebungautomat", "bargldabhenungautomat", 2))
        assertFalse(DescriptionCleanup.levenshteinAtMost("miete", "strom", 2))
        assertFalse(DescriptionCleanup.levenshteinAtMost("abc", "abcdefgh", 2))
    }

    @Test
    fun `an empty target is refused rather than silently ignored`() {
        val bookings = listOf(booking("Musikabo"), booking("Musikaboo"))
        val group = DescriptionCleanup.suggest(bookings).single()
        val thrown = try {
            DescriptionCleanup.apply(bookings, group, "   ", now = 1L); null
        } catch (e: IllegalArgumentException) {
            e
        }
        assertTrue(thrown != null)
    }
}
