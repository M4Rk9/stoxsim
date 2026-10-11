# Android stock-detail fixes — 0.4.1

Watchlist additions previously sent `instrumentId`, but `AddWatchlistItemRequest`
requires `marketRegion`, `exchange` and `symbol`. Android now sends the same fields
as the website. Search, mover and recent-stock entries can be added without a local
ID; BSE and US ETF exchanges are preserved. The existing authenticated mutation
client remains responsible for refresh and does not automatically replay writes.
The returned watchlist enables the existing Watching state and watchlist screen.

Fundamentals mirror `frontend/app/page.tsx`: sector/valuation metric cards, optional
sector comparisons, status and source timestamp, and a grouped revenue/net-profit
chart. Quarterly and Yearly request their own reporting period. Five recent reported
periods are shown oldest to newest; tapping a period or bar group reveals exact values.
Crore and USD-million units remain explicit. Missing observations have no bar and an
em dash readout, zero observations have a baseline mark, and losses extend below zero.
Financial data declared for another period is not relabelled as the chosen period.
Additional provider metrics and history remain available through financial details.

The company description is a whitespace-normalised excerpt of at most 180 characters
under About the company, with Read more/Show less preserving the full supplied text.
Large-font metric grids use one column. No company claims or financial numbers are
invented by this presentation change.

Tests exercise a searched BSE stock, a US ETF, actual HTTP watchlist bodies, reporting
period query parameters, date ordering/joins, missing/zero/loss values, short descriptions,
and native card/chart interactions. Device screenshots use synthetic data only.

Owner-device check after installing 0.4.1:

1. Search a BSE/NSE stock, Add to watchlist, observe Watching, then open Watchlist.
2. Switch to US and add an ETF; confirm its market and exchange are retained.
3. Inspect valuation boxes and select Quarterly/Yearly. Compare the date, units and
   selected period values with the website; do not interpret missing values as zero.
4. Tap historical bars/periods, open financial details, and expand/collapse About.
5. Check light/dark appearance, a small screen and larger text.
