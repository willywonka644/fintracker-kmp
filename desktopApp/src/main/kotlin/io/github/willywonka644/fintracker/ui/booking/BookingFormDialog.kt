package io.github.willywonka644.fintracker.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.booking.TransferFactory
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.booking.BookingFactory
import io.github.willywonka644.fintracker.booking.DescriptionSuggestions
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.ui.common.DesktopDatePickerField
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.parseGermanDate
import io.github.willywonka644.fintracker.util.toGermanDateString
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

/** Semantics tags for UI tests. */
object BookingFormTestTags {
    const val AMOUNT = "booking_form_amount"
    const val DESCRIPTION = "booking_form_description"
    const val SAVE = "booking_form_save"
    const val SUGGESTIONS = "booking_form_suggestions"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookingFormDialog(
    accountId: String,
    categories: List<Category>,
    existing: Booking? = null,
    onDismiss: () -> Unit,
    onSave: (Booking) -> Unit,
    /** Konten fuer das Ziel einer Umbuchung. Leer = keine Umbuchung moeglich. */
    accounts: List<Account> = emptyList(),
    /** Nur wo eine Umbuchung angelegt werden darf; fehlt sie, bleibt der Umschalter zweiteilig. */
    onSaveTransfer: ((outgoing: Booking, incoming: Booking) -> Unit)? = null,
    /**
     * Vorrat fuer die Beschreibungs-Vorschlaege (#136), einmal je Bestand gebaut.
     * Leer heisst: keine Vorschlaege, das Feld verhaelt sich wie vorher.
     */
    descriptionSuggestions: List<DescriptionSuggestions.Suggestion> = emptyList(),
) {
    val now = Clock.System.now()
    val todayStr = now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toGermanDateString()
    val finColors = FinTheme.colors

    // Sign: true = Ausgabe (negative), false = Einnahme (positive)
    var isExpense by remember {
        mutableStateOf(existing?.amount?.let { it < 0 } ?: true)
    }
    var amountText by remember {
        mutableStateOf(existing?.amount?.let { if (it == 0.0) "" else kotlin.math.abs(it).toString() } ?: "")
    }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var dateText by remember {
        mutableStateOf(
            existing?.timestamp?.let {
                Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.currentSystemDefault()).date.toGermanDateString()
            } ?: todayStr
        )
    }
    var selectedCategory by remember {
        mutableStateOf(
            categories.firstOrNull { it.id == existing?.category || it.name == existing?.category }
        )
    }
    // Welche Kategorie zuletzt aus einem Vorschlag kam. Nur die darf ein naechster
    // Vorschlag ueberschreiben — eine von Hand gewaehlte bleibt stehen.
    var categoryFromSuggestion by remember { mutableStateOf<Category?>(null) }

    // ── Umbuchung (#114, Gegenstueck zu #121 auf Android) ─────────────────────
    val transferPossible = onSaveTransfer != null && existing == null && accounts.size > 1
    var isTransfer by remember { mutableStateOf(false) }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var toAccountMenuOpen by remember { mutableStateOf(false) }
    // Zwei Datumsfelder, weil die zwei Seiten wirklich auf verschiedene Tage fallen:
    // gemessen liegen Kartenabrechnungen sechs bis elf Tage auseinander.
    var toDateText by remember { mutableStateOf(todayStr) }
    var toDateError by remember { mutableStateOf(false) }
    var transferError by remember { mutableStateOf<String?>(null) }

    var descriptionError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier.padding(28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Title ─────────────────────────────────────────────────────
                Text(
                    if (existing == null) "Buchung hinzufügen" else "Buchung bearbeiten",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                // ── Betrag + Richtung toggle ──────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ausgabe / Einnahme segmented toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                    ) {
                        ToggleTab(
                            label = "Ausgabe",
                            selected = isExpense && !isTransfer,
                            color = finColors.expense,
                            modifier = Modifier.weight(1f),
                            onClick = { isExpense = true; isTransfer = false },
                        )
                        Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
                        ToggleTab(
                            label = "Einnahme",
                            selected = !isExpense && !isTransfer,
                            color = finColors.income,
                            modifier = Modifier.weight(1f),
                            onClick = { isExpense = false; isTransfer = false },
                        )
                        if (transferPossible) {
                            Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
                            ToggleTab(
                                label = "Umbuchung",
                                selected = isTransfer,
                                // Weder gruen noch rot: eine Umbuchung ist keine Richtung,
                                // und die Richtungsfarben wuerden behaupten, sie bewege die Summen.
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                onClick = { isTransfer = true; transferError = null },
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { v ->
                            // Accept the German decimal comma like the recurring-rule
                            // and reconciliation dialogs do — the numpad produces one,
                            // and rejecting it silently swallowed the keystroke.
                            if (v.isEmpty() || v.replace(',', '.').toDoubleOrNull() != null) {
                                amountText = v; amountError = false
                            }
                        },
                        label = { Text("Betrag (€)") },
                        isError = amountError,
                        supportingText = if (amountError) ({ Text("Gültigen Betrag eingeben") }) else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag(BookingFormTestTags.AMOUNT),
                    )
                }

                // ── Beschreibung ──────────────────────────────────────────────
                // Die Vorschlaege erscheinen nur, solange der Cursor im Feld steht
                // (Nutzerentscheidung, 15.09.2026) — sonst standen sie auch da, wenn gerade niemand
                // an der Beschreibung arbeitet.
                var descriptionFocused by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; descriptionError = false },
                    label = { Text("Beschreibung") },
                    isError = descriptionError,
                    supportingText = if (descriptionError) ({ Text("Beschreibung ist erforderlich") }) else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BookingFormTestTags.DESCRIPTION)
                        .onFocusChanged { descriptionFocused = it.isFocused },
                )

                // Vorschlaege aus dem eigenen Bestand (#136).
                val suggestions = remember(descriptionSuggestions, description) {
                    DescriptionSuggestions.forQuery(descriptionSuggestions, description)
                }
                if (descriptionFocused && suggestions.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().testTag(BookingFormTestTags.SUGGESTIONS),
                    ) {
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            suggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            description = suggestion.description
                                            descriptionError = false
                                            // Bei einer Umbuchung gibt es keine Kategorie,
                                            // und eine von Hand gesetzte bleibt stehen.
                                            val mayFill = !isTransfer &&
                                                (selectedCategory == null || selectedCategory == categoryFromSuggestion)
                                            val match = suggestion.category?.let { name ->
                                                categories.firstOrNull { it.name == name }
                                            }
                                            if (mayFill && match != null) {
                                                selectedCategory = match
                                                categoryFromSuggestion = match
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = suggestion.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    // Was der Klick ins Formular schreibt, steht vorher da.
                                    // Eigene val: `category` kommt aus shared, ueber die
                                    // Modulgrenze verengt Kotlin den Typ nicht selbst.
                                    val categoryLabel = suggestion.category
                                    if (categoryLabel != null) {
                                        Text(
                                            text = categoryLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = finColors.textSub,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 140.dp),
                                        )
                                    }
                                    Text(
                                        text = "${suggestion.uses}×",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = finColors.textFaint,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Datum ─────────────────────────────────────────────────────
                DesktopDatePickerField(
                    value = dateText,
                    onValueChange = { dateText = it; dateError = false },
                    label = if (isTransfer) "Datum Abgang" else "Datum",
                    placeholder = "TT.MM.JJJJ",
                    isError = dateError,
                    supportingText = if (dateError) ({ Text("Format: TT.MM.JJJJ") }) else null,
                    selectedDate = parseGermanDate(dateText),
                    modifier = Modifier.fillMaxWidth(),
                )

                // ── Umbuchung: Zielkonto und zweites Datum statt Kategorie ────
                if (isTransfer) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Text(
                        "Auf welches Konto",
                        style = MaterialTheme.typography.labelMedium,
                        color = finColors.textSub,
                    )
                    Box {
                        OutlinedButton(
                            onClick = { toAccountMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = accounts.firstOrNull { it.id == toAccountId }?.name
                                    ?: "Konto wählen",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        DropdownMenu(
                            expanded = toAccountMenuOpen,
                            onDismissRequest = { toAccountMenuOpen = false },
                        ) {
                            accounts.filter { it.id != accountId }.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        toAccountId = acc.id
                                        toAccountMenuOpen = false
                                        transferError = null
                                    },
                                )
                            }
                        }
                    }
                    DesktopDatePickerField(
                        value = toDateText,
                        onValueChange = { toDateText = it; toDateError = false },
                        label = "Datum Zugang",
                        placeholder = "TT.MM.JJJJ",
                        isError = toDateError,
                        supportingText = if (toDateError) ({ Text("Format: TT.MM.JJJJ") }) else null,
                        selectedDate = parseGermanDate(toDateText),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (transferError != null) {
                        Text(
                            transferError!!,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }

                // ── Kategorie chip picker ─────────────────────────────────────
                // Eine Umbuchung traegt keine: sie ist kein Aufwand, und ohne Namen gibt es
                // nichts umzubenennen, was ihre Behandlung abschalten koennte.
                if (categories.isNotEmpty() && !isTransfer) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Text(
                        "Kategorie",
                        style = MaterialTheme.typography.labelMedium,
                        color = finColors.textSub,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // "Keine" chip
                        CategoryChip(
                            label = "Keine",
                            color = MaterialTheme.colorScheme.outline,
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null; categoryFromSuggestion = null },
                        )
                        categories.forEach { cat ->
                            val catColor = cat.color?.let { Color(it.toInt()) }
                                ?: MaterialTheme.colorScheme.primary
                            CategoryChip(
                                label = cat.name,
                                color = catColor,
                                selected = selectedCategory?.id == cat.id,
                                onClick = { selectedCategory = cat; categoryFromSuggestion = null },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }

                // ── Buttons ───────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }

                    // Gradient "Speichern" button
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(finColors.heroGradient)
                            .testTag(BookingFormTestTags.SAVE)
                            .clickable {
                                var valid = true
                                if (description.isBlank()) { descriptionError = true; valid = false }
                                val absAmount = amountText.replace(',', '.').toDoubleOrNull()
                                if (absAmount == null) { amountError = true; valid = false }
                                val date = parseGermanDate(dateText)
                                if (date == null) { dateError = true; valid = false }

                                if (isTransfer) {
                                    val toDate = parseGermanDate(toDateText)
                                    if (toDate == null) { toDateError = true; valid = false }
                                    val problem = TransferFactory.validate(accountId, toAccountId, absAmount)
                                    if (problem != null) { transferError = problem; valid = false }
                                    if (!valid) return@clickable

                                    val zone = TimeZone.currentSystemDefault()
                                    val (outgoing, incoming) = TransferFactory.create(
                                        idProvider = { UUID.randomUUID().toString() },
                                        groupIdProvider = { UUID.randomUUID().toString() },
                                        fromAccountId = accountId,
                                        toAccountId = toAccountId!!,
                                        amount = absAmount!!,
                                        description = description.trim(),
                                        fromDate = date!!.atStartOfDayIn(zone).toEpochMilliseconds(),
                                        toDate = toDate!!.atStartOfDayIn(zone).toEpochMilliseconds(),
                                    )
                                    onSaveTransfer?.invoke(outgoing, incoming)
                                    return@clickable
                                }

                                if (!valid) return@clickable

                                val amount = if (isExpense) -(absAmount!!) else absAmount!!
                                val timestamp = date!!.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                                val booking = BookingFactory.createManualBooking(
                                    idProvider = { existing?.id ?: UUID.randomUUID().toString() },
                                    accountId = accountId,
                                    amount = amount,
                                    description = description.trim(),
                                    category = selectedCategory?.name,
                                    timestamp = timestamp,
                                    effectiveDate = null,
                                )
                                onSave(booking)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Speichern",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleTab(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (selected) color.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) color else FinTheme.colors.textSub,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CategoryChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (selected) color.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (selected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else FinTheme.colors.textSub,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
