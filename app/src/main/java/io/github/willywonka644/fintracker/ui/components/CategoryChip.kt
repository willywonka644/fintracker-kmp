package io.github.willywonka644.fintracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.category.resolveIcon

/**
 * Circular icon chip for a category: 15% alpha background, full-strength icon.
 * Uses the category's stored color and resolved Material icon.
 */
@Composable
fun CategoryChip(
    category: Category,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val color = Color(category.color)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = resolveIcon(category.iconName),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
