package io.github.willywonka644.fintracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.willywonka644.fintracker.R

/**
 * Manrope type scale for the Trust Blue redesign.
 *
 * SETUP (one-time):
 *  1. Download Manrope (Regular/Medium/SemiBold/Bold/ExtraBold) from
 *     fonts.google.com/specimen/Manrope
 *  2. Drop the .ttf files into  app/src/main/res/font/  named exactly:
 *       manrope_regular.ttf, manrope_medium.ttf, manrope_semibold.ttf,
 *       manrope_bold.ttf, manrope_extrabold.ttf
 *  3. That generates R.font.* below.
 *
 *  Desktop (Compose for Desktop): res-based fonts don't exist. Instead put the
 *  ttf in desktopApp/src/main/resources/font/ and load via
 *  androidx.compose.ui.text.platform.Font("font/manrope_bold.ttf"). Keep the
 *  same TextStyle sizes so both platforms match — see the implementation plan.
 *
 * Money figures use ExtraBold + tabular numbers; set
 *   style = MaterialTheme.typography.headlineMedium.copy(
 *       fontWeight = FontWeight.ExtraBold,
 *       fontFeatureSettings = "tnum")
 * wherever you render a balance so digits don't jitter.
 */
val Manrope = FontFamily(
    Font(R.font.manrope_regular,   FontWeight.Normal),
    Font(R.font.manrope_medium,    FontWeight.Medium),
    Font(R.font.manrope_semibold,  FontWeight.SemiBold),
    Font(R.font.manrope_bold,      FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

val Typography = Typography(
    // Page titles ("Übersicht", "Buchungen")
    headlineLarge  = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    // Big balance figures
    displaySmall   = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.7).sp),
    // Section headers / card titles
    titleLarge     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold,      fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium    = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold,      fontSize = 15.sp, lineHeight = 20.sp),
    // Row titles
    titleSmall     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, lineHeight = 18.sp),
    // Body
    bodyLarge      = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal,    fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal,    fontSize = 13.5f.sp, lineHeight = 19.sp),
    // Captions / subtitles
    bodySmall      = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Medium,    fontSize = 12.sp, lineHeight = 16.sp),
    // Chips / labels / nav
    labelLarge     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold,      fontSize = 13.sp, lineHeight = 16.sp),
    labelMedium    = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold,  fontSize = 11.5f.sp, lineHeight = 14.sp),
    labelSmall     = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold,      fontSize = 9.5f.sp, lineHeight = 12.sp, letterSpacing = 0.4.sp),
)
