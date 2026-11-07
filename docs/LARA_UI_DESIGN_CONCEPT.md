# LARA NEXUS – UI-Designgrundlage (freigegebener Entwurf)

**Status:** Vom Nutzer als Designrichtung freigegeben  
**Zweck:** Verbindliche visuelle und strukturelle Grundlage für die spätere PC-Oberfläche. Dies ist zunächst eine Design-Spezifikation, keine bereits implementierte Oberfläche.

## 1. Visuelle Leitlinie

- Premium-Dark-UI, klar und funktional statt überladenem Sci-Fi-Neon-Look.
- Grundhintergrund: Navy/Anthrazit `#0a0e1a`
- Panels: `#111827`, zweite Ebene `#1e293b`
- Primärer Akzent: Gold `#f59e0b`
- Text: `#f1f5f9`; Sekundärtext: `#64748b`
- Statusfarben: Grün `#22c55e`, Rot `#ef4444`, Blau `#3b82f6`
- Schrift: Inter; technische Werte optional JetBrains Mono.
- Kartenradius ca. 12 px; dezente Rahmen und Schatten.
- Desktop-first mit linker Navigation; auf kleineren Displays responsive Navigation.

## 2. Hauptnavigation

1. Dashboard
2. KI-Arena
3. Agenten
4. Gaming-Server
5. Werkzeuge
6. Systemmonitor
7. Projekte
8. Einstellungen
9. KI-Modelle
10. Hilfe & Support

## 3. Die zehn freigegebenen Bildschirmbereiche

### 1 – Hauptdashboard
Zentrale LARA-NEXUS-Startseite mit Systemstatus, Schnellzugriffen und übersichtlichen Statuskarten. Keine erfundenen Live-Daten: Werte nur anzeigen, wenn echte Messwerte verfügbar sind; sonst als nicht verbunden/keine Daten kennzeichnen.

### 2 – KI-Arena
Modellvergleich, Turniere, Rangliste und nachvollziehbare Testergebnisse. Bewertungen und Modellnamen müssen aus tatsächlich ausgeführten Tests stammen; keine fest eingebauten Beispielrankings als echte Ergebnisse.

### 3 – Agentenzentrale
Agenten, Teams, Aufgaben, Zuständigkeiten, Sitzungen und Protokolle. Agentenstatus nur aus dem tatsächlichen Laufzeitsystem übernehmen.

### 4 – Gaming-Server
Serverübersicht mit Spiel, Erreichbarkeit, Spielerzahl, Start/Stopp/Neustart, Backups, Plugins und Logs. Aktionen nur mit passenden Berechtigungen und bestätigtem Ergebnis als erfolgreich markieren.

### 5 – Werkzeugzentrale
Verfügbare Tools, Skripte, Automationen, Downloads und Integrationen. Jede Aktion zeigt Berechtigungen und Ausführungsstatus.

### 6 – Systemmonitor
CPU, RAM, GPU, Datenträger, Netzwerk und Prozesse. Messwerte müssen von echten lokalen/verbundenen Systemquellen kommen.

### 7 – Projekte
Projektübersicht, Arbeitsbereiche, Dateien und Aktivitäten. Bestehende Projekte und Funktionen erhalten; keine automatische Verschiebung oder Löschung ohne Freigabe.

### 8 – Einstellungen
Allgemein, Sprache, Darstellung, Modelle, Server, Sicherheit, Berechtigungen und Benachrichtigungen.

### 9 – KI-Modelle
Installierte und verbundene Modelle, Anbieter, Endpunkte, Verfügbarkeit und Testfunktion. Geheimnisse/Token niemals im Klartext anzeigen.

### 10 – Hilfe & Support
Anleitungen, Systemdiagnose, Support-Chat und nachvollziehbare Fehlerberichte.

## 4. Architektur- und Funktionsprinzipien

LARA bleibt ein eigenständiges System. OpenClaw dient als Architektur-Referenz bzw. optionale Integration, nicht als Ersatz für LARA.

Die Oberfläche soll die vorhandenen und geplanten Bereiche sauber integrieren:
- Agentenrollen, Teams, Workspaces und Sessions
- Routing, Tools, Skills und Automationen
- Gedächtnis/Projektkontext
- Gaming-Server und KI-Arena
- Rechte, Freigaben, Audit-Logs und Sicherheitsgrenzen

## 5. Verbindliche Umsetzungsvorgaben

- Vor Änderungen den aktuellen GitHub-Stand und die vorhandene Oberfläche prüfen.
- Bestehende Funktionen, IDs, API-Verträge und Abläufe erhalten; keine pauschale Neuentwicklung oder Entfernung.
- Erst Ist-Aufnahme und UI-Mapping, dann kleine nachvollziehbare Änderungen.
- Vor Implementierung Screenshot/Referenz gegen die vorhandene Oberfläche abgleichen.
- Responsive Desktop-Oberfläche; mobile Ansichten dürfen nicht beschädigt werden.
- Keine Demo-Daten als echte Systemdaten ausgeben.
- Jede kritische Aktion benötigt passende Rechte, Bestätigung, Protokollierung und Fehlerbehandlung.
- Nach jeder Änderung Build/Tests durchführen und die Oberfläche visuell prüfen.
- Bei nicht testbaren Funktionen ausdrücklich den offenen Prüfstatus dokumentieren.
- Diese Spezifikation allein erteilt noch keinen Auftrag zur Implementierung.

## 6. Referenz

Die freigegebene Bildübersicht mit zehn Ansichten wurde in der ChatGPT-Unterhaltung erzeugt. Sie ist ein Konzept-Mockup, kein Screenshot des laufenden Programms. Die Bilddatei wurde mit diesem GitHub-Connector nicht als Binärdatei hochgeladen; diese Datei hält die Designentscheidung und Anforderungen dauerhaft im Repository fest.

## 7. Bestehende Projektbasis

- Repository: `thorbenbeilmann3-png/LARA-AI-HUB`
- Ausgangsbranch: `claude/pc-thorben-setup-i4qwi5`
- Bestehende PC-Weboberfläche: `index.html`
- Bestehende Roadmap: `docs/ROADMAP.md`
- OpenClaw-Integrationshinweise: `docs/OPENCLAW_SETUP.md`
