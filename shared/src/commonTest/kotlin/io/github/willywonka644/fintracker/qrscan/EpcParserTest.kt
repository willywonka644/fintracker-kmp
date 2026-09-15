package io.github.willywonka644.fintracker.qrscan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EpcParserTest {

    /** Helper: build a standard 12-line EPC payload. */
    private fun epcPayload(
        serviceTag: String = "BCD",
        version: String = "002",
        charset: String = "1",
        identification: String = "SCT",
        bic: String = "COBADEFFXXX",
        recipientName: String = "Max Mustermann",
        iban: String = "DE89370400440532013000",
        amount: String = "EUR125.50",
        purposeCode: String = "",
        reference: String = "RF18539007547034",
        text: String = "",
        beneficiaryInfo: String = ""
    ): String = listOf(
        serviceTag, version, charset, identification,
        bic, recipientName, iban, amount,
        purposeCode, reference, text, beneficiaryInfo
    ).joinToString("\n")

    @Test
    fun parse_valid_EPC_v002_with_all_fields() {
        val result = EpcParser.parse(epcPayload())

        assertNotNull(result)
        assertEquals("COBADEFFXXX", result!!.bic)
        assertEquals("Max Mustermann", result.recipientName)
        assertEquals("DE89370400440532013000", result.iban)
        assertApprox(125.50, result.amount!!)
        assertEquals("RF18539007547034", result.reference)
        assertNull(result.text)
    }

    @Test
    fun parse_valid_EPC_v001() {
        val result = EpcParser.parse(epcPayload(version = "001"))

        assertNotNull(result)
        assertEquals("Max Mustermann", result!!.recipientName)
    }

    @Test
    fun parse_with_unstructured_text_instead_of_reference() {
        val result = EpcParser.parse(
            epcPayload(reference = "", text = "Rechnung 2024-001")
        )

        assertNotNull(result)
        assertNull(result!!.reference)
        assertEquals("Rechnung 2024-001", result.text)
    }

    @Test
    fun parse_without_BIC_v002_allows_empty_BIC() {
        val result = EpcParser.parse(epcPayload(bic = ""))

        assertNotNull(result)
        assertNull(result!!.bic)
        assertEquals("Max Mustermann", result.recipientName)
    }

    @Test
    fun parse_without_amount() {
        val result = EpcParser.parse(epcPayload(amount = ""))

        assertNotNull(result)
        assertNull(result!!.amount)
    }

    @Test
    fun parse_with_EUR_prefix_only_returns_null_amount() {
        val result = EpcParser.parse(epcPayload(amount = "EUR"))

        assertNotNull(result)
        assertNull(result!!.amount)
    }

    @Test
    fun reject_payload_with_wrong_service_tag() {
        val result = EpcParser.parse(epcPayload(serviceTag = "XXX"))
        assertNull(result)
    }

    @Test
    fun reject_payload_with_invalid_version() {
        val result = EpcParser.parse(epcPayload(version = "003"))
        assertNull(result)
    }

    @Test
    fun reject_payload_with_wrong_identification_code() {
        val result = EpcParser.parse(epcPayload(identification = "SDD"))
        assertNull(result)
    }

    @Test
    fun reject_payload_with_empty_recipient_name() {
        val result = EpcParser.parse(epcPayload(recipientName = ""))
        assertNull(result)
    }

    @Test
    fun reject_payload_with_empty_IBAN() {
        val result = EpcParser.parse(epcPayload(iban = ""))
        assertNull(result)
    }

    @Test
    fun reject_payload_with_too_few_lines() {
        val result = EpcParser.parse("BCD\n002\n1\nSCT\nBIC\nName")
        assertNull(result)
    }

    @Test
    fun parse_minimal_valid_payload_7_lines() {
        val payload = "BCD\n002\n1\nSCT\n\nMax Mustermann\nDE89370400440532013000"
        val result = EpcParser.parse(payload)

        assertNotNull(result)
        assertEquals("Max Mustermann", result!!.recipientName)
        assertEquals("DE89370400440532013000", result.iban)
        assertNull(result.bic)
        assertNull(result.amount)
        assertNull(result.reference)
        assertNull(result.text)
    }

    @Test
    fun parse_handles_whitespace_in_fields() {
        val result = EpcParser.parse(
            epcPayload(
                serviceTag = " BCD ",
                recipientName = " Max Mustermann ",
                iban = " DE89370400440532013000 "
            )
        )

        assertNotNull(result)
        assertEquals("Max Mustermann", result!!.recipientName)
        assertEquals("DE89370400440532013000", result.iban)
    }

    @Test
    fun parse_handles_case_insensitive_service_tag_and_identification() {
        val result = EpcParser.parse(
            epcPayload(serviceTag = "bcd", identification = "sct")
        )

        assertNotNull(result)
        assertEquals("Max Mustermann", result!!.recipientName)
    }

    @Test
    fun parse_amount_with_integer_value() {
        val result = EpcParser.parse(epcPayload(amount = "EUR100"))

        assertNotNull(result)
        assertApprox(100.0, result!!.amount!!)
    }
}

private fun assertApprox(expected: Double, actual: Double, delta: Double = 0.001) =
    kotlin.test.assertTrue(kotlin.math.abs(expected - actual) <= delta, "Expected $expected, actual $actual")
