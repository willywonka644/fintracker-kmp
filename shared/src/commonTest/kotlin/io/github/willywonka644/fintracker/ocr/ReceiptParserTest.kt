package io.github.willywonka644.fintracker.ocr

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #82. The fixtures below are modelled on twelve real receipts that were
 * scanned through the app while the recognised text was captured from the log.
 * Nothing is copied: every shop, address, card number and amount is invented,
 * because the originals carried addresses, tax numbers, masked card numbers and
 * the names of cashiers.
 *
 * What is reproduced faithfully is the shape of ML Kit's output, and that is the
 * part that matters. It does not return a receipt line by line: labels arrive in
 * one run and their numbers in another, so "SUMME" and its amount can sit thirty
 * lines apart. On the sampled till receipts a total keyword shared a line with
 * its value **not once** — the parser's original assumption never held.
 *
 * Measured against the real twelve, the old logic read the correct total on 5 of
 * them; the current one on all 12. Against the fixtures here it is 7 and 12 —
 * the five that both versions pass guard behaviour that already worked.
 */
class ReceiptParserTest {

    /** Summenwoerter ohne Zahl daneben; Summe 9,60 kommt dreimal vor und liegt ueber dem OCR-Fragment B7, 00 */
    @Test
    fun bakeryColumnsTornApart() {
        val result = ReceiptParser.parse(
            """
V
B
N
RECHNUNG #1104250003 0011 0025
Artikelname
Broetchen
MUSTERBAECKER
Baeckerei Musterbaecker KG
im Einkaufszentrum
Musterweg 9
10115 Musterstadt
Menge Einheit
Vollkornbrot 500g
1 Stueck X
Zurueck:
Gesamtsumne:
MwSt:
2
Gegeben EC-Karte:
Rechnng
3 Stueck X
Netto
Einzel
2, 40
1,00
MwSt.
%
B7, 00
Anzahl verkaufter Artikel: 4,00
Es bediente Sie
Datua
Filiale:
11. 04.2026 08:25 Uhr 0011
Gesamt
5, 60
TSE Information:
3,00 B
0,61 9, 60
9,60
9, 60
0,00
Brutto
Kasse: 03/
Vielen Dank
            """.trimIndent()
        )

        assertEquals(9.6, result.amount)
        assertEquals(LocalDate(2026, 4, 11), result.date)
    }

    /** Kartenbeleg: Beschriftung und Zahl auf einer Zeile, Schluesselwortpfad */
    @Test
    fun cardSlipLabelAndValueOnOneLine() {
        val result = ReceiptParser.parse(
            """
K-U-N-D-E-N-B-E-L-E-G
Musterfriseur
Musterallee 2
10115 Musterstadt
Bezahlung Mastercard
Betrag | owos 18,00 EUR
11.04.2026 09: 16 -1D 10000001
TA-Nr. 000001 Be leg-Nr. 1001
Kartennr.#####***###0000 00
Zahlung erfolgt
Beleg zum Verbleib
            """.trimIndent()
        )

        assertEquals(18.0, result.amount)
        assertEquals(LocalDate(2026, 4, 11), result.date)
    }

    /** Kartenbeleg ohne Leerzeichen nach dem Schluesselwort */
    @Test
    fun cardSlipWithoutSpaceAfterLabel() {
        val result = ReceiptParser.parse(
            """
qwertz uiop
mnb vcx
K'UNDENBELEG
Stadtbuecherel
Am Musterplatz 4
10115 Musterstadt
Bezahlung Mastercard
Betrag12,50 EUR
07.04.2026 10:47 T-1D 10000002
TA-Nr 000002Be leg-Nr. 0700
Kartennr. #####**#*###0000 00
            """.trimIndent()
        )

        assertEquals(12.5, result.amount)
        assertEquals(LocalDate(2026, 4, 7), result.date)
    }

    /** 19,00% ist ein Steuersatz, kein Betrag */
    @Test
    fun vatRateLooksLikeAnAmount() {
        val result = ReceiptParser.parse(
            """
Spuelmittel
Zahnbuerste
K.Sahne 2 # 0,79
Kaugummi
Sume
Mustermarkt - Industrie
Musterstrasse 7
10115 Musterstadt
DE111111111
Kartenzahlung
Steuer % Brutto
A 19,00%
0,21
1,29
1,65
1,79
2,49
Datum: 11.04.26 Zeit: 15:27:39 Bon:10001
Terminal-ID :
TA-Nr 100001
Summe
EUR
6,43
6,43
6,43
Netto
5,40
1,03
            """.trimIndent()
        )

        assertEquals(6.43, result.amount)
        assertEquals(LocalDate(2026, 4, 11), result.date)
    }

    /** Summe kommt genau einmal vor, Artikelpreise wiederholen sich */
    @Test
    fun totalAppearsExactlyOnceAtTheBottom() {
        val result = ReceiptParser.parse(
            """
d-drogerie markt
Musterring 21
10115 Musterstadt
Handcreme
2,89 2
Duschgel
1,49 2
Shampoo
2,89 1
Zahnpasta
3,79 1
Wattestaebchen
1,19 2
Papiertuecher
0,99 3
Seife
2,89 1
Buerste
4,59 1
Kamm
1,99 1
Spiegel
6,49 1
Schere
8,99 1
Feile
1,29 1
Steuer-Nr.:11111/11111
39,90
ik***** FISKALINFORMATIONEN TSE *****
Signatur: abcdef
            """.trimIndent()
        )

        assertEquals(39.9, result.amount)
        assertEquals(null, result.date)
    }

    /** 0001111100022200 darf keinen Betrag liefern; Summe kommt viermal vor */
    @Test
    fun phantomFromALongDigitRun() {
        val result = ReceiptParser.parse(
            """
BANANE BIO
SUMME
Mustermarkt Musterinhaber
1, 228 kg
GOUDA SCHEIBEN
BUTTERKAESE SCH.
BIO BERGKAESE
BIO HEFEWUERFEL
3 Stk x
BIO JOGHURT 1,8%.
2,19 EUR/kg
Musterdamm 3
10115 Musterstadt
Tel: 030-1111111
Steuer Nr. 11111/11111
UID Nr, DE111111111
Terminal-ID
1,19
1,45
1,89
2,29
2,79
9,80
EUR
21,51
21,51
21,51
21,51
Geg. EC
Kartenzahlung
#***#**#******#0000 0002
0001111100022200
11.04. 2026
11.04.2026 07:31
            """.trimIndent()
        )

        assertEquals(21.51, result.amount)
        assertEquals(LocalDate(2026, 4, 11), result.date)
    }

    /** BRUTTO steht neben dem Nettowert; Summe kommt viermal vor */
    @Test
    fun fuelReceiptNetNextToBruttoLabel() {
        val result = ReceiptParser.parse(
            """
MUSTER Tankstelle
Musterinhaber
Musterchaussee 15
10115 Musterstadt
Tel.: 030-2222222
Steuer-Nr.: 22222222222
www.muster-tankstelle.de
30,00 Liter SAEULENNUMMER 4*
60,00 EUR#
*Super E10
2,000 EUR/Liter
Terminalnummer
Kartenfolgenummer
Karte #####*#########0000
MWST 19,00% A
NETTO
Kartenzahlung
Zahlung erfolgt
60,00 EUR
50,42 EUR BRUTTO
60,00 EUR
60,00 EUR
9,58 EUR
#10001 11.04.26 19:55
            """.trimIndent()
        )

        assertEquals(60.0, result.amount)
        assertEquals(LocalDate(2026, 4, 11), result.date)
    }

    /** Jahr im Datum ist verstuemmelt (026); Betrag bleibt trotzdem richtig */
    @Test
    fun ocrMangledYearInTheDate() {
        val result = ReceiptParser.parse(
            """
Strg
A
W
Alt
X
11.04.026 18:13 Dry 001111/3 1111
Datun 13.04.26
Bio Tee
Mineralwasser 0,25 Pf
Musterring 21
10115 Musterstadt
Haarshampoo
Duschbad
Handseife
Zahnbuerste
Papiertaschentuecher
Waschmittel
Weichspueler
Allzweckreiniger
0,25
0,79
1,19
1,49
2,09
2,89
3,19
3,79
4,49
5,29
9,95
Terminal-ID :
TA-Nr 111111
Steuer-Nr.:11111/11111
SUMME
35,40
35,40
            """.trimIndent()
        )

        assertEquals(35.4, result.amount)
        assertEquals(LocalDate(2026, 4, 13), result.date)
    }

    /** zweiter Tankbeleg, andere Reihenfolge, gleiche Netto/Brutto-Falle */
    @Test
    fun fuelReceiptSecondLayout() {
        val result = ReceiptParser.parse(
            """
Uhrzeit
Beleg-Nr
MUSTER Tankstelle
Betrag
Karte
*Super E10
2,000 EUR/Liter
Musterinhaber
Musterchaussee 15
10115 Musterstadt
Tell: 030-2222222
25,00 Liter SAEULENNUMMER 2 *
A50,00 EUR*
Kartenfolgenummer
TOTAL
MWST 19,00% A
NETTO
Kartenzahlung
11.04.2026
Zahlung erfolgt
50,00 EUR
42,02 EUR BRUTTO
50,00 EUR
50,00 EUR
#10002 11.04.26 16:46
7,98 EUR
50,00 EUR
            """.trimIndent()
        )

        assertEquals(50.0, result.amount)
        assertEquals(LocalDate(2026, 4, 11), result.date)
    }

    /** verstuemmelter Zeitstempel liefert sonst 70,00; nur zwei Betraege im Text */
    @Test
    fun phantomFromAMangledTimestamp() {
        val result = ReceiptParser.parse(
            """
Musterbaumarkt
04
Zahlung erfolgt
NETTO-Entgelt
11 22
111111
o01 0002
1111111
011 00
08:37 Uhr
19,99
sokjrSc82ninti68nlJBululclupy
2001-01-11eT01:11:70.000
Beleg zum Verbleib
Kartenzahlung
            """.trimIndent()
        )

        assertEquals(19.99, result.amount)
        assertEquals(null, result.date)
    }

    /** BRUTTO ist groesser als die Summe; Pfandbetraege wiederholen sich oft */
    @Test
    fun bruttoLargerThanTheTotal() {
        val result = ReceiptParser.parse(
            """
At
Pfand
Kombi Kiste
Kombi (Pfand)
LF/DOSE 0,25 EUR
L6 12/0,15 EUR
L6 12/0,15 EUR
MUSTER alkoholfrei 0.5
MUSTER hell 20/0.5
IK 1,50 OR
Kombi Ende
16 12/0, 15 EUR
Musterquelle 6
10115 Musterstadt
0,15
0,15
0,15
0,25
1,50
3,30
3,30 A
33,30
33,30
TA-Nr 111111
Es bediente Sie
33,30
2222222222100000000
BRUTTO
34,05
011
Getraenkemarkt Muster
Musterweg 12
10115 Musterstadt
            """.trimIndent()
        )

        assertEquals(33.3, result.amount)
        assertEquals(null, result.date)
    }

    /** zweite Aufnahme desselben Bons: sauberer erkannt, muss ebenfalls stimmen */
    @Test
    fun sameReceiptScannedAgainCleanly() {
        val result = ReceiptParser.parse(
            """
A
Alt
S
X
F
MUSTERBAECKER
Artikelnane
Broetchen
2
Baeckerei Musterbaecker KG
im Einkaufszentrum
Musterweg 9
10115 Musterstadt
Tel. 030
Menge Einheit
Vollkornbrot 500g
Gesamt
Netto
MwSt.
TSE Information:
3, 00 B
0,61 9, 60
9,60
9, 60
0,00
Brutto
Es bediente Sie
Kasse: 03/
Vielen Dank
            """.trimIndent()
        )

        assertEquals(9.6, result.amount)
        assertEquals(null, result.date)
    }

    @Test
    fun anEmptyScanYieldsNothingRatherThanZero() {
        val result = ReceiptParser.parse("")

        assertEquals(null, result.amount)
        assertFalse(result.amountConfident)
    }

    @Test
    fun aVatRateIsNeverReadAsAnAmount() {
        // Isolated version of the supermarket case: the only number that looks
        // like money is a tax rate, so nothing may be reported.
        val result = ReceiptParser.parse("Steuer % Brutto\nA 19,00%")

        assertEquals(null, result.amount)
    }

    @Test
    fun aDateDoesNotProduceAnAmount() {
        // "08.2026" used to be read as 8,20 and outbid the real total.
        val result = ReceiptParser.parse("11. 04.2026 08:25 Uhr 0011\nSUMME\n5,60")

        assertEquals(5.60, result.amount)
    }

    @Test
    fun aThousandsSeparatorSurvives() {
        // "1.234,56" used to be chopped into 1,23 and 4,56 — and the keyword
        // path returned 1,23 as confident, wrong by a factor of a thousand.
        val result = ReceiptParser.parse("SUMME 1.234,56 EUR")

        assertEquals(1234.56, result.amount)
        assertTrue(result.amountConfident)
    }

    @Test
    fun aLabelOnItsOwnLineIsNotTreatedAsEvidence() {
        // A keyword without a value beside it must not make the guess look
        // certain — that is how the fuel receipts reported the net amount.
        val result = ReceiptParser.parse("SUMME\nGesamt\n12,00\n9,00")

        assertEquals(12.00, result.amount)
        assertFalse(result.amountConfident)
    }

    // ── Datum ────────────────────────────────────────────────────────────────

    @Test
    fun spacesAroundTheSeparatorsDoNotHideTheDate() {
        // ML Kit really returns "12. 08. 2026". Three sampled receipts yielded
        // no date at all because of it.
        assertEquals(LocalDate(2026, 8, 12), ReceiptParser.parse("12. 08. 2026").date)
        assertEquals(LocalDate(2026, 8, 12), ReceiptParser.parse("12. 08.2026").date)
    }

    @Test
    fun aThreeDigitYearIsRejected() {
        // "13.03.025" was read as 2025 and outranked the correct date printed
        // further down the same receipt. A year has two digits or four.
        assertEquals(null, ReceiptParser.parse("13.03.025 18:13").date)
    }

    @Test
    fun theLabelledLineWinsOverAnEarlierDate() {
        // On one receipt the header carried a misread month and the line
        // labelled "Datum" was the only correct date on the slip.
        val result = ReceiptParser.parse("13.03.2026 18:13 Kasse 3\nDatum 13.08.26")

        assertEquals(LocalDate(2026, 8, 13), result.date)
        assertTrue(result.dateConfident)
    }

    @Test
    fun theLabelIsMatchedByItsStemBecauseOcrManglesIt() {
        // Seen in the sample: "Datun" and "Datua".
        assertEquals(LocalDate(2026, 8, 13), ReceiptParser.parse("Datun 13.08.26").date)
        assertEquals(LocalDate(2026, 8, 13), ReceiptParser.parse("Datua 13.08.26").date)
    }

    @Test
    fun anImplausibleYearIsRejected() {
        // The range is a parameter so this stays deterministic and does not turn
        // red on its own once the calendar moves past a hard-coded window.
        val result = ReceiptParser.parse("01.02.2019", plausibleYears = 2020..2035)

        assertEquals(null, result.date)
    }

    @Test
    fun aTimeIsNotMistakenForADate() {
        assertEquals(null, ReceiptParser.parse("08:25 Uhr\n15:27:39").date)
    }
}
