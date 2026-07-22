# StoxSim Architecture

## Shape

StoxSim begins as a modular monolith. This keeps transactions and development straightforward while retaining clear domain boundaries.

## Components

- **Next.js web:** user experience and browser WebSocket client
- **Spring Boot API:** authentication, accounts, orders, execution and portfolios
- **PostgreSQL:** users, virtual accounts, refresh tokens, orders, trades, holdings and ledger
- **Redis:** current quotes, market status and ephemeral subscriptions
- **Market adapters:** Upstox for India and a provider-independent US adapter

## Backend modules

```text
com.stoxsim
├── auth
├── account
├── instrument
├── market
├── calendar
├── order
├── execution
├── portfolio
├── trade
├── watchlist
└── common
```

## Critical boundaries

1. Domain services never depend directly on provider SDK models.
2. Every execution updates the order, account, holding, trade and ledger in one transaction.
3. Each market has an independent account, currency, calendar and charge policy.
4. WebSocket data is advisory; persisted PostgreSQL records remain authoritative.
5. Idempotency keys and row locks prevent duplicate orders and double spending.
6. Registration creates the user and both virtual accounts in one transaction.
7. Refresh tokens are stored only as hashes and rotated after use.

## Current API

- `GET /api/v1/system/status`
- `GET /actuator/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

Instrument, quote and market-status endpoints follow in the next milestone.
