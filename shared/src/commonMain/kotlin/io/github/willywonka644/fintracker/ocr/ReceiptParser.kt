package io.github.willywonka644.fintracker.ocr

import kotlinx.datetime.LocalDate

data class OcrResult(
    val amount: Double?,
    val amountConfident: Boolean,
    val date: LocalDate?,
    val dateConfident: Boolean,
    val merchantName: String?,
    val merchantConfident: Boolean,
    val rawText: String
)

object ReceiptParser {

    /**
     * A money value, and nothing that merely looks like one.
     *
     * The guards on both sides are the point. Without them the pattern also
     * matched inside dates (`13.03.025` yielded 13,03), inside mangled
     * timestamps (`…:90.000` yielded 90,00) and inside article numbers — and
     * since the fallback below takes the largest value, such a phantom
     * outbid the real total on four of twelve receipts.
     *
     * Only the comma is accepted as the decimal mark. On German receipts a dot
     * is a thousands separator or part of a date; treating it as a decimal
     * point is what let `08.2026` turn into 8,20.
     *
     * The optional space after the comma is not sloppiness: ML Kit really does
     * return "7, 45".
     */
    private val AMOUNT_PATTERN = Regex(
        """(?<![\d.,])(\d{1,3}(?:\.\d{3})+|\d{1,6}),\s?(\d{2})(?![\d.,])"""
    )

    /**
     * Words that introduce the total.
     *
     * "Brutto" and "Netto" are deliberately absent. They label the VAT
     * breakdown, and because ML Kit reorders the text they often end up beside
     * the wrong number: both fuel receipts in the sample confidently reported
     * the pre-tax amount, roughly 16 % below what was actually paid.
     */
    private val TOTAL_KEYWORDS = listOf(
        "summe", "gesamt", "zu zahlen", "betrag", "endbetrag",
        "gesamtbetrag", "rechnungsbetrag", "total"
    )

    /** Marks the VAT block, whose figures are never the total. */
    private val TAX_BLOCK_WORDS = Regex("""(netto|brutto|mwst)""", RegexOption.IGNORE_CASE)

    /**
     * A day/month/year, tolerant of what OCR does to the separators.
     *
     * The whitespace is not cosmetic: ML Kit returns "12. 08. 2026", and without
     * it three receipts in the sample yielded no date at all.
     *
     * The year is exactly two or four digits. Three is the tell-tale of a
     * misread — "13.03.025" was accepted as 2025 and outranked the correct date
     * printed further down the same receipt.
     */
    private val DATE_REGEX = Regex(
        """(\d{1,2}\s*[./]\s*\d{1,2}\s*[./]\s*(?:\d{4}|\d{2})(?!\d))"""
    )

    /**
     * Stems, not words. OCR mangles the label itself: the sampled receipts
     * printed "Datum" as "Datun" and "Datua", and matching the full word missed
     * the only correct date on the slip.
     */
    private val DATE_KEYWORDS = listOf("datu", "date", "quittung")

    /**
     * Years a receipt may plausibly carry. Defaults to a decade around 2026 and
     * is a parameter so the tests stay deterministic and do not turn red on
     * their own in 2031, which the previously hard-coded 2020..2030 would have.
     */
    private val DEFAULT_PLAUSIBLE_YEARS = 2020..2035

    /**
     * Reads a receipt from the recognised text alone.
     *
     * Reports **no merchant**: a shop name is identifiable only by its position
     * on the paper (#84), and this overload has no positions. It previously
     * guessed from the first few lines of the flat text and was measured at 2 of
     * 11 receipts — and because the merchant prefills the description field, a
     * wrong guess writes an article name or a receipt number into the booking,
     * while nothing at all leaves the field empty for the user to fill. Use
     * [parse] with an [OcrPage] to get a merchant.
     */
    fun parse(rawText: String, plausibleYears: IntRange = DEFAULT_PLAUSIBLE_YEARS): OcrResult {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val (amount, amountConfident) = extractAmount(lines)
        val (date, dateConfident) = extractDate(lines, plausibleYears)

        return OcrResult(
            amount = amount,
            amountConfident = amountConfident,
            date = date,
            dateConfident = dateConfident,
            merchantName = null,
            merchantConfident = false,
            rawText = rawText
        )
    }

    /**
     * Reads a receipt from the recognised text together with the position of
     * every line, which is what makes the merchant findable.
     *
     * The amount and date deliberately go through the text path unchanged, so
     * that everything measured for #82 keeps holding exactly.
     */
    fun parse(page: OcrPage, plausibleYears: IntRange = DEFAULT_PLAUSIBLE_YEARS): OcrResult =
        parse(page.rawText, plausibleYears).copy(merchantName = extractMerchant(page.lines))

    /** A money value together with the line it was found on. */
    private data class AmountHit(val value: Double, val lineIndex: Int)

    /**
     * Picks the total out of the recognised text.
     *
     * Two paths, because receipts come in two shapes. A card slip prints
     * "Betrag 22,00 EUR" — label and value on one line, which is the strongest
     * evidence there is. A till receipt prints them in columns, and ML Kit
     * returns the labels in one run and the numbers in another, so "SUMME" and
     * its amount can end up thirty lines apart. On the sampled till receipts the
     * keyword and its value shared a line **not once**.
     *
     * For those, the total is simply the largest amount on the receipt: it is
     * the sum of the items, so nothing legitimate can exceed it. That only holds
     * once the phantoms are excluded (see [AMOUNT_PATTERN]) and once the VAT
     * block is left out, which can hold a gross figure above the total.
     *
     * Known blind spot: a total that appears exactly once and happens to land
     * next to a VAT label is discarded with the block. None of the twelve
     * sampled receipts did that, but nothing rules it out.
     */
    private fun extractAmount(lines: List<String>): Pair<Double?, Boolean> {
        for (line in lines) {
            val lower = line.lowercase()
            if (TOTAL_KEYWORDS.any { lower.contains(it) }) {
                val hit = amountsIn(line, lineIndex = 0).firstOrNull()
                if (hit != null) return hit.value to true
            }
        }

        val hits = lines.flatMapIndexed { index, line -> amountsIn(line, index) }
        if (hits.isEmpty()) return null to false

        val outsideTaxBlock = hits.filterNot { inTaxBlock(lines, it.lineIndex) }
        val considered = outsideTaxBlock.ifEmpty { hits }
        // Never claimed as confident: this is the largest plausible number, not
        // a value the receipt labelled as its total.
        return considered.maxOf { it.value } to false
    }

    private fun amountsIn(line: String, lineIndex: Int): List<AmountHit> =
        AMOUNT_PATTERN.findAll(line).mapNotNull { match ->
            // "19,00 %" is a VAT rate. Reading it as money made a 7,33 € basket
            // come out as 19,00 €.
            val after = line.substring(match.range.last + 1).trimStart()
            if (after.startsWith("%")) return@mapNotNull null

            val whole = match.groupValues[1].replace(".", "")
            val value = "$whole.${match.groupValues[2]}".toDoubleOrNull()
            if (value == null || value <= 0.0) null else AmountHit(value, lineIndex)
        }.toList()

    /**
     * ML Kit scatters the VAT block over neighbouring lines, so a figure counts
     * as part of it when its own line or either neighbour names one of the
     * words. Judged per occurrence: the real total usually appears several
     * times, and the copies elsewhere stay eligible.
     */
    private fun inTaxBlock(lines: List<String>, lineIndex: Int): Boolean =
        (lineIndex - 1..lineIndex + 1).any { i ->
            lines.getOrNull(i)?.let { TAX_BLOCK_WORDS.containsMatchIn(it) } == true
        }

    /**
     * A line labelled "Datum" is worth more than the first date-shaped string in
     * the text, and on one sampled receipt it was the only correct date on the
     * slip — the header and the fiscal block both carried a misread month.
     *
     * Deliberately not implemented: preferring the ISO timestamp that German
     * tills print in their fiscal block. It looks like the most trustworthy
     * source, being machine-printed, but it is on the same piece of paper and
     * picks up the same misreads. On that receipt it would have moved a correct
     * August date back to March.
     */
    private fun extractDate(lines: List<String>, plausibleYears: IntRange): Pair<LocalDate?, Boolean> {
        for (line in lines) {
            val lower = line.lowercase()
            if (DATE_KEYWORDS.any { lower.contains(it) }) {
                val date = parseDateFromLine(line, plausibleYears)
                if (date != null) return date to true
            }
        }

        for (line in lines) {
            val date = parseDateFromLine(line, plausibleYears)
            if (date != null) return date to false
        }

        return null to false
    }

    private fun parseDateFromLine(line: String, plausibleYears: IntRange): LocalDate? =
        DATE_REGEX.findAll(line)
            .mapNotNull { tryParseReceiptDate(it.value, plausibleYears) }
            .firstOrNull()

    private fun tryParseReceiptDate(value: String, plausibleYears: IntRange): LocalDate? {
        // The pattern tolerates spaces around the separators; drop them before
        // reading the parts.
        val cleaned = value.replace(" ", "")

        Regex("""^(\d{1,2})[./](\d{1,2})[./](\d{2}|\d{4})$""").matchEntire(cleaned)?.let { m ->
            val year = expandYear(m.groupValues[3].toInt())
            return safeLocalDate(year, m.groupValues[2].toInt(), m.groupValues[1].toInt(), plausibleYears)
        }

        return null
    }

    private fun expandYear(y: Int): Int = if (y < 100) 2000 + y else y

    private fun safeLocalDate(year: Int, month: Int, day: Int, plausibleYears: IntRange): LocalDate? {
        if (year !in plausibleYears) return null
        return try {
            LocalDate(year, month, day)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * A shop name has more than three letters. Four is the only load-bearing
     * number in this rule: it is what rejects the fragments that appear above
     * the header — the sample produced "kt", "lvo", "Ein" and "Die", all of them
     * show-through from the back of the slip or scraps of neighbouring paper.
     * Moving it from three to four was the whole difference between 9 and 10
     * correct answers.
     */
    private const val MIN_MERCHANT_LETTERS = 4

    /**
     * Rejects the faintest lines. Measured as insensitive: anything between 0.40
     * and 0.55 scores identically on the sample, so the rule does not balance on
     * this threshold.
     */
    private const val MIN_MERCHANT_CONFIDENCE = 0.45f

    /**
     * A line carrying one of these is a heading, not a name.
     *
     * Matched as a substring of the letters-and-digits-only form of the line,
     * which is what makes "K-U-N-D-E-N-B-E-L-E-G" and the misread
     * "-K-U-N-0-E-N-B-E-L-E-G-" both collapse onto "beleg". Comparing whole
     * words missed them, and they are exactly the lines that have to be stepped
     * over on card slips.
     */
    private val HEADING_MARKERS = listOf("beleg", "rechnung", "quittung", "kassenbon")

    /**
     * The shop name: the topmost line on the paper that could be a name at all.
     *
     * Measured over 11 real receipts (#84), against the rule this replaced at
     * 2 of 11: **10 of 11**.
     *
     * Two more elaborate ideas were built and measured first, and both are
     * worse — they are recorded here so they do not get proposed again:
     *
     * - **Weighting by print size (7 of 11).** Only on 2 of 11 receipts is the
     *   largest line the shop. Receipts set the *total* just as large, and that
     *   is at the bottom; on two fuel receipts the largest line was the amount
     *   due, on a supermarket receipt a footer about loyalty points. Worth
     *   knowing if it is ever revisited: `boundingBox` is axis-aligned around a
     *   possibly tilted line, so a long line a few degrees off horizontal gets a
     *   box far taller than its glyphs — one timestamp measured 3.3x the median
     *   height while being ordinary small print. Print size has to be corrected
     *   by the angle before it means anything.
     * - **Anchoring on the address (7 of 11).** The shop name does sit directly
     *   above its own street, and with coordinates that adjacency is finally
     *   computable — but it lands one line too low with great reliability: below
     *   the name both fuel receipts print the franchisee, the bakery a location.
     *
     * Known limit, not a bug to fix here: where OCR shatters the header, the
     * name exists only as fragments and no rule over these lines can recover it.
     * On the sample that happened once, and the fragment it returns is visible
     * in the description field for the user to correct.
     */
    private fun extractMerchant(lines: List<OcrLine>): String? =
        lines.sortedBy { it.top }
            .firstOrNull { couldBeMerchantName(it) }
            ?.text
            ?.trim()

    private fun couldBeMerchantName(line: OcrLine): Boolean {
        val text = line.text.trim()
        val letters = text.count { it.isLetter() }
        if (letters < MIN_MERCHANT_LETTERS) return false
        if ((line.confidence ?: 1f) < MIN_MERCHANT_CONFIDENCE) return false

        // Mostly digits: article numbers, totals columns, terminal ids.
        val dense = text.filterNot { it.isWhitespace() }
        if (dense.isNotEmpty() && letters.toDouble() / dense.length < 0.5) return false

        val normalised = text.lowercase().filter { it.isLetterOrDigit() }
        if (normalised.isEmpty()) return false
        if (HEADING_MARKERS.any { normalised.contains(it) }) return false

        if (DATE_REGEX.containsMatchIn(text)) return false
        if (AMOUNT_PATTERN.containsMatchIn(text)) return false

        return true
    }
}
