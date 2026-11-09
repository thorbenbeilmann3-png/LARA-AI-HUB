# Änderungsübersicht LARA AI HUB

Neueste Einträge oben. Jeder Eintrag nennt, wer was geändert hat, wo es liegt und wie es geprüft wurde. Den aktuellen Gesamtstand beschreibt `PROJECT-STATUS.md`. Die Einträge bis zum 08.11.2026 stehen ausführlich in `docs/AI_SHARED_HANDOFF.md` (Branch `claude/pc-thorben-setup-i4qwi5`).

# Änderungsübersicht LARA AI HUB

Neueste Einträge oben. Jeder Eintrag nennt, wer was geändert hat, wo es liegt und wie es geprüft wurde. Den aktuellen Gesamtstand beschreibt `PROJECT-STATUS.md`. Die Einträge bis zum 08.11.2026 stehen ausführlich in `docs/AI_SHARED_HANDOFF.md` (Branch `claude/pc-thorben-setup-i4qwi5`).

## 2026-11-09

### Claude: Preview-Stand 6c12380, Automate-Aktion entfernt (Branch, kein Merge)

| Angabe | Wert |
|---|---|
| Datei | `LARA-Preview-6c12380.apk` |
| Commit | `6c12380` auf `claude/alpine-verbindungstest` |
| SHA-256 | `1f25d7e20cafc6679d63d16ce30dfc9137cb5c1c48cb5a8ab33fde44b477ce7d` (aus der APK nachgerechnet, stimmt mit dem GitHub-Wert überein) |
| Größe | 18.419.942 Bytes |
| CI-Lauf | Android branch check #12 (Run-ID `42597721597`), grün; 167 Unit-Tests, 0 Fehler, Tests unverändert |
| Am Gerät getestet | NEIN |

- **Entfernt (E14):** die nicht dokumentierte Automate-Aktion, das Feld für die Flow-Adresse und der Autostart. In der APK geprüft: Die Aktion kommt im Code nicht mehr vor.
- **Zurückgezogen (E15):** Der neue Chat-Client aus `34ea27e` (193 Tests, grün). Er kommt direkt nach der Preview als F4/F5-Fix zurück, dann mit Gerätetest. Bis dahin sind F4 und F5 offen.
- **Chat-Tab:** Er nutzt dieselbe Funktion wie Etappe 4, die am Gerät durch die Test-Nachricht belegt ist. Der Chat-Tab selbst ist am Gerät noch nicht getestet.
- **`lara-debug-latest`:** nicht berührt.
- **Release-Entwurf:** `lara-alpine-test-6c12380` enthält dieselbe Datei als `LARA-Test-6c12380.apk`. Einen Entwurf mit dem Preview-Namen hat GitHub für die Claude-Sitzung abgelehnt (HTTP 403).
- **Regel (E10):** Änderungen an `main` nur noch über Pull Requests mit Thorbens Freigabe.

### Claude: Paket A, FreeLLMAPI, Eingaben merken und Automate (Branch, kein Merge)

Alles auf `claude/alpine-verbindungstest`. Android branch check #10 (Run-ID `42588759747`) grün: 167 Unit-Tests, 0 Fehler, 120 bestehende unverändert. Am Gerät getestet: NEIN.

| Paket | Commits | Umgesetzt | Offen | Braucht Thorben |
|---|---|---|---|---|
| A: feste CI-Signatur (F10, F17) | `f62e983`, `0264d96`, `ba631d8` | Secret `LARA_CI_KEYSTORE_B64` wird Build-Signatur, ohne Secret weiter Debug-Signatur. 77 Skript-Prüfungen, Beweis mit echten Builds (Run `42586874287`). Anleitung `docs/FESTE-SIGNATUR.md` | Echtes Secret, Update am Pixel | Secret eintragen |
| FreeLLMAPI | `c57ef92` | Anbieter mit Standard-Adresse, Test nur `GET /v1/models`, Schlüssel optional im ApiKeyStore, keine LAN-Ausnahme. 11 Tests. Anleitung `docs/FREELLMAPI.md` | Echter Server, HTTPS | HTTPS auf dem Server, falls gewünscht |
| Eingaben merken | `c57ef92` | Adresse, Modell und MCP-Adresse pro Anbieter in den App-Einstellungen, nie Schlüssel. 12 Tests | Anzeige am Pixel | – |
| Automate (F8, F9) | `0f3aaa7` | LARA-Aktion `de.laraai.hub.action.START_FLOW`, Extras `anbieter` und `aktion`, Bestätigung bei kostenpflichtigen Anbietern, höchstens ein automatischer Start pro Minute. 24 Tests. Anleitung `docs/AUTOMATE-SCHNITTSTELLE.md` | Echter Flow am Pixel | Darf die nicht dokumentierte Automate-Aktion bleiben? |

Neue Testversion: `LARA-Test-0f3aaa7.apk`, SHA-256 `5251df37590611cc0fe1bcd0d34482220b58abd2c770e73ae1865ddbeaf0f37f`, 18.419.942 Bytes.

### Claude: F18-Testversion 64fe56d dokumentiert

| Angabe | Wert |
|---|---|
| Datei | `LARA-Test-64fe56d.apk` (privater Release-Entwurf `lara-alpine-test-64fe56d`) |
| Commit | `64fe56d` auf `claude/alpine-verbindungstest` (enthält `dd3ae0b`) |
| SHA-256 | `4214bfe34b2cc343000360dfb77425c007d342a47825f0f50f8746177d7e00f3` (aus der heruntergeladenen APK nachgerechnet, stimmt mit dem GitHub-Wert überein) |
| Größe | 18.403.466 Bytes |
| CI-Lauf | Android branch check #6 (Run-ID `42540090395`), grün; 120 Unit-Tests, 0 Fehler, 0 übersprungen |
| Datum | 09.11.2026, 01:27 MEZ |
| Paket | `de.laraai.hub.alpinetest` |
| Zertifikat | SHA-256 `f7d0fff7fc30a0a81fb3fcd4527d9411f58bff0b16f3d76cf1d53103cb265a65` |
| Am Gerät getestet | NEIN, bis Thorben das Ergebnis einträgt |

- **VERALTET, nicht mehr installieren:** `LARA-Test-4c4a194.apk` (Commit `4c4a194`, SHA-256 `651b7bfaefdd7df9cf1103fd9993745171334374dd672227aab428558f107de5`, Zertifikat `b84e63b05af04ff0c197a5255636fb2eb1a0117c33c22d7c5f2c8bac4860a044`). Sie meldet auf Port 12434 fälschlich „Modell fehlt“ (F18). Wegen der anderen Signatur muss sie vor der Installation der neuen Datei deinstalliert werden; das löscht ihre lokalen Daten (Schlüssel, Chat).
- **Gegenüber `LARA-Test-4c4a194.apk` neu:**
  1. F18-Fix: Liefert `/api/tags` HTTP 200 mit leerer Modellliste, fragt `AlpineProbe` zusätzlich `/v1/models` ab und übernimmt `data[].id` (`dd3ae0b`, Regressionstests in `64fe56d`).
  2. Einrichtungsassistent mit 10 Etappen (`e90e5a3`; `SetupWizard.kt`, `SetupWizardPage.kt`, `SetupWizardTest.kt`).
  3. App-weite Textfarbe korrigiert (`e90e5a3`, `LaraTheme.kt`).
- **Tests:** Die 80 Tests aus `4c4a194` bleiben unverändert; `AlpineProbeTest.kt` wurde nur ergänzt (42 Zeilen), `SetupWizardTest.kt` ist neu. Die im Eintrag von ChatGPT und im Auftrag vom 09.11. genannten 83 Tests sind überholt, weil der Setup-Assistent eigene Tests mitbringt.
- **Erwartung am Gerät (nicht geprüft):** Der Verbindungstest gegen `127.0.0.1:12434` meldet `qwen2.5-0.5b-instruct-q4_k_m` als gefunden; die Etappen 1 bis 6 des Assistenten sind durchklickbar.
- **Integration:** Auf Thorbens ausdrückliche Anweisung vom 09.11.2026 nach `main` übernommen, nur `PROJECT-STATUS.md` und `CHANGELOG.md`. PR #1, #2 und #3 sind nicht angerührt.

### ChatGPT: F18 – Fallback auf OpenAI-kompatible Modellliste

- **Branch:** `claude/alpine-verbindungstest`
- **Commits:** `dd3ae0b` (Implementierung), `64fe56d` (drei Regressionstests).
- **Geändert:** `AlpineProbe.kt`: Wenn `/api/tags` erfolgreich eine leere Modellliste liefert, wird zusätzlich `/v1/models` gelesen; Modellnamen werden aus `data[].id` übernommen. Bei bereits gefüllter oder nicht lesbarer Tags-Liste erfolgt kein zusätzlicher Request.
- **Tests ergänzt:** leerer Tags-Endpunkt mit Modell-Fallback; Fallback-Endpunkt 404 bleibt `MISSING` mit „fehlt“; gesunder Server verwendet exakt drei Requests und fragt `/v1/models` nicht ab.
- **Prüfstatus:** Änderungen auf GitHub gespeichert. Die bestehende Testsuite wurde nicht lokal ausgeführt; GitHub-Actions-Ausführung und Gerätetest stehen noch aus. Die 80 vorhandenen Tests wurden nicht verändert. Erwartet werden 83 Tests insgesamt.
- **Integration:** Kein Merge; Branch bleibt separat bis zu Thorbens Freigabe.

## 2026-11-08

### Claude: Gemeinsame Dateien in `main`, Antworten von Thorben festgehalten

- **Commit:** PR #4 gemergt als `abd6b38`, Freigabe von Thorben: „Ja, und künftig direkt pflegen“. **Geändert:** in `main` neu `PROJECT-STATUS.md`, `CHANGELOG.md`, `docs/LOKALE-KI-PRUEFUNG.md`, `tools/alpine-info.sh`. Kein App-Code geändert.
- **Festgehalten:** E7 (Statusdateien direkt in `main`), E8 (neue Etappen 7 bis 10), E9 (Claude und ChatGPT über API-Schlüssel), Freigabe der Textfarbe, zwei neue Wünsche im Wortlaut.
- **Geprüft:** `main` war vor dem Merge unverändert, der Diff enthält nur neue Dateien und keine Muster von Schlüsseln. Quellen zu E9 wurden gelesen, die OpenAI-Hilfeseiten zur Abrechnung ließen sich nicht öffnen.
- **Nicht getestet:** nichts davon betrifft die App auf dem Gerät.

### Thorben / Claude: Erster Gerätebericht der Testversion

- **Branches:** `claude/projekt-dokumentation`, `claude/alpine-verbindungstest`. **Geändert:** `PROJECT-STATUS.md`, `CHANGELOG.md`, `docs/LOKALE-KI-PRUEFUNG.md`, `docs/ALPINE-OLLAMA-STATUS.md`. Kein Code geändert.
- **Ausgeführt von Thorben auf dem Pixel 9a, Android 17:** Verbindungstest in `LARA Test`, Build `4c4a194`, 22:27 Uhr. Port 12434 antwortet (Version 0.1.32, `/api/tags` leer), `127.0.0.1:8765/mcp` antwortet mit 401.
- **Nicht getestet:** Chat aus der App, Ollama in Alpine auf Port 11434.

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
