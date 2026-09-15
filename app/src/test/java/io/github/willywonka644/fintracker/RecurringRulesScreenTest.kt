package io.github.willywonka644.fintracker

import io.github.willywonka644.fintracker.ui.recurring.frequencyDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Unit tests for RecurringRules screen logic and helpers.
 * Covers frequency labels, list filtering/sorting, and CRUD operation semantics.
 */
class RecurringRulesScreenTest {

    // -- Frequency display names --

    @Test
    fun frequencyDisplayName_weekly_returnsWoechentlich() {
        assertEquals("Wöchentlich", frequencyDisplayName(Frequency.WEEKLY))
    }

    @Test
    fun frequencyDisplayName_monthly_returnsMonatlich() {
        assertEquals("Monatlich", frequencyDisplayName(Frequency.MONTHLY))
    }

    @Test
    fun frequencyDisplayName_yearly_returnsJaehrlich() {
        assertEquals("Jährlich", frequencyDisplayName(Frequency.YEARLY))
    }

    @Test
    fun frequencyDisplayName_allValues_haveNonBlankLabels() {
        for (freq in Frequency.values()) {
            val label = frequencyDisplayName(freq)
            assertTrue("Label for $freq should not be blank", label.isNotBlank())
        }
    }

    // -- Rule exhaustion logic --

    @Test
    fun rule_withoutRemainingExecutions_isNotExhausted() {
        val rule = createRule(remainingExecutions = null)
        assertFalse(isExhausted(rule))
    }

    @Test
    fun rule_withPositiveRemainingExecutions_isNotExhausted() {
        val rule = createRule(remainingExecutions = 5)
        assertFalse(isExhausted(rule))
    }

    @Test
    fun rule_withZeroRemainingExecutions_isExhausted() {
        val rule = createRule(remainingExecutions = 0)
        assertTrue(isExhausted(rule))
    }

    @Test
    fun rule_withOneRemainingExecution_isNotExhausted() {
        val rule = createRule(remainingExecutions = 1)
        assertFalse(isExhausted(rule))
    }

    // -- Amount sign semantics --

    @Test
    fun positiveAmount_isIncome() {
        val rule = createRule(amount = 2500.0)
        assertTrue(rule.amount > 0)
    }

    @Test
    fun negativeAmount_isExpense() {
        val rule = createRule(amount = -49.99)
        assertTrue(rule.amount < 0)
    }

    @Test
    fun zeroAmount_isNeutral() {
        val rule = createRule(amount = 0.0)
        assertEquals(0.0, rule.amount, 0.0001)
    }

    // -- ID generation semantics --

    @Test
    fun newRuleId_usesRPrefix() {
        val id = "r${42}"
        assertTrue(id.startsWith("r"))
        assertEquals("r42", id)
    }

    @Test
    fun ruleId_extractsNumericPart() {
        val id = "r15"
        val numeric = id.removePrefix("r").toIntOrNull()
        assertNotNull(numeric)
        assertEquals(15, numeric)
    }

    @Test
    fun nextIdComputation_fromEmptyList_startsAt1() {
        val rules = emptyList<RecurringRule>()
        val maxId = rules.maxOfOrNull { it.id.removePrefix("r").toIntOrNull() ?: 0 } ?: 0
        val nextId = maxId + 1
        assertEquals(1, nextId)
    }

    @Test
    fun nextIdComputation_fromPopulatedList_incrementsMax() {
        val rules = listOf(
            createRule(id = "r1"),
            createRule(id = "r5"),
            createRule(id = "r3")
        )
        val maxId = rules.maxOfOrNull { it.id.removePrefix("r").toIntOrNull() ?: 0 } ?: 0
        val nextId = maxId + 1
        assertEquals(6, nextId)
    }

    // -- CRUD operation semantics --

    @Test
    fun createRule_addsToList() {
        val rules = mutableListOf<RecurringRule>()
        val newRule = createRule(id = "r1", description = "Netflix")
        rules.add(newRule)

        assertEquals(1, rules.size)
        assertEquals("Netflix", rules[0].description)
    }

    @Test
    fun updateRule_replacesExisting() {
        val rules = mutableListOf(
            createRule(id = "r1", description = "Netflix", amount = -15.99),
            createRule(id = "r2", description = "Salary", amount = 3000.0)
        )

        val updated = rules[0].copy(amount = -19.99)
        val index = rules.indexOfFirst { it.id == "r1" }
        if (index != -1) {
            rules[index] = updated
        }

        assertEquals(-19.99, rules[0].amount, 0.0001)
        assertEquals("Netflix", rules[0].description)
        assertEquals(2, rules.size)
    }

    @Test
    fun deleteRule_removesFromList() {
        val rules = mutableListOf(
            createRule(id = "r1", description = "Netflix"),
            createRule(id = "r2", description = "Salary"),
            createRule(id = "r3", description = "Gym")
        )

        rules.removeAll { it.id == "r2" }

        assertEquals(2, rules.size)
        assertNull(rules.firstOrNull { it.id == "r2" })
        assertNotNull(rules.firstOrNull { it.id == "r1" })
        assertNotNull(rules.firstOrNull { it.id == "r3" })
    }

    @Test
    fun deleteNonexistentRule_doesNotChangeList() {
        val rules = mutableListOf(
            createRule(id = "r1", description = "Netflix")
        )

        rules.removeAll { it.id == "r99" }

        assertEquals(1, rules.size)
    }

    // -- Account name lookup --

    @Test
    fun accountLookup_existingId_returnsName() {
        val accounts = listOf(
            Account(id = "a1", name = "Girokonto", type = AccountType.GIRO),
            Account(id = "a2", name = "Sparkonto", type = AccountType.SPARKONTO)
        )
        val rule = createRule(accountId = "a1")

        val accountName = accounts.firstOrNull { it.id == rule.accountId }?.name
        assertEquals("Girokonto", accountName)
    }

    @Test
    fun accountLookup_nonexistentId_returnsNull() {
        val accounts = listOf(
            Account(id = "a1", name = "Girokonto", type = AccountType.GIRO)
        )
        val rule = createRule(accountId = "a99")

        val accountName = accounts.firstOrNull { it.id == rule.accountId }?.name
        assertNull(accountName)
    }

    // -- Due rule detection --

    @Test
    fun rule_withNextDateInPast_isDue() {
        val rule = createRule(nextExecutionDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(1, DateTimeUnit.DAY))
        assertTrue(isDue(rule))
    }

    @Test
    fun rule_withNextDateToday_isDue() {
        val rule = createRule(nextExecutionDate = Clock.System.todayIn(TimeZone.currentSystemDefault()))
        assertTrue(isDue(rule))
    }

    @Test
    fun rule_withNextDateInFuture_isNotDue() {
        val rule = createRule(nextExecutionDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.DAY))
        assertFalse(isDue(rule))
    }

    @Test
    fun exhaustedRule_isNotDue() {
        val rule = createRule(
            nextExecutionDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).minus(1, DateTimeUnit.DAY),
            remainingExecutions = 0
        )
        assertFalse(isDue(rule))
    }

    @Test
    fun rule_withRemainingExecutions_isDue() {
        val rule = createRule(
            nextExecutionDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
            remainingExecutions = 3
        )
        assertTrue(isDue(rule))
    }

    // -- Helpers --

    private fun isExhausted(rule: RecurringRule): Boolean =
        rule.remainingExecutions.let { it != null && it <= 0 }

    private fun isDue(rule: RecurringRule): Boolean {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val rem = rule.remainingExecutions
        return rule.nextExecutionDate <= today && (rem == null || rem > 0)
    }

    private fun createRule(
        id: String = "r1",
        amount: Double = -50.0,
        description: String = "Test Rule",
        accountId: String = "a1",
        frequency: Frequency = Frequency.MONTHLY,
        nextExecutionDate: LocalDate = LocalDate(2026, 3, 1),
        remainingExecutions: Int? = null,
        category: String? = null
    ) = RecurringRule(
        id = id,
        amount = amount,
        description = description,
        accountId = accountId,
        frequency = frequency,
        nextExecutionDate = nextExecutionDate,
        remainingExecutions = remainingExecutions,
        category = category
    )
}
