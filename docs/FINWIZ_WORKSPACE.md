# Finwiz workspace

Finwiz uses the application theme, a conversation area and a recent-chat sidebar. Main bottom navigation is omitted on this route; Dashboard and Learn remain available. The Learn page owns the topic map and opens suggested questions without submitting them.

History is stored in this browser's localStorage, separated by signed-in account email, with a maximum of 30 threads and 50 answers per thread. Clear history requires a second explicit click. History does not sync between devices. The existing ask API and server-side tier rules remain unchanged; past turns are displayed locally, not automatically sent as model context.

## Markets and charts

Both the quick quote and stock detail chart offer 1D, 1W, 1M, 6M, 1Y, 3Y and 5Y. The corresponding intervals are one minute, fifteen minutes, daily (1M/6M/1Y), and weekly (3Y/5Y). 1D uses the latest available exchange session from a ten-day lookback, so weekends do not produce an empty chart. Requests use the exchange date, clamp month boundaries and discard superseded quick-quote responses.

Candles use validated provider OHLC and equal observation spacing. Closed sessions and missing observations are omitted, never synthesized; the chart states this explicitly and exposes actual timestamps through pointer/tap and keyboard inspection. Pattern detection and its checkbox are removed. Upstox requests that include today combine historical-through-yesterday and today's intraday endpoint. An intraday failure remains an explicit error. Weekly data stays provider-aggregated.

Stock detail Buy/Sell actions appear in the right column below Research note and retain exact exchange, symbol and side in the existing ticket. The BSE exchange option and availability reason are visible even when the server gate is disabled. Existing NSE-only legacy API defaults remain unchanged. The dedicated F&O card always appears in Indian Markets and opens a separate tab; its cookie refresh restores the new tab's session without an opener reference.

## Deployment configuration

UI delivery does not change server flags. These are independent of permission to edit this repository:

- `STOXSIM_BSE_EQUITIES_ENABLED=true` enables BSE cash discovery and eligible simulation after instrument sync and verified provider acceptance.
- `STOXSIM_INDEX_DERIVATIVES_ENABLED=true` enables the virtual F&O lifecycle after the real-provider checks in [VIRTUAL_DERIVATIVES.md](VIRTUAL_DERIVATIVES.md).

Both flags must be set in the actual deployment environment. A successful merge/build alone does not alter its environment. No configured Upstox credential or production connection was available in the implementation workspace, so automated provider scenarios are fixtures, not live acceptance. No paid service was added.

Official API reference: [Upstox intraday V3](https://upstox.com/developer/api-documentation/v3/get-intra-day-candle-data/).
