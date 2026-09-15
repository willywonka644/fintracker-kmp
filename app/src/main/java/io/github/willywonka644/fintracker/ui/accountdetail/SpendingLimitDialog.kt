package io.github.willywonka644.fintracker.ui.accountdetail

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.analytics.LimitKind
import io.github.willywonka644.fintracker.util.MoneyFormat

/**
 * Sets the limit of one account. Not card-only since #99: a Girokonto has a Dispo,
 * and [kind] decides which of the two the dialog calls it.
 */
@Composable
fun SpendingLimitDialog(
    kind: LimitKind,
    currentLimit: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit, // null = entfernen
) {
    var input by remember { mutableStateOf(currentLimit?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun parseOrNull(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return trimmed.replace(',', '.').toDoubleOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    LimitKind.CARD -> "Kreditkartenlimit"
                    LimitKind.OVERDRAFT -> "Dispo-Limit"
                }
            )
        },
        text = {
            ColumnLikeDialogContent(
                kind = kind,
                currentLimit = currentLimit,
                input = input,
                error = error,
                onInputChange = { input = it; error = null }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = parseOrNull(input)

                if (input.trim().isNotEmpty() && parsed == null) {
                    error = "Bitte eine gültige Zahl eingeben (z. B. 1200,00)."
                    return@TextButton
                }
                if (parsed != null && parsed < 0) {
                    error = "Das Limit muss ≥ 0 sein."
                    return@TextButton
                }

                onSave(parsed) // null = entfernen
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun ColumnLikeDialogContent(
    kind: LimitKind,
    currentLimit: Double?,
    input: String,
    error: String?,
    onInputChange: (String) -> Unit
) {
    // keeping this helper avoids nesting issues in AlertDialog "text" slot
    androidx.compose.foundation.layout.Column(
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Aktuell: " + (currentLimit?.let { MoneyFormat.currencyAbs(it) } ?: "Nicht gesetzt"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = {
                Text(
                    when (kind) {
                        LimitKind.CARD -> "Monatliches Limit (€)"
                        LimitKind.OVERDRAFT -> "Dispo-Rahmen (€)"
                    }
                )
            },
            placeholder = { Text("z. B. 1200,00") },
            isError = error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            text = "Tipp: Feld leer lassen, um das Limit zu entfernen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
