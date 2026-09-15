package io.github.willywonka644.fintracker.ui.recurring

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Frequency
import io.github.willywonka644.fintracker.RecurringRule
import io.github.willywonka644.fintracker.ui.theme.FinTrackerTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Phase 6.5 Tier 2a — the create/edit dialog for recurring rules.
 *
 * This is the live dialog: it is opened from `DesktopDauerauftraegeScreen`.
 * (The `RecurringRulesDialog` composable in the same file is currently
 * unreachable — nothing ever sets `showRecurringRules` to true.)
 */
@OptIn(ExperimentalTestApi::class)
class AddEditRecurringRuleDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val giro = Account(id = "acc-1", name = "Girokonto", type = AccountType.GIRO)

    private var saved: RecurringRule? = null
    private var deleted: RecurringRule? = null

    private val existingRule = RecurringRule(
        id = "r7",
        amount = -49.9,
        description = "Fitnessstudio",
        accountId = giro.id,
        frequency = Frequency.MONTHLY,
        nextExecutionDate = LocalDate(2026, 9, 1),
        remainingExecutions = 5,
        category = "Freizeit",
    )

    private fun showDialog(initialRule: RecurringRule? = null, withDelete: Boolean = false) {
        compose.setContent {
            FinTrackerTheme {
                AddEditRecurringRuleDialog(
                    initialRule = initialRule,
                    accounts = listOf(giro),
                    categories = emptyList(),
                    onDismiss = {},
                    onSave = { saved = it },
                    onDelete = if (withDelete) ({ deleted = it }) else null,
                )
            }
        }
    }

    @Test
    fun newRuleGetsABlankIdSoTheCallerCanAssignOne() {
        showDialog()

        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performTextInput("-49,90")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performTextInput("Fitnessstudio")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.SAVE).performClick()

        val rule = assertNotNull(saved, "expected the dialog to emit a rule")
        // AppContent turns the blank id into "r<n>" — see Main.kt onSaveRule.
        assertEquals("", rule.id, "a new rule must carry a blank id")
        assertEquals(-49.9, rule.amount, 0.001, "German decimal comma must be accepted")
        assertEquals("Fitnessstudio", rule.description)
        assertEquals(giro.id, rule.accountId, "defaults to the first account")
        assertEquals(Frequency.MONTHLY, rule.frequency, "monthly is the default frequency")
    }

    @Test
    fun descriptionIsTrimmedBeforeSaving() {
        showDialog()

        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performTextInput("10")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performTextInput("  Miete  ")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.SAVE).performClick()

        assertEquals("Miete", assertNotNull(saved).description)
    }

    @Test
    fun anUnparsableAmountBlocksSaving() {
        showDialog()

        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performTextInput("keine Zahl")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performTextInput("Fitnessstudio")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.SAVE).performClick()

        assertNull(saved, "an invalid amount must not produce a rule")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT_ERROR).assertExists()
    }

    @Test
    fun aBlankDescriptionBlocksSaving() {
        showDialog()

        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.AMOUNT).performTextInput("10")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.SAVE).performClick()

        assertNull(saved, "an empty description must not produce a rule")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION_ERROR).assertExists()
    }

    @Test
    fun editingPreservesIdAndRemainingExecutions() {
        showDialog(initialRule = existingRule)

        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performClick()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performTextClearance()
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DESCRIPTION).performTextInput("Sportverein")
        compose.onNodeWithTag(RecurringRuleDialogTestTags.SAVE).performClick()

        val rule = assertNotNull(saved)
        assertEquals("Sportverein", rule.description)
        assertEquals("r7", rule.id, "editing must not mint a new id")
        assertEquals(5, rule.remainingExecutions, "an existing limit must survive editing")
        assertEquals(LocalDate(2026, 9, 1), rule.nextExecutionDate)
        assertEquals("Freizeit", rule.category)
    }

    @Test
    fun deletingRequiresConfirmationFirst() {
        showDialog(initialRule = existingRule, withDelete = true)

        // The delete button sits at the bottom of the dialog's scrollable area —
        // without scrolling to it the click lands outside the clip and is lost.
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DELETE).performScrollTo().performClick()
        assertNull(deleted, "the first click only opens the confirmation")

        compose.onNodeWithTag(RecurringRuleDialogTestTags.DELETE_CONFIRM).performClick()
        assertEquals(existingRule, deleted)
    }

    @Test
    fun deleteConfirmationPromisesToKeepPostedBookings() {
        showDialog(initialRule = existingRule, withDelete = true)

        compose.onNodeWithTag(RecurringRuleDialogTestTags.DELETE).performScrollTo().performClick()

        // The wording is the lesson from the DKB incident — deleting a rule must
        // not take posted history with it.
        compose.onNodeWithTag(RecurringRuleDialogTestTags.DELETE_CONFIRM_TEXT)
            .assertTextContains("Bereits gebuchte Zahlungen bleiben erhalten.", substring = true)
    }

    @Test
    fun deleteIsNotOfferedWhenCreatingANewRule() {
        showDialog(initialRule = null, withDelete = true)

        compose.onNodeWithTag(RecurringRuleDialogTestTags.DELETE).assertDoesNotExist()
    }
}
