# FreeLLMAPI in LARA

Stand: 09.11.2026. Status: **integriert, ohne HTTPS im LAN blockiert.** Am Gerät getestet: NEIN.

## So verhält sich LARA

| Punkt | Regel |
|---|---|
| Anbieter | „FreeLLMAPI“ unter Tools, OpenAI-kompatible Schnittstelle |
| Standard-Adresse | `http://192.168.178.21:3001/v1`, frei änderbar |
| Verbindungstest („Verbinden / Modelle laden“) | nur `GET /v1/models`, keine Nachricht, keine Kosten |
| API-Schlüssel | optional, ausschließlich im ApiKeyStore (Android Keystore, AES-GCM) |
| HTTP | nur zu `127.0.0.1` und `localhost`. Keine Ausnahme fürs Heimnetz (Beschluss von Thorben, 09.11.2026) |

## Was der Beschluss bedeutet

Die Standard-Adresse ist eine `http://`-Adresse im Heimnetz. LARA blockiert sie deshalb mit der bestehenden Meldung: „Unverschlüsseltes HTTP ist nur für Dienste auf diesem Handy erlaubt …“. Das ist so gewollt. **FreeLLMAPI auf einem anderen Gerät ist erst mit HTTPS nutzbar.** Bis dahin geht nur FreeLLMAPI auf dem Handy selbst (`http://127.0.0.1:3001/v1`, nicht getestet). Dort darf auch der Schlüssel über HTTP laufen, weil er das Handy nicht verlässt.

Laut README brauchen alle `/v1/*`-Routen den „Unified Key“ von FreeLLMAPI ([README](https://github.com/vi70x4/freellmapi)). In LARA ist der Schlüssel optional, ohne ihn kommt dann vermutlich HTTP 401. Nicht geprüft.

## FreeLLMAPI mit HTTPS absichern

**Vorab:** LARA vertraut nur den vorinstallierten Zertifizierungsstellen (`network_security_config.xml`, [Android](https://developer.android.com/privacy-and-security/security-config)). Ein selbst signiertes Zertifikat oder eine lokale CA (zum Beispiel Caddys interne CA, [Caddy](https://caddyserver.com/docs/automatic-https)) reicht deshalb nicht, auch nicht als Nutzer-Zertifikat auf dem Handy. Das Zertifikat muss öffentlich vertrauenswürdig sein und zum Namen in der Adresse passen, eine reine IP-Adresse scheidet damit praktisch aus.

Das README nennt TLS am Reverse-Proxy (nginx), eine eingebaute HTTPS-Funktion beschreibt es nicht.

**Weg A: Tailscale, ohne eigene Domain**

- Tailscale auf dem FreeLLMAPI-Rechner und dem Pixel, beide im selben Tailnet.
- In der Admin-Konsole MagicDNS und „Enable HTTPS“ einschalten. Achtung: Gerätenamen und Tailnet-Name stehen danach im öffentlichen Certificate-Transparency-Log. Die Zertifikate kommen von Let's Encrypt ([Tailscale](https://tailscale.com/docs/how-to/set-up-https-certificates)).
- Auf dem FreeLLMAPI-Rechner die dokumentierte Form mit Port 3001: `tailscale serve localhost:3001` ([Tailscale](https://tailscale.com/docs/reference/tailscale-cli/serve)).
- In LARA eintragen: `https://<gerät>.<tailnet>.ts.net/v1`.

**Weg B: Caddy mit eigener Domain**

- Du brauchst eine eigene Domain, deren Name im Heimnetz auf den FreeLLMAPI-Rechner zeigt.
- Zertifikat über die DNS-Challenge: Sie braucht keine offenen Ports, aber ein Caddy-Modul für deinen DNS-Anbieter und dessen Zugangsdaten ([Caddy](https://caddyserver.com/docs/automatic-https)).
- Die dokumentierte Form für den Proxy ist `caddy reverse-proxy --from <domain> --to :3001` ([Caddy](https://caddyserver.com/docs/quick-starts/reverse-proxy)). Für die DNS-Challenge braucht es stattdessen eine Caddyfile-Konfiguration mit dem Modul.
- Die normale HTTP-Challenge verlangt von außen offene Ports 80 und 443. Das passt nicht zu einem Heimnetz-Dienst.

**In beiden Fällen**

- Nicht ins Internet stellen. Das README sagt ausdrücklich: „don't expose it to the internet“.
- Danach in LARA die HTTPS-Adresse eintragen, den Schlüssel speichern und „Verbinden / Modelle laden“ drücken.
- Für die Bedingungen der einzelnen KI-Anbieter bleibt laut README der Nutzer verantwortlich.

Beide Wege sind aus den Herstellerseiten zusammengestellt und von mir nicht ausprobiert.

## Eingaben merken

LARA merkt sich pro Anbieter die zuletzt genutzte Adresse und das Modell, beim Alpine-Verbindungstest auch die MCP-Adresse, und trägt sie beim nächsten Mal wieder ein. Gespeichert wird in den normalen App-Einstellungen (`lara_provider_memory`), nie ein Schlüssel. Was wie ein Schlüssel aussieht oder eine Adresse mit Zugangsdaten ist, wird nicht gespeichert. Die Erkennung arbeitet mit Mustern und fängt übliche Formate ab, nicht jedes denkbare Geheimnis.

## Teststand

| Prüfung | Stand |
|---|---|
| Unit-Tests `FreeLlmApiProviderTest`, `ProviderMemoryTest` | siehe `PROJECT-STATUS.md` |
| Echter FreeLLMAPI-Server, HTTPS-Wege, Anzeige in der App | NICHT GETESTET |
