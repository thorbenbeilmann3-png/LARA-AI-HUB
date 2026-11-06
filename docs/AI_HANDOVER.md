# LARA NEXUS – Technische Übergabe und nächste Schritte

## Verifizierter GitHub-Stand

- **Repository:** `thorbenbeilmann3-png/LARA-AI-HUB`
- **Arbeitsbranch:** `feature/android-secure-api-key-storage`
- **Vor dieser Dokumentationskorrektur verifizierter Branch-HEAD:** `3c53d9819935b5a6ada3d1c3cbf58fdff3b94537`
- **GitHub:** https://github.com/thorbenbeilmann3-png/LARA-AI-HUB/tree/feature/android-secure-api-key-storage
- Der frühere Eintrag `d4e0d500868b794804cfd40f888a1b8fedd52b1c` war der Eltern-Commit und nicht mehr der aktuelle Branch-HEAD.
- Vor jeder weiteren Änderung den aktuellen Branch-HEAD und die Arbeitskopie erneut prüfen.

## 1. Projektziel

LARA NEXUS ist ein lokales KI-Kontrollzentrum mit dunklem, futuristischem Design und goldenen Akzenten. Langfristig sind vorgesehen:
- LARA Chat mit echten lokalen Modellen
- Ollama-Anbindung und dynamische Modellauswahl
- ARENA für den Wettbewerb mehrerer Modelle
- Agentenrollen, Einstellungen und später weitere Werkzeuge
- Android-/PWA-Nutzung

Keine Funktion darf als fertig bezeichnet werden, wenn sie nur als Oberfläche oder Platzhalter existiert.

## 2. Projektstruktur und bekannte Dateien

Im zuletzt dokumentierten Workspace:
- `index.js` – Node.js HTTP-Server und Proxy
- `index.html` – Frontend
- `package.json` – Startskript `node index.js`
- `app/` – Android-App (Kotlin/Compose; Existenz laut vorherigem Workspace-Bericht, nicht in dieser GitHub-Dateiprüfung neu verifiziert)
- `python-worker/` – Python/FastAPI-Komponente (Existenz laut vorherigem Workspace-Bericht, nicht in dieser GitHub-Dateiprüfung neu verifiziert)
- `.github/workflows/` – CI-Workflows
- `docs/` – Dokumentation
- `backup_lara_nexus/` – zusätzliche lokale Sicherung; nicht als GitHub-Datei verifiziert

## 3. Aktueller Frontend- und Backend-Code – GitHub-Dateiprüfung

Die Dateien `index.js`, `index.html` und `package.json` wurden auf dem Branch `feature/android-secure-api-key-storage` gelesen.

### Backend (`index.js`)
Vorhandene Funktionen:
- HTTP-Server auf Port 3000, gebunden an `0.0.0.0`
- Liefert `index.html` unter `/` und `/index.html`
- `GET /v1/models?ollamaUrl=...` leitet derzeit an **`/api/tags`** des Zielservers weiter
- `POST /api/chat` leitet derzeit an **`/api/chat`** des Zielservers weiter
- `POST /v1/chat/completions` leitet an **`/v1/chat/completions`** des Zielservers weiter
- Proxy-Timeout 120 Sekunden
- Gemeinsamer `sendResponse()`-Helfer

### Frontend (`index.html`)
Vorhandene Funktionen:
- Dunkles UI mit Goldakzenten
- Navigation: Dashboard, LARA Chat, ARENA, Ollama, Agenten, Einstellungen
- Dynamisches Modell-Dropdown
- Verbindungstest und Speicherung von URL/Modell in `localStorage`
- Chat-Ladeanzeige und Fehleranzeige
- Chat sendet aktuell an den Node-Endpunkt `/api/chat`
- Modellparser verarbeitet derzeit `data.models` oder `data.tags`, aber nicht die OpenAI-kompatible Antwortstruktur `data.data[].id`

### Bereits beim Lesen erkannte Code-Probleme
1. Frontend und Proxy erwarten für die Modellliste `/api/tags` bzw. die Felder `models/tags`. Das passt nicht zur früheren erfolgreichen Geräteprüfung von `GET /v1/models`, die eine OpenAI-kompatible Struktur mit `data: [{id: ...}]` zurückgab.
2. Der Frontend-Chat ruft `/api/chat` auf. Der Android-Ollama-Server antwortete laut letztem Gerätebericht auf `/api/chat` mit HTTP 404 und dem Hinweis, dass Local Pool oder Master Mode erforderlich sei.
3. Der OpenAI-kompatible Zielendpunkt `/v1/chat/completions` ist im Proxy vorhanden, aber ein erfolgreicher echter Chat darüber ist **noch nicht nachgewiesen**.
4. `loadModels()` enthält eine Zuweisung an die nicht deklarierte Variable `selValue`; das sollte bereinigt werden, wenn die Funktion ohnehin überarbeitet wird.
5. Der Proxy nimmt eine vom Client übergebene Ziel-URL an. Der aktuelle SSRF-Schutz ist nicht ausreichend für einen öffentlich erreichbaren Server: Hostnamen/DNS-Auflösung, IPv6, Redirects, DNS-Rebinding und beliebige private Zieladressen müssen sicher behandelt werden. Keine Sicherheitsprüfung entfernen oder umgehen. Vor öffentlicher Nutzung den Proxy auf eine feste, ausdrücklich erlaubte Zieladresse begrenzen oder eine sichere Authentifizierungs-/Allowlist-Lösung umsetzen.

## 4. Ollama-Server auf dem Pixel 9a – bekannte, teils widersprüchliche Beobachtungen

Gerät:
- Google Pixel 9a
- 8 GB RAM
- Google Tensor G4

Bekannte Serveradresse aus früheren Tests:
- `http://10.179.130.230:12434`
- IP-Adresse kann sich ändern; vor Nutzung erneut in der Ollama-App prüfen.
- Keine Tokens oder Zugangsdaten in diese Datei eintragen.

Beobachtungen aus früheren Tests:
- `GET /v1/models` wurde im Smartphone-Browser erfolgreich aufgerufen und lieferte eine OpenAI-kompatible Modellliste.
- Ein späterer Test über `/api/tags` lieferte `{"models":[]}`.
- `POST /api/chat` lieferte HTTP 404: „Ollama cluster chat requires Local Pool or Master mode“.
- Laut letztem Übergabebericht war die Modellliste leer und es war kein Modell installiert.

**Wichtig:** Diese Beobachtungen sind nicht vollständig konsistent. Vor Codeänderungen müssen die Endpunkte direkt am aktuellen Gerät erneut getestet werden. Nicht unterstellen, dass es sich um einen unveränderten Standard-Ollama-Server handelt.

## 5. Modellstatus und Hardware-gerechte Empfehlung

Zum Zeitpunkt des letzten Geräteberichts war **kein Modell installiert**. Die folgenden Modelle sind Vorschläge aus der Ollama-Bibliothek, keine lokal bestätigten Installationen.

### Primäre Testempfehlung: `qwen3:4b`
- Ollama-Bibliothek listet ungefähr 2,5 GB für den Q4_K_M-Download.
- Für das Pixel 9a mit 8 GB RAM ist das ein vernünftigerer erster Versuch als ein 8B-Modell.
- Trotz des großen nominellen Modell-Kontextfensters nicht automatisch 256K Kontext aktivieren. Für den ersten Gerätetest mit 4K Kontext beginnen; bei stabiler Speichernutzung vorsichtig auf 8K erhöhen.

### Nicht als Standardempfehlung verwenden: `qwen3:8b`
- Ollama-Bibliothek listet ungefähr 5,2 GB Downloadgröße.
- Die Modellgewichte plus Laufzeit, KV-Cache und Android-System konkurrieren um nur 8 GB Gerätespeicher.
- Eine externe Schätzung nennt bereits ungefähr 6,5 GB Gesamtbedarf bei 4K Kontext; dies ist keine offizielle Pixel-Messung, aber ein deutliches Warnsignal.
- Deshalb nicht als erste Installation empfehlen.

### Kleinere Ausweichoption
- `qwen3:1.7b` – ungefähr 1,4 GB laut Ollama-Bibliothek.
- Geeignet als leichterer Funktionstest, falls 4B auf dem Telefon zu langsam ist oder wegen Speichermangel abbricht.

Offizielle Modellübersicht: https://ollama.com/library/qwen3

## 6. Exakte nächste Arbeitsschritte

### Schritt A – Gerät und Modell
1. Aktuelle IP/Port in der Ollama-App am Pixel 9a prüfen.
2. Prüfen, ob die App ein Modell-Download-/Pull-Menü anbietet.
3. Zuerst `qwen3:4b` installieren, nicht 8B.
4. Nach dem Download die Modellliste über den tatsächlich funktionierenden API-Endpunkt prüfen.
5. Wenn das Modell nicht startet oder das Gerät knapp wird: `qwen3:1.7b` als Rückfall verwenden.
6. Keine Behauptung „installiert“, bevor die API das Modell tatsächlich auflistet.

### Schritt B – API-Endpunkte am Gerät verifizieren
In dieser Reihenfolge testen:
1. `GET /v1/models`
2. `GET /api/tags`
3. `POST /v1/chat/completions` mit einer kurzen Testnachricht und dem exakten Modellnamen
4. `POST /api/chat` nur zu Diagnosezwecken; der letzte bekannte Test lieferte 404.

Der Chat-Test muss die echte Antwort des lokalen Modells zurückgeben. HTTP 200 allein reicht nicht; Antwortinhalt und ausgewähltes Modell müssen geprüft werden.

### Schritt C – Architekturentscheidung
- Cloud-Preview/Cloud-Container kann private Smartphone-IP-Adressen möglicherweise nicht erreichen. Ein 504 ist daher nicht automatisch ein Modellfehler.
- Browser-Direktverbindung kann an HTTPS-zu-HTTP-Mixed-Content-Regeln, CORS oder Android-Netzwerkregeln scheitern.
- Vor Umsetzung echte Verbindung aus genau der Laufzeit testen, in der LARA NEXUS ausgeführt wird.
- Wenn der Cloud-Proxy das private Telefon nicht erreicht, nicht mit weiteren Proxy-Timeouts kaschieren. Stattdessen eine lokale Android-/WebView-Architektur oder einen sicheren, bewusst eingerichteten Netzwerk-Tunnel prüfen.
- Keine Portfreigabe ins öffentliche Internet und keine SSRF-Schutzumgehung.

### Schritt D – Codekorrektur und Tests
Erst nach Bestätigung der Geräte-API:
1. Modellparser an das tatsächliche Antwortformat anpassen (`models`, `tags` oder `data[].id`).
2. Chat-Endpunkt an den nachweislich funktionierenden API-Endpunkt anpassen.
3. Nachrichtenverlauf tatsächlich mitsenden; aktuell wird nur die jeweils neue Nachricht gesendet.
4. Fehler, Timeouts und Ladezustand testen.
5. Modellwechsel testen.
6. Dashboard-/Navigation-/ARENA-/Einstellungsoberfläche auf Regressionen prüfen.
7. Sicherheitsprüfung des Proxy-Zielhosts durchführen.

## 7. Git- und Sicherheitsregeln

- Ausschließlich auf `feature/android-secure-api-key-storage` arbeiten.
- `main` nicht ändern.
- Kein Force-Push.
- Vor jeder Änderung Branch, HEAD, Status und Diff prüfen.
- Keine Tokens, Passwörter oder geheimen URLs committen.
- Keine bestehenden Funktionen oder Designbestandteile ohne Notwendigkeit entfernen.
- Vor Commit Tests ausführen.
- Nach Push Remote-HEAD mit lokalem HEAD vergleichen.
- Keine Erfolgsmeldung ohne tatsächliche Prüfung.

## 8. Nicht bestätigte Punkte

- Ob `POST /v1/chat/completions` auf dem aktuellen Pixel-Server funktioniert.
- Ob das Gerät inzwischen ein Modell installiert hat.
- Ob der Cloud-Preview-Server die private IP des Telefons erreichen kann.
- Ob die Android-App/WebView bereits eine lokale Laufzeit für das Frontend bereitstellt.
- Ob der Preview-Server aktuell läuft.
- Ob die lokalen Workspace-Dateien noch exakt dem GitHub-Stand entsprechen.

## 9. Startanweisung für das nächste KI-Modell

„Lies zuerst diese Datei vollständig. Prüfe dann den aktuellen GitHub-Branch und HEAD. Nimm die dokumentierten API-Widersprüche ernst. Empfiehl für das Pixel 9a zunächst `qwen3:4b` (nicht 8B). Prüfe die echte Geräte-API und die Netzwerkerreichbarkeit aus der tatsächlichen LARA-Laufzeit, bevor du Code änderst. Bearbeite nur eine klar abgegrenzte Aufgabe, teste sie und dokumentiere das Ergebnis. Arbeite ausschließlich auf `feature/android-secure-api-key-storage`; ändere niemals `main`, verwende keinen Force-Push und speichere keine Geheimnisse. PASS gibt es erst nach einem echten lokalen KI-Chat mit nachgewiesener Modellantwort.“

## 10. Quellen zur Modell- und Gerätebewertung

- Google Pixel 9a technische Daten (8 GB RAM): https://support.google.com/pixelphone/answer/7158570
- Ollama Qwen3-Modellbibliothek und Modellgrößen: https://ollama.com/library/qwen3
- Qwen3-Projekt und OpenAI-kompatible API-Hinweise: https://github.com/QwenLM/Qwen3


## 11. Ergänzung nach direkter Prüfung der Android-App

Die folgenden Dateien wurden zusätzlich direkt auf diesem Branch gelesen:

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/de/laraai/hub/MainActivity.kt`
- `app/src/main/java/de/laraai/hub/ApiConnection.kt`
- `app/build.gradle.kts`

### Bereits vorhandener direkter Android-Verbindungsweg

- Die native Android-App verwendet OkHttp direkt auf dem Smartphone. Sie muss den Ollama-Server daher nicht über den Cloud-Node-Proxy erreichen.
- Der Standard-Endpunkt in der App ist derzeit `http://10.179.130.230:12434/`.
- Das Manifest enthält die Internet-Berechtigung und `android:usesCleartextTraffic="true"`, damit lokale HTTP-Verbindungen möglich sind.
- `ApiConnection.discover()` versucht zuerst `GET /v1/models` und liest `data[].id`; bei einem HTTP-/Verbindungsfehler versucht es anschließend `GET /api/tags`.
- `ApiConnection.chat()` verwendet abhängig vom erkannten Modus `POST /v1/chat/completions` oder `POST /api/chat`.
- API-Schlüssel werden nur über HTTPS geladen/übertragen; für lokales Ollama muss das API-Schlüsselfeld leer bleiben.
- Der direkte Android-Verbindungsweg ist für den ersten echten Gerätetest vorzuziehen. Die Cloud-Webvorschau und ihr Node-Proxy sind davon getrennt zu betrachten.

### Noch vorhandene Android-App-Lücken

- Der Chat-Aufruf in `ApiConnection.chat()` sendet aktuell nur die jeweils neue Nutzerfrage. Frühere Gesprächsnachrichten werden lokal gespeichert, aber nicht als Verlauf an das Modell übertragen.
- `discover()` akzeptiert eine erfolgreiche OpenAI-Modelllisten-Antwort auch dann, wenn `data[]` leer ist; in diesem Fall wird nicht zusätzlich `/api/tags` versucht. Das ist nicht zwingend falsch, muss aber gegen das tatsächliche Verhalten des Geräte-Servers geprüft werden.
- Die Android-App wurde in dieser Prüfung nur aus dem GitHub-Quelltext gelesen. Ein Gradle-Build, eine Installation auf dem Pixel 9a und ein echter Chat wurden hier nicht ausgeführt.
- `usesCleartextTraffic="true"` erlaubt unverschlüsseltes HTTP für die App. Nur im vertrauenswürdigen lokalen Netzwerk verwenden; den Ollama-Port nicht öffentlich freigeben.

### Aktualisierte Reihenfolge

1. In der nativen LARA-App auf dem Pixel 9a die aktuelle Ollama-Adresse prüfen.
2. In der App „Verbinden / Modelle laden“ ausführen und den tatsächlich erkannten API-Modus sowie die Modellliste notieren.
3. `qwen3:4b` über die Modellverwaltung der Ollama-App installieren, sofern die App diesen Pull unterstützt. Falls nicht, zunächst klären, welche Download-/Pull-Funktion die verwendete Server-App anbietet; keinen Standard-Ollama-CLI-Befehl als verfügbar voraussetzen.
4. In LARA erneut verbinden und prüfen, ob `qwen3:4b` erscheint.
5. Eine kurze Testfrage senden und die echte Antwort kontrollieren.
6. Erst danach die Webvorschau/den Node-Proxy anpassen. Keine ungetestete Cloud-Proxy-Architektur als funktionierend deklarieren.

