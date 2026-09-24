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

## Remaining production sign-off

Retain evidence privately where it includes operational details. Record dates,
run links and immutable SHA references in the release checklist, not credentials.

| Gate | Required evidence | Current state |
| --- | --- | --- |
| M1–M6 deployment | Owner report and deployment run for the exact image SHA | M7 deployment run 35983761385 succeeded; final release identity remains an operator sign-off |
| Candidate CI | All checks and candidate-evidence artifact | Produced by this PR and rerun on merged main |
| Production DAST | Successful existing Security DAST workflow against final deployment | Security DAST #15 / run 36028693643 succeeded on the deployed M7 revision |
| Production learner acceptance | Dated checklist for actual deployed UI, mail and provider flows | Owner reports features and Scenario Lab work; mail/monitoring and final operator sign-off remain open |
| Production backup recovery | Restore a selected encrypted backup into an isolated approved target, verify data and record timings | CI synthetic backup evidence does not satisfy this gate |
| VPS operating envelope | Bounded load on an approved disposable target matching VPS resources, with latency/errors/CPU/RAM and workload stated | No production-capacity claim yet |
| Production uptime/monitoring | Successful uptime run, targets up, controlled alert receipt | Uptime run 35996186418 succeeded; monitoring/alert evidence remains an operator sign-off |
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
