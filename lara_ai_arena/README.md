# LARA AI Arena – gemeinsamer Arbeitsbereich

Status: OFFEN – Recherche und Turnierplanung. Noch keine App-Integration.

## Zweck
Dieser Ordner ist die gemeinsame, versionierte Arbeitsfläche für ChatGPT und Claude Code. Beide dürfen relevante Ergebnisse ergänzen und daraus Aufgaben übernehmen. Vor jeder Änderung ist der aktuelle Branch- und Dateiinhalt zu lesen; Änderungen werden angehängt oder gezielt minimal vorgenommen.

## Verbindliche Nutzeranforderungen
- Getrennte Wettbewerbe nach Kategorien: Text, Logik/Mathematik, Programmierung, Recherche/Quellen, Bild, Audio/Sprache und Video.
- Ausschließlich kostenlos und ohne Anmeldung tatsächlich testbare Angebote dürfen am Turnier teilnehmen.
- Ziel: bis zu 60 unterschiedliche Modelle; 60 ist eine Obergrenze, keine künstliche Quote.
- ChatGPT recherchiert, qualifiziert und führt die erste Bewertungsrunde durch. Erst nach dokumentierter Auswahl erhält Claude den Integrationsauftrag.
- Dieser Arbeitsbereich soll für ChatGPT, Claude Code und später verfügbare spezialisierte Subagenten nutzbar sein.
- Tokenverbrauch soll langsam und sparsam erfolgen. Qualität und Nachprüfbarkeit dürfen darunter nicht leiden.
- Bestehende LARA-Funktionen bleiben erhalten; Erweiterungen erfolgen rückwärtskompatibel.

## Token- und Kostenregeln
1. Keine dauerhaften oder häufigen LLM-Agenten-Schleifen. Agenten nur für klar abgegrenzte Aufgaben starten und danach beenden.
2. Erst günstige, nicht-LLM-Verfahren nutzen: Web-/GitHub-Suche, offizielle Modellkataloge, Metadaten, lokale Skripte, Parser, Hashes, Duplikaterkennung und automatisierte Tests.
3. Quellen und bereits geprüfte Ergebnisse zwischenspeichern; nur Änderungen seit dem letzten Prüfdatum erneut untersuchen.
4. Keine vollständigen Repository-Dumps oder langen Chatverläufe in Prompts laden. Nur relevante Dateien, Ausschnitte und kompakte Übergaben bereitstellen.
5. Modelle zunächst anhand von Metadaten qualifizieren. Teure bzw. tokenintensive Antwortvergleiche erst für die engere Auswahl durchführen.
6. Gleiche Testprompts und Bewertungsschemata wiederverwenden; Ergebnisse strukturiert speichern, nicht erneut generieren.
7. Batch-Aufgaben bündeln, aber keine unnötigen parallelen Modellaufrufe auslösen.
8. Lokale Skripte und lokale Modelle können externe API-Kosten vermeiden, verbrauchen aber weiterhin Rechenleistung und ggf. lokale Tokens.
9. Keine kostenpflichtigen API-Aufrufe ohne ausdrückliche Freigabe.
10. Verbrauch soweit messbar dokumentieren: Provider, Modell, Input-/Output-Tokens, Kosten, Datum. Nicht verfügbare Werte als unbekannt kennzeichnen, niemals schätzen und als Messwert ausgeben.

## Zugangs- und Testregeln
- Anmeldung, Zahlung oder API-Schlüssel als Voraussetzung schließen einen Kandidaten vom Hauptturnier aus.
- Eine Plattform darf nicht mit einem Modell gleichgesetzt werden. Modellname und Version müssen identifizierbar sein.
- Anbieterbehauptungen sind keine Testergebnisse. Status klar trennen: UNGEPRÜFT, ZUGANG GEPRÜFT, GETESTET, NICHT ZUGÄNGLICH, AUSGESCHIEDEN.
- Keine Botschutz-Umgehung, versteckten APIs oder unzulässige Automatisierung. Bei manueller Prüfung die Grenzen dokumentieren.
- Ein fehlgeschlagener Zugang ist kein Qualitätsverlust des Modells.
- Lizenzen und lokale Nutzbarkeit getrennt vom anonymen Webzugang prüfen.

## Verzeichnisübersicht
- `registry/`: Kandidatenregister und Plattformen
- `tournaments/`: Testaufgaben, Läufe und archivierte Ergebnisse
- `agents/`: Rollen, Agentenaufträge und kompakte Arbeitsanweisungen
- `reports/`: Rechercheberichte, Ranglisten und Monatsberichte
- `schemas/`: Daten- und Bewertungsformate

## Reihenfolge
1. ChatGPT recherchiert und qualifiziert Kandidaten.
2. ChatGPT testet nur tatsächlich zugängliche Modelle und dokumentiert die Grenzen.
3. ChatGPT erstellt eine geprüfte Auswahl und einen kompakten Integrationsauftrag.
4. Claude prüft die Auswahl, baut nach Freigabe die Bibliothek/Integration.
5. ChatGPT kontrolliert die Änderungen unabhängig.

## Agenten dürfen
- aus diesem Ordner passende, klar abgegrenzte Aufgaben beziehen;
- Ergebnisse, Quellen, Testläufe und Fehler dokumentieren;
- keine kostenpflichtigen Aufrufe, Geheimnisse, Releases, Merges oder destruktiven Änderungen ohne gesonderte Freigabe ausführen.
