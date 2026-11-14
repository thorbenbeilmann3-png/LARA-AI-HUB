# THOR Prompt Review – Mistral Workflow

Version 0.1.0, Stand 14.11.2026. Projekt im Auftrag von Thorben (THOR AI / LARA),
entwickelt von der Steuerungsinstanz (Vibe) nach dem 14-Schritte-Ablauf.

## Zweck

Dieser Mistral-Workflow prueft einen Projektauftrag-Prompt (z. B. den
THOR-AI-Masterauftrag) automatisch mit mehreren Mistral-Modellen. Jedes Modell
prueft unabhaengig nach acht festen Kriterien (Klarheit, Widerspruchsfreiheit,
Vollstaendigkeit, Messbarkeit, Machbarkeit, Taeuschungsrobustheit, Sicherheit,
Struktur). Die Antworten werden deterministisch geparst und zu einer
Gesamtuebersicht zusammengefuehrt. Die Auswertung erfindet nichts: was die
Modelle nicht liefern, taucht nicht in den Befunden auf.

## Struktur

```text
workflows/prompt-review/
├── Dockerfile              # Baut den Worker-Container (managed deployment)
├── pyproject.toml          # Abhaengigkeiten und pytest-Konfiguration
├── worker.py               # Entrypoint: run_worker(discover_all_workflows...)
├── src/worker/
│   ├── workflows/prompt_review.py   # Workflow + Modellaktivitaet (SDK)
│   └── logic/review.py              # Reine Logik, ohne SDK (isoliert testbar)
└── tests/test_logic.py     # Tests der reinen Logik (stdlib unittest)
```

## Verwendung

Eingabe (JSON):

```json
{
  "prompt": "<vollstaendiger Text des zu pruefenden Auftrags>",
  "criteria": null,
  "models": null
}
```

`criteria` und `models` sind optional; Standard sind die acht Kriterien und
die Modelle mistral-large-latest, magistral-medium-latest, mistral-small-latest.

Ausgabe: `models_used`, `criteria_used`, `findings` (model, ort, schwere
kritisch/wichtig/gering, grund, vorschlag) und `overall`.

## Lokale Entwicklung

```bash
uv sync
MISTRAL_API_KEY=... uv run python worker.py   # lokaler Worker
uv run pytest                                   # Tests
```

## Bereitstellung (managed deployment)

1. Mistral GitHub App auf dem Repository installieren und in der Konsole
   eine GitHub-Credential hinterlegen.
2. Bereitstellung mit Build-Kontext `workflows/prompt-review` und Dockerfile
   `Dockerfile` erstellen (Revision: gemergter Branch-Stand).
3. Der Worker authentifiziert sich automatisch ueber den injizierten
   Service-Account-Token (mistralai-workflows >= 3.10 erforderlich).
   Es wird kein API-Schluessel im Repository benoetigt.
4. Voraussetzung seitens des Kontos: pay-as-you-go muss aktiviert sein.

## Tests und Pruefstatus

- Logiktests (tests/test_logic.py): lokal ausgefuehrt und bestanden
  (Parsing, Merge, Gesamteinschaetzung, Fehlerfaelle).
- SDK-Integration (Workflow-Registrierung, Modellaufrufe): nicht lokal
  getestet, da die Sandbox keine Paketinstallation zulaesst. Nach dem Merge
  und der Bereitstellung durch einen repraesentativen Ausfuehrungstest
  nachzuweisen.

## Sicherheitsregeln

- Keine Schluessel, Tokens oder Passwoerter im Code, in Tests oder Logs.
- Nur lesender Zugriff auf den Prompt-Inhalt; keine externen Zugriffe.
- Deterministische Auswertung; keine erfundenen Befunde oder Prozentzahlen.

## Bekannte Einschraenkungen

- Die Modellantworten haengen vom jeweiligen Modell ab; unbrauchbare
  Antwortformate werden uebersprungen (kein Fehler, aber weniger Befunde).
- Aggregation zaehlt und sortiert nur; inhaltliche Deduplizierung erfolgt
  bewusst regelbasiert und nicht durch ein weiteres Modell.

## Naechste Schritte

1. Pull Request pruefen und mergen.
2. Mistral GitHub App installieren, pay-as-you-go aktivieren.
3. Managed Deployment erstellen und einen Ausfuehrungstest mit dem
   THOR-AI-Masterauftrag v1.1 fahren.
