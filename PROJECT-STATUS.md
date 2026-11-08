# LARA AI HUB – Projektstatus

**Zentrale Informationsquelle für Thorben, ChatGPT und Claude.**
Zuletzt aktualisiert: 08.11.2026 von Claude. Vor jeder Arbeit zusätzlich den aktuellen GitHub-Stand prüfen, dieser Text kann veraltet sein.

## Kurzfassung

- `main` enthält nur die kleine Starter-App und Dokumentation. Die eigentliche App mit Anbietern, Keystore, App-Sperre und Automate liegt auf Branches, die **nicht** in `main` sind (PR #1 und #3).
- Für Alpine/Ollama gibt es einen fertig gebauten Verbindungstest. **Auf dem Pixel ist noch nichts nachgewiesen.**
- Nächster Schritt: Thorben führt den Terminal-Befehl aus Abschnitt 10 aus und kopiert die Ausgabe in den Claude-Chat.

## 1. Regeln der Zusammenarbeit

Vom Nutzer am 08.11.2026 festgelegt. Sie gelten für ChatGPT und Claude. Ergänzend gelten `docs/USER-WORKFLOW.md` und die Regeln in `docs/AI_SHARED_HANDOFF.md` (Branch `claude/pc-thorben-setup-i4qwi5`).

1. Zuerst die Projektdokumentation und den aktuellen GitHub-Stand lesen.
2. Jeden neuen Wunsch, jede Anforderung und jede wichtige Entscheidung in GitHub speichern.
3. Nach jeder erledigten Aufgabe den Projektstatus aktualisieren.
4. Änderungen, betroffene Dateien, Commits, Testergebnisse und offene Fehler dokumentieren.
5. Klar schreiben, was tatsächlich funktioniert und was nicht getestet wurde.
6. `PROJECT-STATUS.md` ist die zentrale Informationsquelle.
7. `CHANGELOG.md` ist die Änderungsübersicht.
8. Arbeit von ChatGPT, Claude oder anderen niemals ungeprüft überschreiben.
9. Nach jeder Aufgabe einen verständlichen Bericht für das andere Modell hinterlassen (Abschnitte 8 und 9).
10. Vor der nächsten Arbeit die Berichte des anderen Modells lesen.
11. Thorben nach jeder neuen Erkenntnis verständlich zusammenfassen, was neu ist.
12. In kleinen, überprüfbaren Schritten arbeiten.

Außerdem:

- Größere Änderungen an der App nur nach Abstimmung mit Thorben.
- Bei der Einrichtung immer nur **einen** Schritt geben und auf die Rückmeldung warten.
- Keine Merges, Löschungen, Secrets oder kostenpflichtigen API-Aufrufe ohne ausdrückliche Freigabe.
- Keine Tokens oder Schlüssel in GitHub, Chat, Screenshots oder Berichten.
- ChatGPT und Claude sind nicht direkt verbunden. Sie tauschen sich nur über GitHub-Commits aus, es gibt keine automatische Benachrichtigung oder Überwachung.

**So wird diese Datei gepflegt:** Direkt vor dem Schreiben den neuesten Stand holen, nur die betroffenen Abschnitte ändern, nichts Fremdes löschen. Bei einem Konflikt beide Fassungen erhalten und Thorben fragen.

## 2. Wo liegt was

| Branch | Stand | Inhalt | Pull Request |
|---|---|---|---|
| `main` | `ec254c7` | Starter-App (eine Kotlin-Datei), Auftrag `docs/ALPINE-OLLAMA-MCP-INTEGRATION.md`, `docs/USER-WORKFLOW.md` | – |
| `feature/android-secure-api-key-storage` | `3ffc400` | Anbieter, Keystore, Automate-Start, OpenClaw, Python-Grundgerüst | #1 nach `main`, Entwurf |
| `claude/pc-thorben-setup-i4qwi5` | `512a001` | Baut auf #1 auf: App-Sperre, neues Design, Monitor, Core, Gedächtnis, Übergabeprotokoll | #3 nach #1, Entwurf |
| `feature/automate-python-bridge` | `6cd8c40` | Bridge Automate ↔ Python auf Port 8765 | #2 nach `main`, Entwurf |
| `safety/pre-arena-20261105` | `1f5b1f1` | Sicherungsstand | – |
| `claude/alpine-verbindungstest` | `4c4a194` | Verbindungstest, baut auf #3 auf, Details in `docs/ALPINE-OLLAMA-STATUS.md` | noch keiner |
| `claude/projekt-dokumentation` | dieser Stand | Diese Datei, `CHANGELOG.md`, `tools/alpine-info.sh` | #4 nach `main` |

**Downloads:** Die öffentliche Vorabversion `lara-debug-latest` stammt von `0418dfa`; der App-Code ist seitdem bis `512a001` unverändert. Testbuilds des Verbindungstests liegen als private Release-Entwürfe vor.

**Zwei verschiedene „Ollama“ auf dem Pixel, nicht verwechseln:**

| | Android-App (älterer Stand) | Alpine |
|---|---|---|
| Adresse | `127.0.0.1:12434` | `127.0.0.1:11434` |
| Modell | `qwen2.5-0.5b-instruct-q4_k_m` | `qwen2.5:0.5b` |
| Besonderheit | Antwortete auf `/api/chat` mit 404 (`docs/AI_HANDOVER.md`), also kein Standard-Ollama | Laut Auftrag normales Ollama, muss in einem offenen Terminal laufen |

## 3. Was tatsächlich funktioniert

**Durch Build und Tests belegt (kein Nachweis für das Gerät):**

| Prüfung | Ergebnis | Beleg |
|---|---|---|
| Android-Build `main` | PASS | Run 42522973404, `ec254c7` |
| Android-Build und Unit-Tests PR-Stand | PASS | Run 42428044594, `0418dfa` |
| Android-Build und Unit-Tests Verbindungstest | PASS, 80 Tests, 0 Fehler | Run 42523661265, `4c4a194` |
| Python-Core (`python-worker`) | PASS | Run 42486968526, `512a001`; lokal hier nicht ausgeführt (Pakete fehlen) |
| Node-Server | PASS, 11 Tests | Run 42486968635, `512a001`; am 08.11. lokal wiederholt: 11 bestanden |
| Python-Tests auf dem Stand von PR #1 | **FAIL** | Run 42485906156, `3ffc400`, siehe F12 |
| Unabhängige Prüfung Verbindungstest | erste Runde: Fehler gefunden, behoben; zweite Runde läuft | siehe Abschnitt 8 |

**Nutzerangaben, von den Assistenten nicht selbst geprüft:**

- Alpine 3.24.4 (`aarch64`) mit Python 3.14, Node.js 24, Git, curl und Ollama läuft in einer Android-App, Modell `qwen2.5:0.5b`.
- Innerhalb von Alpine antwortet `GET http://127.0.0.1:11434/api/version` mit HTTP 200.
- Der MCP-Endpunkt `http://127.0.0.1:8765/mcp` antwortete ohne Token mit 401.
- In der neuen LARA-App ist Text schwarz. Als Modell wird nur „Ollama – Qwen 2.5.0“ angezeigt.

**Auf dem Pixel 9a NICHT GETESTET:** Erreichbarkeit von Alpine/Ollama aus LARA, Ollama-Chat, MCP, Python, Terminal, Automate-Flows, App-Sperre und Keystore, Core in Pydroid. Die Tabelle mit allen 27 Prüfungen des Auftrags steht in `docs/ALPINE-OLLAMA-STATUS.md`. Davon ist nur Nr. 1 („App baut“) bestanden.

**Nicht implementiert:** MCP-Client, Python/Terminal über Alpine, Alpine-Verwaltungsseite, LARA für Windows, PC-Fernzugriff, ARENA-Logik.

**PC-Software (eigenes Repository `PC-Tweaker`):** Grundgerüst 0.1.0. Die Bestandsaufnahme `BESTANDSAUFNAHME.bat` liegt dort auf dem Branch `claude/pc-thorben-setup-i4qwi5` und ist laut Projektdoku vom 07.11. noch nie auf Windows gelaufen. Die Phasen 2–10 sind offen.

## 4. Wünsche und Anforderungen

| Datum | Wunsch | Stand |
|---|---|---|
| 08.11. | Alpine, Ollama, MCP, Python, echtes Terminal, Automate nach dem Auftrag | Phase A läuft |
| 08.11. | Automate: echte Flow-Ausführungen auf dem Pixel testen | Phase F, nur mit Thorben am Gerät möglich |
| 08.11. | Terminal in LARA, in das man tippt und LARA es selbst ausführt | Geplant in Phase E, siehe B2 |
| 08.11. | PC-Software, die sich von allein einstellt | Nicht fertig, zuerst Bestandsaufnahme |
| 08.11. | Ein cmd-Befehl, der alles installiert und verknüpft | Nur der erste Schritt möglich, danach `EINRICHTEN.bat` geplant. Verknüpfung PC–Handy ist gesperrt, bis die Sicherheitsprüfungen auf dem Gerät bestanden sind |
| 08.11. | Terminal-Text, dessen Ausgabe Claude alle Infos liefert | Erledigt, Abschnitt 10 |
| 08.11. | Textfarben prüfen (Text ist schwarz) | Ursache gefunden, siehe F1 |
| 08.11. | Prüfen, warum nur ein Modell sichtbar ist | Offen. Die Modellliste kommt vom jeweiligen Server, andere Anbieter brauchen einen API-Schlüssel (ANNAHME, am Gerät prüfen) |
| 08.11. | Ersteinrichtung Schritt für Schritt | Gilt ab sofort |
| 08.11. | ChatGPT und Claude arbeiten mit demselben Stand | Diese Datei |

## 5. Entscheidungen

| # | Entscheidung | Grund | Von |
|---|---|---|---|
| E1 | Reihenfolge: Verbindung → Ollama-Chat → Alpine-Verwaltung → Python/Terminal → Automate, MCP als eigenes Paket am Ende | Lesart von Abschnitt 11–13 des Auftrags, änderbar | Claude |
| E2 | Arbeit baut auf `claude/pc-thorben-setup-i4qwi5` auf, nicht auf `main` | Dort liegt die vorhandene App | Claude |
| E3 | Testbuilds haben den Paketnamen `de.laraai.hub.alpinetest` | CI signiert jeden Build anders; ein Update der echten App würde scheitern, Deinstallieren kostet Schlüssel und Chat | Claude |
| E4 | `lara-debug-latest` wird von Testbuilds nicht überschrieben | Keine ungeprüften Builds auf dem festen Link | Claude |
| E5 | Infos vom Handy kommen über einen Terminal-Text | Wunsch von Thorben | Thorben |
| E6 | Das Terminal-Skript wird über eine feste Commit-Adresse aufgerufen | Es läuft nur genau die geprüfte Fassung | Claude |

## 6. Offene Fehler und Widersprüche

### Fehler im Code

Zeilenangaben beziehen sich auf den Branch `claude/pc-thorben-setup-i4qwi5` @ `512a001`.

| # | Fehler | Ort | Stand |
|---|---|---|---|
| F1 | **Schwarzer Text auf dunklem Grund.** `LaraTheme` setzt keine Standard-Textfarbe, und der transparente `Scaffold` übernimmt dadurch Schwarz. Betrifft jeden Text ohne eigene Farbe, auch die Statuszeile unter Chat und Tools | `LaraTheme.kt`, `MainActivity.kt:405` | Ursache aus dem Code abgeleitet, am Gerät nicht geprüft. Nur die neue Testkarte ist korrigiert. App-weit eine kleine Änderung, wartet auf Freigabe |
| F2 | Ollama-Standardport 12434, Alpine läuft auf 11434 | `ApiConnection.kt:21` | Offen, Phase B |
| F3 | Kein `GET /api/version`; Chat läuft über die OpenAI-Schnittstelle statt `/api/chat` | `ApiConnection.kt:177` | Offen, Phase B |
| F4 | Bei eingefrorenem Server hängt die Prüfung rund vier Minuten, auch vor jeder Chat-Nachricht | `ApiConnection.kt:177` | Offen, Phase B |
| F5 | Fehler werden zu einem Text zusammengefasst, die Ursache geht verloren | `ApiConnection.kt:206` | Offen, Phase B |
| F6 | Port 8765 doppelt verplant: Python-Bridge (PR #2) und Alpine-MCP | PR #2, Auftrag | Offen, Phase D |
| F7 | Lokales Ollama hat keine Anmeldung; jede App mit INTERNET-Berechtigung kann es nutzen, wenn es erreichbar ist | Betrieb | Offen |
| F8 | Automate-Autostart feuert bei jedem Neuaufbau der Hauptansicht (Entsperren, Drehen) | `MainActivity.kt:397` | Offen, Phase F |
| F9 | Automate-Start nutzt `START_FLOW`; in den geprüften offiziellen Seiten nicht gefunden, nur in Nutzerbeiträgen | `MainActivity.kt:280` | Offen, Phase F |
| F10 | CI signiert jeden Build mit anderem Zertifikat (vier geprüfte Builds, vier Zertifikate) | Workflows | Offen. Ein fester Schlüssel als GitHub-Secret braucht Freigabe |
| F11 | App-Text sagt, OpenClaw dürfe HTTP im privaten LAN nutzen; tatsächlich ist es blockiert | `ApiConnection.kt:379`, `MainActivity.kt:746` | Offen |
| F12 | Python-Tests rot auf dem Stand von PR #1 (Exit-Code 2). Das Protokoll nennt einen in `d6e8b98` (PR #3) behobenen CI-Importfehler; ob das die Ursache ist, ist nicht bestätigt, das Log war hier nicht lesbar | Run 42485906156 | Offen |

### Dokumentation passt nicht zum Stand

Nur gemeldet, nicht geändert (Regel 8).

| Datei | Aussage | Tatsächlich |
|---|---|---|
| `README.md` (PR-Stand) | Anbieter, Schlüsselspeicher, Python-Worker „not implemented“ | Vorhanden |
| `docs/AI_HANDOVER.md` Abschnitte 3 und 11 | `0.0.0.0`, Adresse `10.179.130.230`, Klartext für alle Ziele, kein Chatverlauf | Geändert in `08ed404`, `cb7feb0`, `4285334` |
| `docs/AI_HANDOVER.md` Abschnitt 7 | Nur auf `feature/android-secure-api-key-storage` arbeiten, `main` nicht ändern | Aktive Arbeit liegt auf anderen Branches, Dokumentation wurde direkt auf `main` gelegt |
| `docs/ROADMAP.md` | Erfolgreicher Build „noch nicht bestätigt“ | CI ist grün |
| `docs/PYTHON-WORKER-SICHERHEIT.md` | Klartext in der Android-App noch offen | Behoben in `cb7feb0` |
| `docs/GATEWAY.md` | „Auf dem Pixel getestet“ und zugleich „NOT TESTED“ | Unklar, was getestet wurde |
| `docs/AUTOMATE-SETUP.md` | `127.0.0.1:12434` „erfolgreich getestet“; das Protokoll nennt denselben App-Test NOT TESTED | Vermutlich Browser-Test, nicht aus LARA |
| `docs/AI_SHARED_HANDOFF.md` | Claude prüft die Datei alle 30 Minuten | Galt nur bis 08.11., 12:00 Uhr. Derzeit läuft keine Überwachung |
| Auftrag `docs/ALPINE-OLLAMA-MCP-INTEGRATION.md` | „Bestehende“ Keystore- und Automate-Anbindung | Gibt es nur auf dem PR-Stand, nicht in `main` |

## 7. Nächste Schritte und Blockaden

1. **Thorben:** Terminal-Befehl aus Abschnitt 10 ausführen, Ausgabe in den Claude-Chat kopieren.
2. **Thorben:** Testversion „LARA Test“ installieren und Verbindung prüfen, sobald Claude sie freigibt.
3. **Claude:** Ergebnis auswerten, danach Ollama-Paket (Phase B) abstimmen.
4. **ChatGPT:** Abschnitt 8 prüfen und das Ergebnis in Abschnitt 9 eintragen.

| # | Blockade | Fehlt |
|---|---|---|
| B1 | Abschluss von Phase A | Ergebnis vom Pixel |
| B2 | Python und Terminal (Phase E) | Name der App, die Alpine bereitstellt, und deren Schnittstellen |
| B3 | Automate (Phase F) | Automate-Version, Klärung der offiziell unterstützten Start-Schnittstelle |
| B4 | MCP (Phase D) | Welche Software den Endpunkt auf Port 8765 bereitstellt |
| B5 | App-weite Textfarben (F1) | Freigabe von Thorben |
| B6 | Diese Dateien in `main` | Freigabe von Thorben (PR #4) |

## 8. Bericht von Claude an ChatGPT

**08.11.2026, Claude (Cloud-Sitzung, kein Zugriff auf Pixel oder PC)**

- **Auftrag:** Phase A aus dem Alpine-Auftrag, danach diese Dokumentation.
- **Geändert:** siehe `CHANGELOG.md`. Keine Änderung an `main` und an fremden Branches. Keine Merges, keine Löschungen.
- **Bitte prüfen:**
  1. Stimmen die Branch-Tabelle (Abschnitt 2) und die Run-Nummern (Abschnitt 3)?
  2. Commits `0373531`, `a709d88` und `4c4a194`: Sagt die Karte wirklich nur, was beobachtet wurde?
  3. Ist die Ursache von F1 richtig (`LaraTheme.kt`, `MainActivity.kt:405`)?
  4. Ist die Reihenfolge E1 so gemeint?
- **Unabhängige Prüfung:** Ein eigener Prüf-Agent fand in der ersten Runde neun Mängel, darunter falsche „erreichbar“-Meldungen und ein mögliches Speichern eingetippter Token. Behoben in `a709d88`. Die zweite Runde lief beim Schreiben noch; das Ergebnis trägt Claude hier nach.
- **Nicht getestet:** alles auf dem Pixel.

## 9. Bericht von ChatGPT an Claude

Noch kein Eintrag. Bitte hier eintragen: Datum, geprüfte Commits, Ergebnis, gefundene Fehler, offene Fragen.

## 10. Infos vom Handy holen

Wenn Claude oder ChatGPT etwas vom Handy wissen müssen, geben sie Thorben eine Zeile für das Alpine-Terminal. Die Ausgabe von `BEGINN` bis `ENDE` kommt in den Chat.

Aktuelle Fassung (nur lesend, fest auf diesen Commit gelegt):

```sh
curl -fsSL https://raw.githubusercontent.com/thorbenbeilmann3-png/LARA-AI-HUB/f3a1b0f9a8ec212623b4127004243432c8754c2b/tools/alpine-info.sh | sh
```

Das Skript `tools/alpine-info.sh` gibt keine Werte von Umgebungsvariablen außer `OLLAMA_HOST`, keine Programm-Argumente, keine Konfigurationsdateien und keine MCP-Antworten aus. Geprüft unter Linux gegen nachgebaute Server, auf Alpine noch nicht gelaufen. Bei Änderungen am Skript immer die neue Commit-Adresse angeben.
