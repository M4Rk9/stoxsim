# Changelog

All notable changes to StoxSim will be documented in this file. The project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and will use semantic versioning beginning with the first public beta tag.

## [Unreleased]

### Added

- Repository governance, dependency update automation, dependency review, and CodeQL scanning.
- Multi-market paper trading for Indian and United States instruments.
- Persistent watchlists, research pages, portfolio analytics, and trade history.
- Private staging deployment with backups, restore, smoke testing, and verified rollback.
- Authenticated browser acceptance testing in CI.

### Security

- HttpOnly SameSite refresh cookies with rotating hashed refresh tokens.
- JWT-authenticated STOMP connections.
- Redis-backed endpoint rate limits.
- Private vulnerability reporting policy and security ownership boundaries.
