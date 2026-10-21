# Changelog

## Android engagement and Finwiz depth

- Explain Inbox/Spam/Junk checks across email flows.
- Record native foreground/stock-open activity using existing daily analytics.
- Enforce server-selected Basic/Expanded/Full Finwiz depth and matching descriptions.
- Complete verification/reset links natively with explicit confirmation and browser fallback in Android 0.5.3.

All notable changes to StoxSim will be documented in this file. The project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and will use semantic versioning beginning with the first public beta tag.

## [Unreleased]

### Non-payment closure (Android 0.5.7)

- Record successful home stock research; prevent premature Android stock-open analytics.
- Add server-only, daily-deduplicated feature engagement and owner dashboard coverage semantics (V124).
- Reject unverified/fallback Scenario Lab marks without spending credits or marking completion.
- Update frontend dependencies from #165 and reconcile Android/setup/release documentation.
- Payments and VPS-equivalent capacity tests remain deferred; deployment/device evidence is separate.


### Medium/low completion

- Android 0.5.2: reviewed pending limit-order edits, complete campus member/moderation
  controls, and an opt-in Pro leaderboard using equal standard India capital.
- Automatic foreground website daily visits; remove manual learning check-in.
- Separate Pro competition seasons with active, unexpired, non-test entitlement
  checks and independent enrollment/baselines.
- Add protected manual upload-key signing and AAB build preparation.
- Update Upstox SDK, frontend/tooling and GitHub Actions dependencies.
- Reconcile stale roadmap entries; distinguish implemented code from benchmark,
  device/Play and isolated production recovery/capacity acceptance.
- Payment work, Finwiz tier behavior and email delivery remain separate.


### Changed

- Upgrade Android to 0.5.0 with compact holdings, a separate portfolio-analysis view,
  a monochrome Finwiz reactor/module map and inline question composer, an interactive
  green/purple Scenario Lab, refined shared navigation and branded launch/reconnect states.

- Replace Android bottom navigation artwork with stock-chart, brain, lab-flask
  and trophy icons; expose Stock, Finwiz AI, Scenario Lab and Competition as four tabs.

- Remove default JWT signing credentials; reject known template keys, isolate environment
  files from Docker builds, verify backend secret isolation in frontend builds and avoid
  logging raw Gemini errors or Upstox authorization URLs.

- Patch Next.js, sharp and source-map-js to resolve dependency findings from the required security scan.

- Fix search focus overlap and overflowing plan controls; remove repetitive dashboard copy.
- Move StoxScore into Portfolio History as Stox Analysis, with sector allocation charts.
- Open Scenario Lab in a separate tab with preset and named custom paths (up to 12 steps).
- Set Plus capital to ₹25 lakh / $50,000 and Pro to ₹1 crore / $100,000. Existing paid
  users receive a US account on their next account load; existing holdings are preserved.
- Add server-enforced Scenario Lab credits: Free 2 starter runs, Plus 10 and Pro 50
  per successful paid renewal. Retries do not spend twice; failed runs spend nothing.
- Display LIVE/CLOSED market availability and keep simulated orders executable during
  quote-provider outages, using an explicitly identified simulated price fallback.

### Added

- M3 campus membership requests and organizer review, scheduled competitions,
  explicit standard-portfolio enrollment and institution-only standings.
- Campus role moderation, last-organizer protection, admin recovery/suspension,
  audit history, account export/deletion and scoped authorization tests.

- First-party daily activity, seven-day activation and exact-day D1/D7/D30
  retention in the private owner dashboard, with observation/maturity bounds.
- Backend-owned watchlist/trading events, 180-day bounded retention, account
  export/deletion support and an updated privacy notice.

- Private owner analytics with current learner/verification totals, UTC signup
  trends, signup-cohort first-trade conversion and standard-account order counts.
- Database-authorized aggregate API, administrator settings link, bounded date
  filters, date-query indexes and a post-release milestone delivery plan.
- Authorization, aggregation and browser coverage for the owner overview.

## [1.0.0] - 2026-09-09

### Added

- Public Terms, Privacy, Cookie and Risk Disclaimer pages with versioned registration acceptance.
- A fail-closed market-data redistribution permission checklist for public release.
- Repository governance, dependency update automation, dependency review, and CodeQL scanning.
- Email verification, enumeration-safe password recovery, active-session management, security-event history, account export, and permanent account deletion.
- Multi-market paper trading for Indian and United States instruments.
- Persistent watchlists, research pages, portfolio analytics, and trade history.
- Private staging deployment with backups, restore, smoke testing, and verified rollback.
- Public production deployment with automatic HTTPS, authenticated Redis, protected promotion workflows, off-host backup enforcement, production smoke testing, and automatic rollback.
- Production observability with structured request-correlated logs, protected Prometheus metrics, Grafana dashboards, host and dependency alerts, external uptime checks, a public status page, and an incident-response runbook.
- Protected staging load and backup/restore verification with release evidence artifacts.
- Authenticated browser acceptance testing in CI.
- SEC EDGAR fair-access documentation and a final public-release sign-off checklist.
- Explicit production switches for public registration and Upstox/Alpaca public market-data serving; the official production bundle defaults all three switches to closed.

### Changed

- Retired GitHub Actions workflows that targeted the decommissioned hosted staging environment. Production DAST remains available, while future restore drills must use an isolated temporary target and must never replace the live production database.

### Security

- HttpOnly SameSite refresh cookies with rotating hashed refresh tokens.
- JWT-authenticated STOMP connections.
- Redis-backed endpoint rate limits.
- Private vulnerability reporting policy and security ownership boundaries.
- Single-use, time-limited verification and password-reset tokens.
- All refresh sessions are revoked after password changes and resets.
- SEC EDGAR requests use an identified contact and a tested process-wide rate limit below the SEC fair-access ceiling.
- Next.js is patched to 16.3.3, which contains the upstream critical RCE fixes shipped after 16.3.1.
- Provider credentials alone cannot enable public market-data serving; provider clients and background synchronization honor the explicit serving gate.
- Public registration is rejected unless the operator opens registration and both provider-serving gates are enabled.
