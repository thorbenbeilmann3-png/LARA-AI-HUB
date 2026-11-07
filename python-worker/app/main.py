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
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from .ratelimit import limiter  # noqa: E402

app = FastAPI(title="LARA Control Plane", version="0.4.0")

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


@app.middleware("http")
async def rate_limit(request: Request, call_next):
    client = request.client.host if request.client else "unknown"
    wait = limiter.check(client)
    if wait is not None:
        return JSONResponse({"detail": "Zu viele Anfragen"}, status_code=429,
                            headers={"Retry-After": str(int(wait) + 1)})
    response = await call_next(request)
    if response.status_code == 401:
        limiter.auth_failed(client)
    return response


@app.get("/health")
async def health() -> dict[str, Any]:
    # Unauthenticated liveness check only: no versions, no configured providers.
    return {"status": "ok", "service": "lara-core", "api_version": app.version}


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


# ---------------------------------------------------------------------------
# Job memory, progress and token monitoring (docs/AUFTRAGSSPEICHER.md)
# ---------------------------------------------------------------------------
from .jobs import JobStore, Thresholds  # noqa: E402

_store: JobStore | None = None


def get_store() -> JobStore:
    global _store
    if _store is None:
        _store = JobStore(os.getenv("LARA_DB_PATH", os.path.join(os.path.dirname(__file__), "..", "lara.db")))
    return _store


def reset_store() -> None:
    """Close the store so the next access reopens it (used by tests and restarts)."""
    global _store
    if _store is not None:
        _store.close()
    _store = None


class StepIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    phase: str = Field(min_length=1, max_length=60)
    title: str = Field(min_length=1, max_length=200)
    weight: float = Field(default=1, gt=0, le=1000)


class JobIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str = Field(min_length=1, max_length=200)
    goal: str = Field(min_length=1, max_length=4000)
    agent: str | None = Field(default=None, max_length=80)
    steps: list[StepIn] = Field(min_length=1, max_length=200)


class StepUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")
    status: Literal["open", "in_progress", "done", "failed", "blocked"]
    result: str | None = Field(default=None, max_length=8000)
    agent: str | None = Field(default=None, max_length=80)


class TokenEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")
    provider: str = Field(pattern="^[a-z0-9._-]{1,40}$")
    model: str = Field(min_length=1, max_length=120)
    input_tokens: int | None = Field(default=None, ge=0)
    output_tokens: int | None = Field(default=None, ge=0)
    source: Literal["api", "estimated", "unknown"]
    job_id: str | None = Field(default=None, pattern="^[a-f0-9]{32}$")


class ContextWindow(BaseModel):
    model_config = ConfigDict(extra="forbid")
    model: str = Field(min_length=1, max_length=120)
    context_window: int = Field(gt=0, le=100_000_000)
    source: Literal["api", "user"]


def _translate(exc: Exception) -> HTTPException:
    if isinstance(exc, KeyError):
        return HTTPException(404, str(exc.args[0]) if exc.args else "Nicht gefunden")
    return HTTPException(400, str(exc))


@app.post("/v1/jobs", status_code=201)
async def create_job(body: JobIn, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    try:
        return get_store().create_job(body.title, body.goal, [s.model_dump() for s in body.steps], body.agent)
    except ValueError as exc:
        raise _translate(exc) from exc


@app.get("/v1/jobs")
async def list_jobs(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    return {"jobs": get_store().list_jobs()}


@app.get("/v1/jobs/{job_id}")
async def get_job(job_id: str, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    try:
        return get_store().get_job(job_id)
    except KeyError as exc:
        raise _translate(exc) from exc


@app.post("/v1/jobs/{job_id}/steps/{step_id}")
async def update_step(job_id: str, step_id: str, body: StepUpdate,
                      authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    try:
        return get_store().update_step(job_id, step_id, body.status, body.result, body.agent)
    except (KeyError, ValueError) as exc:
        raise _translate(exc) from exc


@app.get("/v1/recovery")
async def recovery(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    return {"interrupted": get_store().recovery_report()}


@app.post("/v1/tokens", status_code=201)
async def record_tokens(body: TokenEvent, authorization: str | None = Header(default=None)) -> dict[str, str]:
    require_automation_token(authorization)
    try:
        get_store().record_tokens(body.provider, body.model, body.input_tokens, body.output_tokens,
                                  body.source, body.job_id)
    except (KeyError, ValueError) as exc:
        raise _translate(exc) from exc
    return {"status": "recorded"}


@app.post("/v1/models/context-window")
async def set_context_window(body: ContextWindow, authorization: str | None = Header(default=None)) -> dict[str, str]:
    require_automation_token(authorization)
    get_store().set_context_window(body.model, body.context_window, body.source)
    return {"status": "saved"}


@app.get("/v1/models/context")
async def context_status(model: str, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    if not 1 <= len(model) <= 120:
        raise HTTPException(422, "model must be 1-120 characters")
    try:
        thresholds = Thresholds.from_env()
    except ValueError as exc:
        raise HTTPException(500, str(exc)) from exc
    return get_store().context_status(model, thresholds)


# ---------------------------------------------------------------------------
# Lossless model handoff (docs/MODELLWECHSEL.md)
# ---------------------------------------------------------------------------
from .jobs import confirmation_prompt  # noqa: E402


class HandoffIn(BaseModel):
    model_config = ConfigDict(extra="forbid")
    from_model: str = Field(min_length=1, max_length=120)
    to_model: str = Field(min_length=1, max_length=120)
    decisions: list[str] = Field(default_factory=list, max_length=200)
    important_files: list[str] = Field(default_factory=list, max_length=500)
    tools: list[str] = Field(default_factory=list, max_length=100)
    test_results: list[str] = Field(default_factory=list, max_length=500)
    open_questions: list[str] = Field(default_factory=list, max_length=100)
    sources: list[str] = Field(default_factory=list, max_length=200)
    context_notes: list[str] = Field(default_factory=list, max_length=100)


class HandoffConfirm(BaseModel):
    model_config = ConfigDict(extra="forbid")
    # Either the raw reply text of the new model or an already parsed object.
    reply_text: str | None = Field(default=None, max_length=50_000)
    reply: dict[str, Any] | None = None


@app.post("/v1/jobs/{job_id}/handoffs", status_code=201)
async def create_handoff(job_id: str, body: HandoffIn,
                         authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    extras = body.model_dump(exclude={"from_model", "to_model"})
    try:
        handoff = get_store().create_handoff(job_id, body.from_model, body.to_model, extras)
    except (KeyError, ValueError) as exc:
        raise _translate(exc) from exc
    return {**handoff, "prompt": confirmation_prompt(handoff)}


@app.get("/v1/handoffs/{handoff_id}")
async def get_handoff(handoff_id: str, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    try:
        return get_store().get_handoff(handoff_id)
    except KeyError as exc:
        raise _translate(exc) from exc


@app.post("/v1/handoffs/{handoff_id}/confirm")
async def confirm_handoff(handoff_id: str, body: HandoffConfirm,
                          authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    if (body.reply_text is None) == (body.reply is None):
        raise HTTPException(422, "Genau eines von reply_text oder reply angeben")
    try:
        return get_store().confirm_handoff(handoff_id, body.reply if body.reply is not None else body.reply_text)
    except (KeyError, ValueError) as exc:
        raise _translate(exc) from exc


# ---------------------------------------------------------------------------
# AI gateway – first provider: local model server (docs/GATEWAY.md)
# ---------------------------------------------------------------------------
from . import gateway  # noqa: E402


class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")
    role: Literal["system", "user", "assistant"]
    content: str = Field(min_length=1, max_length=20000)


class GatewayChat(BaseModel):
    model_config = ConfigDict(extra="forbid")
    model: str = Field(min_length=1, max_length=120)
    messages: list[ChatMessage] = Field(min_length=1, max_length=100)
    job_id: str | None = Field(default=None, pattern="^[a-f0-9]{32}$")


@app.post("/v1/gateway/local/chat")
async def gateway_local_chat(body: GatewayChat, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    require_automation_token(authorization)
    try:
        thresholds = Thresholds.from_env()
        return await gateway.chat_local(get_store(), body.model, [m.model_dump() for m in body.messages],
                                        body.job_id, thresholds)
    except gateway.GatewayError as exc:
        raise HTTPException(exc.status, str(exc)) from exc
    except KeyError as exc:
        raise _translate(exc) from exc
    except ValueError as exc:
        raise HTTPException(500, str(exc)) from exc
