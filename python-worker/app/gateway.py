"""LARA Core gateway for the local model server (first provider of the AI gateway).

- The upstream address is fixed by configuration (LARA_LOCAL_MODEL_URL, default
  http://127.0.0.1:12434) and must be loopback: clients never choose the target.
- Every request records its token usage in the job store, with source "api" when the
  server reports counts and "unknown" otherwise (never invented).
- The context window is read once from the server (/api/show, Ollama format) when
  available and stored with source "api"; a user-entered value is never overwritten.
"""
from __future__ import annotations

import os
import time
from typing import Any, Callable
from urllib.parse import urlparse

import httpx

from .jobs import JobStore, Thresholds

LOOPBACK_HOSTS = {"127.0.0.1", "localhost", "::1"}

# Tests replace this factory to inject a mock transport.
client_factory: Callable[[], httpx.AsyncClient] = lambda: httpx.AsyncClient(
    timeout=httpx.Timeout(120.0, connect=5.0), follow_redirects=False
)


class GatewayError(Exception):
    def __init__(self, status: int, message: str) -> None:
        super().__init__(message)
        self.status = status


def local_model_url() -> str:
    url = os.getenv("LARA_LOCAL_MODEL_URL", "http://127.0.0.1:12434").rstrip("/")
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or parsed.hostname not in LOOPBACK_HOSTS:
        raise GatewayError(503, "LARA_LOCAL_MODEL_URL muss auf dieses Gerät zeigen (127.0.0.1/localhost)")
    if parsed.path not in ("", "/") or parsed.query or parsed.username:
        raise GatewayError(503, "LARA_LOCAL_MODEL_URL darf nur Schema, Host und Port enthalten")
    return url


def _context_length(model_info: Any) -> int | None:
    if not isinstance(model_info, dict):
        return None
    for key, value in model_info.items():
        if key.endswith(".context_length") and isinstance(value, int) and value > 0:
            return value
    return None


async def _discover_context_window(client: httpx.AsyncClient, base: str, model: str) -> int | None:
    try:
        r = await client.post(f"{base}/api/show", json={"model": model})
        if r.status_code != 200:
            return None
        return _context_length(r.json().get("model_info"))
    except (httpx.HTTPError, ValueError, AttributeError):
        return None


async def chat_local(store: JobStore, model: str, messages: list[dict[str, str]],
                     job_id: str | None, thresholds: Thresholds) -> dict[str, Any]:
    base = local_model_url()
    if job_id is not None:
        store.get_job(job_id)  # KeyError -> 404 before any model call
    async with client_factory() as client:
        if store.context_status(model, thresholds)["context_window"] is None:
            window = await _discover_context_window(client, base, model)
            if window:
                store.set_context_window(model, window, "api")
        try:
            r = await client.post(f"{base}/v1/chat/completions",
                                  json={"model": model, "messages": messages, "stream": False})
        except httpx.HTTPError as exc:
            raise GatewayError(502, f"Lokaler Modellserver nicht erreichbar: {type(exc).__name__}") from exc
    if r.status_code != 200:
        raise GatewayError(502, f"Lokaler Modellserver antwortet mit HTTP {r.status_code}")
    try:
        data = r.json()
        answer = data["choices"][0]["message"]["content"]
    except (ValueError, KeyError, IndexError, TypeError) as exc:
        raise GatewayError(502, "Antwort des Modellservers hat ein unbekanntes Format") from exc
    usage = data.get("usage") if isinstance(data.get("usage"), dict) else {}
    prompt_tokens, completion_tokens = usage.get("prompt_tokens"), usage.get("completion_tokens")
    if isinstance(prompt_tokens, int) and prompt_tokens >= 0:
        out = completion_tokens if isinstance(completion_tokens, int) and completion_tokens >= 0 else None
        store.record_tokens("local", model, prompt_tokens, out, "api", job_id)
    else:
        store.record_tokens("local", model, None, None, "unknown", job_id)
    return {
        "answer": answer,
        "model": model,
        "provider": "local",
        "context": store.context_status(model, thresholds),
        "recorded_at": time.time(),
    }
