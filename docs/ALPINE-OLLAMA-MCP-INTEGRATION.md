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
