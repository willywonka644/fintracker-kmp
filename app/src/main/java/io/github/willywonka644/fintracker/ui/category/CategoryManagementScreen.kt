package io.github.willywonka644.fintracker.ui.category

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.components.CategoryChip
import io.github.willywonka644.fintracker.ui.nav.GradientFAB
import io.github.willywonka644.fintracker.ui.theme.FinTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    categories: List<Category>,
    bookingCounts: Map<String, Int> = emptyMap(),
    onNavigateBack: () -> Unit,
    onAddCategory: (Category) -> Unit,
    onUpdateCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit
) {
    var showAddEditDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kategorien") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            GradientFAB(onClick = {
                categoryToEdit = null
                showAddEditDialog = true
            })
        }
    ) { innerPadding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Keine Kategorien vorhanden",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTheme.colors.textSub
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories.sortedBy { it.name.lowercase() }, key = { it.id }) { category ->
                    CategoryListItem(
                        category = category,
                        bookingCount = bookingCounts[category.name] ?: 0,
                        onEditClick = {
                            categoryToEdit = category
                            showAddEditDialog = true
                        },
                        onDeleteClick = {
                            categoryToDelete = category
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    if (showAddEditDialog) {
        CategoryAddEditDialog(
            initial = categoryToEdit,
            existingCategories = categories,
            onDismiss = {
                showAddEditDialog = false
                categoryToEdit = null
            },
            onSave = { saved ->
                if (categoryToEdit == null) {
                    onAddCategory(saved)
                } else {
                    onUpdateCategory(saved)
                }
                showAddEditDialog = false
                categoryToEdit = null
            }
        )
    }

    if (categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Kategorie löschen?") },
            text = {
                Text("Möchtest du die Kategorie \"${categoryToDelete!!.name}\" wirklich löschen?")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(categoryToDelete!!)
                    categoryToDelete = null
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryListItem(
    category: Category,
    bookingCount: Int,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val finColors = FinTheme.colors
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onEditClick, onLongClick = onDeleteClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CategoryChip(category = category, size = 40.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = if (bookingCount > 0) "$bookingCount Buchung${if (bookingCount != 1) "en" else ""}"
                    else if (category.isDefault) "Standard" else "Keine Buchungen",
                    style = MaterialTheme.typography.labelSmall,
                    color = finColors.textSub,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = finColors.textFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
