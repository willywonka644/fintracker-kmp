package io.github.willywonka644.fintracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

private val ManropeFamily = FontFamily(
    Font("font/manrope_regular.ttf",   FontWeight.Normal,    FontStyle.Normal),
    Font("font/manrope_medium.ttf",    FontWeight.Medium,    FontStyle.Normal),
    Font("font/manrope_semibold.ttf",  FontWeight.SemiBold,  FontStyle.Normal),
    Font("font/manrope_bold.ttf",      FontWeight.Bold,      FontStyle.Normal),
    Font("font/manrope_extrabold.ttf", FontWeight.ExtraBold, FontStyle.Normal),
)

val Typography = Typography(
    headlineLarge  = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 25.sp,    lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp,    lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    displaySmall   = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp,    lineHeight = 40.sp, letterSpacing = (-0.7).sp),
    titleLarge     = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold,      fontSize = 18.sp,    lineHeight = 24.sp, letterSpacing = (-0.2).sp),
    titleMedium    = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold,      fontSize = 15.sp,    lineHeight = 20.sp),
    titleSmall     = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold,  fontSize = 14.sp,    lineHeight = 18.sp),
    bodyLarge      = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Normal,    fontSize = 15.sp,    lineHeight = 22.sp),
    bodyMedium     = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Normal,    fontSize = 13.5f.sp, lineHeight = 19.sp),
    bodySmall      = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Medium,    fontSize = 12.sp,    lineHeight = 16.sp),
    labelLarge     = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold,      fontSize = 13.sp,    lineHeight = 16.sp),
    labelMedium    = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.SemiBold,  fontSize = 11.5f.sp, lineHeight = 14.sp),
    labelSmall     = TextStyle(fontFamily = ManropeFamily, fontWeight = FontWeight.Bold,      fontSize = 9.5f.sp,  lineHeight = 12.sp, letterSpacing = 0.4.sp),
)
