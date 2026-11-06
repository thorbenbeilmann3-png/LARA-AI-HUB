# LARA AI HUB / ARENA – Roadmap

Stand: 2026-11-06

## Bestand (Code geprüft; Laufzeit noch nicht vollständig geprüft)
- Native Android-App mit Chat, lokalem Postfach, ARENA- und PC-Tab.
- API-Verbindung unterstützt native Ollama-Endpunkte und OpenAI-kompatible Endpunkte.
- Modell-Erkennung über `/api/tags` bzw. `/v1/models`; Chat über `/api/chat` bzw. `/v1/chat/completions`.
- API-Schlüssel werden mit AES-GCM und Android Keystore gespeichert; Schlüsselübertragung ist auf HTTPS beschränkt.
- Android-App-Backups sind deaktiviert.
- Automate-Anbindung kann einen installationsgebundenen Flow aufrufen; optionaler Aufruf beim LARA-Start ist vorhanden. Der Flow selbst muss in Automate angelegt und mit der installierten LLM-App verbunden werden.
- ARENA zeigt den geplanten Ablauf mit bis zu 30 Teilnehmerplätzen; Modellverwaltung, parallele Aufrufe, anonyme Bewertung und Turnierlogik sind noch nicht implementiert.
- Ein GitHub-Actions-Workflow für eine Debug-APK wurde ergänzt; erfolgreicher Build und APK-Artefakt sind noch nicht bestätigt.
- Python-Control-Plane enthält einen Starter für Gemini und Ollama. OpenAI-/Claude-Adapter, sichere Provider-Verwaltung und Automationsausführung fehlen noch.

## Zielarchitektur
1. Android-App: Bedienoberfläche, Freigaben, Status und Ergebnisanzeige.
2. Python-Control-Plane auf Handy/PC/Home-Server: API-Router, Modellverzeichnis, Arena-Orchestrierung und Automationsplanung.
3. Provider-Adapter: Ollama, OpenAI/ChatGPT, Anthropic/Claude und Gemini; weitere Anbieter später.
4. Persistenz: zunächst lokale/Server-Datenbank mit Audit-Events.
5. Runner: isolierte Python-Aufgaben mit Zeit-, Speicher- und Netzwerkgrenzen.
6. GitHub: getrennte Repositories für Projekte, zentraler THORBEN-HQ-Index.

## Reihenfolge
- Phase A: Sicherungsbranch und Projektinventar. (erledigt)
- Phase B: Android-Ollama-Verbindung, Modell-Erkennung, Schlüsselablage und Automate-Handoff. (Code umgesetzt; Build und Gerätetest offen)
- Phase C: Python-Dienst mit Healthcheck und Tests; Provider-Adapter für OpenAI/Claude/Gemini/Ollama.
- Phase D: ARENA-Aufgaben, bis zu 30 Teilnehmer, anonymer Vergleich, Qualifikation/Halbfinale/Finale, nachvollziehbare Wertung und Kosten.
- Phase E: sichere Automationsdefinitionen, Python-Runner und Freigabe-Gate.
- Phase F: Android-Anbindung, Geräte-/Dateisynchronisierung und PC/Home-Server-Verbindung.
- Phase G: unabhängige Prüfung, APK-Build, Installation auf Pixel 9a und Dokumentation.

## Sicherheitsgrenzen
- Kein beliebiger Shell-/Python-Code aus KI-Antworten wird automatisch ausgeführt.
- Keine Admin-Rechte als Standard.
- Keine Aktionen mit Löschen, Installieren, Kauf, Nachrichtenversand oder externen Änderungen ohne ausdrückliche Bestätigung.
- API-Schlüssel werden nie in APKs eingebettet oder in Logs geschrieben.
- API-Schlüssel werden in der Android-App nur über HTTPS übertragen.
- Ollama-Endpunkte dürfen nicht ungeschützt im öffentlichen Internet erreichbar sein.

## Abnahmekriterien
- Unit-Tests grün.
- Healthcheck meldet fehlende Provider-Konfiguration korrekt.
- OpenAI-/Claude-/Gemini-Anfragen nur mit gültigen, sicher verwalteten Secrets.
- Ollama-Anfrage nur gegen konfigurierten Host.
- ARENA startet nur mit mindestens zwei tatsächlich erreichbaren Modellen.
- Arena-Ergebnisse, Bewertung, Gewinner und Kosten werden nachvollziehbar gespeichert.
- Automation führt nur registrierte, freigegebene Aktionen aus.
- Android-Build erfolgreich; Installierbarkeit auf Pixel 9a separat nachzuweisen.
