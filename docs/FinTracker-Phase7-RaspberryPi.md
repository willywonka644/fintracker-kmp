# FinTracker — Phase 7: Raspberry Pi Sync-Server

## Ziel

Ein selbst-gehosteter Sync-Server auf einem Raspberry Pi in Deutschland. 
Android und Desktop können von überall synchronisieren — kein Desktop 
muss offen sein, funktioniert über das Internet.

## Zeitrahmen

2–3 Wochen (nach Phase 6)

## Voraussetzungen

- Phase 5 abgeschlossen (Wi-Fi Sync Protokoll als Basis)
- Phase 6 abgeschlossen (sauberes UI für Sync-Button)
- Raspberry Pi vorhanden und im Heimnetz erreichbar
- Feste lokale IP für den Pi im Heimnetz
- Tailscale-Konto für den Zugang von unterwegs (#94) — **kein DynDNS**

---

## Architektur

```
Android App  ──┐
               ├──► Raspberry Pi Server ◄──► SQLite / Sync DB
Desktop App  ──┘
```

- Der Pi läuft dauerhaft als Server
- Beide Clients (Android + Desktop) connecten aktiv zum Pi
- Kein Peer-to-Peer mehr — alles über den zentralen Pi
- Erreichbar via lokale IP (Heimnetz) oder Tailscale-Adresse (unterwegs)

---

## Server (Raspberry Pi)

**Technologie:** Ktor (Kotlin) — gleiche Basis wie bestehender Wi-Fi Sync  
**Betrieb:** Als systemd Service — startet automatisch beim Pi-Boot  
**Datenbank:** SQLite auf dem Pi (gleiche Struktur wie App-DB)  
**Port:** 8080, **nur im Heimnetz und im Tailscale-Netz** — keine Portweiterleitung

**Endpoints (gleiche API wie Phase 5):**
```
GET  /sync/pull   → gibt alle Daten seit letztem Sync zurück
POST /sync/push   → nimmt neue/geänderte Daten entgegen
GET  /health      → Pi läuft und ist erreichbar
```

**Authentifizierung:**
- Einfacher shared API Key (in App-Einstellungen hinterlegbar)
- Kein öffentlicher Zugriff ohne Key

---

## Android App

- Einstellung: "Sync-Server URL" (z.B. `http://192.168.1.50:8080`)
- Einstellung: "API Key"
- **Sync-Button** auf Hauptscreen → Push + Pull in einem Schritt
- Sync funktioniert im Heimnetz und über mobiles Internet (via Tailscale, #94)

---

## Desktop App

- Gleiche Einstellungen: Server URL + API Key
- **Sync-Button** in der Sidebar
- Desktop muss NICHT mehr als Server laufen
- Desktop wird reiner Client (wie Android)

---

## Setup-Anleitung — fertig, siehe [FinTracker-Server-Setup.md](FinTracker-Server-Setup.md)

Diese sieben Punkte waren die Skizze. Geschrieben wurde die Anleitung am Ende der
Phase, aus dem tatsaechlich Getanen (#95), und sie weicht an mehreren Stellen ab:
Java 21 statt 17, Datenbank auf einem separaten USB-Stick, drei systemd-Drop-ins,
eine taegliche Sicherung — und ein halbes Dutzend Stolpersteine, die man nicht
vorhersagen konnte.

1. Raspberry Pi OS installieren
2. Java/JVM installieren
3. Server-JAR deployen (`fintracker-server.jar`)
4. systemd Service einrichten
5. Tailscale auf Pi, Handy und Desktop einrichten (#94)
6. API Key in App-Einstellungen eintragen
7. Erster Sync

---

## Korrektur 21.08.2026: Tailscale statt offenem Port

Dieser Plan schrieb ursprünglich Portweiterleitung am Router plus DynDNS vor. **Das gilt nicht mehr.** Die Entscheidung fiel auf Tailscale (#94), aus einem Grund, der hier stehen bleiben soll:

Eine Portweiterleitung stellt den Sync-Server ins offene Internet. Was ihn dann noch schützt, ist ein einziger API-Key vor einem selbstgeschriebenen Server, der Finanzdaten hält — und jeder Fehler darin ist von überall erreichbar. Tailscale nimmt den Server aus dem öffentlichen Netz heraus: Handy, Desktop und Pi sehen sich gegenseitig, sonst niemand. Der API-Key aus #89 bleibt als zweite Schicht.

Nebenwirkung, bewusst in Kauf genommen: ein Dritter (Tailscale) ist an der Verbindung beteiligt. Das ist der Preis dafür, keinen offenen Port zu verantworten.

Kein HTTPS bleibt damit vertretbar — der Verkehr läuft ohnehin nur durch das verschlüsselte Tailscale-Netz oder durch das eigene WLAN.

---

## Was Phase 7 NICHT macht

- Kein Cloud-Hosting (kein AWS, kein VPS — nur eigener Pi)
- Kein Echtzeit-Sync (weiterhin manuell per Button)
- Keine Benutzerverwaltung / Multi-User
- Kein HTTPS (unnötig geworden, siehe Korrektur oben — der Verkehr läuft durch Tailscale)

---

## Erfolgskriterien

- [ ] Pi-Server startet automatisch beim Boot
- [ ] Android kann von unterwegs (mobilem Internet) syncen
- [ ] Desktop muss nicht offen sein für Sync
- [ ] Sync-Button auf beiden Plattformen funktioniert
- [ ] Daten kommen korrekt an (kein Datenverlust)

---

## Commit & Merge

```
Branch: feature/phase-7-raspberry-pi-server
Commit: "feat: Phase 7 Raspberry Pi self-hosted sync server"
Merge: feature/phase-7-raspberry-pi-server → main
```
