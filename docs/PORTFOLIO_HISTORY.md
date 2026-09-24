# M5 portfolio history and observed risk

Portfolio → Portfolio history records each standard or sandbox account separately.
Values are simulated, in that account's own INR or USD currency. No FX conversion,
account aggregation, reconstruction of past holdings, or synthetic backfill occurs.

## Collection and data quality

- Migration V116 creates an account-owned table with deletion cascading through the
  account/user. Account export includes dated equity and quality, not benchmark prices.
- A dedicated scheduler observes at 23:40 UTC, after India and US regular sessions,
  on each market's trading days using the existing holiday calendar. These are
  nightly observed marks, not certified exchange closing NAVs; US quotes can include
  extended-hours activity. The UTC date identifies that day's trading session,
  including India where the collection occurs after local midnight.
- Each account is locked while cash, holdings and cumulative trade cash are read,
  matching settlement's account lock. Available plus blocked cash both count.
  Repeated capture cannot rewrite a day's observation. Accounts are paged by UUID;
  errors are isolated by account. Capture outside the 23:00 UTC hour is refused.
- A holding needs a usable LIVE/CLOSED quote dated to that market's observed session,
  no future timestamp, and matching currency. Stale, unavailable or older prices
  produce a missing equity mark. Average-cost fallbacks are never treated as returns.
  Cash-only portfolios can be valued without market prices.
- An offline collector produces a real gap, never a flat invented return. There is
  no historical revaluation endpoint. The UI shows the most recent observation date;
  metric coverage ends there rather than claiming performance through today.
- Nightly retention removes records older than the last 365 calendar dates, even
  when new observation collection is disabled.

## Method and entitlements

Free receives 30 days of dated equity. Active Plus adds 90/365-day windows, normalized
portfolio/benchmark comparisons, observed window return and maximum drawdown. Active
Pro adds annualized sample volatility, beta and correlation. Backend ownership and
effective entitlement checks apply to every request, including expired test benefits.
Locked sandboxes retain their basic history; they cannot trade. Features do not alter
standard starting capital, scores, rankings or holdings.

For adjacent observed sessions, return is `equity[t] / equity[t-1] - 1`. Net external
cash adjustment is the change in total cash minus the change in cumulative signed
trade-ledger cash. Buying/selling is not an external flow. A non-zero adjustment or
changed starting capital breaks performance calculations: exact flow timing is not
available, so the implementation does not fabricate time-weighted returns. Net-zero
offsetting external adjustments cannot be detected with this method; there is no
customer cash-deposit/withdrawal feature today. Add an explicit flow ledger before
introducing that feature. Do not make unrecorded production balance edits.

Window return uses first/last equity; drawdown is the largest percentage drop from
an earlier peak. Missing expected trading sessions, invalid marks, or cash adjustments
withhold normalized lines and window/risk metrics. No interpolation is performed.
Risk requires at least 20 aligned session returns, computes sample variance using
`n-1`, and annualizes volatility with `sqrt(252)`. Beta is sample covariance divided
by benchmark variance; correlation divides covariance by both sample deviations.
Constant benchmarks yield no beta/correlation, not zero. These estimates use observed
marks and a 252-session assumption, are limited by sample length, and are not forecasts.

India compares to the NIFTY 50 price index; US compares to SPY price (an ETF proxy,
not a total-return S&P 500 index). Both are normalized to 100 at the same first
observation. Dividends, fund distributions and FX are not added. The entire selected
series must have aligned benchmark observations; otherwise benchmark return, beta
and correlation remain unavailable while valid portfolio volatility can still appear.

## Configuration and permissions

`STOXSIM_PORTFOLIO_HISTORY_ENABLED=true` enables derived account collection by default.
Existing provider-serving controls still apply. The approval register in
`MARKET_DATA_PERMISSION.md` covers educational analytics and derived portfolio values.

`STOXSIM_PORTFOLIO_HISTORY_BENCHMARK_RETENTION_ENABLED=false` is the default. The existing
register does not specify 365-day benchmark price retention. Review the privately held
Upstox/Alpaca permission terms for that storage and authenticated display before setting
this flag to true in the deployed Compose `.env` and recreating the backend. This flag
neither supplies credentials nor bypasses provider-serving controls. When false, no
new benchmark marks are collected and existing benchmark comparisons are withheld.
No new historical-data API, third-party feed, redistribution endpoint or bulk download
is introduced. Missing benchmark coverage stays visibly unavailable.

## Deployment acceptance

1. Deploy normally; existing portfolios display a collecting state until observation.
2. After a trading-session collection, verify the selected account's currency, date
   and equity. Switch standard India/US and sandbox accounts and verify isolation.
3. Free users get dated values only. Use opted-in admin Plus/Pro test benefits to
   validate longer windows and controls. Beta/volatility require 20 returns; do not
   seed production history to bypass that requirement.
4. Check missing-price and missing-benchmark states. Do not enable retention solely
   to dismiss an unavailable label; confirm the permission scope first.
5. Compare captured cash/holdings to that observation's actual account state, not
   a later live quote. Verify exports and deletion with a disposable account.

Automated PostgreSQL tests cover ownership, entitlement expiry, capture idempotency,
deletion, cash-flow breaks, trade cash, stale prices and deterministic metrics. Browser
tests cover collecting, populated and failed history without breaking live portfolios.
