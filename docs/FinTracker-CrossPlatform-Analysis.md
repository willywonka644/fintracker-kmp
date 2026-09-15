# FinTracker â€“ Cross-Platform Architecture Analysis

## Context

FinTracker is currently a native Android app (Kotlin + Jetpack Compose, Material 3).
The goal is to expand to **Android + Windows Desktop + (ideally) iOS** while keeping
the local-first, no-cloud philosophy intact â€” including local-only data sync.

This document evaluates the technology options and recommends a path forward.

---

## Option 1: Compose Multiplatform (Recommended)

**What it is:** JetBrains' extension of Jetpack Compose to run on Android, Desktop
(Windows/macOS/Linux), iOS, and Web â€” all from shared Kotlin code.

**Why it fits FinTracker best:**

- You already write Kotlin and Jetpack Compose â€” the migration is evolutionary, not a rewrite
- Your analytics module is already pure Kotlin with no Android dependencies â€” it works as-is
- Shared UI code across all platforms with platform-specific adaptations where needed
- Material 3 support across platforms
- Strong IDE support (Android Studio for Android, IntelliJ for Desktop/iOS)
- JetBrains + Google actively co-develop this

**What changes:**

- Project restructures into a Kotlin Multiplatform (KMP) project with shared modules
- Persistence layer needs abstraction (SharedPreferences is Android-only)
- Platform-specific code goes into `androidMain`, `desktopMain`, `iosMain` source sets
- Build system uses Gradle with KMP plugin

**Effort estimate:** Medium. Most business logic transfers directly. UI needs adaptation
but not a rewrite. Persistence layer needs the most work.

**Risk:** iOS support in Compose Multiplatform is stable but younger than Android/Desktop.
Desktop support is mature and production-ready.

---

## Option 2: Flutter

**What it is:** Google's cross-platform framework using Dart language.

**Impact:** Complete rewrite of the entire app in Dart. None of your existing Kotlin code
transfers. Different paradigm, different tooling, different language.

**Pros:** Mature cross-platform support, large ecosystem, excellent desktop support.

**Cons:** Full rewrite. You lose all existing code and Kotlin expertise. Dart is a different
language. The "local-first" persistence patterns need to be rebuilt from scratch.

**Effort estimate:** High. Full rewrite.

**Verdict:** Not recommended. The cost of throwing away a working codebase is too high
when Compose Multiplatform offers a migration path.

---

## Option 3: React Native / Electron

**What it is:** JavaScript/TypeScript-based cross-platform (React Native for mobile,
Electron for desktop).

**Impact:** Complete rewrite in TypeScript. Two separate frameworks for mobile and desktop,
or use frameworks like Tauri for desktop.

**Pros:** Massive ecosystem, easy to find resources.

**Cons:** Full rewrite. Two different frameworks. JavaScript/TypeScript is a completely
different world from Kotlin. Performance overhead. Electron apps are notoriously heavy.

**Effort estimate:** High. Full rewrite + learning curve.

**Verdict:** Not recommended for the same reasons as Flutter, plus the desktop story
is messier.

---

## Option 4: Keep Android + Build Separate Desktop App

**What it is:** Keep the current Android app as-is. Build a separate Windows desktop app
(e.g., with Compose for Desktop or WPF/WinUI) that shares the data format but not code.

**Pros:** No risk to existing Android app. Desktop app can be built independently.

**Cons:** Two separate codebases to maintain. Features must be implemented twice.
Bugs must be fixed twice. Divergence over time is inevitable.

**Effort estimate:** Low initially, high long-term.

**Verdict:** Viable as a short-term strategy but not sustainable.

---

## Recommendation: Compose Multiplatform

### Migration Strategy (Phased)

**Phase 1 â€“ Extract shared modules (no UI changes)**

Restructure the project into a KMP project layout:

```
fintracker/
â”œâ”€â”€ shared/                     # Kotlin Multiplatform shared code
â”‚   â”œâ”€â”€ commonMain/             # Business logic, models, analytics
â”‚   â”œâ”€â”€ androidMain/            # Android-specific implementations
â”‚   â”œâ”€â”€ desktopMain/            # Desktop-specific implementations
â”‚   â””â”€â”€ iosMain/                # iOS-specific (future)
â”œâ”€â”€ androidApp/                 # Android app (Compose)
â”œâ”€â”€ desktopApp/                 # Desktop app (Compose for Desktop)
â””â”€â”€ iosApp/                     # iOS app (future)
```

Move these to `commonMain` (they should work as-is or with minor changes):
- Data models (`Account`, `Booking`, `RecurringRule`, etc.)
- Analytics module (already pure Kotlin)
- Business logic (balance calculations, period handling)
- Repository interfaces

**Phase 2 â€“ Abstract the persistence layer**

Replace `SharedPreferences` with a cross-platform storage solution:

```kotlin
// Common interface
expect class PlatformStorage {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

// Android implementation (androidMain)
actual class PlatformStorage(context: Context) {
    private val prefs = context.getSharedPreferences(...)
    // ...
}

// Desktop implementation (desktopMain)
actual class PlatformStorage(dataDir: Path) {
    // File-based JSON storage
    // ...
}
```

Alternatives: Use a KMP-compatible database like SQLDelight (SQLite across platforms)
which would also be a significant upgrade from SharedPreferences for growing data.

**Phase 3 â€“ Desktop app with shared UI**

Build the Windows desktop app using Compose for Desktop. Most Compose UI code
transfers with platform-specific adjustments (window management, file dialogs, etc.).

**Phase 4 â€“ Local sync**

See sync section below.

**Phase 5 â€“ iOS (optional, later)**

Compose Multiplatform for iOS. Smallest priority based on current goals.

---

## Local Sync (No Cloud)

Since the philosophy is "no cloud," here are the viable local sync approaches:

### Approach A: File-Based Sync via Shared Folder (Simplest)

Both apps read/write the same JSON data file from a shared location
(e.g., a folder synced via Syncthing, a USB-mounted folder, or a local NAS).

**How it works:**
1. Desktop app stores data in a configurable folder (e.g., `~/FinTracker/`)
2. Android app can import/export to/from a folder accessible via USB or local network
3. User triggers sync manually (stays true to "user always confirms" philosophy)

**Pros:** Dead simple. No networking code. User controls when and how.

**Cons:** Not automatic. Requires manual action or third-party folder sync tool.

### Approach B: Local Network Sync (Wi-Fi Direct)

Devices discover each other on the local network and sync directly.

**How it works:**
1. Desktop app runs a lightweight sync server on the local network
2. Android app discovers it via mDNS/Bonjour
3. On sync trigger, devices exchange data over local Wi-Fi
4. Conflict resolution: last-write-wins or user-confirms

**Pros:** Seamless when on same network. No cloud. No manual file handling.

**Cons:** More complex to implement. Requires both devices on same network.
Needs careful conflict resolution logic.

### Approach C: Hybrid (Recommended)

Start with **Approach A** (file-based, manual sync) because:
- It works immediately with the existing JSON export/import
- It's transparent and predictable (user understands exactly what happens)
- It aligns perfectly with the "no magic" philosophy
- It's zero additional code beyond what you already have

Then evolve to **Approach B** (local network sync) as a later feature when
the cross-platform foundation is stable.

---

## Impact on Current Roadmap

The cross-platform pivot affects the feature roadmap. Here's how to sequence it:

### Revised Priority Order

1. **KMP project restructure** (Phase 1) â€” before any new features
2. **Persistence abstraction** (Phase 2) â€” enables desktop app
3. **Desktop app MVP** (Phase 3) â€” basic account + booking management
4. **File-based sync** â€” JSON import/export already exists, just polish it
5. **Resume feature roadmap** â€” Recurring Bookings, Categories, etc.
   (now implemented once in `commonMain`, available on all platforms)

### What this means practically

New features like Recurring Bookings or Category System should be built in the
shared module from the start. This way, every feature you build automatically
works on both Android and Desktop.

---

## Key Decisions Needed

Before starting implementation, you should decide:

1. **Persistence upgrade:** Stay with JSON files (simpler, works now) or migrate
   to SQLDelight/SQLite (more robust, better for growing data, built-in KMP support)?

2. **Desktop MVP scope:** Should the desktop app have full feature parity from day one,
   or start with a subset (e.g., view accounts + bookings, add bookings)?

3. **Sync priority:** Start with file-based sync immediately, or focus on getting
   the desktop app working first and add sync later?

4. **iOS timeline:** Is iOS a "nice to have someday" or a concrete goal for 2026?