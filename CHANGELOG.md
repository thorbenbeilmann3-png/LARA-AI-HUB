# Änderungsübersicht LARA AI HUB

Neueste Einträge oben. Jeder Eintrag nennt, wer was geändert hat, wo es liegt und wie es geprüft wurde. Den aktuellen Gesamtstand beschreibt `PROJECT-STATUS.md`. Die Einträge bis zum 08.11.2026 stehen ausführlich in `docs/AI_SHARED_HANDOFF.md` (Branch `claude/pc-thorben-setup-i4qwi5`).

## 2026-11-08

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
- **Tests:** GitHub Actions Run 42522816831 (`a709d88`): 80 Unit-Tests, 0 Fehler, APK gebaut. Build für `4c4a194` lief beim Schreiben noch.
- **Unabhängige Prüfung:** Erste Runde „fix first“ mit neun Befunden. Alle bearbeitet in `a709d88`. Zweite Runde lief beim Schreiben noch.
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
