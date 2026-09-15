# FinTracker — Phase 1: KMP Project Restructure

## Goal
Restructure the existing Android project into a Kotlin Multiplatform (KMP) layout.
No UI changes. No changes visible to the user. The app on your phone keeps working
exactly as before. This phase is purely about reorganising the code so it is ready
for the SQLDelight migration (Phase 2) and the Desktop build (Phase 3).

---

## Status Overview

| Step | What | Status |
|---|---|---|
| Pre-clean | Replace org.json, fix MoneyFormat, split DefaultCategories | ✅ Done |
| Step 1 | Create git branch `feature/kmp-restructure` | ✅ Done |
| Step 2 | Analyze project structure | ✅ Done |
| Step 3 | Add KMP Gradle plugin + shared module structure | ✅ Done |
| Step 4 | Move data models to commonMain | ✅ Done |
| Step 5 | Move analytics module to commonMain | ✅ Done |
| Step 6 | Move business logic to commonMain | ✅ Done |
| Step 7 | Define repository interfaces in commonMain | ✅ Done |
| Step 8 | Gap analysis — catch anything the plan missed | ✅ Done |
| Step 8A | Move CsvExporter + BackupRestore to commonMain | ✅ Done |
| Step 8B | Migrate CsvParser, ReceiptParser, JsonExporter, PinHasher (java.* → KMP) | ✅ Done |
| Step 8C | Implement MoneyFormat as expect/actual | ✅ Done |
| Step 8D | Add IPinStore + IAttachmentStorage interfaces | ✅ Done |
| Step 8E | Move 6 tests to commonTest | ✅ Done |
| Step 9 | Reconnect androidApp to shared module | ✅ Done |
| Step 10 | Test on device | ✅ Done |
| Step 11 | Commit and merge | ✅ Done |

---

## Pre-clean (Completed)

Before the KMP restructure could begin, three blockers were cleaned up:

- `org.json.*` replaced with `kotlinx.serialization` throughout repositories and JsonExporter
- `MoneyFormat.kt` refactored away from `java.text.NumberFormat`
- `DefaultCategories` split so it contains no Android-specific imports

---

## Step 1 — Create branch (Completed)

```
Create a new git branch called feature/kmp-restructure and switch to it.
```

---

## Step 2 — Analyze project structure (Completed)

```
Analyze the current project structure. List all Kotlin files and identify which ones
contain: data models, business logic, analytics, repository interfaces, UI code,
and Android-specific code (Context, SharedPreferences, etc.).
Output a categorized list.
```

---

## Step 3 — Add KMP Gradle plugin (Completed)

```
Update the root build.gradle.kts to add the Kotlin Multiplatform plugin.
Add a new shared/ module with commonMain, androidMain, desktopMain source sets.
Do not move any files yet — only set up the module structure and Gradle configuration.
Run a Gradle sync and confirm it resolves cleanly.
```

---

## Step 4 — Move data models to commonMain (Completed)

```
Move the following to shared/commonMain: all data model classes (Account, Booking,
RecurringRule, Category, etc.). Ensure no Android imports remain in these files.
Run a Gradle sync and confirm there are no compilation errors.
```

What Claude Code did:
- `Account.kt` and `Booking.kt` moved as-is — already clean
- `RecurringRule.kt` — `java.time.LocalDate` replaced with `kotlinx.datetime.LocalDate`
- `Category.kt` — `java.util.UUID` replaced with pure Kotlin `randomUuid()`
- `LocalDateSerializer.kt` — also migrated to `kotlinx.datetime`
- UI boundary files updated with `.toKotlinLocalDate()` / `.toJavaLocalDate()` converters
- `implementation(project(":shared"))` added to `app/build.gradle.kts`

---

## Step 5 — Move analytics module to commonMain (Completed)

```
Move the analytics module to shared/commonMain. It is already pure Kotlin —
verify no Android imports exist. Run Gradle sync and confirm compilation.
```

What Claude Code did:
- `RecurringOccurrences.kt` — moved as-is, already clean
- `Insights.kt` — moved, removed unused MoneyFormat import
- `FinanceAnalytics.kt` — `java.time.*` replaced with `kotlinx.datetime` equivalents
- `BillingCycleAnalytics.kt` — same date migration, billing cycle arithmetic rewritten
- Originals deleted from `app/src/main/java/.../analytics/`
- `AccountSubScreens.kt` and `MainActivity.kt` updated with date converters at boundaries

---

## Step 6 — Move business logic to commonMain (Completed)

```
Move balance calculation logic, period handling, and any pure Kotlin utility
functions to shared/commonMain. Verify no Android imports. Run Gradle sync.
```

What Claude Code did:
- `qrscan/EpcParser.kt` — moved as-is, already clean
- `export/ExportData.kt` — moved as-is, already clean
- `booking/BookingFactory.kt` — `System.currentTimeMillis()` → `Clock.System.now()`, date types migrated
- `reconciliation/ReconciliationService.kt` — date migration + UUID replaced with `idProvider` parameter
- Left in app intentionally: `CsvParser`, `ReceiptParser`, `PinHasher`, `DefaultCategories`, `MoneyFormat`

---

## Step 7 — Define repository interfaces in commonMain (Completed)

```
Create repository interfaces (IAccountRepository, IBookingRepository,
IRecurringRuleRepository, ICategoryRepository) in shared/commonMain.
These are interfaces only — no implementation logic.
The concrete implementations stay in the Android app for now.
```

What Claude Code did:
- `IAccountRepository` — loadAccounts, reload, saveAccounts, getRawAccountsJson, replaceAccountsJson, serializeAccounts
- `IBookingRepository` — same pattern for bookings
- `IRecurringRuleRepository` — same pattern for recurring rules
- `ICategoryRepository` — same pattern + `categoriesFlow: StateFlow<List<Category>>`

---

## Step 8 — Gap analysis (Completed)

Audit revealed 17 files already in commonMain, and identified the following remaining work.

**Already in commonMain (17 files):**
Account, Booking, RecurringRule, Category, all 4 interfaces, LocalDateSerializer,
FinanceAnalytics, BillingCycleAnalytics, Insights, RecurringOccurrences,
EpcParser, ExportData, BookingFactory, ReconciliationService

**Findings — work remaining before reconnect:**

| Group | Files | Action |
|---|---|---|
| A | CsvExporter, BackupRestore (split) | Move now, no changes needed |
| B | CsvParser, ReceiptParser, JsonExporter, PinHasher, MoneyFormat | Move after java.* → KMP migration |
| C | PinStore, AttachmentStorage | Need new interfaces first |
| D | All UI, Activities, Android repositories | Stay in app/ permanently |
| E | 6 test files | Move to commonTest now |

**Stays in app/ permanently:**
All Compose UI screens, MainActivity, CameraX/OCR, QR scan, ShareUtil,
ExportService, all Android repository implementations, CategoryIcons

---

## Step 8A — Move CsvExporter + BackupRestore to commonMain

```
Move export/CsvExporter.kt to shared/commonMain as-is — it only imports domain models.

For export/BackupRestore.kt: move BackupData, BackupParseError, BackupResult,
BackupStorage interface, BackupParser, RestoreError, RestoreResult, and
RestoreService.restore() to shared/commonMain.
Leave SharedPrefsBackupStorage and RestoreService.restoreFromContext() in app/.
Run a Gradle sync and confirm compilation.
```

---

## Step 8B — Migrate remaining files to commonMain (java.* → KMP)

```
Migrate the following files from java.* to KMP-compatible equivalents,
then move them to shared/commonMain:

- csvimport/CsvParser.kt — replace java.security.MessageDigest (SHA-256)
  with a KMP crypto library, replace java.time.* with kotlinx.datetime
- ocr/ReceiptParser.kt — replace java.time.LocalDate and DateTimeFormatter
  with kotlinx.datetime
- export/JsonExporter.kt — replace java.time.Instant with kotlinx.datetime
- security/PinHasher.kt — replace java.security.MessageDigest,
  SecureRandom, and Base64 with expect/actual or a KMP crypto library

Run a Gradle sync after each file and confirm compilation.
```

---

## Step 8C — Implement MoneyFormat as expect/actual

```
Implement MoneyFormat.kt using the expect/actual pattern:

In shared/commonMain: declare
  expect fun formatMoney(amount: Double): String
  expect fun formatMoneyShort(amount: Double): String

In androidMain: implement using java.text.NumberFormat with Locale.GERMANY

In desktopMain: implement a JVM equivalent for Phase 3.

Remove the existing MoneyFormat.kt from app/ and update all call sites.
Run a Gradle sync and confirm compilation.
```

---

## Step 8D — Add IPinStore + IAttachmentStorage interfaces

```
Create two new interfaces in shared/commonMain:

IPinStore:
- fun hasPin(): Boolean
- fun savePin(hash: String)
- fun getPin(): String?
- fun clearPin()

IAttachmentStorage:
- fun saveAttachment(bookingId: String, data: ByteArray): String
- fun loadAttachment(path: String): ByteArray?
- fun deleteAttachment(path: String)

These are interfaces only — no implementation logic.
The Android concrete classes stay in app/.
Run a Gradle sync and confirm compilation.
```

---

## Step 8E — Move tests to commonTest

```
Move the following test files from app/src/test/ to shared/src/commonTest/:
- BookingFactoryTest
- EpcParserTest
- FinanceAnalyticsTest
- RecurringOccurrencesTest
- RecurringRuleTest
- BookingJsonCompatibilityTest

Leave in app/src/test/: RecurringRulesScreenTest (Compose UI test)
Delete: ExampleUnitTest, ExampleInstrumentedTest (empty scaffolding)

Run ./gradlew :shared:testDebugUnitTest and confirm all tests pass.
```

---

## Step 9 — Reconnect androidApp to shared module

```
Update the Android app to depend on the shared module via Gradle.
Update imports in the Android app to use the moved classes from shared/commonMain.
Ensure all Android repository classes declare they implement their interfaces
(AccountsRepository implements IAccountRepository, etc.).
Run Gradle sync and confirm the Android app compiles without errors.
```

---

## Notiz
"IPinStore + IAttachmentStorage Interfaces definiert aber noch nicht implementiert — async/sync Konflikt. Wird in Phase 2 beim SQLDelight Umbau korrekt gelöst."

## Step 10 — Test on device

```
Build the Android app and install it on the connected device.
Confirm it installs and runs correctly. Check the following manually:
- Account overview loads
- Bookings display correctly
- Adding a booking works
- Analytics screen loads
- Categories work
- Recurring rules work
- Attachments work
- OCR / receipt scanning works
- QR scan works
- CSV import works
- Export works
- Reconciliation screen works
- PIN lock works
All features must work identically to before the restructure.
```

---

## Step 11 — Commit and merge

```
Commit all changes with message "feat: KMP project restructure Phase 1 complete".
Merge feature/kmp-restructure into main and push to GitHub.
```

---

## What Stays Platform-Specific After Phase 1

| Component | Stays In | Reason |
|---|---|---|
| SharedPreferences | androidMain | Android-only API |
| UI / Compose screens | androidApp | UI not shared yet |
| Context usage | androidMain | Android-only |
| Repository implementations | androidApp | Depends on SharedPreferences |
| CameraX / ML Kit (OCR) | androidApp | Android hardware API |
| QR scan | androidApp | Android hardware API |
| ShareUtil / ExportService | androidApp | Android share sheet |
| CategoryIcons | androidApp | Compose-specific |

---

## Testing Checklist

- [ ] App installs on phone without errors
- [ ] Account overview loads correctly
- [ ] Bookings display correctly
- [ ] Adding a new booking works
- [ ] Analytics screen loads correctly
- [ ] Categories work correctly
- [ ] Recurring rules work correctly
- [ ] Attachments work correctly
- [ ] OCR / receipt scanning works
- [ ] QR scan works
- [ ] CSV import works
- [ ] Export works
- [ ] Reconciliation screen works
- [ ] PIN lock works
- [ ] No data loss after restructure
- [ ] Gradle sync completes without errors
- [ ] All commonTest tests pass
- [ ] Gap analysis audit completed ✅
- [ ] GitHub push successful

---

## What Phase 1 Does NOT Do

- Does not change any UI
- Does not change how data is stored (still JSON / SharedPreferences)
- Does not affect the app on your phone in any visible way
- Does not build the desktop app yet
