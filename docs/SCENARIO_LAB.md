# M6 — Scenario Lab

Scenario Lab lives on the Portfolio page, below portfolio history. It provides
versioned **synthetic** educational scenarios against the selected owned account.
Active Pro benefits are required for every run; the authenticated catalog is
visible to all plans. This milestone does not activate billing or change plans.

## Inputs and reproducibility

Version 1 contains:

| ID | Cumulative price shocks | Lesson |
| --- | --- | --- |
| `selloff` | -10%, -20%, -30% | Cash cushions a uniform equity decline |
| `recovery` | -20%, -35%, -15%, 0%, +10% | Ending return can hide a deep intervening drawdown |
| `whipsaw` | +15%, +30%, +5%, -10% | Drawdown is measured from the running peak |
| `custom` | One user-selected shock, -100% through +100% | Explore sensitivity to a bounded uniform move |

Every shock is cumulative relative to the **same starting mark**. These are not
sequential returns to compound. Steps have no dates, duration, annualization,
probabilities, historical-event labels, or forecast interpretation. Custom shocks
allow at most two decimal places. Unknown IDs, versions and incompatible fields
are rejected. Existing definition versions must not silently change; future model
changes require a new version and explicit handling of old requests.

Historical replay is not part of this synthetic catalog. M5 observations remain
available in Portfolio history. We do not invent a historical holdings series or
present a synthetic crash as an actual market event. Historical scenarios would
need a separately defined replay contract and suitably licensed historical inputs.

A run returns its ID, version, full shock sequence, currency, starting equity,
fixed cash, starting holding quantities and prices, quote timestamps, step values,
final holding effects and assumptions. The same valuation inputs and definition
produce the same result; rerunning later may use different current marks. Results
are not persisted. No extra price-retention permission or benchmark switch is
needed for these transient calculations over the existing valuation service.

## Model

Let C be available plus blocked cash, H the sum of current holding market values,
and s the cumulative percentage shock for a step:

- Starting equity = C + H.
- Hypothetical equity = C + H × (1 + s / 100).
- Change = hypothetical equity − starting equity.
- Ending return = ending change / starting equity × 100; unavailable at zero equity.
- Maximum path drawdown = maximum (running peak − step equity) / running peak × 100,
  including the starting value in the running peak. An all-zero path has zero drawdown.

Calculations use decimal arithmetic and round monetary values to four places,
HALF_UP. Return and drawdown are returned to four places; the UI displays two.
Independently rounded holding impacts can differ slightly from the aggregate.
The engine rejects negative cash/exposure and out-of-range shocks.

Every holding receives the same shock, including quantities currently blocked by
orders. Available and blocked cash remain fixed. Pending orders do not execute;
no quantities, balances, order states, portfolio history, ledger entries or
competition records are changed. There is no rebalancing, currency conversion,
dividend, tax, fee, slippage, corporate-action or stock-specific beta model.
This illustrates uniform price sensitivity, not a full multivariate risk model.
India and US accounts, standard accounts and sandboxes remain separate.

## Quality, authorization and isolation

- Account ownership is checked before valuation; another user's account returns 404.
- Active effective `SCENARIO_LAB` entitlement is checked on every run, including
  expired/revoked test benefits. Free/Plus/past-due requests return 403.
- Stale, unavailable, missing-timestamp, future-timestamp and mixed-currency
  holding prices return 409. Cost-basis fallbacks are never scenario marks.
- LIVE and CLOSED quotes are accepted using the existing market-status contract.
  CLOSED marks may be last-session prices; exact quote times are returned/displayed.
  They are not represented as live forecasts or official closing NAVs.
- Cash-only portfolios work even when no market quote is available.
- The service uses a read-only repeatable-read transaction so account cash and
  holdings are read from a consistent database snapshot while settlements continue.
  Market quotes retain their individual timestamps, rather than claiming simultaneity.
- Responses use `Cache-Control: no-store`; authentication and existing API rate
  limiting apply. No new tables, migrations, schedulers or provider integrations.
- Frontend requests are ignored after account changes/unmounting. Controls are
  disabled during a run; failures clear results and leave the main portfolio usable.

## Acceptance

1. Deploy the reviewed image normally; no environment changes or migrations needed.
2. Open Portfolio and scroll to Scenario Lab. Choose the intended account first.
3. With effective Pro access, run Market sell-off and inspect the step table,
   holding effects, timestamps and assumptions. A 20% cash / 80% equity allocation
   under a -30% price shock loses 24% overall, not 30%.
4. Run Fall and partial recovery; confirm drawdown remains visible despite a
   positive ending result. Test Custom price shock at -100%, 0% and +100%.
5. A cash-only portfolio remains flat. Unpriced holdings produce an explicit error.
6. Confirm balances, holdings, orders, history and competition standings remain
   unchanged. Free/Plus should see the catalog but receive the Pro requirement.
7. Switch account/market and run again; verify currency and account-specific values.

Automated coverage includes exact numerical fixtures, cash/zero edge cases,
reproducibility, HTTP ownership/entitlements, version/input rejection, price quality,
nonmutation checks, and browser result/custom-input/error flows. CI results and the
reviewed candidate SHA are recorded on the draft PR; production acceptance is a
separate owner step.
