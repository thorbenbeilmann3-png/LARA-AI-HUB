from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)
TOKEN = "test-token"
AUTH = {"Authorization": f"Bearer {TOKEN}"}


def setup_function():
    import os
    os.environ["LARA_AUTOMATION_TOKEN"] = TOKEN


def test_openai_provider_is_registered(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    response = client.post(
        "/v1/chat",
        headers=AUTH,
        json={"provider": "openai", "model": "gpt-model-name", "prompt": "Test"},
    )
    assert response.status_code == 503
    assert response.json()["detail"] == "OpenAI API key is not configured on the server"


def test_unknown_provider_is_rejected():
    response = client.post(
        "/v1/chat",
        headers=AUTH,
        json={"provider": "chatgpt-plus-session", "model": "any", "prompt": "Test"},
    )
    assert response.status_code == 422
