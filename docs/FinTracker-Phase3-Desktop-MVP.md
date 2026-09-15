# FinTracker — Phase 3: Desktop App MVP (Windows)

**Status: COMPLETE** ✅ — Merged to `main` on 2026-04-06.

## Goal
Build a Windows Desktop app using Compose for Desktop that shares all business logic
with the Android app via the shared/ module built in Phase 1 and 2.

## Prerequisites
- Phase 1 complete (KMP structure in place) ✅
- Phase 2 complete (SQLDelight as persistence layer) ✅
- IntelliJ IDEA Community Edition installed on the laptop ✅
- JDK 17 or higher installed (required for Compose for Desktop packaging) ✅
- Android app still working correctly on device ✅

---

## MVP Scope

The desktop app is NOT a full feature port. It is a focused MVP:

| Feature | Included in MVP |
|---|---|
| View all accounts | ✅ |
| Add a new account | ✅ |
| Edit an account | ✅ |
| Delete an account | ✅ |
| View bookings per account | ✅ |
| Add a new booking | ✅ |
| Edit a booking | ✅ |
| Delete a booking | ✅ |
| Category management (view / add / edit / delete) | ✅ |
| CSV export | ✅ (needed for Phase 4 sync) |
| CSV import | ✅ (needed for Phase 4 sync) |
| JSON backup / restore | ✅ |
| `MoneyFormat` desktopMain actual | ✅ (compile-time prerequisite) |
| `CryptoExpect` desktopMain actual | ✅ (compile-time prerequisite) |
| Analytics / charts | ❌ Phase 4+ |
| Recurring bookings management | ❌ Phase 4+ |
| Reconciliation UI | ❌ Phase 4+ |
| Audit screen | ❌ Phase 4+ |
| Installments UI | ❌ Phase 4+ |
| QR / OCR scanning | ❌ Not applicable on desktop |
| Receipt photo attachments | ❌ Not applicable on desktop |
| PIN lock screen | ❌ Phase 4+ |

---

## Won't Have in Phase 3

These features are excluded because they depend on Android-only APIs or are out of MVP scope:

| Feature | Reason |
|---|---|
| QR scanning | Requires CameraX + ML Kit barcode — Android-only; no desktop camera API equivalent |
| OCR / receipt scanning | Requires CameraX + ML Kit text recognition — Android-only |
| Receipt photo attachments | Requires Android `FileProvider` / `ContentResolver`; `IAttachmentStorage` desktopMain impl deferred to Phase 4 |
| Charts (Vico) | Vico library is Android-only; desktop charting needs a separate library — deferred to Phase 4 |
| PIN lock screen | `DataStore` is Android-only; desktopMain `IPinStore` implementation deferred to Phase 4 |
| Installments UI | Data model exists in commonMain but desktop UI is non-trivial — deferred to Phase 4 |
| Audit screen | Not part of core entry workflow — deferred to Phase 4 |
| Reconciliation UI | Power-user feature; deferred to Phase 4 alongside analytics |
| Android share sheet | Android-only; replaced by native file-save dialogs for export on desktop |

Note: The parsing logic behind QR and OCR (`EpcParser`, `ReceiptParser`) already lives in
`commonMain` and is usable on desktop — only the camera capture is excluded.

---

## Step-by-Step Instructions for Claude Code

### Step 1 — Create a new Git branch ✅
```
Create a new git branch called feature/desktop-mvp and switch to it.
```
> Branch created as `feature/desktop-mvp` (used in place of `feature/desktop-app-mvp`).

### Step 2 — Set up desktopApp module ✅
```
Create a new desktopApp/ module in the project.
Configure it in settings.gradle.kts.
Add Compose for Desktop dependency (org.jetbrains.compose).
Add the SQLite JDBC driver (org.xerial:sqlite-jdbc) for desktop persistence.
Run Gradle sync and confirm the desktopApp module builds.
```

### Step 3 — Implement desktopMain expect/actual prerequisites ✅
```
Before writing any UI, implement the two expect/actual functions that have no
desktopMain actual yet — without these, the shared module will not compile for desktop:

1. MoneyFormat (shared/src/desktopMain/kotlin/.../util/MoneyFormat.desktop.kt)
   Implement formatMoney() using java.text.NumberFormat with the system locale.

2. CryptoExpect (shared/src/desktopMain/kotlin/.../security/CryptoActual.desktop.kt)
   Implement sha256(), secureRandomBytes(), base64Encode(), base64Decode()
   using java.security.MessageDigest, java.security.SecureRandom, and
   java.util.Base64.

Confirm the shared module compiles for the desktop target after this step.
```

### Step 4 — Implement desktop database driver ✅
```
Implement DatabaseFactory for desktopMain using JdbcSqliteDriver
(shared/src/desktopMain/kotlin/.../db/DatabaseDriverFactory.desktop.kt).
The database file is stored at: {user.home}/FinTracker/fintracker.db
Parent directory is created automatically if it does not exist.
```

### Step 5 — Create the desktop app entry point ✅
```
Created main() entry point in desktopApp/src/main/kotlin/io/github/willywonka644/fintracker/Main.kt.
Window title: "FinTracker Desktop", size 1200x800.
Material 3 theme applied consistent with the Android app.
```

### Step 6 — Account Overview screen ✅
```
Built AccountOverviewScreen displaying all accounts from IAccountRepository.
Layout: account list with name, type, and balance summary.
Uses Material 3 components from Compose for Desktop.
```

### Step 7 — Add account form ✅
```
Built account form dialog with fields:
  - name (required text field)
  - type (dropdown: GIRO, CREDIT_CARD, SPARKONTO, TAGESGELD)
  - billingStartDay (optional integer, shown only when type = CREDIT_CARD)
  - spendingLimit (optional decimal, shown only when type = CREDIT_CARD)
Writes to SQLDelight database via IAccountRepository.
```

### Step 8 — Edit and delete account ✅
```
Edit: form pre-populated with current values; updates via IAccountRepository.
Delete: confirmation dialog; removes account and its bookings.
```

### Step 9 — Booking list screen ✅
```
Built BookingListScreen showing bookings for the selected account.
Columns: date, description, amount, category, status (POSTED / SCHEDULED).
Sorted by date (newest first).
Income amounts shown in green, expense amounts in red.
```

### Step 10 — Add booking form ✅
```
Built booking form dialog with fields matching the Booking data model:
  - account (pre-selected from current account context)
  - date (text field in ISO format)
  - amount (decimal; positive = income, negative = expense)
  - description (required text field)
  - category (dropdown populated from ICategoryRepository)
  - status (POSTED / SCHEDULED)
  - effectiveDate (optional)
Uses BookingFactory.createManualBooking() and writes via IBookingRepository.
```

### Step 11 — Edit and delete booking ✅
```
Edit: form pre-populated with current values; updates via IBookingRepository.
Delete: confirmation dialog; removes booking via IBookingRepository.
```

### Step 12 — Category management screen ✅
```
Built CategoryManagementDialog accessible from main navigation:
  - Lists all categories (name, icon, color swatch).
  - Add / edit: form with name, iconName, and color (hex input).
  - Delete: confirmation dialog; prevents deletion of default categories.
Uses ICategoryRepository for all reads and writes.
Category dropdown in Add/Edit Booking is populated from the database.
```

### Step 13 — CSV export on desktop ✅
```
CSV export implemented using the existing CsvExporter from shared/commonMain.
File-save dialog via javax.swing.JFileChooser.
Two export modes:
  - Export current account
  - Export all accounts
Foundation for Phase 4 file-based sync.
```

### Step 14 — CSV import on desktop ✅
```
CSV import implemented using the existing CsvParser from shared/commonMain:
  1. File-open dialog: user selects a .csv file.
  2. Column mapping UI: detected headers shown; user maps each to a CsvColumnRole.
  3. Preview: first rows shown before committing.
  4. On confirm: bookings written via IBookingRepository; duplicates skipped via
     CsvParser.buildDuplicateKeySet().
```

### Step 15 — JSON backup and restore ✅
```
JSON backup: file-save dialog → full app state written as JSON via JsonExporter.
JSON restore: file-open dialog → parse JSON → replace all data via repositories.
Confirmation warning shown before overwriting existing data.
```

### Step 16 — Navigation ✅
```
Navigation wired up between all screens:
  - Account list → Booking list (click account)
  - Booking list → Add / Edit booking
  - Any screen → Add account (toolbar button)
  - Account list → Edit / Delete account
  - Main navigation → Category management (menu item)
  - Main navigation → CSV export / import (menu items)
  - Main navigation → JSON backup / restore (menu items)
  - Back navigation throughout
```

### Step 17 — Package desktop app ✅
```
Gradle packaging configured for Windows in desktopApp/build.gradle.kts:

  compose.desktop {
    application {
      mainClass = "io.github.willywonka644.fintracker.MainKt"
      nativeDistributions {
        targetFormats(TargetFormat.Msi, TargetFormat.Exe)
        packageName = "FinTracker Desktop"
        packageVersion = "1.0.0"
        windows {
          menuGroup = "FinTracker"
          upgradeUuid = "<generated UUID>"
        }
        jvmArgs("-Dfile.encoding=UTF-8")
      }
    }
  }

Run: ./gradlew :desktopApp:packageMsi  (or packageExe)
Bundles a JDK 17+ runtime — no Java pre-installation required on target machine.
```

### Step 18 — Commit and merge ✅
```
Committed: "feat: desktop app MVP Phase 3 complete"
Merged feature/desktop-mvp → main
Pushed to GitHub (willywonka644/fintracker-android)
```

### Step 19 — Move SQL repositories to commonMain ✅
```
Moved SqlAccountRepository, SqlBookingRepository, SqlRecurringRuleRepository,
and SqlCategoryRepository from androidMain to commonMain so they are shared
by both the Android and Desktop targets without duplication.
```

---

## Development Workflow

Since you are on a low-RAM laptop:
- Run the desktop app directly via IntelliJ (no emulator needed)
- Test Android on physical device via USB
- Never run both Android emulator and desktop app at the same time

---

## Testing Checklist

### Desktop App
- [x] App launches on Windows
- [x] Account list displays correctly
- [x] Add account saves correctly (with billingStartDay and spendingLimit for credit cards)
- [x] Edit account updates correctly
- [x] Delete account removes account and its bookings
- [x] Booking list displays for selected account (date, description, amount, category, status)
- [x] Add booking saves correctly; BookingFactory sets status from effectiveDate
- [x] Edit booking updates correctly
- [x] Delete booking removes the booking
- [x] Category management: add, edit, delete categories
- [x] Category dropdown in booking form shows categories from database
- [x] CSV export produces a valid file the Android app can import
- [x] CSV import parses file, shows column mapping, skips duplicates
- [x] JSON backup produces a valid file
- [x] JSON restore replaces all data correctly
- [x] App closes cleanly

### Android App (must still work)
- [x] App still installs and runs on phone
- [x] No regression in any existing feature
- [x] GitHub push successful

---

## What Phase 3 Does NOT Do

- Does not sync data between Android and Desktop automatically
- Does not share a database file between devices (that is Phase 4)
- Does not port all Android features to desktop
- Does not implement charts (Vico is Android-only; a desktop chart library is a Phase 4 decision)
- Does not implement PIN lock on desktop (DataStore is Android-only; deferred to Phase 4)
