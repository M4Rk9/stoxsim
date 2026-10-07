# Simulated pricing and market availability

The product displays LIVE or CLOSED based on the exchange session, independently
of the market-data transport state. Market calendars, available funds, holdings,
price limits and account entitlements still constrain order execution.

`MarketDataService.latestQuote` first uses a valid fresh cache/provider quote. If
it cannot obtain one, it creates a quote with `simulated=true`:

- Hold the latest valid last price, with bid/ask equal to that price.
- If no valid price exists, choose a stable instrument-specific virtual starting
  price, rounded to its tick size. This is not an estimate of the real security.
- Retain the instrument's identity and currency, set a simulation timestamp, and
  cache the quote for consistent valuation, reservation and settlement.
- Retry the external provider on normal cache refresh. Do not compound artificial
  drift or publish an old exchange timestamp as a fresh exchange observation.

The quote API exposes `pricingSource: SIMULATED | EXTERNAL`; Redis stores the
provenance and can still read older 11-field entries. The public Risk Disclaimer
explains this behavior. LIVE is session availability, not a claim of real-time data.

Open orders are also checked every 15 seconds, so an external feed outage cannot
strand an otherwise executable order. Row locking and the existing open-order
check prevent duplicate fills when polling and provider ticks race. Nonmarketable
limits remain open and orders do not execute outside the regular session.

Fallback prices can differ materially from real markets, especially after a cold
start. Provider recovery can move marks. These effects are part of the simulator;
no real order or customer money is involved. Underlying timestamps and provenance
remain available through the API for diagnostics.
