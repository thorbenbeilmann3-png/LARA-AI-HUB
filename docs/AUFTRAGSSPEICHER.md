# LARA Core – Auftragsspeicher, Fortschritt und Token-Überwachung (Schritt 2a)

Der Arbeitsstand liegt **nicht** im Kontextfenster einer KI, sondern dauerhaft in SQLite (`LARA_DB_PATH`, Standard `python-worker/lara.db`, nicht in Git). Jede Änderung wird sofort in einer eigenen Transaktion gespeichert. Schlägt sie fehl, wird alles zurückgerollt.

## Fortschritt

- Ein Auftrag besteht aus Schritten mit **Phase** und **Gewicht**.
- Fortschritt = Gewicht der **erledigten** Schritte ÷ Gesamtgewicht. Das gilt für den Gesamtwert und je Phase. Ein Schritt „in Bearbeitung“ zählt **nicht**. Es gibt keine geschätzten oder von Hand gesetzten Prozentwerte.
- Pro Auftrag ist nur ein Schritt gleichzeitig „in Bearbeitung“, damit keine widersprüchlichen Stände entstehen.
- Angezeigt werden: Gesamt- und Phasenprozent, erledigte und offene Schritte, aktueller und nächster Schritt, Fehler bzw. Blockaden, Zustand (aktiv/blockiert/abgeschlossen), Agent, Beginn und letzte Änderung. Jeder Auftrag wird getrennt geführt.
- Nach einem Neustart oder Absturz meldet `/v1/recovery` alle Aufträge mit unterbrochenem Schritt.

## Tokens und Kontext

- Jede Anfrage wird mit Anbieter, Modell, Eingabe- und Ausgabe-Tokens und der **Quelle** gespeichert: `api` (vom Anbieter gemeldet), `estimated` oder `unknown`. Bei `unknown` sind keine Zahlen erlaubt.
- **Kontextauslastung** = Tokens der **letzten** Anfrage ÷ Kontextfenster. **Gesamtverbrauch** = Summe aller Anfragen, die Grundlage für Kosten. Beides wird getrennt geliefert.
- Kontextfenster je Modell: Quelle `api` oder `user` (Nutzerangabe). Ist es unbekannt, sind Prozent und Status `unbekannt`. Es wird nichts geraten.
- Schwellen (einstellbar): `LARA_CONTEXT_WATCH`=70, `LARA_CONTEXT_PREPARE`=80, `LARA_CONTEXT_SWITCH`=90, daraus der Status `normal` / `beobachten` / `uebergabe_vorbereiten` / `wechsel_einleiten`.
- API-Guthaben ist mit normalen API-Schlüsseln nicht abrufbar und wird deshalb hier nicht geführt.

## Schnittstellen (alle mit `LARA_AUTOMATION_TOKEN`)

| Methode | Pfad | Zweck |
|---|---|---|
| POST | `/v1/jobs` | Auftrag mit Schritten anlegen |
| GET | `/v1/jobs`, `/v1/jobs/{id}` | Aufträge inkl. Fortschritt und Tokens |
| POST | `/v1/jobs/{id}/steps/{step_id}` | Schrittstatus setzen (`open`, `in_progress`, `done`, `failed`, `blocked`) |
| GET | `/v1/recovery` | Unterbrochene Arbeit nach einem Neustart |
| POST | `/v1/tokens` | Tokenverbrauch einer Anfrage melden |
| POST | `/v1/models/context-window` | Kontextfenster eines Modells hinterlegen |
| GET | `/v1/models/context?model=…` | Auslastung, Rest, Status, Gesamtverbrauch |

## Abnahme

| Abnahmetest aus dem Auftrag | Stand |
|---|---|
| 1 Auftrag starten, Fortschritt anzeigen | PASS (Unit-Test) |
| 2 Schritte abschließen, Prozent aktualisieren | PASS (Unit-Test) |
| 3 Tokenverbrauch erfassen | PASS (Unit-Test). Echte Werte vom Pixel-Server: NOT TESTED |
| 4 Warnung bei hoher Auslastung | PASS (Unit-Test) |
| 5–8 Übergabepaket, Modellwechsel, Bestätigung, Fortsetzen | NOT TESTED – noch nicht gebaut (Schritt 2b) |
| 9 Neustart simulieren, Stand wiederherstellen | PASS (Unit-Test, neue Verbindung zur selben Datei) |
| 10 Nichts geht verloren | teilweise: Rollback bei Fehlern getestet; vollständig erst mit 2b |

Noch nicht vorhanden: automatische Erfassung im Chat (die App meldet noch keine Tokens), App-Dashboard „KI- und Auftragsmonitor“, Übergabepaket und Modellwechsel.
