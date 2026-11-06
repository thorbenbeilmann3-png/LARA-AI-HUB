#!/usr/bin/env python3
"""Loopback-only Automate -> Python bridge. No arbitrary code or shell execution."""
from __future__ import annotations
import argparse, json, os, secrets, stat
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

VERSION = "0.1.0"
MAX_BODY_BYTES = 4096
DEFAULT_PORT = 8765

def bridge_home() -> Path:
    override = os.environ.get("LARA_BRIDGE_HOME")
    return Path(override).expanduser() if override else Path.home() / ".lara_bridge"

def load_or_create_token(home: Path | None = None) -> tuple[str, bool]:
    directory = home or bridge_home()
    directory.mkdir(parents=True, exist_ok=True)
    try:
        directory.chmod(0o700)
    except OSError:
        pass
    token_file = directory / "token"
    if token_file.exists():
        token = token_file.read_text(encoding="utf-8").strip()
        if len(token) < 32:
            raise RuntimeError("Token-Datei ist ungültig. Datei löschen und Dienst neu starten.")
        return token, False
    token = secrets.token_urlsafe(32)
    fd = os.open(token_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, stat.S_IRUSR | stat.S_IWUSR)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(token + "\n")
    try:
        token_file.chmod(0o600)
    except OSError:
        pass
    return token, True

class BridgeServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True
    def __init__(self, address: tuple[str, int], token: str):
        super().__init__(address, BridgeHandler)
        self.token = token

class BridgeHandler(BaseHTTPRequestHandler):
    server: BridgeServer
    protocol_version = "HTTP/1.1"
    def log_message(self, fmt: str, *args: Any) -> None:
        print("[LARA-Bridge] " + (fmt % args))
    def _send(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
    def _authorized(self) -> bool:
        supplied = self.headers.get("Authorization", "")
        expected = "Bearer " + self.server.token
        return secrets.compare_digest(supplied, expected)
    def do_GET(self) -> None:
        if not self._authorized():
            self._send(401, {"ok": False, "error": "unauthorized"})
            return
        if self.path != "/v1/health":
            self._send(404, {"ok": False, "error": "not_found"})
            return
        self._send(200, {"ok": True, "service": "lara-python-bridge", "version": VERSION})
    def do_POST(self) -> None:
        if not self._authorized():
            self._send(401, {"ok": False, "error": "unauthorized"})
            return
        if self.path != "/v1/tasks/echo":
            self._send(404, {"ok": False, "error": "not_found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._send(400, {"ok": False, "error": "invalid_content_length"})
            return
        if length < 1 or length > MAX_BODY_BYTES:
            self._send(413, {"ok": False, "error": "body_size_out_of_range", "max_bytes": MAX_BODY_BYTES})
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._send(400, {"ok": False, "error": "invalid_json"})
            return
        if not isinstance(payload, dict) or payload.get("task") != "echo":
            self._send(400, {"ok": False, "error": "unsupported_task", "allowed_tasks": ["echo"]})
            return
        text = payload.get("text")
        if not isinstance(text, str) or not text.strip() or len(text) > 1000:
            self._send(400, {"ok": False, "error": "text_must_be_1_to_1000_characters"})
            return
        self._send(200, {"ok": True, "task": "echo", "result": text, "runtime": "python"})

def make_server(port: int, token: str) -> BridgeServer:
    # Deliberately bind only to loopback; no LAN or public-interface exposure.
    return BridgeServer(("127.0.0.1", port), token)

def main() -> None:
    parser = argparse.ArgumentParser(description="LARA loopback Python bridge")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error("Port muss zwischen 1024 und 65535 liegen.")
    token, created = load_or_create_token()
    server = make_server(args.port, token)
    print(f"LARA Python Bridge gestartet: http://127.0.0.1:{args.port}")
    print(f"Token-Datei: {bridge_home() / 'token'}")
    if created:
        print("AUTOMATE_BEARER_TOKEN=" + token)
        print("Token einmalig in Automate als Variable speichern. Nicht weitergeben.")
    else:
        print("Gespeicherter Token wiederverwendet; Token nicht erneut ausgegeben.")
    print("Erlaubt: GET /v1/health und POST /v1/tasks/echo. Keine Code- oder Shell-Ausführung.")
    try:
        server.serve_forever(poll_interval=0.25)
    except KeyboardInterrupt:
        print("\nLARA Python Bridge wird beendet …")
    finally:
        server.server_close()

if __name__ == "__main__":
    main()
