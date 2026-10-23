import importlib.util
from datetime import date
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("calendar_readiness", Path(__file__).with_name("calendar-readiness.py"))
calendar = importlib.util.module_from_spec(spec)
spec.loader.exec_module(calendar)
FIXTURE = "INDIA.coverage=2026-01-01|2026-12-31\nUS.coverage=2026-01-01|2027-12-31\n"


class CalendarReadinessTest(unittest.TestCase):
    def test_region_specific_coverage_and_warning(self):
        rows = calendar.report(FIXTURE, date(2026, 10, 23))
        self.assertEqual([r["status"] for r in rows], ["REVIEW_DUE", "READY"])
        self.assertEqual(rows[0]["days_remaining"], 69)

    def test_threshold_and_expiry_fail_closed(self):
        self.assertEqual(calendar.report(FIXTURE, date(2026, 11, 1))[0]["status"], "REVIEW_DUE")
        self.assertEqual(calendar.report(FIXTURE, date(2026, 11, 2))[0]["status"], "BLOCKED")
        self.assertEqual(calendar.report(FIXTURE, date(2027, 1, 1))[0]["status"], "BLOCKED")
        self.assertEqual(calendar.report(FIXTURE, date(2025, 12, 31))[0]["status"], "BLOCKED")

    def test_unknown_date_stops_horizon_but_closures_do_not(self):
        rows = calendar.report(FIXTURE + "INDIA.2026-11-08=UNKNOWN\nUS.2026-11-26=CLOSED\n", date(2026, 10, 23))
        self.assertEqual(rows[0]["known_through"], "2026-11-07")
        self.assertEqual(rows[0]["status"], "BLOCKED")
        self.assertEqual(rows[1]["status"], "READY")
        self.assertEqual(calendar.report(FIXTURE + "INDIA.2026-01-01=UNKNOWN\n", date(2026, 10, 23))[0]["status"], "REVIEW_DUE")

    def test_malformed_or_missing_coverage_cannot_pass(self):
        for text in ["", FIXTURE + "US.coverage=2026-01-01|2028-12-31\n", FIXTURE.replace("2027-12-31", "2025-12-31"),
                     FIXTURE + "INDIA.2027-01-01=CLOSED\n", FIXTURE + "invalid line\n"]:
            with self.subTest(text=text), self.assertRaises((ValueError, KeyError)):
                calendar.report(text, date(2026, 10, 23))


if __name__ == "__main__":
    unittest.main()
