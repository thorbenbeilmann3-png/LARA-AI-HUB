"""Simple in-memory rate limiting for the LARA Core (single process, loopback use).

- General limit per client address: LARA_RATE_LIMIT_PER_MIN (default 120) requests
  per sliding 60-second window.
- Failed authentication (HTTP 401) per client: after LARA_AUTH_FAIL_LIMIT (default 10)
  failures within 5 minutes, all requests from that client get HTTP 429 until the
  window passes. This slows down token guessing.
State lives in memory only and is reset when the process restarts.
"""
from __future__ import annotations

import os
import threading
import time
from collections import defaultdict, deque

WINDOW_S = 60.0
AUTH_WINDOW_S = 300.0


class RateLimiter:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._fails: dict[str, deque[float]] = defaultdict(deque)

    @staticmethod
    def _limit(name: str, default: int) -> int:
        try:
            return max(1, int(os.getenv(name, str(default))))
        except ValueError:
            return default

    @staticmethod
    def _trim(q: deque[float], now: float, window: float) -> None:
        while q and now - q[0] >= window:
            q.popleft()

    def check(self, client: str, now: float | None = None, enforce_auth_lockout: bool = True) -> float | None:
        """Register a request; return seconds to wait if it must be rejected, else None."""
        now = time.monotonic() if now is None else now
        with self._lock:
            fails = self._fails[client]
            self._trim(fails, now, AUTH_WINDOW_S)
            if enforce_auth_lockout and len(fails) >= self._limit("LARA_AUTH_FAIL_LIMIT", 10):
                return max(1.0, AUTH_WINDOW_S - (now - fails[0]))
            hits = self._hits[client]
            self._trim(hits, now, WINDOW_S)
            if len(hits) >= self._limit("LARA_RATE_LIMIT_PER_MIN", 120):
                return max(1.0, WINDOW_S - (now - hits[0]))
            hits.append(now)
            return None

    def auth_failed(self, client: str, now: float | None = None) -> None:
        now = time.monotonic() if now is None else now
        with self._lock:
            self._fails[client].append(now)

    def reset(self) -> None:
        with self._lock:
            self._hits.clear()
            self._fails.clear()


limiter = RateLimiter()
