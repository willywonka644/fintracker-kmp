# FinTracker — Phase 6 Abschluss: Code-Review vor Phase 7

## Ziel

Die Codebase stabilisieren, **bevor** Phase 7 (Raspberry-Pi-Sync-Server + Solar-Teil) beginnt.
Desktop und Android sind fertig — jetzt soll das Fundament geprüft werden, damit die nächste
Ausbaustufe nicht auf wackligen Berechnungen aufsetzt.

## Zeitrahmen

Muss **vor Phase 7** abgeschlossen sein. Phase 7 soll ab **20.07.2026** starten.

> ⚠️ **Realistische Einschätzung:** Das Fenster ist knapp. Alle 7 Bereiche in wenigen Tagen
> gründlich zu reviewen ist unrealistisch. Falls die Zeit nicht reicht: **Bereich 1 und 2 haben
> Vorrang** — dort saßen bisher die meisten echten Bugs. Der Rest kann auch nach Phase-7-Start
> nachlaufen, solange die Saldo-Logik steht.

---

## Methode

**Nicht** ein einziger großer Review am Ende, sondern **stückweise, Bereich für Bereich**,
jeweils mit dem stärkeren Modell (Opus 4.8).

**Begründung:** Ein offener „geh mal alles durch"-Auftrag wird teuer und ungezielt. Ein Review
pro klar abgegrenztem Bereich liefert konkrete, überprüfbare Befunde.

**Pro Bereich:**
1. Bereich benennen und Dateien eingrenzen.
2. Review mit Fokus auf **Korrektheit der Berechnung**, nicht auf Stil.
3. Befunde als Liste, dann gezielt fixen.
4. Auf dem Gerät gegenprüfen (Build läuft über Android Studio ▶ — CLI-Gradle scheitert auf
   diesem Rechner an einem JVM-Loopback-Problem).

---

## Bereiche — in dieser Reihenfolge

### 1. Saldo- / Kontostand-Berechnungslogik 🔴 höchste Priorität — ✅ ABGESCHLOSSEN (Android + shared, 16.07.2026)

> Verbleibend nur: Desktop-Aufrufstellen (im Desktop-Durchgang) und die notierten latenten
> Punkte (kein akutes Risiko).

> **Status 16.07.2026:** Reaktive Runde (14.07.) **und** systematischer Durchgang (16.07., Fable 5)
> durch `shared/analytics` + Android-Aufrufstellen abgeschlossen. 5 weitere Befunde gefixt —
> **Geräte-Test der 16.07.-Fixes steht aus.** Die **Desktop-Aufrufstellen** (DesktopDashboard,
> DesktopAuswertungen) sind noch nicht systematisch durch — Fixes in `shared/` wirken dort aber
> automatisch mit.
>
> **Nutzer-Workflow bestätigt (14.07.2026):** Kreditkartenausgaben werden auf der Karte gebucht
> (die zeigt nur den laufenden Zyklus, resettet selbst). Die Bank-Abbuchung wird als **eine**
> negative Buchung auf dem Girokonto mit Kategorie „Kreditkartenabrechnung" erfasst. Das ist der
> einzige Handgriff; die Kategorie triggert den Doppelzählungs-Schutz.

Hier wurden bisher die meisten Bugs gefunden:
- Girokonto-Diskrepanz
- Kreditkarten-Sägezahn (Reset auf 0 an Zyklusgrenzen)
- Dashboard-Startpunkt
- Doppelzählungen

**Kern-Dateien:** `shared/.../analytics/FinanceAnalytics.kt` (u. a. `filterBookingsForAccount`,
`currentBillingCyclePeriod`, `excludeSettlements`, Sawtooth-Berechnung).

#### Die geltenden Regeln (Punkte 18 + 19 aus `prompt.txt`, geklärt am 14.07.2026)

| Buchungsart | Kategorie-Auswertung & Einnahmen/Ausgaben | Kontostand |
|---|---|---|
| **Kontoabgleich-Korrektur** (`source == RECONCILIATION`) | **raus** — korrigiert nur den Saldo, ist keine echte Ausgabe | **drin** |
| **Kreditkartenabrechnung** (`category == "Kreditkartenabrechnung"`) | **sichtbar, sobald ein einzelnes Konto gewählt ist**; nur in der „Alle Konten"-Sicht raus (sonst zählt dieselbe Zahlung doppelt: Ausgabe auf der Karte + Abbuchung vom Giro) | **drin** |

**Wichtig zu wissen:** Eine Korrekturbuchung wird **ohne Kategorie** angelegt
(`ReconciliationService.createCorrectionBooking`). Fehlt der `RECONCILIATION`-Filter, taucht sie
in der Auswertung als **„Ohne Kategorie"** auf — eine Ausgabe, die es nie gab. Das war das
Symptom hinter Punkt 18.

#### Befunde & Fixes (14.07.2026)

Der `RECONCILIATION`-Filter fehlte an **4 von 7** Stellen — behoben:

| Stelle | Vorher | Fix |
|---|---|---|
| Android Auswertungen-Tab | ✅ korrekt (`kpiBookings`) | — |
| Android Übersicht/Dashboard | ❌ Filter fehlte (Kategorie **und** KPIs) | ✅ |
| Android Buchungen (KPIs) | ❌ Filter fehlte | ✅ |
| Desktop Dashboard — KPIs | ✅ korrekt | — |
| Desktop Dashboard — Kategorie | ❌ Filter fehlte | ✅ |
| Desktop Auswertungen — normale Konten | ✅ korrekt | — |
| Desktop Auswertungen — **Kreditkarte** | ❌ Filter fehlte **und** Abrechnungen wurden ausgeblendet (Punkt 19 verletzt) | ✅ Sonderzweig entfernt |

Der Kreditkarten-Sonderzweig in `DesktopAuswertungenScreen` rief
`calculateCategoryBreakdown(expenses)` ohne Parameter auf → Default `filterSettlements = true` →
die Abrechnung war ausgerechnet auf der Kreditkarte unsichtbar. Da `isCreditCard` immer ein
gewähltes Konto voraussetzt, tut der Zweig nach dem Fix exakt dasselbe wie der andere — er wurde
ersatzlos entfernt.

**Ebenfalls gefixt (14.07.2026):** Einnahmen/Ausgaben in Buchungen zählten **geplante** Buchungen
mit, Kontostand/Übersicht/Auswertungen aber nicht → vereinheitlicht auf **nur gebuchte Posten**.

#### 🔴 Doppelzählung der Kreditkartenausgaben (gefunden 14.07.2026)

Der Doppelzählungs-Schutz erkennt eine Abrechnung **nur am exakten Kategorienamen**
`"Kreditkartenabrechnung"`. Diese Kategorie war aber **nicht in den Standard-Kategorien**, und
`ensureSeeded()` legt Kategorien nur an, wenn die Tabelle leer ist → **bestehende Datenbanken
konnten sie nie bekommen**. Der Schutz war damit still deaktiviert: Kreditkartenausgaben zählten
doppelt (einmal auf der Karte, einmal als Abbuchung vom Giro).

Verschärfend: Buchungen ließen sich **nur per Langdruck** öffnen (`onClick = {}` in `BookingRow`),
also konnte man eine Buchung praktisch nicht nachträglich umkategorisieren.

**Fix:** Kategorie wird jetzt garantiert angelegt (auch in bestehenden DBs); Tippen öffnet die
Buchung. ⚠️ **Nutzer-Aktion nötig:** Die bestehende Abbuchung auf dem Girokonto muss einmalig auf
die Kategorie „Kreditkartenabrechnung" gesetzt werden.

#### 🔴 Stale-UI durch `SnapshotStateList.hashCode()` (gefunden 14.07.2026)

Mehrere Berechnungen hingen an `remember(bookings.size, bookings.hashCode(), …)`. Die `hashCode()`
einer `SnapshotStateList` ist **identitätsbasiert** — sie ändert sich **nicht**, wenn eine Buchung
*in-place* bearbeitet wird (z. B. Kategorie ändern, Größe bleibt gleich). Folge: Speichern schrieb
korrekt in die DB, aber die angezeigte Liste/Summe rechnete nicht neu → alte Werte blieben stehen
(„springt nach Speichern zurück"). Betraf `BuchungenScreen` (Perioden-Filter + Kontostand) und
`AuswertungenTabScreen` (`allPosted`). **Fix:** inhaltsbasierter Schlüssel `bookings.toList()`.

> ⚠️ **Für den systematischen Review:** Gezielt nach weiteren `.hashCode()`-Remember-Keys auf
> Snapshot-Listen suchen — dieselbe Falle kann an anderen Stellen lauern.

#### Systematischer Durchgang (16.07.2026, Fable 5) — shared/ + Android-Aufrufstellen

Alle 6 Analytics-Dateien (`FinanceAnalytics`, `BillingCycleAnalytics`, `CcSawthoothBalance`,
`Insights`, `RecurringOccurrences`, `ReconciliationService`) plus Android-Callsites geprüft.
**5 Befunde, alle gefixt** (Geräte-Test ausstehend):

| # | Befund | Fix |
|---|---|---|
| 1 | **Balkendiagramm (Android Auswertungen)** zählte RECONCILIATION-Korrekturen mit — derselbe Bug war am Desktop schon gefixt (M2, Commit `134ff5d`), Android fehlte | Filter ergänzt |
| 2 | **CC-Saldoverlauf im Einzel-Abrechnungszeitraum** startete beim kumulierten Alt-Saldo statt bei 0 — widersprach Zyklus-Methodik (Kachel, Sägezahn, Desktop-G2) | `startingBalance = 0` bei CC |
| 3 | **Übersicht** filterte den Monat nach `timestamp` statt `effectiveDate ?: timestamp` — Buchungen mit Wertstellungsdatum landeten im falschen Monat | Semantik angeglichen |
| 4 | **Bearbeiten-Sheet** behielt beim Datumsändern das alte `effectiveDate` — die Buchung blieb am alten Tag kleben (Raten, CSV-Importe) | `effectiveDate` folgt neuem Datum |
| 5 | **promoteScheduledBookings** prüfte Fälligkeit nach `timestamp`, Status-Vergabe nutzt aber `effectiveDate ?: timestamp` | Semantik angeglichen |

**Geprüft und für korrekt befunden:** Sägezahn-Zyklusgrenzen (Hard-Reset auf 0),
`currentBillingCyclePeriod`-Grenzen `[start, ende)`, Kontostand-Konsistenz Übersicht = Buchungen =
AddBookingSheet (alle über `filterBookingsForAccount` + `currentBillingCyclePeriod`/`allTimePeriod`),
`selectAll` filtert `deleted = 0` an der Quelle, Auto-Post-Dedup + Regel-Fortschreibung,
Saldoverlauf-Startpunkt für normale Konten (kumulativ korrekt).

**Latente Punkte (kein akuter Bug, notiert):**
- `filterBookingsForAccount` filtert `deleted` nicht selbst — Quelle (`selectAll`) ist sauber,
  aber keine Verteidigung in der Tiefe. Bei Gelegenheit `!it.deleted` ergänzen.
- Nur von Tests genutzt (Aufräum-Kandidaten): `sumIncome`, `sumExpenses`, `netTotal`,
  `topExpenseCategory*`, `currentMonthPeriod`, `calculateBillingCycleUsage`, `calculateInsights`;
  komplett unreferenziert: `calculateBillingCycleIncomeExpense`, `generateFutureOccurrences`.
- Balkendiagramm blendet Settlements **immer** aus (`excludeSettlements` intern), die KPI-Kacheln
  desselben Screens zählen sie bei Einzelkonto mit → Teil der bekannten offenen
  Settlement-KPI-Inkonsistenz (siehe unten).

#### Noch offen in diesem Bereich

- **Fragiler Mechanismus (bewusst so belassen, 14.07.2026):** Der Schutz hängt an einem frei
  editierbaren Kategorienamen; Umbenennen/Vertippen deaktiviert ihn lautlos. Sauber wäre ein
  struktureller Marker an der Buchung (z. B. `BookingSource.SETTLEMENT`). **Nutzer hat entschieden,
  es beim String-Abgleich zu belassen** — nicht ohne erneute Rücksprache ändern.
- ~~Inkonsistenz bei Abrechnungen in den Einnahmen/Ausgaben-KPIs~~ **ENTSCHIEDEN & umgesetzt
  (16.07.2026):** Abrechnungen zählen in Einnahmen/Ausgaben-Statistiken **nur in der
  Einzelkonto-Sicht eines Nicht-CC-Kontos.** Begründung: Auf dem Giro ist die Abbuchung echtes
  Geld, das rausgeht (Nutzer-Erwartung); auf der Kreditkarte ist die Ausgleichs-Gutschrift eine
  Tilgung, keine Einnahme; bei „Alle Konten" zählte dieselbe Zahlung doppelt. Einheitlich
  umgesetzt in: Auswertungen-KPIs, Auswertungen-Balkendiagramm
  (`calculateMonthlyIncomeExpense` hat jetzt `filterSettlements`-Parameter, Default `true` →
  Desktop unverändert) und Buchungen-KPIs. **Desktop-Aufrufstellen noch auf diese Regel
  umstellen** (zählen Abrechnungen derzeit nie).
  ⚠️ Voraussetzung in den Daten: Die Ausgleichs-Gutschrift **auf der Karte** muss ebenfalls als
  „Kreditkartenabrechnung" kategorisiert sein, sonst erscheint sie dort als Einnahme.
- Sägezahn-/Abrechnungszyklus-Logik insgesamt (Kreditkarte) noch nicht durchgereviewt.
- Saldoverlauf-Startpunkt (Dashboard) noch nicht gegengeprüft.

### 2. Auswertungen-Ansicht komplett — ✅ ABGESCHLOSSEN (Android, 16.07.2026)
Filter, Drilldowns, Billing Period, Mehrperioden-Auswahl, „Alle Konten".

> **Systematischer Durchgang 16.07.2026 (Fable 5), gesamte `AuswertungenTabScreen.kt` (1.236 Z.):**
>
> **🔴 Crash gefixt:** Die Custom-Zeitraum-Picker validieren `Von ≤ Bis` nicht, und
> `PeriodFilter` wirft bei `from > to` eine `IllegalArgumentException` → App-Absturz bei
> vertauschten Daten (betraf Auswertungen **und** Buchungen, Android **und** Desktop).
> Fix an der Wurzel: `customRangeFilter` (shared) normalisiert jetzt auf `[min, max]` —
> deckt alle 8 Aufrufer auf beiden Plattformen ab. Die zwei CC-Inline-Pfade (Custom-Filter +
> Sägezahn), die statt Crash nur leere Charts lieferten, normalisieren ebenfalls.
> Nachbesserung nach Geräte-Test: auch der **sichtbare Picker-State** (Von/Bis-Buttons) wird
> beim Bestätigen getauscht — vorher zeigten die Buttons „16.7.–16.6." während der Chart
> korrekt normalisiert rechnete (alle 6 Bestätigen-Handler in Auswertungen + Buchungen).
>
> **Geprüft und für korrekt befunden:** Konto-Dropdown (vollständig, „Alle Konten"),
> Drilldown-Konsistenz (gleiche Datenbasis `kpiBookings` wie Breakdown-Zeilen), Donut-Segmente
> und Tap-Zuordnung (gleiche Reihenfolge wie Breakdown), Top-4-Legende, Chart-Verdrahtung
> (indexbasiert, mit `getOrNull` abgesichert), Empty-States, CC-Mehrperioden/Sägezahn-Verdrahtung,
> Perioden-Definitionen (30 Tage / Monat / 6 Monate / Jahr / Gesamt).
>
> Datenbasis-Befunde (KPIs, Balkendiagramm, Saldoverlauf-Start, Abrechnungs-Regel) wurden
> bereits unter Bereich 1 gefixt. Desktop-Auswertungen folgen im Desktop-Durchgang.

### 3. Dashboard — ✅ ABGESCHLOSSEN (Android, 16.07.2026)
Layout, Saldoverlauf, Zeitraum-Konsistenz.

> Datenbasis (Monatsfilter-Semantik, RECONCILIATION-Filter) bereits unter Bereich 1 gefixt.
> Durchgang 16.07.: Benachrichtigungslogik (Badge-Zählung, Navigationsziele inkl. der nach dem
> Detail-Screen-Umbau umgebogenen Routen), Suche (deleted raus, `take(50)`, Betrags-Suche) und
> Limit-Bar geprüft — **keine Befunde**. Notiert (kein Fix): Klick auf ein Suchergebnis landet in
> den Konto-Buchungen mit Standard-Zeitraum — liegt die gefundene Buchung außerhalb, ist sie
> nicht sichtbar (UX-Entscheidung, ggf. später "Gesamter Zeitraum" vorwählen).

### 4. Buchungs-Ansicht — ✅ ABGESCHLOSSEN (Android, 16.07.2026)
„Alle Konten"-Option, Datepicker.

> Datenbasis (KPIs, Kontostand, CC-Dropdown, geplante Buchungen, Datepicker-Normalisierung)
> bereits unter Bereich 1/2 gefixt. Durchgang 16.07.: Gruppierung/Sortierung (absteigend, stabile
> Keys), Typ-/Status-Filter, Empty-States geprüft — sauber. **Zwei kleine Fixes:** Das
> Kategorie-Dropdown bot „Ohne Kategorie" nicht an, obwohl der Filter-Code den Wert unterstützt
> (Buchungen ohne Kategorie waren nicht filterbar) → ergänzt. Tote Variable `categoryNames`
> entfernt.
>
> **Querschnitts-Notiz (für Bereich 6):** Alle Screens cachen `today`/Monatsfilter via
> `remember {}` ohne Key — bleibt die App über Mitternacht offen, zeigen sie bis zum nächsten
> Prozessstart den alten Tag/Monat. Harmlos, einheitliches Muster, bei Bedarf später zentral lösen.

### 5. Inspektions-Werkzeuge — ✅ ABGESCHLOSSEN (Android, 16.07.2026)
Daueraufträge, Ratenzahlungen.

> **Durchgang 16.07.2026:** `RecurringRulesScreen`, `AddEditRecurringRuleDialog`,
> `InstallmentsScreen`, `AddInstallmentDialog`, `InstallmentGrouping` (shared),
> `generateInitialBookings` + MainActivity-Verdrahtung.
>
> **🔴 Gefixt — Tipp löschte ganze Ratenzahlung ohne Rückfrage:** `InstallmentGroupCard` hatte
> `FinCard(onClick = onDelete)` — ein einfacher Tipp auf die Karte löschte sofort und
> unwiderruflich alle Raten-Buchungen der Gruppe. Jetzt Bestätigungsdialog.
>
> **🔴 Gefixt — Cascade-Löschungen ohne Sync-Tombstones:** Raten-Gruppe löschen und
> Dauerauftrag-Cascade nutzten `removeAll + saveBookings` (Hard-Delete). Ohne Tombstone bringt
> der nächste WLAN-Sync die gelöschten Buchungen vom anderen Gerät zurück. Beide Stellen auf das
> `softDeleteBooking`-Muster der Einzel-Löschung umgestellt (inkl. bisher vergessenem
> Attachment-Cleanup).
>
> **Geprüft und in Ordnung:** Regel-Karten → Edit-Dialog (Löschen dort nur per explizitem
> Button), Filter-/Gruppier-Modi, Validierung beider Dialoge, `buildInstallmentGroups`
> (Zählung/Fortschritt/nächste Rate), `generateInitialBookings` konsistent mit Auto-Post.
>
> **UX-Umbau nach Nutzer-Feedback (17.07.2026):** Konto-Chips liefen bei langen Kontonamen
> über (vertikal gequetschter Chip). Daueraufträge + Ratenzahlungen öffnen jetzt **direkt aus
> „Mehr"** (Konto-Picker entfernt, Route-Parameter optional); Konto-Auswahl über **Dropdown**
> („Alle Konten" + jedes Konto, Muster wie Auswertungen/Buchungen). Ratenzahlungs-Statusfilter
> auf **Offen/Abgeschlossen** reduziert (Default Offen). Neue Ratenzahlung bucht auf das im
> Dropdown gewählte Konto; bei „Alle Konten" fragt einmalig der Konto-Picker.
> Benachrichtigungs-Einstieg „Fällige Daueraufträge" (mit Konto) bleibt erhalten.
>
> **UI-Modernisierung (17.07.2026):** `AddEditRecurringRuleDialog` war noch ein Alt-Design-
> AlertDialog → als ModalBottomSheet im Stil der AddBookingSheet neu gebaut (Ausgabe/Einnahme-
> Toggle statt Vorzeichen-Tippen, Chip-Kategorie-Picker mit Neuanlage, Intervall/Wiederholungen
> als Chips, Gradient-Speichern-Button, Löschen unten mit Bestätigung). Dafür wurden
> `DirectionToggle`, `CategoryChipPicker` und `GradientButton` in `AddBookingSheet.kt` von
> `private` auf `internal` gestellt (Wiederverwendung statt Duplikation).
> Ratenzahlungen: Tipp auf Karte öffnet jetzt einen **Bearbeiten-Sheet** mit „Ratenzahlung
> löschen" als Option unten (weiterhin mit Bestätigung + Tombstones). Editierbar (17.07.2026):
> **Beschreibung + Kategorie** (alle Raten), **Ratenbetrag** (nur offene/SCHEDULED-Raten —
> gebuchte Raten sind Historie und werden nie umgeschrieben), **Anzahl der Raten** (Erhöhen
> hängt Raten im Datumsraster hinten an — Intervall wird aus dem Abstand der letzten zwei Raten
> abgeleitet, da es nicht gespeichert ist; Verringern entfernt offene Raten von hinten mit
> Tombstones, Minimum = bereits gebuchte). Die `i/n`-Nummerierung wird nach jeder Änderung in
> Datumsreihenfolge neu geschrieben.
>
> `AddInstallmentDialog` (neue Ratenzahlung) ebenfalls auf ModalBottomSheet im neuen Design
> umgebaut (17.07.2026): Ausgabe/Einnahme-Toggle statt handgetipptem Minus (~~Minus-Problem~~
> damit gelöst), Betrag+Anzahl nebeneinander mit Pro-Rate-Vorschau, Intervall-Chips,
> Chip-Kategorie-Picker. Der dadurch verwaiste Radio-Button-`CategoryPicker`
> (`ui/category/CategoryPicker.kt`) wurde gelöscht.
> - **Gelöschte Dauerauftrag-REGELN haben ebenfalls kein Tombstone** (Tabelle ohne
>   `deleted`-Flag) → können per Sync zurückkommen. Schema-Änderung → gehört in den
>   Sync-Review (Phase 5/7).
> - Konto-Löschung hard-deletet Buchungen ebenfalls ohne Tombstones — gleiche Klasse, dort mit
>   prüfen.
> - Rundung: Einzelrate wird gerundet angezeigt (3 × 33,33 ≠ 100,00) — kosmetisch.
> - `buildInstallmentGroups` filtert `deleted` nicht selbst (Quelle sauber — wie
>   `filterBookingsForAccount`, latent).

### 6. Datumsformat / Lokalisierung — ✅ ABGESCHLOSSEN (Android, 17.07.2026)
Querschnittsthema über alle Screens.

> **Inventar-Durchgang 17.07.2026 — Zustand insgesamt gut:**
> - **Datumsmuster einheitlich:** überall `dd.MM.yyyy` (4-stelliges Jahr); bewusste Ausnahmen
>   sind die Design-Formate `EEEE, d. MMMM` (Tagesgruppen-Header) und `dd. MMM` (Buchungszeile),
>   beide mit `Locale.GERMANY`.
> - **Geld:** komplett zentral über `MoneyFormat` (expect/actual), explizit `Locale.GERMANY` —
>   keine Streuner (`%.2f`) in der UI gefunden.
> - **2 Konsistenz-Fixes:** `Locale.GERMANY` ergänzt bei den einzigen zwei UI-Formattern ohne
>   explizites Locale (Suchdialog-Datum in `OverviewScreen`, Vorschau-Datum in
>   `CsvImportScreen`). Rein numerische Muster sind zwar locale-unkritisch, aber jetzt ist das
>   Muster einheitlich.
>
> **Notiert (kosmetisch, kein Fix):**
> - `Von/Bis`-Buttons (Buchungen/Auswertungen) bauen das Datum per Hand (`padStart`) — Ausgabe
>   ist identisch `dd.MM.yyyy`, bei Gelegenheit auf den Formatter vereinheitlichen.
> - Betrags-Vorbefüllung in Edit-Feldern zeigt Punkt statt Komma („8.99") — beides wird beim
>   Speichern korrekt geparst.
> - Monatsnamen sind hardcodiert deutsch (`FinanceAnalytics`, `OverviewScreen`) — bewusst, die
>   App ist einsprachig deutsch.
> - Dateinamens-Zeitstempel in `ExportService` (`yyyyMMdd-HHmmss`) ohne Locale — rein numerisch,
>   unkritisch.
> - Bekannt (siehe Bereich 4): `remember {}`-gecachtes „heute" veraltet, wenn die App über
>   Mitternacht offen bleibt.

### 7. Android-Bereiche — ✅ ABGEDECKT durch die Android-Durchgänge der Bereiche 1–6
*(Ursprünglich als eigener Punkt geplant, als die Review-Liste Desktop-first gedacht war. Die
Bereiche 1–6 wurden 14.–17.07.2026 direkt auf Android + shared durchgeführt.)*

---

## Verbleibend nach dem Android-Review (Stand 17.07.2026)

1. ~~Desktop-Durchgang~~ ✅ **ERLEDIGT (18.07.2026, Branch `phase6-desktop-review`):**
   - **Abrechnungs-Regel übertragen:** `DesktopAuswertungenScreen` — Einnahmen-KPI filterte
     Settlements **nie** (CC-Tilgung zählte als Einnahme), Ausgaben-KPI + Kategorie-Breakdown
     nutzten noch `selectedAccountId == null` statt `… || isCreditCard`, Balkendiagramm hatte
     **weder** RECONCILIATION-Filter **noch** Settlement-Regel → alle vier Stellen auf die
     einheitliche Regel gezogen (`excludeSettlementsHere = selectedAccountId == null || isCreditCard`).
     Dashboard-KPIs waren korrekt (immer „Alle Konten" → immer excludeSettlements).
   - **hashCode()-Falle:** nicht vorhanden — Desktop hält `bookings` als immutable List in
     `mutableStateOf` (jede Änderung = neue Instanz, `remember(bookings)` funktioniert); die
     `hashCode()`-Treffer sind Farb-Fallbacks, keine Remember-Keys.
   - **Dialoge:** designkonform (Desktop-Plan sieht zentrierte Dialoge vor, Steps 9–11) — kein
     Alt-Design-Problem wie auf Android. Keine Änderung.

2. ~~Sync-Review Tombstones~~ ✅ **ERLEDIGT (18.07.2026):** Regeln + Konten haben jetzt
   Soft-Delete-Tombstones wie Buchungen:
   - **Schema-Migration `3.sqm`** (DB-Version 4): `deleted`-Spalte in `recurringRule` und
     `account`; `selectAll` filtert live, neu: `selectAllIncludingDeleted` + `softDelete`.
     Android migriert automatisch (AndroidSqliteDriver), Desktop über die bestehende
     `user_version`-Logik.
   - **Datenmodell:** `RecurringRule.deleted` / `Account.deleted` (Default `false` → wird nicht
     serialisiert; alte Payloads bleiben kompatibel, `ignoreUnknownKeys` deckt die Gegenrichtung).
   - **Repos:** `softDeleteRule`/`softDeleteAccount`, `loadAll…ForSync`; `saveRules`/`saveAccounts`
     erhalten Tombstones (Muster `saveBookings`).
   - **Sync:** Exporter exportiert Tombstones (Orphan-Kategorien-Scan nur über lebende);
     Importer nimmt Tombstones in die LWW-Merge-Basis (sonst Auferstehung durch ältere
     Live-Kopien!), `knownAccountIds` = nur lebende Konten; Writer schreibt `deleted` mit.
   - **Aufrufer umgestellt (alle Hard-Deletes beseitigt):** Android: Regel-Löschung,
     Auto-Post-erschöpfte Regeln, Konto-Löschung (kaskadiert Buchungen + Regeln + Konto).
     Desktop: Regel-Löschung (2 Stellen), Konto-Löschung — die vergaß bisher sogar die
     **Regeln** des Kontos (Bestandslücke, mitgefixt).
   - 🔴 **Folge-Bug gefunden & gefixt (18.07.2026, Geräte-Test):** Neue Daueraufträge
     verschwanden nach dem Anlegen. Ursache: fortlaufende Regel-IDs (`rN`) wurden nur über
     **lebende** Regeln berechnet → Kollision mit Tombstone-IDs gelöschter Regeln → beim
     Speichern überschrieb der zuletzt eingefügte Tombstone (INSERT OR REPLACE) die neue Regel.
     Fix: ID-Vergabe rechnet über `loadAllRulesForSync()` inkl. Tombstones (Android
     `reloadData` + 2 Desktop-Stellen). Zusätzlich: Der Anlage-Dialog wählte seit dem
     Dropdown-Umbau immer das **erste** Konto vor statt des im Filter gewählten → FAB reicht
     jetzt die Dropdown-Auswahl durch (Muster wie Ratenzahlungen).
     Hinterlassenschaft in Nutzerdaten möglich: Die von der „verschluckten" Regel bereits
     generierten (geplanten) Buchungen bleiben als Waisen — in Buchungen prüfen/löschen.
   - **Produktentscheidung nach Datenverlust-Vorfall (18.07.2026): Regel-Löschung erhält
     gebuchte Historie.** Beim Aufräumen einer doppelten Sparraten-Serie löschte die Kaskade
     auch **gebuchte** März/April-Eingänge (−900 € Datenstand auf dem DKB-Konto; per
     Desktop-DB-Tombstone-Reaktivierung repariert, Backup `fintracker.backup-20260718-141759.db`).
     Neue Semantik auf beiden Plattformen: Regel löschen entfernt Regel + **geplante**
     Buchungen; gebuchte bleiben stehen. Warntexte entsprechend präzisiert.
     (Ratenzahlungs-Löschung bleibt bewusst Alles-oder-nichts — ein Kauf, expliziter Dialog.)
   - **Produktentscheidung (18.07.2026): „Begrenzt" aus dem Dauerauftrag-Dialog entfernt**
     (Android + Desktop). Begrenzte Regeln erzeugten alle N Buchungen sofort und erschienen
     nie in der Liste — funktional identisch zur Ratenzahlung, aber ohne Fortschritt/
     Gruppen-Verwaltung (Falle, an der der Nutzer beim Testen hängen blieb). Endliche Serien
     laufen jetzt ausschließlich über Ratenzahlungen. Datenmodell (`remainingExecutions`)
     und Auto-Post-Logik bleiben defensiv erhalten (Alt-/Import-Daten); beim Bearbeiten
     bleibt ein vorhandener Wert erhalten statt still entgrenzt zu werden.
   - ⚠️ **Notiert — Restore-Semantik:** `saveX` erhält Tombstones auch beim JSON-Restore.
     Stellt man ein Backup wieder her, das einen inzwischen gelöschten Datensatz enthält,
     gewinnt der Tombstone (Datensatz bleibt weg). Gleiches Bestandsverhalten wie bei
     Buchungen seit `2.sqm`; bei Bedarf Restore künftig mit „DB komplett leeren" definieren.

3. **Package-Rename** (alter, personenbezogener Paketname → neutral) + **Repo-Rename** vor GitHub-Veröffentlichung
   (Backup vorher! `applicationId`-Wechsel = neue App).

---

## Zusätzlich vor der GitHub-Veröffentlichung

- [x] **Package-Umbenennung** ✅ (18.07.2026): vom alten, personenbezogenen Paketnamen auf
      `io.github.willywonka644.fintracker`. 8 Quellverzeichnisse per `git mv` (Historie
      erhalten), 157 Dateien ersetzt (inkl. `namespace`/`applicationId`, SQLDelight-`packageName`,
      Desktop-`mainClass`, `.sq`-Typ-Referenzen). Manifest nutzt `${applicationId}` → automatisch.
      **Datenmigration Handy nötig:** neue App = neue Identität; alte App bleibt parallel
      installiert und behält ihre Daten → JSON-Backup oder WLAN-Sync vom Desktop in die neue
      App, dann alte deinstallieren. PIN muss neu gesetzt werden. Desktop-Daten unberührt
      (`%USERPROFILE%\FinTracker`).
      *Beobachtung dabei:* `shared/build.gradle.kts` enthält `version = 3` in der
      SQLDelight-Config, obwohl `3.sqm` existiert (Schema real v4). Die Migration lief beim
      Geräte-Test nachweislich — das Property scheint wirkungslos; bewusst nicht angefasst,
      bei Gelegenheit klären.

- [x] **Repo-Umbenennung** ✅ (18.07.2026): `fintracker-android` → **`fintracker`** (per
      `gh repo rename`; alte URLs leiten weiter, lokale Remote automatisch aktualisiert).

---

## Definition of Done

- [x] Bereich 1 (Saldo-Logik) reviewt und Befunde gefixt — **Pflicht vor Phase 7**
- [x] Bereich 2 (Auswertungen) reviewt und Befunde gefixt — **Pflicht vor Phase 7**
- [x] Bereiche 3–7 reviewt
- [x] Punkte 18 + 19 aus `prompt.txt` verstanden und geklärt
- [x] Desktop-Durchgang (Abrechnungs-Regel, hashCode-Prüfung, Dialoge)
- [x] Sync-Tombstones für Regeln + Konten (Schema-Migration 3.sqm)
- [x] Package umbenannt, Repo umbenannt
- [x] Beide Apps bauen und laufen grün (Tombstone-Sync auf Geräten verifiziert, 18.07.2026)

**Nachtrag 19.07.2026 (Sync-Pairing-UX + Kreditkarten-Abrechnungstag):**
- Abrechnungstag pro Kreditkarte einstellbar (Anlegen + Bearbeiten, 1–28, mit
  Zyklus-Vorschau). Dabei zwei Bestandsbugs behoben: Android setzte beim Speichern **jede**
  Karte zwangsweise auf Tag 18 zurück; Desktop erlaubte Tag 29–31 (hätte die
  Zyklus-Berechnung gecrasht).
- Kopplungscode wird beim „Verbinden" sofort gegen den Desktop geprüft (neuer
  `/sync/ping`-Endpoint, Antwort mit Token verschlüsselt); klare Fehlermeldungen für
  falschen Code vs. Desktop nicht erreichbar; bei Erfolg direkter Sprung zur
  Senden/Holen-Auswahl (der alte „state=Idle"-Re-Entry-Trick kollabierte im selben Frame
  und hat nie funktioniert).
- „Regenerate code" wirkt jetzt sofort: die Server-Routen lasen den Token bisher aus einer
  beim Start eingefrorenen Closure-Kopie.
- Kopplungscode ist garantiert immer 8-stellig (Base64-Stripping erzeugte zufällig
  6–7-stellige Codes, die das 8-Zeichen-Pflichtfeld am Handy komplett aussperrten).
