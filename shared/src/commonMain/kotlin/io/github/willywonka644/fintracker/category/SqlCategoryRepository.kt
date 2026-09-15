package io.github.willywonka644.fintracker.category

import io.github.willywonka644.fintracker.db.FintrackerDatabase
import io.github.willywonka644.fintracker.serialization.AppJson
import io.github.willywonka644.fintracker.db.Category as CategoryRow
import kotlinx.datetime.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * The `lastModifiedAt` every seeded default category carries (#108).
 *
 * Fixed and deliberately ancient, rather than "now". A seed is not a change the
 * user made — it is the state a database starts in — and dating it to the moment
 * of creation made it **win** every merge against devices that hold the real
 * data.
 *
 * That was measured, not feared: restoring a server from empty and pushing a
 * full dataset into it left the eleven defaults stamped with the restore's own
 * date, while the incoming versions from 18.08. and 07.09. were rejected as
 * older. Harmless while the contents match; a renamed default category would
 * have been reverted on every device, during a restore, which is the worst
 * possible moment to lose something.
 *
 * With a fixed old value the seed loses every merge, which is correct: an
 * untouched default should never overwrite anything. The moment somebody edits
 * one, `saveCategories` stamps it with the current time and it wins normally.
 *
 * This is not only a server problem. The seed runs in `commonMain`, so a fresh
 * install of the app on a third device produced the same effect.
 *
 * **Why 1 and not 0:** `saveCategories` treats `0L` as "no timestamp set" and
 * replaces it with the current time. A seed dated to the epoch would therefore
 * arrive in the database tagged as brand new, and the fix would silently do
 * nothing — visible only at the next restore. The accounts and recurring-rule
 * repositories carry the same rule.
 */
const val SEED_LAST_MODIFIED_AT = 1L

class SqlCategoryRepository(private val db: FintrackerDatabase) : ICategoryRepository {

    private val queries = db.categoriesQueries

    private val _categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
    override val categoriesFlow: StateFlow<List<Category>> = _categoriesFlow.asStateFlow()

    init {
        ensureSeeded()
        _categoriesFlow.value = loadCategories()
    }

    override fun loadCategories(): List<Category> =
        queries.selectAll().executeAsList().map { it.toCategory() }

    override fun loadAllCategoriesForSync(): List<Category> =
        queries.selectAllIncludingDeleted().executeAsList().map { it.toCategory() }

    override fun reload(): List<Category> {
        _categoriesFlow.value = loadCategories()
        return _categoriesFlow.value
    }

    override fun saveCategories(categories: List<Category>) {
        val now = Clock.System.now().toEpochMilliseconds()
        db.transaction {
            // Preserve tombstones: callers pass the live in-memory list, which no longer
            // contains soft-deleted categories — re-insert them so the sync deletion survives.
            // Unlike the account repository we skip tombstones the caller also passed, so an
            // explicit resurrection wins: a category the user brings back under its own id
            // keeps that id, rather than the tombstone outliving the decision to restore it.
            val passedIds = categories.map { it.id }.toSet()
            val survivingTombstones = queries.selectAllIncludingDeleted().executeAsList()
                .filter { it.deleted && it.id !in passedIds }
            queries.deleteAll()
            categories.forEach { category ->
                queries.insert(
                    id = category.id,
                    name = category.name,
                    iconName = category.iconName,
                    color = category.color,
                    isDefault = category.isDefault,
                    lastModifiedAt = if (category.lastModifiedAt == 0L) now else category.lastModifiedAt,
                    deleted = category.deleted
                )
            }
            survivingTombstones.forEach { row ->
                queries.insert(
                    id = row.id,
                    name = row.name,
                    iconName = row.iconName,
                    color = row.color,
                    isDefault = row.isDefault,
                    lastModifiedAt = row.lastModifiedAt,
                    deleted = row.deleted
                )
            }
        }
        _categoriesFlow.value = loadCategories()
    }

    override fun softDeleteCategory(id: String, deletedAt: Long) {
        queries.softDelete(lastModifiedAt = deletedAt, id = id)
        _categoriesFlow.value = loadCategories()
    }

    override fun getRawCategoriesJson(): String? {
        val categories = loadCategories()
        return if (categories.isEmpty()) null else serializeCategories(categories)
    }

    override fun replaceCategoriesJson(json: String?): Boolean {
        return try {
            val categories = if (json == null) emptyList()
            else AppJson.decodeFromString<List<Category>>(json)
            saveCategories(categories)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun serializeCategories(categories: List<Category>): String =
        AppJson.encodeToString(categories)

    private fun ensureSeeded() {
        // Tombstones count as content: a database where the user deleted every category
        // is not a fresh one, and re-seeding it would resurrect what they threw away.
        //
        // A second step used to follow: re-add "Kreditkartenabrechnung" wherever it was
        // missing, because excludeTransfers() recognised settlements by that exact name and
        // a deleted category turned the protection off. The name carries no meaning any more
        // — BookingSource.TRANSFER does — so it is an ordinary default again, and one the
        // user may delete without it growing back.
        if (queries.selectAllIncludingDeleted().executeAsList().isEmpty()) {
            saveCategories(seedCategories())
        }
    }

    private fun settlementCategory(): Category = Category(
        id = "default-kreditkartenabrechnung",
        name = "Kreditkartenabrechnung",
        iconName = "CreditCard",
        color = 0xFF7E57C2,
        isDefault = true,
        lastModifiedAt = SEED_LAST_MODIFIED_AT,
    )

    private fun seedCategories(): List<Category> {
        return listOf(
            settlementCategory(),
            Category(id = "default-gehalt",               name = "Gehalt",               iconName = "Payments",       color = 0xFF66BB6A, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-it-equipment",          name = "IT-Equipment",         iconName = "Computer",       color = 0xFF42A5F5, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-lebensmittel",          name = "Lebensmittel",         iconName = "ShoppingCart",   color = 0xFFFFA726, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-finanzen-investment",   name = "Finanzen/Investment",  iconName = "AccountBalance", color = 0xFF26A69A, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-gesundheit",            name = "Gesundheit",           iconName = "LocalHospital",  color = 0xFFEF5350, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-wohnen",                name = "Wohnen",               iconName = "Home",           color = 0xFFFFCA28, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-reisen",                name = "Reisen",               iconName = "Flight",         color = 0xFF26C6DA, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-telekommunikation-ki",  name = "Telekommunikation/KI", iconName = "Smartphone",     color = 0xFFAB47BC, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-versicherungen",        name = "Versicherungen",       iconName = "Shield",         color = 0xFF78909C, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
            Category(id = "default-sonstiges",             name = "Sonstiges",            iconName = "MoreHoriz",      color = 0xFF8D6E63, isDefault = true, lastModifiedAt = SEED_LAST_MODIFIED_AT),
        )
    }
}

private fun CategoryRow.toCategory(): Category = Category(
    id = id,
    name = name,
    iconName = iconName,
    color = color,
    isDefault = isDefault,
    lastModifiedAt = lastModifiedAt,
    deleted = deleted,
)
