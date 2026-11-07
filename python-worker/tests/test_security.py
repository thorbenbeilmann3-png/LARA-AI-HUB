"""Security regression tests: chat authentication and server-side approvals."""
import pytest
from fastapi.testclient import TestClient

from app import main
from app.main import app

client = TestClient(app)
CLIENT_TOKEN = "client-token-for-tests"
APPROVER_TOKEN = "approver-token-for-tests"
CLIENT = {"Authorization": f"Bearer {CLIENT_TOKEN}"}
APPROVER = {"Authorization": f"Bearer {APPROVER_TOKEN}"}
GUARDED = "test.guarded_task"


@pytest.fixture(autouse=True)
def env(monkeypatch):
    monkeypatch.setenv("LARA_AUTOMATION_TOKEN", CLIENT_TOKEN)
    monkeypatch.setenv("LARA_APPROVER_TOKEN", APPROVER_TOKEN)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    # A registered task that needs approval. It has no implementation, so a request
    # that passes the approval check ends in 404; a rejected one ends in 403.
    monkeypatch.setitem(main.TASKS, GUARDED, {"description": "Guarded test task", "approval_required": True})
    main.approvals.clear()
    yield
    main.approvals.clear()


def run(task_id=GUARDED, **extra):
    return client.post("/v1/automation/run", headers=CLIENT, json={"task_id": task_id, **extra})


def new_approval():
    r = client.post("/v1/approvals", headers=CLIENT, json={"task_id": GUARDED})
    assert r.status_code == 201
    return r.json()["approval_id"]


# --- /v1/chat authentication -------------------------------------------------

def test_chat_without_token_is_rejected():
    r = client.post("/v1/chat", json={"provider": "openai", "model": "m", "prompt": "x"})
    assert r.status_code == 401


def test_chat_with_wrong_token_is_rejected():
    r = client.post("/v1/chat", headers={"Authorization": "Bearer wrong"},
                    json={"provider": "openai", "model": "m", "prompt": "x"})
    assert r.status_code == 401


def test_chat_with_approver_token_is_rejected():
    r = client.post("/v1/chat", headers=APPROVER, json={"provider": "openai", "model": "m", "prompt": "x"})
    assert r.status_code == 401


def test_chat_disabled_when_no_token_configured(monkeypatch):
    monkeypatch.delenv("LARA_AUTOMATION_TOKEN")
    r = client.post("/v1/chat", headers=CLIENT, json={"provider": "openai", "model": "m", "prompt": "x"})
    assert r.status_code == 503


def test_chat_with_valid_token_reaches_provider_logic():
    r = client.post("/v1/chat", headers=CLIENT, json={"provider": "openai", "model": "m", "prompt": "x"})
    assert r.status_code == 503
    assert r.json()["detail"] == "OpenAI API key is not configured on the server"


# --- client-side "approved" flag is worthless ---------------------------------

def test_client_approved_flag_does_not_authorize():
    r = run(approved=True)
    assert r.status_code == 403


def test_workflow_request_with_approved_flag_is_not_executed():
    r = client.post("/v1/automation/request", headers=CLIENT, json={"workflow_id": "x", "approved": True})
    assert r.status_code == 404


# --- server-side approval flow ------------------------------------------------

def test_pending_approval_does_not_authorize():
    approval_id = new_approval()
    assert run(approval_id=approval_id).status_code == 403


def test_client_token_cannot_approve():
    approval_id = new_approval()
    r = client.post(f"/v1/approvals/{approval_id}/decision", headers=CLIENT, json={"decision": "approve"})
    assert r.status_code == 401
    assert run(approval_id=approval_id).status_code == 403


def test_client_token_cannot_list_approvals():
    assert client.get("/v1/approvals", headers=CLIENT).status_code == 401


def test_approved_approval_is_single_use():
    approval_id = new_approval()
    listed = client.get("/v1/approvals", headers=APPROVER).json()["approvals"]
    assert [a["approval_id"] for a in listed] == [approval_id]
    r = client.post(f"/v1/approvals/{approval_id}/decision", headers=APPROVER, json={"decision": "approve"})
    assert r.status_code == 200 and r.json()["status"] == "approved"
    first = run(approval_id=approval_id)
    # The guarded test task has no implementation, so after a successful approval
    # check it falls through to "not registered" handling -> 404, not 403.
    assert first.status_code == 404
    assert run(approval_id=approval_id).status_code == 403


def test_denied_approval_does_not_authorize():
    approval_id = new_approval()
    r = client.post(f"/v1/approvals/{approval_id}/decision", headers=APPROVER, json={"decision": "deny"})
    assert r.status_code == 200 and r.json()["status"] == "denied"
    assert run(approval_id=approval_id).status_code == 403


def test_approval_is_bound_to_its_task(monkeypatch):
    monkeypatch.setitem(main.TASKS, "test.other_task", {"description": "Other", "approval_required": True})
    approval_id = new_approval()
    client.post(f"/v1/approvals/{approval_id}/decision", headers=APPROVER, json={"decision": "approve"})
    assert run("test.other_task", approval_id=approval_id).status_code == 403


def test_expired_approval_does_not_authorize(monkeypatch):
    approval_id = new_approval()
    client.post(f"/v1/approvals/{approval_id}/decision", headers=APPROVER, json={"decision": "approve"})
    real_time = main.time.time
    monkeypatch.setattr(main.time, "time", lambda: real_time() + main.APPROVAL_TTL_SECONDS + 1)
    assert run(approval_id=approval_id).status_code == 403


def test_decision_on_unknown_approval_is_404():
    r = client.post("/v1/approvals/" + "a" * 32 + "/decision", headers=APPROVER, json={"decision": "approve"})
    assert r.status_code == 404


def test_approvals_disabled_when_tokens_are_identical(monkeypatch):
    monkeypatch.setenv("LARA_APPROVER_TOKEN", CLIENT_TOKEN)
    assert client.get("/v1/approvals", headers=CLIENT).status_code == 503


def test_approval_for_task_without_approval_requirement_is_rejected():
    r = client.post("/v1/approvals", headers=CLIENT, json={"task_id": "python.runtime_info"})
    assert r.status_code == 400


def test_pending_approval_limit(monkeypatch):
    monkeypatch.setattr(main, "MAX_PENDING_APPROVALS", 2)
    new_approval(); new_approval()
    r = client.post("/v1/approvals", headers=CLIENT, json={"task_id": GUARDED})
    assert r.status_code == 429
