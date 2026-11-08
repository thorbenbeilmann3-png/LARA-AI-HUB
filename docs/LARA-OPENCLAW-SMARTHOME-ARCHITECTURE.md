# LARA HUB – OpenClaw-Architektur und Smart-Home-Integration

Stand: 08.11.2026  
Status: Architekturentscheidung / Spezifikation – **noch keine implementierte oder getestete Funktion**

## 1. Zielbild

LARA AI HUB bleibt das eigenständige zentrale Bedien- und Steuerungssystem. Es übernimmt das **OpenClaw-Prinzip**: Der Nutzer spricht mit LARA über vertraute Kanäle; ein lokaler LARA-Agent koordiniert Aufgaben; je nach Aufgabe werden lokale oder Cloud-LLMs als austauschbare Denkmodelle verwendet; freigegebene Werkzeuge führen Aktionen aus; ein kontrollierter Speicher hält ausdrücklich dafür vorgesehene Informationen und Aufgabenstände fest.

LARA soll OpenClaw nicht einfach kopieren oder dessen Identität übernehmen. LARA behält die bereits geplante Oberfläche, Namen, Navigation, ARENA und die vorhandenen Ollama-Funktionen. OpenClaw wird als optionale, klar abgegrenzte Agenten-/Gateway-Integration betrachtet, sofern die technische Prüfung und Lizenz-/Sicherheitsprüfung dies bestätigen.

## 2. Zielarchitektur

```text
Android / Web / erlaubte Chat-Kanäle
                 |
                 v
          LARA AI HUB (UI)
                 |
       Authentifizierung & Profile
                 |
                 v
       LARA Orchestrator / Task-Agent
          |          |          |
          |          |          +--> Gedächtnis / Aufgabenstatus
          |          |
          |          +--> Modell-Router
          |                 |--> lokale Ollama-Modelle
          |                 |--> freigegebene Cloud-Modelle
          |
          +--> Tool-/Skill-Gateway
                    |--> OpenClaw Gateway (optional)
                    |--> PC-Agent (Windows, später)
                    |--> GitHub / freigegebene Integrationen
                    |--> LARA Smart Home (separates Produkt)
```

### Verantwortlichkeiten

- **LARA AI HUB:** zentrale Oberfläche, Nutzerkonten/Profile, Aufgabenannahme, Plan, Modellwahl, Freigaben, Verlauf und Status.
- **LARA Orchestrator:** zerlegt Aufgaben, wählt geeignete Modelle und Werkzeuge, überwacht Ergebnisse und dokumentiert Übergaben.
- **LLM-Anbieter:** liefern Schlussfolgerungen/Text; sie erhalten nicht automatisch lokale Ausführungsrechte.
- **Lokaler Agent / PC-Agent:** führt nur ausdrücklich erlaubte, eng begrenzte lokale Aktionen aus.
- **OpenClaw-Adapter:** optionale Verbindung zu einem OpenClaw Gateway. Gateway-Adresse und Gateway-Token sind getrennt von Modell-API-Schlüsseln zu speichern.
- **Skills/Plugins:** einzeln aktivierbare, versionierte Integrationen mit klaren Eingaben, Berechtigungen und Ergebnissen.

## 3. Agenten und Aufgabenablauf

Geplante Agentenprofile (keine Behauptung, dass sie bereits implementiert sind):

1. **Koordinator:** nimmt den Auftrag an, erstellt Plan und Aufgabenstatus, wählt Werkzeuge/Modelle und gibt das Endergebnis frei.
2. **Umsetzer:** bearbeitet genau einen abgegrenzten Arbeitsschritt.
3. **Prüfer:** kontrolliert Ergebnis unabhängig gegen Auftrag, Tests und Sicherheitsregeln.

Weitere Fachagenten dürfen später als Rollen/Skill-Konfigurationen hinzukommen. Sie erhalten keine pauschalen Systemrechte. Jeder Agent bekommt nur die für seinen Arbeitsschritt nötigen Werkzeuge.

## 4. Gedächtnis und Aufgabenpersistenz

- Aufgaben, Zwischenergebnisse, Quellen, Freigaben und Status sollen sitzungsübergreifend nachvollziehbar bleiben.
- Fakten, temporärer Arbeitskontext und sensible Zugangsdaten sind getrennte Datentypen.
- Zugangsdaten niemals im Klartext in Chatverlauf, GitHub, Logs oder Memory schreiben.
- Modellanbieter-Schlüssel und OpenClaw-Gateway-Zugangsdaten getrennt und verschlüsselt speichern.
- Nutzer müssen sehen können, welche Daten gespeichert sind, und eine kontrollierte Korrektur/Löschung anstoßen können.
- Kein stilles Hochladen lokaler Dateien oder persönlicher Inhalte an Cloud-Modelle.

## 5. Sicherheitsmodell für lokale Aktionen

- Keine beliebigen, ungeprüften KI-generierten Shell-, PowerShell- oder Python-Befehle ausführen.
- Aktionen laufen über typisierte, allowlist-basierte Tools mit validierten Parametern.
- Änderungen an Dateien, System, Konten, Geräten, Zahlungen oder externen Diensten erfordern eine passende explizite Freigabe.
- Read-only zuerst; Schreib-/Steuerrechte getrennt aktivieren.
- Protokollieren: aufrufender Nutzer/Agent, Werkzeug, Parameter (ohne Geheimnisse), Freigabe, Ergebnis und Zeit.
- Netzwerkzugriffe beschränken; keine ungeschützten öffentlich erreichbaren Agenten-Endpunkte.
- Not-Aus / Deaktivierung für lokale Agenten und Integrationen vorsehen.
- OpenClaw darf diese LARA-Sicherheitsregeln nicht umgehen.

## 6. Separates Produkt: LARA Smart Home

**LARA Smart Home ist eine eigenständige Smart-Home-App bzw. ein getrenntes Modul – nicht die LARA-Hub-Oberfläche selbst.** Sie erhält ihre eigene klare, hochwertige Bedienoberfläche und verwaltet Haus, Räume, Geräte, Sensoren, Bewohnerprofile und Automationen.

### Funktionen

- Frei benennbare Häuser, Etagen und Räume.
- Mehrere Bewohnerprofile mit Namen/Bild und Rollen (Administrator, Bewohner, Gast).
- Geräte und Sensoren hinzufügen, erkennen, Räumen zuordnen und ihren Verbindungsstatus anzeigen.
- Automationen nach Zeit, Wochentag, Temperatur, Bewegung, Tür/Fenster, Anwesenheit, Jahreszeit, Wetter und Sonnenauf-/untergang.
- Profile mit persönlichen Einstellungen sowie gemeinsame Hausregeln.
- Manuelle Übersteuerung, nachvollziehbare Historie, Fehler- und Offline-Verhalten.
- Bei Unsicherheit oder Sensorausfall sicherer Zustand statt unkontrolliertem Schalten.

### Verbindung zu LARA HUB / OpenClaw

- Smart Home stellt eine eng begrenzte, authentifizierte Integrationsschnittstelle bereit.
- LARA HUB und – falls freigegeben – OpenClaw können Smart-Home-Status lesen und erlaubte Aktionen auslösen.
- Beispielaktionen: `list_devices`, `get_device_state`, `get_sensor_reading`, `activate_automation`, `set_device_power`.
- Jede Aktion prüft Nutzerrolle, Geräterecht, Parameter, Zustand und Freigaberegel.
- Kritische Geräte (z. B. Heizgeräte) erhalten zusätzliche Grenzwerte, Laufzeitbegrenzung, Übertemperatur-/Frostschutz und manuelle Notabschaltung.
- Smart Home darf nicht beliebige Shell-Befehle oder allgemeine PC-Steuerung exponieren.

### Gosund / Tuya-Kompatibilität

- Keine pauschale Zusage, dass jedes Gosund-Modell kompatibel ist.
- Zuerst Modellnummer, Funkstandard und ggf. Gateway identifizieren.
- Bevorzugt offizielle Tuya-/Smart-App-SDK- bzw. Cloud-APIs und deren aktuelle Bedingungen prüfen.
- Lokale Steuerung nur für konkret verifizierte Gerätemodelle/Gateways zusagen.
- Keine Zugangsdaten aus der Gosund-App auslesen oder in der App ungeschützt speichern.
- Vor Produktivbetrieb Pairing, Status, Schalten, Offline-Fall und Wiederanlauf an echten Geräten testen.

## 7. Modell-Routing

- Aufgabenklassifizierung nach Komplexität, Datenschutz, Latenz und Kosten.
- Lokales Ollama-Modell bevorzugen, wenn Qualität und Ressourcen ausreichen.
- Cloud-Modell nur über ausdrücklich eingerichtete Anbieter und vorhandene Freigaben verwenden.
- Modellwahl und tatsächliches Modell im Verlauf sichtbar machen.
- Bei Modell-/Provider-Ausfall kontrolliert abbrechen oder ein zulässiges Ersatzmodell nutzen; niemals stillschweigend Geheimnisse übertragen.

## 8. Umsetzungsphasen und Abnahmekriterien

### Phase A – Architektur und Sicherheitsgrenzen
- Bestehende LARA-App, Server und Branch-Stand erneut prüfen.
- OpenClaw Gateway API, Authentifizierung, Berechtigungen, Lizenz und Betriebsanforderungen anhand offizieller Dokumentation verifizieren.
- Datenfluss- und Bedrohungsmodell erstellen.
- **Abnahme:** dokumentierte Schnittstellen und keine offenen kritischen Sicherheitsfragen.

### Phase B – OpenClaw-Verbindungsadapter (read-only zuerst)
- Gateway-Verbindung getrennt konfigurieren.
- Status/Health und erlaubte read-only Funktionen testen.
- Gateway-Token verschlüsselt speichern; nicht mit Modellschlüsseln vermischen.
- **Abnahme:** Verbindungstest, Authentifizierungsfehler, Timeout und Tokenwechsel getestet.

### Phase C – LARA-Orchestrator und Skills
- Aufgabenannahme, Plan, Status, Modell-Routing und begrenzte Tool-Aufrufe integrieren.
- Erst read-only Skills; Schreibaktionen erst nach Freigabefluss.
- **Abnahme:** reproduzierbare Tests für Rollen, Berechtigungen, Fehlerfälle und Audit-Log.

### Phase D – Smart-Home-App separat
- Eigenständige Android-Oberfläche und Datenmodell für Häuser, Räume, Bewohner, Geräte, Sensoren und Regeln.
- Gosund/Tuya-Adapter erst nach Modell- und API-Prüfung.
- **Abnahme:** echte Kopplung, Statusabfrage und Ein-/Ausschalten mit mindestens einem konkreten Gosund-Gerät; anschließend Sensor- und Automationsprüfung.

### Phase E – sichere Verbindung Smart Home ↔ LARA
- Eng begrenzte API/Skill-Schnittstelle und Nutzerrechte.
- OpenClaw kann nur freigegebene Smart-Home-Aktionen aufrufen.
- **Abnahme:** Zugriff ohne Berechtigung wird abgewiesen; jede Aktion wird protokolliert; Not-Aus funktioniert.

### Phase F – Ausbau
- weitere Kanäle, Agenten, Skills und Hersteller nur einzeln und testbasiert hinzufügen.
- Jede Erweiterung in einem kleinen, vollständig geprüften Paket.

## 9. Nicht-Ziele / Grenzen

- Kein ungeprüfter autonomer Vollzugriff auf Windows, Android oder Hausgeräte.
- Keine Ausführung beliebiger KI-generierter Befehle.
- Keine Behauptung, OpenClaw, Tuya oder Gosund seien bereits verbunden.
- Keine Veröffentlichung oder Zusammenführung mit `main` ohne separate Prüfung und Freigabe.
- Keine Löschung oder Überschreibung bestehender Funktionen.
- Keine kostenpflichtigen Dienste oder Cloud-Aufrufe ohne vorherige Zustimmung.

## 10. Aktueller Status

Diese Datei hält die Architekturentscheidung fest. Sie ist **Planung und Spezifikation**, keine Implementierung. Der vorhandene LARA-README-Stand beschreibt weiterhin ein Starterprojekt und listet mehrere Funktionen ausdrücklich als noch nicht implementiert. Vor Umsetzung ist der aktuelle Branch-HEAD erneut zu prüfen und pro Änderung nur ein abgegrenztes Paket zu bearbeiten.

## 11. Quellen / Referenzmaterial

- OpenClaw-Prinzipbild vom Nutzer im Chat bereitgestellt (08.11.2026); dient als konzeptionelle Referenz, nicht als technische API-Dokumentation.
- Gosund-App-Screenshot „Geräte und Dienste von Drittanbietern“ vom Nutzer bereitgestellt (08.11.2026); zeigt Alexa, Google und SmartThings als Integrationsoptionen, bestätigt aber keine offene API für LARA.
- Technische Schnittstellen, SDK-Versionen, Lizenz, Hosting und Gerätemodell-Kompatibilität müssen vor der jeweiligen Implementierungsphase anhand aktueller offizieller Herstellerdokumentation erneut verifiziert werden.
