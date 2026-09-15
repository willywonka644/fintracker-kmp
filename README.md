# FinTracker

Personal finance tracker for Android and Windows Desktop — **offline-first**, with no connection to online banking or external services.

**Current version: v0.10.0** — cross-platform (Android + Windows Desktop)

> **About this repository.** This is a code-only copy, published without the
> development history. The app is used daily with real financial data, so no database,
> backup or key is part of this tree — and the issue tracker, which discusses real
> balances, stays where it is.

---

## Philosophy

- No bank APIs, no cloud storage, no account to register anywhere
- Data stays on the devices you own — and, if you sync, on a server you run yourself
- **No third party in the data path.** The file and Wi-Fi transports involve nobody at
  all. Remote sync is reached over a Tailscale VPN, so that provider does broker the
  connection and may relay the traffic when no direct path exists — but the payload is
  encrypted end to end between your own machines, and it never reaches a service that
  could read it.
- User always confirms — no automation, no black boxes

---

## Features

### Shared (Android + Desktop)
- Account management: Girokonto, Kreditkarte, Sparkonto, Tagesgeldkonto
- Custom billing periods and spending limits with usage tracking
- Manual income and expense entries
- Recurring booking rules (weekly, monthly, yearly)
- Installment splitting for large bookings
- Effective-later bookings (future-dated, applied automatically)
- Custom categories with color coding, 144 icons, and inline creation
- Analytics: balance over time, income vs. expense, spending by category
- Manual reconciliation and an audit view
- Optional PIN lock on start
- JSON backup & restore (full app data)
- CSV export and import with column mapping
- Sync between devices — over a self-hosted server, over Wi-Fi, or by file (see [Sync](#sync))

### Android only
- QR code scanning (Girocode)
- OCR receipt scanning
- Receipt photo attachment per booking

### Desktop (Windows) only
- File-save dialog for exports and backups
- Hosts the Wi-Fi sync server that Android connects to (the fallback transport)
- Database at `%USERPROFILE%\FinTracker\fintracker.db`
- Packaged as MSI / EXE installer (bundles JVM — no Java pre-install required)

---

## Architecture

FinTracker is a **Kotlin Multiplatform (KMP)** project with four modules:

| Module | Role |
|---|---|
| `shared/` | Data models, business logic, repository interfaces, SQLDelight schema (`.sq` files), sync engine. Platform-specific code uses `expect/actual`. |
| `app/` | Android application. Compose UI, CameraX/OCR, Android services. |
| `desktopApp/` | Windows Desktop application. Compose for Desktop UI, file dialogs, `AppServices` wiring. |
| `server/` | The self-hosted sync server (Phase 7). Headless Ktor, runs on a Raspberry Pi. It uses `shared` for the merge rather than reimplementing them — a second implementation of those rules would be the most reliable way to build data loss. |

**Storage:** SQLite via [SQLDelight 2](https://cashapp.github.io/sqldelight/) — type-safe Kotlin query APIs generated from `.sq` schema files.
- Android: `AndroidSqliteDriver`, standard app data directory
- Desktop: `JdbcSqliteDriver`, `{user.home}/FinTracker/fintracker.db`

**UI:** Jetpack Compose / Compose for Desktop with Material 3 on both platforms.

---

## Tech Stack

- **Language:** Kotlin Multiplatform
- **UI:** Jetpack Compose (Android) + Compose for Desktop (Windows), Material 3
- **Database:** SQLDelight 2 (SQLite)
- **Charts:** Vico 1.13.1 (Android); the desktop draws its charts with Compose Canvas
- **Camera:** CameraX + ML Kit — Android only
- **Sync:** Ktor — a standalone `server` module for the Raspberry Pi, reached over a
  Tailscale VPN; plus a Ktor server on the desktop with JmDNS discovery and
  AES-256-GCM payloads for the Wi-Fi fallback
- **Testing:** kotlin.test, Compose UI Test (desktop), Maestro (Android)
- **Platform:** Android (min SDK 26) + Windows Desktop (JVM 17+); the sync server runs
  on anything with a JVM, in practice a Raspberry Pi

---

## How to Build

### Android
```bash
./gradlew :app:installDebug
```
Requires a connected device or running emulator (API 26+).

### Desktop (Windows)
```bash
./gradlew :desktopApp:run
```
To produce an installer:
```bash
./gradlew :desktopApp:packageDistributionForCurrentOS
```
Two tools are needed that a typical Android Studio setup does not bring: a full **JDK 21**
(the bundled JetBrains Runtime ships `jlink` but not `jpackage`, so it cannot package at
all) and **WiX 3.x** (`jpackage` drives WiX for `.msi`/`.exe`; the 4 and 5 lines do not
work). `./gradlew :desktopApp:createDistributable` skips WiX and yields a runnable app
directory — still needs the `jpackage` JDK. See `RELEASE.md`.

---

## Sync

Every participant holds a full copy of the data — both devices, and the sync server if
one is used. Sync reconciles them; there is no server of record and nothing runs in the
background.

**Three transports, one payload.** The payload is a structured JSON document with
accounts, bookings, categories, and recurring rules.

- **Self-hosted server (Phase 7)** — the normal path. A Ktor server on a Raspberry Pi
  the owner runs themselves; each device pushes its full dataset and pulls the merged
  one. Reachable over a Tailscale VPN rather than a forwarded port: there is no port
  open to the internet, and the server binds to the VPN interface only. Requests carry
  a shared API key. The Pi is a third participant in the existing merge, not a new
  protocol and not an authority — it holds a copy like every other device.

  The honest limit of this arrangement: the VPN is exactly as strong as the account it
  is logged in with. That account, not the transport, is the largest real risk, and it
  is the reason two-factor authentication was made part of the setup rather than a
  suggestion. WireGuard was considered instead and rejected — not on cryptographic
  grounds, but because it ships no defaults and a misconfiguration is silently wrong.
- **Wi-Fi (Phase 5)** — the fallback, and deliberately kept as one. The desktop runs a
  Ktor server that Android finds over mDNS; devices are paired with an 8-digit code and
  payloads are encrypted with AES-256-GCM. Both devices must be on the same network.
  It answers when the Pi is off or being rebuilt, which a sync with no second path
  would not.
- **File-based (Phase 4)** — export a `.ftsync` file on one device, carry it over by
  USB, shared folder, or network share, and import it on the other.

**Merge rules.** Records are merged per id, last writer wins by `lastModifiedAt`.
Deletions travel as tombstones — a deleted row is kept with a `deleted` flag rather
than removed, because the merge is a union and would otherwise hand the record back
from the other device on the next run.

Two consequences worth knowing:

- Attachment photos are **not** synced. Binary transfer is out of scope.
- Update both devices before syncing after a schema change. Unknown JSON fields are
  ignored on read, and the merge compares timestamps with strictly-greater — an older
  build drops the new field but keeps the timestamp, after which the two sides are
  tied and the change can never propagate.

---

## Roadmap

| Phase | Description | Status |
|---|---|---|
| Phase 1 | KMP project structure, shared module, repository interfaces | ✅ Complete |
| Phase 2 | SQLDelight migration (replace SharedPreferences/JSON storage) | ✅ Complete |
| Phase 3 | Desktop App MVP (Windows, Compose for Desktop) | ✅ Complete |
| Phase 4 | File-based sync between Android and Desktop (`.ftsync`) | ✅ Complete |
| Phase 5 | Wi-Fi sync (Ktor + AES-256-GCM encrypted payloads) | ✅ Complete |
| Phase 6 | "Trust Blue" redesign (Android + Desktop), Android navigation restructure (account detail screen removed, central tools via "Mehr"), unified booking/recurring/installment sheets, stability review of the balance/analytics logic (see `docs/FinTracker-Phase6-CodeReview.md`) | ✅ Complete |
| Phase 6.5 | Test coverage before touching sync again: shared logic, desktop UI, Android end-to-end, schema migrations, CI (see `docs/FinTracker-Phase6.5-Testing.md` and [Testing](#testing)) | ✅ Complete |
| Phase 7 | Self-hosted sync server (Raspberry Pi) over Tailscale — sync from anywhere via a server the owner controls, no forwarded port | ✅ Complete |

---

## Testing

The suite exists because a cascading delete once removed posted bookings along with
a recurring rule. Everything below guards behaviour that costs real data when it
breaks.

625 tests across four levels, all green as of v0.10.0.

| Layer | What it covers | How to run |
|---|---|---|
| `shared` — 465 tests, 43 classes | Sync merge and tombstones, reconciliation, analytics, text comparison, description suggestions and cleanup, CSV parsing, backup/restore, installment grouping, category rules, **schema migrations** | `./gradlew :shared:desktopTest` |
| `server` — 44 tests, 8 classes | Sync routes, API-key guard, bind address (the server must not answer outside the VPN) | `./gradlew :server:test` |
| `desktopApp` — 90 tests, 14 classes | Compose UI: booking list, reconciliation dialog, recurring-rule editor, sync dialogs against a scripted server, plus one end-to-end run against an in-memory database | `./gradlew :desktopApp:test` |
| `app` — 26 tests, 1 class | The Android unit tests that survived the move of shared logic into `commonTest` | `./gradlew :app:testDebugUnitTest` |
| Android — 5 Maestro flows | Launch, booking smoke test, error tolerance, screen tour, input protection | `maestro test .maestro/` |

Green shared tests do not prove the Android app compiles: the same `commonMain` file can
build for desktop and fail for Android. `:app:assembleDebug` builds the Android target of
`shared` as well, which is what actually proves it — and that is why CI runs it.

`DatabaseMigrationTest` is the one that cannot be fixed after the fact: it builds a
v1 database by hand and migrates it forward, because `Schema.create()` always
produces the newest schema and therefore never exercises the upgrade path.

**CI** runs all four levels on every push and pull request
(`.github/workflows/tests.yml`): shared, server, the Android build plus its unit tests,
and the desktop UI tests under `xvfb`. Maestro is
local only — it needs an emulator.

> Maestro drives the **installed** app against the **real** database on the device.
> Run it on an emulator or a test device, never on a phone holding real account data.

---

## Licence

**None.** The code carries no licence, which under copyright means it may be read but not
reused. That is deliberate rather than an omission: this is a personal finance app the
author uses daily, published so the work can be looked at, not so it can be taken.

Third-party files are a separate matter and do carry their licences — see
[LICENSES/](LICENSES/).

---

## Disclaimer

This project is a **learning and architecture playground** for Android, Kotlin, and Jetpack Compose.
The focus is on **correctness, clarity, and maintainability** — not feature bloat.
