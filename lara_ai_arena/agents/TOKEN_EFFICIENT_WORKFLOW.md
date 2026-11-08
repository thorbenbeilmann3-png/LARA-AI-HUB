# Token-sparender Arbeitsablauf

## Zweck
Die KI-Arena soll Recherche und Entwicklung durchführen, ohne unnötig Tokens oder kostenpflichtige API-Aufrufe zu verbrauchen.

## Regeln
- Zuerst Suchmaschinen, Anbieter-Metadaten, GitHub, offizielle Modellkarten und lokale Skripte nutzen.
- Nur neue oder geänderte Datensätze erneut recherchieren.
- Kandidaten früh aussortieren, wenn Login, Zahlung, API-Key oder unklare Modellidentität zwingend sind.
- Ein kompakter Datensatz pro Modell; keine wiederholten langen Beschreibungen.
- Prompts pro Kategorie einmal festlegen und versionieren.
- Erst wenige Kandidaten tatsächlich testen; nach erfolgreicher Methode stufenweise erweitern.
- Ergebnisse lokal/strukturiert speichern und bei Folgeläufen nur Differenzen auswerten.
- LLM-Agenten nur für klar definierte Aufgaben mit Eingabe-/Ausgabelimit starten. Keine Agenten dauerhaft laufen lassen.
- Subagenten sind nicht automatisch tokenfrei. Externe LLMs können Kosten verursachen; lokale Modelle verbrauchen lokale Rechenressourcen.
- Keine bezahlten API-Aufrufe ohne ausdrückliche Zustimmung.
- Tokenverbrauch nur als gemessenen Wert ausweisen, wenn die Quelle ihn tatsächlich liefert.

## Reihenfolge
1. Kandidaten-Suche (nicht-LLM)
2. Duplikate entfernen
3. Login-/Kosten-/Modellidentität prüfen
4. Nur qualifizierte Kandidaten testen
5. Ergebnisse speichern
6. Claude erhält nur freigegebene, kompakte Ergebnisse zur Integration
