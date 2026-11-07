"""Order drafts: Ja / Nein / Andere Idee, decided only with the approver token."""
import pytest
from fastapi.testclient import TestClient

from app import main

CLIENT = {"Authorization": "Bearer client-tok"}
APPROVER = {"Authorization": "Bearer approver-tok"}
DRAFT = {"title": "Dateiverwaltung", "goal": "Downloads sortieren",
         "details": {"acceptance_criteria": ["Keine Datei geht verloren"], "estimated_cost": "0 €",
                     "steps": [{"phase": "Plan", "title": "Regeln"}, {"phase": "Code", "title": "Sortierer", "weight": 2}]}}


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("LARA_AUTOMATION_TOKEN", "client-tok")
    monkeypatch.setenv("LARA_APPROVER_TOKEN", "approver-tok")
    monkeypatch.setenv("LARA_DB_PATH", str(tmp_path / "lara.db"))
    main.reset_store()
    yield TestClient(main.app)
    main.reset_store()


def new_draft(client, body=DRAFT):
    r = client.post("/v1/drafts", headers=CLIENT, json=body)
    assert r.status_code == 201, r.text
    return r.json()


def decide(client, d, decision, note=None, headers=APPROVER):
    return client.post(f"/v1/drafts/{d['id']}/decision", headers=headers, json={"decision": decision, "note": note})


def test_new_draft_is_not_a_job(client):
    d = new_draft(client)
    assert d["status"] == "draft" and d["job_id"] is None
    assert client.get("/v1/jobs", headers=CLIENT).json()["jobs"] == []


def test_yes_creates_job_with_steps(client):
    d = new_draft(client)
    r = decide(client, d, "approve").json()
    assert r["status"] == "approved" and r["job_id"]
    job = client.get(f"/v1/jobs/{r['job_id']}", headers=CLIENT).json()
    assert [s["title"] for s in job["steps"]] == ["Regeln", "Sortierer"]
    assert job["overall_percent"] == 0.0


def test_no_closes_draft_without_job(client):
    d = new_draft(client)
    r = decide(client, d, "reject").json()
    assert r["status"] == "rejected" and r["job_id"] is None
    assert client.get("/v1/jobs", headers=CLIENT).json()["jobs"] == []


def test_other_idea_needs_note_and_allows_rework(client):
    d = new_draft(client)
    assert decide(client, d, "revise").status_code == 400
    r = decide(client, d, "revise", "Lieber nach Datum sortieren").json()
    assert r["status"] == "revise" and r["note"] == "Lieber nach Datum sortieren"
    reworked = client.put(f"/v1/drafts/{d['id']}", headers=CLIENT,
                          json={**DRAFT, "goal": "Downloads nach Datum sortieren"}).json()
    assert reworked["status"] == "draft" and reworked["goal"].endswith("Datum sortieren")
    assert decide(client, reworked, "approve").json()["status"] == "approved"


def test_client_token_cannot_decide(client):
    d = new_draft(client)
    assert decide(client, d, "approve", headers=CLIENT).status_code == 401
    assert client.get("/v1/jobs", headers=CLIENT).json()["jobs"] == []


def test_decision_only_once(client):
    d = new_draft(client)
    decide(client, d, "approve")
    assert decide(client, d, "reject").status_code == 400
    assert client.put(f"/v1/drafts/{d['id']}", headers=CLIENT, json=DRAFT).status_code == 400


def test_default_single_step_when_none_given(client):
    d = new_draft(client, {"title": "Kurz", "goal": "Etwas"})
    job_id = decide(client, d, "approve").json()["job_id"]
    steps = client.get(f"/v1/jobs/{job_id}", headers=CLIENT).json()["steps"]
    assert [(s["phase"], s["title"]) for s in steps] == [("Umsetzung", "Kurz")]


def test_list_filters_by_status(client):
    a, b = new_draft(client), new_draft(client)
    decide(client, a, "reject")
    open_ids = [x["id"] for x in client.get("/v1/drafts", headers=CLIENT, params={"status": "draft"}).json()["drafts"]]
    assert open_ids == [b["id"]]


def test_invalid_steps_rejected(client):
    bad = {"title": "t", "goal": "g", "details": {"steps": [{"phase": "p", "title": "s", "weight": 0}]}}
    assert client.post("/v1/drafts", headers=CLIENT, json=bad).status_code == 422


def test_drafts_survive_restart(client):
    d = new_draft(client)
    main.reset_store()
    assert client.get("/v1/drafts", headers=CLIENT).json()["drafts"][0]["id"] == d["id"]


def test_endpoints_require_tokens(client):
    assert client.post("/v1/drafts", json=DRAFT).status_code == 401
    assert client.get("/v1/drafts").status_code == 401
