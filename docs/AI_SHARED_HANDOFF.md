# LARA AI HUB – Gemeinsames Übergabeprotokoll für ChatGPT und Claude

> Zweck: Dieses Dokument ist die gemeinsame, versionierte Arbeitsübergabe. ChatGPT und Claude lesen vor Projektarbeit den aktuellen Stand und schreiben nach ihrer Arbeit ihre Änderungen hier hinein. GitHub-Commits sind der nachvollziehbare Austauschweg; es gibt keine automatische Echtzeit-Synchronisierung zwischen den Modellen.

## Verbindliche Regeln
1. Vor jeder Arbeit zuerst dieses Dokument und den aktuellen Repository-/Branch-Stand lesen.
2. Keine Annahmen als Tatsachen ausgeben. Unsicherheiten, fehlende Zugriffe und nicht ausgeführte Tests ausdrücklich kennzeichnen.
3. Änderungen grundsätzlich auf einem Arbeitsbranch durchführen, nicht direkt auf `main`. Keine Merge-, Lösch-, Überschreib- oder Produktionsaktion ohne ausdrückliche Freigabe des Nutzers.
4. Vor Änderungen vorhandene Dateien, offene Änderungen und relevante Tests prüfen. Kleine, klar abgegrenzte Aufgaben bearbeiten.
5. Nach jeder Aufgabe Ergebnis, betroffene Dateien, Commit/PR, Tests mit konkreten Resultaten und offene Risiken dokumentieren.
6. Keine API-Schlüssel, Tokens, Passwörter, privaten Schlüssel, persönlichen Geheimnisse oder andere sensible Zugangsdaten in dieses Dokument oder ins Repository schreiben. Nur Verweise auf sichere Secret-Namen/Key-Stores, niemals deren Werte.
7. Der jeweils andere Assistent darf dokumentierte Arbeit unabhängig prüfen, Fehler benennen und eine Korrektur vorschlagen. Nicht stillschweigend fremde Änderungen überschreiben.
8. Bei Konflikten zuerst stoppen, Konflikt beschreiben und den Nutzer fragen, statt Daten zu verlieren.
9. GitHub ist die gemeinsame Quelle der Wahrheit. Ein Modell sieht Änderungen des anderen erst, nachdem sie committed und für den verwendeten Branch/Ref verfügbar sind.

## Projektziel
LARA AI HUB ist die persönliche KI-Zentrale des Nutzers mit Android-App als Bedienoberfläche, sicherer Anbindung von KI-Modellen und Werkzeugen, Projekt-/Auftragsspeicher, Claude-Code-Integration, Arena für Modellvergleiche, Agentenkoordination, Datei- und Aufgabenverwaltung sowie nachvollziehbaren Tests.

## Nutzerprioritäten
- Moderne, hochwertige und gut bedienbare Android-Oberfläche; bestehende gute Strukturen nicht unnötig ersetzen.
- Funktionierende, getestete Bedienung statt bloßer Mockups oder unbestätigter Versprechen.
- Chat, mehrere Sitzungen, Datei-/Bildanhänge, Modellwahl und Projektsortierung.
- Claude Code aus LARA heraus steuern: Aufgaben senden, Rückfragen/Freigaben beantworten, Fortschritt, Terminalausgabe, Änderungen und Tests sehen.
- KI-Arena: mehrere Modelle bei komplexen Aufgaben vergleichen; Ergebnisse anhand Quellen, Korrektheit, Vollständigkeit, Umsetzbarkeit, Sicherheit und Kosten bewerten.
- Koordinator, Umsetzer und unabhängiger Prüfer mit klaren Zuständigkeiten und Status.
- Python, Automate, Ollama/lokale Modelle, Git/GitHub und weitere Werkzeuge passend zur Aufgabe auswählen.
- Aufgabenfortschritt, Schritte, Token-/Kontextverbrauch und Provider-Kosten getrennt und ehrlich darstellen; unbekannte Werte als unbekannt markieren.
- Projektgedächtnis, Wiederaufnahme nach App-Neustart und sichere Übergaben.
- API-Schlüssel mit Android Keystore und System-Biometrie/Geräteauthentifizierung schützen.
- Dateien automatisch klassifizieren und geeignete Projektordner anlegen.
- Android-Hintergrundlimits, Berechtigungen, Datenschutz und Sicherheitsgrenzen berücksichtigen.

## Aktueller bekannter Stand (Snapshot 2026-11-07)
- Repository: `thorbenbeilmann3-png/LARA-AI-HUB`.
- Aktiver Arbeitsbranch in den zuletzt gesehenen Actions: `claude/pc-thorben-setup-i4qwi5`.
- Zuletzt beobachteter Android-Debug-APK-Build mit Erfolg: Run `42393218773`, Commit `f2bf74824739ad61e9185edb53da307a184fc0f7`. Artefakt `LARA-AI-HUB-debug` war verfügbar.
- Neuere Commits/PR-Arbeit nach diesem APK-Build wurde beobachtet; der jüngste Commit zum Zeitpunkt dieses Snapshots war `e0ca9f3586e7224aa3791e50975235143970421e` mit Monitor-Tab/Token-Monitoring. Die dazugehörigen Actions waren beim Abruf noch `queued`; daher ist dieser Code nicht als in einer APK enthalten oder erfolgreich gebaut bestätigt.
- Erfolgreiche Python- und Node-Testläufe lagen für Commit `d4f381631ca17dd5ea22e762b1ea0eb89f12ba53` vor. Das ist kein Nachweis, dass ein Android-APK-Build dieses Commits erfolgreich war.
- Diese Angaben sind ein zeitgebundener Snapshot und müssen vor jeder Arbeit durch aktuelle GitHub-Daten überprüft werden.

## Bekannte Sicherheits-/Qualitätsanforderungen
- Kein offener Worker-Chat-Endpunkt ohne Authentifizierung.
- Freigaben niemals einem vom Client gelieferten `approved`-Feld vertrauen; Autorisierung serverseitig erzwingen.
- Keine unkontrollierten SSRF-Ziele oder beliebige Zieladressen.
- Keine pauschale Freigabe unverschlüsselter HTTP-Verbindungen.
- Loopback-Dienste nur mit Authentifizierung und minimalen Endpunkten; keine beliebige Shell-/Python-Ausführung.
- Keine Secrets in Logs, Quellcode, Builds oder diesem Dokument.
- Unabhängige Tests für Authentifizierung, Berechtigungen, Wiederherstellung, Fehlerfälle und Android-Integration.

## Arbeitsstatus
### Aktuelle Aufgabe
- Status: OFFEN
- Verantwortlich: noch festzulegen
- Ziel: Gemeinsame, nachvollziehbare Übergabe zwischen ChatGPT und Claude etablieren.
- Nächster Schritt: Claude soll dieses Dokument einlesen, bestätigen, ob es die Projektstruktur korrekt wiedergibt, und konkrete Korrekturen/Ergänzungen als Commit auf dem Arbeitsbranch vorschlagen.

### Letzte abgeschlossene Übergaben
Noch keine Einträge.

## Änderungsprotokoll – von jedem Assistenten nach jeder Aufgabe ergänzen
Für jeden Eintrag diese Vorlage vollständig ausfüllen:

### [Datum/Uhrzeit] – [Kurzname der Aufgabe]
- Agent/Assistent:
- Zuständigkeit:
- Eingaben und Ausgangs-Commit:
- Bearbeitung:
- Geänderte Dateien:
- Commit / Pull Request:
- Ergebnisse:
- Tests: (Befehl/Test, Ergebnis, Anzahl; nicht ausgeführte Tests ausdrücklich nennen)
- Unabhängige Prüfung:
- Risiken / offene Punkte:
- Status: OFFEN / ANNAHME / FEHLER
- Fehlende Informationen / BLOCKIERT:
- Nächster Agent:
- Übergabedaten:

## Antwortformat für neue Einträge
Kurz, konkret und überprüfbar. Keine pauschalen Aussagen wie „alles funktioniert“. Jede Aussage zu Tests muss auf tatsächlich ausgeführten Tests beruhen.
### 2026-11-07 18:10 (Europe/Berlin) – Prüfung des Übergabeprotokolls und Stand von PR #3
- Agent/Assistent: Claude (Claude Code, Cloud-Sitzung)
- Zuständigkeit: Umsetzer für PR #3 (`claude/pc-thorben-setup-i4qwi5` → `feature/android-secure-api-key-storage`)
- Eingaben und Ausgangs-Commit: dieses Dokument (`0bdde5e`), PR #3, GitHub-Actions-Läufe
- Bearbeitung: Dokument gelesen und mit dem Repository abgeglichen. Inhalt nur ergänzt, nichts überschrieben (Regel 7).
- Bestätigung: Ziel, Prioritäten und Sicherheitsanforderungen geben das Projekt korrekt wieder.
- Korrekturen zum Snapshot:
  1. Der Android-Build für `e0ca9f3` (Monitor-Tab) ist **fehlgeschlagen**: 1 von 28 Unit-Tests rot (`TokenUsageTest.gaugeThresholds`). Ursache war ein falscher Erwartungswert im Test (64,0 statt korrekt 64,1 % für 82.000/128.000), nicht der Code. Korrigiert in `886ae95`.
  2. Die Freigabe-APK (`releases/download/lara-debug-latest/LARA-debug.apk`) entspricht dem letzten **erfolgreichen** Android-Build. Sie enthält App-Sperre, Chatverlauf und Klartext-Sperre, den Monitor-Tab erst nach einem grünen Build von `886ae95` oder später.
  3. Ergänzend gibt es inzwischen: Auftragsspeicher (`docs/AUFTRAGSSPEICHER.md`), verifizierten Modellwechsel (`docs/MODELLWECHSEL.md`), App-Sperre (`docs/APP-SPERRE.md`).
- Geänderte Dateien: `app/src/test/java/de/laraai/hub/TokenUsageTest.kt`, dieses Dokument
- Commit / Pull Request: `886ae95`, dieser Eintrag; PR #3
- Tests: Worker `pytest -q` 64 passed (lokal, Python 3.13). Node `npm test` 11 passed (lokal und CI). Android-Unit-Tests: CI-Lauf für den neuen Stand gestartet, Ergebnis **noch offen**. Auf dem Pixel: **nichts getestet**.
- Unabhängige Prüfung: noch keine. ChatGPT kann PR #3 commitweise prüfen, jeder Commit hat eine eigene Doku.
- Risiken / offene Punkte: App-Sperre und Keystore-Übernahme sind nur auf dem Gerät prüfbar. Welche Tokenzahlen der Pixel-Modellserver liefert, ist unbekannt. Der Core läuft noch nicht auf dem Handy.
- Status: OFFEN (Android-CI und Gerätetests ausstehend)
- Fehlende Informationen / BLOCKIERT: Gerätetest-Ergebnisse vom Nutzer
- Nächster Agent: Claude (Core-Gateway für das lokale Modell); ChatGPT optional als Prüfer
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`, Basis `feature/android-secure-api-key-storage`

### 2026-11-07 17:15 UTC (18:15 Europe/Berlin) – Technischer Befund, Projektstand und Tests
- Agent/Assistent: Claude (Claude Code, Cloud-Sitzung, kein Zugriff auf Pixel oder Windows-PC)
- Zuständigkeit: Umsetzer für PR #3; dieser Eintrag ist eine Bestandsaufnahme, keine Codeänderung
- Eingaben und Ausgangs-Commit: Branch `claude/pc-thorben-setup-i4qwi5` bei `d42e20a`, Basis `feature/android-secure-api-key-storage` (`6ab7c8a`), PR #3 (Draft, nicht gemergt)

#### Commits auf diesem Branch (Zeiten in UTC, alle in PR #3)
| SHA | Inhalt |
|---|---|
| `d6e8b98` | Worker: Token für `/v1/chat`, serverseitige Freigaben, CI-Importfehler behoben |
| `08ed404` | Node-Server: `127.0.0.1`, SSRF-Positivliste, Host-Prüfung, kein CORS `*` |
| `cb7feb0` | Android: Klartext-HTTP nur Loopback, Standard `127.0.0.1:12434` |
| `4285334` | Android: Chatverlauf wird mitgesendet |
| `91d3294` | Android: App-Sperre (BiometricPrompt), API-Schlüssel an Anmeldung gebunden |
| `eb3105f` | Core: Auftragsspeicher, Fortschritt, Token-Protokoll |
| `f2bf748` | CI: APK als Pre-Release `lara-debug-latest` |
| `d4f3816` | Core: verifizierter Modellwechsel (Übergabepaket + Prüfsumme) |
| `e0ca9f3` | Android: Monitor-Tab (Tokens, Kontext, Core-Aufträge) |
| `0bdde5e` | (Nutzer) dieses Übergabeprotokoll |
| `886ae95` | Android: falscher Testwert korrigiert (64,1 %) |
| `09230dd` | Claude-Prüfeintrag in diesem Dokument |
| `77e57b5` | Core: Gateway für das lokale Modell mit Token-Erfassung |
| `8aee744` | Core: minimales `/health`, Rate Limiting, Sperre nach falschen Tokens |
| `d42e20a` | Core: Startskript `run_core.py` für Pydroid |

#### Tests, die tatsächlich ausgeführt wurden (2026-11-07, ca. 17:12 UTC, auf `d42e20a`)
| Test | Befehl / Ort | Ergebnis |
|---|---|---|
| Python-Worker/Core | `cd python-worker && pytest -q` (Python 3.13, lokal) | **86 passed** |
| Node-Server | `npm test` (Node 22, lokal) | **11 passed**, 0 failed |
| Python-Bridge | `python -m unittest` im Branch `feature/automate-python-bridge` (`6cd8c40`) | **6 Tests OK** |
| Kotlin-Logik ohne Android | JVM-Hilfsprojekt, Gradle 8.14 | ChatHistory 9, CleartextPolicy 3, SessionLock 6 → **18 passed** |
| Core-Start echt | `run_core.py` lokal auf Port 8011, `curl` | `/health` 200, `/v1/jobs` ohne Token 401, mit Token 200, Token-Dateien `0600` |
| Android-Unit-Tests + APK-Build | GitHub Actions „Android debug APK“, Run `42394430325` auf `09230dd` | **success** (inkl. TokenUsageTest). Spätere Commits ändern keinen Android-Code |
| Python/Node in CI | „Python control plane tests“, „Node server tests“ auf `77e57b5` | **success** |
| Secret-Scan | `git grep` nach typischen Schlüsselmustern, eingecheckte DB/Token-Dateien | **0 Treffer** |
| **Nicht ausgeführt** | Alles auf dem Pixel (App-Sperre, Keystore-Übernahme, Chat gegen `127.0.0.1:12434`, Monitor, Core in Pydroid, Automate); alles auf Windows; echte Aufrufe von Cloud-Anbietern | **NOT TESTED** |

#### Erledigt (im Code, mit Tests, ohne Gerätetest)
Die vier bekannten Sicherheitslücken aus diesem Dokument sind im Code geschlossen: Worker-Anmeldung, serverseitige Freigaben, SSRF/`0.0.0.0` im Node-Server, Klartext-HTTP in der App. Außerdem fertig: App-Sperre, Chatverlauf, Auftragsspeicher mit berechnetem Fortschritt, Token- und Kontextüberwachung, verifizierter Modellwechsel, Monitor-Tab, lokales Gateway, Rate Limiting, Core-Start.

#### Offene Aufgaben
1. **Gerätetests auf dem Pixel** (Voraussetzung für jedes PASS auf dem Gerät).
2. Gedächtnis-Grundlage (Einträge mit Vertrauensstatus, Export `LARA_MEMORY/*.md`).
3. Automate-Flow für Bridge-Health/Echo mit Timeout und Fehlerweg (nur als Anleitung, weil `.flo` nicht validierbar ist).
4. Bridge (Branch `feature/automate-python-bridge`, PR #2) und Core zusammenführen. In Pydroid laufen sie voraussichtlich nicht gleichzeitig (ungeprüft).
5. Freigabe-Oberfläche in der App (Approver-Token, JA/NEIN).
6. Weitere Anbieter im Gateway. Das ist erst nach Freigabe des Nutzers für kostenpflichtige API-Zugänge möglich.
7. ARENA, Agenten, Recherchefilter und Windows-Anbindung sind noch nicht begonnen. Der PC-Fernzugriff bleibt gesperrt.

#### Erkannte Fehler und Sicherheitsbefunde (nicht kritisch, nicht behoben)
| Schwere | Befund | Ort | Empfehlung |
|---|---|---|---|
| Mittel | Die Übernahme der v1-Schlüssel ist „alles oder nichts“: Ist ein einziger alter Eintrag unlesbar, wird nichts übernommen, und die alten Schlüssel sind dann in der App nicht nutzbar. Die Daten gehen nicht verloren | `ApiKeyStore.migrateV1` | Lesbare Einträge übernehmen, unlesbare melden |
| Mittel | Das Rate Limiting zählt nach Client-Adresse. Auf dem Handy sind alle lokalen Apps `127.0.0.1`. Eine fremde App kann mit 10 falschen Tokens LARA für 5 Minuten aussperren (Verfügbarkeit, kein Datenleck) | `python-worker/app/ratelimit.py` | Sperre an den Token-Hash binden oder nur verzögern |
| Mittel | Der Worker sendet den Gemini-Schlüssel als URL-Parameter `?key=`. Er kann so in Logs landen. Die App nutzt korrekt den Header `x-goog-api-key` | `python-worker/app/main.py` (`/v1/chat`) | Header verwenden (Header-Name nach Kenntnisstand; Google-Doku hier gesperrt, ungeprüft) |
| Niedrig | `contents: write` gilt für den ganzen Android-Build-Job, auch für Drittanbieter-Actions | `.github/workflows/android-debug.yml` | Veröffentlichung in einen eigenen Job mit Schreibrecht auslagern |
| Niedrig | Der Node-Server hat keine Anmeldung. Er ist nur über Loopback und mit Host-Prüfung erreichbar, jede App auf dem Gerät kann den erlaubten Modellserver darüber nutzen | `index.js` | Token einführen, wenn das Web-Frontend weiter genutzt wird |
| Niedrig | Die Bridge gibt beim ersten Start den Token im Klartext in der Konsole aus (Screenshot-Risiko) | `lara_bridge.py` (PR #2) | Nur Dateipfad ausgeben |
| Info | Die Übergabeprüfung belegt korrekte Übertragung, nicht inhaltliches Verständnis | `docs/MODELLWECHSEL.md` | Ist dokumentiert |

#### Doku, die nicht mehr zum Code passt (nicht verändert, nur gemeldet)
- `docs/AI_HANDOVER.md` Z. 41, 187–188, 199: „gebunden an `0.0.0.0`“, Standard-Endpunkt `10.179.130.230`, `usesCleartextTraffic="true"`. Das ist **veraltet** seit `08ed404` und `cb7feb0`.
- `README.md` „Not implemented yet“: Mehrere Anbieter, verschlüsselter Schlüsselspeicher und der Python-Worker sind inzwischen vorhanden.
- Die vom Nutzer genannte WLAN-Adresse `10.186.32.74:12434` ist absichtlich **nicht** im Code. Auf dem Pixel gilt `127.0.0.1:12434` (vom Nutzer bestätigt: Server läuft auf dem Pixel).

#### Empfohlene nächste Schritte (Reihenfolge)
1. Nutzer: APK installieren (`releases/download/lara-debug-latest/LARA-debug.apk`) und die Gerätetests aus `docs/APP-SPERRE.md` und `docs/ANDROID-NETZWERK-SICHERHEIT.md` durchführen.
2. Claude: die drei mittleren Befunde beheben (Migration, Rate-Limit-Schlüssel, Gemini-Header), je mit Test.
3. Nutzer: Core in Pydroid nach `docs/CORE-AUF-DEM-PIXEL.md` starten und Ergebnis bzw. Fehlertext melden.
4. Claude: Gedächtnis-Grundlage, danach die Automate-Anleitung.
5. ChatGPT: unabhängige Prüfung von PR #3 commitweise, besonders `91d3294` (Keystore/Biometrie) und `d4f3816` (Übergabeprüfung).

- Unabhängige Prüfung: ausstehend (ChatGPT)
- Status: OFFEN
- Fehlende Informationen / BLOCKIERT: Gerätetest-Ergebnisse; Pydroid-Installierbarkeit von `pydantic`
- Nächster Agent: ChatGPT (Prüfung), danach Claude (Befunde beheben)
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`, Stand vor diesem Eintrag `d42e20a`, PR #3


## Einfache Nutzerbefehle für ChatGPT und Claude (ab 2026-11-07)

Der Nutzer möchte künftig keine langen technischen Anweisungen mehr zwischen den Assistenten kopieren müssen. Er soll in ChatGPT kurz schreiben können:

- **„GitHub lesen“**: ChatGPT liest zuerst diese Datei und prüft danach den aktuellen Repository-Stand, Branches, offene PRs/Commits und relevante Actions. Anschließend berichtet ChatGPT knapp, was neu ist, was offen ist und ob etwas blockiert.
- **„GitHub eintragen: [Information/Aufgabe/Ergebnis]“**: ChatGPT ergänzt diese Übergabedatei um genau diese Information bzw. das Ergebnis. Vorher aktuellen Dateiinhalt und Branch-Stand abrufen; keine fremden Änderungen überschreiben. Danach committen und den Link zum geänderten Dokument nennen.
- **„Claude soll …“**: ChatGPT formuliert bzw. dokumentiert die Aufgabe für Claude in dieser Datei unter „Auftrag an Claude“. Claude muss vor der Umsetzung die neueste Version dieser Datei lesen. ChatGPT behauptet nicht, Claude direkt gestartet oder benachrichtigt zu haben.
- **„Prüfe GitHub“**: ChatGPT führt eine unabhängige Prüfung des dokumentierten Claude-Ergebnisses anhand der tatsächlichen Commits, geänderten Dateien und Testläufe durch. Nicht ausgeführte Prüfungen bleiben ausdrücklich OFFEN.

### Verbindlicher Ablauf bei jeder Übergabe
1. Immer den aktuellen Stand dieses Dokuments und den aktuellen Branch-Head lesen; den Snapshot oben niemals ungeprüft als aktuellen Stand behandeln.
2. Änderungen nur auf dem passenden Arbeitsbranch vornehmen. Bei konkurrierenden Änderungen oder unklarem Branch stoppen und BLOCKIERT melden.
3. Jede Aufgabe mit konkreten Eingaben, Dateien, Commit-SHA, Testergebnissen und offenen Punkten dokumentieren.
4. Claude liest die Datei vor Arbeitsbeginn und aktualisiert sie nach Abschluss mit Commit-SHA und tatsächlich ausgeführten Tests.
5. ChatGPT liest danach die aktualisierte Datei und prüft die Aussagen unabhängig. Erst danach gilt die Übergabe als geprüft.
6. Niemals mergen, Branches löschen, Secrets eintragen oder kostenpflichtige API-Aufrufe aktivieren, ohne ausdrückliche Nutzerfreigabe.

### Auftrag an Claude – vorhandenen Anthropic-Anschluss robust machen
- Status: UMGESETZT durch Claude (`cfda044`), unabhängige Prüfung durch ChatGPT OFFEN – siehe Eintrag „Anthropic-Anschluss robust gemacht“ unten
- Zuständigkeit: Claude als Umsetzer; ChatGPT anschließend als unabhängiger Prüfer
- Ziel: Den bereits vorhandenen Claude-/Anthropic-Anschluss in LARA verbessern. Keine zweite parallele Claude-Client-Klasse und keine neue API-Integration bauen.
- Ausgangspunkt: Die aktuelle Implementierung in `ApiConnection.kt` und die dazugehörigen Tests vollständig untersuchen. Vorhandene Keystore-Speicherung, App-Sperre, Chatverlauf, Modellabfrage und Token-Monitoring erhalten.
- Aufgabe 1: Prüfen, wie Anthropic-Antworten mit `stop_reason` behandelt werden. Ablehnungen (`refusal`) und abgeschnittene Antworten (z. B. `max_tokens`) sauber erkennen und in verständliche, unterscheidbare UI-/Fehlermeldungen übersetzen. Tatsächliche Anthropic-Semantik anhand offizieller Dokumentation verifizieren; keine Modellnamen oder API-Fähigkeiten erfinden.
- Aufgabe 2: Robuste Verarbeitung der Content-Blöcke sicherstellen. Nicht blind nur den ersten Block oder ein nicht vorhandenes `text`-Feld lesen. Mehrere Textblöcke korrekt sammeln und Nicht-Text-Blöcke/Thinking-Blöcke sicher behandeln.
- Aufgabe 3: Unit-Tests für normale Antwort, mehrere Textblöcke, Thinking-/Nicht-Text-Block vor Text, Ablehnung und abgeschnittene Antwort ergänzen. Tests müssen ohne kostenpflichtigen API-Aufruf laufen.
- Aufgabe 4: Vorhandene Timeout-, Modelllisten-, Verlauf- und Token-Monitor-Funktion nicht verschlechtern. Keine API-Schlüssel in Code, BuildConfig, Logs oder Repository.
- Aufgabe 5: Relevante Tests ausführen, Ergebnisse mit exakten Befehlen und Zahlen dokumentieren und einen APK-Build nur dann als erfolgreich bezeichnen, wenn GitHub Actions ihn tatsächlich als success meldet.
- Kostenregel: Keine echten Anthropic-API-Aufrufe und keine Einrichtung/Änderung von API-Schlüsseln. Diese Aufgabe soll durch Codeänderungen und Unit-Tests zunächst 0 € API-Kosten verursachen.
- Sicherheitsregel: Kein Merge und keine Änderung an kostenpflichtigen Provider-Einstellungen ohne ausdrückliche Freigabe.
- Abnahme: Änderungen sind auf einem Arbeitsbranch committed; Tests decken alle oben genannten Fälle ab; unabhängige Prüfung durch ChatGPT steht als eigener Status im Protokoll.
- Fehlende Informationen / BLOCKIERT: Falls die tatsächliche Implementierung oder offizielle API-Semantik nicht verifiziert werden kann, vor Codeänderung stoppen und den konkreten Grund dokumentieren.

### Übergabeprotokoll – Kurzformat
Jeder neue Eintrag muss alle Felder enthalten:
- Agent:
- Zuständigkeit:
- Eingaben:
- Bearbeitung:
- Ergebnisse:
- Status: OFFEN / ANNAHME / FEHLER
- Fehlende Informationen:
- Nächster Agent:
- Übergabedaten:
- Dokumentation:

### 2026-11-07 17:35 UTC (18:35 Europe/Berlin) – Anthropic-Anschluss robust gemacht
- Agent: Claude (Claude Code, Cloud-Sitzung)
- Zuständigkeit: Umsetzer für den „Auftrag an Claude – vorhandenen Anthropic-Anschluss robust machen“
- Eingaben: dieses Dokument bei `1796fd8`; `app/src/main/java/de/laraai/hub/ApiConnection.kt`; offizielle Anthropic-Doku zu `stop_reason` (https://platform.claude.com/docs/en/build-with-claude/handling-stop-reasons, live abgerufen) und die Claude-API-Referenz der Sitzung
- Bearbeitung:
  - **Keine** zweite Client-Klasse und keine neue Integration. Der vorhandene Anthropic-Zweig in `ApiConnection.kt` ruft jetzt `AnthropicResponse.toChatText()` auf (neue Datei `AnthropicResponse.kt`).
  - Aufgabe 1 (`stop_reason`): Verifiziert sind die Werte `end_turn`, `max_tokens`, `stop_sequence`, `tool_use`, `pause_turn`, `refusal`, `model_context_window_exceeded`, und dass `stop_details` nur bei `refusal` gefüllt ist. Umsetzung: `refusal` wird zu einem eigenen Fehler („Claude hat die Anfrage abgelehnt (Kategorie: …): …“). `max_tokens`, `model_context_window_exceeded`, `pause_turn` und `tool_use` behalten den Text und bekommen einen mit „⚠“ markierten Hinweis. Damit sind beide Fälle in der Oberfläche unterscheidbar.
  - Aufgabe 2 (Content-Blöcke): Gesammelt werden nur Blöcke mit `type == "text"`, auch mehrere. `thinking`, `redacted_thinking` und unbekannte Blöcke werden übersprungen, auch wenn sie ein `text`-Feld haben.
  - Aufgabe 4: Zeitlimits (10 s Verbindung / 120 s Lesen), Modellliste, Chatverlauf, Token-Monitor, Keystore und App-Sperre bleiben unverändert. Keine Schlüssel in Code, BuildConfig, Logs oder Repository.
- Ergebnisse / Tests:
  - `AnthropicResponseTest` (9 Tests: normale Antwort, mehrere Textblöcke, Thinking/Redacted/unbekannter Block vor Text, Ablehnung mit Details, Ablehnung ohne Details, abgeschnitten, Kontextfenster voll, abgeschnitten ohne Text, leer). **Lokal** im JVM-Hilfsprojekt (Gradle 8.14, org.json 20240303): **9 passed**. Zusammen mit ChatHistory (9), CleartextPolicy (3) und SessionLock (6) sind es lokal **27 passed, 0 failed**.
  - **GitHub Actions** „Android debug APK“, Run `42395878371` (Nr. 49) auf `cfda044`: **success**. Er enthält `:app:testDebugUnitTest` (alle App-Unit-Tests einschließlich `TokenUsageTest`) und `:app:assembleDebug`. Die genaue Testanzahl in CI ist im Log nicht ausgewiesen, dort ist nur der Schritt als erfolgreich gemeldet.
  - Kosten: **0 €**. Es gab keinen echten Anthropic-Aufruf, an Schlüsseln wurde nichts geändert.
  - **NOT TESTED:** echte Claude-Antwort auf dem Gerät (dafür bräuchte es einen kostenpflichtigen API-Schlüssel).
- Zusätzlich behoben (aus dem Befund-Eintrag oben, eigene Commits):
  - `993297e` Core: Gemini-Schlüssel per Header statt URL. Gültige Tokens sind von der Rate-Limit-Sperre ausgenommen und haben einen eigenen Zähler. Tests: `pytest -q` **89 passed**.
  - `23a8789` App: Übernahme der v1-Schlüssel erfolgt einzeln statt „alles oder nichts“. Unit-Tests dafür sind nicht möglich (Android Keystore). Der Android-CI-Build dafür ist gestartet, **Ergebnis noch offen**. Gerätetest **NOT TESTED**.
- Status: OFFEN (wartet auf unabhängige Prüfung durch ChatGPT; CI für `23a8789` offen)
- Fehlende Informationen: keine für diese Aufgabe
- Nächster Agent: ChatGPT (Prüfung von `cfda044`, `993297e`, `23a8789`)
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`, PR #3 (Draft, nicht gemergt)
- Dokumentation: `docs/PYTHON-WORKER-SICHERHEIT.md` (Nachbesserung), `docs/APP-SPERRE.md` (Übernahme)



## Verbindliche Aufgabenverteilung zwischen ChatGPT und Claude (Nutzerfreigabe: 2026-11-07)

### Ziel
Claude soll nur für anspruchsvolle Aufgaben eingesetzt werden. ChatGPT übernimmt einfache und mittlere Aufgaben selbst, soweit die verfügbaren Werkzeuge und Berechtigungen dies erlauben. Dadurch sollen Claude-Kontext und Claude-Tokens geschont werden.

### Zuständigkeiten
**ChatGPT – primärer Arbeitsassistent**
- Dokumentation, Aufgabenlisten und GitHub-Projektstand lesen, prüfen und strukturieren.
- Einfache bis mittlere Fehleranalyse und klar begrenzte Codeänderungen.
- Kleinere Tests, Auswertungen, Texte und Konzepte.
- Aufgaben in konkrete Schritte zerlegen und Ergebnisse dokumentieren.
- Vor einer Claude-Übergabe prüfen, ob die Aufgabe selbst zuverlässig erledigt werden kann.

**Claude – Spezialist für anspruchsvolle Aufgaben**
- Komplexe Architekturänderungen und umfangreiche Refactorings.
- Schwierige Programmierfehler und systemübergreifende Integrationen.
- Anspruchsvolle Android-/Kotlin-/Python-Probleme.
- Sicherheitsprobleme und Aufgaben, bei denen ChatGPT nachweislich nicht zuverlässig weiterkommt.

### Token- und Kostenregeln
- Claude nicht für einfache Fragen, Routine-Dokumentation oder kleine Standardaufgaben einsetzen.
- Claude nur eine klar abgegrenzte Aufgabe mit den dafür notwendigen Dateien, Anforderungen und Fehlerprotokollen übergeben.
- Keine unnötigen Wiederholungen und keine vollständige Kontextübertragung, wenn ein präziser Verweis auf diese Datei genügt.
- Keine kostenpflichtigen API-Aufrufe und keine kostenpflichtigen Provideränderungen ohne ausdrückliche Zustimmung des Nutzers.
- Qualität und Sicherheit haben Vorrang vor Tokenersparnis; ChatGPT darf eine Aufgabe nicht selbst übernehmen, wenn es dafür die nötige Fähigkeit oder Berechtigung nicht besitzt.

### Gemeinsamer Ablauf
1. ChatGPT koordiniert und erledigt einfache/mittlere Aufgaben.
2. Wenn eine Aufgabe zu komplex ist, erstellt ChatGPT einen abgegrenzten Auftrag für Claude und dokumentiert ihn hier unter „Auftrag an Claude“.
3. Claude liest vor Beginn diese Datei und den aktuellen Repository-/Branch-Stand.
4. Claude dokumentiert Änderungen, betroffene Dateien, tatsächlich ausgeführte Tests, Commit-SHA und offene Punkte.
5. ChatGPT liest die aktualisierte Datei und prüft Claudes Ergebnisse unabhängig.
6. Kein Merge, Löschen, Überschreiben, Secret-Eintrag oder kostenpflichtiger API-Aufruf ohne ausdrückliche Freigabe.

## Morgenübergabe – genehmigte Aufgaben und Entscheidungen zusammentragen (Nutzerfreigabe: 2026-11-07)

### Auftrag an Claude
- Zieltermin: **Sonntag, 2026-11-08, spätestens 12:00 Uhr Europe/Berlin**.
- Prüfe den dir tatsächlich zugänglichen bisherigen Projekt- und Gesprächskontext auf Aufgaben und Entscheidungen, denen der Nutzer ausdrücklich zugestimmt hat.
- Trenne sauber:
  1. ausdrücklich genehmigte, noch offene Aufgaben;
  2. bereits erledigte Aufgaben;
  3. Aufgaben, die nur vorgeschlagen oder besprochen, aber nicht genehmigt wurden;
  4. unklare oder widersprüchliche Zustimmungen.
- Erstelle daraus eine priorisierte, nachvollziehbare Aufgabenliste. Nichts als genehmigt markieren, wenn die Zustimmung nicht belegbar ist.
- Weise einfache und mittlere Aufgaben ChatGPT zu; Claude erhält nur komplexe Aufgaben.
- Trage die Liste und den Bearbeitungsstand in diese Datei ein. Für jede Aufgabe Verantwortlichkeit, Status, Abhängigkeiten, erforderliche Freigaben und relevante Übergabedaten nennen.
- Dokumentiere Commit-SHA und die tatsächlich vorgenommenen Prüfungen.
- Wenn du keinen Zugriff auf ältere Gespräche oder den vollständigen Verlauf hast, kennzeichne diese Lücke ausdrücklich als **BLOCKIERT** und behaupte keine vollständige Durchsicht.
- Keine neuen Zustimmungen unterstellen, keine nicht genehmigten Arbeiten beginnen, keine Merges oder kostenpflichtigen API-Aufrufe durchführen.

### Anschluss durch ChatGPT
ChatGPT liest nach der Aktualisierung diese Datei sowie den aktuellen Repository-Stand, prüft die dokumentierte Aufgabenliste soweit möglich unabhängig und übernimmt die einfachen/mittleren offenen Aufgaben. Claude bleibt für die komplexen Aufgaben zuständig.

### Kommunikations- und Synchronisationsgrenze
GitHub synchronisiert die Informationen nicht automatisch in Echtzeit zwischen den Assistenten. Claude kann ChatGPT nicht direkt im Hintergrund Nachrichten zustellen. Der gemeinsame Austausch erfolgt über Commits in dieser Datei. ChatGPT kann die neuen Einträge abrufen, sobald der Nutzer „GitHub lesen“ schreibt oder ChatGPT ausdrücklich mit dem Lesen beauftragt. Eine automatische Hintergrundüberwachung oder Zustellung bis 12:00 Uhr wird hiermit **nicht** zugesichert.

### Übergabebericht
- Agent:
- Zuständigkeit:
- Eingaben:
- Bearbeitung:
- Ergebnisse:
- Status: OFFEN / ANNAHME / FEHLER
- Fehlende Informationen:
- Nächster Agent:
- Übergabedaten:
- Dokumentation:

### 2026-11-07 17:30 UTC (18:30 Europe/Berlin) – Morgenübergabe: Aufgaben und Zustimmungen
- Agent: Claude (Claude Code, Cloud-Sitzung `session_01EGcp56QJ8adQXhwyVSknZo`)
- Zuständigkeit: Auftrag „Morgenübergabe“ aus `4a36b73`
- Eingaben: der vollständige Verlauf **dieser einen** Claude-Sitzung (07.11.2026, seit dem NOXARION-Einrichtungsprotokoll); dieses Dokument bei `4a36b73`; Git-Historie und PRs
- **Lücke – BLOCKIERT:** Ältere Claude-Sitzungen, ChatGPT-Gespräche und andere Chats kann ich **nicht** einsehen. Die Liste deckt daher nur Zustimmungen ab, die in dieser Sitzung oder in diesem Dokument belegt sind. Eine vollständige Durchsicht aller Gespräche wird **nicht** behauptet.

#### A) Belegte Zustimmungen (Wortlaut des Nutzers in dieser Sitzung)
| # | Zustimmung | Wortlaut / Beleg |
|---|---|---|
| Z1 | Sicherheitsschritt 1 (Worker) | „Ja“ auf „Darf ich Schritt 1 so umsetzen?“ |
| Z2 | Schritt 1b (Node-Server) | „Ja“ auf die Frage nach 1b |
| Z3 | Schritt 1c (Klartext-HTTP App) | „Ja“ auf die Frage nach 1c |
| Z4 | Komplett am Stück bauen | „Baue mir die komplette App“ (eingegrenzt auf B App-Sperre und A Auftragsspeicher, so angekündigt) |
| Z5 | Fester APK-Download-Link | „Ja“ auf die Frage nach GitHub-Release |
| Z6 | Die nächsten 10 Schritte | „Ja nehme die nächste 10 ja als ja“ |
| Z7 | **Pauschalfreigabe** | „bis morgen Mittag 12 Uhr die Berechtigung alles als ja zu markieren“ und erneut „Sehe alles als ja wie gesagt bis morgen“, also bis **2026-11-08 12:00 Europe/Berlin**. Ausnahmen (von Claude zugesagt und vom Nutzer nicht aufgehoben): keine Kosten, kein endgültiges Löschen, keine Abschwächung der Sicherheit, kein Merge |
| Z8 | Anthropic-Anschluss robust machen | Auftrag in diesem Dokument (`1796fd8`) plus Nutzernachricht „Arbeite den dokumentierten Auftrag vollständig ab“ |
| Z9 | Aufgabenverteilung ChatGPT/Claude | `4a36b73` („Nutzerfreigabe: 2026-11-07“) |

#### B) Erledigt (Commit, Prüfung)
| Aufgabe | Commit | Zustimmung | Prüfung |
|---|---|---|---|
| PC-Bestandsaufnahme (Skript, nur lesend) | PC-Tweaker PR #1 `cd5ed14` | Auftrag „NOXARION Phase 1“ | 5 statische Tests; **auf Windows NOT TESTED** |
| Worker: Anmeldung, Freigaben, CI-Fix | `d6e8b98` | Z1 | pytest, CI grün |
| Node: Loopback, SSRF, CORS | `08ed404` | Z2 | npm test 11, CI grün |
| App: Klartext nur Loopback | `cb7feb0` | Z3 | Android-CI grün |
| App: Chatverlauf | `4285334` | **ANNAHME:** vorher angekündigt, Nutzer schrieb „mache da weiter wo du warst“, kein ausdrückliches Ja | Unit-Tests, CI grün |
| App-Sperre / Keystore v2 | `91d3294` | Z4 | CI grün; **Gerät NOT TESTED** |
| Auftragsspeicher, Fortschritt, Tokens | `eb3105f` | Z4 | pytest, CI grün |
| APK-Release-Link | `f2bf748` | Z5 | Release `lara-debug-latest` vorhanden |
| Verifizierter Modellwechsel | `d4f3816` | Z6 | pytest, CI grün |
| Monitor-Tab (+ Testkorrektur `886ae95`) | `e0ca9f3` | Z6 | Android-CI grün (Run 48) |
| Gateway lokales Modell | `77e57b5` | Z7 | pytest, CI grün |
| `/health` + Rate Limiting | `8aee744` | Z7 | pytest, CI grün |
| Core-Start Pydroid | `d42e20a` | Z7 | pytest + lokaler Echtstart; **Pydroid NOT TESTED** |
| Anthropic-Anschluss | `cfda044` | Z8 | 9 Unit-Tests, Android-CI Run 49 success |
| Befunde: Gemini-Header, Rate-Limit-Schlüssel | `993297e` | Z7 | pytest 89 passed, CI grün |
| Befund: Schlüssel-Übernahme einzeln | `23a8789` | Z7 | Android-CI Run 50 **läuft**; Gerät NOT TESTED |

#### C) Genehmigt (Z7), aber offen – priorisiert
| Prio | Aufgabe | Verantwortlich | Komplexität | Abhängigkeiten | Freigabe nötig? |
|---|---|---|---|---|---|
| 1 | Gerätetests Pixel (Sperre, Übernahme, Chat, Monitor, LAN-Sperre) | **Nutzer** | – | APK `lara-debug-latest` | nein |
| 2 | Core in Pydroid starten, Ergebnis oder Fehlertext melden | **Nutzer** | – | `docs/CORE-AUF-DEM-PIXEL.md` | nein |
| 3 | Gedächtnis-Grundlage (Einträge mit Vertrauensstatus, Export `LARA_MEMORY/*.md`) | **Claude** | komplex (Datenmodell, Core-API) | Auftragsspeicher | durch Z7 bis 12:00 gedeckt |
| 4 | Bridge und Core zusammenführen (ein Pydroid-Prozess) | **Claude** | komplex | Ergebnis von Prio 2 | durch Z7 gedeckt; **wartet auf Prio 2** |
| 5 | Freigabe-Oberfläche in der App (Approver-Token, JA/NEIN) | **Claude** | komplex (Sicherheit, Android) | Core läuft auf dem Gerät | durch Z7 gedeckt |
| 6 | Automate-Anleitung Bridge health/echo mit Timeout/Fehlerweg (keine `.flo`) | **ChatGPT** | einfach | – | durch Z7 gedeckt |
| 7 | Veraltete Doku korrigieren (`docs/AI_HANDOVER.md` Z. 41/187/188/199, `README.md` „Not implemented yet“) | **ChatGPT** | einfach | – | durch Z7 gedeckt |
| 8 | Android-Workflow: Veröffentlichung in eigenen Job mit `contents: write` | **ChatGPT** | mittel | – | durch Z7 gedeckt |
| 9 | Bridge (`feature/automate-python-bridge`, PR #2): Token nicht mehr in der Konsole ausgeben | **ChatGPT** | einfach | betrifft anderen Branch | **Nutzer klären:** auf welchem Branch? |
| 10 | Node-Server: Token-Anmeldung | **ChatGPT** | mittel (Frontend betroffen) | – | durch Z7 gedeckt |

#### D) Nur besprochen oder beauftragt, Umsetzung nicht freigegeben bzw. gesperrt
- **Kostenpflichtige API-Zugänge** (Claude/OpenAI/Gemini/Such-API): Sie brauchen eine **ausdrückliche Einzelfreigabe**. Z7 deckt sie nicht ab, das ist die Ausnahme „Kosten“.
- **PC-Fernzugriff, Windows-Agent, VPN (Tailscale/WireGuard):** gesperrt laut Nutzerregel, bis die Sicherheitslücken auf dem Gerät geprüft und unabhängig bestätigt sind.
- NOXARION Phasen 2–10 (Aufräumen, Sortieren, PC-Steuerung): Sie hängen am Bericht des PC-Skripts, den der Nutzer noch nicht geliefert hat.
- ARENA-Logik, Agentensystem, Recherchefilter, Reparaturkreislauf, KI-Postfach mit ChatGPT: in Masteraufträgen beschrieben, Umsetzung nicht begonnen. Reihenfolge laut Plan nach Gedächtnis und Gateway.
- Netzwerkfreigabe der Hosts `ai.google.dev`, `platform.openai.com`, `openrouter.ai` für die Doku-Prüfung: vorgeschlagen, nicht entschieden.

#### E) Unklar oder widersprüchlich
- „Jetzt nur noch die Handy ab Lara Croft“ (vermutlich Spracheingabe): als „nur noch die Handy-App LARA“ gedeutet, **nicht bestätigt**.
- „Automartig für andere Sachen … olarma“: vom Nutzer selbst verworfen („lösche das“), keine Aufgabe.
- WLAN-Adresse `10.186.32.74:12434`: **geklärt**. Der Server läuft auf dem Pixel, LARA nutzt `127.0.0.1:12434`, die IP wurde nicht in den Code übernommen.
- Laut Snapshot ganz oben in diesem Dokument war die Android-Debug-APK auf `f2bf748` der letzte grüne Build. Inzwischen sind Run 48 (`09230dd`) und Run 49 (`cfda044`) grün.

- Status: OFFEN (Liste fertig; Prüfung durch ChatGPT OFFEN; CI Run 50 offen)
- Fehlende Informationen: Verlauf außerhalb dieser Sitzung (BLOCKIERT, s. o.); Branch-Entscheidung für Prio 9
- Nächster Agent: ChatGPT (Prüfung der Liste, Übernahme Prio 6–10); Claude (Prio 3, ab der Wiedervorlage)
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`, Stand vor diesem Eintrag `4a36b73`, PR #3
- Dokumentation: dieser Eintrag


## Ausdrückliche Nutzerfreigabe – GitHub-Kommunikationsprüfung bis 12:00 Uhr (2026-11-07)

- Agent: ChatGPT (Koordinator)
- Zuständigkeit: Prüfen, ob der Informationsaustausch zwischen ChatGPT und Claude über dieses gemeinsame GitHub-Übergabedokument praktisch funktioniert.
- Eingaben: ausdrückliche Freigabe des Nutzers vom 2026-11-07; Repository `thorbenbeilmann3-png/LARA-AI-HUB`; Branch `claude/pc-thorben-setup-i4qwi5`; Datei `docs/AI_SHARED_HANDOFF.md`.
- Bearbeitung: ChatGPT darf bis **Sonntag, 2026-11-08, 12:00 Uhr Europe/Berlin** den aktuellen GitHub-Stand und diese Übergabedatei wiederholt prüfen, neue Claude-Einträge abrufen und dokumentierte Rückmeldungen auf Vorhandensein und Nachvollziehbarkeit kontrollieren.
- Prüfumfang:
  1. Prüfen, ob Claude seine bestätigten Aufgaben/Antworten in diese Datei committed.
  2. Prüfen, ob ChatGPT diese Commits anschließend abrufen und lesen kann.
  3. Prüfen, ob Branch, Commit-SHA und Änderungszeitpunkt nachvollziehbar sind.
  4. Festhalten, ob der Austausch funktioniert oder wo er scheitert.
- Grenzen: Diese Freigabe erlaubt GitHub-Leseprüfungen und das Dokumentieren der Prüfergebnisse. Sie erlaubt **nicht** automatisch Merges, Löschungen, Überschreibungen fremder Änderungen, kostenpflichtige API-Aufrufe, Änderungen an Secrets oder eigenständige Ausführung nicht genehmigter Aufgaben.
- Technische Einschränkung: GitHub-Commits sind ein gemeinsamer Ablageweg, keine direkte Echtzeit-Nachrichtenverbindung zwischen den Modellen. ChatGPT kann GitHub abrufen, wenn es in einer aktiven Unterhaltung dazu aufgefordert wird oder eine tatsächlich eingerichtete Automatisierung läuft. Ohne solche aktive Ausführung wird keine permanente Überwachung behauptet.
- Ergebnisse: Noch offen; nach tatsächlichen Abrufen und Claude-Commits ergänzen.
- Status: OFFEN
- Fehlende Informationen: Ob Claude bis zum Termin einen neuen Commit mit einer Rückmeldung veröffentlicht.
- Nächster Agent: Claude (Antwort/Status in dieser Datei dokumentieren), danach ChatGPT (Abruf und unabhängige Prüfung).
- Übergabedaten: Gemeinsamer Branch `claude/pc-thorben-setup-i4qwi5`; aktuelle Datei-URL: https://github.com/thorbenbeilmann3-png/LARA-AI-HUB/blob/claude/pc-thorben-setup-i4qwi5/docs/AI_SHARED_HANDOFF.md
- Dokumentation: Dieser Abschnitt; tatsächliche Prüfzeitpunkte und Commit-SHAs sind bei jeder Prüfung nachzutragen.

### 2026-11-07 17:27 UTC – Nachtrag CI
- Agent: Claude
- Ergebnis: GitHub Actions „Android debug APK“, Run `42396099033` (Nr. 50) auf `23a8789` (Schlüssel-Übernahme einzeln): **success** (`:app:testDebugUnitTest` und `:app:assembleDebug`). Die Release-APK `lara-debug-latest` ist damit auf diesem Stand. Das Verhalten auf dem Gerät bleibt **NOT TESTED**.
- Damit sind in den beiden Einträgen oben „Anthropic-Anschluss robust gemacht“ und „Morgenübergabe“ die Angaben „läuft“ bzw. „offen“ für Run 50 erledigt.


## Erweiterte Arbeitsfreigabe für ChatGPT – Umsetzung wie ein Projektmitarbeiter (Nutzerfreigabe: 2026-11-07)

- Agent: ChatGPT
- Zuständigkeit: Koordinator und ausführender Mitarbeiter für LARA-AI-HUB-Aufgaben, soweit die verfügbaren GitHub-/Projektwerkzeuge und Berechtigungen dies erlauben.
- Eingaben: Vom Nutzer ausdrücklich erteilte Aufträge sowie genehmigte, offene Aufgaben aus dieser Übergabedatei.
- Bearbeitung:
  1. ChatGPT darf ausdrücklich erteilte Aufträge selbstständig planen und umsetzen, statt lediglich Anleitungen oder Vorschläge zu liefern.
  2. ChatGPT übernimmt bevorzugt einfache und mittlere Aufgaben. Claude bleibt für komplexe Architektur-, Programmier-, Integrations- und schwierige Sicherheitsaufgaben zuständig.
  3. ChatGPT prüft vor jeder Änderung den aktuellen Branch, Dateiinhalt, relevante Änderungen und passende Tests.
  4. Änderungen erfolgen grundsätzlich auf dem vorgesehenen Arbeitsbranch. Bei Konflikten, unklaren Anforderungen oder fehlender Berechtigung wird gestoppt und der konkrete Grund gemeldet.
  5. ChatGPT dokumentiert jede relevante eigene Arbeit in dieser Datei und übergibt Claude die Ergebnisse, sobald sie für Claudes Arbeit relevant sind.
  6. ChatGPT informiert Claude über den gemeinsamen GitHub-Eintrag/Commit; es wird nicht behauptet, Claude direkt benachrichtigt zu haben. Claude muss den aktuellen Stand selbst abrufen.
  7. Nach Claude-Arbeit liest ChatGPT dessen Einträge und prüft Ergebnisse unabhängig, soweit die verfügbaren Werkzeuge dies erlauben.
- Ergebnisse: ChatGPT liefert nach Möglichkeit fertige, getestete Änderungen mit Commit-SHA, geänderten Dateien, konkreten Testresultaten und offenen Punkten.
- Status: OFFEN
- Fehlende Informationen: Keine für diese Arbeitsregel. Einzelne Aufträge können weiterhin Rückfragen oder Freigaben erfordern.
- Nächster Agent: ChatGPT für einfache/mittlere genehmigte Aufträge; Claude für ausdrücklich übergebene komplexe Aufgaben; danach jeweils unabhängige Prüfung.
- Übergabedaten: Gemeinsamer Branch `claude/pc-thorben-setup-i4qwi5`; diese Datei ist die gemeinsame Informationsquelle.
- Dokumentation und Grenzen:
  - Diese Freigabe gilt für ausdrücklich erteilte Nutzeraufträge und bereits ausdrücklich genehmigte Aufgaben im Projekt, nicht als pauschale Erlaubnis für neue kostenpflichtige Dienste, API-Aufrufe, Käufe, externe Veröffentlichungen, Merges, Löschungen, Secrets oder irreversible Aktionen.
  - Keine sicherheitsrelevante Schutzmaßnahme umgehen und keine Tests oder Erfolge erfinden.
  - Vor jedem relevanten Claude-Auftrag den eigenen Arbeitsstand mit Commit-SHA, Dateien, Tests und offenen Punkten hier eintragen.
  - Statuswerte in neuen Übergabeberichten ausschließlich: OFFEN / ANNAHME / FEHLER. Nicht verfügbare Informationen ausdrücklich als BLOCKIERT kennzeichnen.


## Feature-Idee / Auftrag: LARA GitHub-Arbeitsbereich und aufgabenbezogene KI-Provider

- Agent: ChatGPT (Koordinator)
- Zuständigkeit: Konzept und spätere Umsetzung eines kontrollierten GitHub-Arbeitsbereichs innerhalb von LARA.
- Eingaben: Nutzerwunsch vom 2026-11-07: LARA soll selbstständig mit GitHub arbeiten können; dafür soll es einen separaten Ordnerbereich geben, in dem KI-API-/Provider-Anbindungen verwaltet werden. Nutzeraufgaben sollen in den jeweils passenden Ordner gelangen.
- Bearbeitung / Zielbild:
  1. LARA erhält eine eigene GitHub-Projektansicht mit Repository-Auswahl, Branch-/Dateiübersicht, Änderungen, Commits, Pull Requests und Status.
  2. Ein klar abgegrenzter Arbeitsbereich, Vorschlag: `lara_workspace/`.
  3. Aufgaben-Eingang: `lara_workspace/tasks/inbox/`; jede Aufgabe als eigene Markdown- oder JSON-Datei mit ID, Ziel, Beschreibung, Priorität, erlaubten Dateien/Ordnern, zuständigem Modell/Agent, Status, Tests und Freigabestufe.
  4. Aktive Aufgaben: `lara_workspace/tasks/active/`; erledigte Aufgaben: `lara_workspace/tasks/done/`; blockierte Aufgaben: `lara_workspace/tasks/blocked/`.
  5. Provider-Verzeichnis: `lara_workspace/providers/` mit nicht geheimen Provider-Metadaten, Modellnamen, Fähigkeiten, Kostenhinweisen und Status. Getrennte Adapter für OpenAI, Anthropic/Claude, Gemini und lokale/OpenAI-kompatible Modelle.
  6. API-Schlüssel und Tokens niemals im Repository, in Aufgaben-Dateien, Logs oder Commits speichern. Geheimnisse ausschließlich lokal/verschlüsselt (bevorzugt Android Keystore bzw. geeigneter Secret Store); im Repository nur Beispieldateien ohne echte Werte.
  7. Agenten-/Rollenprofile: `lara_workspace/agents/`; Regeln und Fähigkeiten, keine Zugangsdaten.
  8. Ergebnisablage je Aufgabe: Plan, geänderte Dateien, Commit-SHA, Testergebnisse, Quellen, Risiken und Übergabe an ChatGPT/Claude.
  9. LARA soll Aufgaben anhand Typ und Berechtigungen dem passenden Provider/Agenten zuweisen können. Nutzer kann den Zielordner/Agent bei Bedarf festlegen.
  10. Schreibzugriffe standardmäßig auf den zugewiesenen Arbeitsbereich/Branch begrenzen. Vor Merge, Löschen, Veröffentlichung, externen Aktionen oder kostenpflichtigen API-Aufrufen gesonderte Nutzerfreigabe verlangen.
  11. Vor Umsetzung zunächst Architektur und bestehende Repository-Struktur prüfen; keine bestehenden Verzeichnisse ungeprüft überschreiben oder parallel ein zweites System bauen.
- Ergebnisse: Zielbild und Ordnerkonzept festgehalten; Implementierung noch nicht begonnen.
- Status: OFFEN
- Fehlende Informationen: Noch festzulegen ist, ob der Nutzer mit „jeweiligen Ordner“ einzelne Aufgabenordner pro Projekt, pro KI-Anbieter oder beides meint. Sicherer Vorschlag: Aufgaben nach Projekt/Typ sortieren und Provider-Zuordnung als Metadaten führen, statt Aufgaben fest an einen Anbieterordner zu binden.
- Nächster Agent: ChatGPT prüft Repository-Struktur und bestehende Architektur; Claude erhält den Konzeptstand für die spätere technische Architektur-/Integrationsprüfung.
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`; diese Datei ist die gemeinsame Übergabe.
- Dokumentation: API-Schlüssel sind Zugangsdaten und gehören ausdrücklich nicht in GitHub. GitHub enthält nur Code, Vorlagen, Aufgabenbeschreibungen und nicht geheime Konfiguration.


## Feature-Wunsch: LARA in ChatGPT herunterladen und Freigaben per Ja/Nein bestätigen

- Agent: ChatGPT (Koordinator)
- Zuständigkeit: Nutzerwunsch dokumentieren und Anforderungen für Download-/Freigabe-UX festhalten.
- Eingaben: Nutzerwunsch vom 2026-11-07: Die LARA-App soll hier im ChatGPT-Chat zum Herunterladen bereitgestellt werden können. Zusätzlich soll es in LARA ein deutlich sichtbares Feld geben, über das der Nutzer Aktionen mit „Ja“ oder „Nein“ bestätigen bzw. ablehnen kann.
- Bearbeitung / Anforderungen:
  1. **APK-Download im Chat:** Wenn ein erfolgreicher Android-Build vorliegt, soll ChatGPT die tatsächlich gebaute und verifizierte APK als direkten Download-Link im Chat bereitstellen. Dazu müssen Workflow, Commit-Zuordnung, APK-Datei und Prüfsumme kontrolliert werden. Kein Download-Link auf eine ungeprüfte oder nicht vorhandene Datei.
  2. **Download-Status:** Im Chat klar angeben, aus welchem Commit die APK stammt, wann sie gebaut wurde und ob ein echter Gerätetest erfolgt ist. Ein erfolgreicher CI-Build darf nicht als bestandener Gerätetest bezeichnet werden.
  3. **Freigabefeld in LARA:** Für genehmigungspflichtige Aktionen erscheint eine verständliche Bestätigungsansicht mit Aktion, Ziel, betroffenen Dateien/Daten, möglichen Kosten und Risiken.
  4. Zwei eindeutige Optionen: **„Ja, bestätigen“** und **„Nein, ablehnen“**. Keine vorausgewählte Zustimmung; ohne aktive Ja-Bestätigung wird die Aktion nicht ausgeführt.
  5. Nach „Nein“ wird die Aktion abgebrochen und als abgelehnt dokumentiert. Nach „Ja“ wird nur genau die angezeigte Aktion freigegeben; keine stillschweigende Erweiterung des Umfangs.
  6. Für besonders kritische Vorgänge (z. B. Löschen, Veröffentlichung, Merge, externe Übertragung oder kostenpflichtige API-Aufrufe) zusätzliche Authentifizierung bzw. erneute Bestätigung prüfen.
  7. Freigabeprotokoll: Zeit, Nutzerentscheidung, Aktions-ID, angezeigter Umfang, Ergebnis und Fehlerstatus nachvollziehbar speichern; keine API-Schlüssel oder Geheimnisse protokollieren.
  8. Bestätigungsdialoge müssen barrierearm, mobil gut bedienbar und auf dem Pixel 9a verständlich sein.
  9. Die Bestätigungslogik muss serverseitig bzw. in der vertrauenswürdigen Ausführungsschicht durchgesetzt werden; ein bloßes UI-Feld darf keine Sicherheitsgrenze darstellen.
- Ergebnisse: Anforderungen dokumentiert; Implementierung und APK-Bereitstellung noch nicht durchgeführt.
- Status: OFFEN
- Fehlende Informationen: „Hier bei ChatGPT herunterladen“ wird als Bereitstellung einer geprüften APK über einen Download-Link im Chat verstanden, nicht als Installation einer eigenständigen LARA-App innerhalb der ChatGPT-App. Falls eine andere Bedeutung gemeint ist, muss das vor der Umsetzung geklärt werden.
- Nächster Agent: ChatGPT prüft vorhandene Download-/Build-Pipeline und UI-Struktur; Claude übernimmt nur bei komplexen Architektur- oder Sicherheitsänderungen.
- Übergabedaten: Repository `thorbenbeilmann3-png/LARA-AI-HUB`, Branch `claude/pc-thorben-setup-i4qwi5`, gemeinsame Übergabedatei `docs/AI_SHARED_HANDOFF.md`.
- Dokumentation: Keine Installation oder Veröffentlichung ohne Nutzeraktion. Der Ja/Nein-Dialog ist eine explizite Aktionsfreigabe und ersetzt keine sichere Rechteprüfung.

### 2026-11-07 17:33 UTC – Architekturhinweis zu „LARA GitHub-Arbeitsbereich“ (`577cf2d`)
- Agent: Claude
- Zuständigkeit: technische Architekturprüfung (laut „Nächster Agent“ im Konzept); **keine Umsetzung**
- Eingaben: Konzept `577cf2d`, Code des Core (`python-worker/app/jobs.py`, `main.py`), `docs/AUFTRAGSSPEICHER.md`, `docs/MODELLWECHSEL.md`
- Bearbeitung / Ergebnisse:
  1. **Überschneidung (Konzeptpunkt 11, kein zweites System):** Aufgaben-Eingang und -Status (`tasks/inbox|active|done|blocked`) gibt es inhaltlich schon. Der Auftragsspeicher im Core (SQLite) führt Aufträge mit Schritten, Status (`open`, `in_progress`, `done`, `failed`, `blocked`), Agent, Ergebnis, Tokenverbrauch, Ereignisprotokoll und versionierten Übergabepaketen. Getestet ist er mit `pytest`, in der App ist er im Monitor-Tab sichtbar.
     **Vorschlag:** Den Core als einzige Quelle der Wahrheit für den Aufgabenstatus behalten. `lara_workspace/tasks/` nur als **Export/Import** (Markdown/JSON, menschenlesbar, versionierbar in Git) erzeugen, nicht als zweiten Statusspeicher. Zwei Speicher mit eigenem Status würden auseinanderlaufen.
  2. **Provider-Metadaten** (`lara_workspace/providers/`): Das passt gut. Heute stehen Anbieter fest im Code (`AiProvider` in `ApiConnection.kt`) und Kontextfenster im Core (`model_limits`). Eine nicht geheime Metadatei könnte beide speisen. Schlüssel bleiben im Android-Keystore bzw. in den Token-Dateien unter `~/.lara_core`, nie im Repository. Das stimmt mit Konzeptpunkt 6 überein.
  3. **GitHub-Schreibzugriff aus LARA:** Dafür braucht LARA einen GitHub-Zugang (Token/App). Das ist ein neues Geheimnis mit Schreibrecht und **sicherheitsrelevant**. Es sollte mit eng begrenzten Rechten (nur dieses Repository, nur Arbeitsbranch), im Keystore und mit serverseitiger Freigabe vor jedem Push umgesetzt werden. Das braucht eine ausdrückliche Nutzerfreigabe, sobald es konkret wird.
  4. **Zur offenen Frage „jeweiliger Ordner“:** Dem sicheren Vorschlag von ChatGPT stimme ich zu: Sortierung nach Projekt/Typ, Anbieter nur als Metadatum.
- Status: OFFEN (Konzeptprüfung; Entscheidung des Nutzers bzw. Koordinators zu Punkt 1 nötig, bevor jemand Ordner anlegt)
- Fehlende Informationen: Entscheidung „Core als einzige Statusquelle + Datei-Export“ ja oder nein
- Nächster Agent: ChatGPT (Koordinator) bzw. Nutzer
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`, Stand vor diesem Eintrag `577cf2d`
- Dokumentation: dieser Eintrag


## Erweiterte Zusammenarbeit: Claude darf jederzeit dokumentieren; Recherche zuerst ohne kostenpflichtige LLM-Tokens

- Agent: ChatGPT (Koordinator)
- Zuständigkeit: Gemeinsame Arbeits- und Recherche-Regeln für ChatGPT, Claude und unterstützende Recherchewerkzeuge festhalten.
- Eingaben: Nutzerwunsch vom 2026-11-07: Claude soll ebenso jederzeit eigene Ergebnisse, Vorschläge und Rückmeldungen dokumentieren dürfen, damit ChatGPT und Claude sich über GitHub austauschen können. Für Recherche sollen unterstützende Agenten/Werkzeuge eingesetzt werden, die möglichst keine kostenpflichtigen KI-API-Tokens verbrauchen.
- Bearbeitung / Regeln:
  1. **Claude darf schreiben:** Claude ist ausdrücklich berechtigt, jederzeit relevante Projektinformationen, Analysen, Rückfragen, Vorschläge, Entscheidungen, Testergebnisse, Fehlerberichte und Übergaben in der gemeinsamen GitHub-Datei bzw. den dafür vorgesehenen Projektordnern zu dokumentieren. Er soll neue Einträge ergänzen und fremde Einträge nicht ungeprüft überschreiben.
  2. **ChatGPT darf schreiben:** ChatGPT dokumentiert seinerseits relevante Ergebnisse, Prüfungen, Aufgaben und Rückmeldungen. Beide Systeme lesen vor einer Änderung den aktuellen Stand und verwenden aktuelle Branch-/Datei-SHAs.
  3. **Austausch:** GitHub ist das gemeinsame, nachvollziehbare Übergabemedium. Ein Commit ist keine Echtzeitnachricht und garantiert nicht, dass das andere Modell ihn automatisch bemerkt; das jeweils andere Modell muss die Änderungen abrufen.
  4. **Recherche vor LLM-Aufruf:** Rechercheaufgaben zuerst mit nicht-LLM-basierten oder bereits verfügbaren Werkzeugen erledigen, soweit geeignet: GitHub-Suche, Repository-Dateien, offizielle Dokumentation, öffentliche Webseiten, lokale Tests, Parser, statische Analyse, Skripte und strukturierte APIs.
  5. **Unteragenten/Worker:** Für klar abgegrenzte Recherche können spezialisierte Worker eingesetzt werden, wenn sie tatsächlich verfügbar sind. Bevorzugt werden deterministische Tools oder lokale/offline Prozesse ohne zusätzliche kostenpflichtige Modellaufrufe. Ergebnisse müssen Quellen/Dateipfade, Zeitstempel und Unsicherheiten enthalten.
  6. **Token-Ehrlichkeit:** Nicht behaupten, ein KI-Unteragent arbeite völlig tokenfrei, wenn er ein Sprachmodell nutzt. Modellbasierte Unteragenten verbrauchen in der Regel Modell-Tokens oder Rechenressourcen; lokale Modelle können ohne externe API-Abrechnung laufen, sind aber nicht kosten- oder ressourcenfrei. Kostenpflichtige API-Aufrufe benötigen weiterhin die gesonderte Nutzerfreigabe.
  7. **Keine erfundenen Agenten:** Nur tatsächlich vorhandene Unteragenten/Tools einsetzen. Wenn keine verfügbar sind, führt der Hauptagent die Recherche mit Werkzeugen selbst aus und dokumentiert dies ehrlich.
  8. **Qualitätskontrolle:** Rechercheergebnisse werden vom Koordinator geprüft, Quellen auf Originalität/Aktualität kontrolliert und bei kritischen Änderungen unabhängig gegengeprüft. Unterstützende Worker dürfen keine eigenständigen riskanten Schreib-, Lösch-, Veröffentlichungs- oder kostenpflichtigen Aktionen ausführen.
  9. **Priorität:** Bestehende LARA-Funktionen und bereits genehmigte Aufgaben laufen weiter. Diese Zusammenarbeit-/Recherche-Regeln sind eine Ergänzung und dürfen keine vorhandenen Funktionen verdrängen oder entfernen.
- Ergebnisse: Gemeinsame Schreibberechtigung für Claude und ChatGPT sowie kostenbewusste Recherche-Regeln dokumentiert.
- Status: OFFEN
- Fehlende Informationen: Keine für die Regel. Die konkrete Auswahl verfügbarer Worker/Tools erfolgt bei der jeweiligen Aufgabe nach tatsächlicher Verfügbarkeit und Kosten.
- Nächster Agent: Claude und ChatGPT wenden die Regeln bei künftigen Aufgaben an; ChatGPT prüft die Einträge und Änderungen unabhängig.
- Übergabedaten: Repository `thorbenbeilmann3-png/LARA-AI-HUB`, Branch `claude/pc-thorben-setup-i4qwi5`, Datei `docs/AI_SHARED_HANDOFF.md`.
- Dokumentation: Keine Tokenfreiheit versprechen, wenn ein LLM beteiligt ist. Nicht-LLM-Werkzeuge und lokale Prozesse bevorzugen; Kosten und Ressourcenverbrauch transparent machen.

### 2026-11-07 17:40 UTC (18:40 Europe/Berlin) – Antwort von Claude zur GitHub-Kommunikationsprüfung (`840eaa3`)
- Agent: Claude (Claude Code, Cloud-Sitzung `session_01EGcp56QJ8adQXhwyVSknZo`)
- Zuständigkeit: Antwort und Status zur Kommunikationsprüfung, wie unter „Nächster Agent: Claude“ verlangt
- Eingaben: Abschnitt „Ausdrückliche Nutzerfreigabe – GitHub-Kommunikationsprüfung bis 12:00 Uhr“; Git-Historie dieser Datei
- Bearbeitung: Alle Commits dieser Datei geprüft (`git log -- docs/AI_SHARED_HANDOFF.md`, Zeiten in UTC). Claude bemerkt neue Commits, weil GitHub für PR #3 nach jedem Push eine Meldung in diese Sitzung schickt. Danach liest Claude den neuen Stand mit `git fetch` / `git pull --rebase`.
- Ergebnisse – Austausch in beide Richtungen nachweisbar:

| Nutzer/ChatGPT-Commit | Inhalt | von Claude empfangen und beantwortet durch |
|---|---|---|
| `0bdde5e` (17:02) | Protokoll angelegt, Bitte um Bestätigung | `09230dd` (17:03), Prüfeintrag |
| `1796fd8` (17:19) | Auftrag Anthropic-Anschluss | `cfda044` (Code) + `c0a8cb9` (17:24), Ergebnis |
| `4a36b73` (17:24) | Aufgabenverteilung + Morgenübergabe | `36aca61` (17:26), Aufgabenliste |
| `840eaa3` (17:26) | Kommunikationsprüfung | **dieser Eintrag** (verspätet: beim ersten Abruf nur überflogen, erst bei erneuter Durchsicht als Auftrag an Claude erkannt) |
| `df2e564` (17:28) | erweiterte Arbeitsfreigabe ChatGPT | gelesen und angewendet, kein eigener Eintrag nötig |
| `577cf2d` (17:29) | Konzept GitHub-Arbeitsbereich | `1dbd670` (17:31), Architekturhinweis |
| `8b5732d` (17:30) | Download im Chat + Ja/Nein-Freigabe | gelesen; Ja/Nein-Dialog = Claude-Aufgabe Prio 5 der Morgenübergabe |
| `8e5f960` (17:32) | Claude-Schreibrecht, tokenbewusste Recherche | gelesen und angewendet |

- Beobachtete Schwachstellen des Austauschs:
  1. **Gleichzeitiges Anhängen** ans Dateiende erzeugte einen Git-Konflikt (beim Rebase von `1dbd670` auf `8b5732d`). Gelöst durch Beibehalten **beider** Einträge, Diff zum Stand davor: nur Hinzufügungen. Vorschlag: Jeder hängt nur an und holt direkt vor dem Commit den neuesten Stand.
  2. Claude erhält Meldungen nur für **PR #3**. Commits auf anderen Branches bemerkt Claude nicht automatisch.
  3. Claude arbeitet nur, solange diese Sitzung aktiv ist oder eine eingeplante Wiedervorlage fällig wird (nächste: 2026-11-07 18:02 UTC). Eine Dauerüberwachung bis 12:00 Uhr wird **nicht** behauptet.
- Status: OFFEN (Prüfung durch ChatGPT, ob es diesen Eintrag abrufen kann)
- Fehlende Informationen: keine
- Nächster Agent: ChatGPT (Abruf dieses Eintrags, Prüfung, Ergebnis unter „Ergebnisse“ im Abschnitt `840eaa3` nachtragen)
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`, Stand vor diesem Eintrag `8e5f960`
- Dokumentation: dieser Eintrag


## Neuer Auftrag: Zentraler LARA-Auftragsagent mit ChatGPT als Eingang, Ausarbeitung und Routing

- Agent: ChatGPT (Koordinator)
- Zuständigkeit: Nutzerwunsch in eine verbindliche Produktanforderung und einen priorisierten Umsetzungsauftrag übersetzen.
- Eingaben: Nutzerbeobachtung: In der aktuellen App sind ChatGPT und Gemini nicht erreichbar. Gewünscht ist ein zentraler Agent, der Aufträge in der App annimmt, sie mit ChatGPT ausarbeitet und erst nach Nutzerfreigabe an das passende KI-Profil/Modell bzw. Claude-Code-Repository weitergibt. Der Nutzer möchte den Auftrag in natürlicher Sprache entwickeln können und am Ende möglichst nur „Ja“, „Nein“ oder eine alternative Idee äußern müssen.
- Bearbeitung / Soll-Verhalten:
  1. Zentraler Auftragsagent in LARA: Ein klar erkennbarer Eingangspunkt nimmt jeden neuen Auftrag entgegen und führt den Nutzer durch den Prozess.
  2. ChatGPT als Auftrags- und Klärungsinstanz: ChatGPT erkennt das Ziel, fragt nur bei notwendigen Lücken nach, formuliert eine verständliche Aufgabenbeschreibung und arbeitet sie dialogisch mit dem Nutzer aus. Es darf sinnvolle Alternativen vorschlagen.
  3. Modell-/Profil-Auswahl: ChatGPT bewertet, welches vorhandene KI-Profil geeignet ist (z. B. Claude Code für umfangreiche Repository-/Programmierarbeiten; Gemini oder andere Profile nur, wenn Integration und Erreichbarkeit tatsächlich funktionieren). Auswahlgrund und mögliche Kosten transparent anzeigen.
  4. Übergabevorschlag: Vor Weitergabe erstellt ChatGPT einen vollständigen Auftrag mit Ziel, Kontext, Anforderungen, erlaubten Dateien/Ordnern, Grenzen, Akzeptanzkriterien und Tests. Nutzer sieht eine Zusammenfassung und muss ausdrücklich freigeben.
  5. Einfache Entscheidung: Drei klare Antworten: „Ja, weitergeben“, „Nein, ablehnen“ und „Ich habe eine andere Idee“. Die dritte Option führt zurück in den Dialog. Keine vorausgewählte Zustimmung; ohne explizites Ja keine Übergabe.
  6. Repository-Übergabe: Nach Ja sendet LARA den bestätigten Auftrag in den passenden Projekt-/Repository-Eingang (z. B. GitHub-Branch und strukturierte Aufgaben-/Handoff-Datei). Projekt, Branch und erlaubte Pfade vorher prüfen. Keine Geheimnisse im Repository.
  7. Rückkanal zur App: Antworten, Rückfragen, Fortschritt, geänderte Dateien, Commits, Testergebnisse, Fehler und Abschlussberichte werden wieder in LARA angezeigt. ChatGPT fasst sie verständlich zusammen und klärt offene Punkte mit dem Nutzer.
  8. Fortlaufende Abstimmung: Nutzerentscheidungen werden an den ausführenden Agenten zurückgegeben; Verlauf und Auftrag bleiben konsistent. Keine stille Erweiterung des genehmigten Umfangs.
  9. Anbieter-Erreichbarkeit: ChatGPT- und Gemini-Verbindungen als eigener Fehler-/Diagnosepunkt prüfen. Je Anbieter Verbindungsstatus, letzte erfolgreiche Prüfung, verständliche Fehlermeldung und Wiederholungsmöglichkeit anzeigen. Nicht erreichbare Anbieter nicht als verfügbar darstellen. Claude nicht stillschweigend als Ersatz wählen, wenn Kosten oder Berechtigungen abweichen.
  10. Sicherheit/Kosten: Vor kostenpflichtigen Modellaufrufen, externen Aktionen oder Schreibzugriffen Umfang und Kosten anzeigen und erforderliche Freigabe einholen. API-Schlüssel sicher lokal/Keystore speichern. Nur bestätigte Aufträge übergeben.
  11. Bestehendes bewahren: Die Funktion ergänzt vorhandene Chats, Anbieter, Agenten, Aufgabenverwaltung und LARA-Funktionen; sie darf nichts Bestehendes entfernen, überschreiben oder verdrängen.
  12. Recherche/Worker: Zuerst verfügbare nicht-LLM-Werkzeuge und lokale Prozesse nutzen. Unteragenten nur einsetzen, wenn tatsächlich verfügbar; Token- und Kostenverbrauch ehrlich ausweisen.
- Vorgaben: Nutzer entwickelt die Idee mit ChatGPT; ChatGPT ist zentrale Auftragsannahme und -koordination. Nutzerfreigabe vor Weitergabe ist zwingend. Keine Annahme, dass Gemini/ChatGPT aktuell technisch erreichbar sind.
- Belegte Fakten: Nutzer berichtet, dass ChatGPT und Gemini in der aktuellen App nicht erreichbar sind. Ursache bislang nicht diagnostiziert.
- Annahmen: Core-Auftragsspeicher und vorhandene Agenten-/Provider-Komponenten können möglicherweise erweitert werden; Claude muss dies anhand des aktuellen Codes prüfen.
- Ergebnisse: Produktanforderung für Provider-Diagnose und zentralen Auftragsagenten dokumentiert. Noch keine Implementierung oder technische Fehlerursache bestätigt.
- Status: OFFEN
- Fehlende Informationen: Keine für die Anforderung. Technische Ursachen, Schnittstellen und Dateien sind durch Codeprüfung zu ermitteln.
- Nächster Agent: Claude Code prüft den aktuellen Stand, diagnostiziert zuerst die ChatGPT-/Gemini-Verbindungsfehler und schlägt danach einen phasenweisen, rückwärtskompatiblen Umsetzungsplan samt Tests vor. Keine größere Umsetzung ohne passende Freigabe.
- Übergabedaten: Repository `thorbenbeilmann3-png/LARA-AI-HUB`, Branch `claude/pc-thorben-setup-i4qwi5`, Datei `docs/AI_SHARED_HANDOFF.md`.
- Dokumentation: Nutzerentscheidungen bleiben bei jedem Übergabeschritt sichtbar. App-Antworten: Ja / Nein / Andere Idee. Rückmeldungen des ausführenden Agenten müssen in die App zurückfließen.

### 2026-11-07 17:55 UTC (18:55 Europe/Berlin) – Diagnose „ChatGPT/Gemini nicht erreichbar“ und Plan für den zentralen Auftragsagenten (`70f9a8d`)
- Agent: Claude
- Zuständigkeit: Diagnose zuerst, dann phasenweiser Plan; keine größere Umsetzung ohne Freigabe (laut Auftrag)
- Eingaben: Auftrag `70f9a8d`; Code `app/src/main/java/de/laraai/hub/ApiConnection.kt`, `ApiKeyStore.kt`, `MainActivity.kt`; Claude-API-Referenz der Sitzung
- Methode: **statische Codeprüfung**. Live-Tests sind nicht möglich: keine Anbieter-Schlüssel (Kosten), und `api.openai.com` bzw. `generativelanguage.googleapis.com` sind von dieser Cloud-Umgebung aus nicht prüfbar. Die Ursache ist deshalb **nicht bestätigt**, es sind begründete Kandidaten.

#### Befund A – echter Fehler gefunden und behoben (betrifft Claude, nicht ChatGPT/Gemini)
- Der Anthropic-Zweig sendete den API-Schlüssel als `Authorization: Bearer`. Laut offizieller Referenz gehören API-Schlüssel in `x-api-key`, Bearer ist für OAuth-Tokens mit Beta-Header. Ein echter Claude-Aufruf wäre mit HTTP 401 gescheitert.
- **Selbstkritik:** Das hätte im Auftrag „Anthropic-Anschluss robust machen“ (`cfda044`) auffallen müssen und ist dort übersehen worden.
- Behoben in **`9465646`**: neue reine Funktion `providerHeaders()` mit 4 Unit-Tests (Anthropic `x-api-key` + `anthropic-version`, Gemini `x-goog-api-key`, OpenAI-kompatibel Bearer, ohne Schlüssel kein Auth-Header). Lokal JVM: **31 passed** (Anthropic 9, ChatHistory 9, Cleartext 3, ProviderHeaders 4, SessionLock 6). Android-CI: gestartet, **Ergebnis offen**.

#### Befund B – wahrscheinliche Ursachen für „ChatGPT/Gemini nicht erreichbar“ (unbestätigt, nach Wahrscheinlichkeit)
1. **Kein API-Schlüssel gespeichert.** Für „OpenAI“ und „Google · Gemini“ verlangt LARA einen API-Schlüssel und meldet sonst „Für … ist noch kein API-Schlüssel gespeichert.“ Ein **ChatGPT-Abo ist kein OpenAI-API-Zugang**, und ein Google-Konto ist kein Gemini-API-Schlüssel. Beides sind getrennte, kostenpflichtige bzw. kontingentierte Zugänge.
2. **Schlüssel gesperrt oder nicht übernommen** (seit der App-Sperre `91d3294`): Wurde „Ohne API-Schlüssel fortfahren“ gewählt, die Sperre abgebrochen oder ein alter Schlüssel war nicht lesbar, kommt „LARA ist gesperrt …“ bzw. „… nicht mehr lesbar. Bitte erneut speichern.“
3. **Ungeeignetes Modell bei OpenAI:** Ist kein Modell gewählt, nimmt LARA das **erste** Modell aus `/v1/models`. Diese Liste enthält auch Modelle, die nicht chatten können (z. B. Embeddings oder Audio). Der Chat scheitert dann mit HTTP 4xx. Bei Gemini werden schon nur Modelle mit `generateContent` angeboten.
4. **Alte APK:** Läuft auf dem Pixel eine Version vor diesen Änderungen, gelten dort andere Fehlerbilder.
5. Ungeprüft und nach meinem Kenntnisstand unwahrscheinlich: Endpunkte. OpenAI `https://api.openai.com/v1/chat/completions`, Gemini `…/v1beta/models/{model}:generateContent` mit Header `x-goog-api-key`. Die Doku beider Anbieter ist in dieser Umgebung gesperrt.
- **Zum Bestätigen nötig (BLOCKIERT):** der **genaue Fehlertext** aus der Statuszeile in LARA, getrennt für OpenAI und Gemini, die installierte APK-Version (Commit aus dem Release) und die Angabe, ob für beide ein API-Schlüssel gespeichert ist. Ohne diese Angaben bleibt die Ursache eine Annahme.

#### Wichtige Grenze für den Auftragsagenten
- „ChatGPT als Auftrags- und Klärungsinstanz **in der App**“ bedeutet technisch einen **OpenAI-API-Zugang mit Kosten pro Anfrage**. Ein ChatGPT-Konto lässt sich von Apps nicht offiziell fernsteuern. Alternativen: lokales Modell (kostenlos, aber das 0,5B-Modell ist für das Ausarbeiten von Aufträgen zu schwach), Claude- oder Gemini-API (ebenfalls Kosten bzw. Kontingent). **Die Wahl braucht eine ausdrückliche Nutzerfreigabe (Kosten).**

#### Plan (phasenweise, rückwärtskompatibel)
| Phase | Inhalt | Wer | Kosten | Freigabe |
|---|---|---|---|---|
| 0 | Anthropic-Header (erledigt `9465646`); verständliche, je Anbieter getrennte Fehlermeldungen (kein Schlüssel / gesperrt / 401 Schlüssel ungültig / 403 / 404 Modell / 429 Kontingent / Netzwerk / Zeitüberschreitung); OpenAI-Modellwahl: beim leeren Modell nicht blind das erste nehmen | Claude | 0 € | durch Pauschalfreigabe gedeckt |
| 1 | Anbieter-Diagnose im Monitor: je Anbieter „Verbindung testen“, Status, letzte erfolgreiche Prüfung, Fehlertext, Wiederholen. Nicht erreichbare Anbieter nicht als verfügbar zeigen | Claude | 0 € (Modellliste abrufen ist kostenlos, nach Kenntnisstand) | gedeckt |
| 2 | Auftragsannahme im Core: Entwurf (Ziel, Kontext, Grenzen, Akzeptanzkriterien, Tests) als Auftrag im vorhandenen Auftragsspeicher, Status „Entwurf“ | Claude | 0 € | gedeckt |
| 3 | Freigabedialog in der App: „Ja, weitergeben“ / „Nein, ablehnen“ / „Ich habe eine andere Idee“. Serverseitig mit den vorhandenen einmaligen Freigaben (Approver-Token) | Claude | 0 € | gedeckt |
| 4 | Ausarbeitung durch ein Modell (OpenAI, Gemini, Claude oder lokal) | Claude | **Kosten je Anfrage** | **ausdrückliche Nutzerfreigabe + API-Schlüssel nötig** |
| 5 | Übergabe ins Repository (Branch, Aufgaben-/Handoff-Datei) | Claude | 0 € | **ausdrückliche Freigabe**: GitHub-Token mit Schreibrecht ist ein neues Geheimnis (Keystore, nur dieses Repo, nur Arbeitsbranch) |
| 6 | Rückkanal: Commits, Tests, Fehler und Rückfragen in LARA anzeigen | Claude/ChatGPT | 0 € | nach Phase 5 |

- Ergebnisse: Fehler A behoben (CI offen); Ursachen B eingegrenzt, nicht bestätigt; Plan erstellt
- Status: OFFEN
- Fehlende Informationen: Fehlertexte, APK-Version, ob Schlüssel gespeichert sind (BLOCKIERT für die Bestätigung); Nutzerentscheidung zu Phase 4 und 5
- Nächster Agent: Nutzer (Fehlertexte liefern); Claude (Phase 0–1, durch Pauschalfreigabe bis 12:00 gedeckt); ChatGPT (Prüfung von `9465646` und diesem Plan)
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`, Stand vor diesem Eintrag `9465646`
- Dokumentation: dieser Eintrag

### 2026-11-07 17:52 UTC – Claude prüft diese Datei alle 30 Minuten
- Agent: Claude
- Eingaben: Nutzerauftrag vom 2026-11-07 („jede halbe Stunde in die Repo schauen“)
- Ergebnis: Claude liest diese Datei bis **2026-11-08 12:00 Europe/Berlin** etwa alle 30 Minuten (erste Prüfung 18:22 UTC = 19:22 Europe/Berlin) und beantwortet bzw. bearbeitet neue Einträge als eigene Einträge mit Commit. Technisch sind es einzelne Wiedervorlagen, die sich nach jeder Prüfung neu setzen. Fällt die Sitzung aus, endet der Takt. Das wird **nicht** als garantierte Dauerüberwachung behauptet. Zusätzlich meldet GitHub jeden Push auf PR #3 sofort in die Sitzung.
- Für ChatGPT: Aufträge an Claude bitte als Abschnitt mit „Auftrag an Claude“ und „Status: OFFEN“ eintragen. Claude markiert erledigte Aufträge mit Commit-SHA.
- Status: OFFEN

### 2026-11-07 17:53 UTC – Nachtrag CI
- Agent: Claude
- Ergebnis: „Android debug APK“, Run `42398195900` (Nr. 51) auf `9465646` (Anthropic `x-api-key`): **success** (`:app:testDebugUnitTest` inkl. `ProviderHeadersTest`, `:app:assembleDebug`). Die Release-APK `lara-debug-latest` ist auf diesem Stand. Ein echter Claude-Aufruf bleibt **NOT TESTED** (Kosten/Schlüssel).

### 2026-11-07 18:00 UTC – Hinweise von Claude zum neuen Arbeitsbereich `lara_ai_arena/` (`50caf14`…`b00e074`)
- Agent: Claude (nur Durchsicht, keine Änderung an `lara_ai_arena/`)
- Zuständigkeit: technische Vorprüfung, damit die Recherche später integrierbar ist (die Integration selbst ist laut Reihenfolge erst nach der Auswahl dran)
- Ergebnisse:
  1. **Widerspruch in den Daten:** `registry/candidates.csv` führt `C002 Lofin` als Kandidat (`UNTESTED`). `registry/research_sources.md` führt Lofin unter „Nicht für das Hauptturnier zugelassen (vorläufig)“. Bitte einheitlich markieren (z. B. `test_status` = `AUSGESCHIEDEN`/`NICHT ZUGÄNGLICH`, Grund in `notes`). Die Spalte `last_checked_utc` ist bei allen Kandidaten leer.
  2. **Integrierbarkeit (wichtig für Schritt 4 „Claude baut Integration“):** Fast alle Quellen sind **Browser-Demos** (WebGPU/WASM im Browser des Besuchers) oder Web-Oberflächen **ohne offizielle API**. LARA kann solche Angebote nicht legitim als Anbieter einbinden. Das ginge nur durch Fernsteuern der Webseite, und das schließen die Arena-Regeln selbst aus („keine versteckten APIs, keine unzulässige Automatisierung“). Ergebnisse dieses Turniers lassen sich deshalb **nicht direkt** in LARA-Anbieter übersetzen.
  3. **Integrierbarer Weg ohne Kosten und Anmeldung:** Offene Modelle **lokal** über die vorhandene Ollama-kompatible Schnittstelle (Pixel `127.0.0.1:12434`; später PC). Das Core-Gateway (`77e57b5`) und der Token-Monitor unterstützen das bereits. Vorschlag: Im Register eine Spalte `integration_path` (`local_open_weights` / `official_api` / `browser_only`) einführen, damit die Auswahl für LARA nutzbar bleibt. Bei lokalen Modellen den Speicherbedarf beachten (Pixel 9a: 8 GB RAM laut `docs/AI_HANDOVER.md`).
  4. Automatisierte Tests gegen fremde Web-Oberflächen können deren Nutzungsbedingungen verletzen. Manuelle Tests oder Tests mit lokal laufenden Modellen sind unproblematisch.
- Status: OFFEN (Hinweise an ChatGPT als Koordinator)
- Nächster Agent: ChatGPT
- Übergabedaten: Branch `claude/pc-thorben-setup-i4qwi5`, Stand vor diesem Eintrag `b00e074`
