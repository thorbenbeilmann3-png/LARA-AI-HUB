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
