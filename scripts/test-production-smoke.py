#!/usr/bin/env python3
"""Exercise real curl requests against a disposable local HTTP server."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parents[1]
TOKEN = "test-secret-access-token-never-print-this"


class ProductionSmokeTest(unittest.TestCase):
    def run_smoke(self, scenario):
        state = {"exists": False, "deletes": 0, "logins": 0, "registers": 0}

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_):
                pass

            def reply(self, code, payload=None, cookie=False):
                self.send_response(code)
                if cookie:
                    self.send_header("Set-Cookie", "stoxsim_refresh=private; Path=/; HttpOnly; Secure; SameSite=Strict")
                self.end_headers()
                if payload is not None:
                    self.wfile.write(json.dumps(payload).encode())

            def do_GET(self):
                if self.path == "/actuator/health/readiness":
                    self.reply(200, {"status": "UP"})
                elif self.path == "/api/v1/auth/me":
                    self.reply(200 if state["exists"] or scenario == "false_deletion" else 401,
                               {"email": state.get("email")})
                else:
                    self.reply(200, "StoxSim support.stoxsim@gmail.com")

            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or "{}")
                if self.path.endswith("/register"):
                    state["registers"] += 1
                    if scenario == "closed":
                        self.reply(503, {"status": 503, "message": "Public registration is temporarily closed"})
                        return
                    state.update(exists=True, email=body["email"], password=body["password"])
                    if scenario == "lost_registration_response":
                        self.close_connection = True
                        return
                    payload = {"user": {"id": "00000000-0000-0000-0000-000000000001",
                               "accounts": [{"marketRegion": "INDIA", "availableCash": 500000},
                                            {"marketRegion": "UNITED_STATES", "availableCash": 10000}]}}
                    if scenario != "missing_registration_token":
                        payload["accessToken"] = TOKEN
                    self.reply(201, payload, cookie=scenario not in ("bad_cookie", "bad_cookie_and_delete_fails"))
                elif self.path.endswith("/login"):
                    state["logins"] += 1
                    if body.get("email") == state.get("email") and body.get("password") == state.get("password") and state["exists"]:
                        self.reply(200, {"accessToken": TOKEN})
                    else:
                        self.reply(401)
                else:
                    self.reply(200, {"accessToken": TOKEN})

            def do_DELETE(self):
                self.rfile.read(int(self.headers.get("Content-Length", 0)))
                state["deletes"] += 1
                if scenario == "expired_cleanup_token" and state["deletes"] == 1:
                    self.reply(401)
                elif scenario in ("delete_fails", "bad_cookie_and_delete_fails"):
                    self.reply(500, {"private": TOKEN})
                elif scenario == "transient_delete" and state["deletes"] == 1:
                    self.reply(503)
                else:
                    state["exists"] = False
                    self.reply(204)

        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            shutil.copy(ROOT / "scripts/production-smoke.sh", tmp / "production-smoke.sh")
            # Scope this suite to registration/auth/cleanup; the security smoke
            # script has its own checks and must not contact any deployed service.
            (tmp / "security-smoke.sh").write_text("#!/bin/bash\nexit 0\n")
            (tmp / "sleep").write_text("#!/bin/bash\nexit 0\n")
            (tmp / "sleep").chmod(0o755)
            server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            base = f"http://127.0.0.1:{server.server_port}"
            summary = tmp / "summary"
            try:
                result = subprocess.run(["bash", str(tmp / "production-smoke.sh")],
                    env={**os.environ, "PATH": str(tmp) + os.pathsep + os.environ["PATH"],
                         "PRODUCTION_API_URL": base, "PRODUCTION_WEB_URL": base,
                         "PRODUCTION_SMOKE_EMAIL": "support.stoxsim@gmail.com",
                         "GITHUB_STEP_SUMMARY": str(summary)},
                    capture_output=True, text=True, timeout=20)
            finally:
                server.shutdown()
                server.server_close()
                thread.join()
            output = result.stdout + result.stderr
            self.assertNotIn(TOKEN, output)
            self.assertNotIn(state.get("password", "not-present"), output)
            self.assertNotIn(state.get("email", "not-present"), output)
            state["summary"] = summary.read_text() if summary.exists() else ""
            state["output"] = output
            return result.returncode, state

    def test_success_deletes_and_verifies(self):
        code, state = self.run_smoke("success")
        self.assertEqual(code, 0, state["output"])
        self.assertFalse(state["exists"])
        self.assertEqual(state["deletes"], 1)
        self.assertIn("session rejection verified", state["output"])

    def test_transient_cleanup_retries(self):
        code, state = self.run_smoke("transient_delete")
        self.assertEqual(code, 0, state["output"])
        self.assertEqual(state["deletes"], 2)
        self.assertFalse(state["exists"])

    def test_failed_cleanup_fails_workflow_and_summary(self):
        code, state = self.run_smoke("delete_fails")
        self.assertNotEqual(code, 0)
        self.assertTrue(state["exists"])
        self.assertEqual(state["deletes"], 3)
        self.assertIn("cleanup failed", state["summary"])
        self.assertIn("::error::Production smoke cleanup failed", state["output"])

    def test_early_failure_still_cleans(self):
        code, state = self.run_smoke("bad_cookie")
        self.assertNotEqual(code, 0)
        self.assertFalse(state["exists"])

    def test_early_failure_with_cleanup_failure_is_visible(self):
        code, state = self.run_smoke("bad_cookie_and_delete_fails")
        self.assertNotEqual(code, 0)
        self.assertTrue(state["exists"])
        self.assertIn("cleanup failed", state["summary"])

    def test_rejected_cleanup_session_recovers_login(self):
        code, state = self.run_smoke("expired_cleanup_token")
        self.assertEqual(code, 0, state["output"])
        self.assertEqual(state["logins"], 1)
        self.assertEqual(state["deletes"], 2)
        self.assertFalse(state["exists"])

    def test_missing_token_recovers_login_for_cleanup(self):
        code, state = self.run_smoke("missing_registration_token")
        self.assertNotEqual(code, 0)
        self.assertEqual(state["logins"], 1)
        self.assertFalse(state["exists"])

    def test_lost_response_recovers_and_preserves_failure(self):
        code, state = self.run_smoke("lost_registration_response")
        self.assertNotEqual(code, 0)
        self.assertEqual(state["logins"], 1)
        self.assertFalse(state["exists"])

    def test_deletion_requires_session_rejection(self):
        code, state = self.run_smoke("false_deletion")
        self.assertNotEqual(code, 0)
        self.assertIn("cleanup failed", state["summary"])

    def test_closed_gate_never_logs_in_or_deletes(self):
        code, state = self.run_smoke("closed")
        self.assertEqual(code, 0, state["output"])
        self.assertEqual(state["logins"], 0)
        self.assertEqual(state["deletes"], 0)


if __name__ == "__main__":
    unittest.main()
