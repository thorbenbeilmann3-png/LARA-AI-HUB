#!/usr/bin/env python3
"""Start the LARA Core on this device (e.g. in Pydroid 3 on the Pixel).

- Listens only on 127.0.0.1 (LARA_CORE_PORT, default 8000).
- Creates two random tokens once in ~/.lara_core (permissions 0600):
  automation_token (for the LARA app / Automate) and approver_token (for approvals).
  Tokens are printed only when they are created, never again.
- Stores the job database in ~/.lara_core/lara.db.
No arbitrary code or shell execution is exposed.
"""
from __future__ import annotations

import os
import secrets
import stat
import sys
from pathlib import Path


def core_home() -> Path:
    override = os.environ.get("LARA_CORE_HOME")
    return Path(override).expanduser() if override else Path.home() / ".lara_core"


def load_or_create_token(home: Path, name: str) -> tuple[str, bool]:
    home.mkdir(parents=True, exist_ok=True)
    try:
        home.chmod(0o700)
    except OSError:
        pass
    path = home / name
    if path.exists():
        token = path.read_text(encoding="utf-8").strip()
        if len(token) < 32:
            raise RuntimeError(f"{path} ist ungültig. Datei löschen und neu starten.")
        return token, False
    token = secrets.token_urlsafe(32)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, stat.S_IRUSR | stat.S_IWUSR)
    with os.fdopen(fd, "w", encoding="utf-8") as fh:
        fh.write(token + "\n")
    return token, True


def prepare_environment(home: Path) -> dict[str, bool]:
    automation, new_a = load_or_create_token(home, "automation_token")
    approver, new_b = load_or_create_token(home, "approver_token")
    if secrets.compare_digest(automation, approver):
        raise RuntimeError("Tokens dürfen nicht gleich sein.")
    os.environ["LARA_AUTOMATION_TOKEN"] = automation
    os.environ["LARA_APPROVER_TOKEN"] = approver
    os.environ.setdefault("LARA_DB_PATH", str(home / "lara.db"))
    os.environ.setdefault("LARA_LOCAL_MODEL_URL", "http://127.0.0.1:12434")
    return {"automation_token": new_a, "approver_token": new_b}


def main() -> None:
    try:
        import uvicorn
    except ImportError:
        sys.exit("uvicorn fehlt. In Pydroid: Menü → Pip → fastapi, uvicorn, httpx, pydantic installieren.")
    home = core_home()
    created = prepare_environment(home)
    port = int(os.environ.get("LARA_CORE_PORT", "8000"))
    if not 1024 <= port <= 65535:
        sys.exit("LARA_CORE_PORT muss zwischen 1024 und 65535 liegen.")
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from app.main import app

    print(f"LARA Core startet: http://127.0.0.1:{port}  (Datenbank: {os.environ['LARA_DB_PATH']})")
    for name, is_new in created.items():
        if is_new:
            print(f"NEU {name.upper()}={os.environ['LARA_' + name.upper()]}")
    if any(created.values()):
        print("Token einmalig in LARA (Monitor → Core-Token) eintragen. Nicht weitergeben, keine Screenshots.")
    else:
        print(f"Gespeicherte Tokens in {home} werden verwendet; sie werden nicht erneut angezeigt.")
    uvicorn.run(app, host="127.0.0.1", port=port, log_level="info", access_log=False)


if __name__ == "__main__":
    main()
