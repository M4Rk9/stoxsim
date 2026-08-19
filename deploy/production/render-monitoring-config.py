#!/usr/bin/env python3
"""Render secret-bearing monitoring files from the production .env."""

from __future__ import annotations

import json
import os
from pathlib import Path
import tempfile


DEPLOY_DIR = Path(__file__).resolve().parent
ENV_FILE = DEPLOY_DIR / ".env"
MONITORING_DIR = DEPLOY_DIR / "monitoring"
TEMPLATE_FILE = MONITORING_DIR / "alertmanager.yml.template"
ALERTMANAGER_FILE = MONITORING_DIR / "alertmanager.yml"
TOKEN_FILE = MONITORING_DIR / "metrics-scrape-token"


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        values[key.strip()] = value
    return values


def required(values: dict[str, str], key: str) -> str:
    value = values.get(key, "").strip()
    if not value:
        raise SystemExit(f"Set {key} in {ENV_FILE}")
    return value


def atomic_write(path: Path, content: str, mode: int = 0o600) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.",
        dir=path.parent,
        text=True,
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write(content)
        os.chmod(temporary, mode)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def yaml_string(value: str) -> str:
    return json.dumps(value)


def main() -> None:
    if not ENV_FILE.is_file():
        raise SystemExit(f"Missing {ENV_FILE}")

    values = read_env(ENV_FILE)
    scrape_token = required(values, "METRICS_SCRAPE_TOKEN")
    if len(scrape_token) < 32:
        raise SystemExit("METRICS_SCRAPE_TOKEN must contain at least 32 characters")

    replacements = {
        "__MAIL_SMARTHOST__": yaml_string(
            f"{required(values, 'MAIL_HOST')}:{required(values, 'MAIL_PORT')}"
        ),
        "__MAIL_FROM__": yaml_string(required(values, "MAIL_FROM")),
        "__MAIL_USERNAME__": yaml_string(required(values, "MAIL_USERNAME")),
        "__MAIL_PASSWORD__": yaml_string(required(values, "MAIL_PASSWORD")),
        "__ALERT_EMAIL_TO__": yaml_string(required(values, "ALERT_EMAIL_TO")),
    }

    rendered = TEMPLATE_FILE.read_text(encoding="utf-8")
    for placeholder, replacement in replacements.items():
        rendered = rendered.replace(placeholder, replacement)
    if "__" in rendered:
        raise SystemExit("An Alertmanager template placeholder was not rendered")

    os.chmod(MONITORING_DIR, 0o700)
    # Containers run as non-root users and must read these bind-mounted files.
    # The parent directory remains owner-only on the host.
    atomic_write(ALERTMANAGER_FILE, rendered, 0o644)
    atomic_write(TOKEN_FILE, scrape_token + "\n", 0o644)
    print("Rendered protected monitoring configuration")


if __name__ == "__main__":
    main()
