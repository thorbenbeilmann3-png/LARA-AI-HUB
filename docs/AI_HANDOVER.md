# LARA NEXUS – Modellübergabe

## Projektstatus

- **Branch:** `feature/android-secure-api-key-storage`
- **Letzter Commit:** `d4e0d500868b794804cfd40f888a1b8fedd52b1c` ("Ollama-Verbindung: dynamische Modellerkennung, Proxy-Timeout 120s, /api/chat Support")
- **Remote:** `https://github.com/thorbenbeilmann3-png/LARA-AI-HUB` (origin)
- **GitHub-Link:** https://github.com/thorbenbeilmann3-png/LARA-AI-HUB/tree/feature/android-secure-api-key-storage
- **Git-Status:** Sauber (nur `backup_lara_nexus/` untracked)

---

## 12 Übergabepunkte

### 1. Projektstruktur
Projektdateien im `/workspace`-Verzeichnis:
- `index.js` – Node.js HTTP-Proxy auf Port 3000
- `index.html` – LARA NEXUS Frontend (423 Zeilen)
- `package.json` – Start-Skript: `node index.js`
- `app/` – Android-App (Kotlin, Compose)
- `python-worker/` – Python FastAPI Control Plane
- `.github/workflows/` – CI-Workflows
- `docs/` – Dokumentation
- `backup_lara_nexus/` – Zusätzliche Sicherung der Hauptdateien

### 2. Technischer Stand (Backend)
- Node.js v24.22.1, npm 11.12.1 installiert
- Port 3000, gebunden an `0.0.0.0`
- Proxy-Endpunkte:
  - `GET /v1/models?ollamaUrl=<url>` – Ollama-Modellliste abrufen
  - `POST /api/chat` – Ollama-nativer Chat (`/api/chat` auf Zielserver)
  - `POST /v1/chat/completions` – OpenAI-kompatibler Chat (Fallback)
  - `OPTIONS` – CORS Preflight
- SSRF-Schutz: Blockiert localhost, 127.0.0.0/8, 169.254.0.0/16, 0.0.0.0/8. Erlaubt 10.x.x.x, 172.16-31.x.x, 192.168.x.x
- Timeout: 120 Sekunden pro Proxy-Anfrage
- CORS-Header: `Access-Control-Allow-Origin: *`
- `sendResponse()` verhindert doppelte `writeHead`-Aufrufe (kein `ERR_HTTP_HEADERS_SENT`)

### 3. Technischer Stand (Frontend)
- `index.html` serviert unter `GET /` und `GET /index.html`
- Dunkles futuristisches Design mit goldenen Akzenten
- Navigation: Dashboard, Chat, ARENA, Ollama, Agenten, Einstellungen
- Ollama-Sektion:
  - `ollamaUrl` Input (Default: `http://10.179.130.230:12434`)
  - `modelSelect` Dropdown (dynamisch befüllt über `/v1/models`)
  - `testOllama()` – testet Verbindung über Proxy
  - `saveOllamaSettings()` – speichert URL und Modell in `localStorage`
- Chat-Sektion:
  - `sendMessage()` – sendet Nachricht über `/api/chat` Proxy
  - `loadModels()` – lädt Modelle beim Start
  - Ladeanzeige während Anfrage
  - Fehlerbehandlung mit `chatError` Anzeige

### 4. Ollama-Server (Pixel 9a)
- Adresse: `http://10.179.130.230:12434`
- Status: Erreichbar (HTTP 200, Version 0.1.32, CORS aktiviert)
- Modellliste: **Leer** (`{"models":[]}`) – kein Modell installiert
- `/api/chat` liefert 404: "Ollama cluster chat requires Local Pool or Master mode"

### 5. Installierte Modelle
Keine. Auf dem Ollama-Server ist derzeit kein Modell installiert.

### 6. Verfügbare Modelle (Empfehlung)
Über Ollama pullbar (lokal, kostenlos):
- `qwen3:8b` – Empfohlen (beste Balance Programmierung/Kontext/Ressourcen)
- `deepseek-coder:6.7b` – Alternative (Code-spezialisiert, 16k Kontext)
- `qwen2.5-0.5b-instruct-q4_k_m` – Geringe Ressourcen, schon genannt

### 7. Empfohlenes Modell für LARA NEXUS
**`qwen3:8b`**
- 32k Kontext reicht für die Codebasis
- Sehr gute Programmierungsfähigkeiten
- Geeignet für Pixel 9a Ressourcen

### 8. Startanweisung für das nächste Modell
Auf dem Pixel 9a ausführen:
```bash
ollama pull qwen3:8b
```
Alternativ bei knappem Arbeitsspeicher:
```bash
ollama pull qwen2.5-0.5b-instruct-q4_k_m
```

Nach der Installation prüfen:
```bash
ollama list
```

### 9. Nächste Schritte nach Modellinstallation
1. `ollama list` auf dem Pixel 9a ausführen → Modell sollte erscheinen
2. Modellliste über Proxy prüfen:
   ```bash
   curl "http://localhost:3000/v1/models?ollamaUrl=http://10.179.130.230:12434"
   ```
3. Echten Chat-Test über Proxy:
   ```bash
   curl -X POST "http://localhost:3000/api/chat" \
     -H "Content-Type: application/json" \
     -d '{"ollamaUrl":"http://10.179.130.230:12434","model":"qwen3:8b","messages":[{"role":"user","content":"Sag Hallo"}]}'
   ```
4. LARA NEXUS Frontend im Browser öffnen und Chat testen

### 10. Git-Workflow (Sicherheitsregeln)
- Arbeiten ausschließlich auf `feature/android-secure-api-key-storage`
- Nie `main` verändern
- Nie `git push --force` verwenden
- Keine Zugangsdaten oder Tokens committen
- Vor dem Commit: `git status`, `git diff`
- Nach Tests: Commit + Push + `git ls-remote origin refs/heads/feature/android-secure-api-key-storage` verifizieren

### 11. Bekannte offene Probleme
- Kein Ollama-Modell auf dem Pixel 9a installiert
- `/api/chat` auf dem Pixel 9a liefert 404 (erfordert Local Pool oder Master mode)
- Echter KI-Chat noch nicht nachweisbar (fehlendes Modell)
- Vorschau-Server wurde gestoppt (muss bei Bedarf mit `npm start` neu gestartet werden)

### 12. Dateien die nicht geändert werden dürfen
- Keine bestehenden Funktionen löschen oder überschreiben
- `main` Branch unangetastet lassen
- Keine API-Schlüssel oder Zugangsdaten in Dateien speichern
- Sicherheitsprüfungen (SSRF, CORS) nicht umgehen

---

## Verifizierungsliste (für das nächste Modell)

- [ ] `git branch --show-current` → `feature/android-secure-api-key-storage`
- [ ] `git log -1 --format="%H %s"` → `d4e0d500868b794804cfd40f888a1b8fedd52b1c`
- [ ] `git status` → sauber
- [ ] `git ls-remote origin refs/heads/feature/android-secure-api-key-storage` → Commit-ID stimmt mit lokalem HEAD überein
- [ ] `npm start` → Server läuft auf Port 3000
- [ ] `http://localhost:3000/` → HTTP 200, enthält "LARA NEXUS"
- [ ] Ollama-Modell installiert auf Pixel 9a (`ollama list`)
- [ ] Echter Chat-Test erfolgreich (Modell antwortet)
