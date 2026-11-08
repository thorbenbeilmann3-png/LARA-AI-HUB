# LARA: Alpine Linux, Ollama und MCP integrieren

## Verbindlicher Auftrag

Lies dieses Dokument vollständig, bevor du Änderungen am Projekt vornimmst. Ziel ist eine **nachweislich funktionierende Verbindung** zwischen der Android-App LARA AI HUB auf einem Google Pixel 9a, der dort installierten Alpine-Linux-Umgebung und Ollama. Anschließend soll ein echter MCP-Client für Alpine integriert werden.

Nicht nur planen oder eine Oberfläche vortäuschen: Implementiere die Funktionen im bestehenden Projekt, baue die APK und teste jede erreichbare Funktion. Bestehendes Design und vorhandene Funktionen möglichst erhalten.

## 1. Bestätigter Ist-Zustand auf dem Pixel 9a

- Gerät: Google Pixel 9a, Android.
- Alpine Linux: 3.24.4, Architektur `aarch64`.
- Alpine läuft in einer Android-App/PRoot-Umgebung.
- Installiert: Python 3.14, Node.js 24, Git, curl sowie Ollama.
- Ollama-Modell installiert: `qwen2.5:0.5b`.
- Ollama-Server wurde manuell mit `ollama serve` gestartet.
- Innerhalb derselben Alpine-Umgebung antwortete `GET http://127.0.0.1:11434/api/version` mit HTTP 200.
- Das Modell ließ sich mit `ollama run qwen2.5:0.5b` starten und erzeugte eine deutsche Antwort. Die Qualität war für ein 0.5B-Modell erwartungsgemäß begrenzt.
- Alpine-MCP-Endpunkt: `http://127.0.0.1:8765/mcp`.
- Der MCP-Endpunkt verlangt Bearer-Authentifizierung. Ein nicht authentifizierter Aufruf lieferte 401. Ein früher verwendeter Token wurde erneuert. **Es liegt kein gültiger Token in diesem Dokument.**
- Gemessener Speicherzustand vor dem Modellstart: 7.4 GiB RAM insgesamt, ca. 1.4 GiB verfügbar; 3.7 GiB Swap, davon ca. 3.0 GiB belegt; ca. 19.3 GiB freier Gerätespeicher.
- Das Alpine-Terminal mit `ollama serve` muss derzeit geöffnet bleiben, damit der Server läuft.

## 2. Kritische offene technische Frage

Die erfolgreiche Ollama-Prüfung erfolgte **aus Alpine selbst**. Sie beweist nicht, dass LARA als separate Android-App den Alpine-Loopback-Endpunkt erreichen kann.

`127.0.0.1` in Alpine und `127.0.0.1` im Android-App-Prozess können unterschiedliche Netzwerk-/Namespace-Kontexte bezeichnen. Prüfe die Erreichbarkeit aus dem tatsächlichen LARA-Prozess auf dem Gerät. Keine Verbindung als funktionsfähig melden, bevor ein echter App-seitiger HTTP-Test erfolgreich war.

Wenn direkter Zugriff nicht funktioniert, untersuche eine sichere Bridge zwischen Alpine und LARA. Berücksichtige dabei Android-Sandboxing, PRoot-Netzwerkverhalten, Bind-Adresse, Portfreigabe, Lebenszyklus und Hintergrundbeschränkungen. Öffne keine ungeschützten Ports für das lokale Netzwerk oder Internet. Bevorzuge minimale lokale Angriffsfläche und authentifizierte Kommunikation, wo erforderlich.

## 3. Phase A – Projektanalyse und Verbindungstest

1. Repository und vorhandene Kotlin-/Compose-Architektur vollständig prüfen.
2. Bestehende Chat-, API-, Einstellungs-, Secure-Storage- und Automate-Integration identifizieren.
3. Prüfen, welche Provider-Konfigurationen bereits vorhanden sind und weshalb sie in der installierten App möglicherweise nicht sichtbar sind.
4. Einen kleinen, reproduzierbaren Android-seitigen Verbindungstest implementieren oder einen vorhandenen Testpfad nutzen.
5. Aus LARA heraus prüfen, ob Alpine/Ollama erreichbar ist.
6. Ergebnis mit konkreter Ursache dokumentieren. Nicht direkt mit umfangreicher UI- oder MCP-Implementierung beginnen, solange der grundlegende Transport ungeklärt ist.

## 4. Phase B – Ollama in LARA integrieren

Eigene Provider-Konfiguration für Ollama, getrennt von MCP:

- Serverbasis-URL editierbar; nicht blind `127.0.0.1` als App-übergreifend erreichbar voraussetzen.
- Verbindung testen und verständliche Fehler anzeigen.
- `GET /api/version` zur Serverprüfung.
- `GET /api/tags` zum Abruf verfügbarer Modelle.
- Modell auswählen und aktives Modell anzeigen.
- Chat-Anfragen über Ollamas unterstützte Chat-API senden und Antworten im vorhandenen LARA-Chat anzeigen.
- Lade-/Fehlerzustände, Timeouts und Abbruch sauber behandeln.
- Modell `qwen2.5:0.5b` erkennen, sofern es über den gewählten Transport erreichbar ist.
- Netzwerkzugriffe dürfen den UI-Thread nicht blockieren.
- Streaming nur implementieren, wenn es korrekt unterstützt und getestet wird.

## 5. Phase C – Alpine-Verwaltung

Eine verständliche Alpine-Verwaltungsseite in LARA ergänzen:

- Verbindungsstatus und letzter erfolgreicher Test.
- Verbindungsadresse und Transportart anzeigen/bearbeiten.
- Verbinden, trennen und Verbindung testen.
- Laufzeit-/Erreichbarkeitsdiagnose.
- Fehlerursachen verständlich darstellen.
- Persistente, sichere Konfiguration und Wiederherstellung nach App-Neustart.
- Keine Behauptung, Alpine-Prozesse starten/stoppen zu können, solange keine echte und getestete Steuerung existiert.

## 6. Phase D – Echter MCP-Client

MCP ist von Ollama getrennt zu konfigurieren. Für den Alpine-MCP-Endpunkt einen echten MCP-Client gemäß der aktuellen offiziellen MCP-Spezifikation und dem tatsächlich unterstützten HTTP-Transport implementieren.

Erforderlich:

- Initialisierung und Protokollversion aushandeln.
- Authentifizierten HTTP-Transport korrekt umsetzen.
- Tools auflisten und Tool-Metadaten darstellen.
- Tools kontrolliert aufrufen und Ergebnisse an LARA zurückgeben.
- Fehler, Timeouts, nicht unterstützte Protokollfunktionen und Verbindungsabbrüche behandeln.
- Bearer-Token über Android Keystore-gestützte sichere Speicherung oder eine geeignete bestehende sichere Speicherlösung schützen.
- Token niemals in Quellcode, Git, Build-Artefakten, Klartext-Logs oder Diagnoseexporten speichern.
- Token niemals im Chat oder in Screenshots anfordern.
- Tool-Aufrufe mit Auswirkungen auf Dateien, System oder externe Dienste nur nach expliziter Nutzerfreigabe ausführen.
- Keine beliebigen Shell-Befehle ohne Freigabe ausführen.

Vor der Implementierung aktuelle offizielle MCP-Dokumentation und die konkrete Alpine-MCP-Implementierung prüfen. Keine Protokollversion, Header oder Transportdetails erraten.

## 7. UI und Bedienbarkeit

Die bestehende LARA-Oberfläche erhalten und sinnvoll erweitern. Eine klar auffindbare Einstellungs-/Verwaltungsseite mit getrennten Bereichen:

1. Alpine Linux
2. Ollama / lokale KI
3. MCP-Verbindungen
4. Verbindungstests und Diagnose
5. Sicherheit / Zugangsdaten

Jeweils klar anzeigen: Verbunden / Nicht verbunden / Nicht getestet, letzte Prüfung, aktive Modellbezeichnung, verfügbare MCP-Werkzeuge und verständliche Fehlerursache.

Keine toten Schaltflächen oder rein dekorativen Statusanzeigen.

## 8. Sicherheit und Android

- Android-Berechtigungen auf das notwendige Minimum beschränken.
- Bestehende Android-Sicherheitsmechanismen und Secure Storage prüfen und wiederverwenden.
- Keine Tokens oder privaten Schlüssel in GitHub.
- Keine unnötige Freigabe an LAN/Internet.
- Keine unsichere Umgehung der Android-Sandbox.
- Netzwerkoperationen asynchron ausführen.
- App-Lebenszyklus, Prozessabbrüche, Energiesparmodus und Hintergrundlimits berücksichtigen.
- Keine erfolgreiche Geräteverbindung aus einem Emulator- oder Unit-Test ableiten.

## 9. Verbindliche Tests

Mindestens folgende Prüfungen durchführen und einzeln dokumentieren:

1. Bestehende App baut erfolgreich.
2. App startet auf Android.
3. LARA-Prozess erreicht den Alpine-Endpunkt tatsächlich.
4. Ollama-Version wird über LARA abgerufen.
5. Modellliste wird über LARA abgerufen.
6. `qwen2.5:0.5b` wird erkannt.
7. Eine Testnachricht wird aus dem LARA-Chat an Ollama gesendet.
8. Die echte Modellantwort erscheint im LARA-Chat.
9. MCP-Authentifizierung funktioniert ohne Token-Offenlegung.
10. MCP-Tools werden korrekt aufgelistet.
11. Ein harmloser MCP-Testaufruf funktioniert.
12. Falsche Adresse, falscher Token, Serverausfall und Timeout werden sauber behandelt.
13. App-Neustart und Wiederverbindung werden geprüft.

Status pro Test: PASS / FAIL / NICHT GETESTET. Ein erfolgreicher Gradle-Build allein ist kein Nachweis für die Geräteverbindung. Tests, die mangels physischem Gerät nicht möglich sind, ausdrücklich als NICHT GETESTET markieren.

## 10. Arbeitsreihenfolge und Freigabe

Arbeite in kleinen, vollständigen Funktionspaketen:

1. Ziel, Eingaben und Vorgaben erfassen.
2. Fehlende, unklare oder widersprüchliche Angaben prüfen.
3. Bei wesentlichen Lücken FEHLER und BLOCKIERT kennzeichnen und anhalten.
4. Erforderliche Fakten und aktuelle Originaldokumentation recherchieren.
5. Belege prüfen, Tatsachen zitieren und Unsicherheiten kennzeichnen.
6. Vorgaben, belegte Fakten, Annahmen und Ergebnisse trennen.
7. Aufgabenbeschreibung erstellen.
8. Phasenweisen Arbeitsplan erstellen.
9. Plan anhand der Aufgabenbeschreibung prüfen und überarbeiten.
10. Mit einem passenden technischen Test beginnen.
11. Genau ein Funktionspaket umsetzen.
12. Unabhängig prüfen; Fehler vor dem nächsten Paket korrigieren.
13. Änderungen, Tests, Übergaben und Entscheidungen dokumentieren.
14. Unabhängige Abschlussprüfung durchführen und erst danach freigeben.

Rollen: Koordinator, Umsetzer, Prüfer. Echte Unteragenten nur einsetzen, wenn sie in der tatsächlichen Umgebung verfügbar sind. Andernfalls Rollen nacheinander ausführen und keine Agenten vortäuschen.

Jede Übergabe enthält:

- Agent:
- Zuständigkeit:
- Eingaben:
- Bearbeitung:
- Ergebnisse:
- Status: OFFEN / ANNAHME / FEHLER
- Fehlende Informationen:
- Nächster Agent:
- Übergabedaten:
- Dokumentation:

BLOCKIERTE Vorgänge nicht weitergeben. Erst nach Klärung und Dokumentation fortsetzen.

## 11. Gewünschtes Ergebnis

- Implementierter und nachvollziehbar geänderter Quellcode.
- Neue installierbare Android-APK.
- Erfolgreicher Build.
- Nachweis der echten Verbindung zwischen LARA, Alpine und Ollama auf dem Pixel 9a.
- MCP-Integration mit authentifiziertem, getestetem Zugriff.
- Änderungsübersicht und Testergebnisse.
- Installations-/Update-Anleitung.

**Priorität:** Zuerst die reale Verbindung LARA → Alpine → Ollama zuverlässig herstellen. Danach MCP integrieren. Bestehende LARA-Funktionen und das aktuelle Design erhalten. Keine Funktion als fertig bezeichnen, solange sie nicht nachweislich funktioniert.


## 12. Phase E – Python und Alpine-Terminal in LARA integrieren

Python und ein echtes Terminal sind ausdrücklich Teil des Auftrags. Nicht nur eine Python-Code-Eingabebox oder eine simulierte Terminal-Oberfläche bauen.

### Python-Integration

- Die bereits in Alpine installierte Python-Laufzeit (Python 3.14) als bevorzugte Python-Umgebung verwenden.
- LARA soll Python-Dateien und Skripte über die Alpine-Umgebung ausführen können.
- Python-Code im LARA-Editor erstellen, bearbeiten, speichern und ausführen.
- Skriptdateien in einem klar definierten Arbeitsverzeichnis unter `/root/LARA/projects` oder einem geeigneten, persistenten Benutzerprojektverzeichnis ablegen.
- Ausgabe, Fehlerausgabe, Exit-Code und Laufzeit sichtbar darstellen.
- Abhängigkeiten kontrolliert über `python -m pip` verwalten, soweit die Alpine-Umgebung dies unterstützt; System-Python nicht unkontrolliert überschreiben.
- Python-Version und verfügbare Pakete anzeigen.
- Lange Prozesse abbrechen können und Timeouts/Prozessstatus anzeigen.
- Keine Ausführung als erfolgreich melden, wenn die App nur den Code gespeichert oder an eine nicht erreichbare Umgebung gesendet hat.

### Terminal-Oberfläche

- Einen echten interaktiven Terminal-Zugang zur Alpine-Shell in LARA bereitstellen, sofern die Alpine-App/PRoot-Umgebung einen sicheren unterstützten Zugriff ermöglicht.
- Befehle und Argumente korrekt an die Alpine-Shell übergeben; stdout, stderr, Exit-Code und laufende Prozesse zurückliefern.
- Interaktive Sitzungen unterstützen, soweit technisch möglich; andernfalls Einschränkung klar anzeigen und einen verlässlichen nicht-interaktiven Befehlsmodus bereitstellen.
- Mehrere Sitzungen, laufende Prozesse, Abbruch, Sitzungsende und Wiederverbindung sauber behandeln.
- Arbeitsverzeichnis anzeigen und ändern können.
- Ausgabe lesbar und kopierbar darstellen.
- Dateien im LARA-Projektverzeichnis auflisten, lesen, erstellen und bearbeiten können, soweit die Verbindung und Berechtigungen dies erlauben.

### Sicherheit und Freigaben

- Terminal-Zugriff darf nicht durch eine versteckte oder ungeschützte Netzwerk-Shell realisiert werden.
- Keine pauschale Freigabe beliebiger Shell-Kommandos an MCP oder KI.
- Gefährliche oder irreversible Befehle (z. B. Löschen, Paketentfernung, Rechteänderungen, Netzwerkfreigaben) vor Ausführung deutlich anzeigen und explizit vom Nutzer bestätigen lassen.
- Tokens und sensible Umgebungsvariablen niemals in Terminal-Logs, Chatverläufe oder Diagnoseberichte übernehmen.
- Keine Root-Rechte außerhalb der bereits bestehenden Alpine-Umgebung ausweiten.
- Prüfen, welche IPC-/Bridge-Schnittstelle die Alpine-Android-App tatsächlich bereitstellt. Nicht einfach einen lokalen HTTP-Terminalserver ohne Authentifizierung starten oder an `0.0.0.0` binden.
- Falls keine sichere App-zu-App-Terminalverbindung möglich ist, den technischen Blocker dokumentieren und eine sichere, vom Nutzer aktivierte Bridge entwerfen. Keine Scheinimplementierung.

### Python-/Terminal-Tests

Zusätzlich zu den bisherigen Tests:

14. LARA kann die Alpine-Python-Version aus der echten App-Verbindung abfragen.
15. LARA kann ein einfaches Python-Skript in Alpine anlegen und ausführen.
16. Python stdout, stderr und Exit-Code werden korrekt in LARA angezeigt.
17. Ein Python-Skript mit absichtlichem Fehler wird korrekt als fehlgeschlagen angezeigt.
18. Ein laufender Prozess kann kontrolliert beendet werden.
19. Terminal-Befehl `pwd` und ein harmloser Shell-Befehl liefern echte Alpine-Ergebnisse.
20. Terminal-Eingaben werden nicht nur lokal simuliert, sondern nachweislich in Alpine ausgeführt.
21. App-Neustart und Wiederverbindung verhalten sich nachvollziehbar; nicht persistente Terminal-Sitzungen werden als beendet angezeigt.

### Priorität

Nach dem grundlegenden App-seitigen Verbindungsnachweis zuerst Ollama-Chat und danach Python-/Terminal-Zugriff in einem kleinen, vollständig getesteten Paket umsetzen. MCP-Werkzeuge sollen später kontrolliert dieselbe sichere Ausführungsumgebung nutzen können, aber nicht automatisch uneingeschränkten Terminalzugriff erhalten.


## 13. Phase F – Automate-Integration direkt im Anschluss

Nach dem erfolgreichen Abschluss und der Prüfung der vorherigen Kernpakete soll als nächstes Funktionspaket **Automate von LlamaLab** in LARA integriert werden. Die bestehende Automate-Integration im Projekt ist zuerst zu untersuchen und möglichst weiterzuverwenden, statt eine zweite parallele Lösung zu bauen.

### Ziel

LARA soll mit der installierten Android-App Automate zusammenarbeiten können, um vom Nutzer freigegebene Automatisierungs-Flows zu starten und deren Status bzw. Ergebnisse nachvollziehbar anzuzeigen.

### Vor der Implementierung prüfen

- Welche Automate-Version auf dem Pixel installiert ist.
- Welche offiziellen Integrationsmöglichkeiten die installierte Version bereitstellt (z. B. dokumentierte Intents, Deep Links, Content Provider oder andere unterstützte Schnittstellen).
- Welche Berechtigungen und Nutzerfreigaben erforderlich sind.
- Welche Teile der bestehenden LARA-Automate-Integration bereits funktionieren.
- Ob Status und Ergebnisse eines gestarteten Flows zuverlässig an LARA zurückgegeben werden können.

Keine undokumentierten Intent-Aktionen oder erfundenen API-Endpunkte verwenden. Maßgeblich sind die aktuelle offizielle Automate-Dokumentation und Tests auf dem echten Pixel.

### Funktionen in LARA

- Automate-Verfügbarkeit erkennen und Installationsstatus anzeigen.
- Unterstützte Automate-Flows bzw. Startmöglichkeiten auffindbar machen.
- Einen vom Nutzer ausgewählten Flow aus LARA starten.
- Vor dem Start Name und Wirkung des Flows anzeigen; bei sensiblen oder folgenreichen Aktionen eine ausdrückliche Bestätigung verlangen.
- Startfehler und fehlende Berechtigungen verständlich anzeigen.
- Soweit die offizielle Schnittstelle es ermöglicht: Ausführungsstatus und Ergebnis zurückmelden.
- Keine erfolgreiche Ausführung anzeigen, wenn LARA lediglich Automate geöffnet oder einen Start angefordert hat.
- Eine übersichtliche Automate-Verwaltung in LARA anbieten.

### Sicherheitsregeln

- Keine Automate-Flows stillschweigend oder ohne Nutzerfreigabe starten.
- Keine Flow-Inhalte, Zugangsdaten oder sensiblen Daten ungeschützt in Logs schreiben.
- Nur dokumentierte und erforderliche Android-Berechtigungen verwenden.
- LARA darf keine Automate-Flows verändern oder löschen, ohne dass der Nutzer dies ausdrücklich bestätigt.
- KI-gestützte Flow-Auswahl darf nur Vorschläge machen; die tatsächliche Ausführung folgt den Freigaberegeln.

### Tests

22. LARA erkennt, ob Automate installiert ist.
23. LARA zeigt eine unterstützte Startmöglichkeit für einen vorhandenen Test-Flow.
24. Ein harmloser Test-Flow wird nach Nutzerbestätigung tatsächlich gestartet.
25. LARA unterscheidet korrekt zwischen „Automate geöffnet“, „Start angefordert“, „läuft“, „erfolgreich beendet“ und „fehlgeschlagen“, soweit diese Zustände über die unterstützte Schnittstelle ermittelbar sind.
26. Fehlende Berechtigungen, nicht installierte App und ungültige Flow-Referenz werden sauber behandelt.
27. App-Neustart und Rückkehr aus Automate führen nicht zu falschen Erfolgsanzeigen.

Wenn Automate keine verlässliche Rückmeldung über einen Flow-Abschluss anbietet, muss LARA diese Einschränkung klar anzeigen und darf keinen Abschlussstatus erfinden.

### Reihenfolge

Automate wird **direkt nach** der stabilen Umsetzung und Prüfung von Alpine/Ollama sowie Python/Terminal begonnen. MCP bleibt ein getrenntes Funktionspaket und darf die Automate-Integration nicht ersetzen. Die Reihenfolge und Abhängigkeiten sind im Arbeitsplan transparent zu dokumentieren.
