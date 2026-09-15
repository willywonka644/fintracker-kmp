package io.github.willywonka644.fintracker.qrscan

/**
 * Parsed result from an EPC/GiroCode QR code.
 * See: https://en.wikipedia.org/wiki/EPC_QR_code
 */
data class EpcData(
    val bic: String?,
    val recipientName: String,
    val iban: String,
    val amount: Double?,
    val reference: String?,
    val text: String?
)

object EpcParser {

    /**
     * Parses an EPC/GiroCode QR code payload.
     *
     * EPC QR Code format (line-separated):
     *   Line 1: Service tag ("BCD")
     *   Line 2: Version ("001" or "002")
     *   Line 3: Character set (1 = UTF-8)
     *   Line 4: Identification code ("SCT")
     *   Line 5: BIC (optional in v2)
     *   Line 6: Recipient name (max 70 chars)
     *   Line 7: IBAN
     *   Line 8: Amount (e.g. "EUR123.45")
     *   Line 9: Purpose code (optional, 4 chars)
     *   Line 10: Structured reference (e.g. RF-creditor-reference)
     *   Line 11: Unstructured remittance text
     *   Line 12: Beneficiary-to-originator info (optional)
     *
     * Returns null if the payload is not a valid EPC QR code.
     */
    fun parse(payload: String): EpcData? {
        val lines = payload.lines()
        if (lines.size < 7) return null

        // Line 1: Must be "BCD"
        if (lines[0].trim().uppercase() != "BCD") return null

        // Line 2: Version "001" or "002"
        val version = lines[1].trim()
        if (version != "001" && version != "002") return null

        // Line 4: Must be "SCT" (SEPA Credit Transfer)
        if (lines[3].trim().uppercase() != "SCT") return null

        val bic = lines[4].trim().takeIf { it.isNotEmpty() }
        val recipientName = lines[5].trim()
        val iban = lines[6].trim()

        if (recipientName.isEmpty() || iban.isEmpty()) return null

        // Line 8: Amount (optional), format: "EUR123.45"
        val amount = lines.getOrNull(7)?.trim()?.let { parseAmount(it) }

        // Line 10: Structured reference (optional)
        val reference = lines.getOrNull(9)?.trim()?.takeIf { it.isNotEmpty() }

        // Line 11: Unstructured remittance text (optional)
        val text = lines.getOrNull(10)?.trim()?.takeIf { it.isNotEmpty() }

        return EpcData(
            bic = bic,
            recipientName = recipientName,
            iban = iban,
            amount = amount,
            reference = reference,
            text = text
        )
    }

    /**
     * Parses the amount field from an EPC QR code.
     * Format: "EUR123.45" or "EUR1234.56"
     */
    private fun parseAmount(value: String): Double? {
        if (value.isEmpty()) return null
        val cleaned = value.uppercase()
            .removePrefix("EUR")
            .trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull()
    }
}
