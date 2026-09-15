package io.github.willywonka644.fintracker.ui.booking

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.AccountType
import io.github.willywonka644.fintracker.Booking
import io.github.willywonka644.fintracker.booking.DescriptionSuggestions
import io.github.willywonka644.fintracker.BookingSource
import io.github.willywonka644.fintracker.booking.TransferFactory
import io.github.willywonka644.fintracker.attachment.AttachmentStorage
import io.github.willywonka644.fintracker.category.Category
import io.github.willywonka644.fintracker.form.FieldState
import io.github.willywonka644.fintracker.form.hasContentWorthKeeping
import io.github.willywonka644.fintracker.ui.category.CategoryAddEditDialog
import io.github.willywonka644.fintracker.ui.components.CategoryChip
import io.github.willywonka644.fintracker.ui.components.MoneyText
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class SheetStep { ACCOUNT, FORM }

private val MIN_DATE_MILLIS = java.time.LocalDate.of(2026, 1, 1)
    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private val DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookingSheet(
    accounts: List<Account>,
    accountBalances: Map<String, Double>,
    categories: List<Category>,
    initialAccountId: String? = null,
    initialBooking: Booking? = null,
    prefillAmount: String? = null,
    prefillDescription: String? = null,
    /**
     * Vorrat fuer die Beschreibungs-Vorschlaege (#136), einmal je Bestand gebaut und
     * nicht je Tastendruck. Leer heisst: keine Vorschlaege, das Feld verhaelt sich wie
     * vorher.
     */
    descriptionSuggestions: List<DescriptionSuggestions.Suggestion> = emptyList(),
    onDismiss: () -> Unit,
    onSaveOneTime: (accountId: String, amount: Double, description: String, category: String?, timestamp: Long, effectiveDate: Long?, attachmentPath: String?) -> Unit,
    /** The other half when an existing transfer is being edited (#121). */
    initialTransferCounterpart: Booking? = null,
    /** Given only where a transfer may be created; its absence hides the Umbuchung tile. */
    onSaveTransfer: ((fromAccountId: String, toAccountId: String, amount: Double, description: String, fromDate: Long, toDate: Long) -> Unit)? = null,
    /** Rewrites both halves of an existing transfer under their own ids. */
    onUpdateTransfer: ((outgoingId: String, incomingId: String, fromAccountId: String, toAccountId: String, amount: Double, description: String, fromDate: Long, toDate: Long) -> Unit)? = null,
    onDeleteBooking: (() -> Unit)? = null,
    onAddCategory: (Category) -> Unit = {},
    onNavigateToImageViewer: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val finColors = FinTheme.colors
    val context = LocalContext.current
    val isEditMode = initialBooking != null
    val hasPrefill = !isEditMode && (prefillAmount != null || prefillDescription != null)

    var step by remember {
        mutableStateOf(
            when {
                isEditMode -> SheetStep.FORM
                hasPrefill -> SheetStep.FORM
                initialAccountId != null -> SheetStep.FORM
                else -> SheetStep.ACCOUNT
            }
        )
    }
    // ── Umbuchung (#121) ─────────────────────────────────────────────────────
    // Editing works on the pair, not on the row that was tapped: whichever side was
    // opened, the form always shows the outgoing account on the left and the incoming
    // on the right, so "von" and "nach" mean the same thing every time.
    val editingTransfer = initialBooking?.source == BookingSource.TRANSFER &&
        initialTransferCounterpart != null
    val transferSides = listOfNotNull(initialBooking, initialTransferCounterpart)
    val transferOut = transferSides.firstOrNull { it.amount < 0 }
    val transferIn = transferSides.firstOrNull { it.amount > 0 }
    var isTransfer by remember { mutableStateOf(editingTransfer) }

    var selectedAccountId by remember {
        mutableStateOf(
            if (editingTransfer) transferOut?.accountId
            else initialAccountId ?: initialBooking?.accountId
        )
    }
    var toAccountId by remember { mutableStateOf(transferIn?.accountId) }
    var showToAccountMenu by remember { mutableStateOf(false) }
    var transferError by remember { mutableStateOf<String?>(null) }

    // Form state
    val prefillIsExpense = prefillAmount?.trim()?.startsWith("-") ?: true
    var isExpense by remember { mutableStateOf(initialBooking?.let { it.amount < 0 } ?: prefillIsExpense) }
    var amountInput by remember {
        mutableStateOf(
            (if (editingTransfer) transferOut else initialBooking)?.let { abs(it.amount).toString() }
                ?: prefillAmount?.trim()?.removePrefix("+")?.removePrefix("-")?.trim()
                ?: ""
        )
    }
    var description by remember { mutableStateOf(initialBooking?.description ?: prefillDescription ?: "") }
    // Welche Kategorie zuletzt aus einem Vorschlag kam. Nur die darf ein naechster
    // Vorschlag ueberschreiben — eine von Hand gewaehlte Kategorie bleibt stehen.
    var categoryFromSuggestion by remember { mutableStateOf<String?>(null) }
    var selectedCategoryName by remember {
        // No silent default. The picker's first entry is "Kreditkartenabrechnung",
        // and a booking nobody categorised would be filed under it — wrong in the
        // category breakdown, and wrong in a way nobody sees, because the field
        // looks answered. It once did worse: the old name check dropped exactly that
        // category from every figure, so such a booking moved the balance and then
        // vanished. Empty means the picker shows its "Kategorie wählen" placeholder.
        mutableStateOf(initialBooking?.category ?: "")
    }
    fun Booking.localDate(): java.time.LocalDate =
        Instant.ofEpochMilli(effectiveDate ?: timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    val initDate = (if (editingTransfer) transferOut else initialBooking)?.localDate()
        ?: java.time.LocalDate.now()
    var selectedDate by remember { mutableStateOf(initDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    // The two sides keep their own dates on purpose — a card settlement really does land
    // on the card and on the current account days apart (measured: six to eleven).
    var toDate by remember { mutableStateOf(transferIn?.localDate() ?: initDate) }
    var showToDatePicker by remember { mutableStateOf(false) }


    // Attachment
    var attachmentPath by remember { mutableStateOf(initialBooking?.attachmentPath) }
    var newlyAddedPath by remember { mutableStateOf<String?>(null) }
    val tempId = remember { initialBooking?.id ?: "temp_${System.currentTimeMillis()}" }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = AttachmentStorage.copyImageToStorage(context, uri, tempId)
            if (path != null) {
                if (newlyAddedPath != null) AttachmentStorage.deleteAttachment(newlyAddedPath)
                attachmentPath = path
                newlyAddedPath = path
            }
        }
    }

    // Validation errors
    var amountError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // ── Schutz vor versehentlichem Verwerfen ─────────────────────────────────
    // Tippen auf den abgedunkelten Rand oder ein Wischen nach unten löst
    // onDismissRequest aus. Ohne Zwischenstufe war damit alles Eingetippte weg —
    // bei einer Ratenzahlung samt Raten und Intervall.
    //
    // Der Ausgangszustand wird beim ersten Zeichnen festgehalten, damit auch das
    // Bearbeiten einer bestehenden Buchung erkennt, ob wirklich etwas geändert
    // wurde. Wer den Sheet nur aus Versehen geöffnet hat, soll ihn ohne Rückfrage
    // wieder loswerden.
    // Jedes Feld, das der Nutzer setzen kann, gehört hier hinein — was fehlt,
    // verschwindet beim Wegwischen ohne Rückfrage. Kategorie und Datum fehlten
    // anfangs, eine ausgewählte Kategorie war damit lautlos weg.
    val baselineAmount = remember { amountInput }
    val baselineDescription = remember { description }
    val baselineCategory = remember { selectedCategoryName }
    val baselineDate = remember { selectedDate.toString() }
    val baselineToAccount = remember { toAccountId.orEmpty() }
    val baselineToDate = remember { toDate.toString() }

    // Muss beim Schließen gerechnet werden, nicht währenddessen aufgehoben.
    // Material merkt sich für die Wischgeste einen Rückruf aus dem Aufbau des
    // Sheets. Ein vorab berechneter Boolean wird darin als Wert eingefroren und
    // bleibt für immer der von damals — also "leer". Die Felder daneben sind
    // MutableState und werden live gelesen, weshalb der Fehler beim Tippen auf
    // den Rand nicht auftrat und nur das Wischen die Eingaben verlor.
    fun hasUnsavedInput(): Boolean = hasContentWorthKeeping(
        openedWithScannedData = hasPrefill,
        hasNewAttachment = newlyAddedPath != null,
        fields = listOf(
            FieldState(amountInput, baselineAmount),
            FieldState(description, baselineDescription),
            FieldState(selectedCategoryName, baselineCategory),
            FieldState(selectedDate.toString(), baselineDate),
            FieldState(toAccountId.orEmpty(), baselineToAccount),
            FieldState(toDate.toString(), baselineToDate),
        ),
    )

    var showDiscardConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun dismissAndCleanup() {
        if (newlyAddedPath != null) AttachmentStorage.deleteAttachment(newlyAddedPath)
        onDismiss()
    }

    /** Material blendet das Sheet vor onDismissRequest bereits aus — zurückholen. */
    fun keepEditing() {
        showDiscardConfirm = false
        scope.launch { sheetState.show() }
    }

    fun millisOf(date: java.time.LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun validateAndSaveTransfer() {
        val absAmount = amountInput.replace(',', '.').toDoubleOrNull()
        TransferFactory.validate(selectedAccountId, toAccountId, absAmount)?.let {
            transferError = it
            return
        }
        transferError = null
        amountError = null
        val from = selectedAccountId ?: return
        val to = toAccountId ?: return
        val amount = absAmount ?: return
        val text = description.trim()
        if (editingTransfer) {
            val outId = transferOut?.id ?: return
            val inId = transferIn?.id ?: return
            onUpdateTransfer?.invoke(outId, inId, from, to, amount, text, millisOf(selectedDate), millisOf(toDate))
        } else {
            onSaveTransfer?.invoke(from, to, amount, text, millisOf(selectedDate), millisOf(toDate))
        }
        dismissAndCleanup()
    }

    fun validateAndSave() {
        if (isTransfer) {
            validateAndSaveTransfer()
            return
        }
        val absAmount = amountInput.replace(',', '.').toDoubleOrNull()
        if (absAmount == null || absAmount <= 0) {
            amountError = "Bitte einen gültigen Betrag eingeben"
            return
        }
        amountError = null
        val signedAmount = if (isExpense) -absAmount else absAmount
        val accountId = selectedAccountId ?: return
        val catName = selectedCategoryName.ifBlank { null }
        val ts = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // If the booking carries an effectiveDate (installments, CSV imports), it must
        // follow the newly chosen date — all filtering prefers effectiveDate, so keeping
        // the old value would pin the booking to its old day no matter what was edited.
        val effDate = if (isEditMode && initialBooking?.effectiveDate != null) ts else null
        onSaveOneTime(accountId, signedAmount, description.trim(), catName, ts, effDate, attachmentPath)
        dismissAndCleanup()
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (hasUnsavedInput()) showDiscardConfirm = true else dismissAndCleanup()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            when (step) {
                SheetStep.ACCOUNT -> AccountStep(
                    accounts = accounts,
                    accountBalances = accountBalances,
                    finColors = finColors,
                    onSelect = { accId ->
                        selectedAccountId = accId
                        step = SheetStep.FORM
                    },
                )

                SheetStep.FORM -> FormContent(
                    isEditMode = isEditMode,
                    accountName = accounts.find { it.id == selectedAccountId }?.name ?: "",
                    categories = categories,
                    isExpense = isExpense,
                    // An existing transfer stays one: switching it to Ausgabe would leave its
                    // other half behind as an orphan, which is the half transfer this type
                    // exists to prevent. The tiles stay visible, they just do not react.
                    onIsExpenseChange = {
                        if (!editingTransfer) {
                            isExpense = it
                            isTransfer = false
                            transferError = null
                        }
                    },
                    accounts = accounts,
                    isTransfer = isTransfer,
                    // An existing booking cannot change into a transfer or out of one — that
                    // would mean creating or orphaning a second row behind the user's back.
                    onTransferSelected = when {
                        editingTransfer -> ({})
                        onSaveTransfer != null && !isEditMode -> ({ isTransfer = true; amountError = null })
                        else -> null
                    },
                    toAccountId = toAccountId,
                    onToAccountMenuOpen = { showToAccountMenu = true },
                    showToAccountMenu = showToAccountMenu,
                    onToAccountMenuDismiss = { showToAccountMenu = false },
                    onToAccountChange = { toAccountId = it; showToAccountMenu = false; transferError = null },
                    toDate = toDate,
                    onToDatePickerOpen = { showToDatePicker = true },
                    transferError = transferError,
                    amountInput = amountInput,
                    onAmountChange = { amountInput = it; amountError = null },
                    amountError = amountError,
                    description = description,
                    onDescriptionChange = { description = it },
                    suggestions = remember(descriptionSuggestions, description) {
                        DescriptionSuggestions.forQuery(descriptionSuggestions, description)
                    },
                    onSuggestionPicked = { suggestion ->
                        description = suggestion.description
                        // Bei einer Umbuchung gibt es keine Kategorie, und eine von Hand
                        // gesetzte wird nicht ueberschrieben.
                        val mayFill = !isTransfer &&
                            (selectedCategoryName.isBlank() || selectedCategoryName == categoryFromSuggestion)
                        // Eigene val: `category` kommt aus shared, ueber die Modulgrenze
                        // verengt Kotlin den Typ nicht selbst.
                        //
                        // Nur ein Name, den es als Kategorie wirklich gibt. `booking.category`
                        // ist ein Name, keine Id: wird eine Kategorie umbenannt, tragen alte
                        // Buchungen den alten Namen weiter, und ein Vorschlag koennte ihn
                        // zurueckschreiben. Der Waehler findet ihn dann nicht und zeigt
                        // "Kategorie waehlen" — gespeichert wuerde er trotzdem. Genau der
                        // Fehler, vor dem der Kommentar an `selectedCategoryName` warnt:
                        // falsch, und falsch auf eine Art, die niemand sieht.
                        val fromSuggestion = suggestion.category
                            ?.takeIf { name -> categories.any { it.name == name } }
                        if (mayFill && fromSuggestion != null) {
                            selectedCategoryName = fromSuggestion
                            categoryFromSuggestion = fromSuggestion
                        }
                    },
                    selectedCategoryName = selectedCategoryName,
                    onCategoryChange = { selectedCategoryName = it; categoryFromSuggestion = null },
                    selectedDate = selectedDate,
                    onDatePickerOpen = { showDatePicker = true },
                    attachmentPath = attachmentPath,
                    onAddAttachment = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemoveAttachment = {
                        if (attachmentPath == newlyAddedPath) {
                            AttachmentStorage.deleteAttachment(newlyAddedPath)
                            newlyAddedPath = null
                        }
                        attachmentPath = null
                    },
                    onViewAttachment = { if (attachmentPath != null) onNavigateToImageViewer(attachmentPath!!) },
                    onSave = { validateAndSave() },
                    onDelete = if (onDeleteBooking != null) { { showDeleteConfirm = true } } else null,
                    onBack = if (!isEditMode) { { step = SheetStep.ACCOUNT } } else null,
                    onAddCategory = onAddCategory,
                    finColors = finColors,
                )
            }
        }
    }

    if (showDatePicker) {
        val initMillis = selectedDate
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = initMillis,
            yearRange = 2026..2100,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= MIN_DATE_MILLIS
                override fun isSelectableYear(year: Int) = year >= 2026
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") } }
        ) { DatePicker(state = dpState) }
    }

    if (showToDatePicker) {
        val initMillis = toDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = initMillis,
            yearRange = 2026..2100,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= MIN_DATE_MILLIS
                override fun isSelectableYear(year: Int) = year >= 2026
            }
        )
        DatePickerDialog(
            onDismissRequest = { showToDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        toDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showToDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToDatePicker = false }) { Text("Abbrechen") } }
        ) { DatePicker(state = dpState) }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            // Auch das Wegtippen dieses Dialogs bedeutet "weiter bearbeiten" —
            // ein zweiter Fehltipp darf die Eingaben nicht doch noch kosten.
            onDismissRequest = { keepEditing() },
            title = { Text("Eingaben verwerfen?") },
            text = { Text("Was du bisher eingegeben hast, geht dabei verloren.") },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; dismissAndCleanup() }) {
                    Text("Verwerfen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { keepEditing() }) { Text("Weiter bearbeiten") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (editingTransfer) "Umbuchung löschen?" else "Buchung löschen?") },
            text = {
                Text(
                    if (editingTransfer) {
                        "Eine Umbuchung besteht aus zwei Buchungen. Beide Seiten werden gelöscht — " +
                            "der Abgang und der Zugang."
                    } else {
                        "Soll diese Buchung wirklich gelöscht werden?"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { onDeleteBooking?.invoke(); showDeleteConfirm = false }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

// ── Step 1: Account selection ────────────────────────────────────────────────

@Composable
private fun AccountStep(
    accounts: List<Account>,
    accountBalances: Map<String, Double>,
    finColors: io.github.willywonka644.fintracker.ui.theme.FinColors,
    onSelect: (String) -> Unit,
) {
    Text(
        text = "Konto wählen",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        accounts.forEach { account ->
            val balance = accountBalances[account.id] ?: 0.0
            val (icon, iconColor) = when (account.type) {
                AccountType.GIRO -> Icons.Outlined.AccountBalance to MaterialTheme.colorScheme.primary
                AccountType.CREDIT_CARD -> Icons.Filled.CreditCard to finColors.expense
                AccountType.SPARKONTO -> Icons.Filled.Savings to finColors.income
                AccountType.TAGESGELD -> Icons.Filled.Payments to MaterialTheme.colorScheme.primary
            }
            AccountRow(
                account = account,
                icon = icon,
                iconColor = iconColor,
                balance = balance,
                onClick = { onSelect(account.id) },
            )
        }
    }
}

@Composable
private fun AccountRow(
    account: Account,
    icon: ImageVector,
    iconColor: Color,
    balance: Double,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = when (account.type) {
                    AccountType.GIRO -> "Girokonto"
                    AccountType.CREDIT_CARD -> "Kreditkarte"
                    AccountType.SPARKONTO -> "Sparkonto"
                    AccountType.TAGESGELD -> "Tagesgeldkonto"
                },
                style = MaterialTheme.typography.bodySmall,
                color = FinTheme.colors.textSub,
            )
        }
        MoneyText(amount = balance, style = MaterialTheme.typography.bodyMedium)
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = FinTheme.colors.textFaint,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Step 3: Booking form ─────────────────────────────────────────────────────

@Composable
private fun FormContent(
    isEditMode: Boolean,
    accountName: String,
    categories: List<Category>,
    isExpense: Boolean,
    onIsExpenseChange: (Boolean) -> Unit,
    accounts: List<Account>,
    isTransfer: Boolean,
    onTransferSelected: (() -> Unit)?,
    toAccountId: String?,
    onToAccountMenuOpen: () -> Unit,
    showToAccountMenu: Boolean,
    onToAccountMenuDismiss: () -> Unit,
    onToAccountChange: (String) -> Unit,
    toDate: java.time.LocalDate,
    onToDatePickerOpen: () -> Unit,
    transferError: String?,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    amountError: String?,
    description: String,
    onDescriptionChange: (String) -> Unit,
    suggestions: List<DescriptionSuggestions.Suggestion>,
    onSuggestionPicked: (DescriptionSuggestions.Suggestion) -> Unit,
    selectedCategoryName: String,
    onCategoryChange: (String) -> Unit,
    selectedDate: java.time.LocalDate,
    onDatePickerOpen: () -> Unit,
    attachmentPath: String?,
    onAddAttachment: () -> Unit,
    onRemoveAttachment: () -> Unit,
    onViewAttachment: () -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onBack: (() -> Unit)?,
    onAddCategory: (Category) -> Unit,
    finColors: io.github.willywonka644.fintracker.ui.theme.FinColors,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = finColors.surfaceHi,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
    )
    val fieldShape = MaterialTheme.shapes.medium

    // Header row
    if (isEditMode) {
        Text(
            text = "Buchung bearbeiten",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp),
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = finColors.textSub)
                }
                Spacer(Modifier.width(4.dp))
            }
            Column {
                Text(
                    text = "Neue Buchung",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (accountName.isNotBlank()) {
                    Text(
                        text = accountName,
                        style = MaterialTheme.typography.bodySmall,
                        color = finColors.textSub,
                    )
                }
            }
        }
    }

    // Direction toggle (Ausgabe / Einnahme / Umbuchung)
    DirectionToggle(
        isTransfer = isTransfer,
        onTransferSelected = onTransferSelected,
        isExpense = isExpense,
        onIsExpenseChange = onIsExpenseChange,
        finColors = finColors,
    )

    Spacer(Modifier.height(12.dp))

    // Amount field
    OutlinedTextField(
        value = amountInput,
        onValueChange = onAmountChange,
        label = { Text("Betrag") },
        suffix = { Text("€") },
        isError = amountError != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = fieldShape,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )
    if (amountError != null) {
        Text(amountError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
    }

    Spacer(Modifier.height(10.dp))

    // Description
    // Die Vorschlaege erscheinen nur, solange der Cursor im Feld steht (Nutzerentscheidung,
    // 15.09.2026). Sonst schoben sie Kategoriewaehler und Speichern-Knopf auch dann
    // nach unten, wenn gerade niemand an der Beschreibung arbeitet.
    var descriptionFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        label = { Text("Beschreibung (optional)") },
        singleLine = true,
        shape = fieldShape,
        colors = fieldColors,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { descriptionFocused = it.isFocused },
    )

    // Vorschlaege aus dem eigenen Bestand (#136). Bewusst als Zeilen unter dem Feld und
    // nicht als aufklappendes Menue: das Sheet scrollt selbst, und ein Popup darin hat
    // sich bei der Kategorieauswahl schon einmal mit dem Scrollen gestritten.
    if (descriptionFocused && suggestions.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionPicked(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = finColors.textFaint,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = suggestion.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        // Die Kategorie steht hier, bevor sie gesetzt wird: was der Tipp
                        // ins Formular schreibt, ist vorher zu lesen und keine Ueberraschung.
                        // Eigene val: `category` kommt aus shared, ueber die Modulgrenze
                        // verengt Kotlin den Typ nicht selbst.
                        val categoryLabel = suggestion.category
                        if (categoryLabel != null) {
                            Text(
                                text = categoryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = finColors.textSub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 120.dp),
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

    Spacer(Modifier.height(10.dp))

    if (isTransfer) {
        // Where the money goes. No category: a transfer is not spending, and with no
        // category there is no name anyone could rename to switch its handling off.
        Text(
            text = "Auf welches Konto",
            style = MaterialTheme.typography.labelMedium,
            color = finColors.textSub,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onToAccountMenuOpen,
                shape = fieldShape,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Text(
                    text = accounts.firstOrNull { it.id == toAccountId }?.name ?: "Konto wählen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (toAccountId == null) finColors.textSub else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = showToAccountMenu, onDismissRequest = onToAccountMenuDismiss) {
                accounts.forEach { acc ->
                    DropdownMenuItem(
                        text = { Text(acc.name) },
                        onClick = { onToAccountChange(acc.id) },
                    )
                }
            }
        }
        if (transferError != null) {
            Text(
                text = transferError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    } else {
        // Category chip picker
        Text(
            text = "Kategorie",
            style = MaterialTheme.typography.labelMedium,
            color = finColors.textSub,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        CategoryChipPicker(
            categories = categories,
            selectedName = selectedCategoryName,
            onSelect = onCategoryChange,
            onAddCategory = onAddCategory,
        )
    }

    Spacer(Modifier.height(10.dp))

    // Date. A transfer gets one per side — the two really do fall on different days.
    OutlinedTextField(
        value = selectedDate.format(DATE_FMT),
        onValueChange = {},
        label = { Text(if (isTransfer) "Datum Abgang" else "Datum") },
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onDatePickerOpen) {
                Icon(Icons.Default.DateRange, "Datum wählen")
            }
        },
        shape = fieldShape,
        colors = fieldColors,
        modifier = Modifier.fillMaxWidth(),
    )

    if (isTransfer) {
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = toDate.format(DATE_FMT),
            onValueChange = {},
            label = { Text("Datum Zugang") },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onToDatePickerOpen) {
                    Icon(Icons.Default.DateRange, "Datum wählen")
                }
            },
            shape = fieldShape,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(10.dp))

    // Attachment
    if (attachmentPath != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(fieldShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.AttachFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(
                text = "Beleg angehängt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onViewAttachment() },
            )
            TextButton(onClick = onRemoveAttachment, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Entfernen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(fieldShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onAddAttachment)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.AttachFile, null, tint = finColors.textSub, modifier = Modifier.size(18.dp))
            Text("Beleg anhängen", style = MaterialTheme.typography.bodyMedium, color = finColors.textSub)
        }
    }

    Spacer(Modifier.height(20.dp))

    // Save button
    GradientButton(
        text = "Buchung speichern",
        onClick = onSave,
    )

    // Delete (edit mode only)
    if (onDelete != null) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            TextButton(onClick = onDelete) {
                Text("Buchung löschen", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Shared sub-composables ───────────────────────────────────────────────────

/**
 * Ausgabe / Einnahme, and optionally Umbuchung.
 *
 * The third tile only appears when [onTransferSelected] is given: an instalment plan and a
 * standing order use this same control, and neither of them can be a transfer. Passing
 * nothing keeps the two-tile control those two dialogs have always had.
 */
@Composable
internal fun DirectionToggle(
    isExpense: Boolean,
    onIsExpenseChange: (Boolean) -> Unit,
    finColors: io.github.willywonka644.fintracker.ui.theme.FinColors,
    isTransfer: Boolean = false,
    onTransferSelected: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Ausgabe
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(if (isExpense && !isTransfer) finColors.expense.copy(alpha = 0.15f) else Color.Transparent)
                .clickable { onIsExpenseChange(true) }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Ausgabe",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isExpense && !isTransfer) FontWeight.Bold else FontWeight.Normal,
                color = if (isExpense && !isTransfer) finColors.expense else finColors.textSub,
            )
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(48.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        )
        // Einnahme
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(if (!isExpense && !isTransfer) finColors.income.copy(alpha = 0.15f) else Color.Transparent)
                .clickable { onIsExpenseChange(false) }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Einnahme",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (!isExpense && !isTransfer) FontWeight.Bold else FontWeight.Normal,
                color = if (!isExpense && !isTransfer) finColors.income else finColors.textSub,
            )
        }
        if (onTransferSelected != null) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        if (isTransfer) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .clickable { onTransferSelected() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Umbuchung",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isTransfer) FontWeight.Bold else FontWeight.Normal,
                    // Neither green nor red: a transfer is not a direction, and using
                    // either colour would claim it moves the totals.
                    color = if (isTransfer) MaterialTheme.colorScheme.primary else finColors.textSub,
                )
            }
        }
    }
}

@Composable
internal fun CategoryChipPicker(
    categories: List<Category>,
    selectedName: String,
    onSelect: (String) -> Unit,
    onAddCategory: (Category) -> Unit,
) {
    val sorted = remember(categories) { categories.sortedBy { it.name.lowercase() } }
    var showDialog by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val finColors = FinTheme.colors
    val selectedCat = sorted.firstOrNull { it.name == selectedName }

    // Tappable trigger row styled like a form field
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .clickable { query = ""; showDialog = true }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedCat != null) {
            CategoryChip(category = selectedCat, size = 24.dp)
        }
        Text(
            text = selectedCat?.name ?: "Kategorie wählen",
            style = MaterialTheme.typography.bodyLarge,
            color = if (selectedCat != null) MaterialTheme.colorScheme.onSurface else finColors.textSub,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = finColors.textFaint,
            modifier = Modifier.size(18.dp),
        )
    }

    if (showDialog) {
        val filtered = remember(sorted, query) {
            if (query.isBlank()) sorted
            else sorted.filter { it.name.contains(query, ignoreCase = true) }
        }
        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.95f),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Kategorie wählen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Suchen…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    // Plain Column+Row grid (no LazyVerticalGrid) to avoid nested-scroll
                    // conflicts with the outer ModalBottomSheet.
                    val allItems: List<Category?> = filtered + listOf(null)
                    val rows = allItems.chunked(4)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rows.forEach { chunk ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                chunk.forEach { cat ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (cat != null) {
                                            val isSelected = selectedName == cat.name
                                            val catColor = Color(cat.color)
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) catColor.copy(alpha = 0.12f) else Color.Transparent)
                                                    .border(
                                                        width = if (isSelected) 1.5.dp else 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                        shape = RoundedCornerShape(8.dp),
                                                    )
                                                    .clickable { onSelect(cat.name); showDialog = false }
                                                    .padding(8.dp),
                                            ) {
                                                CategoryChip(category = cat, size = 36.dp)
                                                Text(
                                                    cat.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                    .clickable { showCreate = true }
                                                    .padding(8.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = "Neue Kategorie",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                Text("+", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                                // fill empty slots in last row
                                repeat(4 - chunk.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showDialog = false },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Schließen") }
                }
            }
        }
    }

    if (showCreate) {
        CategoryAddEditDialog(
            initial = null,
            existingCategories = categories,
            onDismiss = { showCreate = false },
            onSave = { newCat ->
                onAddCategory(newCat)
                onSelect(newCat.name)
                showCreate = false
                showDialog = false
            },
        )
    }
}

@Composable
internal fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(FinTheme.colors.heroGradient)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
