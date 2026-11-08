# LARA: Status zu Alpine, Ollama, Python/Terminal, Automate und MCP

Arbeitsstand zum verbindlichen Auftrag `docs/ALPINE-OLLAMA-MCP-INTEGRATION.md` (Stand des Auftrags: Commit `a92bfd5`). Der aktuelle Gesamtstand steht in `PROJECT-STATUS.md`; diese Datei enthält die Einzelheiten zu diesem Paket.

**Kurzfassung:** Phase A ist analysiert, der Verbindungstest läuft auf dem Pixel 9a. Der entscheidende Nachweis für Ollama in Alpine (Port 11434) steht noch aus. Die Geräteergebnisse stammen aus Berichten von Thorben, diese Arbeit selbst läuft in einer Cloud-Umgebung ohne Zugriff auf das Handy.

## 1. Vorgaben, belegte Fakten, Annahmen

**Vorgaben (Auftrag):** kleine geprüfte Pakete; Bestehendes erhalten und wiederverwenden; Reihenfolge Verbindung → Ollama-Chat → Python/Terminal → Automate, MCP als eigenes Paket; keine Tokens in Git, Chat oder Berichten; Test-Status nur PASS / FAIL / NICHT GETESTET.

**Belegt, im Repository geprüft:**

| Fund | Beleg |
|---|---|
| `main` enthält nur die Starter-App (eine Kotlin-Datei, 183 Zeilen) und den Auftrag | `git ls-files` auf `a92bfd5` |
| Die vorhandenen Funktionen liegen auf unverbundenen Entwurfs-Branches | PR #1, #2, #3, alle offen und nicht gemergt |
| Aktiver Stand der App: `claude/pc-thorben-setup-i4qwi5` @ `512a001`, 13 Kotlin-Dateien | `docs/AI_SHARED_HANDOFF.md`, Git |
| Zu Alpine, PRoot und einem MCP-Client gibt es auf keinem Branch Code | `git grep` über alle 5 Branches |
| Am App-Code hat sich seit dem Release-Build `0418dfa` nichts geändert | `git diff 0418dfa 512a001 -- app` ist leer |

**Belegt, Fremdquellen:**

- PRoot trennt das Netzwerk nicht ab: Programme im Gast nutzen Host-Ressourcen wie das Netzwerk „wie normale Host-Programme“ ([proot-Handbuch](https://dyn.manpages.debian.org/testing/proot/proot.1.en.html)).
- Loopback-Ports sind auf Android für andere Apps erreichbar ([SEI CERT DRD23-J](https://wiki.sei.cmu.edu/confluence/display/android/DRD23-J.+Do+not+use+loopback+when+handling+sensitive+data)).
- Die lokale Netzwerk-Berechtigung von Android 17 gilt für Apps mit Target-SDK 37 oder höher. Apps darunter mit INTERNET-Berechtigung behalten den Zugriff vorerst. Loopback wird dort nicht erwähnt, also weder ein- noch ausgeschlossen ([Android-Doku, Stand 03.11.2026](https://developer.android.com/privacy-and-security/local-network-permission)). LARA hat Target-SDK 35.
- Ollama: `GET /api/version` ([Doku](https://docs.ollama.com/api-reference/get-version)), `GET /api/tags` mit `models[].name` ([Doku](https://docs.ollama.com/api/tags)), `/v1/chat/completions` ([Doku](https://docs.ollama.com/api/openai-compatibility)). Lokale Anfragen brauchen keinen Schlüssel ([Doku](https://docs.ollama.com/api/index)).
- App-Freezer: Zwischengespeicherte App-Prozesse werden angehalten, ab Android 14 zehn Sekunden nach Eintritt in den Cached-Zustand ([AOSP](https://source.android.com/docs/core/perf/cached-apps-freezer)).
- Kindprozesse von Apps können ab Android 12 beendet werden, Grenze 32 für alle Apps zusammen. Ab Android 14 gibt es dazu die Entwickleroption „Disable child process restrictions“ ([Dokument eines Termux-Maintainers](https://github.com/agnostic-apollo/Android-Docs/blob/master/en/docs/apps/processes/phantom-cached-and-empty-processes.md), keine offizielle Google-Quelle).

**Annahmen (nicht belegt):**

- A1: Alpine läuft wirklich unter PRoot und nicht in einer virtuellen Maschine. Die gemessenen 7,4 GiB RAM passen zum ganzen Gerät, das ist aber nur ein Indiz.
- A2: Daraus folgt die Erwartung, dass LARA `127.0.0.1:11434` erreicht. Das ist eine Erwartung, kein Ergebnis.
- A3: Die Testversion mit eigenem Paketnamen verhält sich im Netzwerk wie LARA (gleiches Manifest, gleiche Netzwerkregeln, andere Linux-UID).

## 2. Bestand im App-Code (Branch `claude/pc-thorben-setup-i4qwi5`)

| Bereich | Ort | Zustand |
|---|---|---|
| Chat | `MainActivity.kt` (Tab „Chat“) | Fragt vor jeder Nachricht erneut die Modellliste ab, dann `ApiConnection.chat` |
| API / Anbieter | `ApiConnection.kt` | 8 Anbieter in `AiProvider`; Ollama-Standard `http://127.0.0.1:12434/` (Zeile 21) |
| Einstellungen | `loadConfig` / `saveConfig` in `MainActivity.kt`, Tab „Tools“ | Anbieter, Adresse, Modell, Flow-URI in `lara_hub_config` |
| Sichere Speicherung | `ApiKeyStore.kt`, `SessionLock.kt` | AES-GCM im Android Keystore, an Geräte-Anmeldung gebunden, Anbieter-ID als AAD. Für den MCP-Token mit eigener ID wiederverwendbar |
| Verbindungsprüfung | `ApiConnection.discover`, Monitor „Anbieter-Status“ | Vorhanden, aber ohne `/api/version` |
| Klartext-HTTP | `res/xml/network_security_config.xml` | Nur `127.0.0.1` und `localhost`, passt zu Ollama und MCP |
| Automate | `openAutomate` in `MainActivity.kt`, `docs/AUTOMATE-SETUP.md` | Siehe Abschnitt 5 |
| Python | `python-worker/` (Core für Pydroid), PR #2 (Bridge) | Nicht für Alpine gebaut, auf dem Pixel laut Protokoll nicht getestet |

**Warum Anbieter in der installierten App fehlen können (ANNAHME, am Gerät zu klären):** Wer eine APK aus `main` installiert hat (jeder Push auf `main` baut eine), hat die Starter-App: Tabs Chat, Postfach, ARENA, PC, ohne Anbieterauswahl. Auf dem PR-Stand heißen die Tabs Home, Chat, Arena, Projekte, Tools. Dort versteckt sich die Auswahl hinter einer Schaltfläche, die nur den aktuellen Anbieter nennt („Ollama · lokal“). Auch dann zeigt die App für Ollama Port 12434 statt 11434 an.

## 3. Befunde, die noch nicht behoben sind

Das Paket hat sie bewusst nicht angefasst. Sie gehören in die genannte Phase.

| # | Befund | Phase |
|---|---|---|
| 1 | Ollama-Standardport ist 12434; Alpine-Ollama läuft laut Auftrag auf 11434 | B |
| 2 | Kein `GET /api/version`; `discoverOllama` nimmt zuerst `/v1/models`, der Chat läuft dann über die OpenAI-Schnittstelle statt `/api/chat` | B |
| 3 | Bei einem eingefrorenen Server kann `discover` rund vier Minuten hängen (zwei Versuche, je bis 120 s), auch vor jeder Chat-Nachricht | B |
| 4 | `discoverOllama` fasst Fehler zu einem Text zusammen, die Ursache geht für `ProviderErrors` verloren | B |
| 5 | Port 8765 ist doppelt belegt: Python-Bridge aus PR #2 und Alpine-MCP-Endpunkt. Auf gemeinsamem Loopback kann nur einer lauschen | D |
| 6 | Lokales Ollama hat keine Anmeldung. Ist es erreichbar, kann es jede App auf dem Handy mit INTERNET-Berechtigung nutzen | B / C |
| 7 | Automate-Autostart, siehe Abschnitt 5 | F |
| 8 | `versionCode` und `versionName` sind für alle Builds gleich; welcher Build installiert ist, war bisher nicht erkennbar. Der Test-Bericht nennt jetzt den Commit | B |
| 10 | Schwarzer Text auf dunklem Grund: `LaraTheme` setzt keine Standard-Textfarbe, und `Scaffold(containerColor = Color.Transparent)` übernimmt dadurch Schwarz. Betrifft jeden `Text` ohne eigene Farbe, auch die Statuszeile. Nur die neue Karte ist korrigiert, die App-weite Korrektur braucht deine Zustimmung | vor dem nächsten App-Paket |
| 9 | CI signiert ohne festen Schlüssel: `lara-debug-latest` hat das Zertifikat `5d3db071…`, der Testbuild `a5f90b91…`. Android lehnt ein Update bei abweichender Signatur ab, dann bleibt nur Deinstallieren, und damit gehen gespeicherte Schlüssel und Chat verloren. Mögliche Ursache für „alte App bleibt installiert“ (ANNAHME). Ein fester Schlüssel als GitHub-Secret braucht deine Freigabe | vor einem Update der echten App |

## 4. Paket A4: Verbindungstest

Neue Karte unter **Tools → Alpine / Ollama – Verbindungstest**. Sie läuft im App-Prozess, nutzt OkHttp wie die echten Anfragen und prüft mit kurzen Fristen (3 s Verbinden, 6 s Lesen, 10 s gesamt):

1. `GET {Ollama}/api/version`
2. `GET {Ollama}/api/tags`, erkennt das erwartete Modell
3. `GET {MCP}` **ohne Token**, wertet nur den Statuscode aus

„Erreichbar“ meldet die Karte nur, wenn eine gültige Versionsangabe und eine gültige Modellliste zurückkamen. Ein HTTP 401 vom MCP-Endpunkt zeigt nur, dass dort ein Dienst antwortet. Der kopierbare Bericht enthält Commit, UID, PID und Android-Version, aber keine Antworten und keine Zugangsdaten. In den Adressfeldern sind nur Protokoll, Rechner und Port erlaubt, bei MCP zusätzlich `/mcp`; andere Adressen werden weder gesendet noch gespeichert.

| Meldung | Bedeutung | Naheliegende Ursache |
|---|---|---|
| Abgewiesen | Aus Sicht dieser App lauscht dort nichts | Server beendet, falscher Port oder Alpine-Loopback ist getrennt |
| Verbindung verhindert | Android hat die Verbindung nicht aufgebaut | Keine Route oder keine Berechtigung, Firewall- oder VPN-App |
| Keine Verbindung | Zeitüberschreitung beim Verbinden | Netzwerkregel, Firewall- oder VPN-App |
| Keine Antwort | Verbindung steht, Server schweigt | Alpine-App eingefroren oder Gerät überlastet |
| Zeitüberschreitung | Gesamtfrist abgelaufen, Verbindungsstand unbekannt | Wie bei den beiden Zeilen darüber |
| Unerwartete Antwort | Etwas antwortet, aber nicht wie erwartet | Anderer Dienst auf dem Port |

**Geräteprüfung:**

1. In Alpine `ollama serve` starten.
2. „LARA Test“ öffnen, unter Tools „Verbindung jetzt testen“ tippen, Bericht kopieren.
3. **Gegenprobe:** `ollama serve` in Alpine beenden und erneut testen. Es muss „Abgewiesen“ erscheinen. Erst damit ist belegt, dass wirklich dieser Alpine-Server geantwortet hat.

Die Testversion heißt „LARA Test“ (`de.laraai.hub.alpinetest`) und installiert sich neben einer vorhandenen LARA-App, sie kann diese samt gespeicherter Schlüssel nicht überschreiben. Eine neuere Testversion lässt sich wegen der wechselnden CI-Signatur erst installieren, wenn die ältere „LARA Test“ entfernt ist. Für den Nachweis im echten LARA-Prozess reicht in der vorhandenen App: Tools → Ollama · lokal → Adresse `http://127.0.0.1:11434` → „Verbinden / Modelle laden“.

## 5. Automate: Prüfung der vorhandenen Anbindung

`openAutomate` (`MainActivity.kt`, Zeilen 274–302) startet per `startActivity` die Aktion `com.llamalab.automate.intent.action.START_FLOW` mit der Flow-URI. Klappt das nicht, öffnet LARA Automate. Die Rückmeldung ist ehrlich formuliert („an Android übergeben“).

- **Dokumentationslage:** Die geprüften offiziellen Seiten nennen die Flow-URI und einen Payload, beschreiben aber keine Start-Schnittstelle für andere Apps und erwähnen `START_FLOW` nicht ([Flow beginning](https://llamalab.com/automate/doc/block/flow_beginning.html), [Flow start](https://llamalab.com/automate/doc/block/flow_start.html), [Extensions](https://llamalab.com/automate/doc/extensions.html)). Gefunden habe ich sie nur in [Nutzerbeiträgen von 2017 und 2019](https://groups.google.com/g/automate-user/c/wlu_Gdykst8). Der Auftrag verbietet undokumentierte Intents. Vor Phase F ist zu klären, ob die Aktion offiziell belegt ist oder ob ein anderer Weg nötig wird. Ob sie auf dem Pixel funktioniert, ist nicht geprüft.
- **Regelkonflikt:** Der optionale Autostart (`MainActivity.kt`, Zeile 397) löst den Flow bei jedem Neuaufbau der Hauptansicht aus, auch nach dem Entsperren oder Drehen. Das widerspricht voraussichtlich „keine Flows stillschweigend starten“.
- **Es fehlen:** Anzeige des Installationsstatus, Bestätigung vor dem Start, Rückmeldung über Lauf und Ergebnis. Eine offizielle Rückmeldung habe ich bisher nicht gefunden; das bleibt in Phase F zu prüfen.

## 6. Testmatrix (Auftrag Abschnitte 9, 12, 13)

| Nr. | Prüfung | Status | Hinweis |
|---|---|---|---|
| 1 | Bestehende App baut | **PASS** | GitHub Actions: `main` Run 42517359066; PR-Stand Run 42428044594; dieser Branch Run 42519737662 (72 Unit-Tests, 0 Fehler). Kein Nachweis für das Gerät |
| 2 | App startet auf Android | **PASS** | Testversion `de.laraai.hub.alpinetest`, Build `4c4a194`, Pixel 9a, Android 17, Bericht vom 08.11., 22:27 Uhr |
| 3 | LARA-Prozess erreicht Alpine | NICHT GETESTET | Starker Hinweis: Der App-Prozess bekam von `127.0.0.1:8765/mcp` HTTP 401. Dass der Dienst in Alpine läuft, ist nicht belegt. Es fehlen Port 11434 und die Gegenprobe |
| 4 | Ollama-Version über LARA | NICHT GETESTET | Geprüft wurde bisher nur Port 12434 (anderer Server, 0.1.32) |
| 5 | Modellliste über LARA | NICHT GETESTET | Gerät, Paket A4 |
| 6 | `qwen2.5:0.5b` erkannt | NICHT GETESTET | Gerät, Paket A4 |
| 7–8 | Chat an Ollama, echte Antwort | NICHT GETESTET | Gerät, vorhandener Chat oder Phase B |
| 9–11 | MCP-Anmeldung, Tools, Testaufruf | NICHT GETESTET | Kein MCP-Client vorhanden |
| 12 | Falsche Adresse, Token, Ausfall, Timeout | NICHT GETESTET | Teile nur als Unit-Tests der Prüflogik |
| 13 | Neustart und Wiederverbindung | NICHT GETESTET | Phase C |
| 14–21 | Python und Terminal | NICHT GETESTET | Nicht implementiert |
| 22–27 | Automate | NICHT GETESTET | Teilweise Code vorhanden, siehe Abschnitt 5 |

## 7. Arbeitsplan

| Paket | Inhalt | Voraussetzung |
|---|---|---|
| A4 | Verbindungstest | Gerätebericht schließt das Paket ab |
| B | Ollama: Adresse, `/api/version`, `/api/tags`, `/api/chat`, Modellwahl, Abbruch | A4 mit positivem Gerätebericht; sonst zuerst Bridge untersuchen |
| C | Alpine-Verwaltung: Status, letzte Prüfung, Diagnose, Wiederherstellung | B |
| E | Python und Terminal | **BLOCKIERT:** Welche App Alpine bereitstellt und welche Schnittstelle sie anbietet, ist unbekannt |
| F | Automate | E stabil; Automate-Version vom Pixel; Klärung der Start-Schnittstelle |
| D | MCP-Client | Aktuelle MCP-Spezifikation, konkrete Alpine-MCP-Implementierung; eigenes Paket |

Lesart der Reihenfolge: Abschnitt 11 des Auftrags nennt MCP „danach“, Abschnitte 12 und 13 setzen Python/Terminal und direkt danach Automate. MCP steht deshalb hier am Ende. Das lässt sich ohne Aufwand ändern.

## 8. Übergabe

- Agent: Claude (Cloud-Sitzung, Rollen Koordinator und Umsetzer; Prüfer als eigener Agent)
- Zuständigkeit: Phase A, Paket A4
- Eingaben: Auftrag @ `a92bfd5`; Branch `claude/pc-thorben-setup-i4qwi5` @ `512a001`
- Bearbeitung: siehe Abschnitte 2 bis 5
- Ergebnisse: siehe Abschnitt 6
- Status: OFFEN
- Fehlende Informationen: Gerätebericht vom Pixel; Name der App, die Alpine bereitstellt; installierte LARA-Version (Tabs); Automate-Version. **BLOCKIERT:** Phase-A-Abschluss bis zum Gerätebericht, Paket E bis zur Klärung der Alpine-App
- Nächster Agent: Nutzer (Gerätetest), danach Claude
- Übergabedaten: Branch `claude/alpine-verbindungstest`
- Dokumentation: diese Datei
