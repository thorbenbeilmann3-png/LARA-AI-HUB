# Node-Server (`index.js`) – Sicherheitsmodell

## Was vorher galt

- Der Server lauschte auf `0.0.0.0`, also in jedem Netz, in dem das Gerät hängt.
- Der Client durfte jede Ziel-URL vorgeben (`ollamaUrl`). Private Netze wie `10.x` oder `192.168.x` und beliebige Internet-Hosts waren erlaubt. Der Server war also ein offener Proxy (SSRF).
- CORS `*`: Jede Webseite im Browser konnte den Server benutzen und die Antworten lesen.

## Was jetzt gilt

| Schutz | Umsetzung | Einstellung |
|---|---|---|
| Nur lokal erreichbar | lauscht standardmäßig auf `127.0.0.1` | `LARA_BIND_HOST` (z. B. VPN-Adresse; nie öffentlich) |
| Positivliste statt freier Ziele | `ollamaUrl` muss **genau** einem erlaubten Origin entsprechen (Schema, Host, Port). Pfad, Query, Zugangsdaten in der URL werden abgewiesen. Weitergeleitet wird an den Origin aus der Konfiguration, nicht an die Zeichenkette des Clients | `LARA_ALLOWED_UPSTREAMS`, Standard `http://127.0.0.1:12434` |
| Schutz gegen DNS-Rebinding | nur bekannte `Host`-Header | `LARA_ALLOWED_HOSTS`, Standard `127.0.0.1:<PORT>`, `localhost:<PORT>` |
| Kein Fremdzugriff aus dem Browser | kein CORS, außer für ausdrücklich erlaubte Origins | `LARA_ALLOWED_ORIGINS`, Standard: keine |
| Größenlimit | Anfragen über 1 MiB → HTTP 413 | – |
| Keine internen Details | Verbindungsfehler melden nur „Modellserver nicht erreichbar“ | – |

Die Schnittstelle für das Frontend bleibt gleich: `GET /v1/models?ollamaUrl=…`, `POST /api/chat` und `POST /v1/chat/completions` mit dem Feld `ollamaUrl`.

**Wichtig:** Wer im Browser noch die alte Adresse `http://10.179.130.230:12434` gespeichert hat, bekommt jetzt HTTP 403. In den Einstellungen `http://127.0.0.1:12434` eintragen, das ist die auf dem Pixel bestätigte Adresse. Oder die gewünschte Adresse bewusst in `LARA_ALLOWED_UPSTREAMS` aufnehmen.

## Tests

`npm test` führt `tests/index.test.js` aus (Node-Testrunner, keine Abhängigkeiten). Die CI läuft im Workflow „Node server tests“.

## Grenzen

- Der Server hat **keine Anmeldung**. Er ist nur für die lokale Nutzung gedacht. Wird `LARA_BIND_HOST` auf eine Netzadresse gesetzt, kann jedes Gerät in diesem Netz den erlaubten Modellserver über ihn nutzen.
- Verbindungen zum Upstream laufen per HTTP, wie im Ziel eingetragen. Für Loopback ist das in Ordnung.
