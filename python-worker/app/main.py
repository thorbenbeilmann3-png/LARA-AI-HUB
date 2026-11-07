"""LARA Python control plane.

Only explicitly registered automation tasks can be invoked. No arbitrary code execution.
Secrets are read only from the server environment.

Security model (0.3.0):
- Every endpoint except /health requires the client token (LARA_AUTOMATION_TOKEN).
- Tasks that need approval run only with a server-issued approval that was granted
  with a separate approver token (LARA_APPROVER_TOKEN). A client-supplied
  ``approved`` flag is ignored and never authorizes anything.
- Approvals are single-use, bound to one task, expire after APPROVAL_TTL_SECONDS and
  live only in memory (a restart drops them, which fails safe).
"""
import hmac
import os
import platform
import secrets
import sys
import threading
import time
from typing import Any, Literal

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

app = FastAPI(title="LARA Control Plane", version="0.3.0")

APPROVAL_TTL_SECONDS = 300
MAX_PENDING_APPROVALS = 50


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    provider: str = Field(pattern="^(openai|gemini|ollama)$")
    model: str = Field(min_length=1, max_length=120)
    prompt: str = Field(min_length=1, max_length=12000)


class AutomationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    workflow_id: str = Field(pattern="^[a-z0-9_-]{1,64}$")
    # Deprecated: accepted for compatibility, ignored. Never authorizes anything.
    approved: bool = False


class TaskRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    task_id: str = Field(pattern="^[a-z0-9_.-]{1,80}$")
    approval_id: str | None = Field(default=None, pattern="^[A-Za-z0-9_-]{16,64}$")
    # Deprecated: accepted for compatibility, ignored. Never authorizes anything.
    approved: bool = False


class ApprovalCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    task_id: str = Field(pattern="^[a-z0-9_.-]{1,80}$")


class ApprovalDecision(BaseModel):
    model_config = ConfigDict(extra="forbid")
    decision: Literal["approve", "deny"]


# Registry of the only tasks that exist. approval_required=True means the task needs a
# server-side approval granted with the approver token.
TASKS: dict[str, dict[str, Any]] = {
    "python.runtime_info": {
        "description": "Read Python runtime and platform information.",
        "approval_required": False,
    },
    "ollama.list_models": {
        "description": "Read the model list from the configured Ollama server.",
        "approval_required": False,
    },
}


def _check_bearer(authorization: str | None, env_name: str, label: str) -> None:
    expected = os.getenv(env_name)
    if not expected:
        raise HTTPException(503, f"{label} is disabled: {env_name} is not configured")
    supplied = authorization.removeprefix("Bearer ").strip() if authorization else ""
    if not supplied or not hmac.compare_digest(supplied.encode(), expected.encode()):
        raise HTTPException(401, f"Invalid {label.lower()} credentials")


def require_automation_token(authorization: str | None) -> None:
    _check_bearer(authorization, "LARA_AUTOMATION_TOKEN", "Automation")


def require_approver_token(authorization: str | None) -> None:
    client_token = os.getenv("LARA_AUTOMATION_TOKEN") or ""
    approver_token = os.getenv("LARA_APPROVER_TOKEN") or ""
    if approver_token and hmac.compare_digest(client_token.encode(), approver_token.encode()):
        # Same secret for both roles would let any client approve its own requests.
        raise HTTPException(503, "Approvals are disabled: LARA_APPROVER_TOKEN must differ from LARA_AUTOMATION_TOKEN")
    _check_bearer(authorization, "LARA_APPROVER_TOKEN", "Approval")


class ApprovalStore:
    """In-memory, single-use approvals bound to one task."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._items: dict[str, dict[str, Any]] = {}

    def _purge(self, now: float) -> None:
        for key in [k for k, v in self._items.items() if v["expires_at"] <= now or v["state"] in ("used", "denied")]:
            del self._items[key]

    def create(self, task_id: str) -> dict[str, Any]:
        now = time.time()
        with self._lock:
            self._purge(now)
            if sum(1 for v in self._items.values() if v["state"] == "pending") >= MAX_PENDING_APPROVALS:
                raise HTTPException(429, "Too many pending approvals")
            approval_id = secrets.token_urlsafe(24)
            item = {"approval_id": approval_id, "task_id": task_id, "state": "pending",
                    "created_at": now, "expires_at": now + APPROVAL_TTL_SECONDS}
            self._items[approval_id] = item
            return dict(item)

    def pending(self) -> list[dict[str, Any]]:
        with self._lock:
            self._purge(time.time())
            return [dict(v) for v in self._items.values() if v["state"] == "pending"]

    def decide(self, approval_id: str, decision: str) -> dict[str, Any]:
        with self._lock:
            self._purge(time.time())
            item = self._items.get(approval_id)
            if item is None or item["state"] != "pending":
                raise HTTPException(404, "Approval not found, expired or already decided")
            item["state"] = "approved" if decision == "approve" else "denied"
            return dict(item)

    def consume(self, approval_id: str | None, task_id: str) -> None:
        if not approval_id:
            raise HTTPException(403, "Task requires a server-side approval (approval_id)")
        with self._lock:
            self._purge(time.time())
            item = self._items.get(approval_id)
            if item is None or item["task_id"] != task_id or item["state"] != "approved":
                raise HTTPException(403, "Approval missing, not granted, expired or for another task")
            item["state"] = "used"

    def clear(self) -> None:
        with self._lock:
            self._items.clear()


approvals = ApprovalStore()


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "python_version": platform.python_version(),
        "openai_configured": bool(os.getenv("OPENAI_API_KEY")),
        "gemini_configured": bool(os.getenv("GEMINI_API_KEY")),
        "ollama_configured": bool(os.getenv("OLLAMA_BASE_URL")),
        "automation": "registered tasks only; bearer token required",
        "approvals": "server-side; separate approver token required",
    }


@app.post("/v1/chat")
async def chat(request: ChatRequest, authorization: str | None = Header(default=None)) -> dict[str, str]:
    require_automation_token(authorization)
    timeout = httpx.Timeout(45.0, connect=8.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
        if request.provider == "openai":
            key = os.getenv("OPENAI_API_KEY")
            if not key:
                raise HTTPException(503, "OpenAI API key is not configured on the server")
            try:
                response = await client.post(
                    "https://api.openai.com/v1/responses",
                    headers={"Authorization": f"Bearer {key}"},
                    json={"model": request.model, "input": request.prompt},
                )
                response.raise_for_status()
                data = response.json()
                output_parts = []
                for item in data.get("output", []):
                    if item.get("type") == "message":
                        for part in item.get("content", []):
                            if part.get("type") == "output_text" and isinstance(part.get("text"), str):
                                output_parts.append(part["text"])
                answer = "\n".join(output_parts).strip()
                if not answer:
                    raise HTTPException(502, "OpenAI returned no text output")
                return {"provider": "openai", "model": request.model, "answer": answer}
            except httpx.HTTPStatusError as exc:
                raise HTTPException(502, f"OpenAI request failed with HTTP {exc.response.status_code}") from exc
            except (httpx.HTTPError, KeyError, TypeError, ValueError) as exc:
                raise HTTPException(502, f"OpenAI request failed: {type(exc).__name__}") from exc

        if request.provider == "gemini":
            key = os.getenv("GEMINI_API_KEY")
            if not key:
                raise HTTPException(503, "Gemini API key is not configured on the server")
            base = os.getenv("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com/v1beta")
            url = f"{base}/models/{request.model}:generateContent"
            try:
                response = await client.post(
                    url,
                    params={"key": key},
                    json={"contents": [{"parts": [{"text": request.prompt}]}]},
                )
                response.raise_for_status()
                data = response.json()
                answer = data["candidates"][0]["content"]["parts"][0]["text"]
                return {"provider": "gemini", "model": request.model, "answer": answer}
            except (httpx.HTTPError, KeyError, IndexError, ValueError) as exc:
                raise HTTPException(502, f"Gemini request failed: {type(exc).__name__}") from exc

        base = os.getenv("OLLAMA_BASE_URL")
        if not base:
            raise HTTPException(503, "Ollama base URL is not configured")
        try:
            response = await client.post(
                f"{base.rstrip('/')}/api/chat",
                json={
                    "model": request.model,
                    "messages": [{"role": "user", "content": request.prompt}],
                    "stream": False,
                },
            )
            response.raise_for_status()
            data = response.json()
            return {
                "provider": "ollama",
                "model": request.model,
                "answer": data["message"]["content"],
            }
        except (httpx.HTTPError, KeyError, ValueError) as exc:
            raise HTTPException(502, f"Ollama request failed: {type(exc).__name__}") from exc


@app.get("/v1/automation/tasks")
async def automation_tasks(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    return {"tasks": [{"id": task_id, **meta} for task_id, meta in TASKS.items()]}


@app.post("/v1/approvals", status_code=201)
async def create_approval(
    request: ApprovalCreate,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    require_automation_token(authorization)
    task = TASKS.get(request.task_id)
    if task is None:
        raise HTTPException(404, "Task is not registered")
    if not task["approval_required"]:
        raise HTTPException(400, "Task does not require approval")
    item = approvals.create(request.task_id)
    return {"status": "pending", "approval_id": item["approval_id"], "task_id": item["task_id"],
            "expires_in_seconds": APPROVAL_TTL_SECONDS}


@app.get("/v1/approvals")
async def list_approvals(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_approver_token(authorization)
    return {"approvals": [
        {"approval_id": i["approval_id"], "task_id": i["task_id"],
         "description": TASKS.get(i["task_id"], {}).get("description", ""),
         "expires_in_seconds": max(0, int(i["expires_at"] - time.time()))}
        for i in approvals.pending()
    ]}


@app.post("/v1/approvals/{approval_id}/decision")
async def decide_approval(
    approval_id: str,
    request: ApprovalDecision,
    authorization: str | None = Header(default=None),
) -> dict[str, str]:
    require_approver_token(authorization)
    item = approvals.decide(approval_id, request.decision)
    return {"status": item["state"], "approval_id": item["approval_id"], "task_id": item["task_id"]}


@app.post("/v1/automation/run")
async def run_automation_task(
    request: TaskRequest,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    require_automation_token(authorization)
    task = TASKS.get(request.task_id)
    if task is None:
        raise HTTPException(404, "Task is not registered; arbitrary Python execution is disabled")
    if task["approval_required"]:
        approvals.consume(request.approval_id, request.task_id)
    if request.task_id == "python.runtime_info":
        return {
            "status": "completed",
            "task_id": request.task_id,
            "result": {
                "python_version": sys.version.split()[0],
                "platform": platform.platform(),
                "executable": os.path.basename(sys.executable),
            },
        }
    if request.task_id == "ollama.list_models":
        base = os.getenv("OLLAMA_BASE_URL")
        if not base:
            raise HTTPException(503, "Ollama base URL is not configured")
        try:
            async with httpx.AsyncClient(timeout=httpx.Timeout(15.0, connect=5.0)) as client:
                response = await client.get(f"{base.rstrip('/')}/api/tags")
                response.raise_for_status()
                data = response.json()
            models = [
                item.get("name")
                for item in data.get("models", [])
                if isinstance(item, dict) and isinstance(item.get("name"), str)
            ]
            return {"status": "completed", "task_id": request.task_id, "result": {"models": models}}
        except (httpx.HTTPError, ValueError) as exc:
            raise HTTPException(502, f"Ollama lookup failed: {type(exc).__name__}") from exc
    raise HTTPException(404, "Task is not registered; arbitrary Python execution is disabled")


@app.post("/v1/automation/request")
async def request_automation(
    request: AutomationRequest,
    authorization: str | None = Header(default=None),
) -> dict[str, str]:
    require_automation_token(authorization)
    # No workflows are registered yet. The client-supplied "approved" flag is ignored.
    raise HTTPException(404, "Workflow is not registered; use /v1/automation/tasks")
