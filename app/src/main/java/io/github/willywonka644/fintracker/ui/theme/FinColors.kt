package io.github.willywonka644.fintracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Material 3 has no semantic slot for "income green / expense red", nor for the
 * extra surface tiers and the brand gradient the FinTracker redesign uses.
 * FinColors carries those as a theme extension, provided by FinTrackerTheme and
 * read anywhere via `FinTheme.colors`.
 *
 * Usage in a composable:
 *   val amountColor = if (booking.amount < 0) FinTheme.colors.expense
 *                     else FinTheme.colors.income
 *   Box(Modifier.background(FinTheme.colors.heroGradient))
 */
@Immutable
data class FinColors(
    val income: Color,
    val expense: Color,
    val surfaceHi: Color,   // inputs / nested surfaces
    val textSub: Color,     // secondary text
    val textFaint: Color,   // tertiary / disabled / axis labels
    val gradStart: Color,
    val gradMid: Color,
    val gradEnd: Color,
    val isDark: Boolean,
) {
    /** Balance-hero gradient (135°, matches the prototype). */
    val heroGradient: Brush
        get() = Brush.linearGradient(listOf(gradStart, gradMid, gradEnd))

    /** Resolve a category accent by its display name (extend as needed). */
    fun category(name: String): Color = when (name) {
        "IT-Equipment"        -> CatItEquip
        "Lebensmittel"        -> CatFood
        "Gesundheit"          -> CatHealth
        "Hobbys"              -> CatHobby
        "Finanzen/Investment" -> CatFinance
        "Versicherungen"      -> CatInsurance
        "Reisen"              -> CatTravel
        "Gehalt"              -> CatSalary
        "Korrektur"           -> CatCorrection
        else                  -> if (isDark) BlueAccent else BlueAccentLight
    }
}

val DarkFinColors = FinColors(
    income = IncomeDark, expense = ExpenseDark,
    surfaceHi = DarkSurfaceHi, textSub = DarkTextSub, textFaint = DarkTextFaint,
    gradStart = GradStart, gradMid = GradMid, gradEnd = GradEnd, isDark = true,
)

val LightFinColors = FinColors(
    income = IncomeLight, expense = ExpenseLight,
    surfaceHi = LightSurfaceHi, textSub = LightTextSub, textFaint = LightTextFaint,
    gradStart = GradStart, gradMid = GradMid, gradEnd = GradEnd, isDark = false,
)

val LocalFinColors = staticCompositionLocalOf { DarkFinColors }
