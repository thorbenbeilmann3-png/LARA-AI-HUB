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

import hashlib
import json
import os
import re
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
CREATE TABLE IF NOT EXISTS handoffs (
    id TEXT PRIMARY KEY,
    job_id TEXT NOT NULL REFERENCES jobs(id),
    version INTEGER NOT NULL,
    from_model TEXT NOT NULL,
    to_model TEXT NOT NULL,
    package TEXT NOT NULL,
    checksum TEXT NOT NULL,
    status TEXT NOT NULL,
    detail TEXT,
    created_at REAL NOT NULL,
    decided_at REAL
);
CREATE TABLE IF NOT EXISTS drafts (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    goal TEXT NOT NULL,
    details TEXT NOT NULL,
    status TEXT NOT NULL,
    note TEXT,
    job_id TEXT,
    created_at REAL NOT NULL,
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
        from .memory import MemoryStore  # same database file, same lock
        self.memory = MemoryStore(self._db, self._lock)

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
        active = self._db.execute(
            "SELECT to_model FROM handoffs WHERE job_id=? AND status='confirmed' ORDER BY version DESC LIMIT 1",
            (job_id,)).fetchone()
        return {**dict(job), **progress(steps), "steps": steps, "tokens": self.job_tokens(job_id),
                "active_model": active["to_model"] if active else None}

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

    # ---------------------------------------------------------------- drafts

    def create_draft(self, title: str, goal: str, details: dict[str, Any]) -> dict[str, Any]:
        """A proposed order. It becomes a job only after an approver says yes."""
        _validate_draft_steps(details)
        now = time.time()
        draft_id = uuid.uuid4().hex
        with self._tx() as db:
            db.execute("INSERT INTO drafts VALUES (?,?,?,?, 'draft', NULL, NULL, ?, ?)",
                       (draft_id, title, goal, canonical_json(details), now, now))
        return self.get_draft(draft_id)

    def update_draft(self, draft_id: str, title: str, goal: str, details: dict[str, Any]) -> dict[str, Any]:
        """Rework a draft (e.g. after "Ich habe eine andere Idee"); only while undecided."""
        _validate_draft_steps(details)
        with self._tx() as db:
            row = db.execute("SELECT status FROM drafts WHERE id=?", (draft_id,)).fetchone()
            if row is None:
                raise KeyError("Entwurf nicht gefunden")
            if row["status"] not in ("draft", "revise"):
                raise ValueError(f"Entwurf ist bereits entschieden ({row['status']})")
            db.execute("UPDATE drafts SET title=?, goal=?, details=?, status='draft', updated_at=? WHERE id=?",
                       (title, goal, canonical_json(details), time.time(), draft_id))
        return self.get_draft(draft_id)

    def decide_draft(self, draft_id: str, decision: str, note: str | None) -> dict[str, Any]:
        """approve -> creates the job; reject -> closed; revise -> back to the user/ChatGPT with a note."""
        if decision not in ("approve", "reject", "revise"):
            raise ValueError("Entscheidung muss approve, reject oder revise sein")
        if decision == "revise" and not (note and note.strip()):
            raise ValueError("Für „andere Idee“ bitte die Idee als Notiz angeben")
        with self._tx() as db:
            row = db.execute("SELECT * FROM drafts WHERE id=?", (draft_id,)).fetchone()
            if row is None:
                raise KeyError("Entwurf nicht gefunden")
            if row["status"] != "draft":
                raise ValueError(f"Entwurf ist nicht zur Entscheidung offen ({row['status']})")
            job_id = None
            if decision == "approve":
                details = json.loads(row["details"])
                steps = details.get("steps") or [{"phase": "Umsetzung", "title": row["title"], "weight": 1}]
                job_id = uuid.uuid4().hex
                now = time.time()
                db.execute("INSERT INTO jobs VALUES (?,?,?,?,?,?)", (job_id, row["title"], row["goal"], None, now, now))
                for pos, step in enumerate(steps):
                    db.execute(
                        "INSERT INTO steps (id, job_id, position, phase, title, weight, status, updated_at) "
                        "VALUES (?,?,?,?,?,?, 'open', ?)",
                        (uuid.uuid4().hex, job_id, pos, step["phase"], step["title"], float(step.get("weight", 1)), now),
                    )
                self._event(db, job_id, "created", f"aus Entwurf {draft_id} nach Freigabe")
            status = {"approve": "approved", "reject": "rejected", "revise": "revise"}[decision]
            db.execute("UPDATE drafts SET status=?, note=?, job_id=?, updated_at=? WHERE id=?",
                       (status, note, job_id, time.time(), draft_id))
        return self.get_draft(draft_id)

    def get_draft(self, draft_id: str) -> dict[str, Any]:
        with self._lock:
            row = self._db.execute("SELECT * FROM drafts WHERE id=?", (draft_id,)).fetchone()
            if row is None:
                raise KeyError("Entwurf nicht gefunden")
            return {**{k: row[k] for k in row.keys() if k != "details"}, "details": json.loads(row["details"])}

    def list_drafts(self, status: str | None = None) -> list[dict[str, Any]]:
        with self._lock:
            ids = [r["id"] for r in self._db.execute(
                "SELECT id FROM drafts WHERE (? IS NULL OR status=?) ORDER BY created_at", (status, status))]
        return [self.get_draft(i) for i in ids]

    # -------------------------------------------------------------- handoffs

    def create_handoff(self, job_id: str, from_model: str, to_model: str,
                       extras: dict[str, Any] | None = None) -> dict[str, Any]:
        """Freeze the current work state into a versioned, checksummed handoff package.

        The package is built from stored data (job, steps, tokens) plus optional
        caller-supplied lists (decisions, files, tools, tests, questions, sources,
        context). A newer package supersedes older unconfirmed ones.
        """
        extras = extras or {}
        unknown = set(extras) - set(HANDOFF_EXTRA_FIELDS)
        if unknown:
            raise ValueError(f"Unbekannte Felder: {sorted(unknown)}")
        with self._tx() as db:
            job = self._get_job_unlocked(job_id)
            if job["next_step"] is None and job["current_step"] is None:
                raise ValueError("Auftrag hat keinen offenen Schritt; eine Übergabe ist nicht nötig")
            version = (db.execute("SELECT MAX(version) AS v FROM handoffs WHERE job_id=?", (job_id,))
                       .fetchone()["v"] or 0) + 1
            steps = job["steps"]
            package = {
                "format": "lara-handoff/1",
                "job_id": job_id,
                "version": version,
                "job_updated_at": job["updated_at"],
                "from_model": from_model,
                "to_model": to_model,
                "original_request": job["title"],
                "goal_and_requirements": job["goal"],
                "completed_steps": [{"id": x["id"], "phase": x["phase"], "title": x["title"], "result": x["result"]}
                                    for x in steps if x["status"] == "done"],
                "progress": {"overall_percent": job["overall_percent"], "phases": job["phases"]},
                "open_tasks": [{"id": x["id"], "phase": x["phase"], "title": x["title"], "status": x["status"]}
                               for x in steps if x["status"] != "done"],
                "current_step": job["current_step"],
                "next_step": job["next_step"],
                "known_errors": job["problems"],
                "token_usage": job["tokens"],
                **{name: list(extras.get(name, [])) for name in HANDOFF_EXTRA_FIELDS},
            }
            body = canonical_json(package)
            checksum = hashlib.sha256(body.encode("utf-8")).hexdigest()
            db.execute("UPDATE handoffs SET status='superseded', decided_at=? WHERE job_id=? AND status='prepared'",
                       (time.time(), job_id))
            handoff_id = uuid.uuid4().hex
            db.execute(
                "INSERT INTO handoffs (id, job_id, version, from_model, to_model, package, checksum, status, created_at) "
                "VALUES (?,?,?,?,?,?,?, 'prepared', ?)",
                (handoff_id, job_id, version, from_model, to_model, body, checksum, time.time()),
            )
            self._event(db, job_id, "handoff", f"v{version} {from_model} -> {to_model} vorbereitet")
        return {"handoff_id": handoff_id, "status": "prepared", "checksum": checksum, "package": package}

    def get_handoff(self, handoff_id: str) -> dict[str, Any]:
        with self._lock:
            row = self._db.execute("SELECT * FROM handoffs WHERE id=?", (handoff_id,)).fetchone()
            if row is None:
                raise KeyError("Übergabe nicht gefunden")
            return {**{k: row[k] for k in row.keys() if k != "package"}, "package": json.loads(row["package"])}

    def confirm_handoff(self, handoff_id: str, reply: dict[str, Any] | str) -> dict[str, Any]:
        """Check the new model's confirmation against the stored package.

        A handoff is confirmed only if: the stored package is intact (checksum),
        the job has not changed since the package was built, and the reply
        repeats job_id, current/next step IDs and checksum exactly. Free text
        such as "verstanden" never counts. On any mismatch the handoff is
        rejected and the job is not released to the new model.
        """
        with self._tx() as db:
            row = db.execute("SELECT * FROM handoffs WHERE id=?", (handoff_id,)).fetchone()
            if row is None:
                raise KeyError("Übergabe nicht gefunden")
            if row["status"] != "prepared":
                raise ValueError(f"Übergabe ist nicht offen (Status: {row['status']})")
            problems: list[str] = []
            if hashlib.sha256(row["package"].encode("utf-8")).hexdigest() != row["checksum"]:
                problems.append("Gespeichertes Paket ist beschädigt (Prüfsumme)")
            package = json.loads(row["package"])
            job = self._get_job_unlocked(row["job_id"])
            if job["updated_at"] != package["job_updated_at"]:
                problems.append("Arbeitsstand hat sich seit der Übergabe geändert; neues Paket nötig")
            parsed = reply if isinstance(reply, dict) else extract_json_object(reply)
            if parsed is None:
                problems.append("Antwort enthält keine strukturierte Bestätigung (JSON)")
                parsed = {}
            expected = {
                "job_id": package["job_id"],
                "current_step_id": (package["current_step"] or {}).get("id"),
                "next_step_id": (package["next_step"] or {}).get("id"),
                "checksum": row["checksum"],
            }
            for key, value in expected.items():
                if parsed.get(key) != value:
                    problems.append(f"{key} stimmt nicht überein")
            status = "rejected" if problems else "confirmed"
            db.execute("UPDATE handoffs SET status=?, detail=?, decided_at=? WHERE id=?",
                       (status, "; ".join(problems) or None, time.time(), handoff_id))
            self._event(db, row["job_id"], "handoff",
                        f"v{row['version']} {status}" + (f": {'; '.join(problems)}" if problems else ""))
        return {"handoff_id": handoff_id, "status": status, "problems": problems,
                "released_to": row["to_model"] if status == "confirmed" else None}

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


HANDOFF_EXTRA_FIELDS = ("decisions", "important_files", "tools", "test_results",
                        "open_questions", "sources", "context_notes")


def canonical_json(value: Any) -> str:
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(",", ":"))


def extract_json_object(text: str) -> dict[str, Any] | None:
    """Return the first JSON object in a model reply (also inside ``` fences), else None."""
    if not isinstance(text, str) or len(text) > 50_000:
        return None
    decoder = json.JSONDecoder()
    for match in re.finditer(r"\{", text):
        try:
            obj, _ = decoder.raw_decode(text, match.start())
        except json.JSONDecodeError:
            continue
        if isinstance(obj, dict):
            return obj
    return None


def confirmation_prompt(handoff: dict[str, Any]) -> str:
    """Instruction for the receiving model; its reply is checked by confirm_handoff."""
    return (
        "Du übernimmst einen laufenden LARA-Auftrag. Lies das folgende Übergabepaket vollständig.\n"
        "Antworte ausschließlich mit einem JSON-Objekt mit genau diesen Feldern:\n"
        '{"job_id": "...", "current_step_id": "... oder null", "next_step_id": "... oder null", '
        '"checksum": "...", "goal_summary": "ein Satz"}\n'
        "Übernimm job_id, Schritt-IDs und checksum unverändert aus dem Paket.\n\n"
        f"checksum: {handoff['checksum']}\n"
        f"Übergabepaket:\n{canonical_json(handoff['package'])}"
    )


def _validate_draft_steps(details: dict[str, Any]) -> None:
    steps = details.get("steps")
    if steps is None:
        return
    if not isinstance(steps, list) or not steps:
        raise ValueError("steps muss eine nicht leere Liste sein")
    for st in steps:
        if not isinstance(st, dict) or not st.get("phase") or not st.get("title"):
            raise ValueError("Jeder Schritt braucht phase und title")
        if float(st.get("weight", 1)) <= 0:
            raise ValueError("Gewicht muss größer als 0 sein")

