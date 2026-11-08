# Gemeinsame Agenten-Aufträge

## Rollen
- Koordinator: ChatGPT – Recherche, Auswahl, Planung und Schlussprüfung.
- Implementierer: Claude Code – erst nach dokumentierter Auswahl und passender Freigabe Integration.
- Prüfer: unabhängig arbeitende Review-Rolle; keine Selbstfreigabe.

## Aufgabenformat
Jeder Auftrag enthält: ID, Ziel, Eingaben/Quellen, erlaubte Pfade, konkrete Einzelschritte, Akzeptanzkriterien, Testvorgaben, Kosten-/Tokenlimit (falls bekannt), Status und Ergebnisdateien.

## Token-Sparregeln
- Agenten nur bei konkretem Bedarf starten; keine Leerlauf- oder Polling-Schleifen.
- Aufgaben klein und unabhängig halten.
- Übergaben als kurze Zusammenfassung plus Dateipfade/Commit-SHA statt vollständiger Gesprächsverläufe.
- Rechercheergebnisse einmal speichern und wiederverwenden.
- Kein Agent darf behaupten, tokenfrei zu arbeiten. Lokale Agenten können API-Kosten vermeiden, benötigen aber Rechenressourcen.
- Unbekannte Tokenkosten klar als unbekannt kennzeichnen.

## Status
OFFEN / ANNAHME / FEHLER. Fehlende wesentliche Angaben zusätzlich BLOCKIERT kennzeichnen.
