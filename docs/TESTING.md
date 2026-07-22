# Testing and acceptance

StoxSim uses three complementary test layers. Backend tests protect domain and transaction rules, frontend checks protect the TypeScript application and production build, and Playwright verifies the learner journey through the complete application stack.

## Local checks

Run backend tests, including PostgreSQL Testcontainers coverage:

```bash
cd backend
mvn test
```

Run frontend type and production-build checks:

```bash
cd frontend
npm ci
npm run typecheck
npm run build
```

## Browser acceptance

The browser test requires PostgreSQL, Redis, the Spring Boot API and the Next.js application. Docker Compose starts the same services used for local development:

```bash
docker compose up --build --detach --wait --wait-timeout 180
cd frontend
npm ci
npx playwright install --with-deps chromium
npm run e2e
```

Stop the isolated stack and remove its test data when finished:

```bash
docker compose down --volumes
```

The authenticated learner journey verifies:

1. A new user can register.
2. The India account starts with exactly ₹5,00,000 available cash and account value.
3. The browser establishes the market WebSocket connection.
4. Authentication and dashboard state survive a page reload.
5. Signing out removes the local session.
6. The same user can sign in again and recover the persisted account.

Each test uses a unique email address and the Compose database is removed after the CI job, so repeated workflow runs remain independent.

## GitHub Actions gate

The `browser-acceptance` CI job starts only after the backend and frontend jobs pass. It builds the production Docker images, waits for PostgreSQL, Redis, API readiness and the rendered frontend, then runs the Chromium journey.

On failure, GitHub Actions retains:

- Playwright screenshots, traces and videos when available
- the HTML Playwright report
- complete Docker Compose logs

These diagnostics are uploaded as the `browser-acceptance-diagnostics` workflow artifact. A pull request is not ready to merge until the backend, frontend and browser-acceptance jobs all pass.

## Staging boundary

This gate proves the full product journey on an isolated runner. The manual staging smoke workflow remains responsible for verifying public HTTPS, deployed image tags, managed services and WebSocket proxying after a staging host is selected.
