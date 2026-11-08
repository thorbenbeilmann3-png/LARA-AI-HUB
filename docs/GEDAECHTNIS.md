# LARA Core – Gedächtnis (Grundlage, Masterauftrag Teil D)

Gespeichert wird in derselben SQLite-Datei wie die Aufträge (`LARA_DB_PATH`). Das Gedächtnis gilt für alle Agenten und hängt von keinem Modell ab.

## Eintrag

| Feld | Bedeutung |
|---|---|
| `category` | `praeferenz`, `projekt`, `architektur`, `technik`, `entscheidung`, `aufgabe`, `loesung`, `fehler`, `recherche` |
| `content` | die Information, max. 4.000 Zeichen |
| `source` | woher sie stammt (z. B. „Nutzertest Pixel 07.11.“) |
| `trust` | `BESTÄTIGT`, `NUTZERANGABE`, `ANNAHME`, `UNGEPRÜFT`, `WIDERSPRUCH`; `VERALTET` entsteht nur durch Ersetzen |
| `project` | optional |
| `review_at` | optionales Datum für eine erneute Prüfung |

## Regeln

- **Ersetzen statt Überschreiben:** `POST /v1/memory/{id}/replace` legt einen neuen Eintrag an. Der alte wird `VERALTET` und mit dem neuen verknüpft (`supersedes` / `superseded_by`). Veraltete Einträge erscheinen nur mit `include_outdated=true`.
- **Ausschnitte statt alles:** `GET /v1/memory?category=…&project=…&q=…&limit=…`. So bekommt jeder Agent nur, was er braucht.
- **Keine Geheimnisse:** Inhalte, die wie API-Schlüssel, Tokens, Passwörter oder private Schlüssel aussehen, werden mit HTTP 400 abgewiesen. Zugangsdaten bleiben im Keystore der App bzw. in `~/.lara_core`.
- **Prüftermine:** `GET /v1/memory/review` listet fällige Einträge.
- **Export:** `GET /v1/memory/export` liefert `LARA_MEMORY/*.md` (`USER_PREFERENCES`, `LARA_PROJECT_MASTER`, `ARCHITECTURE`, `TECHNICAL_STATE`, `DECISIONS`, `OPEN_TASKS`, `VERIFIED_SOLUTIONS`, `KNOWN_ERRORS`, `RESEARCH_INDEX`, `MEMORY_CHANGELOG`).
- Alle Endpunkte brauchen `LARA_AUTOMATION_TOKEN`.

## Grenzen

- Die Secret-Erkennung beruht auf Mustern. Sie fängt übliche Formate ab, aber **nicht jedes denkbare Geheimnis**.
- Noch nicht vorhanden: Import aus `LARA_MEMORY/*.md`, die Gedächtnis-Ansicht in der App, automatisches Übernehmen von Wissen aus Chats (das braucht Regeln, was „langfristig relevant“ ist, und gegebenenfalls ein Modell, also Kosten).

Tests: `python-worker/tests/test_memory.py` (14 Fälle).
