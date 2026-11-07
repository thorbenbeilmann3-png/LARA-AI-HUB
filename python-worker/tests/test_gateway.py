"""Gateway tests with a mocked local model server (no network)."""
import httpx
import pytest
from fastapi.testclient import TestClient

from app import gateway, main

TOKEN = "gw-token"
AUTH = {"Authorization": f"Bearer {TOKEN}"}
MSG = {"model": "qwen2.5-0.5b-instruct-q4_k_m", "messages": [{"role": "user", "content": "Hallo"}]}


class Upstream:
    def __init__(self, chat=None, show=None, chat_status=200, show_status=200, fail=False):
        self.calls = []
        self.chat = chat if chat is not None else {"choices": [{"message": {"content": "Hallo Thorben!"}}],
                                                   "usage": {"prompt_tokens": 20, "completion_tokens": 5}}
        self.show, self.chat_status, self.show_status, self.fail = show, chat_status, show_status, fail

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.calls.append((request.method, str(request.url)))
        if self.fail:
            raise httpx.ConnectError("refused")
        if request.url.path == "/api/show":
            return httpx.Response(self.show_status, json=self.show or {})
        return httpx.Response(self.chat_status, json=self.chat)


@pytest.fixture
def setup(tmp_path, monkeypatch):
    monkeypatch.setenv("LARA_AUTOMATION_TOKEN", TOKEN)
    monkeypatch.setenv("LARA_DB_PATH", str(tmp_path / "lara.db"))
    monkeypatch.delenv("LARA_LOCAL_MODEL_URL", raising=False)
    main.reset_store()

    def install(up: Upstream):
        monkeypatch.setattr(gateway, "client_factory",
                            lambda: httpx.AsyncClient(transport=httpx.MockTransport(up.handler)))
        return up

    yield TestClient(main.app), install
    main.reset_store()


def test_answer_and_reported_tokens_are_recorded(setup):
    client, install = setup
    up = install(Upstream(show={"model_info": {"qwen2.context_length": 32768}}))
    r = client.post("/v1/gateway/local/chat", headers=AUTH, json=MSG)
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["answer"] == "Hallo Thorben!"
    ctx = data["context"]
    assert ctx["used"] == 25 and ctx["usage_source"] == "api"
    assert ctx["context_window"] == 32768 and ctx["context_window_source"] == "api"
    assert ctx["status"] == "normal"
    assert ("POST", "http://127.0.0.1:12434/v1/chat/completions") in up.calls


def test_missing_usage_is_recorded_as_unknown(setup):
    client, install = setup
    install(Upstream(chat={"choices": [{"message": {"content": "ok"}}]}, show_status=404))
    ctx = client.post("/v1/gateway/local/chat", headers=AUTH, json=MSG).json()["context"]
    assert ctx["used"] is None and ctx["status"] == "unbekannt"
    assert ctx["total_consumption"]["requests_without_counts"] == 1


def test_user_context_window_is_not_overwritten(setup):
    client, install = setup
    install(Upstream(show={"model_info": {"x.context_length": 99999}}))
    client.post("/v1/models/context-window", headers=AUTH, json={"model": MSG["model"], "context_window": 4096, "source": "user"})
    ctx = client.post("/v1/gateway/local/chat", headers=AUTH, json=MSG).json()["context"]
    assert ctx["context_window"] == 4096 and ctx["context_window_source"] == "user"


def test_tokens_are_attached_to_job(setup):
    client, install = setup
    install(Upstream())
    job = client.post("/v1/jobs", headers=AUTH, json={"title": "t", "goal": "g", "steps": [{"phase": "p", "title": "s"}]}).json()
    client.post("/v1/gateway/local/chat", headers=AUTH, json={**MSG, "job_id": job["id"]})
    assert client.get(f"/v1/jobs/{job['id']}", headers=AUTH).json()["tokens"]["input_tokens"] == 20


def test_unknown_job_is_rejected_before_model_call(setup):
    client, install = setup
    up = install(Upstream())
    r = client.post("/v1/gateway/local/chat", headers=AUTH, json={**MSG, "job_id": "a" * 32})
    assert r.status_code == 404 and up.calls == []


def test_unreachable_server_gives_502(setup):
    client, install = setup
    install(Upstream(fail=True))
    assert client.post("/v1/gateway/local/chat", headers=AUTH, json=MSG).status_code == 502


def test_upstream_error_status_gives_502(setup):
    client, install = setup
    install(Upstream(chat_status=404))
    assert client.post("/v1/gateway/local/chat", headers=AUTH, json=MSG).status_code == 502


def test_malformed_answer_gives_502(setup):
    client, install = setup
    install(Upstream(chat={"weird": True}))
    assert client.post("/v1/gateway/local/chat", headers=AUTH, json=MSG).status_code == 502


@pytest.mark.parametrize("url", ["http://10.186.32.74:12434", "http://example.com", "file:///x", "http://127.0.0.1:12434/v1"])
def test_non_loopback_or_odd_upstream_is_refused(setup, monkeypatch, url):
    client, install = setup
    up = install(Upstream())
    monkeypatch.setenv("LARA_LOCAL_MODEL_URL", url)
    assert client.post("/v1/gateway/local/chat", headers=AUTH, json=MSG).status_code == 503
    assert up.calls == []


def test_gateway_requires_token(setup):
    client, _ = setup
    assert client.post("/v1/gateway/local/chat", json=MSG).status_code == 401


def test_client_cannot_choose_target(setup):
    client, install = setup
    install(Upstream())
    r = client.post("/v1/gateway/local/chat", headers=AUTH, json={**MSG, "url": "http://evil.example"})
    assert r.status_code == 422
