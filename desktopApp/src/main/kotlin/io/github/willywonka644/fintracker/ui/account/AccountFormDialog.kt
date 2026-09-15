package io.github.willywonka644.fintracker.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.analytics.LimitKind
import io.github.willywonka644.fintracker.analytics.limitKindFor
import java.util.UUID
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFormDialog(
    existing: Account? = null,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.GIRO) }
    var billingStartDay by remember { mutableStateOf(existing?.billingStartDay?.toString() ?: "") }
    var spendingLimit by remember { mutableStateOf(existing?.spendingLimit?.toString() ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.width(440.dp)) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    if (existing == null) "Konto hinzufügen" else "Konto bearbeiten",
                    style = MaterialTheme.typography.titleLarge,
                )

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Name *") },
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("Name ist erforderlich") }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Type dropdown
                ExposedDropdownMenuBox(
                    expanded = typeMenuExpanded,
                    onExpandedChange = { typeMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = type.displayName(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Typ") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false },
                    ) {
                        AccountType.entries.forEach { accountType ->
                            DropdownMenuItem(
                                text = { Text(accountType.displayName()) },
                                onClick = {
                                    type = accountType
                                    typeMenuExpanded = false
                                    // The billing day belongs to the card and goes with
                                    // it; the limit does not (#99) — a Girokonto keeps
                                    // what was typed, as its Dispo.
                                    if (accountType != AccountType.CREDIT_CARD) {
                                        billingStartDay = ""
                                    }
                                },
                            )
                        }
                    }
                }

                // Credit card only: the billing day
                if (type == AccountType.CREDIT_CARD) {
                    OutlinedTextField(
                        value = billingStartDay,
                        onValueChange = { v ->
                            // 1..28 only: the billing-cycle math requires a day that exists
                            // in every month (currentBillingCyclePeriod crashes above 28).
                            if (v.isEmpty() || (v.toIntOrNull() != null && v.toInt() in 1..28)) {
                                billingStartDay = v
                            }
                        },
                        label = { Text("Abrechnungstag (1–28)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Every account can carry a limit (#99). This used to be inside the
                // credit-card block and, worse, was thrown away on save for anything
                // else — the entry looked as though it had worked.
                OutlinedTextField(
                    value = spendingLimit,
                    onValueChange = { v ->
                        if (v.isEmpty() || v.toDoubleOrNull() != null) spendingLimit = v
                    },
                    label = {
                        Text(
                            when (limitKindFor(type)) {
                                LimitKind.CARD -> "Kreditlimit (€)"
                                LimitKind.OVERDRAFT -> "Dispo-Rahmen (€)"
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = true
                                return@Button
                            }
                            val account = Account(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                type = type,
                                billingStartDay = if (type == AccountType.CREDIT_CARD) {
                                    billingStartDay.toIntOrNull()?.takeIf { it in 1..28 }
                                } else null,
                                spendingLimit = spendingLimit.toDoubleOrNull(),
                                lastModifiedAt = Clock.System.now().toEpochMilliseconds()
                            )
                            onSave(account)
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Text("Speichern")
                    }
                }
            }
        }
    }
}
