#!/usr/bin/env python3
import json
import os
import sys
from pathlib import Path


def require(name: str) -> str:
    value = os.environ.get(name, "")
    if not value.strip():
        raise SystemExit(f"Missing required environment variable: {name}")
    return value


def update_env_file(path: Path, values: dict[str, str]) -> None:
    existing_lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    updated: list[str] = []
    replaced: set[str] = set()

    for line in existing_lines:
        key = line.split("=", 1)[0] if "=" in line else None
        if key in values:
            updated.append(f"{key}={values[key]}")
            replaced.add(key)
        else:
            updated.append(line)

    for key, value in values.items():
        if key not in replaced:
            updated.append(f"{key}={value}")

    path.write_text("\n".join(updated) + "\n", encoding="utf-8")
    path.chmod(0o600)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: sync_staging_market_data_secrets.py <env-file>")

    payload = json.load(sys.stdin)
    required = {
        "ALPACA_API_KEY_ID",
        "ALPACA_API_SECRET_KEY",
    }
    missing = [key for key in required if not str(payload.get(key, "")).strip()]
    if missing:
        raise SystemExit("Missing required Alpaca values: " + ", ".join(sorted(missing)))

    values = {
        "ALPACA_API_KEY_ID": str(payload["ALPACA_API_KEY_ID"]),
        "ALPACA_API_SECRET_KEY": str(payload["ALPACA_API_SECRET_KEY"]),
        "ALPACA_DATA_FEED": str(payload.get("ALPACA_DATA_FEED", "iex")),
        "ALPACA_INSTRUMENT_SYNC_ON_STARTUP": str(payload.get("ALPACA_INSTRUMENT_SYNC_ON_STARTUP", "true")),
        "ALPACA_POLLING_ENABLED": str(payload.get("ALPACA_POLLING_ENABLED", "true")),
        "ALPACA_POLLING_INTERVAL_MILLIS": str(payload.get("ALPACA_POLLING_INTERVAL_MILLIS", "5000")),
    }
    update_env_file(Path(sys.argv[1]), values)


if __name__ == "__main__":
    main()
