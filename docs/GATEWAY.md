# LARA Core – KI-Gateway (erster Anbieter: lokales Modell)

`POST /v1/gateway/local/chat` mit `LARA_AUTOMATION_TOKEN`:

```json
{"model": "qwen2.5-0.5b-instruct-q4_k_m", "messages": [{"role": "user", "content": "Hallo"}], "job_id": "<optional>"}
```

- **Ziel ist fest konfiguriert:** `LARA_LOCAL_MODEL_URL` mit Standard `http://127.0.0.1:12434`, der auf dem Pixel bestätigten Adresse. Es sind nur Loopback-Adressen erlaubt (`127.0.0.1`, `localhost`, `::1`). Der Client kann kein Ziel angeben. Weiterleitungen werden nicht verfolgt.
- **Auch `OLLAMA_BASE_URL`** (genutzt von `/v1/chat` mit Anbieter `ollama` und der Aufgabe `ollama.list_models`) wird seit 2026-11-08 mit derselben Regel geprüft: nur Loopback, nur Schema/Host/Port, keine Zugangsdaten. Eine andere Adresse ergibt HTTP 503, bevor eine Anfrage hinausgeht. `run_core.py` setzt sie standardmäßig auf denselben Wert wie `LARA_LOCAL_MODEL_URL`.
- **Anfrage** an `/v1/chat/completions` (OpenAI-kompatibel, auf dem Pixel getestet), ohne Streaming.
- **Tokens:** Liefert der Server `usage.prompt_tokens`/`completion_tokens`, wird die Anfrage mit Quelle `api` gespeichert, sonst mit `unknown`. Es wird nichts geschätzt. Ist eine `job_id` angegeben, werden die Tokens dem Auftrag zugeordnet.
- **Kontextfenster:** Beim ersten Aufruf je Modell versucht das Gateway `POST /api/show` (Ollama-Format, `model_info["<arch>.context_length"]`). Gibt es dort einen Wert, wird er mit Quelle `api` gespeichert. Ein vom Nutzer eingetragener Wert wird nie überschrieben.
- **Antwort:** `answer` plus `context` (Auslastung, Rest, Status, Gesamtverbrauch, wie bei `/v1/models/context`).

## Getestet / nicht getestet

- PASS (Unit-Tests mit simuliertem Modellserver, 13 Fälle): Antwort und Tokens; fehlende Tokens → `unknown`; Nutzerwert bleibt erhalten; Zuordnung zum Auftrag; unbekannter Auftrag → 404 ohne Modellaufruf; Server nicht erreichbar, HTTP-Fehler oder unbekanntes Format → 502; Nicht-Loopback-Ziele → 503 ohne Aufruf; Token nötig; Client kann kein Ziel setzen.
- NOT TESTED: echter Aufruf gegen den Pixel-Server. Ob er `usage` und `/api/show` unterstützt, ist unbekannt (siehe Monitor-Tab nach dem ersten Chat).
- Weitere Anbieter (Claude, Gemini, OpenAI) folgen erst nach deiner Freigabe für einen API-Zugang, weil sie Kosten verursachen.
