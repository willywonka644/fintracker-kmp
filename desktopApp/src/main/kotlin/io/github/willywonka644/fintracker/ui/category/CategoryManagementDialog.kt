package io.github.willywonka644.fintracker.ui.category

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.willywonka644.fintracker.category.AVAILABLE_ICONS
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.hasCategoryNameConflict
import io.github.willywonka644.fintracker.category.resolveIcon
import io.github.willywonka644.fintracker.ui.common.ConfirmDialog
import kotlinx.datetime.Clock

/** Predefined color palette for category color selection. */
private val COLOR_PALETTE: List<Long> = listOf(
    0xFFEF5350, // Red
    0xFFEC407A, // Pink
    0xFFAB47BC, // Purple
    0xFF7E57C2, // Deep Purple
    0xFF5C6BC0, // Indigo
    0xFF42A5F5, // Blue
    0xFF29B6F6, // Light Blue
    0xFF26C6DA, // Cyan
    0xFF26A69A, // Teal
    0xFF66BB6A, // Green
    0xFF9CCC65, // Light Green
    0xFFFFCA28, // Amber
    0xFFFFA726, // Orange
    0xFF8D6E63, // Brown
    0xFF78909C, // Blue Grey
    0xFFBDBDBD  // Grey
)

@Composable
fun CategoryManagementDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showAddForm by remember { mutableStateOf(false) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(640.dp).fillMaxHeight(0.85f)) {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Kategorien", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showAddForm = true; editingCategory = null }) {
                        Icon(Icons.Default.Add, contentDescription = "Add category")
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()

                if (showAddForm || editingCategory != null) {
                    CategoryForm(
                        existing = editingCategory,
                        allCategories = categories,
                        onCancel = { showAddForm = false; editingCategory = null },
                        onSave = { cat ->
                            onSave(cat)
                            showAddForm = false
                            editingCategory = null
                        },
                    )
                    HorizontalDivider()
                }

                val listState = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 12.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            CategoryRow(
                                category = cat,
                                onEdit = { editingCategory = it; showAddForm = false },
                                onDelete = if (cat.isDefault) null else ({ deletingCategory = it }),
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    )
                }
            }
        }
    }

    deletingCategory?.let { cat ->
        ConfirmDialog(
            title = "Kategorie löschen",
            message = "\"${cat.name}\" löschen?",
            onConfirm = { onDelete(cat); deletingCategory = null },
            onDismiss = { deletingCategory = null },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    onEdit: (Category) -> Unit,
    onDelete: ((Category) -> Unit)?,
) {
    val color = runCatching { Color(category.color) }.getOrElse { Color.Gray }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = resolveIcon(category.iconName),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            category.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (category.isDefault) {
            Text("Standard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { onEdit(category) }, modifier = Modifier.height(32.dp)) {
            Text("Bearbeiten", style = MaterialTheme.typography.labelSmall)
        }
        if (onDelete != null) {
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { onDelete(category) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.height(32.dp),
            ) {
                Text("Löschen", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryForm(
    existing: Category?,
    allCategories: List<Category>,
    onCancel: () -> Unit,
    onSave: (Category) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var selectedIcon by remember(existing) { mutableStateOf(existing?.iconName ?: "MoreHoriz") }
    var selectedColor by remember(existing) {
        mutableStateOf(existing?.color ?: COLOR_PALETTE.first())
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var iconSearch by remember { mutableStateOf("") }

    val filteredIcons = remember(iconSearch) {
        if (iconSearch.isBlank()) AVAILABLE_ICONS.entries.toList()
        else AVAILABLE_ICONS.entries.filter { it.key.contains(iconSearch, ignoreCase = true) }
    }

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(max = 500.dp)) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (existing == null) "Neue Kategorie" else "Kategorie bearbeiten",
            style = MaterialTheme.typography.titleSmall,
        )

        // ── Name ──────────────────────────────────────────────────────────────
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; nameError = null },
            label = { Text("Name *") },
            isError = nameError != null,
            supportingText = nameError?.let { message -> { Text(message) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ── Color picker ──────────────────────────────────────────────────────
        Text("Farbe", style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            COLOR_PALETTE.forEach { colorValue ->
                val isSelected = colorValue == selectedColor
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(colorValue))
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .clickable { selectedColor = colorValue },
                )
            }
        }

        // ── Icon picker ───────────────────────────────────────────────────────
        Text("Symbol", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = iconSearch,
            onValueChange = { iconSearch = it },
            label = { Text("Symbole suchen") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            filteredIcons.forEach { (iconName, imageVector) ->
                val isSelected = iconName == selectedIcon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) Color(selectedColor)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .clickable { selectedIcon = iconName },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = iconName,
                        tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // ── Preview ───────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(selectedColor)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = resolveIcon(selectedIcon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                name.ifBlank { "Vorschau" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── Actions ───────────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) { Text("Abbrechen") }
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = "Name darf nicht leer sein"
                        return@Button
                    }
                    if (allCategories.hasCategoryNameConflict(name, excludingId = existing?.id)) {
                        nameError = "Es gibt bereits eine Kategorie mit diesem Namen"
                        return@Button
                    }
                    onSave(
                        Category(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            iconName = selectedIcon,
                            color = selectedColor,
                            isDefault = existing?.isDefault ?: false,
                            lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Speichern") }
        }
    }
    VerticalScrollbar(
        adapter = rememberScrollbarAdapter(scrollState),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    )
    }
}
