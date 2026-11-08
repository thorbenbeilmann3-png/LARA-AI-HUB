# Android-App – unverschlüsseltes HTTP

## Vorher

`android:usesCleartextTraffic="true"` erlaubte unverschlüsseltes HTTP zu **jedem** Ziel, auch über WLAN und Internet. Bei OpenClaw ging dabei im LAN ein Gateway-Token im Klartext über das Netz.

## Jetzt

- `app/src/main/res/xml/network_security_config.xml` (im Manifest über `android:networkSecurityConfig`):
  - Standard: **kein** Klartext-HTTP (`cleartextTrafficPermitted="false"`), nur System-CAs.
  - Ausnahme: `127.0.0.1` und `localhost`, also Dienste auf dem Handy selbst, z. B. der lokale Modellserver `127.0.0.1:12434` und die Python-Bridge `127.0.0.1:8765`.
- Zusätzlich prüft `ApiConnection.isCleartextAllowed()` jede Adresse vor der Anfrage. So bekommst du eine verständliche deutsche Meldung statt des OkHttp-Fehlers „CLEARTEXT communication … not permitted“.
- Der Standard-Endpunkt ist jetzt `http://127.0.0.1:12434/`, die auf dem Pixel bestätigte Adresse. Vorher stand dort `http://10.179.130.230:12434/`.

Grundlage ist die offizielle Doku: https://developer.android.com/privacy-and-security/security-config. Dort ist `domain-config` mit numerischen Loopback-Adressen wie `127.0.0.1` ausdrücklich beschrieben. Ab Android 9 ist Klartext ohnehin standardmäßig aus.

## Folgen

| Fall | Ergebnis |
|---|---|
| Lokales Modell `http://127.0.0.1:12434` | erlaubt |
| Gespeicherte alte Adresse `http://10.179.130.230:12434` | abgewiesen mit Hinweis. In den Einstellungen `http://127.0.0.1:12434` eintragen |
| OpenClaw/eigener Server im WLAN per `http://192.168.…` | **abgewiesen.** Dafür HTTPS verwenden |
| Cloud-Anbieter (OpenAI, Anthropic, Gemini …) | unverändert per HTTPS |
| Automate, Pydroid, Browser | nicht betroffen (andere Apps) |

## Später: Verbindung zum PC

Für den PC ist ein VPN (z. B. Tailscale/WireGuard) geplant. Auch dann wird keine pauschale Klartext-Ausnahme eingetragen. Bevorzugt ist HTTPS über das VPN. Falls nötig, wird gezielt nur die eine VPN-Adresse des PCs ergänzt, und das erst nach Freigabe.

## Prüfung

- CI-Build (`assembleDebug`) muss grün sein.
- **Noch nicht auf dem Pixel getestet:** Chat mit `127.0.0.1:12434` muss weiter funktionieren, und `http://10.179.130.230:12434` muss die neue Meldung zeigen.
