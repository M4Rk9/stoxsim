# StoxSim Architecture

Subscription entitlements and account-scope isolation are documented in
`SUBSCRIPTIONS.md`. Standard trading APIs select only `STANDARD` accounts;
the portfolio switcher and paid sandbox APIs use an authenticated owned account
ID. Sandbox accounts are structurally ineligible for standard rankings.

## Shape

StoxSim begins as a modular monolith. This keeps transactions and development straightforward while retaining clear domain boundaries.

## Components

- **Next.js web:** user experience and browser STOMP/WebSocket client
- **Spring Boot API:** authentication, accounts, instruments, market data, orders, execution and portfolios
- **PostgreSQL:** users, virtual accounts, refresh tokens, instruments, orders, trades, holdings and ledger
- **Redis:** current quotes, historical-candle responses, market status and ephemeral subscriptions
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
├── onboarding
└── common
```

## Market-data flow

1. The Upstox adapter converts SDK models into StoxSim `Quote` and `Candle` records.
2. REST quote requests read Redis first and fall back to Upstox on cache miss.
3. Historical-candle responses are cached using the instrument, interval and date range.
4. One reconnecting Upstox V3 stream receives the configured index set and dynamic subscriptions.
5. Every tick refreshes Redis and is broadcast to browser clients on `/topic/market/quotes`.
6. Provider SDK types remain inside `market.provider.upstox`.

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
10. An unavailable Redis cache falls back to the provider instead of stopping quote retrieval.

## Current API

- `GET /api/v1/system/status`
- `GET /actuator/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `GET /api/v1/onboarding`
- `POST /api/v1/onboarding/introduction/complete`
- `POST /api/v1/onboarding/dismiss`
- `POST /api/v1/onboarding/resume`
- `GET /api/v1/instruments/search`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}/quote`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}/candles`
- `WS /ws/market`
- `GET /api/v1/market/status`
- `GET /api/v1/portfolio`
- `GET /api/v1/portfolio/analytics`
- `GET /api/v1/accounts`
- `GET /api/v1/accounts/{accountId}/portfolio`
- `GET /api/v1/accounts/{accountId}/portfolio/analytics`
- `GET /api/v1/accounts/{accountId}/portfolio/insights`
- `GET|POST /api/v1/accounts/{accountId}/orders`
- `GET|PUT|DELETE /api/v1/accounts/{accountId}/orders/{orderId}`
- `GET /api/v1/accounts/{accountId}/holdings`
- `GET /api/v1/accounts/{accountId}/trades`
- `GET /api/v1/accounts/{accountId}/ledger`
- `POST /api/v1/orders`
- `GET|PUT|DELETE /api/v1/orders/{id}`
- `GET /api/v1/holdings`
- `GET /api/v1/trades`
- `GET /api/v1/account/ledger`


## Paper-order transaction

Each execution locks the virtual account and order, then atomically settles reserved cash or shares, updates the holding, creates one trade, writes one ledger entry and closes the order. PostgreSQL constraints, pessimistic locks and idempotency keys prevent overspending, overselling and duplicate submissions.

An accepted paper order records the learner's first-order milestone in the same
database transaction. The browser may complete or dismiss the educational
introduction, but it cannot claim first-trade completion.
