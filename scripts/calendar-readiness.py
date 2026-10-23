#!/usr/bin/env python3
"""Read-only warning before reviewed exchange coverage expires; never invent dates."""
import argparse
from datetime import date, datetime, timedelta
import json
from pathlib import Path
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[1]
ZONES = {"INDIA": "Asia/Kolkata", "US": "America/New_York"}


def report(text, as_of=None, minimum_days=60):
    if minimum_days < 0:
        raise ValueError("minimum-days must be nonnegative")
    data = {}
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        key, separator, value = line.partition("=")
        key, value = key.strip(), value.strip()
        if not separator or not key or key in data:
            raise ValueError("Malformed or duplicate calendar property")
        data[key] = value
    results = []
    for region, zone in ZONES.items():
        today = as_of or datetime.now(ZoneInfo(zone)).date()
        start, end = map(date.fromisoformat, data[f"{region}.coverage"].split("|"))
        if start > end:
            raise ValueError(f"{region}: reversed coverage")
        effective_end = end
        for key, value in data.items():
            if not key.startswith(f"{region}.") or key.endswith((".coverage", ".hours")):
                continue
            day = date.fromisoformat(key.split(".", 1)[1])
            if not start <= day <= end:
                raise ValueError(f"{region}: exception outside coverage")
            if value == "UNKNOWN" and day >= today:
                effective_end = min(effective_end, day - timedelta(days=1))
        remaining = (effective_end - today).days
        status = "BLOCKED" if today < start or remaining < minimum_days else "REVIEW_DUE" if remaining < 90 else "READY"
        results.append({"region": region, "as_of": today.isoformat(), "coverage_start": start.isoformat(),
                        "coverage_end": end.isoformat(), "known_through": effective_end.isoformat(),
                        "days_remaining": remaining, "status": status})
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--calendar", type=Path, default=ROOT / "backend/src/main/resources/market-calendar.properties")
    parser.add_argument("--as-of", type=date.fromisoformat, help="Deterministic review date (default: current exchange-local date)")
    parser.add_argument("--minimum-days", type=int, default=60)
    args = parser.parse_args()
    try:
        results = report(args.calendar.read_text(), args.as_of, args.minimum_days)
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, f"Calendar readiness failed: {error}\n")
    print(json.dumps({"minimum_days": args.minimum_days, "regions": results}, indent=2))
    return int(any(row["status"] == "BLOCKED" for row in results))


if __name__ == "__main__":
    raise SystemExit(main())
