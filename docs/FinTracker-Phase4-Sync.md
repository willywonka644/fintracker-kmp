# FinTracker — Phase 4: Local File-Based Sync + Desktop Feature Completion

## Goal
Two goals in one phase:

1. **Sync** — Enable data synchronisation between the Android app and the Windows Desktop
   app without any cloud dependency. The user controls when and how sync happens.
2. **Desktop feature completion** — Port the remaining Phase 3-deferred screens to the
   desktop app so both platforms are functionally equivalent for core workflows.

This stays true to the FinTracker philosophy: local-first, transparent, no magic.

## Estimated Time
2–3 weeks

## Prerequisites
- Phase 3 complete (desktop app MVP working) ✅
- CSV export working on both Android and Desktop ✅
- Both apps reading/writing from SQLDelight ✅

---

## Sync Philosophy

No cloud. No automatic sync. No background processes.
The user explicitly triggers sync. The user understands exactly what happens.

**Sync mechanism:** Export a structured sync file from one device,
transfer it manually (USB cable, shared folder, or local network share),
import it on the other device.

This is called **file-based sync** — simple, transparent, and fully offline.

---

## Sync File Format

We use a dedicated sync file format (`.ftsync`) which is a JSON file containing:

```json
{
  "version": "1.0",
  "exportedAt": "2025-04-03T10:00:00Z",
  "deviceId": "android-phone",
  "accounts": [...],
  "bookings": [...],
  "categories": [...],
  "recurringRules": [...]
}
```

**Rules:**
- `attachmentPath` is **excluded** from the sync payload on export. On import, any
  `attachmentPath` field present in an incoming record is set to `null`. Binary file
  transfer is not part of Phase 4.
- `recurringRules` is included so both devices stay in sync on scheduled entries.
- Version field must be checked on import — see Error Handling below.

---

## Conflict Resolution Strategy

Since both devices can modify data independently, conflicts are possible.
We use a simple, transparent strategy that applies uniformly to all record types:

**Last-write-wins per record by ID** — each record (Booking, Account, Category,
RecurringRule) has a `lastModifiedAt` timestamp. On import, if a record already
exists (same `id`), the version with the newer `lastModifiedAt` wins.
New records on either side are always added.

| Record type | Conflict rule |
|---|---|
| Booking | Last-write-wins by `id`, `lastModifiedAt` |
| Account | Last-write-wins by `id`, `lastModifiedAt` |
| Category | Last-write-wins by `id`, `lastModifiedAt` |
| RecurringRule | Last-write-wins by `id`, `lastModifiedAt` |

The user sees a summary before confirming the import:
- X new records will be added
- Y records will be updated (newer version from sync file wins)
- Z records are identical (no change)

The user confirms or cancels. No automatic changes.

---

## Error Handling Strategy for Malformed Sync Files

`SyncImporter` must validate the file before processing. On any validation failure,
it must return an error result and make no changes to the database.

| Error condition | Handling |
|---|---|
| File is not valid JSON | Return `SyncError.InvalidJson` — show user-facing message |
| `version` field missing or unknown | Return `SyncError.UnknownVersion` — do not attempt import; instruct user to update the app |
| `exportedAt`, `deviceId`, or any required top-level field missing | Return `SyncError.MissingField(fieldName)` |
| A booking references an `accountId` that is not in the sync file and not in the local DB | Skip that booking, add it to a `skippedCount` in `SyncPreview`; report to user |
| A booking or account references a `categoryId` that is not in the sync file and not in the local DB | Set `category = null` on import; add to `warnings` list in `SyncPreview` |
| File is truncated (valid JSON start, abrupt end) | Caught by JSON parser → `SyncError.InvalidJson` |
| Partial import failure (write error mid-import) | Wrap entire `SyncWriter` operation in a database transaction; roll back on any error |

The SyncPreview screen must surface `skippedCount` and `warnings` to the user
before they confirm. They must be able to make an informed decision.

---

## Step-by-Step Instructions for Claude Code

---

### MUST HAVE — File-Based Sync

### Step 1 ✅ — Create a new Git branch
```
Create a new git branch called feature/phase4-sync and switch to it.
```

### Step 2 ✅ — Add lastModifiedAt to all models
```
Add a lastModifiedAt: Long (Unix epoch milliseconds) field to Booking, Account,
Category, and RecurringRule in shared/commonMain data models.

Update the SQLDelight schema (.sq files) to include lastModifiedAt on all four tables.

Update all places where these models are created or modified to set
lastModifiedAt = System.currentTimeMillis() (or the platform equivalent via expect/actual).

Write a SQLDelight migration that sets lastModifiedAt = <current epoch millis at
migration time> for all existing records. Do NOT reference a createdAt field —
no such field exists in the schema.
```

### Step 3 ✅ — Add deviceId to app settings
```
Create a new SQLDelight table called app_settings with two columns:
  key TEXT PRIMARY KEY
  value TEXT NOT NULL

This table acts as a key-value store for app-level configuration.

On first launch (when no deviceId row exists), generate a UUID and INSERT it as:
  key = "deviceId", value = "<generated UUID>"

Expose a SettingsRepository interface in commonMain with at least:
  fun getDeviceId(): String
  fun get(key: String): String?
  fun set(key: String, value: String)

Implement SqlSettingsRepository in commonMain using this table.
Wire it up in AppServices (desktop) and inject it in the Android app.
```

### Step 4 ✅ — Implement SyncExporter in commonMain
```
Create a SyncExporter class in shared/commonMain:
- Reads all accounts, bookings, categories, recurringRules from the repositories
- Strips attachmentPath from all bookings before serialisation (set to null)
- Reads deviceId from SettingsRepository
- Serialises to the .ftsync JSON format defined above
- Returns the JSON string (platform handles file writing)
```

### Step 5 ✅ — Implement SyncImporter in commonMain
```
Create a SyncImporter class in shared/commonMain:
- Parses a .ftsync JSON string
- Validates: version, required fields, referential integrity (see Error Handling above)
- On any validation error, returns SyncError — does NOT proceed
- Compares incoming records with existing local data using lastModifiedAt
- Applies last-write-wins per ID for Booking, Account, Category, RecurringRule
- Nulls out any attachmentPath on incoming bookings
- Returns a SyncPreview:
    data class SyncPreview(
        val newCount: Int,
        val updatedCount: Int,
        val unchangedCount: Int,
        val skippedCount: Int,
        val warnings: List<String>,
        val resolvedAccounts: List<Account>,
        val resolvedBookings: List<Booking>,
        val resolvedCategories: List<Category>,
        val resolvedRecurringRules: List<RecurringRule>
    )
- Does NOT write to database — only returns the preview
```

### Step 6 ✅ — Implement SyncWriter in commonMain
```
Create a SyncWriter class in shared/commonMain:
- Accepts a confirmed SyncPreview
- Wraps all writes in a single database transaction
- Upserts all resolved accounts, bookings, categories, recurringRules
- On any write failure: rolls back the transaction, returns SyncError.WriteFailed
- On success: returns SyncResult.Success(newCount, updatedCount)
- Called only after user confirms the import preview
```

### Step 7 ✅ — Android: Export UI
```
Add a "Sync Export" option to the Account Overview menu on Android.

File writing on Android 10+ (API 29+) must use the Storage Access Framework (SAF):
  - Use ActivityResultContracts.CreateDocument("application/octet-stream")
  - Default filename: fintracker-sync-<yyyy-MM-dd>.ftsync
  - Write SyncExporter output to the URI returned by the SAF intent
  - Do NOT write directly to a hardcoded path like Downloads/

On success: show confirmation snackbar with the saved file name.
```

### Step 8 ✅ — Android: Import UI
```
Add a "Sync Import" option to the Account Overview menu on Android.

File reading on Android 10+ must also use SAF:
  - Use ActivityResultContracts.OpenDocument with mimeType "*/*" filtered by .ftsync
  - Read file content from the URI returned by the SAF intent

After file selected:
  - Run SyncImporter
  - If SyncError returned: show error dialog with explanation, no further action
  - If SyncPreview returned: show SyncPreview screen:
      - New / Updated / Unchanged / Skipped counts
      - Any warnings listed (e.g. "3 bookings had unknown categories — set to none")
      - Confirm and Cancel buttons
  - On confirm: run SyncWriter, show success message, refresh UI
  - On cancel: no changes
```

### Step 9 ✅ — Desktop: Export UI
```
Add a "Sync Export" menu item to the desktop app (File menu or toolbar).
On click: run SyncExporter, open JFileChooser save dialog defaulting to
{user.home}/FinTracker/, suggested filename: fintracker-sync-<yyyy-MM-dd>.ftsync.
Show confirmation dialog after save.
```

### Step 10 ✅ — Desktop: Import UI
```
Add a "Sync Import" menu item to the desktop app.
On click: open JFileChooser file picker filtered to .ftsync files.

After file selected:
  - Run SyncImporter
  - If SyncError returned: show error dialog, no further action
  - If SyncPreview returned: show SyncPreview dialog (same logic as Android)
  - On confirm: run SyncWriter, refresh UI
  - On cancel: no changes
```

### Step 11 ✅ — End-to-end test
```
Test the full sync cycle:
1. Add bookings on Android, export sync file
2. Transfer file to desktop via USB
3. Import on desktop, confirm preview, verify data matches
4. Add bookings on desktop, export sync file
5. Transfer file to Android via USB
6. Import on Android, confirm preview, verify data matches
7. Test conflict: modify same booking on both devices, sync, verify newer version wins
8. Test error handling: import a corrupted file, verify no data changes
9. Test missing reference: import a file with a booking referencing an unknown account,
   verify skippedCount is correct and remaining import succeeds
```

---

### SHOULD HAVE — Desktop Feature Completion

### Step 12 ✅ — Desktop: IPinStore implementation
```
Implement ISettingsRepository-based PIN storage for desktop using the app_settings
SQLDelight table added in Step 3.

Create DesktopPinStore in desktopApp (or desktopMain) implementing IPinStore:
  - hasPin(): SELECT value FROM app_settings WHERE key = 'pin_hash'
  - savePin(hash): UPSERT into app_settings
  - getPin(): SELECT, return null if not found
  - clearPin(): DELETE WHERE key IN ('pin_hash', 'pin_salt', 'pin_enabled')

Wire up in AppServices.

Add a PIN settings option to the desktop app (Settings menu or toolbar):
  - Enable/disable PIN
  - Set/change PIN (same flow as Android PinSettingsDialog — 4-digit entry,
    confirmation step, SHA-256 hash via CryptoExpect)

Add PIN lock screen on desktop app startup if a PIN is set.
```

### Step 13 ✅ — Move buildInstallmentGroups() to commonMain
```
The buildInstallmentGroups() helper function currently lives in the Android module
(app/src/main/java/io/github/willywonka644/fintracker/ui/installments/InstallmentsScreen.kt).

Move it to shared/commonMain so it can be used on desktop. Suggested location:
  shared/src/commonMain/kotlin/io/github/willywonka644/fintracker/installments/InstallmentGrouping.kt

Update the Android InstallmentsScreen to import it from commonMain.
Confirm Android still compiles and the installments screen still works.
```

### Step 14 ✅ — Desktop: Recurring Rules UI
```
Build a desktop recurring rules screen in desktopApp/ui/recurring/:

RecurringRulesScreen:
  - Lists all recurring rules for the selected account (or all accounts)
  - Columns: description, amount, frequency, next execution date, remaining executions
  - Add / Edit / Delete actions

AddEditRecurringRuleDialog:
  - Fields: description (required), amount (decimal), account (dropdown),
    frequency (WEEKLY / MONTHLY / YEARLY), next execution date (date input),
    remaining executions (optional integer or "unlimited" toggle), category (dropdown)
  - Writes via IRecurringRuleRepository

Add a "Recurring Rules" menu item to main navigation.
IRecurringRuleRepository is already wired in AppServices — no backend changes needed.
```

### Step 15 ✅ — Desktop: Reconciliation UI
```
Build a desktop reconciliation screen in desktopApp/ui/reconciliation/:

ReconciliationScreen:
  - Account selector (dropdown or from current account context)
  - "Expected balance" field (read-only, computed by ReconciliationService)
  - "Real balance" input field (user types the actual bank balance)
  - Difference label (real - expected)
  - "Create correction booking" button — calls ReconciliationService.createCorrectionBooking()
  - History panel: list of past reconciliation events from getReconciliationHistory()

ReconciliationService is already in commonMain — no backend changes needed.
Add a "Reconciliation" menu item to main navigation.
```

### Step 16 ✅ — Desktop: Audit Screen
```
Build a desktop audit screen in desktopApp/ui/audit/:

AuditScreen:
  - Account selector
  - Header: "X of Y bookings verified"
  - List of all bookings for the account
  - Each row: date, description, amount (green/red), category dot, verified checkbox
  - Clicking the checkbox toggles Booking.isVerified via IBookingRepository
  - Verified rows shown with muted style; unverified rows shown prominently

Booking.isVerified is already stored in SQLDelight — no schema changes needed.
Add an "Audit" menu item to main navigation.
```

### Step 17 ✅ — Desktop: Installments UI
```
Build a desktop installments screen in desktopApp/ui/installments/
(requires Step 13 to be complete first).

InstallmentsScreen:
  - Lists installment groups (from InstallmentGrouping.buildInstallmentGroups())
  - Each group row: description, total amount, paid/total count, next date, category
  - Progress indicator (X of Y paid)
  - Delete group action (confirmation dialog — deletes all bookings in the group)

AddInstallmentDialog:
  - Fields: description, total amount, number of instalments, start date,
    account (dropdown), category (dropdown)
  - Creates N bookings with the same installmentGroupId, spaced monthly

Add an "Installments" menu item to main navigation.
```

### Step 18 — Commit and merge
```
Commit all changes with message "feat: file-based sync and desktop feature completion Phase 4 complete".
Merge feature/phase4-sync into main and push to GitHub.
Tag this release: git tag v1.0.0-cross-platform
Push tag to GitHub.
```

---

## Transfer Methods (No Code Required)

The sync file transfer itself is handled by the user — no networking code needed:

| Method | How |
|---|---|
| USB cable | Connect phone to PC, copy .ftsync file |
| Local network share | Drop file in a shared Windows folder |
| Syncthing | Third-party local sync tool, no cloud |

---

## Won't Have in Phase 4

| Feature | Reason | Target |
|---|---|---|
| Desktop analytics / charts | Vico is Android-only; desktop charting library not yet chosen | Phase 5 |
| Desktop attachment UI (image picker / viewer) | Low priority; no desktop `IAttachmentStorage` implementation yet | Phase 5 |
| Binary attachment transfer via sync | Increases complexity and file size significantly | Phase 5 |
| Automatic or background sync | Violates local-first, user-controlled philosophy for now | Phase 5+ |
| Wi-Fi / network sync | Same SyncExporter logic reused but needs Ktor + mDNS work | Phase 5 |
| Cloud sync | Not in scope — data stays local by design | Phase 6+ |

---

## Testing Checklist

### Sync
- [ ] Export from Android produces valid .ftsync file (SAF file picker used)
- [ ] Export from Desktop produces valid .ftsync file
- [ ] All `attachmentPath` fields are null/absent in exported .ftsync
- [ ] Import on Android shows correct preview counts
- [ ] Import on Desktop shows correct preview counts
- [ ] New records sync correctly in both directions (Bookings, Accounts, Categories, RecurringRules)
- [ ] Conflict resolution: newer `lastModifiedAt` wins for all record types
- [ ] Cancel import leaves data unchanged
- [ ] Corrupted file shows error dialog, no data changes
- [ ] Unknown version field shows version error dialog, no data changes
- [ ] Booking with unknown accountId is skipped; remainder imports correctly
- [ ] Booking with unknown categoryId imports with category = null; warning shown
- [ ] Transaction rollback: partial write failure leaves database unchanged
- [ ] No data loss in any test scenario
- [ ] Both apps remain fully functional after sync

### Desktop Feature Completion
- [ ] PIN can be set, changed, and cleared on desktop
- [ ] Desktop locks on startup if PIN is set
- [ ] Recurring rules: add, edit, delete on desktop
- [ ] Reconciliation: compute expected balance, create correction booking, view history
- [ ] Audit: toggle isVerified on bookings, header count updates
- [ ] Installments: view groups, add instalment set, delete group

### Release
- [ ] GitHub tag v1.0.0-cross-platform pushed

---

## Future Phases

### Phase 5 — Wi-Fi Sync (Local Network)

Once file-based sync is stable, the next evolution removes the manual file step entirely.

**Architecture:**
- Desktop runs a lightweight local HTTP server using **Ktor** on a fixed local port.
- Android discovers the desktop on the same Wi-Fi network using **mDNS/NSD** (Android
  Network Service Discovery API).
- Same `SyncExporter` / `SyncImporter` / `SyncWriter` logic from Phase 4 is reused
  without modification — only the transport layer changes (network instead of file).
- User taps "Sync over Wi-Fi" on Android → app discovers desktop → sync runs in the
  background → result summary shown.

**Other Phase 5 scope:**
- Desktop analytics and charts (requires choosing a desktop-compatible charting library)
- Desktop `IAttachmentStorage` implementation (`java.nio.file`-based, storing to
  `{user.home}/FinTracker/attachments/`)
- Desktop attachment UI (image picker, viewer)
- Optional: binary attachment transfer as a sidecar to the sync payload

---

### Phase 6 — Self-Hosted Server (Raspberry Pi)

For households with multiple devices or when Wi-Fi direct sync is inconvenient,
a self-hosted sync server provides cloud-like comfort without giving data to third parties.

**Architecture:**
- A Raspberry Pi (or any always-on home server) runs a lightweight FinTracker sync
  server — a small Ktor application with a persistent SQLite database.
- All devices (Android phones, desktop) sync automatically to the Pi whenever they are
  on the home network.
- The Pi runs 24/7, so sync is seamless — open the app, data is up to date.
- Data **never leaves the home network**. No accounts, no subscriptions, no cloud vendor.
- End-to-end encryption between devices and the Pi is strongly recommended.

**Same core logic reused:**
- `SyncExporter` / `SyncImporter` / `SyncWriter` from Phase 4 unchanged.
- Transport layer: HTTPS to the Pi (self-signed cert or local CA).
- Conflict resolution: same last-write-wins strategy, now arbitrated by the server.
- The Pi becomes the single source of truth between syncs.

**This phase is optional** — file-based sync (Phase 4) and Wi-Fi sync (Phase 5)
already cover most use cases. Phase 6 is for users who want zero-friction
always-on sync without any compromise on data ownership.
