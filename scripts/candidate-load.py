#!/usr/bin/env python3
"""Bounded CI-only exercise; never accepts an external target or stores credentials."""
import concurrent.futures
import json
import math
import os
import secrets
import time
import urllib.error
import urllib.request
from pathlib import Path


def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)] if values else None


def request(path, token=None, body=None, method=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request("http://127.0.0.1:8080/api/v1/" + path,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers=headers, method=method)
    start = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return response.status, response.read(), (time.monotonic() - start) * 1000
    except urllib.error.HTTPError as error:
        return error.code, b"", (time.monotonic() - start) * 1000
    except (OSError, TimeoutError):
        return 0, b"", (time.monotonic() - start) * 1000


def main():
    if os.environ.get("GITHUB_ACTIONS") != "true" or not os.environ.get("COMPOSE_PROJECT_NAME", "").startswith("stoxsim-ci-"):
        raise SystemExit("Only the isolated GitHub Actions Compose stack is supported")
    fixtures, samples, cleanup = [], [], []
    report = {"candidate_sha": os.environ["CANDIDATE_SHA"], "profile": "4 authenticated cash-only users, one request per user per second, 60 seconds", "production_capacity_claim": False}
    try:
        for _ in range(4):
            password = "M7-fixture-" + secrets.token_hex(12)
            status, raw, _ = request("auth/register", body={"displayName": "CI capacity fixture", "email": "m7-" + secrets.token_hex(12) + "@stoxsim.test", "password": password, "termsAccepted": True})
            if status != 201:
                raise RuntimeError("Fixture registration failed with status " + str(status))
            token = json.loads(raw)["accessToken"]
            fixtures.append((token, password))
        def worker(fixture):
            token, _ = fixture
            status, raw, _ = request("accounts", token)
            if status != 200:
                raise RuntimeError("Account lookup failed")
            accounts = json.loads(raw)
            account = next(a["id"] for a in accounts if a["marketRegion"] == "INDIA" and a["accountKind"] == "STANDARD")
            paths = ["auth/me", "accounts", "accounts/" + account + "/portfolio", "accounts/" + account + "/portfolio/history?days=30", "scenarios"]
            end, index, rows = time.monotonic() + 60, 0, []
            while time.monotonic() < end:
                began = time.monotonic()
                path = paths[index % len(paths)]
                status, _, latency = request(path, token)
                rows.append({"surface": ["identity", "accounts", "portfolio", "history", "scenario_catalog"][index % len(paths)], "status": status, "ms": latency})
                index += 1
                time.sleep(max(0, 1 - (time.monotonic() - began)))
            return rows
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
            for rows in executor.map(worker, fixtures):
                samples.extend(rows)
        surfaces = {}
        for name in sorted({r["surface"] for r in samples}):
            rows = [r for r in samples if r["surface"] == name]
            surfaces[name] = {"requests": len(rows), "errors": sum(r["status"] != 200 for r in rows), "p50_ms": percentile([r["ms"] for r in rows], .5), "p95_ms": percentile([r["ms"] for r in rows], .95)}
        report["surfaces"] = surfaces
        report["passed"] = len(samples) >= 200 and all(s["errors"] == 0 and s["p95_ms"] < 2000 for s in surfaces.values())
    finally:
        for token, password in fixtures:
            status, _, _ = request("auth/me", token, {"password": password}, "DELETE")
            cleanup.append(status == 204)
        report["fixture_cleanup_passed"] = bool(cleanup) and all(cleanup)
        Path("candidate-evidence").mkdir(exist_ok=True)
        Path("candidate-evidence/load.json").write_text(json.dumps(report, indent=2) + "\n")
    if not report.get("passed") or not report["fixture_cleanup_passed"]:
        raise SystemExit("Candidate load or fixture cleanup failed; inspect aggregate evidence")


if __name__ == "__main__":
    main()
