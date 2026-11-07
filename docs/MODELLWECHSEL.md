# LARA Core – Verlustfreier Modellwechsel (Schritt 2b)

## Ablauf

1. **Paket erzeugen:** `POST /v1/jobs/{id}/handoffs` mit `from_model`, `to_model` und optionalen Listen. LARA baut das Paket aus dem **gespeicherten** Stand, nicht aus dem Chatverlauf:
   - ursprünglicher Auftrag sowie Ziel und Anforderungen
   - erledigte Schritte mit Ergebnis, Fortschritt (gesamt und je Phase)
   - offene Aufgaben, aktueller und nächster Schritt, bekannte Fehler, Tokenverbrauch
   - optional: `decisions`, `important_files`, `tools`, `test_results`, `open_questions`, `sources`, `context_notes`
   
   Das Paket bekommt eine Versionsnummer und eine **SHA-256-Prüfsumme**. Ein neues Paket ersetzt ältere, noch nicht bestätigte Pakete.
2. **Prompt für das neue Modell:** Die Antwort enthält `prompt`. Das neue Modell soll damit **nur** ein JSON-Objekt mit `job_id`, `current_step_id`, `next_step_id`, `checksum` und `goal_summary` zurückgeben.
3. **Prüfung:** `POST /v1/handoffs/{id}/confirm` mit `reply_text` (Rohantwort, auch mit Codeblock) oder `reply` (Objekt). Bestätigt wird **nur**, wenn
   - das gespeicherte Paket unverändert ist (Prüfsumme stimmt),
   - der Arbeitsstand sich seit dem Paket nicht geändert hat,
   - `job_id`, beide Schritt-IDs und `checksum` **exakt** übereinstimmen.
   
   Andernfalls wird die Übergabe mit der Liste der Probleme abgelehnt, und der Auftrag wird **nicht** freigegeben.
4. **Fortsetzen:** Erst nach der Bestätigung zeigt der Auftrag `active_model` = neues Modell. Gearbeitet wird am gespeicherten Schritt weiter. Nichts wird gelöscht, auch alte und abgelehnte Pakete bleiben gespeichert.

**Abnahmekriterium:** Dass das neue Modell irgendetwas antwortet („Verstanden“), zählt nie als Erfolg. Das ist getestet.

## Grenzen (ehrlich)

- Die Prüfung belegt, dass der Arbeitsstand **vollständig und unverfälscht** angekommen ist und vom Modell korrekt wiedergegeben wird. Ob das Modell die Aufgabe **inhaltlich gut** weiterführt, belegt sie nicht. Dafür sind Tests und die Abschlussprüfung da.
- Das Wechseln selbst ist noch nicht automatisch. LARA ruft das neue Modell noch nicht selbst auf, weil das Gateway (Schritt 3) fehlt. Die Schwellen aus `docs/AUFTRAGSSPEICHER.md` melden, **wann** gewechselt werden sollte. Auslösen und Bestätigen läuft bis dahin über die Schnittstelle.
- Kleine lokale Modelle (z. B. 0,5B) scheitern voraussichtlich öfter an der strengen JSON-Bestätigung. Dann wird abgelehnt und pausiert, statt mit falschem Stand weiterzuarbeiten.

## Abnahme

| Test | Stand |
|---|---|
| 5 Übergabepaket erzeugen | PASS (Unit-Test, alle Bestandteile, Prüfsumme) |
| 6 An anderes Modell übergeben | PASS (Unit-Test, über Schnittstelle). Echter Modellaufruf: NOT TESTED |
| 7 Bestätigung prüfen | PASS (richtig → bestätigt; jedes falsche Feld, Freitext, veraltetes oder manipuliertes Paket → abgelehnt) |
| 8 Am letzten Schritt fortsetzen | PASS (Unit-Test) |
| 10 Nichts geht verloren | PASS (Unit-Test, inkl. Neustart) |
