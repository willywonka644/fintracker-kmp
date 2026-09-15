package io.github.willywonka644.fintracker.csvimport

import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.security.sha256
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

enum class CsvColumnRole {
    DATE, AMOUNT, DESCRIPTION, CATEGORY, IGNORE
}

data class CsvParseResult(
    val headers: List<String>,
    val rows: List<List<String>>,
    val detectedDelimiter: Char,
    val autoMapping: Map<Int, CsvColumnRole>
)

object CsvParser {

    private val COMMON_DELIMITERS = listOf(';', ',', '\t', '|')

    private val DATE_KEYWORDS = listOf("datum", "date", "buchungstag", "valuta", "wertstellung", "buchungsdatum")
    private val AMOUNT_KEYWORDS = listOf("betrag", "amount", "umsatz", "saldo", "wert", "summe", "value")
    private val DESCRIPTION_KEYWORDS = listOf(
        "beschreibung", "verwendungszweck", "description", "text", "buchungstext",
        "empfänger", "empfaenger", "auftraggeber", "name", "bezeichnung"
    )
    private val CATEGORY_KEYWORDS = listOf("kategorie", "category", "typ", "type", "art")

    fun parse(csvText: String): CsvParseResult {
        val lines = csvText.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return CsvParseResult(emptyList(), emptyList(), ';', emptyMap())

        val delimiter = detectDelimiter(lines)
        val parsedRows = lines.map { parseLine(it, delimiter) }

        val headers = parsedRows.first()
        val dataRows = parsedRows.drop(1)

        val autoMapping = autoDetectMapping(headers, dataRows)

        return CsvParseResult(
            headers = headers,
            rows = dataRows,
            detectedDelimiter = delimiter,
            autoMapping = autoMapping
        )
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val firstFewLines = lines.take(5)
        return COMMON_DELIMITERS.maxByOrNull { delimiter ->
            val counts = firstFewLines.map { line -> parseLine(line, delimiter).size }
            if (counts.all { it == counts.first() } && counts.first() > 1) {
                counts.first()
            } else {
                0
            }
        } ?: ';'
    }

    private fun parseLine(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == delimiter && !inQuotes -> {
                    fields.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString().trim())
        return fields
    }

    private fun autoDetectMapping(headers: List<String>, dataRows: List<List<String>>): Map<Int, CsvColumnRole> {
        val mapping = mutableMapOf<Int, CsvColumnRole>()
        val assigned = mutableSetOf<CsvColumnRole>()

        // Phase 1: Match by header name keywords
        headers.forEachIndexed { index, header ->
            val lower = header.lowercase()
            val role = when {
                CsvColumnRole.DATE !in assigned && DATE_KEYWORDS.any { lower.contains(it) } -> CsvColumnRole.DATE
                CsvColumnRole.AMOUNT !in assigned && AMOUNT_KEYWORDS.any { lower.contains(it) } -> CsvColumnRole.AMOUNT
                CsvColumnRole.DESCRIPTION !in assigned && DESCRIPTION_KEYWORDS.any { lower.contains(it) } -> CsvColumnRole.DESCRIPTION
                CsvColumnRole.CATEGORY !in assigned && CATEGORY_KEYWORDS.any { lower.contains(it) } -> CsvColumnRole.CATEGORY
                else -> null
            }
            if (role != null) {
                mapping[index] = role
                assigned.add(role)
            }
        }

        // Phase 2: Match by data content analysis for unmapped required columns
        if (CsvColumnRole.DATE !in assigned) {
            val dateCol = headers.indices.firstOrNull { col ->
                col !in mapping && isDateColumn(dataRows, col)
            }
            if (dateCol != null) {
                mapping[dateCol] = CsvColumnRole.DATE
                assigned.add(CsvColumnRole.DATE)
            }
        }

        if (CsvColumnRole.AMOUNT !in assigned) {
            val amountCol = headers.indices.firstOrNull { col ->
                col !in mapping && isAmountColumn(dataRows, col)
            }
            if (amountCol != null) {
                mapping[amountCol] = CsvColumnRole.AMOUNT
                assigned.add(CsvColumnRole.AMOUNT)
            }
        }

        if (CsvColumnRole.DESCRIPTION !in assigned) {
            val descCol = headers.indices.firstOrNull { col ->
                col !in mapping && isDescriptionColumn(dataRows, col)
            }
            if (descCol != null) {
                mapping[descCol] = CsvColumnRole.DESCRIPTION
                assigned.add(CsvColumnRole.DESCRIPTION)
            }
        }

        return mapping
    }

    private fun isDateColumn(rows: List<List<String>>, colIndex: Int): Boolean {
        val sample = rows.take(5).mapNotNull { it.getOrNull(colIndex) }.filter { it.isNotBlank() }
        if (sample.isEmpty()) return false
        val matches = sample.count { value -> tryParseDate(value) != null }
        return matches.toDouble() / sample.size >= 0.6
    }

    private fun isAmountColumn(rows: List<List<String>>, colIndex: Int): Boolean {
        val sample = rows.take(5).mapNotNull { it.getOrNull(colIndex) }.filter { it.isNotBlank() }
        if (sample.isEmpty()) return false
        val matches = sample.count { value -> tryParseAmount(value) != null }
        return matches.toDouble() / sample.size >= 0.6
    }

    private fun isDescriptionColumn(rows: List<List<String>>, colIndex: Int): Boolean {
        val sample = rows.take(5).mapNotNull { it.getOrNull(colIndex) }.filter { it.isNotBlank() }
        if (sample.isEmpty()) return false
        val avgLength = sample.map { it.length }.average()
        val notNumeric = sample.count { tryParseAmount(it) == null }
        val notDate = sample.count { tryParseDate(it) == null }
        return avgLength > 3 && notNumeric >= sample.size * 0.6 && notDate >= sample.size * 0.6
    }

    fun tryParseDate(value: String): LocalDate? {
        val cleaned = value.trim()

        // yyyy-MM-dd (ISO)
        if (cleaned.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            return safeLocalDate(cleaned.substring(0, 4).toInt(), cleaned.substring(5, 7).toInt(), cleaned.substring(8, 10).toInt())
        }

        // dd.MM.yyyy or d.M.yyyy (dot-separated, day-first)
        Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""").matchEntire(cleaned)?.let { m ->
            return safeLocalDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }

        // dd/MM/yyyy (slash, day-first)
        Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})$""").matchEntire(cleaned)?.let { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); val y = m.groupValues[3].toInt()
            // Try dd/MM/yyyy first; if invalid, try MM/dd/yyyy
            return safeLocalDate(y, mo, d) ?: safeLocalDate(y, d, mo)
        }

        // dd-MM-yyyy (dash, day-first)
        Regex("""^(\d{1,2})-(\d{1,2})-(\d{4})$""").matchEntire(cleaned)?.let { m ->
            return safeLocalDate(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }

        return null
    }

    private fun safeLocalDate(year: Int, month: Int, day: Int): LocalDate? =
        try { LocalDate(year, month, day) } catch (_: Exception) { null }

    /**
     * Generates a duplicate-detection key from a booking's core fields:
     * date (day precision) + amount + description + accountId.
     * Uses SHA-256 hash for compact, collision-resistant keys.
     */
    fun duplicateKey(booking: Booking): String {
        val dateStr = Instant.fromEpochMilliseconds(booking.timestamp)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
        val raw = "$dateStr|${booking.amount}|${booking.description.trim().lowercase()}|${booking.accountId}"
        val hash = sha256(raw.encodeToByteArray())
        return hash.joinToString("") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /**
     * Builds a set of duplicate keys from existing bookings for fast lookup.
     */
    fun buildDuplicateKeySet(existingBookings: List<Booking>): Set<String> {
        return existingBookings.map { duplicateKey(it) }.toSet()
    }

    fun tryParseAmount(value: String): Double? {
        val cleaned = value.trim()
            .replace("€", "")
            .replace("EUR", "", ignoreCase = true)
            .replace(" ", "")
            .trim()

        if (cleaned.isEmpty()) return null

        // German format: 1.234,56 → handle comma as decimal separator
        val germanPattern = Regex("""^-?\d{1,3}(\.\d{3})*(,\d{1,2})?$""")
        if (germanPattern.matches(cleaned)) {
            val normalized = cleaned.replace(".", "").replace(",", ".")
            return normalized.toDoubleOrNull()
        }

        // International format: 1,234.56
        val intlPattern = Regex("""^-?\d{1,3}(,\d{3})*(\.\d{1,2})?$""")
        if (intlPattern.matches(cleaned)) {
            val normalized = cleaned.replace(",", "")
            return normalized.toDoubleOrNull()
        }

        // Simple decimal with comma: 123,45
        val simpleComma = Regex("""^-?\d+(,\d{1,2})$""")
        if (simpleComma.matches(cleaned)) {
            return cleaned.replace(",", ".").toDoubleOrNull()
        }

        // Fallback: try direct parse
        return cleaned.toDoubleOrNull()
    }
}
