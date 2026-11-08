# LARA Core – Auftragsentwürfe mit „Ja / Nein / Andere Idee“ (Phase 2 und 3, Serverseite)

Ein Auftrag entsteht nicht mehr direkt, sondern zuerst als **Entwurf**. Erst nach einer ausdrücklichen Entscheidung mit dem **Approver-Token** wird daraus ein Auftrag im Auftragsspeicher.

| Schritt | Schnittstelle | Token |
|---|---|---|
| Entwurf anlegen (Ziel, Kontext, erlaubte Pfade, Grenzen, Akzeptanzkriterien, Tests, vorgeschlagener Agent, Kostenhinweis, optional Schritte) | `POST /v1/drafts` | Client (`LARA_AUTOMATION_TOKEN`) |
| Entwürfe ansehen (optional `?status=draft/revise/approved/rejected`) | `GET /v1/drafts` | Client |
| Nach „andere Idee“ überarbeiten (nur solange nicht entschieden) | `PUT /v1/drafts/{id}` | Client |
| **Entscheiden:** `approve` = „Ja, weitergeben“, `reject` = „Nein, ablehnen“, `revise` = „Ich habe eine andere Idee“ (Notiz Pflicht) | `POST /v1/drafts/{id}/decision` | **Approver** (`LARA_APPROVER_TOKEN`) |

Regeln:
- Ohne `approve` entsteht **kein** Auftrag. Es gibt keine Vorauswahl.
- Mit dem Client-Token kann man nicht entscheiden (401). So kann sich kein Agent selbst freigeben.
- Jeder Entwurf wird genau einmal entschieden. `revise` schickt ihn zurück, nach dem Überarbeiten ist er wieder `draft`.
- `approve` legt den Auftrag mit den Schritten des Entwurfs an. Fehlen Schritte, entsteht ein Schritt „Umsetzung“. Der Fortschritt startet bei 0 %.
- Entwürfe liegen in derselben SQLite-Datei und überstehen Neustarts.

Noch nicht vorhanden: die Dialog-Oberfläche in der App (drei Knöpfe), das Ausarbeiten der Entwürfe durch ein Modell (Phase 4, Kosten bzw. Freigabe nötig) und die Übergabe ins Repository (Phase 5, GitHub-Token bzw. Freigabe nötig).

Tests: `python-worker/tests/test_drafts.py` (11 Fälle).
