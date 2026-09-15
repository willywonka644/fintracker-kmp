# FinTracker — Phase 6 Redesign · Claude Code Implementation Plan

**Design:** "Trust Blue" (Direction A) · dark + light · Android + Windows Desktop
**Stack:** Kotlin Multiplatform · Jetpack Compose + Compose for Desktop · Material 3 · SQLDelight · Vico
**Golden rule:** This is a **visual re-skin**, not a feature change. No new features, no DB/model changes. Every screen already exists — we restyle it to match the prototype.

**How to use this doc:** Work top-to-bottom, one step per branch/commit. Paste each *"Task for Claude Code"* block verbatim into Claude Code. After each step, build and compare against the matching prototype screen (`FinTracker Prototype.html` for Android, `FinTracker Desktop.html` for Desktop — open `handoff/reference.html` for a side-by-side).

The two platforms **share the same tokens**. Build Android first (Steps 1–8); Desktop (Steps 9–12) then reuses the same colors/type/components, only the layout shell differs.

---

## Reference tokens (single source of truth)

| Token | Dark | Light |
|---|---|---|
| Accent / primary | `#6E7BFF` | `#5560FF` |
| App background | `#0B0E16` | `#EEF0F8` |
| Card surface | `#161A28` | `#FFFFFF` |
| Input / nested surface | `#1D2233` | `#F6F7FB` |
| Hairline outline | white 7% | `#141C2D` 8% |
| Text primary | `#FFFFFF` | `#141828` |
| Text secondary | `#8A90A6` | `#6B7185` |
| Text faint | `#5A6076` | `#9AA0B4` |
| **Income (green)** | `#34D399` | `#10B981` |
| **Expense (red)** | `#FB7185` | `#F43F5E` |
| Hero gradient | `#5560FF → #7E74FF → #A99BFF` (135°) | same |
| Radii | chip 10 · card 16 · hero/sheet 20 dp | same |
| Font | Manrope (400–800) | same |

All of these are already encoded in the drop-in files under `handoff/theme/`.

---

## STEP 0 — Branch + drop in the theme ✅ DONE

> **Task for Claude Code**
> Create a branch `phase6-trust-blue`. Replace the four theme files in
> `app/src/main/java/io/github/willywonka644/fintracker/ui/theme/` with the versions I'm providing
> (`Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`) and add the new file `FinColors.kt`
> in the same package.
> Key behavioral changes to call out and keep:
>  • `FinTrackerTheme` no longer takes a `dynamicColor` param — dynamic color is
>    removed so the app looks identical on all devices. Remove any caller that
>    passes `dynamicColor = ...`.
>  • A new `FinColors` theme extension is provided via `LocalFinColors`; access it
>    as `FinTheme.colors.income` / `.expense` / `.heroGradient` / `.textSub` etc.
> Then: download Manrope (Regular/Medium/SemiBold/Bold/ExtraBold) and place the ttf
> files in `app/src/main/res/font/` as `manrope_regular.ttf` … `manrope_extrabold.ttf`
> so `R.font.*` resolves in `Type.kt`. Build and confirm the app compiles and the
> existing screens now render in blue instead of purple.

✅ **Done when:** app builds, no purple anywhere, money still uses default colors (we fix those per-screen next).

---

## STEP 1 — Reusable components (the design kit) ✅ DONE

Create these small composables once; every screen uses them. Put them in a new package `ui/components/`.

> **Task for Claude Code**
> Create the following reusable composables in `io.github.willywonka644.fintracker.ui.components`,
> styled per the prototype. Use `MaterialTheme.colorScheme` + `FinTheme.colors`,
> `MaterialTheme.shapes`, and `MaterialTheme.typography` — no hard-coded hex.
>  1. `MoneyText(amount: BigDecimal, style, emphasize=true)` — formats EUR German
>     style ("1.234,56 €"), colors it `FinTheme.colors.income` if ≥ 0 else `.expense`
>     (or `onSurface` when `emphasize=false`), `fontFeatureSettings="tnum"`.
>  2. `FinCard(onClick?, content)` — Surface, `shapes.medium`, `surface` color,
>     1dp `outline` border, 16dp padding.
>  3. `CategoryChip(name, size=40.dp)` — circle filled with `category(name)` at 15%
>     alpha, the category icon centered in full-strength color. (Reuse existing
>     `CategoryIcons` mapping.)
>  4. `BalanceHero(title, amount, periodLabel, sub?)` — rounded 20dp Box with
>     `FinTheme.colors.heroGradient` background, white text, two decorative circles,
>     an optional pill row for in/out. Used on dashboard + account detail.
>  5. `FilterChipRow(items, selectedIndex, onSelect)` — horizontal scrollable
>     M3 `FilterChip`s; selected = `primary` bg / `onPrimary` text.
>  6. `SectionHeader(title, action?)` — bold title + optional trailing accent action.
>  7. `StatusPill(text, color)` and `FlagBadge(text, icon?)` — small rounded tags for
>     "Gebucht/Geplant" and "Dauer/Korrektur/Rate".
>  8. `InOutSummary(income, expense)` — two side-by-side FinCards with up/down icon.
> Match radii, paddings and font weights to the prototype. Add `@Preview`s in both
> light and dark.

✅ **Done when:** a preview screen shows all components correctly in light + dark.

---

## STEP 2 — Navigation shell (bottom bar) ✅ DONE

> **Task for Claude Code**
> Restyle the Android bottom navigation to the prototype: a floating rounded
> (`shapes.large`) `NavigationBar` inset 12dp from screen edges, sitting on the app
> `background`, with a 1dp `outline` border and `surface` fill. Four destinations —
> Übersicht (grid), Buchungen (list), Auswertung (bar-chart), Mehr (gear). Selected
> item: `primary` icon + label + a 5dp dot indicator above it; unselected: `textFaint`.
> Keep the existing NavHost/destinations and routes — only restyle. The big "+" add
> action is a `FloatingActionButton` (rounded 16dp, `heroGradient` background) docked
> bottom-right above the bar on Übersicht and Buchungen, NOT a nav item.

✅ **Done when:** nav matches prototype, all existing routes still work.

---

## STEP 3 — Übersicht (Dashboard) ✅ DONE

> **Task for Claude Code**
> Rebuild the Übersicht screen layout to match the prototype (keep the existing
> ViewModel + data):
>  • Large header "Übersicht" + subtitle "Mai 2026 · N Konten", with search + bell
>    icon buttons (icon buttons in `surface` tiles, `shapes.small`).
>  • `BalanceHero` showing Gesamtvermögen (sum of accounts) with a period pill and an
>    in/out row (Einnahmen green ↑ / Ausgaben red ↓).
>  • "Konten" `SectionHeader` + a column of account `FinCard`s (icon tile, name, sub,
>    balance via `MoneyText`, chevron). Credit accounts over limit show a red progress
>    bar + "Limit überschritten" line. Tapping a card → existing account-detail route.
>  • "Ausgaben nach Kategorie" `FinCard` containing a donut (Step 6) + top-4 category
>    legend with %.
> Use `LazyColumn`. Spacing 16–20dp between sections.

✅ **Done when:** dashboard matches prototype in both themes; tapping accounts navigates.

---

## STEP 4 — Buchungen (transaction list) ✅ DONE

> **Task for Claude Code**
> Restyle the Buchungen list to match the prototype: header (account name + period
> sub), `InOutSummary`, a `FilterChipRow` (Alle / Ausgaben / Einnahmen / Daueraufträge),
> a date-group label, then bookings inside a single `FinCard` as rows separated by 1dp
> `outline` dividers. Each row: `CategoryChip`, title (+ inline `FlagBadge` for
> Dauerauftrag/Ratenzahlung "n/m"/Korrektur), "Kategorie · DD. Mon" subtitle, a green
> "geprüft" check icon when verified, and `MoneyText`. Tapping a row → existing booking
> detail. Keep filtering logic; just restyle.

✅ **Done when:** list + filters render per prototype; verified checks + flags show.

---

## STEP 5 — Add / Edit Booking (bottom sheet) ✅ DONE

> **Task for Claude Code**
> Convert the add-booking flow into a `ModalBottomSheet` (top corners `shapes.large`,
> `surfaceVariant` bg, drag handle) with the prototype's 3-step flow:
>  Step 1 "Konto wählen" — list of account rows.
>  Step 2 "Art der Buchung" — Einmalig / Ratenzahlung / Dauerauftrag option rows.
>  Step 3 form — amount display, Ausgabe/Einnahme segmented toggle (red/green tint),
>    description field, scrollable category chip picker, date field, plus Raten count
>    (rate) or Intervall (dauer) when relevant; primary "Buchung speichern" button in
>    `heroGradient`.
> Reuse the existing save/validation logic and the existing Edit entry point (Edit
> opens Step 3 pre-filled). Inputs use `surface`/`surfaceHi`, `shapes.medium`, 1dp
> outline; focus border = `primary`.

✅ **Done when:** add + edit both work through the restyled sheet.

---

## STEP 6 — Auswertungen (charts) ✅ DONE

> **Task for Claude Code**
> Restyle the analytics screen. Re-theme the existing **Vico** charts to the palette:
>  • Saldoverlauf — smooth line chart, `primary` line + soft vertical gradient fill,
>    no chart border; axis labels in `textFaint`, faint dashed gridlines (`outline`).
>  • Einnahmen vs. Ausgaben — grouped column chart, income `FinTheme.colors.income`,
>    expense `.expense`, rounded bar caps, 4 month groups, with a small legend.
>  • Ausgaben nach Kategorie — donut + ranked list with per-category progress bars
>    (bar color = `category(name)`), amount + %.
> Wrap each chart in a `FinCard` with a `SectionHeader`. Period `FilterChipRow`
> (30 Tage / Dieser Monat / 6 Monate / Jahr) at top + `InOutSummary`/KPIs. Keep all
> existing data aggregation; only restyle + recolor.

✅ **Done when:** all three charts render in-palette and match the prototype.

---

## STEP 7 — Verwaltung drill-ins

> **Task for Claude Code**
> Restyle these existing screens to the prototype (data unchanged):
>  • **Account-Detail** — back bar, compact `BalanceHero` ("Saldo im Zeitraum" + period
>    + "N von M geprüft"), credit-limit card if applicable, horizontal submenu pills
>    (Auswertungen / Daueraufträge / Ratenzahlungen / Kontoabgleich / Audit), then the
>    booking list (Step 4 rows). The ⋮ menu opens a `ModalBottomSheet` of actions.
>  • **Daueraufträge** — cards: category chip, title, interval w/ repeat icon, amount,
>    divider, "Nächste Ausführung" date. FAB "Neuer Dauerauftrag".
>  • **Ratenzahlungen** — cards with a progress bar (paid vs. total), "x von y Raten
>    offen", next-rate date; completed ones use `income` bar + "Abgeschlossen".
>  • **Kontoabgleich** — explainer, Stichtag + expected-saldo rows, actual-balance input,
>    "Abgleich durchführen" primary button, then the reconciliation history list.
>  • **Audit** — list of bookings each with a category dot, title, date, amount and a
>    round check toggle (`income` when checked); header counter "N von M geprüft".
>  • **Kategorien** — list of categories (chip + name + booking count + chevron);
>    FAB "Neue Kategorie".

✅ **Done when:** each drill-in matches its prototype screen.

---

## STEP 8 — Mehr (Settings) + polish

> **Task for Claude Code**
> Restyle the "Mehr" screen as grouped `FinCard` sections (Verwaltung / Daten / App)
> with `MenuRow`s (icon tile, label, optional subtitle, chevron or `Switch`). Keep all
> existing actions (JSON backup export/restore, CSV, WLAN-Sync, PIN toggle, About).
> Final pass: set the system status-bar/navigation-bar colors to match `background`
> per theme (edge-to-edge), check all touch targets ≥ 48dp, and verify dark+light
> across every screen.

✅ **Android done.** Tag `v0.6.0-android`.

---

## STEP 9 — Desktop: shared theme + window shell

> **Task for Claude Code**
> In `desktopApp`, apply the same `FinTrackerTheme`. Because desktop can't use
> `R.font.*`, load Manrope from `desktopApp/src/main/resources/font/` via
> `org.jetbrains.compose.resources` or `Font("font/manrope_bold.ttf", weight=...)`,
> and build the same `Typography` (identical sizes) in a desktop `Type.desktop.kt`.
> Replace the current light-lavender window with the prototype shell: a top title bar
> (app name + window controls) and a **left sidebar** (232dp): FT logo, nav groups
> "Übersicht" (Dashboard/Buchungen/Auswertungen) and "Verwaltung" (Daueraufträge/
> Ratenzahlungen/Einstellungen), active item = `primaryContainer` bg + accent bar, and
> a bottom "WLAN-Sync" status card. Content area scrolls independently.

✅ **Done when:** desktop window has the dark/light sidebar shell, theme shared with Android.

---

## STEP 10 — Desktop: Dashboard + Buchungen

> **Task for Claude Code**
> Build the desktop Dashboard: a KPI row (wide `heroGradient` Gesamtvermögen card +
> Einnahmen + Ausgaben KPI `FinCard`s), then a two-column body — left: Konten list;
> right: Saldoverlauf line chart + a row with Einnahmen/Ausgaben bars and the category
> donut. Reuse Step-1 components and Step-6 Vico charts at desktop sizes.
> Build the desktop Buchungen view: account header w/ saldo, a horizontal filter bar
> (period chips + category/status dropdowns + reset), the two charts, and a **data
> table** (columns: Datum · Beschreibung · Kategorie · Status · Betrag · actions) with
> hover row highlight, status pills, category dots, verified checks, right-aligned
> `MoneyText`. Same data/queries as Android.

✅ **Done when:** desktop dashboard + table match the prototype.

---

## STEP 11 — Desktop: remaining screens + Add dialog

> **Task for Claude Code**
> Build desktop Auswertungen (large charts + category breakdown), Daueraufträge (table),
> Ratenzahlungen (2-column progress cards), and Einstellungen (grouped rows incl. the
> "Verbunden" sync badge + PIN switch). Convert add/edit booking into a **centered
> Dialog** (540dp) matching the prototype: Konto + Art row, Richtung toggle + Betrag,
> Beschreibung, category chip wrap, date (+ Raten/Intervall), Abbrechen / Speichern.
> Reuse existing logic.

✅ **Desktop done.** Tag `v0.6.0-desktop`.

---

## STEP 12 — Cross-platform QA

> **Task for Claude Code**
> Verify both targets against the prototypes: identical palette, type scale, radii,
> category colors, income/expense semantics. Check light+dark on every screen, empty
> states, long account names, and over-limit credit display. Confirm no `dynamicColor`
> references remain and no hard-coded hex colors are left in feature code (everything
> via theme). Update screenshots in the README.

---

### File manifest (in `handoff/theme/`)
| File | Replaces / Adds |
|---|---|
| `Color.kt` | replaces template palette |
| `Theme.kt` | replaces — removes dynamicColor, adds FinColors provider |
| `Type.kt` | replaces — Manrope scale |
| `Shape.kt` | **new** — corner radii |
| `FinColors.kt` | **new** — income/expense/gradient/category extension |

> Tip: paste Step 0 + the five files first and build once. A green build there means
> every later step is pure UI work.
