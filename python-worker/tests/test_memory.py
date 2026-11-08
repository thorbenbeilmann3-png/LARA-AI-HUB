"""LARA memory: trust status, supersede instead of overwrite, secret guard, export."""
import pytest
from fastapi.testclient import TestClient

from app import main
from app.memory import looks_like_secret

AUTH = {"Authorization": "Bearer mem-tok"}


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("LARA_AUTOMATION_TOKEN", "mem-tok")
    monkeypatch.setenv("LARA_DB_PATH", str(tmp_path / "lara.db"))
    main.reset_store()
    yield TestClient(main.app)
    main.reset_store()


def add(client, **kw):
    body = {"category": "technik", "content": "Modellserver auf 127.0.0.1:12434", "source": "Nutzertest Pixel",
            "trust": "BESTÄTIGT", **kw}
    r = client.post("/v1/memory", headers=AUTH, json=body)
    return r


def test_add_and_search_by_category_project_and_text(client):
    add(client, project="LARA")
    add(client, category="praeferenz", content="Antworten auf Deutsch", source="Nutzer", trust="NUTZERANGABE")
    tech = client.get("/v1/memory", headers=AUTH, params={"category": "technik"}).json()["entries"]
    assert [e["content"] for e in tech] == ["Modellserver auf 127.0.0.1:12434"]
    hit = client.get("/v1/memory", headers=AUTH, params={"q": "Deutsch"}).json()["entries"]
    assert hit[0]["trust"] == "NUTZERANGABE"
    proj = client.get("/v1/memory", headers=AUTH, params={"project": "LARA"}).json()["entries"]
    assert len(proj) == 1


def test_replace_marks_old_outdated_and_links(client):
    old = add(client, content="Modellserver auf 10.179.130.230:12434", trust="ANNAHME").json()
    new = client.post(f"/v1/memory/{old['id']}/replace", headers=AUTH,
                      json={"content": "Modellserver auf 127.0.0.1:12434", "source": "Test 07.11.", "trust": "BESTÄTIGT"}).json()
    assert new["supersedes"] == old["id"]
    current = client.get("/v1/memory", headers=AUTH).json()["entries"]
    assert [e["id"] for e in current] == [new["id"]]
    everything = client.get("/v1/memory", headers=AUTH, params={"include_outdated": True}).json()["entries"]
    stale = [e for e in everything if e["id"] == old["id"]][0]
    assert stale["trust"] == "VERALTET" and stale["superseded_by"] == new["id"]
    again = client.post(f"/v1/memory/{old['id']}/replace", headers=AUTH, json={"content": "x", "source": "y", "trust": "ANNAHME"})
    assert again.status_code == 400


def test_trust_can_be_updated_but_not_set_to_outdated(client):
    e = add(client, trust="UNGEPRÜFT").json()
    assert client.post(f"/v1/memory/{e['id']}/trust", headers=AUTH, json={"trust": "BESTÄTIGT"}).json()["trust"] == "BESTÄTIGT"
    assert client.post(f"/v1/memory/{e['id']}/trust", headers=AUTH, json={"trust": "VERALTET"}).status_code == 422


@pytest.mark.parametrize("secret", [
    "Mein Schlüssel sk-ant-abcdefghijklmnop1234",
    "api_key = 1234567890abcdef",
    "Passwort: geheim123!",
    "Authorization: Bearer abcdefghijklmnopqrstuvwxyz",
    "-----BEGIN RSA PRIVATE KEY-----",
])
def test_secrets_are_rejected(client, secret):
    assert add(client, content=secret).status_code == 400
    assert looks_like_secret(secret)


def test_normal_text_is_not_flagged():
    assert not looks_like_secret("Der Token-Monitor zeigt die Kontextauslastung.")
    assert not looks_like_secret("Port 12434 auf dem Pixel")


def test_unknown_category_rejected(client):
    assert add(client, category="geheim").status_code == 422


def test_review_due(client):
    add(client, review_at=1.0)
    add(client)
    due = client.get("/v1/memory/review", headers=AUTH).json()["due"]
    assert len(due) == 1


def test_export_markdown_files(client):
    old = add(client, content="alt", trust="ANNAHME").json()
    client.post(f"/v1/memory/{old['id']}/replace", headers=AUTH, json={"content": "neu", "source": "s", "trust": "BESTÄTIGT"})
    files = client.get("/v1/memory/export", headers=AUTH).json()["files"]
    assert set(files) >= {"USER_PREFERENCES.md", "TECHNICAL_STATE.md", "DECISIONS.md", "MEMORY_CHANGELOG.md"}
    assert "[BESTÄTIGT] neu" in files["TECHNICAL_STATE.md"] and "alt" not in files["TECHNICAL_STATE.md"]
    assert "alt" in files["MEMORY_CHANGELOG.md"]


def test_memory_survives_restart(client):
    add(client)
    main.reset_store()
    assert len(client.get("/v1/memory", headers=AUTH).json()["entries"]) == 1


def test_requires_token(client):
    assert client.get("/v1/memory").status_code == 401
    body = {"category": "technik", "content": "x", "source": "y", "trust": "ANNAHME"}
    assert client.post("/v1/memory", json=body).status_code == 401
