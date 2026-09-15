package io.github.willywonka644.fintracker.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.category.AVAILABLE_ICONS
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.hasCategoryNameConflict
import io.github.willywonka644.fintracker.category.resolveIcon
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryAddEditDialog(
    initial: Category?,
    existingCategories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    val isEditing = initial != null

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(initial?.iconName ?: "MoreHoriz") }
    var selectedColor by remember { mutableStateOf(initial?.color ?: COLOR_PALETTE.first()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var iconSearch by remember { mutableStateOf("") }

    // The registry holds 144 icons. Unfiltered that is twenty-odd rows of grid to
    // scroll past before the save button comes back into reach on a phone.
    val filteredIcons = remember(iconSearch) {
        if (iconSearch.isBlank()) AVAILABLE_ICONS.entries.toList()
        else AVAILABLE_ICONS.entries.filter { it.key.contains(iconSearch, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEditing) "Kategorie bearbeiten" else "Neue Kategorie")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { message -> { Text(message) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color picker
                Text("Farbe", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    COLOR_PALETTE.forEach { colorValue ->
                        val isSelected = colorValue == selectedColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colorValue))
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = colorValue }
                        )
                    }
                }

                // Icon picker
                Text("Symbol", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = iconSearch,
                    onValueChange = { iconSearch = it },
                    label = { Text("Symbole suchen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredIcons.forEach { (iconName, imageVector) ->
                        val isSelected = iconName == selectedIcon
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(selectedColor)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedIcon = iconName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = imageVector,
                                contentDescription = iconName,
                                tint = if (isSelected) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Preview
                Spacer(modifier = Modifier.height(4.dp))
                Text("Vorschau", style = MaterialTheme.typography.titleSmall)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(selectedColor)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = resolveIcon(selectedIcon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = "Name darf nicht leer sein"
                    return@TextButton
                }
                if (existingCategories.hasCategoryNameConflict(name, excludingId = initial?.id)) {
                    nameError = "Es gibt bereits eine Kategorie mit diesem Namen"
                    return@TextButton
                }
                val category = Category(
                    id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                    name = name.trim(),
                    iconName = selectedIcon,
                    color = selectedColor,
                    isDefault = initial?.isDefault ?: false,
                    lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                )
                onSave(category)
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
