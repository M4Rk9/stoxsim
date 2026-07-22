# India Paper-Trading Engine

## MVP rules

- NSE cash equities and ETFs
- Delivery and long-only holdings
- Whole-share quantities
- Market and limit orders
- DAY validity
- No short selling, leverage, partial fills or real brokerage orders
- Configurable disadvantageous slippage, default 5 basis points
- Simulated charges are currently zero and will be added through a versioned charge engine

## Session phases

| IST phase | Order behavior |
|---|---|
| Weekend or holiday | Queue for the next trading day |
| Before 09:00 | Queue for the same trading day |
| 09:00–09:08 | Accept pre-open orders |
| 09:08–09:12 | Freeze placement, modification and cancellation |
| 09:12–09:15 | Continue the freeze during the buffer |
| 09:15–15:30 | Execute marketable orders |
| After 15:30 | Queue for the next trading day |

Pre-open orders are not matched as a reconstructed exchange auction. They become eligible against the first regular-session market data at or after 09:15.

## Order submission

```http
POST /api/v1/orders
Authorization: Bearer <access-token>
Idempotency-Key: 04eb8ec9-69bb-4ab1-a1b8-227a41686a7f
Content-Type: application/json

{
  "marketRegion": "INDIA",
  "exchange": "NSE",
  "symbol": "RELIANCE",
  "side": "BUY",
  "orderType": "LIMIT",
  "quantity": 10,
  "limitPrice": 1450.00
}
```

Submitting the same key again for the same account returns the original order.

## Resource blocking

A buy order moves its maximum required value from available cash to blocked cash. A sell order moves the requested quantity from available shares to blocked shares. Cancellation, rejection and expiry release those resources.

At execution, one transaction:

1. Locks the account, order and holding.
2. Revalidates the market phase and limit condition.
3. Settles blocked resources.
4. Updates or creates the holding.
5. Calculates realized profit/loss for sells.
6. Creates the trade and cash-ledger entry.
7. Marks the order executed.

## Live matching

Resting orders subscribe through the provider-independent market-data boundary. Open-order subscriptions are restored after application startup, and the Upstox adapter reference-counts identical instruments across users.

`UPSTOX_STREAM_ENABLED=true` is required for resting orders to react continuously to provider ticks.
