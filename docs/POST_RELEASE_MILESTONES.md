# Post-release delivery milestones

This sequence starts after the released v1.0.0 platform, beginner UI completion
and SEO work. These identifiers apply to this post-release sequence; they do
not replace the historical launch milestones or feature batches.

Implement and review one focused milestone at a time. A merged PR, a passing
candidate, and a successful production acceptance are separate states.

| Milestone | Deliverable | Acceptance criteria | Status |
| --- | --- | --- | --- |
| M1 — Owner overview | Private owner dashboard and aggregate API | Database ADMIN authorization on every request; registration trends; verified-user totals; signup-cohort first-trade conversion; standard-account order breakdown; explicit date/privacy semantics; tests | Merged/deployed; owner access confirmed 2026-09-21; remaining acceptance tracked in [#122](https://github.com/M4Rk9/stoxsim/issues/122) |
| M2 — Activity and retention | First-party activity events, activation funnel and retention | Small versioned event allowlist; authenticated identity; bounded ingestion and retention; backend-authoritative trade events; D1/D7/D30 mature-cohort denominators; DAU/WAU/MAU definitions; deletion/export support; admin-only views | Merged in #124; CI passed (164 backend tests, 24 browser tests); deployment reported by owner 2026-09-21; production cohort acceptance remains ongoing |
| M3 — Campus competitions | Institution organizer controls, membership requests, competitions and standings | Institution-scoped authorization; verified membership; opt-in enrollment; standard India accounts only; cross-institution isolation and concurrency tests; moderation and audit trail | Implemented in M3 branch; CI and production acceptance pending |
| M4 — Paid subscriptions | Checkout and cancellation-only subscription lifecycle | Signed/idempotent webhooks; server-owned plan/price mapping; replay/out-of-order handling; renewal/grace; cancel-at-period-end; sandbox locking and settlement; test acceptance before live rollout | Owner confirmed deployed checkout and #127 lifecycle. Final test-mode cancellation engineering implemented; final CI/deployment acceptance pending. Refunds excluded by owner decision. Live merchant activation and a reviewed live-mode rollout remain gated; [completion checklist](RAZORPAY_TEST_BILLING.md). |
| M5 — Advanced portfolio history | Portfolio snapshots, benchmark comparison and historical risk views | Document available licensed data and retention; handle cash flows, gaps, currencies and limited history correctly; enforce premium capabilities on backend; deterministic numerical fixtures | Planned; verify relevant historical-data rights |
| M6 — Scenario Lab | Versioned educational scenarios and isolated what-if results | Define synthetic vs historical inputs; show assumptions and limitations; no mutation of real simulated holdings or competitive scores; backend Pro entitlement; reproducible calculations and tests | Planned; depends on M5 for historical scenarios |
| M7 — Release and capacity evidence | Current production verification and documented operating envelope | Final-candidate DAST and learner acceptance; isolated backup restore; bounded load test on disposable target; latency/error/resource evidence; completed sign-off; release notes and version aligned with deployed SHA | Planned; release checks also apply to each preceding milestone |

## Maintenance alongside the milestones

At the 2026-09-21 review, dependency PRs #118–#121 were open with passing
reported checks. Review each update and its compatibility before merging;
especially review major GitHub Actions updates. Keep dependency changes in
their existing PRs so the M1 feature review remains focused. Passing checks
alone are not approval to merge or deploy.

## M1 scope and completion evidence

- Tracking: [issue #122](https://github.com/M4Rk9/stoxsim/issues/122).
- API: `GET /api/v1/admin/analytics/overview?from=YYYY-MM-DD&to=YYYY-MM-DD`.
- UI: `/admin/analytics`, linked from account settings for administrators.
- Reuses `app_user.platform_role`; no new role-promotion endpoint.
- No analytics vendor, billing service, provider calls or event collection.
- `V110` adds date-query indexes; no existing data is rewritten.
- Backend unit tests cover role enforcement, revoked/deleted requesters,
  UTC defaults, missing days and bounded ranges.
- PostgreSQL integration tests cover cohort/order calculations, duplicate
  trades, both markets, sandbox/admin/deleted-user exclusion, midnight
  boundaries, HTTP authorization, cache policy and empty results.
- Browser tests cover aggregates, date controls, noindex, access denial,
  empty results, failed reloads, session recovery and narrow light/dark layouts.

The feature branch must pass CI before merge. After deploying its candidate,
verify that a reviewed administrator sees the dashboard and a normal learner
receives HTTP 403. Record that evidence with the candidate SHA. No production
role or configuration is changed by this milestone implementation.

## M2 measurement contract

M1 first-trade conversion must not be renamed to the full activation metric.
The existing activation definition requires stock research, a watchlist add
and a valid paper order within seven days of registration. M2 uses the explicit active-day and cohort definitions in PRODUCT_ANALYTICS.md;
it does not expose session counts or durations.
Unobserved historical events must remain unavailable, not backfilled with
invented sessions. Separate event-time cohorts from current-record M1 metrics.

## Delivery gates

For every milestone: focused PR, tests for changed behavior, documentation,
security/privacy review proportionate to the change, and immutable deployment
with rollback evidence. External service purchases, live billing enablement,
production data operations and release publication need their concrete setup
reviewed before execution. Native mobile distribution remains a later product
decision after the web flow and billing are validated.
