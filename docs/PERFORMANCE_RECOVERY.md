# Performance and recovery verification

Milestone 7 establishes a repeatable staging load baseline and proves that a verified database backup can actually restore learner data. It does not change production data.

## Protected workflow

Open **Actions → Performance and recovery verification → Run workflow**. The workflow uses the existing protected `staging` environment and shares its deployment concurrency lock, so it cannot overlap a staging deployment.

Two modes are available:

- `load-only` runs a bounded authenticated k6 baseline and can be used for ordinary regression checks.
- `load-and-restore` runs the same load baseline, then replaces the staging database with a backup created during the workflow. It requires the exact confirmation `replace-staging-database` and any configured `staging` environment approval.

The recovery drill intentionally causes brief staging downtime. Never point it at production and do not run it while another person is using staging.

## Load contract

The baseline uses four virtual users for three minutes. Each virtual user issues at most one request per second and rotates across:

- the public web page and system status;
- the current-user endpoint;
- the India portfolio and order list;
- the default watchlist.

The workflow creates one temporary learner before the test and permanently deletes it in teardown. It does not submit orders or call paid AI endpoints.

The run fails when:

- more than 1% of HTTP requests fail;
- fewer than 99% of checks pass;
- overall or authenticated p95 response time exceeds two seconds.

This is the public-beta regression baseline for the current low-memory staging host, not a claim of production-scale capacity. Raise the profile only after documenting the expected user concurrency and confirming that the configured API rate limits will not invalidate the result.

## Recovery proof

The guarded drill:

1. Creates a unique learner marker.
2. Uploads versioned helper scripts and runs them as files, so Docker cannot consume the remote control script through standard input.
3. Runs the existing staging `backup.sh` with detached input and pins a run-specific recovery copy.
4. Verifies both the SHA-256 checksum and PostgreSQL archive before deleting any live data.
5. Deletes the marker from the live database.
6. Runs the confirmation-guarded `restore.sh` using the exact verified backup.
7. Waits for API and web readiness.
8. Proves the deleted marker was restored by signing in and reading its identity.
9. Permanently deletes the marker and run-specific recovery files.

The pre-deletion verification barrier is intentional: a failed, missing, stale, or malformed backup stops the drill while the marker still exists. Each workflow run uses unique remote helper and backup filenames, preventing concurrent or stale runs from selecting one another's artifacts.

The workflow uploads `k6-summary.json` and, when applicable, `restore-evidence.txt`. These artifacts and the workflow URL belong in the release sign-off record.

## Acceptance criteria

Milestone 7 is complete when one `load-and-restore` run against the release-candidate staging deployment is green and its artifacts show:

- the k6 thresholds passed;
- the dump checksum passed;
- the restored marker could authenticate;
- both the web and API recovered;
- the temporary marker was deleted after verification.

Milestone 6's market-open Security DAST evidence remains a separate release gate and may be collected later.
