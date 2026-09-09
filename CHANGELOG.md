# Changelog

All notable changes to StoxSim will be documented in this file. The project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and will use semantic versioning beginning with the first public beta tag.

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
