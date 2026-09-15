package io.github.willywonka644.fintracker.category

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Issue #77: the save path used to check only for a blank name, which is how three
 * duplicate categories ended up in the production database. Bookings reference
 * categories by name, so duplicates make colour, icon and the analytics ambiguous.
 */
class CategoryNameConflictTest {

    private fun category(id: String, name: String, deleted: Boolean = false) = Category(
        id = id, name = name, iconName = "MoreHoriz", color = 0xFF8D6E63,
        isDefault = false, lastModifiedAt = 1L, deleted = deleted
    )

    private val existing = listOf(
        category("default-lebensmittel", "Lebensmittel"),
        category("default-sonstiges", "Sonstiges"),
    )

    @Test
    fun rejectsAnExactDuplicate() {
        assertTrue(existing.hasCategoryNameConflict("Lebensmittel"))
    }

    @Test
    fun rejectsDuplicatesThatDifferOnlyInCaseOrPadding() {
        // "  lebensmittel " is the same category to a human and to every booking
        // that references it, because the lookup trims and the picker sorts folded.
        assertTrue(existing.hasCategoryNameConflict("  lebensmittel "))
        assertTrue(existing.hasCategoryNameConflict("LEBENSMITTEL"))
    }

    @Test
    fun acceptsAnUnusedName() {
        assertFalse(existing.hasCategoryNameConflict("Bargeldabhebung"))
    }

    @Test
    fun aCategoryDoesNotCollideWithItself() {
        // Editing only the colour of an existing category must still save.
        assertFalse(
            existing.hasCategoryNameConflict("Lebensmittel", excludingId = "default-lebensmittel")
        )
    }

    @Test
    fun renamingOntoAnotherCategorysNameIsStillRejected() {
        assertTrue(
            existing.hasCategoryNameConflict("Sonstiges", excludingId = "default-lebensmittel")
        )
    }

    @Test
    fun tombstonesDoNotBlockTheName() {
        // A deleted category is invisible in the UI. Blocking its name would reject
        // the input for a reason the user has no way of seeing.
        val withTombstone = existing + category("dead", "Hobbys", deleted = true)
        assertFalse(withTombstone.hasCategoryNameConflict("Hobbys"))
    }

    @Test
    fun blankIsNotAConflict() {
        // The empty-name check is a separate rule with its own message; this one
        // must not claim a blank field is "already taken".
        assertFalse(existing.hasCategoryNameConflict(""))
        assertFalse(existing.hasCategoryNameConflict("   "))
    }
}
