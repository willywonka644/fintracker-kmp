# FinTracker — Phase 6.5: Automatisiertes Testing

## Ziel

Ein mehrschichtiges, weitgehend automatisiertes Testnetz **vor** dem Raspberry-Pi-Umbau
(Phase 7). Motivation: Phase 7 fasst den fragilsten Code an (Sync), und der
DKB-Datenverlust-Vorfall (−900 € durch Kaskadenlöschung, siehe Commit `7f0dc65`)
hat gezeigt, dass Regressionen in der Geschäftslogik real und teuer sind.

## Warum vor Phase 7?

- Phase 7 ist hardware-blockiert (Pi nicht bestellt, kommt frühestens nach 07.08.2026,
  dazwischen Urlaub) → die Wartezeit kostet keinen Phase-7-Fortschritt.
- Phase 7 baut auf Wi-Fi-Sync + Tombstones auf → Regressionsnetz um Sync = Pflicht,
  bevor ein Server dazwischen kommt.
- Reine Logik-Tests laufen headless in CI (GitHub Actions) — automatisch bei jedem
  Push, sogar im Urlaub.

## Ist-Stand (bereits vorhanden)

`shared/src/commonTest/` enthält schon:
- `analytics/FinanceAnalyticsTest.kt`
- `analytics/RecurringOccurrencesTest.kt`
- `BookingFactoryTest.kt`
- `BookingJsonCompatibilityTest.kt`
- `qrscan/EpcParserTest.kt`
- `RecurringRuleTest.kt`

Test-Framework: `kotlin("test")` in `commonTest` (multiplattform, headless).

## Bau-/Ausführ-Hinweis

CLI-Gradle scheitert auf dem Rechner am JVM-Loopback-Fehler → Tests werden in
**Android Studio** (▶ auf Testklasse) bzw. via **GitHub-Actions-CI** ausgeführt.
Claude liefert kleine, einzeln lauffähige Testdateien; Klammer-/Kompilier-Check
ersetzt das Ausführen nicht.

---

## Tier 1 — Logik-Tests (`shared`, headless) + CI

Höchster ROI. Reine Kotlin-Logik, kein Emulator, gratis.

### 1a. Sync + Tombstones (PRIORITÄT — Phase-7-Fundament)
- [ ] `SyncExporter` — Export enthält Tombstones (deleted-Flag) für Rules & Accounts
- [ ] `SyncImporter` — Merge-Semantik: Tombstone gewinnt / verliert korrekt gegen neuere Änderung
- [ ] `SyncImporter` — Versions-Validierung (`SUPPORTED_VERSIONS`), unbekannte Version wird abgelehnt
- [ ] ID-Kollision: neue Regel wird nicht von Tombstone überschrieben (Regress `947f592`)
- [ ] Round-Trip: Export → Import ergibt identischen Zustand
- [ ] `SyncWriter` / `SyncPreview` — Vorschau zählt Adds/Updates/Deletes korrekt

### 1b. Reconciliation / Settlement
- [ ] `ReconciliationService` — Settlement zählt nur bei Einzelkonto ≠ Kreditkarte
- [ ] Kategorie „Kreditkartenabrechnung" wird garantiert geseedet (String-Match beabsichtigt)
- [ ] RECONCILIATION-Buchungen fließen nicht in normale Summen/KPIs

### 1c. Analytics-Ergänzungen
- [ ] `BillingCycleAnalytics` — Abrechnungstag 1–28, Zyklusgrenzen korrekt
- [ ] `CcSawthoothBalance` — CC-Einzelzyklus-Saldoverlauf startet bei 0
- [ ] `Insights` — geplante Buchungen aus Summen ausgeschlossen

### 1d. Weitere reine Logik
- [ ] `InstallmentGrouping` — Ratenzahlungs-Gruppierung, Intervall aus Datumsabstand
- [ ] `CsvParser` — Happy Path + Grenzfälle (leere Felder, Komma/Semikolon, Vorzeichen)
- [ ] `BackupRestore` — Legacy-Version-Mapping, unsupported version → Fehler
- [ ] `PinHasher` — Hash stabil, falsche PIN schlägt fehl
- [ ] `MoneyFormat` / `DateUtils` — Formatierung + Grenzfälle

### 1e. Repository-Tests (In-Memory-SQLite, `desktopTest`)
- [ ] Regel-Löschung erhält gebuchte Historie (DKB-Fix, Commit `7f0dc65`)
- [ ] Kaskaden nutzen softDelete-Tombstones statt Hard-Delete
- [ ] DB-Migrationen 1→4 laufen sauber durch (user_version = 4)

### 1f. CI
- [ ] GitHub-Actions-Workflow: `./gradlew :shared:allTests` bei push/PR

---

## Tier 2 — Desktop-E2E (Compose UI Test)

Compose-UI-Tests statt SikuliX (nutzt dieselben Composables, stabiler).

**Aufteilung in zwei Stufen** (Befund aus der Code-Sichtung, 13.08.2026): die
Screens sind bereits pur (Daten rein, Callbacks raus) und damit ohne Umbau
testbar. Nur der Weg durch `AppContent` braucht einen DI-Seam, weil
`AppServices` ein `object` ist, das über `DatabaseDriverFactory` fest die echte
Nutzer-DB unter `~/FinTracker/fintracker.db` öffnet — ein E2E-Test würde dort
hineinschreiben.

### 2a — Komponententests (kein Refactor nötig) — ABGESCHLOSSEN, 21 Tests grün

Gelernt beim Schreiben (für Tier 2b/3 und künftige UI-Tests):
- `AlertDialog`/`DropdownMenu`-Inhalte sind ohne Sonderbehandlung findbar.
- **`performClick` prüft keine Sichtbarkeit.** Ein Klick auf einen weggescrollten
  Knoten verpufft lautlos; der Test scheitert erst Schritte später an
  scheinbar unzusammenhängender Stelle. Alles in `verticalScroll`-Bereichen
  braucht `performScrollTo()` davor.
- Geldbeträge nie als String hartkodieren (`NumberFormat` de_DE nutzt ein
  geschütztes Leerzeichen vor dem €) — immer über `MoneyFormat` berechnen.
- Textfinder brechen an Mehrdeutigkeit (Kontoname steht in Zeile UND Menü);
  interaktive Knoten deshalb per `testTag` adressieren.

- [x] Infrastruktur: Test-Deps in `desktopApp/build.gradle.kts`
      (`compose.desktop.uiTestJUnit4`, `useJUnit()`) + `ComposeUiSmokeTest`
- [x] Kontoabgleich-Dialog: Differenz-Berechnung → Korrekturbuchung
      (`ReconciliationDialogTest`, 4 Tests). Nebenbefund: `AlertDialog`-Inhalt
      ist in Compose-Desktop-Tests ohne Sonderbehandlung findbar.
- [x] BookingListScreen: Liste rendert, Filter/Konto-Dropdown wirken
      (`BookingListScreenTest`, 8 Tests). Der Screen filtert NICHT selbst nach
      Konto — das macht der Aufrufer; `account` steuert nur Kopf/Saldo/CC-Leiste.
- [x] Regel-Dialog: Anlegen/Bearbeiten liefert erwartetes `RecurringRule`
      (`AddEditRecurringRuleDialogTest`, 7 Tests)

**Beim Testen gefunden — alle drei ERLEDIGT:**
1. `BookingListScreen`: „Noch keine Buchungen" war unerreichbar, weil
   `selectedStatus` auf `POSTED` startet und damit `isFiltered` immer true war.
   Behoben über ein zweites Flag `hasNarrowedResults` nur für den Leertext;
   der Filter-zurücksetzen-Knopf hängt weiter an `isFiltered`, damit er auch
   für einen reinen Status-Filter erreichbar bleibt.
2. Toter Code: die Verdrahtung von `RecurringRulesDialog` und
   `InstallmentsDialog` in Main.kt ist entfernt (State, Parameter, beide
   Callback-Blöcke) — sie duplizierte die DKB-kritische Soft-Delete-Kaskade.
   OFFEN: die nun unreferenzierten Composables `RecurringRulesDialog` /
   `RecurringRuleCard` / `InstallmentsDialog` / `InstallmentGroupCard` stehen
   noch in ihren Dateien. ACHTUNG beim Aufräumen: dieselben Dateien enthalten
   lebenden Code (`AddEditRecurringRuleDialog`, `DesktopDateField`,
   `AddInstallmentDialog`) — nicht dateiweise löschen.
3. `Account.billingStartDay`-KDoc behauptete noch „für CREDIT_CARD immer 18" —
   korrigiert auf „pro Karte einstellbar (1–28), Vorgabe 18".

### 2b — echtes E2E (DI-Seam) — ABGESCHLOSSEN, 25 Tests grün gesamt
- [x] `Services`-Interface extrahieren, `AppContent` `internal` + Parameter.
      In zwei einzeln baubaren Schritten: erst Interface + `AppServices` als
      Implementierung (Verhalten identisch), dann 51 Zeilen `AppServices.` →
      `services.` in `AppContent`. Die 4 Stellen in `main()` bleiben bewusst
      auf `AppServices` — das ist der Produktions-Einstiegspunkt.
      `DatabaseDriverFactory` musste NICHT angefasst werden.
- [x] Happy Path: Buchung anlegen → in DB + in Liste (`AppContentTest`)
- [x] Regel anlegen → ID-Vergabe in `AppContent` (`r1`) + in Liste

`TestServices` (src/test) hält eine eigene In-Memory-DB pro Instanz und nutzt
bewusst die **echten** SQL-Repositories — nur der Ort der Datenbank wird
getauscht. Gegen handgeschriebene Attrappen würde ein UI-Test nicht merken,
wenn Screen und Persistenzschicht auseinanderlaufen.

**Wichtig bei E2E:** `AppContent` lädt über `Dispatchers.Default`.
`waitForIdle` deckt das NICHT ab — Zusicherungen brauchen `waitUntil`, sonst
wird der Test flackerig. Laufzeit beachten: der Buchungs-E2E-Test braucht ~10 s,
die restlichen 24 zusammen unter 2 s.

**Beim Testen gefunden und behoben:** Das Betragsfeld im Buchungsformular
verwarf die deutsche Komma-Eingabe stillschweigend (`v.toDoubleOrNull()` als
Filter) — man konnte kein Komma tippen. Jetzt wie in den anderen beiden
Dialogen mit `replace(',', '.')` an beiden Stellen (Tippen UND Speichern).

**Noch offen:** `WiFiSyncDialog` greift weiterhin direkt auf `AppServices` zu;
kein E2E-Test öffnet ihn. Bei Bedarf dieselbe Umstellung.

**Kein Test „Regel anlegen → Auto-Post" auf Desktop.** `autoPostDueBookings`
wird ausschließlich von `MainActivity` aufgerufen; in `desktopApp/src` gibt es
null Treffer. Das ist eine bewusste Entscheidung, keine Lücke: die
Duplikat-Sperre in `RecurringOccurrences.kt` prüft gegen *lokal vorhandene*
Buchungen. Würde der Desktop ebenfalls posten, entstünde im Fenster zwischen
Android-Buchung und Sync dieselbe Buchung zweimal mit verschiedenen UUIDs.
Beidseitiges Posten bräuchte einen deterministischen Occurrence-Key
(Regel-ID + Datum statt zufälliger UUID) und gehört frühestens in Phase 7.
Arbeitsteilung bleibt: **Android ist das führende Gerät**, der Desktop erhält
gebuchte Sätze per Sync. Konsequenz: eine am Desktop angelegte Regel erzeugt
zunächst keine Buchungen (`Main.kt` speichert nur die Regel). Die Auto-Post-
Logik selbst ist über `RecurringOccurrencesTest` in Tier 1 abgedeckt.

## Tier 3 — Android-E2E (Maestro)

YAML-Flows in `.maestro/`, laufen lokal gegen den Emulator.

**Setup (14.08.2026):** Maestro 2.8.0 unter `C:\dev\maestro` (nativ, kein WSL
mehr nötig seit 1.39.9). Aufruf ohne PATH-Eintrag über den vollen Pfad:
`"/c/dev/maestro/bin/maestro.bat" test .maestro/<flow>.yaml`.
Emulator `Medium_Phone` (API 36.1), App über Android Studio (Konfiguration
`app`) installieren. `MAESTRO_CLI_NO_ANALYTICS=1` setzen — Maestro sendet sonst
Nutzungsdaten. **Nur gegen Emulator/Testgerät laufen lassen:** die Flows nutzen
`clearState`, das die App-Daten löscht.

- [x] Smoke: App startet, Konto + Buchung anlegen (`02-smoke-booking.yaml`)
- [x] Fehlertoleranz: Quatsch-Eingaben crashen nicht (`03-fehlertoleranz.yaml`)
      — leerer Name, Abrechnungstag 99, Buchstaben im Betrag, Betrag 0,
      überlange Beschreibung. Alle korrekt abgewiesen, kein Absturz.

- [x] Screen-Rundgang: jeder erreichbare Screen öffnet und schließt wieder
      (`04-screen-rundgang.yaml`). Bewusst ausgelassen: JSON-Restore
      (überschreibt alles), Datei-Dialoge, Kamera-Scanner, WLAN-Sync,
      Kreditlimit (braucht Kreditkartenkonto) — Begründungen stehen in der Datei.

Ganze Suite: `maestro test .maestro/` — 4/4 Flows in ~3:45.

**Monkey (zufällige Eingaben, kein Maestro).** Android bringt das eingebaut mit:
`adb shell monkey -p io.github.willywonka644.fintracker -s 4711 --throttle 50
--pct-syskeys 0 --ignore-timeouts -v 10000`. Fester Startwert (`-s`) macht einen
Fund reproduzierbar. **Ergebnis 14.08.2026: 10.000 Ereignisse, kein Absturz.**
Die `Exception`-Zeilen im Protokoll stammen von Monkey selbst
(`/dev/input/event0: EACCES`, kann keine Flip-Ereignisse senden), nicht von der
App.

Grenze des Verfahrens, wichtig fürs Einordnen: Monkey prüft Robustheit, nicht
Richtigkeit. **Weder F1 noch F2 wären ihm aufgefallen** — beim Sheet-Verwerfen
stürzt nichts ab, und welche Kategorie richtig wäre, kann er nicht wissen.
- [ ] ~~Sync-Pairing-Flow~~ ZURÜCKGESTELLT auf nach Phase 7: bräuchte parallel
      die Desktop-App als Server, der Emulator erreicht den Host nur über
      `10.0.2.2` (keine mDNS-Suche), und Maestro steuert immer nur ein Gerät.
      Gegen den Pi-Server sinnvoller als gegen den Desktop.

**Maestro-Eigenheiten (teuer gelernt):**
- Textselektoren müssen **vollständig** passen: „Betrag" trifft NICHT
  „Betrag auf mehrere Raten aufteilen".
- **`hideKeyboard` ist tabu, solange ein Bottom Sheet offen ist** — Maestro
  löst es über die Zurück-Geste aus, die das Sheet mitschließt. Stattdessen
  `scrollUntilVisible` auf den Zielknopf.
- Der Buchungs-Sheet ist ein dreistufiger Assistent: Konto → Art der Buchung →
  Formular. Die Felder existieren vorher nicht.
- Bei Fehlschlägen legt Maestro die komplette Oberflächen-Hierarchie unter
  `~/.maestro/tests/<datum>/` ab — dort stehen die tatsächlich sichtbaren
  Texte. Schneller als raten.
- **Geldbeträge nie mit normalem Leerzeichen suchen:** die deutsche
  Zahlenformatierung setzt ein geschütztes Leerzeichen (U+00A0) vor das €.
  `"-12,00 €"` findet nichts, `"-12,00.€"` (Punkt als Platzhalter) schon.
  Dieselbe Falle wie bei den Desktop-Tests, dort umgangen durch Berechnen
  über `MoneyFormat` — in YAML geht das nicht.
- `inputText` tippt über die Tastatur des Emulators, die einzelne Wörter
  **automatisch großschreibt**. Längere Texte per Regex prüfen, nicht wörtlich.

---

## Migrationen (nachgezogen 14.08.2026) — `DatabaseMigrationTest`, 5 Tests

War die letzte echte Lücke: `1.sqm`/`2.sqm`/`3.sqm` waren völlig ungetestet,
weil `Schema.create()` immer das neueste Schema baut und den Migrationspfad
prinzipiell nicht durchlaufen kann. Das v1-Schema steht deshalb von Hand im
Test — die heutigen Tabellen minus alles, was die drei `.sqm` hinzufügen.

Warum das die wichtigste Lücke war: Ein Fehler hier trifft bestehende Nutzer
beim Update, und die Originaldaten sind danach bereits umgeformt. Alles andere
lässt sich in der nächsten Version reparieren.

Abgesichert:
- `Schema.version == 4` — damit ein Schema-Umbau ohne neue `.sqm` auffällt
- alle Zeilen überleben **mit ihren Werten**
- **kein Grabstein durch die Migration**: wäre `deleted` auf 1 vorbelegt, stünde
  die App nach dem Update leer da, obwohl die Zeilen in der Datei liegen
- **`lastModifiedAt` wird befüllt**: bliebe es 0, verlöre jeder migrierte
  Datensatz beim ersten Sync gegen das andere Gerät — lautlos
- `app_settings` existiert (PIN und Geräte-ID liegen dort)

Stand `shared`-Suite danach: **130 Tests in 17 Klassen**.

---

## Gefundene Fehler — ALLE DREI BEHOBEN (14.08.2026)

Gesammelt beim Testen, bewusst erst nach Abschluss der Testarbeit umgesetzt
(Nutzer-Entscheidung: erst weiter testen, dann in einem Rutsch bauen).
Umgesetzt auf Branch `fix/phase6.5-funde`, jeder Fix durch einen Flow
abgesichert. Maestro-Suite danach 5/5 grün.

Nutzer-Entscheidungen dazu:
- F3: `values-en` löschen (nicht übersetzen)
- F2: bestehende Buchungen mit falscher Kategorie **nicht** nachträglich ändern
  — Historie umschreiben schafft neue Probleme. Auswertungen fielen bisher
  womöglich zu niedrig aus.

### F3 — Sprachmischmasch auf nicht-deutschen Geräten (Android) — BEHOBEN

**Fix:** `res/values-en/` gelöscht. Die App ist jetzt unabhängig von der
Gerätesprache durchgängig deutsch. Abgesichert durch `04-screen-rundgang`.

Beim Screen-Rundgang aufgefallen: Der Emulator läuft auf `en-US`, und der
PIN-Dialog trägt dort den Titel **„PIN Protection"**, während direkt darunter
„PIN aktivieren" steht und der Menüeintrag davor „PIN-Schutz" hieß.

Zahlen dazu:
- `res/values/strings.xml` (Standard) = **Deutsch**, 143 Strings
- `res/values-en/strings.xml` = Englisch, nur **36** davon übersetzt
- nur 12 von 53 Kotlin-Dateien nutzen `stringResource`, der Rest hat deutschen
  Text fest im Code

Auf einem englischsprachigen Gerät erscheinen also die 36 übersetzten Strings
englisch, die restlichen 107 fallen auf Deutsch zurück, und alles Hartkodierte
ist ohnehin deutsch — teils innerhalb desselben Dialogs.

Das ist kein Datenrisiko, aber es sieht kaputt aus. **Entscheidung liegt bei
dir**, und es gibt genau zwei saubere Wege:
1. `values-en/` löschen und die App ehrlich als deutschsprachig führen. Wenig
   Aufwand, konsistentes Ergebnis. Für eine private Finanz-App naheliegend.
2. Übersetzung zu Ende führen — hieße 107 fehlende Strings **plus** die
   hartkodierten Texte aus 41 Dateien in Ressourcen überführen. Große Arbeit.

Ein Mittelweg („nur die wichtigsten übersetzen") führt genau in den heutigen
Zustand zurück.

Nebenwirkung für die Tests: Die Maestro-Flows prüfen deutschen Text. Solange
`values-en` existiert und der Emulator englisch läuft, müssen Zusicherungen auf
ressourcenbasierte Texte beide Sprachen zulassen (siehe `04-screen-rundgang`).
Alternative: Emulator auf Deutsch stellen — dann testet man die App aber in
einer Konfiguration, die nicht der Auslieferung entspricht.

### F2 — Jede neue Buchung wird stillschweigend als Kreditkartenabrechnung eingestuft (Android) — BEHOBEN

**Fix:** `AddBookingSheet.kt` — Vorauswahl der Kategorie ist jetzt leer statt
`categories.firstOrNull()`. Der Picker zeigt seinen Platzhalter „Kategorie
wählen". Abgesichert durch `02-smoke-booking` (`assertNotVisible:
"Kreditkartenabrechnung"` nach einer Buchung ohne Kategoriewahl).

Beim Fehlertoleranz-Flow aufgefallen: Eine Buchung, bei der nie eine Kategorie
gewählt wurde, erschien in der Liste als `Kreditkartenabrechnung · 14. Aug.`.

Ursachenkette (alle drei Stellen verifiziert):
1. `SqlCategoryRepository.seedCategories()` legt `settlementCategory()` als
   **erstes** Element an.
2. `AddBookingSheet.kt:152` setzt die Vorauswahl auf
   `categories.firstOrNull()?.name` — also unbedingt auf die erste Kategorie.
3. `FinanceAnalytics.excludeSettlements()` filtert alle Buchungen mit genau
   diesem Kategorienamen aus `sumIncome`, `sumExpenses`, der Kategorie-
   Aufschlüsselung und den Insights; auf Android zusätzlich in den
   Auswertungs-KPIs (`AuswertungenTabScreen.kt:268`).

Folge: Wer beim Erfassen keine Kategorie antippt, erzeugt eine Buchung, die den
Kontosaldo verändert, in den **Auswertungen aber nicht auftaucht**. Still und
ohne Hinweis — dieselbe Klasse von Problem wie der DKB-Vorfall.

**Der Desktop ist NICHT betroffen:** `BookingFormDialog.kt:94` lässt die
Auswahl für neue Buchungen auf `null` („Keine").

Geplanter Fix: Android auf dieselbe Semantik bringen — Vorauswahl `""` statt
erster Kategorie. Der Picker zeigt dann seinen Platzhalter „Kategorie wählen",
das ist bereits vorgesehen. Kein Umbau nötig, eine Zeile.
Offene Frage für dich: Sollen bestehende Buchungen mit dieser Kategorie
angefasst werden? Ich würde **nein** sagen — nachträglich Kategorien ändern
verfälscht Historie. Aber du solltest wissen, dass in deinen Echtdaten
vermutlich Buchungen liegen, die fälschlich als Abrechnung zählen.

### F1 — Buchungs-Sheet verwirft Eingaben bei Fehltipp (Android) — BEHOBEN

**Fix:** Ausgangszustand wird beim ersten Zeichnen per `remember` festgehalten;
`onDismissRequest` zeigt nur bei echten Änderungen den Dialog „Eingaben
verwerfen?". `keepEditing()` holt den Sheet über `sheetState.show()` zurück —
auch beim Wegtippen des Dialogs selbst, damit ein zweiter Fehltipp nicht doch
noch alles kostet. Abgesichert durch `05-eingaben-schutz` (drei Fälle).
Aus der echten Nutzung gemeldet. `AddBookingSheet.kt:243` führt
`onDismissRequest` ohne Zwischenstufe auf `dismissAndCleanup()`. Ein Tipp auf
den abgedunkelten Rand oder ein zu großzügiges Wischen nach unten verwirft alle
Eingaben; man fängt von vorn an. Betrifft **alle drei** Erfassungsarten
(Buchung, Ratenzahlung, Dauerauftrag) — bei einer Ratenzahlung entsprechend
ärgerlicher.

Geplanter Fix: Beim Schließversuch aktuelle Eingaben gegen den Stand beim
Öffnen vergleichen (Baseline per `remember` beim ersten Compose festhalten).
Unverändert → schließen wie bisher. Verändert → Dialog „Eingaben verwerfen?"
mit „Weiter bearbeiten" / „Verwerfen". Wegtippen NICHT komplett unterbinden,
die Geste ist auf Android erwartet.
Achtung: Material blendet das Sheet beim Randtipp aus, *bevor*
`onDismissRequest` feuert — bei „Weiter bearbeiten" muss `sheetState.show()`
es aktiv zurückholen.
Danach mit einem Maestro-Flow festnageln (halb ausfüllen → danebentippen →
Eingaben müssen noch da sein).

---

## Reihenfolge

1. Tier 1a (Sync/Tombstones) — direkt Phase-7-relevant
2. Tier 1b–1e — Geschäftslogik absichern
3. Tier 1f — CI aktivieren (ab dann läuft alles automatisch)
4. Tier 2 (Desktop-E2E)
5. Tier 3 (Android/Maestro)
