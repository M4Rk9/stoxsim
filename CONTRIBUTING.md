# Contributing to StoxSim

Thank you for helping improve StoxSim.

## Before you start

- Use an issue for substantial product or architecture changes.
- Report vulnerabilities through the private process in [SECURITY.md](SECURITY.md).
- Keep pull requests focused and do not mix unrelated refactors with a fix.
- Never include real credentials, production data, or personal user data.

## Development

The supported toolchain is Java 21, Node.js 24, PostgreSQL, Redis, Docker Compose, Maven, and npm.

Run the full local stack:

```bash
cp .env.example .env
docker compose up --build
```

Run the primary checks:

```bash
cd backend && mvn -B test
cd frontend && npm ci && npm run typecheck && npm run build
cd frontend && npx playwright install chromium && npm run e2e
```

## Pull requests

1. Branch from the current default branch.
2. Add or update tests for behavior changes.
3. Add a forward-only Flyway migration for schema changes; never edit a migration that may already have run.
4. Update documentation and CHANGELOG.md for user-visible behavior.
5. Complete the pull request template.
6. Wait for every required GitHub check to pass before merging.

Prefer a squash merge with a concise imperative title. Delete the head branch after merge.

## Project boundaries

StoxSim is an educational paper-trading simulator. Contributions must not imply that it executes real brokerage orders or gives individualized investment advice.
