# Changelog

All notable changes to StoxSim will be documented in this file. The project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and will use semantic versioning beginning with the first public beta tag.

## [Unreleased]

### Added

- Public Terms, Privacy, Cookie and Risk Disclaimer pages with versioned registration acceptance.
- A fail-closed market-data redistribution permission checklist for public release.
- Repository governance, dependency update automation, dependency review, and CodeQL scanning.
- Email verification, enumeration-safe password recovery, active-session management, security-event history, account export, and permanent account deletion.
- Multi-market paper trading for Indian and United States instruments.
- Persistent watchlists, research pages, portfolio analytics, and trade history.
- Private staging deployment with backups, restore, smoke testing, and verified rollback.
- Authenticated browser acceptance testing in CI.

### Security

- HttpOnly SameSite refresh cookies with rotating hashed refresh tokens.
- JWT-authenticated STOMP connections.
- Redis-backed endpoint rate limits.
- Private vulnerability reporting policy and security ownership boundaries.
- Single-use, time-limited verification and password-reset tokens.
- All refresh sessions are revoked after password changes and resets.
