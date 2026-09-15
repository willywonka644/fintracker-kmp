# FinTracker — Daily Development Roadmap

**Last updated:** March 2026  
**Current branch:** `main`  
**Current version:** v0.5.x (Android only, feature-complete sprint baseline)

---

## Legend

- ✅ Done & merged to main
- 🔧 Partially done / needs follow-up
- ⏳ Next up
- ☐ Not started

---

## Architecture Decision: Auswertungen Screen

All analytics features live **exclusively in the Auswertungen screen**, accessible via the
"Auswertungen" button in each account's submenu. The `AccountDetailScreen` is **not** extended
with analytics UI — it stays focused on the booking list and balance.

When the user taps "Auswertungen" → a dedicated `AuswertungenScreen` opens for that account.
All analytics sections (category breakdown, charts, income vs expense, etc.) are built there.

This keeps the account detail screen clean and gives analytics its own space to grow.

---

## What's Already in Main

### ✅ Sprint 1–4 Baseline (v0.5.1)
- Account management: Girokonto, Kreditkarte, Sparkonto, Tagesgeldkonto
- Booking CRUD: income, expenses, transfers
- Dynamic balance calculation (`baseBalance + sum(bookings)`)
- Manual reconciliation
- Analytics module: ALL_TIME, THIS_MONTH, CUSTOM
- Credit card billing cycles & spending limits with visualization
- PIN protection (device-local)
- Export: JSON backup + CSV bookings
- Restore from JSON backup
- Settings, Help, About screens
- Custom launcher icon

### ✅ Balance Model Redesign
- Removed manual balance override (`currentBalance` gone from Account model)
- Balance is always computed from bookings in the selected period
- Label updated to "Saldo im Zeitraum"
- JSON schema bumped to v2, fully backwards compatible

### ✅ Period / Filter Redesign
- Girokonto: month selector (Jan 2026, Feb 2026, … up to current month)
- Kreditkarte: billing cycle selector (18.01–17.02.2026, 18.02–17.03.2026, …)
- Current period pre-selected by default
- Old "Dieser Monat / Gesamtzeitraum" toggle removed

### ✅ Date Editing for Bookings
- Material 3 DatePicker in booking creation and edit
- Moving a booking's date automatically moves it between billing cycles

### ✅ Recurring Bookings (Days 1–4 — implemented in one shot)
- `RecurringRule` model: id, amount, description, accountId, frequency (WEEKLY/MONTHLY/YEARLY), nextExecutionDate, `remainingExecutions: Int?` (null = unlimited)
- `RecurringRulesRepository` with JSON persistence
- Booking creation: Einmalig / Regelmäßig toggle at the top
- Recurring bookings appear inline in the booking list:
  - Past/today execution date → shown as normal booking
  - Future execution date → shown as "Geplant" (grey, excluded from balance)
- Long-press on a recurring booking: "Diesen Eintrag bearbeiten" / "Gesamte Serie bearbeiten" / "Löschen"
- All upcoming occurrences generated and displayed (unlimited = next 6 months)
- JSON backup includes recurring rules

### ✅ Settings Menu Redesign (Account Overview)
- MoreVert (three dots) → dropdown with 6 icon+label items:
  - 🔒 PIN-Schutz
  - 📤 Backup exportieren
  - 📥 Backup wiederherstellen
  - 📊 CSV exportieren
  - ❓ Hilfe
  - ℹ️ Über FinTracker

### ✅ Fix 1 — Filter Chip Polish
- "Zeitraum" removed from chip label (commit `d4e6d05`)
- Chip shows only the selected period name (e.g. "Feb 2026") + calendar icon

### ✅ Day 5 — Category System: Data Model & CRUD
- `Category` model: id, name, color (hex), icon (Material icon name)
- `CategoriesRepository` with JSON persistence
- Category management screen: create, edit, delete
- German default categories: Gehalt, IT-Equipment, Lebensmittel, Finanzen/Investment,
  Gesundheit, Wohnen, Reisen, Telekommunikation/KI, Versicherungen, Sonstiges
- Categories included in JSON backup/restore

### ✅ Day 6 — Category System: Booking Integration
- Category picker in booking creation and edit dialogs
- Category icon + color dot shown in booking list rows
- Reactive `StateFlow` categories
- `BookingStatus` enum (`POSTED` / `SCHEDULED`) — implemented early for recurring bookings
- `merchantName` field already on the `Booking` model
- Future-dated bookings shown as "Geplant", excluded from balance
- "Auswertungen" submenu item available for all account types

### ✅ Day 7 — Category Analytics (Auswertungen Screen)
- `AuswertungenScreen` foundation built
- Category breakdown: color dot | name | amount | % of total, sorted by amount descending
- Calculation logic in analytics module (pure Kotlin, no Android imports)

### ✅ Day 8 — Effective-Later Bookings
- `effectiveDate: LocalDate?` added to Booking model
- Balance calculation uses `effectiveDate` when present, falls back to `transactionDate`
- "Wirksam ab" date picker in booking creation/edit
- "Geplant" pill with grey styling for future-effective bookings
- `BookingStatus` enum fully wired up

### ✅ Day 9 — Analytics: Balance Over Time Chart
- Vico chart library (v1.13.1) integrated
- Balance-over-time line chart as Section 2 in `AuswertungenScreen`
- X-axis = dates in selected period, Y-axis = running balance
- Account name tappable for account switcher navigation

### ✅ Day 10 — Analytics: Income vs Expense Chart
- Monthly income (green) vs expense (red/orange) bar chart as Section 3
- Category breakdown horizontal bar chart as Section 4
- Charts tap-interactive
- Fixed: duplicate category breakdown display; corrected billing cycle grouping

### ✅ Day 11 — CSV Import
- `BookingSource` enum (MANUAL, IMPORT) and `importBatchId: String?` added to Booking model
- `CsvParser` with auto-detection of column structure
- `CsvImportScreen` accessible via Account Detail MoreVert → "CSV importieren"
- Column mapping screen with 5-row preview
- Duplicate detection (hash of date + amount + description + accountId)
- Import summary: "X importiert, Y übersprungen"

### ✅ Day 12–15 — QR Scan, Receipt Attachment, Reconciliation
- QR scan (EPC/GiroCode) via CameraX + ML Kit → prefills booking form
- Receipt attachment: `Attachment` model, camera/gallery picker, thumbnail in list
- Advanced reconciliation: user sets real bank balance → "Korrektur" booking created

### ✅ Day 16 — OCR: Receipt Text Recognition
- "Beleg scannen" button in booking creation → camera or gallery → ML Kit Text Recognition
- Extracts amount, date, merchant from receipt text and prefills booking form
- Confidence labels ("erkannt" / "bitte prüfen") on prefilled fields
- `ocrRawText: String?` stored on Booking model and included in JSON backup
- OCR is assist-only: always shows extracted values in editable fields before user confirms
- Note: OCR prefill is intentionally basic. This feature is kept as a future ML learning project — a custom on-device model (TFLite/ONNX) trained on real receipt data will replace the current heuristics in a later phase.

### ✅ Fix — Empfänger field removed from booking form
- `merchantName` is no longer shown as a visible input field in booking creation/edit
- QR scan and OCR now append the merchant name to the description field (e.g. "Einkauf – dm-drogerie markt")
- Booking creation form is now identical across all entry points: manual, QR scan, OCR scan

### ✅ Day 17 — Quick Add Booking FAB + Booking Type Refactor
- "Buchung hinzufügen" FAB added to Account Overview (bottom-left)
- Bottom sheet flow: account selection → booking type selection (Einmalige Buchung / Ratenzahlung / Dauerauftrag)
- "Einmalig / Regelmäßig" toggle removed from AccountDetailScreen booking form
- Replaced with ExposedDropdownMenuBox with three types; form content switches dynamically
- Dauerauftrag-linked bookings show a small repeat icon (Icons.Default.Repeat, 14–16dp) in the booking list
- Installment bookings continue to show "X/N" label; one-time bookings show neither

### ✅ Day 18 — Installments (confirmed complete)
- "Als Raten" option in booking creation
- User enters total amount + number of installments + start date
- App generates multiple effective-dated bookings (e.g. 3× monthly)
- Installment bookings linked by `installmentGroupId`
- Displayed as "1/3", "2/3", "3/3" in the booking list
- Included in JSON backup

---

## Upcoming Work

---

### ✅ Day 19 — Booking Verification: Dedicated Audit Screen
- `isVerified: Boolean` (default false) added to Booking model
- Included in JSON backup/restore (backward compatible)
- "Audit" menu item in Account Detail MoreVert dropdown (PlaylistAddCheck icon)
- Dedicated AuditScreen: full-screen list of bookings for current account + period
- Each row shows date, description, category color dot, amount, and large toggleable checkmark
- Verified: green CheckCircle; Unverified: grey RadioButtonUnchecked
- Toggle persists immediately via repository (no save button)
- Top bar subtitle: "X von Y geprüft"
- Read-only green checkmark (15dp) shown next to amount in AccountDetailScreen booking list
- Verification summary ("X von Y Buchungen geprüft") in account detail header area

---

### Day 20 — Category Improvements: Alphabetical Sorting + Inline Creation ✅

**Task for Claude Code:**
> Make two improvements to the category system:
>
> **Part A — Alphabetical sorting:**
> In the Category Management screen and in all category pickers (booking creation,
> booking edit), sort categories alphabetically by name at all times.
> The "recently used" fast-picker section (if implemented) can keep its own order,
> but the full category list below it must be alphabetical.
>
> **Part B — Inline category creation during booking:**
> In the category picker shown during booking creation and booking edit,
> add a "+ Neue Kategorie" entry at the bottom of the category list
> (after all existing categories, alphabetically sorted).
>
> When the user taps "+ Neue Kategorie":
> - Show a compact inline creation form (name field + color picker + icon picker)
>   either as a bottom sheet or an inline expansion within the picker.
> - On confirm, the new category is saved via `CategoriesRepository` and immediately
>   appears in the picker so the user can select it for the current booking.
> - Use the singleton `CategoriesRepository` pattern to ensure the new category
>   is immediately available everywhere (reactive StateFlow).
> - Do not navigate away from the booking form — the user stays in context.
>
> Do not change the Category Management screen beyond the alphabetical sort.

**What you test:** Open booking creation → category picker → categories are alphabetically sorted. Tap "+ Neue Kategorie" → enter name, pick color and icon → confirm → new category appears in the list and can be selected immediately. Check Category Management screen — new category is listed there too, alphabetically sorted.

---

### Day 21 — Analytics Chart Interactivity: Tooltip with Date + Balance ✅

**Task for Claude Code:**
> Improve the interactive tooltip behavior on the charts in `AuswertungenScreen`.
>
> **Current behavior:** When the user touches or drags across the line chart or bar chart,
> only the date is shown. The balance or amount value at that point is not displayed.
>
> **Target behavior:**
>
> Line chart (balance over time, Section 2):
> When the user touches a point on the line, show a tooltip or highlight marker that
> displays both the date (e.g. "12. März") AND the account balance at that point
> (e.g. "1.842,50 €"). The tooltip should appear above or near the touched point,
> styled consistently with the app's Material 3 theme (surface container, rounded corners).
>
> Bar chart (income vs expense, Section 3):
> When the user taps or holds a bar, show a tooltip or label that displays
> the month name AND the exact income or expense amount for that bar
> (e.g. "Februar — Ausgaben: 650,00 €"). Distinguish income vs expense in the tooltip.
>
> Use the Vico library (v1.13.1) tooltip/marker API for both charts.
> Keep the calculation logic in the analytics module (pure Kotlin).
> Only the chart rendering and marker configuration changes — no data model changes needed.
>
> Known Vico 1.13.1 constraint: `DynamicShaders.fromBrush()` does not exist —
> use `DynamicShaders.verticalGradient()` or `DynamicShaders.color()` instead.

**What you test:** Open Auswertungen → scroll to the line chart → touch and drag across it → tooltip shows date AND balance at the touched point. Scroll to the bar chart → tap a bar → tooltip shows month AND income or expense amount.

---

### Day 22 — Documentation & Version Release ⏳

**Task for Claude Code:**
> Update all end-user-facing documentation and prepare a version release.
>
> **Part A — Update README.md:**
> Rewrite the README to reflect the current feature set of v0.6.0.
> Include: what FinTracker is, core philosophy (local-only, no bank APIs, user always confirms),
> current feature list (account types, booking types, recurring rules, categories, analytics,
> CSV import, QR scan, OCR, receipt attachment, installments, reconciliation, audit checkmarks),
> and a brief "how to build" section for Android Studio.
> Keep it concise and factual. No marketing language.
>
> **Part B — Update the in-app Help screen:**
> Review the existing Help screen content. Update it to cover all features implemented
> since v0.5.1: recurring bookings, categories, analytics (Auswertungen screen),
> effective-later bookings, CSV import, QR scan, OCR, receipt attachment, installments,
> advanced reconciliation, audit checkmarks, and the Quick Add FAB.
> Use plain German. Keep each help entry short (2–3 sentences max).
>
> **Part C — Update the in-app "Über FinTracker" screen:**
> Update the version number to v0.6.0.
> Update the feature summary or changelog blurb if one exists.
>
> **Part D — Bump version in build.gradle:**
> Set `versionName` to `"0.6.0"` and increment `versionCode` by 1.
>
> **Part E — Git tag:**
> After all changes are committed, create a Git tag: `git tag v0.6.0` and push it.

**What you test:** README reads correctly on GitHub. In-app Help covers all current features. "Über FinTracker" shows v0.6.0. Build compiles with new version number. `git tag` lists v0.6.0.

---

## After Day 22: Cross-Platform (Phase A)

With all features built, tested, and stable on Android, the KMP restructure
moves proven code into the shared module.

| Days | Task |
|---|---|
| 21–23 | KMP project restructure (shared / androidApp / desktopApp) |
| 24–26 | SQLDelight migration (replace SharedPreferences + JSON) |
| 27–30 | Desktop app MVP: account overview + booking list + add booking |
| 31–33 | File-based sync polish (JSON export/import between devices) |
| 34+ | Local network sync (Wi-Fi, user-triggered) |

---

## How to Use This Roadmap

1. Copy the **"Task for Claude Code"** text for the next item
2. Paste it into Claude Code (Code tab in Claude Desktop)
3. Claude Code works on a feature branch
4. Switch to that branch in Android Studio
5. Compile, run, test
6. If something's off → feed the issue back to Claude Code
7. When satisfied → merge into `main` and push to GitHub

Always start from the top of the "Upcoming Work" section.
**Day 22 (Documentation & Version Release) is the immediate next step.**
