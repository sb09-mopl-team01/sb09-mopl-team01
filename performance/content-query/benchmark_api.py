#!/usr/bin/env python3
"""Benchmark the authenticated content query API without third-party packages."""

from __future__ import annotations

import argparse
import concurrent.futures
import http.cookiejar
import json
import os
import socket
import statistics
import time
import urllib.parse
import urllib.request
from pathlib import Path


SCENARIOS = {
    "latest": {"limit": 20, "sortBy": "createdAt", "sortDirection": "DESCENDING"},
    "type-movie": {
        "typeEqual": "movie", "limit": 20,
        "sortBy": "createdAt", "sortDirection": "DESCENDING",
    },
    "keyword-one-char": {
        "keywordLike": "우", "limit": 20,
        "sortBy": "createdAt", "sortDirection": "DESCENDING",
    },
    "keyword-space": {
        "keywordLike": "우주", "limit": 20,
        "sortBy": "createdAt", "sortDirection": "DESCENDING",
    },
    "keyword-romance": {
        "keywordLike": "로맨스", "limit": 20,
        "sortBy": "createdAt", "sortDirection": "DESCENDING",
    },
    "tags-action-sf": {
        "tagsIn": ["액션", "SF"], "limit": 20,
        "sortBy": "createdAt", "sortDirection": "DESCENDING",
    },
    "rate": {"limit": 20, "sortBy": "rate", "sortDirection": "DESCENDING"},
    "deep-created-at": {
        "cursor": "2025-07-02T09:40:57+09:00",
        "idAfter": "122a1ae8-322d-a762-fdc4-50422ec9a6c8",
        "limit": 20, "sortBy": "createdAt", "sortDirection": "DESCENDING",
    },
    "deep-rate": {
        "cursor": "3",
        "idAfter": "802d0b79-5a97-9096-66ae-1b1b7b487d23",
        "limit": 20, "sortBy": "rate", "sortDirection": "DESCENDING",
    },
    "watcher-count": {
        "limit": 20, "sortBy": "watcherCount", "sortDirection": "DESCENDING",
    },
    "combined": {
        "keywordLike": "우주", "tagsIn": ["액션", "SF"], "limit": 20,
        "sortBy": "createdAt", "sortDirection": "DESCENDING",
    },
    "combined-romance": {
        "keywordLike": "로맨스", "tagsIn": ["인기", "축구"], "limit": 20,
        "sortBy": "createdAt", "sortDirection": "DESCENDING",
    },
}


def redis_command(host: str, port: int, *parts: str) -> str:
    payload = f"*{len(parts)}\r\n" + "".join(
        f"${len(part.encode('utf-8'))}\r\n{part}\r\n" for part in parts
    )
    with socket.create_connection((host, port), timeout=5) as connection:
        connection.sendall(payload.encode("utf-8"))
        return connection.recv(4096).decode("utf-8", errors="replace")


def flush_cache(host: str, port: int, password: str) -> None:
    def frame(command: list[str]) -> bytes:
        value = (
            f"*{len(command)}\r\n"
            + "".join(
                f"${len(part.encode('utf-8'))}\r\n{part}\r\n" for part in command
            )
        )
        return value.encode("utf-8")

    with socket.create_connection((host, port), timeout=5) as connection:
        connection.sendall(frame(["AUTH", password]))
        auth_response = connection.recv(4096).decode("utf-8", errors="replace")
        if not auth_response.startswith("+OK"):
            raise RuntimeError("Redis authentication failed")

        connection.sendall(frame(["FLUSHDB"]))
        flush_response = connection.recv(4096).decode("utf-8", errors="replace")
        if not flush_response.startswith("+OK"):
            raise RuntimeError(f"Redis FLUSHDB failed: {flush_response!r}")


def login(base_url: str, email: str, password: str) -> str:
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    opener.open(f"{base_url}/api/auth/csrf-token", timeout=10).read()
    csrf = next((cookie.value for cookie in jar if cookie.name == "XSRF-TOKEN"), None)
    if not csrf:
        raise RuntimeError("XSRF-TOKEN cookie was not issued")

    body = urllib.parse.urlencode({"username": email, "password": password}).encode()
    request = urllib.request.Request(
        f"{base_url}/api/auth/sign-in",
        data=body,
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "X-XSRF-TOKEN": urllib.parse.unquote(csrf),
        },
        method="POST",
    )
    with opener.open(request, timeout=15) as response:
        payload = json.load(response)
    return payload["accessToken"]


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(len(ordered) * fraction) - 1))
    return ordered[index]


def timed_get(url: str, token: str) -> tuple[float, int, int]:
    request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    started = time.perf_counter()
    with urllib.request.urlopen(request, timeout=30) as response:
        body = response.read()
        status = response.status
    return (time.perf_counter() - started) * 1000, status, len(body)


def summarize(values: list[float]) -> dict[str, float]:
    return {
        "min_ms": round(min(values), 3),
        "avg_ms": round(statistics.mean(values), 3),
        "median_ms": round(statistics.median(values), 3),
        "p95_ms": round(percentile(values, 0.95), 3),
        "max_ms": round(max(values), 3),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:18080")
    parser.add_argument("--output", required=True)
    parser.add_argument("--redis-host", default="localhost")
    parser.add_argument("--redis-port", type=int, default=16379)
    parser.add_argument(
        "--confirm-flush-performance-redis",
        action="store_true",
        help="Required because the benchmark executes FLUSHDB on the configured Redis.",
    )
    parser.add_argument("--warm-requests", type=int, default=30)
    parser.add_argument("--concurrent-requests", type=int, default=100)
    parser.add_argument("--workers", type=int, default=10)
    parser.add_argument(
        "--scenarios",
        help="Comma-separated scenario names. Defaults to all scenarios.",
    )
    args = parser.parse_args()

    if not args.confirm_flush_performance_redis:
        raise SystemExit(
            "--confirm-flush-performance-redis is required; "
            "the benchmark clears the configured Redis database."
        )

    selected_names = (
        [name.strip() for name in args.scenarios.split(",") if name.strip()]
        if args.scenarios
        else list(SCENARIOS)
    )
    unknown_names = [name for name in selected_names if name not in SCENARIOS]
    if unknown_names:
        raise SystemExit(f"Unknown scenarios: {', '.join(unknown_names)}")
    selected_scenarios = {name: SCENARIOS[name] for name in selected_names}

    email = os.environ.get("ADMIN_EMAIL")
    password = os.environ.get("ADMIN_PASSWORD")
    redis_password = os.environ.get("PERF_REDIS_PASSWORD", "perf-local-only")
    if not email or not password:
        raise SystemExit("ADMIN_EMAIL and ADMIN_PASSWORD environment variables are required")

    token = login(args.base_url.rstrip("/"), email, password)

    # Remove one-time JVM, connection-pool, and query compilation costs from the
    # per-scenario cold-cache comparison. Redis is flushed again before each
    # measured cold request below.
    for params in selected_scenarios.values():
        query = urllib.parse.urlencode(params, doseq=True)
        url = f"{args.base_url.rstrip('/')}/api/contents?{query}"
        timed_get(url, token)

    results: dict[str, object] = {
        "dataset": {"contents": 100000, "tags": 300000, "reviews": 300000, "sessions": 20000},
        "configuration": {
            "warm_requests": args.warm_requests,
            "concurrent_requests": args.concurrent_requests,
            "workers": args.workers,
        },
        "scenarios": {},
    }

    for name, params in selected_scenarios.items():
        query = urllib.parse.urlencode(params, doseq=True)
        url = f"{args.base_url.rstrip('/')}/api/contents?{query}"
        flush_cache(args.redis_host, args.redis_port, redis_password)

        cold_ms, status, body_bytes = timed_get(url, token)
        if status != 200:
            raise RuntimeError(f"{name} returned HTTP {status}")

        for _ in range(3):
            timed_get(url, token)
        warm = [timed_get(url, token)[0] for _ in range(args.warm_requests)]

        concurrent_started = time.perf_counter()
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
            concurrent_values = list(
                executor.map(lambda _: timed_get(url, token)[0], range(args.concurrent_requests))
            )
        concurrent_seconds = time.perf_counter() - concurrent_started

        results["scenarios"][name] = {
            "cold_ms": round(cold_ms, 3),
            "response_bytes": body_bytes,
            "warm_sequential": summarize(warm),
            "warm_concurrent": {
                **summarize(concurrent_values),
                "throughput_rps": round(args.concurrent_requests / concurrent_seconds, 3),
            },
        }
        print(
            f"{name}: cold={cold_ms:.2f}ms "
            f"warm_p95={percentile(warm, 0.95):.2f}ms "
            f"concurrent_p95={percentile(concurrent_values, 0.95):.2f}ms"
        )

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
