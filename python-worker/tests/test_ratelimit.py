from fastapi.testclient import TestClient

from app import main
from app.ratelimit import RateLimiter

client = TestClient(main.app)


def test_health_reveals_no_provider_details():
    data = client.get("/health").json()
    assert data == {"status": "ok", "service": "lara-core", "api_version": "0.4.0"}


def test_general_limit_returns_429_with_retry_after(monkeypatch):
    monkeypatch.setenv("LARA_RATE_LIMIT_PER_MIN", "3")
    codes = [client.get("/health").status_code for _ in range(4)]
    assert codes == [200, 200, 200, 429]
    r = client.get("/health")
    assert r.status_code == 429 and int(r.headers["Retry-After"]) >= 1


def test_repeated_wrong_tokens_lock_out_client(monkeypatch):
    monkeypatch.setenv("LARA_AUTOMATION_TOKEN", "right")
    monkeypatch.setenv("LARA_AUTH_FAIL_LIMIT", "3")
    for _ in range(3):
        assert client.get("/v1/jobs", headers={"Authorization": "Bearer wrong"}).status_code == 401
    # Even the correct token is refused during the lockout window.
    assert client.get("/v1/jobs", headers={"Authorization": "Bearer right"}).status_code == 429


def test_windows_expire():
    rl = RateLimiter()
    import os
    os.environ["LARA_RATE_LIMIT_PER_MIN"] = "1"
    try:
        assert rl.check("c", now=0.0) is None
        assert rl.check("c", now=10.0) is not None
        assert rl.check("c", now=61.0) is None
        for t in range(10):
            rl.auth_failed("d", now=float(t))
        assert rl.check("d", now=20.0) is not None
        assert rl.check("d", now=400.0) is None
    finally:
        del os.environ["LARA_RATE_LIMIT_PER_MIN"]


def test_clients_are_separate():
    rl = RateLimiter()
    import os
    os.environ["LARA_RATE_LIMIT_PER_MIN"] = "1"
    try:
        assert rl.check("a", now=0.0) is None
        assert rl.check("b", now=0.0) is None
    finally:
        del os.environ["LARA_RATE_LIMIT_PER_MIN"]
