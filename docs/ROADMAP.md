# LARA AI HUB / ARENA – Roadmap

Stand: 2026-11-05

## Bestand (verifiziert)
- Native Android-App mit Chat, lokalem Postfach, ARENA- und PC-Tab.
- Chat spricht derzeit einen einzelnen OpenAI-kompatiblen Endpunkt an.
- GitHub Actions baut eine Debug-APK.
- ARENA-Oberfläche ist bislang nur ein Platzhalter.
- Gemini-Adapter, sichere Cloud-Key-Verwaltung, Python-Ausführung und Automationsengine sind noch nicht implementiert.

## Zielarchitektur
1. Android-App: Bedienoberfläche, Freigaben, Status und Ergebnisanzeige.
2. Python-Control-Plane: API-Router, Modellverzeichnis, Arena-Orchestrierung und Automationsplanung.
3. Provider-Adapter: Gemini API und Ollama zuerst; weitere Anbieter später.
4. Persistenz: zunächst lokale/Server-Datenbank mit Audit-Events.
5. Runner: isolierte Python-Aufgaben mit Zeit-, Speicher- und Netzwerkgrenzen.
6. GitHub: getrennte Repositories für Projekte, zentraler THORBEN-HQ-Index.

## Reihenfolge
- Phase A: Sicherungsbranch und Projektinventar.
- Phase B: Python-Dienst mit Healthcheck und Tests.
- Phase C: Gemini- und Ollama-Adapter mit Secrets ausschließlich serverseitig.
- Phase D: ARENA-Aufgaben, Vergleich und nachvollziehbare Wertung.
- Phase E: sichere Automationsdefinitionen und Freigabe-Gate.
- Phase F: Android-Anbindung und APK-Build.
- Phase G: unabhängige Prüfung, Gerätetest und Dokumentation.

## Sicherheitsgrenzen
- Kein beliebiger Shell-/Python-Code aus KI-Antworten wird automatisch ausgeführt.
- Keine Admin-Rechte als Standard.
- Keine Aktionen mit Löschen, Installieren, Kauf, Nachrichtenversand oder externen Änderungen ohne ausdrückliche Bestätigung.
- API-Schlüssel werden nie in APKs eingebettet.
- Ollama-Endpunkte dürfen nicht ungeschützt im öffentlichen Internet erreichbar sein.

## Abnahmekriterien
- Unit-Tests grün.
- Healthcheck meldet fehlende Provider-Konfiguration korrekt.
- Gemini-Anfrage nur mit gültigem serverseitigem Secret.
- Ollama-Anfrage nur gegen konfigurierten Host.
- ARENA startet nur mit mindestens zwei tatsächlich erreichbaren Modellen.
- Automation führt nur registrierte, freigegebene Aktionen aus.
- Android-Build erfolgreich; Installierbarkeit auf Pixel separat nachzuweisen.
