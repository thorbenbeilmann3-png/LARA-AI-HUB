"""Acceptance tests 5-8 and 10: lossless, verified model handoff."""
import json

import pytest
from fastapi.testclient import TestClient

from app import main
from app.jobs import HANDOFF_EXTRA_FIELDS, extract_json_object

TOKEN = "handoff-test-token"
AUTH = {"Authorization": f"Bearer {TOKEN}"}
STEPS = [
    {"phase": "Planung", "title": "Plan", "weight": 1},
    {"phase": "Programmierung", "title": "Code", "weight": 2},
    {"phase": "Tests", "title": "Tests", "weight": 1},
]


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("LARA_AUTOMATION_TOKEN", TOKEN)
    monkeypatch.setenv("LARA_DB_PATH", str(tmp_path / "lara.db"))
    main.reset_store()
    yield TestClient(main.app)
    main.reset_store()


@pytest.fixture
def job(client):
    j = client.post("/v1/jobs", headers=AUTH, json={"title": "Dateiverwaltung", "goal": "Dateien sortieren", "steps": STEPS}).json()
    client.post(f"/v1/jobs/{j['id']}/steps/{j['steps'][0]['id']}", headers=AUTH, json={"status": "done", "result": "Plan steht"})
    client.post(f"/v1/jobs/{j['id']}/steps/{j['steps'][1]['id']}", headers=AUTH, json={"status": "in_progress"})
    return client.get(f"/v1/jobs/{j['id']}", headers=AUTH).json()


def prepare(client, job, **extra):
    r = client.post(f"/v1/jobs/{job['id']}/handoffs", headers=AUTH,
                    json={"from_model": "claude-opus-5-5", "to_model": "qwen-local", **extra})
    assert r.status_code == 201, r.text
    return r.json()


def good_reply(h):
    p = h["package"]
    return {"job_id": p["job_id"], "current_step_id": p["current_step"]["id"],
            "next_step_id": p["next_step"]["id"], "checksum": h["checksum"], "goal_summary": "Dateien sortieren"}


def confirm(client, h, **body):
    return client.post(f"/v1/handoffs/{h['handoff_id']}/confirm", headers=AUTH, json=body)


# 5. Übergabepaket erzeugen
def test_package_contains_all_required_parts(client, job):
    h = prepare(client, job, decisions=["SQLite statt JSON-Dateien"], important_files=["python-worker/app/jobs.py"],
                tools=["pytest"], test_results=["45 passed"], open_questions=["Wo liegt das Archiv?"],
                sources=["docs/AUFTRAGSSPEICHER.md"], context_notes=["Nutzer will Deutsch"])
    p = h["package"]
    for key in ("original_request", "goal_and_requirements", "completed_steps", "progress", "open_tasks",
                "current_step", "next_step", "known_errors", "token_usage", *HANDOFF_EXTRA_FIELDS):
        assert key in p, key
    assert p["completed_steps"][0]["result"] == "Plan steht"
    assert p["progress"]["overall_percent"] == 25.0
    assert p["current_step"]["title"] == "Code" and p["next_step"]["title"] == "Tests"
    assert p["decisions"] == ["SQLite statt JSON-Dateien"]
    assert len(h["checksum"]) == 64 and h["checksum"] in h["prompt"]
    assert h["status"] == "prepared"


def test_unknown_extra_fields_are_rejected(client, job):
    r = client.post(f"/v1/jobs/{job['id']}/handoffs", headers=AUTH,
                    json={"from_model": "a", "to_model": "b", "secrets": ["x"]})
    assert r.status_code == 422


# 6.+7. Übergabe an anderes Modell, Bestätigung prüfen
def test_correct_structured_confirmation_releases_new_model(client, job):
    h = prepare(client, job)
    r = confirm(client, h, reply=good_reply(h)).json()
    assert r == {"handoff_id": h["handoff_id"], "status": "confirmed", "problems": [], "released_to": "qwen-local"}


def test_confirmation_inside_model_text_is_parsed(client, job):
    h = prepare(client, job)
    text = "Verstanden.\n```json\n" + json.dumps(good_reply(h)) + "\n```"
    assert confirm(client, h, reply_text=text).json()["status"] == "confirmed"


@pytest.mark.parametrize("field", ["job_id", "current_step_id", "next_step_id", "checksum"])
def test_any_wrong_field_rejects(client, job, field):
    h = prepare(client, job)
    reply = good_reply(h)
    reply[field] = "falsch"
    r = confirm(client, h, reply=reply).json()
    assert r["status"] == "rejected" and r["released_to"] is None
    assert any(field in p for p in r["problems"])


def test_plain_answer_without_structure_is_not_success(client, job):
    """Abnahmekriterium: eine Antwort allein ist kein erfolgreicher Wechsel."""
    h = prepare(client, job)
    r = confirm(client, h, reply_text="Ich habe alles verstanden und mache weiter.").json()
    assert r["status"] == "rejected"


def test_stale_package_is_rejected_after_job_changed(client, job):
    h = prepare(client, job)
    client.post(f"/v1/jobs/{job['id']}/steps/{job['steps'][1]['id']}", headers=AUTH, json={"status": "done"})
    r = confirm(client, h, reply=good_reply(h)).json()
    assert r["status"] == "rejected"
    assert any("geändert" in p for p in r["problems"])


def test_tampered_package_is_detected(client, job):
    h = prepare(client, job)
    db = main.get_store()._db
    db.execute("UPDATE handoffs SET package = replace(package, 'Dateiverwaltung', 'Manipuliert') WHERE id=?", (h["handoff_id"],))
    r = confirm(client, h, reply=good_reply(h)).json()
    assert r["status"] == "rejected" and any("Prüfsumme" in p for p in r["problems"])


def test_handoff_can_only_be_decided_once(client, job):
    h = prepare(client, job)
    confirm(client, h, reply=good_reply(h))
    assert confirm(client, h, reply=good_reply(h)).status_code == 400


def test_new_package_supersedes_old_one(client, job):
    old = prepare(client, job)
    new = prepare(client, job)
    assert new["package"]["version"] == old["package"]["version"] + 1
    assert confirm(client, old, reply=good_reply(old)).status_code == 400
    assert client.get(f"/v1/handoffs/{old['handoff_id']}", headers=AUTH).json()["status"] == "superseded"


# 8. Am gespeicherten Schritt fortsetzen
def test_new_model_continues_at_saved_step(client, job):
    h = prepare(client, job)
    confirm(client, h, reply=good_reply(h))
    state = client.get(f"/v1/jobs/{job['id']}", headers=AUTH).json()
    assert state["active_model"] == "qwen-local"
    assert state["current_step"]["id"] == h["package"]["current_step"]["id"]
    assert state["overall_percent"] == h["package"]["progress"]["overall_percent"]


def test_rejected_handoff_does_not_change_active_model(client, job):
    h = prepare(client, job)
    confirm(client, h, reply_text="ok")
    assert client.get(f"/v1/jobs/{job['id']}", headers=AUTH).json()["active_model"] is None


# 10. Nichts geht verloren (auch über einen Neustart)
def test_handoff_and_original_state_survive_restart(client, job):
    h = prepare(client, job, decisions=["D1"])
    main.reset_store()
    stored = client.get(f"/v1/handoffs/{h['handoff_id']}", headers=AUTH).json()
    assert stored["package"] == h["package"] and stored["checksum"] == h["checksum"]
    assert confirm(client, h, reply=good_reply(h)).json()["status"] == "confirmed"
    restored = client.get(f"/v1/jobs/{job['id']}", headers=AUTH).json()
    assert [s["status"] for s in restored["steps"]] == [s["status"] for s in job["steps"]]


def test_finished_job_needs_no_handoff(client):
    j = client.post("/v1/jobs", headers=AUTH, json={"title": "t", "goal": "g", "steps": [{"phase": "p", "title": "s"}]}).json()
    client.post(f"/v1/jobs/{j['id']}/steps/{j['steps'][0]['id']}", headers=AUTH, json={"status": "done"})
    r = client.post(f"/v1/jobs/{j['id']}/handoffs", headers=AUTH, json={"from_model": "a", "to_model": "b"})
    assert r.status_code == 400


def test_handoff_endpoints_require_token(client, job):
    assert client.post(f"/v1/jobs/{job['id']}/handoffs", json={"from_model": "a", "to_model": "b"}).status_code == 401


def test_json_extraction_edge_cases():
    assert extract_json_object("kein json") is None
    assert extract_json_object('x {"a": 1} y {"b": 2}') == {"a": 1}
    assert extract_json_object("{kaputt} dann {\"ok\": true}") == {"ok": True}
