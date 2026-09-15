package io.github.willywonka644.fintracker.category

/**
 * Is [name] already taken by another live category?
 *
 * Bookings reference categories by name, not by id, so two categories sharing a
 * name are not merely untidy — the pickers and the analytics disagree about which
 * one a booking belongs to, and colour and icon become a coin toss. Issue #77 grew
 * out of exactly that: the save path only ever checked for a blank name.
 *
 * Tombstones are ignored on purpose. A deleted category is invisible in the UI, so
 * blocking its name would reject the input for a reason the user cannot see.
 *
 * @param excludingId  The category being edited. Without it, saving an unchanged
 *                     category would collide with itself.
 */
fun List<Category>.hasCategoryNameConflict(name: String, excludingId: String? = null): Boolean {
    val candidate = name.normalizedCategoryName()
    if (candidate.isEmpty()) return false
    return any { other ->
        !other.deleted &&
            other.id != excludingId &&
            other.name.normalizedCategoryName() == candidate
    }
}

/** Trimmed and case-folded, so "  lebensmittel " and "Lebensmittel" are one name. */
private fun String.normalizedCategoryName(): String = trim().lowercase()
