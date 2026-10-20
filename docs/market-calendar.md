# Market calendar and price provenance

## Calendar coverage and maintenance

`backend/src/main/resources/market-calendar.properties` is the reviewed, versioned
calendar. Coverage is **1 January–31 December 2026** for India and US cash equities.
It is deliberately not an annual recurrence rule: exchange amendments, exceptional
closures and weekend trading sessions can differ from a public-holiday calendar.

Sources reviewed on 20 October 2026:

- [NSE 2026 capital-market holidays, CMTR71775](https://nsearchives.nseindia.com/content/circulars/CMTR71775.pdf).
- [NSE 15 January election closure, CMTR72260](https://nsearchives.nseindia.com/content/circulars/CMTR72260.pdf).
- [NSE Sunday 1 February Budget session, CMTR72349](https://nsearchives.nseindia.com/content/circulars/CMTR72349.pdf).
- [NSE 8 November Muhurat schedule, CMTR76941](https://nsearchives.nseindia.com/content/circulars/CMTR76941.pdf).
- [BSE 2026 cash-equity holiday notice 20251212-8](https://www.bseindia.com/markets/MarketInfo/DispNewNoticesCirculars.aspx?page=20251212-8).
- [NYSE holidays and early closes](https://www.nyse.com/trade/hours-calendars).
- [Nasdaq 2026 trading calendar](https://www.nasdaqtrader.com/Trader.aspx?id=Calendar).

India instruments use Asia/Kolkata; US instruments use America/New_York, including
DST. NSE/BSE share the India cash-session model; NASDAQ, NYSE, NYSE_ARCA, AMEX and
CBOE use the US core-session model. No premarket or extended-hours fills are
supported. Orders entered after the core close are queued for the next session.

The Muhurat circular distinguishes CAS-eligible securities from other securities.
StoxSim has no CAS eligibility feed or auction matcher. **On 8 November, continuous
matching stops at 15:15 IST for every instrument**, conservatively excluding the
15:15–15:30 window even for non-CAS securities. The UI/API reports CLOSING_AUCTION;
new/modified orders are frozen, existing orders remain cancellable, and DAY orders
expire at 15:30. Auction execution and security-specific CAS participation are
outside this patch; do not advertise this simulator as an auction implementation.

Resource values are `CLOSED`, `UNKNOWN`, or pipe-separated local times:
`open|close[|extendedClose|preOpen|matching|buffer|continuousClose]`.
An explicit hours entry takes precedence over a weekend. A `market_holiday` DB row
is an emergency closure override and takes precedence over bundled open sessions.
Migration V123 permits these overrides for all supported US exchanges as well.
Never insert a holiday to represent an early close; change that date's hours.

Before calendar coverage ends, review the next year's **trading** calendars and
all amendments, add dates/hours, extend coverage, run calendar/order/history tests
and deploy. Also review new exchange circulars for in-year changes. An unsupported
year or `UNKNOWN` date produces CALENDAR_UNAVAILABLE with null nextTransition and
orderDate. Entry/modification and matching fail closed; cancellation is still
available. The next-session search stops at an unknown date instead of silently
skipping a possible special session. No external calendar feed or runtime network
request is needed for order placement.

The expiry job runs every minute, including weekends. The exchange-local date and
closing time are rechecked under the order lock. Matching requires the order's
session date to equal today's session date, preventing old DAY orders from filling
before the expiry job catches up.

## Fallback prices and historical observations

Quote, websocket, watchlist and holding responses distinguish `EXTERNAL` from
`SIMULATED`; holdings may also use `COST_BASIS` when valuation fails. Market-open
status is separate from source. Web and Android show source notices next to quotes,
order tickets and portfolio values. Missing source metadata is unconfirmed, never
implicitly external. Real ticks replace the previous source metadata.

Generated fallback prices retain the original exchange timestamp when available;
a seed price has no exchange timestamp, OHLC or volume. Generating a fallback does
not create evidence of a new exchange observation. Index and mover snapshots reject
fallback quotes. Paper trading can still use fallback prices with visible notices.

Daily portfolio observations require external prices, matching currency and
same-session, non-future exchange timestamps for every holding. Synthetic SPY
benchmarks are rejected; NIFTY's index service also rejects synthetic quotes. A
cash-only portfolio remains observable without a stock quote. Missing/unconfirmed
prices create MISSING_PRICES gaps, without fabricated equity or normalized returns.

**Migration V123 quarantines existing OBSERVED rows as LEGACY_UNVERIFIED**, because
those rows did not retain enough evidence to identify their price sources. It
preserves their raw equity and benchmark values in the database for audit. The
history API does not expose them as observed chart values or calculate returns
across them. Historical charts/metrics rebuild from new verified observations;
there is no invented historical backfill. Calendar-unknown gaps and observations
on non-trading days cannot produce continuous performance series.

## Financial units

Both web financial views format the financial API's `unitsIn`, without rescaling
values. SEC values in USD million appear as `$…M`; INR crore (including Upstox's
bare `crore`) appears as `₹… Cr`. Other declared units remain explicit, and missing
units are not guessed from the currently selected account. The standalone stock
page also displays the returned statement description instead of claiming US
financial history is unavailable.
