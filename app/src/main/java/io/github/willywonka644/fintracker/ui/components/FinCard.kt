package io.github.willywonka644.fintracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Branded card: medium-radius surface, 1dp outline border, 16dp inner padding.
 * Provide [onClick] to make the whole card tappable (adds a ripple).
 */
@Composable
fun FinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surface
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}
