package io.github.willywonka644.fintracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * FinTracker "Trust Blue" theme — Phase 6 redesign.
 *
 * IMPORTANT CHANGE vs. the old template:
 *   • dynamicColor is now OFF by default. The app must look identical on every
 *     device, so we no longer borrow Material You wallpaper colors.
 *   • The old Purple80/Purple40 template palette is replaced by Trust Blue.
 *
 * Access the brand extras (income/expense/gradient) through FinTheme.colors,
 * and standard roles through MaterialTheme.colorScheme as usual.
 */

private val DarkColors = darkColorScheme(
    primary            = BlueAccent,
    onPrimary          = DarkText,
    primaryContainer   = BlueAccentSoftDk,
    onPrimaryContainer = BlueAccent,
    secondary          = BlueAccent,
    onSecondary        = DarkText,
    background         = DarkBg,
    onBackground       = DarkText,
    surface            = DarkSurface,
    onSurface          = DarkText,
    surfaceVariant     = DarkBgElev,
    onSurfaceVariant   = DarkTextSub,
    surfaceContainer   = DarkSurface,
    surfaceContainerHigh = DarkSurfaceHi,
    outline            = DarkOutline,
    outlineVariant     = DarkOutline,
    error              = ExpenseDark,
    onError            = DarkText,
)

private val LightColors = lightColorScheme(
    primary            = BlueAccentLight,
    onPrimary          = LightSurface,
    primaryContainer   = BlueAccentSoftLt,
    onPrimaryContainer = BlueAccentLight,
    secondary          = BlueAccentLight,
    onSecondary        = LightSurface,
    background         = LightBg,
    onBackground       = LightText,
    surface            = LightSurface,
    onSurface          = LightText,
    surfaceVariant     = LightBgElev,
    onSurfaceVariant   = LightTextSub,
    surfaceContainer   = LightSurface,
    surfaceContainerHigh = LightSurfaceHi,
    outline            = LightOutline,
    outlineVariant     = LightOutline,
    error              = ExpenseLight,
    onError            = LightSurface,
)

@Composable
fun FinTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val finColors   = if (darkTheme) DarkFinColors else LightFinColors

    CompositionLocalProvider(LocalFinColors provides finColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            shapes      = FinShapes,
            content     = content,
        )
    }
}

/** Convenience accessor for the brand extras: `FinTheme.colors.income` etc. */
object FinTheme {
    val colors
        @Composable get() = LocalFinColors.current
}
