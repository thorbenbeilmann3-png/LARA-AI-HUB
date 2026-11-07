# Python-Worker – Sicherheitsmodell (0.3.0)

## Zwei getrennte Schlüssel

| Umgebungsvariable | Wer hat ihn | Wofür |
|---|---|---|
| `LARA_AUTOMATION_TOKEN` | Clients (LARA-App, Automate, Agenten) | `/v1/chat`, `/v1/automation/*`, Freigabe *anfragen* |
| `LARA_APPROVER_TOKEN` | Nur die Freigabe-Oberfläche des Nutzers | Freigaben *ansehen* und *entscheiden* |

- Ist ein Schlüssel nicht gesetzt, ist der jeweilige Bereich mit HTTP 503 abgeschaltet.
- Sind beide Schlüssel gleich, sind Freigaben abgeschaltet (503). Sonst könnte sich ein Client selbst freigeben.
- Schlüssel nur über Umgebungsvariablen setzen, nie in Git, Logs oder Screenshots.

## Freigaben

1. Client: `POST /v1/approvals {"task_id": ...}` → `approval_id` (nur für Aufgaben mit `approval_required: true`).
2. Nutzer: `GET /v1/approvals`, dann `POST /v1/approvals/{id}/decision {"decision": "approve"|"deny"}` mit dem Approver-Schlüssel.
3. Client: `POST /v1/automation/run {"task_id": ..., "approval_id": ...}`.

Eine Freigabe gilt für genau eine Aufgabe und eine Ausführung. Sie läuft nach 300 s ab und liegt nur im Arbeitsspeicher, nach einem Neustart ist sie also weg. Es dürfen höchstens 50 Freigaben gleichzeitig offen sein.

Das frühere Feld `approved` wird aus Kompatibilitätsgründen noch angenommen, **aber ignoriert**.

## Bewusste Verhaltensänderung

`python.runtime_info` und `ollama.list_models` sind als `approval_required: false` registriert. Das stand schon vorher so in der Aufgabenliste. Sie laufen jetzt mit gültigem Client-Schlüssel ohne Freigabe. Vorher genügte dafür `approved: true` vom Client, was keinen echten Schutz bot.

## Noch offen (eigene Schritte)

- ~~Node-Server `index.js`: `0.0.0.0` und SSRF~~ → behoben, siehe `docs/NODE-SERVER-SICHERHEIT.md`.
- Android: `usesCleartextTraffic="true"` für alle Ziele.
- `/health` ist ohne Schlüssel lesbar und zeigt, welche Anbieter konfiguriert sind (nur ja/nein, keine Schlüssel).
- `ollama.list_models` nutzt `/api/tags`. Der Server auf dem Pixel bietet laut letztem Test `/v1/models` an.
- Keine Begrenzung der Anfragerate (Rate Limiting).
- Der Worker hat keine Startanleitung. Er soll nur auf `127.0.0.1` oder der VPN-Adresse lauschen (uvicorn-Standard: `127.0.0.1`).
