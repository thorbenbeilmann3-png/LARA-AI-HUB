# Prüfung der lokalen KI unter 192.168.178.21:12434

Stand: 08.11.2026, Claude (Cloud-Sitzung ohne Zugriff auf Handy oder PC). Nur Prüfung und Dokumentation, am Code wurde nichts geändert.

**Ergebnis:** LARA unterstützt OpenAI-kompatible Server. Eine echte Chat-Anfrage an diese Adresse konnte von hier **nicht** ausgeführt werden, sie ist also **NICHT GETESTET**. Außerdem blockiert die aktuelle LARA-App unverschlüsseltes HTTP zu dieser Adresse absichtlich.

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
| Aus der LARA-App | – | NICHT GETESTET |

`192.168.178.21` ist eine private Adresse im Heimnetz. Dass sie aus der Cloud nicht erreichbar ist, ist zu erwarten und sagt **nichts** über den Server aus.

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
| Server beantwortet `/v1/chat/completions` mit echtem Text | **NICHT GETESTET** |
| Modellname wird vom Server akzeptiert | **NICHT GETESTET** |
| LARA-Prozess erreicht den Server | **NICHT GETESTET** |
| LARA darf diese Adresse verwenden | **Nicht erfüllt** im PR-Stand, erfüllt nur auf `main` |

## Was für die Integration noch fehlt

1. Der echte Chat-Test (unten).
2. Die Klärung, **auf welchem Gerät** der Server läuft:
   - **Auf dem Pixel:** In LARA `http://127.0.0.1:12434` eintragen. Keine Codeänderung nötig, die Adresse ist bereits erlaubt.
   - **Auf einem anderen Gerät:** Klartext-HTTP ins WLAN müsste zugelassen werden oder der Server HTTPS bekommen. Das ändert eine vom Nutzer freigegebene Sicherheitsentscheidung (`cb7feb0`) und betrifft App, Core und Node-Proxy. Nur nach Abstimmung. Eine feste IP-Adresse wäre dann ebenfalls nötig.
3. Der Test aus dem echten LARA-Prozess auf dem Pixel.

Hinweis: Hat der Server keine Anmeldung, kann ihn jedes Gerät im selben WLAN benutzen.

## Echter Chat-Test

Im Alpine-Terminal ausführen, die Ausgabe in den Chat kopieren. Der Befehl sendet genau das Anfrageformat, das LARA verwendet, und prüft beide Adressen:

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
