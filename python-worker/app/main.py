"""LARA Python control plane.

Only explicitly registered automation tasks can be invoked. No arbitrary code execution.
Secrets are read only from the server environment.
"""
import os
import platform
import sys
from typing import Any

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, ConfigDict, Field

app = FastAPI(title="LARA Control Plane", version="0.2.0")


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    provider: str = Field(pattern="^(openai|gemini|ollama)$")
    model: str = Field(min_length=1, max_length=120)
    prompt: str = Field(min_length=1, max_length=12000)


class AutomationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    workflow_id: str = Field(pattern="^[a-z0-9_-]{1,64}$")
    approved: bool = False


class TaskRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    task_id: str = Field(pattern="^[a-z0-9_.-]{1,80}$")
    approved: bool = False


def require_automation_token(authorization: str | None) -> None:
    expected = os.getenv("LARA_AUTOMATION_TOKEN")
    if not expected:
        raise HTTPException(503, "Automation is disabled: LARA_AUTOMATION_TOKEN is not configured")
    supplied = authorization.removeprefix("Bearer ").strip() if authorization else ""
    if not supplied or not __import__("hmac").compare_digest(supplied, expected):
        raise HTTPException(401, "Invalid automation credentials")


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "python_version": platform.python_version(),
        "openai_configured": bool(os.getenv("OPENAI_API_KEY")),
        "gemini_configured": bool(os.getenv("GEMINI_API_KEY")),
        "ollama_configured": bool(os.getenv("OLLAMA_BASE_URL")),
        "automation": "registered tasks only; bearer token required",
    }


@app.post("/v1/chat")
async def chat(request: ChatRequest) -> dict[str, str]:
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
    return {
        "tasks": [
            {
                "id": "python.runtime_info",
                "description": "Read Python runtime and platform information.",
                "approval_required": False,
            },
            {
                "id": "ollama.list_models",
                "description": "Read the model list from the configured Ollama server.",
                "approval_required": False,
            },
        ]
    }


@app.post("/v1/automation/run")
async def run_automation_task(
    request: TaskRequest,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    require_automation_token(authorization)
    if not request.approved:
        raise HTTPException(403, "Task execution requires explicit approval in LARA.")
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
    if not request.approved:
        return {
            "status": "approval_required",
            "workflow_id": request.workflow_id,
            "message": "A user must approve this registered workflow before execution.",
        }
    raise HTTPException(404, "Workflow is not registered; use /v1/automation/tasks")
