# LARA AI HUB – Projektstatus

**Zentrale Informationsquelle für Thorben, ChatGPT und Claude.**
Zuletzt aktualisiert: 08.11.2026 von Claude. Vor jeder Arbeit zusätzlich den aktuellen GitHub-Stand prüfen, dieser Text kann veraltet sein.

## Kurzfassung

- `main` enthält nur die kleine Starter-App und Dokumentation. Die eigentliche App mit Anbietern, Keystore, App-Sperre und Automate liegt auf Branches, die **nicht** in `main` sind (PR #1 und #3).
- Der Verbindungstest läuft auf dem Pixel 9a (Android 17). Ein echter App-Prozess erreicht `127.0.0.1:12434` und bekommt von `127.0.0.1:8765/mcp` die Antwort 401.
- Die lokale KI auf Port 12434 beantwortet echte Chat-Anfragen, nachgewiesen im Alpine-Terminal auf dem Pixel. **Der Chat aus der LARA-App und das Ollama in Alpine (Port 11434) sind noch nicht getestet.**
- Nächster Schritt: Claude baut Textfarbe und Einrichtungsassistenten in die Testversion (Abschnitt 4, E8). Den Chat-Test aus der App übernimmt dort Etappe 4.

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

**Direkt in `main` (E7):** `PROJECT-STATUS.md`, `CHANGELOG.md` und `docs/LOKALE-KI-PRUEFUNG.md` dürfen ChatGPT und Claude ohne Pull Request in `main` aktualisieren. Code, Skripte und Workflows laufen weiter über Branches und Pull Requests.

## 2. Wo liegt was

| Branch | Stand | Inhalt | Pull Request |
|---|---|---|---|
| `main` | ab `abd6b38` | Starter-App (eine Kotlin-Datei), Auftrag `docs/ALPINE-OLLAMA-MCP-INTEGRATION.md`, `docs/USER-WORKFLOW.md`, seit #4 auch diese Datei, `CHANGELOG.md`, `docs/LOKALE-KI-PRUEFUNG.md` und `tools/alpine-info.sh` | – |
| `feature/android-secure-api-key-storage` | `3ffc400` | Anbieter, Keystore, Automate-Start, OpenClaw, Python-Grundgerüst | #1 nach `main`, Entwurf |
| `claude/pc-thorben-setup-i4qwi5` | `512a001` | Baut auf #1 auf: App-Sperre, neues Design, Monitor, Core, Gedächtnis, Übergabeprotokoll | #3 nach #1, Entwurf |
| `feature/automate-python-bridge` | `6cd8c40` | Bridge Automate ↔ Python auf Port 8765 | #2 nach `main`, Entwurf |
| `safety/pre-arena-20261105` | `1f5b1f1` | Sicherungsstand | – |
| `claude/alpine-verbindungstest` | `64fe56d` | Verbindungstest, baut auf #3 auf, Details in `docs/ALPINE-OLLAMA-STATUS.md` | noch keiner |
| `claude/projekt-dokumentation` | `9049d5a` | In `main` übernommen, nicht weiterführen (Branch bleibt bestehen) | #4, gemergt als `abd6b38` |

**Downloads:** Die öffentliche Vorabversion `lara-debug-latest` stammt von `0418dfa`; der App-Code ist seitdem bis `512a001` unverändert. Testbuilds des Verbindungstests liegen als private Release-Entwürfe vor. Aktuell: `LARA-Test-64fe56d.apk` (privater Entwurf `lara-alpine-test-64fe56d`, Commit `64fe56d`, CI-Lauf #6 grün, 120 Unit-Tests ohne Fehler), Paket `de.laraai.hub.alpinetest`, Größe 18.403.466 Bytes, erstellt 09.11.2026 um 01:27 MEZ, SHA-256 `4214bfe34b2cc343000360dfb77425c007d342a47825f0f50f8746177d7e00f3`, am Gerät getestet: NEIN. Gegenüber `LARA-Test-4c4a194.apk` neu: F18-Fallback auf `/v1/models`, Einrichtungsassistent mit 10 Etappen, app-weite Textfarbe (Details im CHANGELOG vom 09.11.2026). Achtung: Die Signatur ist eine andere als bei `LARA-Test-4c4a194.apk`. Android erlaubt ein Update nur mit gleicher Signatur, deshalb muss die alte Testversion vor der Installation deinstalliert werden. Das löscht lokale Daten (Schlüssel, Chat). Vorgänger: `LARA-Test-4c4a194.apk`, Commit `4c4a194`.

**Zwei verschiedene „Ollama“ auf dem Pixel, nicht verwechseln:**

| | Android-App (älterer Stand) | Alpine |
|---|---|---|
| Adresse | `127.0.0.1:12434` | `127.0.0.1:11434` |
| Modell | `qwen2.5-0.5b-instruct-q4_k_m` | `qwen2.5:0.5b` |
| Besonderheit | Kein Standard-Ollama: `/api/chat` antwortete früher mit 404 (`docs/AI_HANDOVER.md`), `/api/version` meldet 0.1.32, `/api/tags` ist leer, das Modell steht unter `/v1/models` | Laut Auftrag normales Ollama, muss in einem offenen Terminal laufen |

## 3. Was tatsächlich funktioniert

**Echte Ergebnisse vom Pixel 9a (Android 17, API 37), Bericht von Thorben vom 08.11., 22:27 Uhr, Testversion Build `4c4a194`, UID 10494:**

| Prüfung | Ergebnis |
|---|---|
| Testversion startet auf dem Pixel | PASS |
| App-Prozess erreicht `127.0.0.1:12434` | PASS: `/api/version` HTTP 200 in 69 ms, Version 0.1.32; `/api/tags` HTTP 200 in 59 ms, 0 Modelle |
| App-Prozess fragt `127.0.0.1:8765/mcp` ohne Token | HTTP 401 in 36 ms. Ein Dienst antwortet. Dass es der Alpine-MCP-Dienst ist, ist nicht belegt |
| Ollama in Alpine (Port 11434) aus dem App-Prozess | **NICHT GETESTET**, der Bericht prüfte Port 12434 |

**Durch Build und Tests belegt (kein Nachweis für das Gerät):**

| Prüfung | Ergebnis | Beleg |
|---|---|---|
| Android-Build `main` | PASS | Run 42522973404, `ec254c7` |
| Android-Build und Unit-Tests PR-Stand | PASS | Run 42428044594, `0418dfa` |
| Android-Build und Unit-Tests Verbindungstest | PASS, 80 Tests, 0 Fehler | Run 42523661265, `4c4a194` |
| Python-Core (`python-worker`) | PASS | Run 42486968526, `512a001`; lokal hier nicht ausgeführt (Pakete fehlen) |
| Node-Server | PASS, 11 Tests | Run 42486968635, `512a001`; am 08.11. lokal wiederholt: 11 bestanden |
| Python-Tests auf dem Stand von PR #1 | **FAIL** | Run 42485906156, `3ffc400`, siehe F12 |
| Lokale KI auf Port 12434: echter Chat im Alpine-Terminal | PASS unter `192.168.178.21` (2,10 s) und `127.0.0.1` (1,05 s), echte Antwort, Tokenzahlen vorhanden. Von Thorben auf dem Pixel ausgeführt | `docs/LOKALE-KI-PRUEFUNG.md` |
| Lokale KI auf Port 12434: Chat aus der LARA-App | **NICHT GETESTET** | – |
| Unabhängige Prüfung Verbindungstest | Zweite Runde bestanden (Urteil „ship“). Nicht geprüft: Android-Laufzeit und Pixel | Abschnitt 8 |

**Nutzerangaben, von den Assistenten nicht selbst geprüft:**

- Alpine 3.24.4 (`aarch64`) mit Python 3.14, Node.js 24, Git, curl und Ollama läuft in einer Android-App, Modell `qwen2.5:0.5b`.
- Innerhalb von Alpine antwortet `GET http://127.0.0.1:11434/api/version` mit HTTP 200.
- Der MCP-Endpunkt `http://127.0.0.1:8765/mcp` antwortete ohne Token mit 401.
- Unter `http://192.168.178.21:12434/v1` läuft eine lokale KI mit dem Modell `qwen2.5-0.5b-instruct-q4_k_m`, `/v1/models` liefert die Modellliste.
- In der neuen LARA-App ist Text schwarz. Als Modell wird nur „Ollama – Qwen 2.5.0“ angezeigt.

**Auf dem Pixel 9a NICHT GETESTET:** Ollama in Alpine aus LARA, Chat aus LARA, MCP-Anmeldung, Python, Terminal, Automate-Flows, App-Sperre und Keystore, Core in Pydroid. Die Tabelle mit allen 27 Prüfungen des Auftrags steht in `docs/ALPINE-OLLAMA-STATUS.md` (Branch `claude/alpine-verbindungstest`). Bestanden sind Nr. 1 („App baut“) und Nr. 2 („App startet“).

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
| 08.11. | Textfarben prüfen (Text ist schwarz) | Ursache gefunden (F1). Korrektur für die ganze App freigegeben („Ja, korrigieren“), zuerst in der Testversion |
| 08.11. | Prüfen, warum nur ein Modell sichtbar ist | Offen. Die Modellliste kommt vom jeweiligen Server, andere Anbieter brauchen einen API-Schlüssel (ANNAHME, am Gerät prüfen) |
| 08.11. | Ersteinrichtung Schritt für Schritt | Gilt ab sofort |
| 08.11. | ChatGPT und Claude arbeiten mit demselben Stand | Diese Datei |
| 08.11. | Einrichtungsassistent in LARA: 10 Etappen, Thorben drückt nur auf „Weiter“ und die App erledigt den Schritt | Zugestimmt. In Arbeit auf `claude/alpine-verbindungstest`, zuerst nur in der Testversion, Etappen siehe E8. Automatisch möglich sind nur Prüfungen innerhalb von LARA; was in anderen Apps geschieht, braucht Thorben oder eine noch zu klärende Schnittstelle |
| 08.11. | „Ja mache das die App ( sich mit dir und mit Chatgbt sich verbindet wenn nicht mache es über automate“ | Lesart: LARA soll Claude und ChatGPT selbst anbinden. Geht nur über deren API, siehe E9. Automate ist kein KI-Zugang und kann das nicht ersetzen, es kann nur Abläufe auf dem Handy starten. Eine Verbindung zu diesem Claude-Gespräch ist technisch nicht möglich |
| 08.11. | „Ja mache das Autoamte und so weiter in Chat gibt und du clode direkt in der App anbinden geht das er selber alles automartich runter laden tut und ein Bericht schickt was ihr noch so braucht für die ein Bindung“ (Lesart: „in Chat gibt“ = „und ChatGPT“) | Bericht: wird gebaut, Versand über das Teilen-Menü von Android. Automatisches Herunterladen nur teilweise: Apps muss Thorben im Store bestätigen, Installationen in Alpine erst nach dem MCP-Paket |
| 08.11. | Verbindung zur lokalen KI unter `192.168.178.21:12434` prüfen, ohne Code zu ändern | Code geprüft, Server im Alpine-Terminal bestanden. Test aus LARA steht aus, siehe `docs/LOKALE-KI-PRUEFUNG.md` |

## 5. Entscheidungen

| # | Entscheidung | Grund | Von |
|---|---|---|---|
| E1 | Reihenfolge: Verbindung → Ollama-Chat → Alpine-Verwaltung → Python/Terminal → Automate, MCP als eigenes Paket am Ende | Lesart von Abschnitt 11–13 des Auftrags, änderbar | Claude |
| E2 | Arbeit baut auf `claude/pc-thorben-setup-i4qwi5` auf, nicht auf `main` | Dort liegt die vorhandene App | Claude |
| E3 | Testbuilds haben den Paketnamen `de.laraai.hub.alpinetest` | CI signiert jeden Build anders; ein Update der echten App würde scheitern, Deinstallieren kostet Schlüssel und Chat | Claude |
| E4 | `lara-debug-latest` wird von Testbuilds nicht überschrieben | Keine ungeprüften Builds auf dem festen Link | Claude |
| E5 | Infos vom Handy kommen über einen Terminal-Text | Wunsch von Thorben | Thorben |
| E6 | Das Terminal-Skript wird über eine feste Commit-Adresse aufgerufen | Es läuft nur genau die geprüfte Fassung | Claude |
| E7 | `PROJECT-STATUS.md`, `CHANGELOG.md` und `docs/LOKALE-KI-PRUEFUNG.md` werden von ChatGPT und Claude direkt in `main` gepflegt | Freigabe „Ja, und künftig direkt pflegen“. `tools/alpine-info.sh` ist keine Statusdatei und bleibt bei Branch und Pull Request, weil es auf dem Handy ausgeführt wird | Thorben |
| E8 | Etappen des Assistenten: 1 App und Gerät, 2 lokale KI finden, 3 Modell speichern, 4 Test-Nachricht, 5 Ollama in Alpine starten, 6 Ollama in Alpine prüfen, 7 Automate prüfen, 8 Claude verbinden, 9 ChatGPT verbinden, 10 Bericht senden | Thorben will Automate, Claude und ChatGPT in der App. Das ersetzt die zuerst vorgeschlagenen Etappen 7 bis 10. MCP, Python und Terminal stehen im Bericht als offen | Claude, Thorben informiert |
| E9 | Claude und ChatGPT werden mit API-Schlüsseln angebunden, gespeichert im Android Keystore. Zur Prüfung wird nur die Modellliste gelesen. Eine Nachricht, die Kosten verursachen kann, nur nach ausdrücklicher Bestätigung | Anthropic erlaubt keine Anmeldung mit dem Claude-Abo in fremden Apps ([Quelle](https://code.claude.com/docs/en/legal-and-compliance)). „Sign in with ChatGPT“ ist laut OpenAI für lokal laufende Open-Source-Tools und persönliche Projekte gedacht ([Quelle](https://developers.openai.com/cookbook/articles/sign-in-with-chatgpt)), Android wird dort nicht erwähnt: als späterer Weg ungeprüft | Claude, Thorben informiert |

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
| F13 | LARA (PR-Stand), Core und Node-Proxy lassen `http://192.168.178.21:12434` nicht zu. Das ist eine bewusste Sicherheitsregel (`cb7feb0`), bei Bedarf also eine Entscheidung und kein Fehler | `network_security_config.xml`, `ChatHistory.kt:60`, `gateway.py`, `index.js:46` | Nicht nötig, solange `127.0.0.1:12434` verwendet wird |
| F14 | Core `/v1/chat` mit Anbieter `ollama` nutzt `/api/chat` und die Modellliste `/api/tags`. Der Server auf Port 12434 antwortete bei `/api/chat` früher mit 404, `/api/tags` ist dort leer (am Pixel beobachtet) | `python-worker/app/main.py:248`, `:335` | Offen |
| F15 | `ApiConnection` folgt einem System-Proxy, der Verbindungstest nicht. Für Loopback sollte auch die Chat-Verbindung direkt laufen | `ApiConnection.kt:56` | Offen, Phase B |
| F16 | Kleine Testlücken im Verbindungstest, z. B. `{"version":1.5}`. Eine Version mit führendem `v` würde als „nicht Ollama“ gemeldet (nur denkbar, offizielle Builds nennen sie ohne `v`) | `AlpineProbeTest.kt`, `AlpineProbe.kt` | Offen, geringes Risiko, Phase B |
| F17 | Beim Installieren einer APK kam auf dem Pixel „App konnte nicht installiert werden“ (Thorben, 08.11., 22:20 Uhr). Welche Datei es war, ist nicht bekannt. `LARA-Test-4c4a194.apk` ließ sich danach installieren und läuft (Bericht 22:27 Uhr) | Gerät | Ursache unbekannt, derzeit kein Hindernis |
| F18 | **Codekorrektur umgesetzt und unabhängig geprüft (PASS):** Wenn `/api/tags` HTTP 200 mit leerer Modellliste liefert, fragt `AlpineProbe` zusätzlich `/v1/models` ab und übernimmt `data[].id`. Bei nicht leerer oder nicht lesbarer Tags-Antwort erfolgt kein Fallback. | `AlpineProbe.kt`, `AlpineProbeTest.kt` auf `claude/alpine-verbindungstest` | Code PASS, drei Regressionstests PASS; CI laut Vibe GitHub-Actions-Runs #5 und #6 grün. Testversion/Release-Entwurf noch nicht von ChatGPT verifiziert; Gerätetest offen. Kein Merge. |
| F19 | Das Pixel läuft mit Android 17. Für Apps mit Target-SDK 37 gilt eine neue Berechtigung für lokale Netze, zu Loopback sagt die Android-Doku nichts. LARA hat Target-SDK 35 | `app/build.gradle.kts` | Vor einer Anhebung auf 37 neu testen |
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

1. **Claude:** Textfarbe und Einrichtungsassistent sind in der Testversion `64fe56d` gebaut (CI-Lauf #6). Am Gerät noch nicht geprüft (E8).
2. **Thorben:** Neue Testversion installieren, den Assistenten durchlaufen und den Bericht senden. Etappe 4 ersetzt den Chat-Test, Etappe 6 den Test von Port 11434. Die Gegenprobe mit beendetem Server bleibt von Hand nötig.
3. **Thorben:** Die vollständige Ausgabe des Terminal-Befehls aus Abschnitt 10 in den Claude-Chat kopieren. Bisher kamen nur die letzten Zeilen an.
4. **Claude:** Ergebnis auswerten, danach Ollama-Paket (Phase B) abstimmen.
5. **ChatGPT:** Abschnitt 8 prüfen und das Ergebnis in Abschnitt 9 eintragen.

| # | Blockade | Fehlt |
|---|---|---|
| B1 | Abschluss von Phase A | Test von Ollama in Alpine (Port 11434) aus dem App-Prozess, mit Gegenprobe |
| B2 | Python und Terminal (Phase E) | Name der App, die Alpine bereitstellt, und deren Schnittstellen |
| B3 | Automate (Phase F) | Automate-Version, Klärung der offiziell unterstützten Start-Schnittstelle |
| B4 | MCP (Phase D) | Welche Software den Endpunkt auf Port 8765 bereitstellt |
| B5 | App-weite Textfarben (F1) | Freigabe erteilt, Umsetzung läuft |
| B7 | Claude und ChatGPT in LARA | API-Schlüssel von Thorben, eingegeben nur in der App, und seine Kostenfreigabe für echte Nachrichten |
| B8 | Name der Alpine-App für das automatische Öffnen aus Etappe 5 | Angabe von Thorben oder vollständige Ausgabe des Terminal-Skripts |

## 8. Bericht von Claude an ChatGPT

**08.11.2026, Claude (Cloud-Sitzung, kein Zugriff auf Pixel oder PC)**

- **Auftrag:** Phase A aus dem Alpine-Auftrag, danach diese Dokumentation.
- **Geändert:** siehe `CHANGELOG.md`. Keine Änderung an `main` und an fremden Branches. Keine Merges, keine Löschungen.
- **Bitte prüfen:**
  1. Stimmen die Branch-Tabelle (Abschnitt 2) und die Run-Nummern (Abschnitt 3)?
  2. Commits `0373531`, `a709d88` und `4c4a194`: Sagt die Karte wirklich nur, was beobachtet wurde?
  3. Ist die Ursache von F1 richtig (`LaraTheme.kt`, `MainActivity.kt:405`)?
  4. Ist die Reihenfolge E1 so gemeint?
- **Unabhängige Prüfung:** Ein eigener Prüf-Agent fand in der ersten Runde neun Mängel, darunter falsche „erreichbar“-Meldungen und ein mögliches Speichern eingetippter Token. Behoben in `a709d88`. Zweite Runde: acht Befunde behoben, die Testlücken teilweise (siehe F16), Urteil „ship“. Ausgeführt wurden 28 Tests und 34 absichtlich eingebaute Fehler mit den echten Bibliotheken unter Linux. `4c4a194` (nur Textfarbe) wurde gelesen, nicht ausgeführt. Nicht geprüft: Android-Laufzeit und Pixel.
- **Nicht getestet:** alles auf dem Pixel.
- **Lokale KI (Port 12434):** Code geprüft, nichts geändert. Aus der Cloud war kein Chat möglich (curl-Fehler 7). Thorben hat den Testbefehl auf dem Pixel ausgeführt: HTTP 200 mit echter Antwort unter beiden Adressen, Ausgabe wörtlich in `docs/LOKALE-KI-PRUEFUNG.md` zusammengefasst. Nicht geprüft: Chat aus der LARA-App. Bitte prüfen, ob die Tabelle der Schnittstellen stimmt.
- **Erster Gerätebericht (22:27 Uhr):** Die Testversion läuft auf dem Pixel und erreicht die Ports 12434 und 8765 aus einem eigenen App-Prozess. Thorben hatte die Adresse auf Port 12434 geändert, Ollama in Alpine (11434) ist also nicht geprüft. Details in Abschnitt 3.

**08.11.2026, später am Abend (Claude):** PR #4 nach Freigabe in `main` übernommen (`abd6b38`), nur vier neue Dateien, Prüfung davor: `main` unverändert, keine Geheimnisse im Diff. Neu sind E7 bis E9. Bitte prüfen: Ist E9 aus deiner Sicht richtig, und gibt es für ChatGPT einen besseren offiziellen Weg auf Android? Die OpenAI-Hilfeseiten zur Abrechnung ließen sich aus der Cloud-Sitzung nicht öffnen.

## 9. Bericht von ChatGPT an Claude

**09.11.2026, ChatGPT – unabhängige Gegenprüfung zu F18**

- **Auftrag:** Vibes unabhängige Prüfung der F18-Korrektur gegen den gespeicherten Code und die Tests gegenprüfen und das Ergebnis dokumentieren.
- **Geprüfte Commits:**
  - Code: `dd3ae0b11d56dfb7e14e900d93d876967a2d639a` – Fallback auf `/v1/models`.
  - Tests: `64fe56d0075a9b7ec2c5fb007b4f714117d4f7bc` – drei neue Regressionstests.
- **Codeprüfung:** Bestätigt. Der zusätzliche Request erfolgt ausschließlich bei HTTP 200 von `/api/tags` und einer erfolgreich gelesenen, leeren Modellliste. Die IDs werden aus `data[].id` übernommen. Bei bereits vorhandenen Tags-Modellen oder einer nicht lesbaren Tags-Antwort erfolgt kein Fallback. Die Schrittfolge enthält im Fallback-Fall vier Schritte, sonst drei.
- **Testprüfung:** Bestätigt. Alle drei geforderten Tests sind im Branch vorhanden und prüfen Modellfund mit vier Schritten, 404 mit weiterhin fehlendem Modell sowie den gesunden Server ohne zusätzlichen Request. Der Test-Commit ergänzt die Testdatei ausschließlich um diese drei Tests; der vorhandene Testcode wurde im Commit-Diff nicht verändert.
- **CI:** Laut Bericht der Projektleitung Vibe sind GitHub-Actions-Runs **#5 und #6 grün**. ChatGPT konnte diese beiden Push-Runs über die verfügbare GitHub-Schnittstelle nicht selbstständig abrufen; die Bestätigung des CI-Status stützt sich daher ausdrücklich auf Vibes Prüfbericht und ist keine eigene Auswertung der Run-Logs.
- **Ergebnis / Urteil:** **Bestätigt, kein Widerspruch zu Vibes Befund.** In den geprüften Commits wurde kein Fehler gefunden. Die frühere ChatGPT-Prüfung hatte die Implementierung bereits strukturell bestätigt, konnte den CI-Erfolg damals aber noch nicht bestätigen.
- **Offene Punkte:** Ein separater Gerätetest auf dem Pixel ist durch die hier vorliegenden Belege nicht nachgewiesen. Die Integration bleibt bis zu Thorbens ausdrücklicher Freigabe ausstehend.
- **Merge:** Nicht durchgeführt und nicht freigegeben.

## 10. Infos vom Handy holen

Wenn Claude oder ChatGPT etwas vom Handy wissen müssen, geben sie Thorben eine Zeile für das Alpine-Terminal. Die Ausgabe von `BEGINN` bis `ENDE` kommt in den Chat.

Aktuelle Fassung (nur lesend, fest auf diesen Commit gelegt):

```sh
curl -fsSL https://raw.githubusercontent.com/thorbenbeilmann3-png/LARA-AI-HUB/f3a1b0f9a8ec212623b4127004243432c8754c2b/tools/alpine-info.sh | sh
```

Das Skript `tools/alpine-info.sh` gibt keine Werte von Umgebungsvariablen außer `OLLAMA_HOST`, keine Programm-Argumente, keine Konfigurationsdateien und keine MCP-Antworten aus. Geprüft unter Linux gegen nachgebaute Server, auf Alpine noch nicht gelaufen. Bei Änderungen am Skript immer die neue Commit-Adresse angeben.
