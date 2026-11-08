"""LARA memory: durable, curated knowledge shared by all agents (Masterauftrag Teil D).

- Entries have a category, content, source, project and an explicit trust status.
- Newer knowledge does not overwrite older entries: the old entry is marked
  VERALTET and linked (supersedes), so changes stay traceable.
- Agents get filtered slices (category/project/search) instead of everything.
- Secrets never belong here: content that looks like a key, token or password is
  rejected. Credentials live only in the device key store / token files.
- Export as LARA_MEMORY/*.md for portability (provider change, reinstall).
"""
from __future__ import annotations

import re
import sqlite3
import threading
import time
import uuid
from typing import Any

CATEGORIES = {
    "praeferenz": "USER_PREFERENCES.md",
    "projekt": "LARA_PROJECT_MASTER.md",
    "architektur": "ARCHITECTURE.md",
    "technik": "TECHNICAL_STATE.md",
    "entscheidung": "DECISIONS.md",
    "aufgabe": "OPEN_TASKS.md",
    "loesung": "VERIFIED_SOLUTIONS.md",
    "fehler": "KNOWN_ERRORS.md",
    "recherche": "RESEARCH_INDEX.md",
}
TRUST = ("BESTÄTIGT", "NUTZERANGABE", "ANNAHME", "UNGEPRÜFT", "VERALTET", "WIDERSPRUCH")

_SECRET_PATTERNS = [
    re.compile(r"sk-[A-Za-z0-9_-]{16,}"),
    re.compile(r"sk-ant-[A-Za-z0-9_-]{10,}"),
    re.compile(r"AIza[0-9A-Za-z_-]{30,}"),
    re.compile(r"gh[pousr]_[A-Za-z0-9]{30,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"(?i)\b(passwor[dt]|password|api[_ -]?key|secret|token)\b\s*[:=]\s*\S{6,}"),
    re.compile(r"(?i)bearer\s+[A-Za-z0-9._-]{20,}"),
]

SCHEMA = """
CREATE TABLE IF NOT EXISTS memory (
    id TEXT PRIMARY KEY,
    category TEXT NOT NULL,
    content TEXT NOT NULL,
    source TEXT NOT NULL,
    trust TEXT NOT NULL,
    project TEXT,
    created_at REAL NOT NULL,
    checked_at REAL NOT NULL,
    review_at REAL,
    superseded_by TEXT,
    supersedes TEXT
);
CREATE INDEX IF NOT EXISTS memory_category ON memory(category);
"""


def looks_like_secret(text: str) -> bool:
    return any(p.search(text) for p in _SECRET_PATTERNS)


class MemoryStore:
    def __init__(self, db: sqlite3.Connection, lock: threading.RLock) -> None:
        self._db, self._lock = db, lock
        with self._lock:
            self._db.executescript(SCHEMA)

    def _check(self, category: str, trust: str, content: str) -> None:
        if category not in CATEGORIES:
            raise ValueError(f"Unbekannte Kategorie: {category}")
        if trust not in TRUST or trust == "VERALTET":
            raise ValueError("Vertrauensstatus muss BESTÄTIGT, NUTZERANGABE, ANNAHME, UNGEPRÜFT oder WIDERSPRUCH sein")
        if looks_like_secret(content):
            raise ValueError("Inhalt sieht nach Zugangsdaten aus – Geheimnisse gehören nicht ins Gedächtnis")

    def add(self, category: str, content: str, source: str, trust: str, project: str | None = None,
            review_at: float | None = None) -> dict[str, Any]:
        self._check(category, trust, content)
        now = time.time()
        entry_id = uuid.uuid4().hex
        with self._lock:
            self._db.execute("BEGIN IMMEDIATE")
            try:
                self._db.execute(
                    "INSERT INTO memory (id, category, content, source, trust, project, created_at, checked_at, review_at) "
                    "VALUES (?,?,?,?,?,?,?,?,?)",
                    (entry_id, category, content, source, trust, project, now, now, review_at))
                self._db.execute("COMMIT")
            except Exception:
                self._db.execute("ROLLBACK")
                raise
        return self.get(entry_id)

    def supersede(self, old_id: str, content: str, source: str, trust: str) -> dict[str, Any]:
        """Replace knowledge: new entry, old one marked VERALTET and linked both ways."""
        with self._lock:
            old = self._db.execute("SELECT * FROM memory WHERE id=?", (old_id,)).fetchone()
            if old is None:
                raise KeyError("Eintrag nicht gefunden")
            if old["trust"] == "VERALTET":
                raise ValueError("Eintrag ist bereits veraltet; bitte den aktuellen Eintrag ersetzen")
            self._check(old["category"], trust, content)
            now = time.time()
            new_id = uuid.uuid4().hex
            self._db.execute("BEGIN IMMEDIATE")
            try:
                self._db.execute(
                    "INSERT INTO memory (id, category, content, source, trust, project, created_at, checked_at, supersedes) "
                    "VALUES (?,?,?,?,?,?,?,?,?)",
                    (new_id, old["category"], content, source, trust, old["project"], now, now, old_id))
                self._db.execute("UPDATE memory SET trust='VERALTET', superseded_by=? WHERE id=?", (new_id, old_id))
                self._db.execute("COMMIT")
            except Exception:
                self._db.execute("ROLLBACK")
                raise
        return self.get(new_id)

    def set_trust(self, entry_id: str, trust: str) -> dict[str, Any]:
        if trust not in TRUST or trust == "VERALTET":
            raise ValueError("VERALTET entsteht nur durch Ersetzen; sonst gültigen Status angeben")
        with self._lock:
            row = self._db.execute("SELECT trust FROM memory WHERE id=?", (entry_id,)).fetchone()
            if row is None:
                raise KeyError("Eintrag nicht gefunden")
            if row["trust"] == "VERALTET":
                raise ValueError("Veraltete Einträge werden nicht geändert")
            self._db.execute("UPDATE memory SET trust=?, checked_at=? WHERE id=?", (trust, time.time(), entry_id))
        return self.get(entry_id)

    def get(self, entry_id: str) -> dict[str, Any]:
        with self._lock:
            row = self._db.execute("SELECT * FROM memory WHERE id=?", (entry_id,)).fetchone()
        if row is None:
            raise KeyError("Eintrag nicht gefunden")
        return dict(row)

    def search(self, category: str | None = None, project: str | None = None, query: str | None = None,
               include_outdated: bool = False, limit: int = 50) -> list[dict[str, Any]]:
        sql = "SELECT * FROM memory WHERE 1=1"
        args: list[Any] = []
        if category:
            sql += " AND category=?"; args.append(category)
        if project:
            sql += " AND project=?"; args.append(project)
        if query:
            sql += " AND content LIKE ? ESCAPE '\\'"
            args.append("%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
        if not include_outdated:
            sql += " AND trust<>'VERALTET'"
        sql += " ORDER BY checked_at DESC LIMIT ?"
        args.append(max(1, min(limit, 500)))
        with self._lock:
            return [dict(r) for r in self._db.execute(sql, args)]

    def due_for_review(self, now: float | None = None) -> list[dict[str, Any]]:
        now = time.time() if now is None else now
        with self._lock:
            return [dict(r) for r in self._db.execute(
                "SELECT * FROM memory WHERE review_at IS NOT NULL AND review_at<=? AND trust<>'VERALTET'", (now,))]

    def export_markdown(self) -> dict[str, str]:
        """LARA_MEMORY/*.md: one file per category plus a changelog of superseded entries."""
        files: dict[str, str] = {}
        with self._lock:
            rows = [dict(r) for r in self._db.execute("SELECT * FROM memory ORDER BY category, created_at")]
        for category, filename in CATEGORIES.items():
            current = [r for r in rows if r["category"] == category and r["trust"] != "VERALTET"]
            lines = [f"# {filename[:-3]}", "", "Export aus dem LARA-Gedächtnis. Status in eckigen Klammern.", ""]
            for r in current:
                proj = f" (Projekt: {r['project']})" if r["project"] else ""
                lines.append(f"- [{r['trust']}] {r['content']}{proj} — Quelle: {r['source']} · ID `{r['id'][:8]}`")
            if not current:
                lines.append("_Keine Einträge._")
            files[filename] = "\n".join(lines) + "\n"
        changes = [r for r in rows if r["trust"] == "VERALTET"]
        log = ["# MEMORY_CHANGELOG", "", "Ersetzte (veraltete) Einträge und ihre Nachfolger.", ""]
        for r in changes:
            log.append(f"- `{r['id'][:8]}` → `{(r['superseded_by'] or '')[:8]}`: {r['content']} ({r['category']})")
        if not changes:
            log.append("_Noch keine Änderungen._")
        files["MEMORY_CHANGELOG.md"] = "\n".join(log) + "\n"
        return files
