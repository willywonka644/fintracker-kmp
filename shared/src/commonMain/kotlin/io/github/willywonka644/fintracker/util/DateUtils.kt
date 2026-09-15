package io.github.willywonka644.fintracker.util

import kotlinx.datetime.LocalDate

fun parseGermanDate(input: String): LocalDate? {
    return try {
        val parts = input.trim().split(".")
        if (parts.size != 3) return null
        val day   = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year  = parts[2].toIntOrNull() ?: return null
        if (day !in 1..31 || month !in 1..12 || year < 100) return null
        LocalDate(year, month, day)
    } catch (_: Exception) { null }
}

fun LocalDate.toGermanDateString(): String =
    "${dayOfMonth.toString().padStart(2, '0')}.${monthNumber.toString().padStart(2, '0')}.$year"
