# StoxSim Architecture

## Shape

StoxSim begins as a modular monolith. This keeps transactions and development straightforward while retaining clear domain boundaries.

## Components

- **Next.js web:** user experience and browser WebSocket client
- **Spring Boot API:** authentication, accounts, instruments, orders, execution and portfolios
- **PostgreSQL:** users, virtual accounts, refresh tokens, instruments, orders, trades, holdings and ledger
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
8. Provider instrument keys are the durable market-data identity; display symbols are searchable labels.
9. A failed instrument download cannot deactivate the existing catalogue.

## Current API

- `GET /api/v1/system/status`
- `GET /actuator/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `GET /api/v1/instruments/search`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}`

Quote, candle and market-stream endpoints follow in the next milestone.
