# FinTracker — Phase 2: SQLDelight Migration

## Goal
Replace SharedPreferences + JSON with SQLDelight (SQLite) as the persistence layer.
This makes data storage cross-platform and significantly more robust for growing data.

## Estimated Time
1–2 weeks

## Prerequisites
- Phase 1 complete and merged to main
- Android app still working correctly on device
- New Git branch created before starting

---

## Why SQLDelight

| Current (SharedPreferences + JSON) | After (SQLDelight) |
|---|---|
| Android-only | Works on Android + Desktop + iOS |
| One big JSON blob | Structured relational data |
| No querying | Full SQL queries |
| Slow for large data | Fast with indexes |
| Manual serialization | Type-safe generated code |

SQLDelight generates type-safe Kotlin code from SQL schema files.
You write SQL, it generates the Kotlin API. Works identically on all platforms.

---

## Status Overview

| Step | What | Status |
|---|---|---|
| Step 1 | Create git branch `feature/sqldelight-migration` | ✅ Done |
| Step 2 | Analyze current data structure | ✅ Done |
| Step 3 | Add SQLDelight dependency | ✅ Done |
| Step 4 | Define the SQL schema | ✅ Done |
| Step 5 | Create the database factory | ✅ Done |
| Step 6 | Implement new repositories | ✅ Done |
| Step 7 | Data migration | ✅ Done |
| Step 8 | Switch repositories | ✅ Done |
| Step 9 | Test thoroughly on device | ✅ Done |
| Step 10 | Remove old SharedPreferences code | ✅ Done |
| Step 11 | Commit and merge | ✅ Done |

---

## Step-by-Step Instructions for Claude Code

### Step 1 — Create a new Git branch
```
Create a new git branch called feature/sqldelight-migration and switch to it.
```

### Step 2 — Analyze current data structure
```
Analyze the current SharedPreferences and JSON storage implementation.
List all data that is currently persisted: what models, what fields, what relationships.
Output a complete inventory of the current data model.
```

### Step 3 — Add SQLDelight dependency
```
Add SQLDelight to the project:
- Add the SQLDelight Gradle plugin to root build.gradle.kts
- Add sqldelight runtime dependency to shared/commonMain
- Add Android driver dependency to androidMain
- Add SQLite driver dependency to desktopMain (for later)
Run Gradle sync and confirm no errors.
```

### Step 4 — Define the SQL schema
```
Create SQLDelight schema files in shared/commonMain/sqldelight/:
- accounts.sq — Account table matching the current Account data model
- bookings.sq — Booking table matching the current Booking data model
- recurring_rules.sq — RecurringRule table
- categories.sq — Category table
Include all fields from the current JSON model. Add appropriate indexes.
Generate the SQLDelight code and confirm compilation.
```

### Step 5 — Create the database factory
```
Create a DatabaseFactory in shared/commonMain that provides the SQLDelight database.
Create the Android-specific implementation in androidMain using AndroidSqliteDriver.
Keep desktopMain implementation as a stub for now (JdbcSqliteDriver — Phase 3).
```

### Step 6 — Implement new repositories
```
Create new repository implementations in androidMain using SQLDelight queries
instead of SharedPreferences. Implement the repository interfaces defined in Phase 1.
Keep the old SharedPreferences repositories — do not delete them yet.
```

### Step 7 — Data migration
```
Write a one-time migration function that:
1. Reads all existing data from SharedPreferences/JSON
2. Writes it into the new SQLDelight database
3. Marks migration as complete (so it only runs once)
This function runs on first app launch after the update.
```

### Step 8 — Switch repositories
```
Update dependency injection to use the new SQLDelight repositories instead of
the old SharedPreferences ones. The UI and business logic must not change —
only the data source switches underneath.
```

### Step 9 — Test thoroughly on device
```
Build and install on device. Verify all existing data migrated correctly.
Test all features. Confirm no data loss.
```

### Step 10 — Remove old SharedPreferences code
```
Only after confirming the migration works correctly:
Delete the old SharedPreferences repository implementations.
Delete the JSON serialization code that is no longer needed.
Run final Gradle sync and device test.
```

### Step 11 — Commit and merge
```
Commit all changes with message "feat: SQLDelight migration Phase 2 complete".
Merge feature/sqldelight-migration into main and push to GitHub.
```

---

## Risk Management

This is the highest-risk phase because it touches stored data.

**Mitigation:**
- Always keep the old SharedPreferences code until Step 10
- Test migration on your own device first
- Export a CSV backup of your data before starting (use existing export feature)
- Use Git — if anything goes wrong, revert to main

---

## Testing Checklist

- [x] CSV export of all data done before starting (backup)
- [x] App installs without crash on first launch (migration runs)
- [x] All accounts present after migration
- [x] All bookings present after migration
- [x] All recurring rules present after migration
- [x] All categories present after migration
- [x] Adding new accounts works
- [x] Adding new bookings works
- [x] Analytics screen still correct
- [x] No performance regression
- [x] GitHub push successful

---

## What Phase 2 Does NOT Do

- Does not change any UI
- Does not build the desktop app yet
- Does not implement sync
