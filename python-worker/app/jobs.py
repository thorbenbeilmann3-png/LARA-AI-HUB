"""Persistent job memory for LARA: jobs, steps, progress and token usage.

Design rules (see docs/AUFTRAGSSPEICHER.md):
- The work state never lives only in a model's context window: everything is
  written to SQLite immediately (one transaction per change).
- Progress is computed only from completed steps (weight of "done" steps / total
  weight). It is never estimated or set by hand.
- Token values carry their source: "api" (reported by the provider), "estimated"
  or "unknown". Unknown context windows are reported as unknown, not guessed.
- Context usage (size of the latest request vs. the model's window) and total
  consumption (sum over all requests, basis for costs) are kept separate.
"""
from __future__ import annotations

import os
import sqlite3
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Any

STEP_STATUSES = ("open", "in_progress", "done", "failed", "blocked")
TOKEN_SOURCES = ("api", "estimated", "unknown")

SCHEMA = """
CREATE TABLE IF NOT EXISTS jobs (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    goal TEXT NOT NULL,
    agent TEXT,
    created_at REAL NOT NULL,
    updated_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS steps (
    id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL REFERENCES jobs(id),
    position INTEGER NOT NULL,
    phase TEXT NOT NULL,
    title TEXT NOT NULL,
    weight REAL NOT NULL CHECK (weight > 0),
    status TEXT NOT NULL DEFAULT 'open',
    result TEXT,
    agent TEXT,
    updated_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS token_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id TEXT REFERENCES jobs(id),
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    source TEXT NOT NULL,
    created_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS model_limits (
    model TEXT PRIMARY KEY,
    context_window INTEGER NOT NULL CHECK (context_window > 0),
    source TEXT NOT NULL,
    updated_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS job_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id TEXT NOT NULL REFERENCES jobs(id),
    kind TEXT NOT NULL,
    detail TEXT NOT NULL,
    created_at REAL NOT NULL
);
"""


@dataclass(frozen=True)
class Thresholds:
    """Context-usage thresholds in percent (configurable)."""

    watch: float = 70.0
    prepare: float = 80.0
    switch: float = 90.0

    @classmethod
    def from_env(cls) -> "Thresholds":
        t = cls(
            float(os.getenv("LARA_CONTEXT_WATCH", "70")),
            float(os.getenv("LARA_CONTEXT_PREPARE", "80")),
            float(os.getenv("LARA_CONTEXT_SWITCH", "90")),
        )
        if not (0 < t.watch < t.prepare < t.switch <= 100):
            raise ValueError("Schwellen müssen 0 < watch < prepare < switch <= 100 erfüllen")
        return t

    def status(self, percent: float) -> str:
        if percent >= self.switch:
            return "wechsel_einleiten"
        if percent >= self.prepare:
            return "uebergabe_vorbereiten"
        if percent >= self.watch:
            return "beobachten"
        return "normal"


class JobStore:
    def __init__(self, path: str) -> None:
        self.path = path
        self._lock = threading.RLock()
        self._db = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self._db.row_factory = sqlite3.Row
        self._db.execute("PRAGMA foreign_keys = ON")
        if path != ":memory:":
            self._db.execute("PRAGMA journal_mode = WAL")
        self._db.executescript(SCHEMA)

    def close(self) -> None:
        self._db.close()

    def _tx(self):
        return _Tx(self._db, self._lock)

    # ------------------------------------------------------------------ jobs

    def create_job(self, title: str, goal: str, steps: list[dict[str, Any]], agent: str | None = None) -> dict[str, Any]:
        if not steps:
            raise ValueError("Ein Auftrag braucht mindestens einen Arbeitsschritt")
        now = time.time()
        job_id = uuid.uuid4().hex
        with self._tx() as db:
            db.execute("INSERT INTO jobs VALUES (?,?,?,?,?,?)", (job_id, title, goal, agent, now, now))
            for pos, step in enumerate(steps):
                weight = float(step.get("weight", 1))
                if weight <= 0:
                    raise ValueError("Gewicht muss größer als 0 sein")
                db.execute(
                    "INSERT INTO steps (id, job_id, position, phase, title, weight, status, updated_at) "
                    "VALUES (?,?,?,?,?,?, 'open', ?)",
                    (uuid.uuid4().hex, job_id, pos, step["phase"], step["title"], weight, now),
                )
            self._event(db, job_id, "created", f"{len(steps)} Schritte")
        return self.get_job(job_id)

    def update_step(self, job_id: str, step_id: str, status: str, result: str | None = None,
                    agent: str | None = None) -> dict[str, Any]:
        if status not in STEP_STATUSES:
            raise ValueError(f"Unbekannter Status: {status}")
        now = time.time()
        with self._tx() as db:
            row = db.execute("SELECT status FROM steps WHERE id=? AND job_id=?", (step_id, job_id)).fetchone()
            if row is None:
                raise KeyError("Schritt nicht gefunden")
            if status == "in_progress":
                busy = db.execute(
                    "SELECT id FROM steps WHERE job_id=? AND status='in_progress' AND id<>?", (job_id, step_id)
                ).fetchone()
                if busy is not None:
                    # Avoid two conflicting work states within one job.
                    raise ValueError("Ein anderer Schritt dieses Auftrags ist bereits in Bearbeitung")
            db.execute(
                "UPDATE steps SET status=?, result=COALESCE(?, result), agent=COALESCE(?, agent), updated_at=? "
                "WHERE id=?",
                (status, result, agent, now, step_id),
            )
            db.execute("UPDATE jobs SET updated_at=? WHERE id=?", (now, job_id))
            self._event(db, job_id, "step", f"{step_id}: {row['status']} -> {status}")
        return self.get_job(job_id)

    def get_job(self, job_id: str) -> dict[str, Any]:
        with self._lock:
            return self._get_job_unlocked(job_id)

    def _get_job_unlocked(self, job_id: str) -> dict[str, Any]:
        job = self._db.execute("SELECT * FROM jobs WHERE id=?", (job_id,)).fetchone()
        if job is None:
            raise KeyError("Auftrag nicht gefunden")
        steps = [dict(r) for r in self._db.execute(
            "SELECT * FROM steps WHERE job_id=? ORDER BY position", (job_id,))]
        return {**dict(job), **progress(steps), "steps": steps, "tokens": self.job_tokens(job_id)}

    def list_jobs(self) -> list[dict[str, Any]]:
        with self._lock:
            return self._list_jobs_unlocked()

    def _list_jobs_unlocked(self) -> list[dict[str, Any]]:
        ids = [r["id"] for r in self._db.execute("SELECT id FROM jobs ORDER BY created_at")]
        return [self.get_job(i) for i in ids]

    def recovery_report(self) -> list[dict[str, Any]]:
        """Jobs whose work was interrupted (a step still 'in_progress'), e.g. after a crash."""
        report = []
        for job in self.list_jobs():
            open_work = [s for s in job["steps"] if s["status"] == "in_progress"]
            if open_work:
                report.append({
                    "job_id": job["id"], "title": job["title"], "overall_percent": job["overall_percent"],
                    "interrupted_step": {"id": open_work[0]["id"], "title": open_work[0]["title"]},
                    "next_step": job["next_step"],
                })
        return report

    # ---------------------------------------------------------------- tokens

    def record_tokens(self, provider: str, model: str, input_tokens: int | None, output_tokens: int | None,
                      source: str, job_id: str | None = None) -> None:
        if source not in TOKEN_SOURCES:
            raise ValueError(f"Unbekannte Quelle: {source}")
        for v in (input_tokens, output_tokens):
            if v is not None and v < 0:
                raise ValueError("Tokenzahlen dürfen nicht negativ sein")
        if source == "unknown" and (input_tokens is not None or output_tokens is not None):
            raise ValueError("Quelle 'unknown' darf keine Zahlen enthalten")
        with self._tx() as db:
            if job_id is not None and db.execute("SELECT 1 FROM jobs WHERE id=?", (job_id,)).fetchone() is None:
                raise KeyError("Auftrag nicht gefunden")
            db.execute(
                "INSERT INTO token_events (job_id, provider, model, input_tokens, output_tokens, source, created_at) "
                "VALUES (?,?,?,?,?,?,?)",
                (job_id, provider, model, input_tokens, output_tokens, source, time.time()),
            )

    def set_context_window(self, model: str, context_window: int, source: str) -> None:
        if source not in ("api", "user"):
            raise ValueError("Quelle muss 'api' oder 'user' (Nutzerangabe) sein")
        with self._tx() as db:
            db.execute(
                "INSERT INTO model_limits VALUES (?,?,?,?) ON CONFLICT(model) DO UPDATE SET "
                "context_window=excluded.context_window, source=excluded.source, updated_at=excluded.updated_at",
                (model, context_window, source, time.time()),
            )

    def job_tokens(self, job_id: str) -> dict[str, Any]:
        with self._lock:
            return self._job_tokens_unlocked(job_id)

    def _job_tokens_unlocked(self, job_id: str) -> dict[str, Any]:
        rows = list(self._db.execute(
            "SELECT input_tokens, output_tokens, source FROM token_events WHERE job_id=?", (job_id,)))
        return _sum_tokens(rows)

    def context_status(self, model: str, thresholds: Thresholds) -> dict[str, Any]:
        with self._lock:
            return self._context_status_unlocked(model, thresholds)

    def _context_status_unlocked(self, model: str, thresholds: Thresholds) -> dict[str, Any]:
        limit = self._db.execute("SELECT * FROM model_limits WHERE model=?", (model,)).fetchone()
        last = self._db.execute(
            "SELECT * FROM token_events WHERE model=? ORDER BY id DESC LIMIT 1", (model,)).fetchone()
        totals = _sum_tokens(list(self._db.execute(
            "SELECT input_tokens, output_tokens, source FROM token_events WHERE model=?", (model,))))
        result: dict[str, Any] = {
            "model": model,
            "context_window": limit["context_window"] if limit else None,
            "context_window_source": limit["source"] if limit else "unknown",
            "total_consumption": totals,
        }
        if last is None or last["source"] == "unknown" or last["input_tokens"] is None:
            result.update(used=None, remaining=None, percent=None, status="unbekannt",
                          usage_source=last["source"] if last else "unknown")
            return result
        used = int(last["input_tokens"]) + int(last["output_tokens"] or 0)
        result.update(used=used, usage_source=last["source"])
        if not limit:
            result.update(remaining=None, percent=None, status="unbekannt")
            return result
        window = int(limit["context_window"])
        percent = round(100.0 * used / window, 1)
        result.update(remaining=max(0, window - used), percent=percent, status=thresholds.status(percent))
        return result

    def _event(self, db: sqlite3.Connection, job_id: str, kind: str, detail: str) -> None:
        db.execute("INSERT INTO job_events (job_id, kind, detail, created_at) VALUES (?,?,?,?)",
                   (job_id, kind, detail, time.time()))


def progress(steps: list[dict[str, Any]]) -> dict[str, Any]:
    """Progress strictly from completed steps, overall and per phase (ordered by first appearance)."""
    total = sum(s["weight"] for s in steps)
    done = sum(s["weight"] for s in steps if s["status"] == "done")
    phases: dict[str, dict[str, float]] = {}
    for s in steps:
        p = phases.setdefault(s["phase"], {"total": 0.0, "done": 0.0})
        p["total"] += s["weight"]
        if s["status"] == "done":
            p["done"] += s["weight"]
    current = next((s for s in steps if s["status"] == "in_progress"), None)
    nxt = next((s for s in steps if s["status"] == "open"), None)
    problems = [s for s in steps if s["status"] in ("failed", "blocked")]
    return {
        "overall_percent": _pct(done, total),
        "phases": [{"phase": k, "percent": _pct(v["done"], v["total"])} for k, v in phases.items()],
        "completed_steps": sum(1 for s in steps if s["status"] == "done"),
        "open_steps": sum(1 for s in steps if s["status"] != "done"),
        "current_step": {"id": current["id"], "title": current["title"]} if current else None,
        "next_step": {"id": nxt["id"], "title": nxt["title"]} if nxt else None,
        "problems": [{"id": s["id"], "title": s["title"], "status": s["status"]} for s in problems],
        "state": "abgeschlossen" if total and done == total else ("blockiert" if problems else "aktiv"),
    }


def _pct(done: float, total: float) -> float:
    return round(100.0 * done / total, 1) if total else 0.0


def _sum_tokens(rows: list[sqlite3.Row]) -> dict[str, Any]:
    known = [r for r in rows if r["source"] != "unknown"]
    return {
        "requests": len(rows),
        "requests_without_counts": len(rows) - len(known),
        "input_tokens": sum(int(r["input_tokens"] or 0) for r in known),
        "output_tokens": sum(int(r["output_tokens"] or 0) for r in known),
        "estimated": any(r["source"] == "estimated" for r in known),
    }


class _Tx:
    def __init__(self, db: sqlite3.Connection, lock: threading.RLock) -> None:
        self.db, self.lock = db, lock

    def __enter__(self) -> sqlite3.Connection:
        self.lock.acquire()
        self.db.execute("BEGIN IMMEDIATE")
        return self.db

    def __exit__(self, exc_type, exc, tb) -> None:
        try:
            self.db.execute("ROLLBACK" if exc_type else "COMMIT")
        finally:
            self.lock.release()
