"""Acceptance tests for job memory, progress and token monitoring."""
import pytest
from fastapi.testclient import TestClient

from app import main
from app.jobs import JobStore, Thresholds, progress

TOKEN = "jobs-test-token"
AUTH = {"Authorization": f"Bearer {TOKEN}"}

STEPS = [
    {"phase": "Planung", "title": "Anforderungen", "weight": 1},
    {"phase": "Programmierung", "title": "Modul A", "weight": 2},
    {"phase": "Programmierung", "title": "Modul B", "weight": 2},
    {"phase": "Tests", "title": "Unit-Tests", "weight": 1},
]


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("LARA_AUTOMATION_TOKEN", TOKEN)
    monkeypatch.setenv("LARA_DB_PATH", str(tmp_path / "lara.db"))
    main.reset_store()
    yield TestClient(main.app)
    main.reset_store()


def new_job(client):
    r = client.post("/v1/jobs", headers=AUTH, json={"title": "Dateiverwaltung", "goal": "Dateien ordnen", "steps": STEPS})
    assert r.status_code == 201
    return r.json()


def set_step(client, job, index, status, **extra):
    step_id = job["steps"][index]["id"]
    return client.post(f"/v1/jobs/{job['id']}/steps/{step_id}", headers=AUTH, json={"status": status, **extra})


# 1. Auftrag starten und Fortschritt anzeigen
def test_new_job_starts_at_zero(client):
    job = new_job(client)
    assert job["overall_percent"] == 0.0
    assert job["open_steps"] == 4 and job["completed_steps"] == 0
    assert job["next_step"]["title"] == "Anforderungen"
    assert [p["phase"] for p in job["phases"]] == ["Planung", "Programmierung", "Tests"]


# 2. Schritte abschließen -> Prozent aus erledigtem Gewicht
def test_progress_is_computed_from_completed_weight(client):
    job = new_job(client)
    set_step(client, job, 0, "done")
    r = set_step(client, job, 1, "done")
    data = r.json()
    assert data["overall_percent"] == 50.0  # (1 + 2) / 6
    phases = {p["phase"]: p["percent"] for p in data["phases"]}
    assert phases == {"Planung": 100.0, "Programmierung": 50.0, "Tests": 0.0}


def test_in_progress_does_not_count_as_progress(client):
    job = new_job(client)
    data = set_step(client, job, 0, "in_progress").json()
    assert data["overall_percent"] == 0.0
    assert data["current_step"]["title"] == "Anforderungen"


def test_only_one_step_in_progress_per_job(client):
    job = new_job(client)
    set_step(client, job, 0, "in_progress")
    assert set_step(client, job, 1, "in_progress").status_code == 400


def test_failed_step_is_reported_as_problem(client):
    job = new_job(client)
    data = set_step(client, job, 3, "failed", result="2 Tests rot").json()
    assert data["state"] == "blockiert"
    assert data["problems"][0]["title"] == "Unit-Tests"


def test_all_done_is_100_and_completed(client):
    job = new_job(client)
    for i in range(4):
        data = set_step(client, job, i, "done").json()
    assert data["overall_percent"] == 100.0 and data["state"] == "abgeschlossen"


def test_parallel_jobs_are_separate(client):
    a, b = new_job(client), new_job(client)
    set_step(client, a, 0, "done")
    jobs = {j["id"]: j for j in client.get("/v1/jobs", headers=AUTH).json()["jobs"]}
    assert jobs[a["id"]]["overall_percent"] > 0 and jobs[b["id"]]["overall_percent"] == 0


# 3. Tokenverbrauch erfassen
def test_token_usage_is_recorded_per_job(client):
    job = new_job(client)
    for i, o in ((100, 20), (150, 30)):
        assert client.post("/v1/tokens", headers=AUTH, json={
            "provider": "ollama", "model": "qwen", "input_tokens": i, "output_tokens": o,
            "source": "api", "job_id": job["id"]}).status_code == 201
    tokens = client.get(f"/v1/jobs/{job['id']}", headers=AUTH).json()["tokens"]
    assert tokens == {"requests": 2, "requests_without_counts": 0, "input_tokens": 250,
                      "output_tokens": 50, "estimated": False}


def test_unknown_usage_is_not_invented(client):
    client.post("/v1/tokens", headers=AUTH, json={"provider": "pixel", "model": "m", "source": "unknown"})
    status = client.get("/v1/models/context", headers=AUTH, params={"model": "m"}).json()
    assert status["used"] is None and status["status"] == "unbekannt"
    bad = client.post("/v1/tokens", headers=AUTH, json={"provider": "p", "model": "m", "input_tokens": 5, "source": "unknown"})
    assert bad.status_code == 400


# 4. Warnung bei hoher Kontextauslastung
@pytest.mark.parametrize("used,expected", [(6000, "normal"), (7000, "beobachten"), (8500, "uebergabe_vorbereiten"), (9500, "wechsel_einleiten")])
def test_context_thresholds(client, used, expected):
    client.post("/v1/models/context-window", headers=AUTH, json={"model": "m", "context_window": 10000, "source": "user"})
    client.post("/v1/tokens", headers=AUTH, json={"provider": "p", "model": "m", "input_tokens": used, "output_tokens": 0, "source": "api"})
    status = client.get("/v1/models/context", headers=AUTH, params={"model": "m"}).json()
    assert status["status"] == expected
    assert status["remaining"] == 10000 - used
    assert status["context_window_source"] == "user"


def test_context_uses_latest_request_not_total(client):
    client.post("/v1/models/context-window", headers=AUTH, json={"model": "m", "context_window": 1000, "source": "api"})
    for n in (500, 300):
        client.post("/v1/tokens", headers=AUTH, json={"provider": "p", "model": "m", "input_tokens": n, "output_tokens": 0, "source": "api"})
    status = client.get("/v1/models/context", headers=AUTH, params={"model": "m"}).json()
    assert status["used"] == 300 and status["percent"] == 30.0
    assert status["total_consumption"]["input_tokens"] == 800


def test_unknown_window_is_reported_unknown(client):
    client.post("/v1/tokens", headers=AUTH, json={"provider": "p", "model": "x", "input_tokens": 10, "output_tokens": 1, "source": "api"})
    status = client.get("/v1/models/context", headers=AUTH, params={"model": "x"}).json()
    assert status["used"] == 11 and status["percent"] is None and status["status"] == "unbekannt"


def test_thresholds_are_configurable(monkeypatch):
    monkeypatch.setenv("LARA_CONTEXT_WATCH", "50")
    monkeypatch.setenv("LARA_CONTEXT_PREPARE", "60")
    monkeypatch.setenv("LARA_CONTEXT_SWITCH", "70")
    assert Thresholds.from_env().status(55) == "beobachten"
    monkeypatch.setenv("LARA_CONTEXT_SWITCH", "40")
    with pytest.raises(ValueError):
        Thresholds.from_env()


# 9. App-Neustart simulieren und Arbeitsstand wiederherstellen
def test_state_survives_restart_and_interruption_is_reported(client):
    job = new_job(client)
    set_step(client, job, 0, "done")
    set_step(client, job, 1, "in_progress")
    main.reset_store()  # simulated crash/restart: new connection to the same file
    restored = client.get(f"/v1/jobs/{job['id']}", headers=AUTH).json()
    assert restored["overall_percent"] == job["overall_percent"] + round(100 / 6, 1)
    assert restored["current_step"]["title"] == "Modul A"
    report = client.get("/v1/recovery", headers=AUTH).json()["interrupted"]
    assert report[0]["job_id"] == job["id"] and report[0]["interrupted_step"]["title"] == "Modul A"


def test_failed_write_is_rolled_back(tmp_path):
    store = JobStore(str(tmp_path / "x.db"))
    with pytest.raises(ValueError):
        store.create_job("t", "g", [{"phase": "p", "title": "ok"}, {"phase": "p", "title": "bad", "weight": 0}])
    assert store.list_jobs() == []
    store.close()


# Security
def test_job_endpoints_require_token(client):
    assert client.get("/v1/jobs").status_code == 401
    assert client.post("/v1/tokens", json={"provider": "p", "model": "m", "source": "unknown"}).status_code == 401
    assert client.get("/v1/models/context", params={"model": "m"}).status_code == 401


def test_progress_helper_handles_empty():
    assert progress([])["overall_percent"] == 0.0
