# Änderungsübersicht LARA AI HUB

Neueste Einträge oben. Jeder Eintrag nennt, wer was geändert hat, wo es liegt und wie es geprüft wurde. Den aktuellen Gesamtstand beschreibt `PROJECT-STATUS.md`. Die Einträge bis zum 08.11.2026 stehen ausführlich in `docs/AI_SHARED_HANDOFF.md` (Branch `claude/pc-thorben-setup-i4qwi5`).

## 2026-11-08

### Claude: Fehlermeldung bei der APK-Installation festgehalten

- **Branch:** `claude/projekt-dokumentation`. **Geändert:** `PROJECT-STATUS.md` (F17), `CHANGELOG.md`.
- **Gemeldet von Thorben:** „App konnte nicht installiert werden“.
- **Geprüft:** Struktur von `LARA-Test-4c4a194.apk` im Vergleich mit `lara-debug-latest`: keine Abweichung, die eine Installation verhindern würde.
- **Nicht geprüft:** Ursache auf dem Gerät.

### Claude: Prüfung der lokalen KI unter 192.168.178.21:12434

- **Branch:** `claude/projekt-dokumentation`, PR #4
- **Neu:** `docs/LOKALE-KI-PRUEFUNG.md`. **Geändert:** `PROJECT-STATUS.md`, `CHANGELOG.md`. Kein Code geändert.
- **Ergebnis:** LARA unterstützt OpenAI-kompatible Server. Die aktuelle App, Core und Node-Proxy lassen diese Adresse über HTTP nicht zu.
- **Ausgeführt:** `GET /v1/models` und `POST /v1/chat/completions` aus der Cloud-Umgebung: keine Verbindung (curl-Fehler 7). Danach von Thorben im Alpine-Terminal auf dem Pixel: `POST /v1/chat/completions` an `192.168.178.21:12434` und `127.0.0.1:12434`, jeweils HTTP 200 mit echter Modellantwort (2,10 s und 1,05 s).
- **Nicht getestet:** Verbindung aus der LARA-App.

### Claude: Gemeinsame Projektdokumentation

- **Branch:** `claude/projekt-dokumentation`, PR #4 nach `main` (nicht gemergt)
- **Neu:** `PROJECT-STATUS.md`, `CHANGELOG.md`, `tools/alpine-info.sh`
- **Commits:** `f3a1b0f` (Skript), dieser Commit (Dokumentation)
- **Geprüft:** Alle Branches, Pull Requests und CI-Läufe gelesen, Dokumente mit dem Code abgeglichen. Node-Tests lokal: 11 bestanden. Python-Tests lokal nicht ausführbar (Pakete fehlen), CI-Ergebnis übernommen. `tools/alpine-info.sh` unter Linux (dash) gegen nachgebaute Ollama- und MCP-Server getestet, eingebaute Test-Geheimnisse erschienen nicht in der Ausgabe.
- **Nicht geprüft:** Das Skript auf Alpine.
- **Offen:** Freigabe für die Übernahme nach `main`.

### Claude: Verbindungstest LARA → Ollama / MCP (Phase A)

- **Branch:** `claude/alpine-verbindungstest`, baut auf `claude/pc-thorben-setup-i4qwi5` auf (kein PR, nicht gemergt)
- **Commits:** `f9473d2` (Auftrag übernommen), `0373531` (Verbindungstest), `22d4ee6` (Doku), `a709d88` (Prüfbefunde behoben), `4c4a194` (Textfarbe der Karte)
- **Neu:** `AlpineProbe.kt`, `AlpineProbeCard.kt`, `AlpineProbeTest.kt`, `.github/workflows/android-branch-check.yml`, `docs/ALPINE-OLLAMA-STATUS.md`
- **Geändert:** `MainActivity.kt` (eine Karte unter Tools, `copyText` intern), `AndroidManifest.xml` (App-Name über Platzhalter), `app/build.gradle.kts` (Commit im Build, Testpaket, Testbibliothek), `docs/AI_SHARED_HANDOFF.md` (Eintrag angehängt)
- **Tests:** GitHub Actions Run 42522816831 (`a709d88`) und Run 42523661265 (`4c4a194`): je 80 Unit-Tests, 0 Fehler, APK gebaut.
- **Unabhängige Prüfung:** Erste Runde „fix first“ mit neun Befunden, alle bearbeitet in `a709d88`. Zweite Runde „ship“, verbleibend nur kleine Testlücken. `4c4a194` wurde nur gelesen.
- **Bereitgestellt:** `LARA-Test-4c4a194.apk` im Claude-Chat, SHA-256 `651b7bfaefdd7df9cf1103fd9993745171334374dd672227aab428558f107de5`.
- **Fehler gefunden und behoben:** falsche „erreichbar“-Meldung bei fremden Diensten, Fehlerursachen falsch benannt, eingetippte Zugangsdaten konnten gespeichert werden, Servertext im Bericht. Getestet durch die in `a709d88` verschärften Unit-Tests.
- **Nicht getestet:** Alles auf dem Pixel.

### ChatGPT / Thorben: Auftrag und Arbeitsregeln

- **Branch:** `main`
- **Commits:** `9e46bea`, `da09e1e`, `a92bfd5` (`docs/ALPINE-OLLAMA-MCP-INTEGRATION.md`), `9ad93f9`, `ec254c7` (`docs/USER-WORKFLOW.md`)
- **Art:** Nur Dokumentation. Wer genau committet hat, ist aus Git nicht erkennbar (Autor: Thorbens Konto).

## Bis 2026-11-08 (Kurzfassung)

| Branch | Inhalt | Details |
|---|---|---|
| `main` | Starter-App, Postfach | `a33060e` |
| `feature/android-secure-api-key-storage` | Anbieter, Keystore, Automate, OpenClaw | PR #1 |
| `claude/pc-thorben-setup-i4qwi5` | Sicherheit, App-Sperre, Design, Monitor, Core, Gedächtnis | PR #3, `docs/AI_SHARED_HANDOFF.md` |
| `feature/automate-python-bridge` | Python-Bridge für Automate | PR #2 |
