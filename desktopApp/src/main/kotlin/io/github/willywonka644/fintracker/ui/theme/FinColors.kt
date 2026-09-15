package io.github.willywonka644.fintracker.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class FinColors(
    val income: Color,
    val expense: Color,
    val surfaceHi: Color,
    val textSub: Color,
    val textFaint: Color,
    val gradStart: Color,
    val gradMid: Color,
    val gradEnd: Color,
    val isDark: Boolean,
) {
    val heroGradient: Brush
        get() = Brush.linearGradient(listOf(gradStart, gradMid, gradEnd))

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
