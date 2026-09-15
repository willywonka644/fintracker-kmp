package io.github.willywonka644.fintracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii for the Trust Blue redesign.
 *  • small  (10dp) — chips, small buttons, icon tiles
 *  • medium (16dp) — cards, inputs, list items
 *  • large  (20dp) — hero balance card, bottom sheets, FAB
 *
 * Bottom sheets use a top-only large radius (see ModalBottomSheet shape).
 */
val FinShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)
