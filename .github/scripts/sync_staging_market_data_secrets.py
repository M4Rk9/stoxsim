#!/usr/bin/env python3
import json
import sys
from pathlib import Path


def update_env_file(
    path: Path,
    values: dict[str, str],
    remove_keys: set[str] | None = None,
) -> None:
    existing_lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    updated: list[str] = []
    replaced: set[str] = set()
    removed = remove_keys or set()

    for line in existing_lines:
        key = line.split("=", 1)[0] if "=" in line else None
        if key in removed:
            continue
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
        raise SystemExit("Missing required provider values: " + ", ".join(sorted(missing)))

    values = {
        "ALPACA_API_KEY_ID": str(payload["ALPACA_API_KEY_ID"]),
        "ALPACA_API_SECRET_KEY": str(payload["ALPACA_API_SECRET_KEY"]),
        "ALPACA_DATA_FEED": str(payload.get("ALPACA_DATA_FEED", "iex")),
        "ALPACA_INSTRUMENT_SYNC_ON_STARTUP": str(payload.get("ALPACA_INSTRUMENT_SYNC_ON_STARTUP", "true")),
        "ALPACA_POLLING_ENABLED": str(payload.get("ALPACA_POLLING_ENABLED", "true")),
        "ALPACA_POLLING_INTERVAL_MILLIS": str(payload.get("ALPACA_POLLING_INTERVAL_MILLIS", "5000")),
        "GEMINI_API_KEY": str(payload.get("GEMINI_API_KEY", "")),
        "GEMINI_MODEL": str(payload.get("GEMINI_MODEL", "gemini-3.6-flash")),
        "GEMINI_BASE_URL": str(payload.get("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com")),
        "FINWIZ_AI_ENABLED": str(payload.get("FINWIZ_AI_ENABLED", "true")),
        "FINWIZ_MAX_QUESTION_CHARACTERS": str(payload.get("FINWIZ_MAX_QUESTION_CHARACTERS", "2000")),
        "FINWIZ_MAX_OUTPUT_TOKENS": str(payload.get("FINWIZ_MAX_OUTPUT_TOKENS", "900")),
        "FINWIZ_THINKING_LEVEL": str(payload.get("FINWIZ_THINKING_LEVEL", "minimal")),
    }
    # New fields are optional; older workflow payloads must not erase them.
    optional = ["GROQ_API_KEY","CLOUDFLARE_API_TOKEN","CLOUDFLARE_ACCOUNT_ID","OPENROUTER_API_KEY","FINWIZ_GROQ_ENABLED","FINWIZ_GROQ_FREE_CAPACITY_CONFIRMED","GROQ_MODEL","FINWIZ_GROQ_DAILY_LIMIT","FINWIZ_CLOUDFLARE_ENABLED","FINWIZ_CLOUDFLARE_FREE_CAPACITY_CONFIRMED","CLOUDFLARE_AI_MODEL","FINWIZ_CLOUDFLARE_DAILY_LIMIT","FINWIZ_OPENROUTER_ENABLED","FINWIZ_OPENROUTER_FREE_CAPACITY_CONFIRMED","OPENROUTER_MODEL","OPENROUTER_UPSTREAM","FINWIZ_OPENROUTER_DAILY_LIMIT","FINWIZ_GEMINI_DAILY_LIMIT","FINWIZ_ATTEMPT_TIMEOUT_SECONDS","FINWIZ_DEADLINE_SECONDS","FINWIZ_PROVIDER_CONCURRENCY","FINWIZ_BASIC_DAILY_REQUESTS","FINWIZ_EXPANDED_DAILY_REQUESTS","FINWIZ_FULL_DAILY_REQUESTS","FINWIZ_GLOBAL_DAILY_ATTEMPTS"]
    optional += ["FINWIZ_GEMINI_REQUESTS_PER_MINUTE","FINWIZ_GEMINI_TOKENS_PER_MINUTE","FINWIZ_GEMINI_TOKENS_PER_DAY","FINWIZ_GROQ_REQUESTS_PER_MINUTE","FINWIZ_GROQ_TOKENS_PER_MINUTE","FINWIZ_GROQ_TOKENS_PER_DAY","FINWIZ_CLOUDFLARE_REQUESTS_PER_MINUTE","FINWIZ_CLOUDFLARE_TOKENS_PER_MINUTE","FINWIZ_CLOUDFLARE_TOKENS_PER_DAY","FINWIZ_OPENROUTER_REQUESTS_PER_MINUTE","FINWIZ_OPENROUTER_TOKENS_PER_MINUTE","FINWIZ_OPENROUTER_TOKENS_PER_DAY"]
    for key in optional:
        if key in payload:
            value = str(payload[key])
            if "\n" in value or "\r" in value:
                raise SystemExit("Invalid multiline provider setting: " + key)
            values[key] = value
    update_env_file(
        Path(sys.argv[1]),
        values,
        remove_keys={
            "OPENAI_API_KEY",
            "OPENAI_MODEL",
            "OPENAI_BASE_URL",
            "FINWIZ_THINKING_BUDGET",
        },
    )


if __name__ == "__main__":
    main()
