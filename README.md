# StoxSim

**Practise markets. Risk nothing.**

StoxSim is a multi-market paper-trading platform for Indian and United States stocks. Users receive separate virtual accounts, research equities and ETFs, submit simulated orders and measure portfolio performance without risking real money.

> This project is an educational simulator. It does not place real brokerage orders or provide investment advice.

## Markets

| India | United States |
|---|---|
| ₹5,00,000 virtual capital | $10,000 virtual capital |
| NSE equities and ETFs | NASDAQ and NYSE equities and ETFs |
| NIFTY 50, SENSEX and sector indices | S&P 500, NASDAQ-100 and Dow |
| Indian sessions and simulated charges | US sessions and simulated fees |

## Technology

- Java 21 and Spring Boot 4.1
- Next.js 16 and TypeScript
- PostgreSQL and Flyway
- Redis
- Docker Compose
- GitHub Actions
- PostgreSQL Testcontainers for migration and concurrency tests
- Playwright browser acceptance tests

## Run locally

```bash
cp .env.example .env
docker compose up --build
```

- Web application: http://localhost:3000
- API status: http://localhost:8080/api/v1/system/status
- API health: http://localhost:8080/actuator/health

Set `UPSTOX_ANALYTICS_TOKEN` to use India quotes and company fundamentals. Set `UPSTOX_STREAM_ENABLED=true` so resting limit and queued orders can react to live ticks.

## Implemented APIs

### Authentication and account settings

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`
- `PATCH /api/v1/auth/me`
- `PATCH /api/v1/auth/me/password`
- `POST /api/v1/auth/email-verification/confirm`
- `POST /api/v1/auth/email-verification/resend`
- `POST /api/v1/auth/password/forgot`
- `POST /api/v1/auth/password/reset`
- `GET /api/v1/auth/sessions`
- `DELETE /api/v1/auth/sessions/{sessionId}`
- `POST /api/v1/auth/logout-all`
- `GET /api/v1/auth/events`
- `POST /api/v1/auth/me/export`
- `DELETE /api/v1/auth/me`

### Instruments and market data

- `GET /api/v1/instruments/search?marketRegion=INDIA&q=Reliance`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}/quote`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}/candles`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}/insights`
- `GET /api/v1/market/status?exchange=NSE`
- `GET /api/v1/market/indices`
- `GET /api/v1/market/movers` — top gainers and losers restricted to the NIFTY 100 universe
- STOMP WebSocket endpoint: `/ws/market`
- Quote topic: `/topic/market/quotes`

### Watchlists

- `GET /api/v1/watchlists/default`
- `POST /api/v1/watchlists/default/items`
- `DELETE /api/v1/watchlists/default/items/{itemId}`

### Paper trading

- `POST /api/v1/orders`
- `GET /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `PUT /api/v1/orders/{id}`
- `DELETE /api/v1/orders/{id}`
- `GET /api/v1/holdings`
- `GET /api/v1/trades`
- `GET /api/v1/account/ledger`
- `GET /api/v1/trading/charges/estimate`
- `GET /api/v1/portfolio?marketRegion=INDIA`

Order submissions require an `Idempotency-Key` header. The India MVP supports NSE cash equities and ETFs, delivery, whole-share quantities, market and limit orders, DAY validity and long-only selling. Executions include an effective-dated simulated charge breakdown, and portfolio valuation incorporates charges into cost basis and realized returns.

The connected dashboard supports registration, sign-in, automatic access-token refresh, editable profile and password settings, separate India and US portfolios, region-aware benchmark cards and market movers, persistent watchlists, real-time STOMP quote updates with reconnect health, multi-market stock search, interactive historical charts, provider-aware company fundamentals, standalone stock research pages, order entry, cancellation, holdings, portfolio metrics and trade history. Its data panels load independently, so a temporarily unavailable analytics endpoint does not block authentication or the rest of the portfolio experience.

The Upstox India instrument catalogue synchronizes on weekdays at 07:30 Asia/Kolkata, before the 09:15 regular market open.

## Documentation

- [Product definition](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Feature roadmap implementation](docs/ROADMAP_IMPLEMENTATION.md)
- [Authentication and account lifecycle](docs/AUTHENTICATION.md)
- [Instruments and market data](docs/INSTRUMENTS.md)
- [Upstox market-data integration](docs/MARKET_DATA.md)
- [Market-data public-release permission gate](docs/MARKET_DATA_PERMISSION.md)
- [SEC EDGAR access and attribution review](docs/SEC_EDGAR_COMPLIANCE.md)
- [Public release sign-off](docs/RELEASE_CHECKLIST.md)
- [India paper-trading engine](docs/TRADING.md)
- [Simulated Indian charges](docs/CHARGES.md)
- [Testing and acceptance](docs/TESTING.md)
- [Private staging operations](deploy/staging/README.md)
- [Public production operations](deploy/production/README.md)
- [Production operations and incident response](docs/OPERATIONS.md)
- [Performance and recovery verification](docs/PERFORMANCE_RECOVERY.md)
- [Production deployment](docs/DEPLOYMENT.md)

## Current milestone

The release candidate includes public legal disclosures, enforceable versioned registration consent and a fail-closed market-data permission gate. The India learning journey includes persistent watchlists, real-time WebSocket updates, previous-close index moves, NIFTY 100 market movers, dedicated stock research pages, editable account settings, PostgreSQL-backed migration/concurrency coverage and an authenticated Chromium acceptance gate. Provider-neutral staging and public-production bundles add automatic HTTPS, WebSocket proxying, isolated PostgreSQL and Redis, encrypted off-host backup support, immutable-image deployment, protected release workflows and verified rollback. Production observability adds structured request-correlated logs, private Prometheus metrics, a Grafana dashboard, Resend incident alerts, independent uptime checks and a public status page.

## License

MIT
