package io.github.willywonka644.fintracker.category

import kotlinx.coroutines.flow.StateFlow

interface ICategoryRepository {
    val categoriesFlow: StateFlow<List<Category>>
    fun loadCategories(): List<Category>
    /** Includes tombstones — for sync only, never for the pickers. */
    fun loadAllCategoriesForSync(): List<Category>
    fun reload(): List<Category>
    fun saveCategories(categories: List<Category>)
    fun softDeleteCategory(id: String, deletedAt: Long)
    fun getRawCategoriesJson(): String?
    fun replaceCategoriesJson(json: String?): Boolean
    fun serializeCategories(categories: List<Category>): String
}
