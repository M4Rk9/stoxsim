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

## Run locally

```bash
cp .env.example .env
docker compose up --build
```

- Web application: http://localhost:3000
- API status: http://localhost:8080/api/v1/system/status
- API health: http://localhost:8080/actuator/health

Set `UPSTOX_ANALYTICS_TOKEN` to use India quotes and candles. Set `UPSTOX_STREAM_ENABLED=true` so resting limit and queued orders can react to live ticks.

## Implemented APIs

### Authentication

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

### Instruments and market data

- `GET /api/v1/instruments/search?marketRegion=INDIA&q=Reliance`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}/quote`
- `GET /api/v1/instruments/{marketRegion}/{exchange}/{symbol}/candles`
- `GET /api/v1/market/status?exchange=NSE`
- STOMP WebSocket endpoint: `/ws/market`
- Quote topic: `/topic/market/quotes`

### Paper trading

- `POST /api/v1/orders`
- `GET /api/v1/orders`
- `GET /api/v1/orders/{id}`
- `PUT /api/v1/orders/{id}`
- `DELETE /api/v1/orders/{id}`
- `GET /api/v1/holdings`
- `GET /api/v1/trades`
- `GET /api/v1/account/ledger`

Order submissions require an `Idempotency-Key` header. The India MVP supports NSE cash equities and ETFs, delivery, whole-share quantities, market and limit orders, DAY validity and long-only selling.

The Upstox India instrument catalogue synchronizes on weekdays at 07:30 Asia/Kolkata, before the 09:15 regular market open.

## Documentation

- [Product definition](docs/PRODUCT.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Authentication](docs/AUTHENTICATION.md)
- [Instruments and market data](docs/INSTRUMENTS.md)
- [Upstox market-data integration](docs/MARKET_DATA.md)
- [India paper-trading engine](docs/TRADING.md)

## Current milestone

The transactional India paper-order engine is implemented. Next: portfolio valuation, simulated Indian charges and the first connected trading dashboard.

## License

MIT
