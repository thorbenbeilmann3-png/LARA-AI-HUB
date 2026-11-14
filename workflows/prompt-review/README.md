# THOR Prompt Review (Mistral-Workflow)

Version: 0.3.0

## Zweck

Dieser Workflow prueft einen Projektauftrag-Prompt (z. B. den THOR-AI-Masterauftrag)
mit mehreren Mistral-Modellen unabhaengig voneinander nach acht festen Kriterien
(Klarheit, Widerspruchsfreiheit, Vollstaendigkeit, Messbarkeit, Machbarkeit,
Robustheit gegen Taeuschung, Sicherheit, Struktur). Die Befunde werden
deterministisch geparst, zusammengefuehrt und nach Schwere sortiert. Nichts wird
dabei erfunden: unbrauchbare oder leere Modellantworten werden verworfen und
ggf. ehrlich als fehlend gemeldet.

## Pruefmodelle

- `mistral-large-4` (expliziter Version-Pin; API-Namen laut Doku:
  `mistral-large-4`, `mistral-large-4-0`, `mistral-large-latest`, GA seit
  05.11.2026, Kontext 1M)
- `magistral-medium-latest`
- `mistral-small-latest`

Der Alias `mistral-large-latest` wird bewusst nicht zusaetzlich aufgefuehrt,
um doppelte Anfragen an dasselbe Modell zu vermeiden.

## Struktur

```
workflows/prompt-review/
├── Dockerfile                      # Build-Kontext fuer das Managed Deployment
├── main.py                         # Worker-Entrypoint (heisst bewusst nicht worker.py)
├── pyproject.toml                  # Projekt- und pytest-Konfiguration
├── README.md
├── src/worker/
│   ├── workflows/prompt_review.py  # Workflow-Definition (mistralai-workflows SDK)
│   └── logic/review.py             # reine Logik: Kriterien, Parsing, Merge, Overall
└── tests/test_logic.py            # unittest-Tests der reinen Logik
```

`main.py` heisst bewusst nicht `worker.py`: eine gleichnamige Datei haette das
Paket `worker` unter `src/` verdeckt und den ersten Deployment-Versuch zum
Scheitern gebracht.

## Verwendung

Eingabe (Workflow `thor-prompt-review`):

```json
{
  "prompt": "<vollstaendiger zu pruefender Prompt>",
  "criteria": null,
  "models": null
}
```

`criteria` und `models` sind optional; ohne Angabe gelten die acht Standard-
kriterien und die drei Standardmodelle. Ausgabe: `models_used`,
`criteria_used`, `findings` (Feld je Befund: model, ort, schwere, grund,
vorschlag) und `overall` (deterministische Gesamteinschaetzung).

## Formatbindung und tolerantes Parsing (ab 0.3.0)

Erfahrungsgemaess antworteten `magistral-medium-latest` und
`mistral-small-latest` formatkonform, waehrend `mistral-large-4` wiederholt vom
vorgegebenen Befundformat abwich (Format abweichend, Antworten wurden verworfen).
Version 0.3.0 greift deshalb an zwei Stellen ein:

1. Verschaerfte Systemanweisung fuer alle Modelle: keine Markdown-Auszeichnung,
   Antwort beginnt unmittelbar mit `Nummer: 1`, ausschliesslich die Feldnamen
   Nummer, Ort, Schwere, Grund, Vorschlag, Schwere nur
   kritisch/wichtig/gering, Abschluss mit `Gesamt: ...`.
2. Toleranteres, weiterhin deterministisches Parsing haeufiger Abweichungen:
   Markdown-Betonung (`**Fett**`, `## Ueberschriften`, Listensymbole) wird
   entfernt; Blockgrenzen werden zusaetzlich an `Befund`-Ueberschriften,
   Nummerierungspraefixen (`1.`, `1)`) vor Block-Startfeldern (Ort, Nummer,
   Befund) sowie an wiederholten `Ort:`-Zeilen erkannt; Schwereangaben mit
   Zusatz wie `wichtig, da ...` werden erkannt. Unbrauchbare Bloecke werden
   weiterhin verworfen; es werden keine Befunde erfunden oder aufgewertet.

## Tests

- 17 unittest-Tests (`python3 -m unittest discover -s tests`), lokal
  erfolgreich, u. a. Regressionstest zur Befundzaehlung (Zaehler beginnen bei
  0) sowie Tests fuer Markdown-Antworten, Antworten ohne Nummerierung,
   Schwereangaben mit Zusatz und unbrauchbare Antworten.

Einschraenkung: die SDK-Integration (`mistralai-workflows`, Activities,
Managed Deployment) ist lokal nicht ausfuehrbar; sie wird ausschliesslich im
Managed Deployment von Mistral getestet. Bekannte Einschaenkung zu
`mistral-large-4`: trotz verschaeerter Systemanweisung und tolerantem Parsing
kann das Modell weiterhin vom Format abweichen; Rohantworten sind in den
Workflow-Ereignissen nicht einsehbar, daher kann eine Abweichung nur als
fehlender Modellbeitrag gemeldet werden, nicht mit Voltext belegt.

## Deployment

- Repository: `thorbenbeilmann3-png/LARA-AI-HUB`, Branch
  `feature/prompt-review-workflow`
- Managed Deployment: `thor-prompt-review` mit `build_directory`
  `workflows/prompt-review`, `dockerfile_path` `Dockerfile`
- Der Worker benoetigt keinen API-Key im Repository: beim Managed Deployment
  wird der Token des injizierten Service-Accounts verwendet.

## Versionsgeschichte

- 0.1.0: Initialversion (Entrypoint `worker.py`, fehlgeschlagen; umbenannt zu
  `main.py`), Befundzaehlung korrigiert (Zaehler beginnen bei 0, inkl.
  Regressionstest)
- 0.2.0: `mistral-large-4` als expliziter Version-Pin statt
  `mistral-large-latest`
- 0.3.0: verschaerfte Systemanweisung und tolerantes Parsing zur Formatbindung
  von Mistral Large 4; Testumfang auf 17 Tests erweitert
