package io.github.willywonka644.fintracker.ocr

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Issue #84. Built from 11 real receipts scanned with coordinates; every shop,
 * street and number below is invented, because the originals carry addresses,
 * tax numbers and the names of shop staff.
 *
 * What is reproduced faithfully is the geometry — which line sits above which,
 * how tall it is, how confident ML Kit was — because that is the whole substance
 * of the rule under test. In particular the fixtures keep the property that
 * broke the old rule: the order lines arrive in is not the order they appear on
 * the paper.
 *
 * Measured on the real sample: the rule this replaced found the shop on 2 of 11
 * receipts, this one on 10 of 11.
 */
class ReceiptMerchantTest {

    private fun line(
        text: String,
        top: Int,
        left: Int = 100,
        right: Int = 600,
        height: Int = 30,
        blockIndex: Int = 0,
        confidence: Float = 0.85f,
        angle: Float = 0f,
    ) = OcrLine(
        text = text,
        left = left,
        top = top,
        right = right,
        bottom = top + height,
        blockIndex = blockIndex,
        confidence = confidence,
        angle = angle,
    )

    private fun pageOf(vararg lines: OcrLine, rawText: String? = null) = OcrPage(
        rawText = rawText ?: lines.joinToString("\n") { it.text },
        lines = lines.toList(),
        imageWidth = 1440,
        imageHeight = 1920,
    )

    @Test
    fun theShopIsTheTopmostLineOnThePaper() {
        val page = pageOf(
            line("MUSTERBAECKER", top = 100),
            line("Musterweg 9", top = 140),
            line("10115 Musterstadt", top = 180),
            line("Vollkornbrot 500g", top = 400),
        )
        assertEquals("MUSTERBAECKER", ReceiptParser.parse(page).merchantName)
    }

    /**
     * The point of the whole issue. ML Kit returned article names and headings
     * before the shop, and the old rule read the first lines of that sequence.
     * Here the same lines arrive in a deliberately unhelpful order.
     */
    @Test
    fun theOrderTheLinesArriveInDoesNotMatter() {
        val page = pageOf(
            line("Vileda Glitzi", top = 700, blockIndex = 4),
            line("BANANE BIO", top = 900, blockIndex = 5),
            line("MUSTERMARKT", top = 120, blockIndex = 9),
            line("Musterweg 9", top = 160, blockIndex = 9),
        )
        assertEquals("MUSTERMARKT", ReceiptParser.parse(page).merchantName)
    }

    @Test
    fun theCardSlipHeadingIsSteppedOver() {
        val page = pageOf(
            line("K-U-N-D-E-N-B-E-L-E-G", top = 100),
            line("Musterfriseur", top = 150),
            line("Musterallee 2", top = 200),
        )
        assertEquals("Musterfriseur", ReceiptParser.parse(page).merchantName)
    }

    /**
     * OCR read the D of KUNDENBELEG as a zero on one of the sampled slips, which
     * is why the heading is matched as a substring of the letters-and-digits
     * form rather than as a word.
     *
     * Written without the letter spacing on purpose: the spaced variant is
     * already rejected for having more punctuation than letters, so it would
     * pass this test without exercising the heading check at all.
     */
    @Test
    fun aMisreadHeadingIsAlsoSteppedOver() {
        val page = pageOf(
            line("KUN0ENBELEG", top = 100),
            line("Stadtbuecherei", top = 150),
        )
        assertEquals("Stadtbuecherei", ReceiptParser.parse(page).merchantName)
    }

    /**
     * Above the header of a photographed receipt there is usually something:
     * show-through from the back of the slip, or a scrap of the next receipt.
     * On the sample these were "kt", "lvo", "Ein" and "Die" — short, and that is
     * what makes them rejectable.
     */
    @Test
    fun shortFragmentsAboveTheHeaderAreIgnored() {
        val page = pageOf(
            line("kt", top = 40, confidence = 0.41f),
            line("Die", top = 60, confidence = 0.61f),
            line("Ein", top = 80, confidence = 0.37f),
            line("HEM Musterstelle", top = 120),
            line("Musterstr. 152", top = 160),
        )
        assertEquals("HEM Musterstelle", ReceiptParser.parse(page).merchantName)
    }

    @Test
    fun aBarelyRecognisedLineIsIgnored() {
        val page = pageOf(
            line("Muellerei", top = 60, confidence = 0.20f),
            line("MUSTERMARKT", top = 120),
        )
        assertEquals("MUSTERMARKT", ReceiptParser.parse(page).merchantName)
    }

    /**
     * One sampled receipt prints the shop and its street on a single line. The
     * whole line is returned: it names the shop, and trimming the street off
     * risks cutting into the name instead.
     */
    @Test
    fun aShopSharingItsLineWithTheStreetIsReturnedWhole() {
        val page = pageOf(
            line("Musterkauf - Musterstrasse 34", top = 100),
            line("10115 Musterstadt", top = 140),
        )
        assertEquals("Musterkauf - Musterstrasse 34", ReceiptParser.parse(page).merchantName)
    }

    @Test
    fun aDateIsNeverTheMerchant() {
        val page = pageOf(
            line("11. 04.2026 08:25 Uhr 0011", top = 60),
            line("MUSTERMARKT", top = 120),
        )
        assertEquals("MUSTERMARKT", ReceiptParser.parse(page).merchantName)
    }

    @Test
    fun anAmountIsNeverTheMerchant() {
        val page = pageOf(
            line("Summe EUR 12,40", top = 60),
            line("MUSTERMARKT", top = 120),
        )
        assertEquals("MUSTERMARKT", ReceiptParser.parse(page).merchantName)
    }

    @Test
    fun aLineOfMostlyDigitsIsIgnored() {
        val page = pageOf(
            line("A100420038624 ForuA 0", top = 60),
            line("MUSTERMARKT", top = 120),
        )
        assertEquals("MUSTERMARKT", ReceiptParser.parse(page).merchantName)
    }

    @Test
    fun aPageWithoutPositionedLinesYieldsNoMerchant() {
        val page = OcrPage(rawText = "MUSTERMARKT", lines = emptyList(), imageWidth = 0, imageHeight = 0)
        assertNull(ReceiptParser.parse(page).merchantName)
    }

    /**
     * The known limit, pinned rather than papered over: where OCR shattered the
     * header, the shop name exists only as fragments and the rule returns one of
     * them. It happened on 1 of 11 receipts. The result is visible in the
     * description field, so the user corrects it in passing.
     */
    @Test
    fun aShatteredHeaderReturnsAFragmentAndThatIsAccepted() {
        val page = pageOf(
            line("aarkt", top = 60, confidence = 0.49f),
            line("An-drog10", top = 80, confidence = 0.46f),
            line("Musterweg", top = 100, confidence = 0.51f),
        )
        assertEquals("aarkt", ReceiptParser.parse(page).merchantName)
    }

    /**
     * The text-only overload deliberately reports no merchant. It used to guess
     * from the first lines and was right on 2 of 11 receipts, and since the
     * merchant prefills the description, a wrong guess is worse than an empty
     * field.
     */
    @Test
    fun theTextOnlyOverloadReportsNoMerchant() {
        val result = ReceiptParser.parse("MUSTERMARKT\nMusterweg 9\nSUMME\n9,60")
        assertNull(result.merchantName)
        assertFalse(result.merchantConfident)
    }

    /** The merchant is never claimed as confident: the rule has no evidence to offer. */
    @Test
    fun theMerchantIsNeverReportedAsConfident() {
        val page = pageOf(line("MUSTERMARKT", top = 100))
        val result = ReceiptParser.parse(page)
        assertEquals("MUSTERMARKT", result.merchantName)
        assertFalse(result.merchantConfident)
    }

    /** Amount and date still come from the text path, unchanged by #84. */
    @Test
    fun amountAndDateStillComeFromTheRecognisedText() {
        val page = pageOf(
            line("MUSTERMARKT", top = 100),
            line("Gesamt", top = 400),
            line("9, 60", top = 400, left = 500, right = 620),
            rawText = "MUSTERMARKT\nDatum 11.04.2026\nGesamt\n9, 60\n9,60",
        )
        val result = ReceiptParser.parse(page)
        assertEquals(9.6, result.amount)
        assertEquals(LocalDate(2026, 4, 11), result.date)
        assertEquals("MUSTERMARKT", result.merchantName)
    }
}
