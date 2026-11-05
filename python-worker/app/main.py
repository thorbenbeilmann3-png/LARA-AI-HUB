"""LARA Python control-plane starter. Secrets are read only from server environment."""
import os
from typing import Any

import httpx
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field

app = FastAPI(title="LARA Control Plane", version="0.1.0")


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    provider: str = Field(pattern="^(gemini|ollama)$")
    model: str = Field(min_length=1, max_length=120)
    prompt: str = Field(min_length=1, max_length=12000)


class AutomationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    workflow_id: str = Field(pattern="^[a-z0-9_-]{1,64}$")
    approved: bool = False


@app.get("/health")
async def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "gemini_configured": bool(os.getenv("GEMINI_API_KEY")),
        "ollama_configured": bool(os.getenv("OLLAMA_BASE_URL")),
        "automation": "approval-gated; execution disabled",
    }


@app.post("/v1/chat")
async def chat(request: ChatRequest) -> dict[str, str]:
    timeout = httpx.Timeout(45.0, connect=8.0)
    async with httpx.AsyncClient(timeout=timeout) as client:
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


@app.post("/v1/automation/request")
async def request_automation(request: AutomationRequest) -> dict[str, str]:
    if not request.approved:
        return {
            "status": "approval_required",
            "workflow_id": request.workflow_id,
            "message": "A user must approve this registered workflow before execution.",
        }
    raise HTTPException(501, "No workflows are registered yet; execution is disabled")
