package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.serialization.AppJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class RecurringRuleTest {

    @Test
    fun serializeRule_fullRule_allFieldsPreserved() {
        val rule = RecurringRule(
            id = "r1",
            amount = -50.0,
            description = "Netflix",
            accountId = "a1",
            frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 3, 1),
            remainingExecutions = 12,
            category = "Entertainment"
        )

        val restored = roundTrip(rule)

        assertEquals("r1", restored.id)
        assertApprox(-50.0, restored.amount)
        assertEquals("Netflix", restored.description)
        assertEquals("a1", restored.accountId)
        assertEquals(Frequency.MONTHLY, restored.frequency)
        assertEquals(LocalDate(2026, 3, 1), restored.nextExecutionDate)
        assertEquals(12, restored.remainingExecutions)
        assertEquals("Entertainment", restored.category)
    }

    @Test
    fun serializeRule_nullOptionalFields_nullsPreserved() {
        val rule = RecurringRule(
            id = "r2",
            amount = 1500.0,
            description = "Salary",
            accountId = "a1",
            frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 3, 1),
            remainingExecutions = null,
            category = null
        )

        val restored = roundTrip(rule)

        assertNull(restored.remainingExecutions)
        assertNull(restored.category)
    }

    @Test
    fun deserializeRule_fullJson_parsesAllFields() {
        val jsonString = """
            {
              "id": "r1",
              "amount": -50.0,
              "description": "Netflix",
              "accountId": "a1",
              "frequency": "MONTHLY",
              "nextExecutionDate": "2026-03-01",
              "remainingExecutions": 5,
              "category": "Entertainment",
              "excludedDates": []
            }
        """.trimIndent()

        val rule = AppJson.decodeFromString<RecurringRule>(jsonString)

        assertEquals("r1", rule.id)
        assertApprox(-50.0, rule.amount)
        assertEquals("Netflix", rule.description)
        assertEquals("a1", rule.accountId)
        assertEquals(Frequency.MONTHLY, rule.frequency)
        assertEquals(LocalDate(2026, 3, 1), rule.nextExecutionDate)
        assertEquals(5, rule.remainingExecutions)
        assertEquals("Entertainment", rule.category)
    }

    @Test
    fun deserializeRule_missingOptionalFields_defaultsToNull() {
        val jsonString = """
            {
              "id": "r3",
              "amount": -9.99,
              "description": "Spotify",
              "accountId": "a2",
              "frequency": "MONTHLY",
              "nextExecutionDate": "2026-04-15"
            }
        """.trimIndent()

        val rule = AppJson.decodeFromString<RecurringRule>(jsonString)

        assertNull(rule.remainingExecutions)
        assertNull(rule.category)
        assertTrue(rule.excludedDates.isEmpty())
    }

    @Test
    fun deserializeRule_explicitNullFields_defaultsToNull() {
        val jsonString = """
            {
              "id": "r4",
              "amount": -100.0,
              "description": "Insurance",
              "accountId": "a1",
              "frequency": "YEARLY",
              "nextExecutionDate": "2026-06-01",
              "remainingExecutions": null,
              "category": null
            }
        """.trimIndent()

        val rule = AppJson.decodeFromString<RecurringRule>(jsonString)

        assertNull(rule.remainingExecutions)
        assertNull(rule.category)
    }

    @Test
    fun roundTrip_serializeAndDeserialize_isIdentical() {
        val original = RecurringRule(
            id = "r5",
            amount = -29.99,
            description = "Gym membership",
            accountId = "a3",
            frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 2, 20),
            remainingExecutions = 10,
            category = "Health"
        )

        assertEquals(original, roundTrip(original))
    }

    @Test
    fun roundTrip_nullOptionals_isIdentical() {
        val original = RecurringRule(
            id = "r6",
            amount = 2000.0,
            description = "Salary",
            accountId = "a1",
            frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 3, 1)
        )

        assertEquals(original, roundTrip(original))
    }

    @Test
    fun allFrequencies_serializeAndDeserializeCorrectly() {
        for (freq in Frequency.values()) {
            val rule = RecurringRule(
                id = "r-${freq.name}",
                amount = -10.0,
                description = "Test ${freq.name}",
                accountId = "a1",
                frequency = freq,
                nextExecutionDate = LocalDate(2026, 1, 1)
            )
            assertEquals(freq, roundTrip(rule).frequency)
            assertEquals(rule, roundTrip(rule))
        }
    }

    @Test
    fun multipleRules_serializeAsJsonArray_roundTrip() {
        val rules = listOf(
            RecurringRule(
                id = "r1", amount = -50.0, description = "Netflix", accountId = "a1",
                frequency = Frequency.MONTHLY, nextExecutionDate = LocalDate(2026, 3, 1),
                category = "Entertainment"
            ),
            RecurringRule(
                id = "r2", amount = 1500.0, description = "Salary", accountId = "a1",
                frequency = Frequency.MONTHLY, nextExecutionDate = LocalDate(2026, 3, 15)
            ),
            RecurringRule(
                id = "r3", amount = -52.0, description = "Weekly groceries", accountId = "a2",
                frequency = Frequency.WEEKLY, nextExecutionDate = LocalDate(2026, 2, 24),
                category = "Food"
            )
        )

        val serialized = AppJson.encodeToString(rules)
        val restored = AppJson.decodeFromString<List<RecurringRule>>(serialized)

        assertEquals(rules.size, restored.size)
        for (i in rules.indices) assertEquals(rules[i], restored[i])
    }

    @Test
    fun positiveAmount_representIncome() {
        val rule = RecurringRule(
            id = "r-income", amount = 3000.0, description = "Monthly salary",
            accountId = "a1", frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 3, 1)
        )

        assertTrue(rule.amount > 0)
        assertApprox(3000.0, roundTrip(rule).amount)
    }

    @Test
    fun negativeAmount_representExpense() {
        val rule = RecurringRule(
            id = "r-expense", amount = -14.99, description = "Disney+",
            accountId = "a1", frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 3, 5), category = "Entertainment"
        )

        assertTrue(rule.amount < 0)
        assertApprox(-14.99, roundTrip(rule).amount)
    }

    @Test
    fun remainingExecutions_serializesCorrectly() {
        val rule = RecurringRule(
            id = "r10", amount = -30.0, description = "Installment",
            accountId = "a1", frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 4, 1), remainingExecutions = 4
        )

        val restored = roundTrip(rule)
        assertEquals(4, restored.remainingExecutions)
    }

    @Test
    fun unlimitedRule_remainingExecutionsIsNull() {
        val rule = RecurringRule(
            id = "r11", amount = -9.99, description = "Subscription",
            accountId = "a1", frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 3, 1), remainingExecutions = null
        )

        assertNull(roundTrip(rule).remainingExecutions)
    }

    @Test
    fun excludedDates_roundTrip_preservesDates() {
        val original = RecurringRule(
            id = "r12", amount = -50.0, description = "Test",
            accountId = "a1", frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 3, 1),
            excludedDates = setOf(LocalDate(2026, 4, 1), LocalDate(2026, 6, 1))
        )

        val restored = roundTrip(original)

        assertEquals(original.excludedDates, restored.excludedDates)
        assertEquals(2, restored.excludedDates.size)
        assertTrue(restored.excludedDates.contains(LocalDate(2026, 4, 1)))
        assertTrue(restored.excludedDates.contains(LocalDate(2026, 6, 1)))
    }

    @Test
    fun excludedDates_emptySet_roundTripProducesEmptySet() {
        val original = RecurringRule(
            id = "r13", amount = -10.0, description = "No exclusions",
            accountId = "a1", frequency = Frequency.MONTHLY,
            nextExecutionDate = LocalDate(2026, 3, 1), excludedDates = emptySet()
        )

        val restored = roundTrip(original)

        assertTrue(restored.excludedDates.isEmpty())
        assertEquals(original, restored)
    }

    private fun roundTrip(rule: RecurringRule): RecurringRule =
        AppJson.decodeFromString(AppJson.encodeToString(rule))
}

private fun assertApprox(expected: Double, actual: Double, delta: Double = 0.0001) =
    assertTrue(kotlin.math.abs(expected - actual) <= delta, "Expected $expected, actual $actual")
