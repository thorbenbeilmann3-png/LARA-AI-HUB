"""Prompt-Review-Workflow: prueft einen Auftrags-Prompt mit mehreren Mistral-Modellen.

Design: Die Modelle pruefen unabhaengig voneinander nach festen Kriterien
(Klarheit, Widerspruchsfreiheit, Vollstaendigkeit, Messbarkeit, Machbarkeit,
Taeuschungsrobustheit, Sicherheit, Struktur). Die Befunde werden
deterministisch geparst und zusammengefuehrt; nichts wird dazu erfunden.
"""

from datetime import timedelta

from pydantic import BaseModel, Field

import mistralai.workflows as workflows
from mistralai.workflows import Depends
from mistralai.workflows.client import get_mistral_client

from worker.logic.review import (
    DEFAULT_CRITERIA,
    DEFAULT_MODELS,
    REVIEW_SYSTEM_PROMPT,
    build_overall,
    build_user_prompt,
    merge_findings,
    parse_findings,
    parse_overall,
)


class FindingOut(BaseModel):
    """Ein strukturierter Pruefbefund in der Ausgabe."""

    model: str
    ort: str
    schwere: str
    grund: str
    vorschlag: str


class PromptReviewInput(BaseModel):
    """Eingabe: der zu pruefende Prompt, optional eigene Kriterien/Modelle."""

    prompt: str = Field(..., min_length=1)
    criteria: list[str] | None = None
    models: list[str] | None = None


class PromptReviewOutput(BaseModel):
    """Ausgabe: Befunde aller Modelle und die Gesamteinschaetzung.

    raw_reviews enthaelt die unveraenderten Rohtexte je Modell; darueber
    lassen sich Formatabweichungen (z. B. von mistral-large-4) nachvollziehen,
    statt sie nur als fehlenden Beitrag zu vermuten.
    """

    models_used: list[str]
    criteria_used: list[str]
    findings: list[FindingOut]
    overall: str
    raw_reviews: dict[str, str]


@workflows.activity(
    start_to_close_timeout=timedelta(minutes=10),
    retry_policy_max_attempts=2,
)
async def review_with_model(
    model: str,
    prompt: str,
    criteria: list[str],
    client=Depends(get_mistral_client),
) -> str:
    """Ein Modell prueft den Prompt und liefert Rohtext zurueck."""
    response = await client.chat.complete_async(
        model=model,
        messages=[
            {"role": "system", "content": REVIEW_SYSTEM_PROMPT},
            {"role": "user", "content": build_user_prompt(prompt, criteria)},
        ],
    )
    content = response.choices[0].message.content
    return content if isinstance(content, str) else ""


@workflows.workflow.define(
    name="thor-prompt-review",
    workflow_display_name="THOR Prompt Review",
    workflow_description=(
        "Prueft einen Projektauftrag-Prompt mit mehreren Mistral-Modellen "
        "nach acht festen Kriterien und fuehrt die Befunde zusammen."
    ),
)
class PromptReviewWorkflow:
    """Orchestriert die Modellpruefungen und die deterministische Auswertung."""

    @workflows.workflow.entrypoint
    async def run(self, input: PromptReviewInput) -> PromptReviewOutput:
        criteria = input.criteria if input.criteria else list(DEFAULT_CRITERIA)
        models = input.models if input.models else list(DEFAULT_MODELS)

        reviews: list[tuple[str, str]] = []
        for model in models:
            raw = await review_with_model(
                model=model, prompt=input.prompt, criteria=criteria
            )
            reviews.append((model, raw))

        parsed = [
            (model, parse_findings(model, raw)) for model, raw in reviews
        ]
        findings = merge_findings(parsed)
        overall_parts = {
            model: parse_overall(raw) for model, raw in reviews
        }
        overall = build_overall(findings, overall_parts)

        return PromptReviewOutput(
            models_used=models,
            criteria_used=criteria,
            findings=[FindingOut(**f.to_dict()) for f in findings],
            overall=overall,
            raw_reviews={model: raw for model, raw in reviews},
        )
