import os
import stat

import pytest

import run_core


def test_tokens_created_once_with_private_permissions(tmp_path, monkeypatch):
    monkeypatch.delenv("LARA_DB_PATH", raising=False)
    monkeypatch.delenv("LARA_LOCAL_MODEL_URL", raising=False)
    monkeypatch.delenv("OLLAMA_BASE_URL", raising=False)
    first = run_core.prepare_environment(tmp_path)
    assert first == {"automation_token": True, "approver_token": True}
    a = os.environ["LARA_AUTOMATION_TOKEN"]
    b = os.environ["LARA_APPROVER_TOKEN"]
    assert a != b and len(a) >= 32
    assert os.environ["OLLAMA_BASE_URL"] == os.environ["LARA_LOCAL_MODEL_URL"] == "http://127.0.0.1:12434"
    for name in ("automation_token", "approver_token"):
        mode = stat.S_IMODE((tmp_path / name).stat().st_mode)
        assert mode & 0o077 == 0
    second = run_core.prepare_environment(tmp_path)
    assert second == {"automation_token": False, "approver_token": False}
    assert os.environ["LARA_AUTOMATION_TOKEN"] == a
    assert os.environ["LARA_DB_PATH"] == str(tmp_path / "lara.db")


def test_short_token_file_is_rejected(tmp_path):
    (tmp_path / "automation_token").write_text("kurz")
    with pytest.raises(RuntimeError):
        run_core.prepare_environment(tmp_path)


def test_listens_on_loopback_only():
    source = open(run_core.__file__, encoding="utf-8").read()
    assert 'host="127.0.0.1"' in source and "0.0.0.0" not in source
