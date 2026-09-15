# FinTracker — Phase 5: Wi-Fi Sync + Desktop UI Overhaul

## Status: ✅ Complete

---

## Goal

Three goals in one phase:

1. **Wi-Fi sync** — Remove the manual file transfer step. The Desktop app runs a local Ktor
   HTTP server; Android discovers it via mDNS/NSD and pushes or pulls the sync payload
   over the local network. The same `SyncExporter` / `SyncImporter` / `SyncWriter` engine
   from Phase 4 is reused without modification — only the transport changes.

2. **Desktop UI overhaul** — The desktop app reached feature correctness in Phase 4 but
   several UX fundamentals were missing: scrollbars, filters, search, a real date picker,
   dark mode, and window position memory. These make the app daily-driveable.

3. **Unsynced changes tracking** — A live counter and badge tracks how many changes have
   not yet been synced. On close, the app prompts the user to sync before exiting.

This stays true to the FinTracker philosophy: local-first, transparent, user-controlled.

---

## Architecture Overview

### Wi-Fi Sync

```
Android (client)                      Desktop (server)
─────────────────────                 ─────────────────────────────
NsdManager.discoverServices()  ──→    JmDNS registers _fintracker._tcp.local.
                                      Ktor listens on fixed port (e.g. 54321)

SyncExporter.export()          ──→    POST /sync/import  →  SyncImporter.import()
                               ←──    SyncPreviewResponse (JSON)

User confirms on Android
SyncWriter.write(preview)      (local write on Android)

GET /sync/export               ──→    SyncExporter.export()
SyncImporter.import(json)      (local write on Android)
SyncWriter.write(preview)
```

**Key design decisions:**
- The Desktop is always the **server**. Android is always the **client**. This avoids
  the complexity of peer-to-peer negotiation.
- The server exposes two endpoints: `GET /sync/export` (desktop → Android direction)
  and `POST /sync/import` (Android → desktop direction, returns preview JSON).
- A second `POST /sync/confirm` endpoint commits a previously previewed import on the
  desktop side after the user taps Confirm on Android.
- The same `SyncImporter` / `SyncWriter` logic handles both directions — no duplication.
- **All payloads are encrypted with AES-256-GCM** using the pairing token as the
  key material (derived via SHA-256). HTTP carries ciphertext only — sniffing the
  connection reveals nothing. Authentication is implicit: a wrong token causes
  decryption to fail with an authentication tag mismatch, so no separate
  `Authorization` header is needed. The existing `CryptoExpect` / `CryptoActual`
  pattern is extended with two new `expect` functions (`aesGcmEncrypt`,
  `aesGcmDecrypt`); both platforms use identical `javax.crypto` implementations.

### Sync Engine Reuse

| Phase 4 component | Phase 5 role | Changes needed |
|---|---|---|
| `SyncExporter` | Unchanged — called by Ktor `GET /sync/export` | None |
| `SyncImporter` | Unchanged — called by Ktor `POST /sync/import` | None |
| `SyncWriter` | Unchanged — called after user confirmation | None |
| `SyncPayload` / `SyncPreview` | Unchanged | None |
| `SyncError` | Unchanged | None |

---

## What Was Implemented

### Wi-Fi Sync (P0)

| Step | Feature | Status |
|---|---|---|
| 1 | Create feature/phase5-wifi-sync branch | ✅ Done |
| 2 | Add Ktor + JmDNS dependencies to desktopApp | ✅ Done |
| 3 | `KtorSyncServer` — Ktor Netty engine + JmDNS registration + sync routes | ✅ Done |
| 4 | Desktop Wi-Fi Sync dialog (start/stop, status, IP, pairing code display) | ✅ Done |
| 5 | Android NSD discovery (`WiFiSyncDiscovery.kt`) | ✅ Done |
| 6 | Android Wi-Fi sync UI (`WiFiSyncScreen.kt`) with push/pull flow and state machine | ✅ Done |
| 7a | `aesGcmEncrypt` / `aesGcmDecrypt` expect functions in `CryptoExpect.kt` | ✅ Done |
| 7b | `SyncEncryption.kt` helper (`encryptSyncPayload` / `decryptSyncPayload`) in commonMain | ✅ Done |
| 7c | Encryption wired into `KtorSyncServer` (all routes encrypt/decrypt) | ✅ Done |
| 7d | Decryption wired into Android Wi-Fi sync client; pairing token entry + error handling | ✅ Done |
| 7e | Pairing code displayed in `WiFiSyncDialog.kt` with regenerate button | ✅ Done |

### Desktop UI Overhaul (P0)

| Step | Feature | Status |
|---|---|---|
| 8 | Vertical scrollbars on all dialogs and lists | ✅ Done |
| 9 | Booking list filters (date range, category, status) | ✅ Done |
| 10 | Search across bookings (real-time, combined with filters) | ✅ Done |
| 11 | Reusable `DesktopDatePicker.kt` composable with calendar grid | ✅ Done |
| 12 | Window size and position memory (persisted to `app_settings`) | ✅ Done |
| 13 | Dark mode toggle (light/dark, persisted, applies without restart) | ✅ Done |
| 14 | General layout and spacing improvements (padding, empty states, color coding) | ✅ Done |

### Unsynced Changes Tracking (P0)

| Step | Feature | Status |
|---|---|---|
| 15 | `unsynced_changes` counter in `app_settings` — increments on every mutation, resets on sync | ✅ Done |
| 16 | Unsynced badge in Android top bar and Desktop toolbar | ✅ Done |
| 17 | Close-app prompt when unsynced changes exist (Android + Desktop) | ✅ Done |

### Not Implemented — Moved to Later Phases

| Step | Feature | Reason |
|---|---|---|
| 18–19 | Desktop analytics with KoalaPlot (line chart, bar chart, category breakdown) | ❌ P1 — out of scope for Phase 5; moved to a later phase |
| 20–21 | Desktop `IAttachmentStorage` implementation + image picker + viewer | ❌ P1 — out of scope for Phase 5; moved to a later phase |

---

## Won't Have in Phase 5

| Feature | Reason | Target |
|---|---|---|
| Cloud sync | Not in scope — data stays local by design | Phase 7+ |
| Android-to-Android sync | Desktop is always the server; phone-to-phone not in scope | Later phase |
| Background / automatic sync | Violates local-first, user-controlled philosophy | Later phase |
| Full TLS / HTTPS transport | AES-256-GCM payload encryption covers the data; full TLS adds cert management complexity for marginal gain on a home LAN | Later phase |
| Binary attachment transfer | Requires sync format v2.0 and Android refactor | Later phase |

---

## Known Limitations (by design)

- Sync only works on the same Wi-Fi network
- Desktop must be running and open during sync
- No automatic background sync
- These constraints will be addressed in Phase 7 (Raspberry Pi)

---

## Future Phases

### Phase 6 — Design Overhaul

A full visual overhaul of both the Android and Desktop apps: modern typography,
consistent spacing, refined color system, polished dark theme, and improved
information hierarchy throughout all screens.

### Phase 7 — Self-Hosted Server (Raspberry Pi)

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
already cover most use cases. Phase 7 is for users who want zero-friction always-on
sync without any compromise on data ownership.
