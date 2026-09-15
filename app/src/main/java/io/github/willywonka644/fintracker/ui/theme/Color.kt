package io.github.willywonka644.fintracker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * FinTracker — "Trust Blue" palette (Phase 6 redesign).
 * Values lifted 1:1 from the approved design prototype (Direction A).
 *
 * Naming: <Role><Shade>. Dark/Light variants are assembled in Theme.kt.
 * These map onto Material 3 ColorScheme roles + the FinColors extension
 * (see FinColors.kt) for the income/expense semantics M3 has no slot for.
 */

// ── Brand / accent ──────────────────────────────────────────────────────────
val BlueAccent       = Color(0xFF6E7BFF) // primary on dark
val BlueAccentLight  = Color(0xFF5560FF) // primary on light (a touch deeper for contrast)
val BlueAccentSoftDk = Color(0x296E7BFF) // ~16% — accent container on dark
val BlueAccentSoftLt = Color(0x1A5560FF) // ~10% — accent container on light

// Gradient stops for the balance hero (use Brush.linearGradient in Compose)
val GradStart = Color(0xFF5560FF)
val GradMid   = Color(0xFF7E74FF)
val GradEnd   = Color(0xFFA99BFF)

// ── Dark theme neutrals ─────────────────────────────────────────────────────
val DarkBg       = Color(0xFF0B0E16) // app background
val DarkBgElev   = Color(0xFF0E1220) // elevated background / sheets
val DarkSurface  = Color(0xFF161A28) // cards
val DarkSurfaceHi= Color(0xFF1D2233) // inputs / nested surfaces
val DarkOutline  = Color(0x12FFFFFF) // ~7% hairline
val DarkText     = Color(0xFFFFFFFF) // primary text
val DarkTextSub  = Color(0xFF8A90A6) // secondary text
val DarkTextFaint= Color(0xFF5A6076) // tertiary / disabled

// ── Light theme neutrals ────────────────────────────────────────────────────
val LightBg       = Color(0xFFEEF0F8)
val LightBgElev   = Color(0xFFF6F7FB)
val LightSurface  = Color(0xFFFFFFFF)
val LightSurfaceHi= Color(0xFFF6F7FB)
val LightOutline  = Color(0x14141C2D) // ~8% hairline
val LightText     = Color(0xFF141828)
val LightTextSub  = Color(0xFF6B7185)
val LightTextFaint= Color(0xFF9AA0B4)

// ── Semantic finance colors (income / expense) ──────────────────────────────
val IncomeDark  = Color(0xFF34D399)
val ExpenseDark = Color(0xFFFB7185)
val IncomeLight = Color(0xFF10B981)
val ExpenseLight= Color(0xFFF43F5E)

// ── Category accents (match prototype + your Audit dots) ─────────────────────
val CatItEquip   = Color(0xFF5B8DEF)
val CatFood      = Color(0xFFFF9F45)
val CatHealth    = Color(0xFFF4564E)
val CatHobby     = Color(0xFF2DD4BF)
val CatFinance   = Color(0xFF34D399)
val CatInsurance = Color(0xFF94A3B8)
val CatTravel    = Color(0xFF22D3EE)
val CatSalary    = Color(0xFF34D399)
val CatCorrection= Color(0xFF6E7BFF)
