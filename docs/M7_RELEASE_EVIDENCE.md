# M7 release and capacity evidence

M7 distinguishes completed candidate engineering from production sign-off.
A deployment report from the owner is evidence of deployment, not a substitute
for a measured capacity limit, production DAST, or a production-backup restore.

## Automated candidate evidence

Every successful CI browser-acceptance job now runs the following on its own
Compose project (`stoxsim-ci-<run>-<attempt>`), with no production credentials:

1. Existing authenticated learner and feature browser tests.
2. Record candidate SHA, actual checked-out SHA, run URL, CPU count and RAM.
   PR CI may test a synthetic merge commit; both identities are retained.
3. Register four disposable learners. For 60 seconds, each worker sends at most
   one request per second across identity, accounts, cash-only portfolio, portfolio
   history and the scenario catalog. Individual requests time out after five seconds.
4. Require at least 200 requests, no non-200 responses and p95 below 2,000 ms on
   every surface. Clean up every registered fixture and fail if deletion fails.
5. Sample container CPU/memory/network/block-I/O observations approximately every
   five seconds while the load runs. These are observations, not resource thresholds.
6. Stop application writers, dump the disposable PostgreSQL database, restore into
   a separate `m7_restore` database, and compare row counts plus canonical ordered
   row fingerprints for every public table. Failure blocks CI. The temporary
   database and dump are removed regardless of success.

The `candidate-evidence-<sha>` artifact lasts 14 days. It contains aggregate load
results, runner identity, container resource observations and restore outcome.
No tokens, passwords, emails, database dumps or row contents are uploaded.
The CI project's volumes are deleted at job completion. Do not run these scripts
on the production host; they intentionally accept only the CI project convention
and fixed loopback API address. No arbitrary remote target is accepted.

This small closed-loop load profile is a repeatable regression baseline. It does
**not** establish maximum concurrent users, MAU, production throughput, populated
portfolio performance, provider latency, Pro scenario throughput or a saturation
point. Its restore test is neither proof that an encrypted production backup can
be recovered nor a production RTO/RPO measurement.

## Operational workflows

The dedicated **Production backup recovery** and **VPS-equivalent load test** workflows
are implemented in the M7 operational-drill follow-up. See [setup, execution and completion](M7_OPERATIONAL_RUNBOOK.md).
They require a separate validation host and scoped backup access; engineering tests do not close their real-run gates.

## Operational review — 2026-09-26 (IST)

Status: **engineering implemented; observed operational checks passed; full M7
sign-off pending**. The owner supplied terminal, S3, Grafana and inbox screenshots
during the guided review. These observations were reviewed from screenshots,
not obtained through direct SSH or AWS access. Keep the original evidence private.

The production API/web containers shown in the review use image SHA
`847b5ea213a2167dad92f5a4baefca7ef28886e1`.
Operational tooling was merged separately in [PR #133](https://github.com/M4Rk9/stoxsim/pull/133)
at `49eeaf1ed83b4e7db798e4b91e8fe701dfc0ffe1`; merging tooling does not prove a new
application deployment.

| Check | Observed evidence | Scope and limits |
| --- | --- | --- |
| Scheduled offsite upload | Dump `stoxsim-production-20260926T021501Z.dump` (2.6 MB) and matching checksum (107 B), uploaded at 07:45:05 and 07:45:07 IST | Confirms the expected day's objects appeared; no restore or downloaded checksum verification performed in this review |
| Services and monitoring readiness | All listed containers running; API/web/PostgreSQL/Redis healthy; Prometheus ready and Alertmanager returned OK | Point-in-time health |
| Monitoring targets | Six targets UP with no scrape errors: blackbox, node, prometheus, two public-https targets and stoxsim-api | Scrape health; Grafana separately showed two public endpoints up |
| Alert delivery | `StoxSimM7EmailTest` FIRING received at 11:53 and RESOLVED at 11:58 IST | Synthetic alert submitted directly to Alertmanager; verifies routing/email delivery, not Prometheus rule evaluation end to end |
| Resource snapshot | Root disk 45% used with 22 GB available; RAM 2.1 GiB available of 3.7 GiB; swap usage 38 MiB; all containers below memory caps | Backend and Grafana approximately 77% of their caps; no heavy-load capacity inference |
| Log rotation | All ten containers: json-file, max-size 10m, max-file 3 | Configuration inspected; not a forced rotation test |
| Grafana | Dashboard opened through SSH tunnel; API, JVM and database-pool metrics populated; one-hour view showed 46.5 ms API p95 | Failure panels showed No data; do not interpret missing series as a verified zero error rate |
| Rollback files | Previous image tag exists and matches 40 lowercase hexadecimal characters; previous deployment archive exists and can be listed by tar | No rollback executed; image availability, application/schema compatibility and recovery success remain untested |

Registration remains intentionally open. Daily scheduling and retention were reported
configured in the preceding setup; today's screenshot verifies object arrival, not
the complete retention/encryption policy.

### Explicitly deferred by the owner

- **Real production-backup recovery:** not executed. The owner declined local
  WSL/Docker setup; do not require laptop installation or imply that a separate
  validation host has been provisioned.
- **VPS-equivalent load test:** not executed. CI synthetic results and the current
  resource snapshot do not establish the production operating envelope.

Resume these with the isolated workflows in [the operational runbook](M7_OPERATIONAL_RUNBOOK.md)
when a suitable validation target and scoped access are available. Deferral is not
a pass or a waiver of full M7 verification. Final release identity, remaining
acceptance items and operator go/no-go remain open; no release is published by
this record.

## Remaining production sign-off

Retain evidence privately where it includes operational details. Record dates,
run links and immutable SHA references in the release checklist, not credentials.

| Gate | Required evidence | Current state |
| --- | --- | --- |
| M1–M6 deployment | Owner report and deployment run for the exact image SHA | M7 deployment run 35983761385 succeeded; final release identity remains an operator sign-off |
| Candidate CI | All checks and candidate-evidence artifact | PR #133 checks passed; merged-main CI run 36109753505 succeeded |
| Production DAST | Successful existing Security DAST workflow against final deployment | Security DAST #15 / run 36028693643 succeeded on the deployed M7 revision |
| Production learner acceptance | Dated checklist for actual deployed UI, mail and provider flows | Owner reports features and Scenario Lab work; monitoring email verified 2026-09-26; remaining learner-mail acceptance and final operator sign-off remain open |
| Production backup recovery | Restore a selected encrypted backup into an isolated approved target, verify data and record timings | Deferred by owner; scheduled upload verified 2026-09-26, actual recovery untested |
| VPS operating envelope | Bounded load on an approved disposable target matching VPS resources, with latency/errors/CPU/RAM and workload stated | Deferred by owner; no measured production-capacity claim |
| Production uptime/monitoring | Successful uptime run, targets up, controlled alert receipt | Uptime run 36209840071 succeeded; targets, dashboard and firing/resolved email reviewed 2026-09-26 (see scope above) |
| Release identity | Approved version, deployed SHA, passing checks, notes and operator | Draft notes below; no new tag or release published |

Use `docs/RELEASE_CHECKLIST.md` for the final go/no-go. Keep benchmark retention
disabled until the provider storage permission is reviewed, and billing in the
existing test-only mode until a separately reviewed live rollout. Cancellation
remains the supported customer action; no refund feature is introduced.

## Draft release notes — post-release milestones M1–M6

Version: pending owner approval and final production evidence.

- Private owner analytics, activity measurement and retention views.
- Campus membership controls and opt-in competitions.
- Razorpay test-mode subscriptions and cancellation lifecycle.
- Daily portfolio history, plan-aware analytics and gated benchmark comparisons.
- Pro Scenario Lab with versioned synthetic paths, custom shocks, drawdown and
  per-holding effects; account balances and competition scores remain unchanged.
- Repeatable CI load baseline and isolated PostgreSQL restore evidence.

Known limits: live billing remains gated; historical portfolio observations begin
at deployment; risk metrics need sufficient valid observations; benchmark storage
permission must be reviewed before activation; scenarios are synthetic uniform
shocks, not historical events or forecasts. Production capacity is not yet measured.

Do not create a release tag or publish these notes until the final deployed SHA,
approved version and production sign-off are recorded. The original v1.0.0 tag
must not be reused for a later release.
