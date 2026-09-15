package io.github.willywonka644.fintracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Die eine Stelle, an der entschieden wird, wann zwei Texte als gleich gelten.
 *
 * Wird von der Kategoriesuche in der Auswertung, den Beschreibungs-Vorschlägen und den
 * Umbenennungs-Vorschlägen benutzt. Eine Änderung hier ändert alle drei — deshalb steht
 * jede Regel hier als eigener Fall.
 */
class TextFoldTest {

    @Test
    fun `fold lowercases and maps umlauts onto their base letters`() {
        assertEquals("lufter", TextFold.fold("Lüfter"))
        assertEquals("strasse", TextFold.fold("Straße"))
        assertEquals("nusse", TextFold.fold("NÜSSE"))
        assertEquals("marz", TextFold.fold("März"))
        assertEquals("osterreich", TextFold.fold("Österreich"))
    }

    @Test
    fun `folding goes one way only`() {
        // "luf" soll "Lüfter" finden; "luefter" ist eine Gewohnheit von Tastaturen ohne
        // die Taste, und diese hat sie.
        assertNotEquals(TextFold.fold("Lüfter"), TextFold.fold("Luefter"))
    }

    @Test
    fun `words splits on everything that is not a letter or a digit`() {
        assertEquals(listOf("spar", "rate"), TextFold.words("Spar-Rate"))
        assertEquals(listOf("etf", "sparrate"), TextFold.words("ETF  Sparrate"))
        assertEquals(listOf("lebensmittel", "markt"), TextFold.words("Lebensmittel Markt."))
        assertEquals(listOf("marz", "april", "2026"), TextFold.words("März/ April 2026"))
        assertEquals(emptyList<String>(), TextFold.words("   "))
    }

    @Test
    fun `squash makes a hyphen invisible`() {
        assertEquals(TextFold.squash("Sparrate"), TextFold.squash("Spar-Rate"))
        assertEquals(TextFold.squash("Bargeldabhebung Automat"), TextFold.squash("Bargeldabhebung  Automat"))
        assertEquals(TextFold.squash("USB Kabel"), TextFold.squash("Usb Kabel"))
    }

    @Test
    fun `squash keeps two different words apart`() {
        assertNotEquals(TextFold.squash("ETF Sparrate"), TextFold.squash("Sparrate ETF"))
        assertNotEquals(TextFold.squash("Gehalt Juni"), TextFold.squash("Gehalt Juli"))
    }

    @Test
    fun `wordKey makes the order invisible`() {
        assertEquals(TextFold.wordKey("ETF Sparrate"), TextFold.wordKey("Sparrate ETF"))
        assertEquals(TextFold.wordKey("Bistro Essen gehen"), TextFold.wordKey("Essen gehen bistro"))
    }

    @Test
    fun `wordKey keeps a different word set apart`() {
        assertNotEquals(TextFold.wordKey("Sparrate"), TextFold.wordKey("ETF Sparrate"))
        assertNotEquals(TextFold.wordKey("Spar-Rate"), TextFold.wordKey("Sparrate"))
    }
}
