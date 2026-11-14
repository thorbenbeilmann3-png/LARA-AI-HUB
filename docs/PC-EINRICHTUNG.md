# LARA am PC – Zielbild „vollwertig eingerichtet mit einem Doppelklick"

Stand: 14.11.2026. **Entwurf/Mockup** – noch keine lauffähige PC-Software.
Mockup der Oberfläche: [pc-hub-mockup.svg](pc-hub-mockup.svg)

## Ziel

Ein PC, auf dem LARA komplett eingerichtet ist, ohne dass etwas von Hand
konfiguriert werden muss: ein Doppelklick auf `EINRICHTEN.bat` richtet alles ein
und startet den Hub im Browser (`localhost`).

## Ablauf (Vision)

1. **Doppelklick `EINRICHTEN.bat`** – prüft Python und den lokalen KI-Server
   (z. B. Ollama auf `127.0.0.1:11434`), installiert Fehlendes, startet den Server.
2. **LARA PC-Hub öffnet sich** im Browser auf `localhost` – Chat mit der lokalen KI,
   ohne Konto und ohne Internet.
3. **NAS automatisch erkennen** (IP + Freigaben im LAN) oder manuell eintragen –
   nur SMB im lokalen Netz, kein öffentlicher Zugriff.
4. **Game-Zentrale scannt** die installierten Bibliotheken (Steam, Epic, GOG)
   und zeigt Spiele samt Spielständen (die auf dem NAS liegen).
5. **Handy-App verbindet sich** über das LAN mit dem PC – laut
   `PROJECT-STATUS.md` gesperrt, bis die Sicherheitstests auf dem Gerät
   bestanden sind.

## Aktueller Stand

- LARA AI HUB ist **Android-only** (natives Kotlin/Compose); „LARA für Windows“
  steht in `PROJECT-STATUS.md` unter „Nicht implementiert“.
- Die PC-Seite ist das eigene Repo `PC-Tweaker` (v0.1.0: read-only Systemübersicht,
  `STARTEN.bat`/`BAUEN_EXE.bat`). `EINRICHTEN.bat` ist dort als nächster Schritt
  geplant.
- Dieses Mockup zeigt nur, wie die fertige Oberfläche aussehen soll.

## Sicherheit

- Alles läuft auf `localhost`/LAN – nichts wird öffentlich erreichbar gemacht.
- Keine API-Schlüssel im Klartext in Dateien oder Berichten; Schlüssel gehören
  nur in die App bzw. den Passwort-Manager.
