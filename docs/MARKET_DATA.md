# Upstox Market-Data Integration

## Scope

Upstox supplies read-only India market data. StoxSim owns all virtual cash, orders, fills, holdings and ledger entries. The application does not call Upstox order or portfolio APIs.

## Configuration

```env
UPSTOX_ANALYTICS_TOKEN=your-read-only-token
UPSTOX_STREAM_ENABLED=true
```

The token stays in the Spring Boot environment and must never be exposed through a `NEXT_PUBLIC_*` variable.

Streaming is disabled by default so tests and local development can start without opening an external connection. When enabled, the backend connects once and automatically retries interruptions.

## REST examples

```http
GET /api/v1/instruments/INDIA/NSE/RELIANCE/quote
Authorization: Bearer <access-token>
```

```http
GET /api/v1/instruments/INDIA/NSE/RELIANCE/candles?interval=FIFTEEN_MINUTES&from=2026-07-01&to=2026-07-22
Authorization: Bearer <access-token>
```

Quote responses always include `dataStatus` as `LIVE` or `STALE`, plus the provider and exchange timestamps.

## WebSocket

Connect to the STOMP endpoint:

```text
/ws/market
```

Subscribe to:

```text
/topic/market/quotes
```

The initial upstream subscriptions are NIFTY 50, NIFTY BANK, FINNIFTY, NIFTY IT, INDIA VIX and SENSEX. StoxSim keeps a reference count for dynamic instrument subscriptions so multiple browser users do not create multiple Upstox subscriptions.

## Cache policy

- Latest quotes: 30-second Redis TTL
- Quote stale threshold: 15 seconds
- Historical date-range responses: 5-minute Redis TTL
- Redis failures degrade to provider calls; they do not become authoritative trading state

Cash, orders, trades, holdings and ledger entries remain in PostgreSQL.

## Provider boundary

The SDK's generated REST classes use `io.swagger.client.api`, while response objects use `com.upstox.api`. Those packages are intentionally confined to the Upstox adapter. The rest of StoxSim depends only on provider-independent records and interfaces.
