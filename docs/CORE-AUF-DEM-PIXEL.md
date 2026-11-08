# LARA Core auf dem Pixel starten (Pydroid 3)

**Status:** Unter Linux mit Python 3.13 getestet (Start, `/health`, 401 ohne Token, 200 mit Token, Token-Dateien mit Rechten `0600`). **Auf dem Pixel/Pydroid NOT TESTED.** Ob Pydroid alle Pakete installieren kann, ist unbekannt (siehe Risiken).

## Einmalig einrichten

1. Den Ordner `python-worker/` aus dem Repository auf das Handy kopieren, z. B. nach `Download/LARA/python-worker/`.
2. In Pydroid 3: **Menü → Pip** und nacheinander installieren: `fastapi`, `uvicorn`, `httpx`, `pydantic`.
3. In Pydroid `python-worker/run_core.py` öffnen und starten (▶).
4. Beim **ersten** Start erscheinen zwei Zeilen `NEU AUTOMATION_TOKEN=…` und `NEU APPROVER_TOKEN=…`.
   - Den **AUTOMATION_TOKEN** in LARA eintragen: **Monitor → Core-Token → „Core-Token sicher speichern“**. LARA fragt dabei nach Fingerabdruck oder PIN.
   - Den **APPROVER_TOKEN** brauchst du erst, wenn Freigaben in der App gebaut sind. Er liegt in Pydroid unter `~/.lara_core/approver_token`.
   - Keine Screenshots davon machen und die Tokens nicht weitergeben. Sie werden später **nicht** erneut angezeigt.
5. In LARA: **Monitor → Core-Adresse** `http://127.0.0.1:8000` → **„Aufträge laden“**. Erwartet wird „0 Auftrag/Aufträge geladen“.

## Wichtig

- Der Core lauscht nur auf `127.0.0.1` und ist damit nicht aus dem WLAN erreichbar.
- **Pydroid führt in der Regel nur ein Programm gleichzeitig aus.** Läuft schon die Python-Bridge (`lara_bridge.py`, Port 8765), wird sie beim Start des Core vermutlich beendet. Bridge und Core laufen nicht gleichzeitig, bis sie zusammengelegt sind. Das ist nicht geprüft und hängt von Pydroid ab.
- Android kann Pydroid im Hintergrund beenden. Ist der Core nicht erreichbar, zeigt LARA „Core nicht erreichbar“. Dann Pydroid öffnen und `run_core.py` erneut starten. Die Daten bleiben in `~/.lara_core/lara.db` erhalten.
- Token neu erzeugen: Core stoppen, die Datei `~/.lara_core/automation_token` löschen, neu starten und den neuen Token in LARA speichern.

## Risiken

- `pydantic` benötigt `pydantic-core`, eine kompilierte Erweiterung. Ob Pydroid dafür ein passendes Paket anbietet, ist **ungeprüft**. Schlägt die Installation fehl, den Fehlertext an Claude geben. Ausweg wäre der Betrieb auf dem PC, sobald die Verbindung über das VPN steht.
