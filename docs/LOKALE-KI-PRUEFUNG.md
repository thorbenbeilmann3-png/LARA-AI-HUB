# Prüfung der lokalen KI unter 192.168.178.21:12434

Stand: 08.11.2026, Claude (Cloud-Sitzung ohne Zugriff auf Handy oder PC). Nur Prüfung und Dokumentation, am Code wurde nichts geändert.

**Ergebnis (aktualisiert 08.11.2026, 22:20 Uhr):** Der Server beantwortet echte Chat-Anfragen, unter `192.168.178.21` und unter `127.0.0.1`. Das wurde auf dem Pixel im Alpine-Terminal ausgeführt. Das Antwortformat passt zu LARA. Eine eigene App auf dem Pixel erreicht den Server (Testversion, 22:27 Uhr). Eine **Chat-Anfrage aus der LARA-App** ist weiterhin **NICHT GETESTET**. Die App sperrt `192.168.178.21` per HTTP absichtlich, `127.0.0.1` ist erlaubt.

## Angaben des Nutzers (nicht selbst geprüft)

- API-Basisadresse: `http://192.168.178.21:12434/v1`
- Modell: `qwen2.5-0.5b-instruct-q4_k_m`
- `/v1/models` liefert erfolgreich die Modellliste

Dieser Server ist nicht das Ollama in Alpine (Port 11434), siehe `PROJECT-STATUS.md`, Abschnitt 2.

## Tatsächlich ausgeführte Tests

| Test | Von wo | Ergebnis |
|---|---|---|
| `GET /v1/models` | Cloud-Umgebung, 08.11.2026 21:13 UTC | Keine Verbindung (curl-Fehler 7, „Couldn't connect to server“) |
| `POST /v1/chat/completions` mit dem Modell oben | Cloud-Umgebung, gleiche Zeit | Keine Verbindung (curl-Fehler 7) |
| Über den PC des Nutzers | – | Nicht möglich, der Computer war nicht verbunden |
| `POST /v1/chat/completions` an `192.168.178.21:12434` | Alpine-Terminal auf dem Pixel, ausgeführt von Thorben, 08.11.2026 22:16 Uhr | **PASS:** HTTP 200 in 2,10 s, Antwort „Hallo! Wie kann ich Ihnen heute helfen?“, `finish_reason: stop`, Tokens 93 / 9 / 102 |
| `POST /v1/chat/completions` an `127.0.0.1:12434` | Gleiche Stelle, Sekunden später | **PASS:** HTTP 200 in 1,05 s, gleiche Antwort und gleiche Tokenzahlen |
| `GET /api/version` an `127.0.0.1:12434` | Testversion von LARA auf dem Pixel (Android 17, Build `4c4a194`, UID 10494), 22:27 Uhr | **PASS:** HTTP 200 in 69 ms, Version 0.1.32 |
| `GET /api/tags` an `127.0.0.1:12434` | Gleiche Stelle | HTTP 200 in 59 ms, **0 Modelle** |
| Chat aus der LARA-App | – | NICHT GETESTET |

`192.168.178.21` ist eine private Adresse im Heimnetz. Dass sie aus der Cloud nicht erreichbar ist, ist zu erwarten und sagt **nichts** über den Server aus.

Der Server meldet IDs der Form `local-…`, das ist nicht das Format von Ollama. Er ist also nicht das Ollama in Alpine. 93 Eingabe-Tokens für eine sehr kurze Nachricht sprechen dafür, dass er einen eigenen Systemtext ergänzt (ANNAHME).

## Vorhandene KI-Schnittstellen

Geprüft durch Lesen des Codes auf `claude/pc-thorben-setup-i4qwi5` @ `512a001` und `main` @ `ec254c7`.

| Komponente | OpenAI-kompatibel? | Würde `http://192.168.178.21:12434` annehmen? |
|---|---|---|
| Android-App, PR-Stand: Anbieter „Ollama · lokal“ und „Eigener OpenAI-kompatibler Server“ (`ApiConnection.kt`: `/v1/models`, `/v1/chat/completions`, `choices[0].message.content`, Tokens aus `usage`) | Ja. Basisadresse mit `/v1` wird richtig gekürzt (`rootUrl`) | **Nein.** Zweifach gesperrt: `network_security_config.xml` und `CleartextPolicy` erlauben HTTP nur zu `127.0.0.1` und `localhost` |
| Android-App auf `main` (`callChatApi`) | Ja, nur Chat und nur mit voller URL. Keine Modellliste, Einstellungen werden nicht gespeichert, Lese-Zeitlimit 10 s | Ja, `usesCleartextTraffic="true"` |
| Core-Gateway `POST /v1/gateway/local/chat` (`gateway.py`) | Ja | **Nein**, nur Loopback (HTTP 503) |
| Core `POST /v1/chat`, Anbieter `ollama` (`main.py`) | Nein, nutzt `/api/chat`. Dieser Server-Typ antwortete dort früher mit 404 (`docs/AI_HANDOVER.md`) | **Nein**, nur Loopback |
| Node-Proxy (`index.js`) | Ja | Nur wenn in `LARA_ALLOWED_UPSTREAMS` eingetragen, Standard ist `127.0.0.1:12434` |
| Web-Oberfläche (`index.html`) | Ja, läuft über den Node-Proxy | Wie Node-Proxy |

Automatische Tests für diesen Weg gibt es im Core (`test_gateway.py`, mit simuliertem Server) und im Node-Proxy (`tests/index.test.js`). Für den OpenAI-Weg in der Android-App gibt es keinen eigenen Test.

## Voraussetzungen

| Voraussetzung | Stand |
|---|---|
| LARA kennt das OpenAI-Format für Modellliste und Chat | Erfüllt (Code gelesen) |
| Kein API-Schlüssel nötig für diesen Anbietertyp | Erfüllt (Code gelesen). Ob der Server einen verlangt: unbekannt |
| Zeitlimit lang genug für ein kleines lokales Modell | Erfüllt im PR-Stand (120 s). Auf `main` nur 10 s |
| Server liefert `/v1/models` | Nutzerangabe |
| Server beantwortet `/v1/chat/completions` mit echtem Text | **Erfüllt** (Test auf dem Pixel, siehe oben) |
| Modellname wird vom Server akzeptiert | **Erfüllt** (gleicher Test) |
| Antwort passt zu LARA (`choices[0].message.content`, `usage.prompt_tokens`, `usage.completion_tokens`) | **Erfüllt** (Ausgabe mit dem Code verglichen) |
| Server unter `127.0.0.1:12434` erreichbar | **Erfüllt** aus dem Alpine-Terminal |
| LARA-Prozess erreicht den Server | **Erfüllt** (Testversion mit dem Code von LARA und eigenem Paketnamen) |
| LARA-Prozess bekommt eine Chat-Antwort | **NICHT GETESTET** |
| LARA darf die Adresse verwenden | `127.0.0.1`: erfüllt. `192.168.178.21`: **nicht erfüllt** im PR-Stand, erfüllt nur auf `main` |

## Was für die Integration noch fehlt

1. Eine Chat-Anfrage aus der LARA-App auf dem Pixel.
2. In LARA die Adresse `http://127.0.0.1:12434` verwenden. Dafür ist **keine Codeänderung** nötig, sie ist der Standard des Anbieters „Ollama · lokal“. Weil beide Adressen im Alpine-Terminal antworten, läuft der Server sehr wahrscheinlich auf dem Pixel.
3. Nur falls der Server doch über das WLAN angesprochen werden soll: Klartext-HTTP müsste zugelassen werden oder der Server HTTPS bekommen. Das ändert eine vom Nutzer freigegebene Sicherheitsentscheidung (`cb7feb0`) und betrifft App, Core und Node-Proxy. Nur nach Abstimmung, eine feste IP-Adresse wäre dann ebenfalls nötig.

**Nebenergebnis für Alpine:** Sollte dieser Server eine eigene Android-App sein, zeigt der Test, dass Alpine dasselbe `127.0.0.1` nutzt wie andere Apps. Das wäre ein starker Hinweis, dass auch LARA das Ollama in Alpine erreicht. Ob der Server außerhalb von Alpine läuft, ist noch nicht belegt.

Hinweis: Hat der Server keine Anmeldung, kann ihn jedes Gerät im selben WLAN benutzen.

## Hinweis zur Meldung „Modell fehlt“

Die Testkarte meldete „das erwartete Modell fehlt“. Das ist bei diesem Server ein Fehlalarm: Sie liest `/api/tags`, und dort liefert er eine leere Liste. Das Modell heißt hier außerdem `qwen2.5-0.5b-instruct-q4_k_m` und nicht `qwen2.5:0.5b`. Die Chat-Funktion von LARA fragt zuerst `/v1/models` ab. Dort steht das Modell laut Thorbens Angabe.

## Echter Chat-Test

Am 08.11.2026 ausgeführt, Ergebnis siehe oben. Zum Wiederholen im Alpine-Terminal ausführen und die Ausgabe in den Chat kopieren. Der Befehl sendet genau das Anfrageformat, das LARA verwendet, und prüft beide Adressen:

```sh
for h in 192.168.178.21 127.0.0.1; do echo "== $h"; curl -sS -m 180 -w '\nHTTP %{http_code} in %{time_total}s\n' http://$h:12434/v1/chat/completions -H 'Content-Type: application/json' -d '{"model":"qwen2.5-0.5b-instruct-q4_k_m","messages":[{"role":"user","content":"Sag nur: Hallo"}],"stream":false}'; done
```

| Ausgabe | Bedeutung |
|---|---|
| Bei beiden HTTP 200 mit Antworttext | Der Server arbeitet und läuft auf diesem Handy. In LARA `127.0.0.1` verwenden |
| Nur `192.168.178.21` klappt | Der Server läuft auf einem anderen Gerät, oder Alpine sieht das Loopback des Handys nicht |
| Nur `127.0.0.1` klappt | Der Server läuft auf diesem Handy, ist aber nicht über die WLAN-Adresse erreichbar |
| HTTP 400 oder 404 | Endpunkt oder Modellname passt nicht |
| „Failed to connect“ bei beiden | Server läuft nicht oder ist von Alpine aus nicht erreichbar |

Ein Erfolg im Alpine-Terminal beweist die Funktion des Servers, aber nicht die Verbindung aus der LARA-App.
