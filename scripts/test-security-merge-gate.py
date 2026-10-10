"""Verify the required check fails closed when a security scan does not succeed."""
import os
from pathlib import Path
import re
import subprocess
import textwrap

root = Path(__file__).resolve().parents[1]
ci = (root / ".github/workflows/ci.yml").read_text()
scanner = (root / ".github/workflows/security-verification.yml").read_text()
backend = ci.split("  backend:\n", 1)[1].split("  frontend:\n", 1)[0]
assert "uses: ./.github/workflows/security-verification.yml" in ci
assert "workflow_call:" in scanner
assert "needs: [security]" in backend
assert "if: ${{ always() }}" in backend
assert "SECURITY_RESULT: ${{ needs.security.result }}" in backend
assert "name: Secret history scan" in scanner
assert "name: Dependency and configuration scan" in scanner
# Neither scan may be silently skipped or have its failure ignored.
assert not re.search(r"^\s*(if|continue-on-error):", scanner, re.MULTILINE)
guard = re.search(r"        run: \|\n((?:          .*\n)+)", backend)
assert guard, "Missing security result guard before backend steps"
shell = textwrap.dedent(guard.group(1))
for result in ("success", "failure", "cancelled", "skipped", ""):
    process = subprocess.run(["bash", "-e", "-c", shell],
                             env={**os.environ, "SECURITY_RESULT": result},
                             text=True, capture_output=True)
    assert (process.returncode == 0) == (result == "success"), (result, process.stdout, process.stderr)
print("Security merge gate: success passes; failed, cancelled, skipped and missing results fail.")
