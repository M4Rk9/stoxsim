# Scenario Lab

Scenario Lab opens at `/scenario-lab` in a new tab. It loads the session through the
existing refresh-cookie flow and selects the active plan's primary portfolio in
India or the US. It supports existing presets and named custom paths of 1–12
cumulative price moves from -100% to +100%, with up to two decimals. Each move is
relative to the initial price, not a compounded period return.

## Plans and credits

| Plan | India | US | FinWiz | Scenario credits |
| --- | --- | --- | --- | --- |
| Free | Existing ₹5 lakh account | Existing $10,000 account | Basic | 2 lifetime starter credits |
| Plus | ₹25 lakh | $50,000 | Expanded | 10 per paid renewal |
| Pro | ₹1 crore | $100,000 | Full | 50 per paid renewal |

Free credits are a one-time allowance, the implementation default pending any
subsequent product decision. Paid credits reset to the allowance, without rollover,
on a newly verified paid period. Duplicate events, failed renewals, and grace
extensions do not refill them. An upgrade in the same period increases the allowance
while retaining usage; a downgrade does not reset usage.

`GET /api/v1/scenarios` returns presets; `GET /api/v1/scenarios/credits` returns the
current effective plan, allowance, remaining balance and credited period boundary.
`POST /api/v1/accounts/{accountId}/scenarios` accepts:

```json
{
  "scenarioId": "custom",
  "version": 1,
  "title": "My recovery",
  "customShocks": [-20, 0, 25],
  "requestId": "bdb1b80b-7fd3-4bd9-a11c-8ca16e183f52"
}
```

The legacy `customShockPercent` input still supports a single custom move. Do not
combine it with `customShocks`. Presets accept neither custom input nor title.

The server verifies ownership, locks the subscription row, checks the request ID,
validates inputs, consumes a credit, values the portfolio, computes the result and
saves the request/result atomically. Failed runs roll back the debit. Concurrent
requests cannot overspend the allowance. A repeated request ID returns the original
result without a second charge; reusing it for different inputs/account returns 409.
The browser reuses an ID after a transport failure and generates a fresh one after
success or edited inputs. Legacy clients without request IDs consume one credit per
successful request.

Scenario runs do not mutate cash, holdings, pending orders, history or rankings.
Cash and quantities stay fixed. Sector-specific shocks, actual event replays,
probabilities, fees, FX and rebalancing are outside this model. Calculation details
are collapsed in the result. Named scenarios and results are retained for replay,
included in account export and removed on account deletion through cascading keys.

## Deployment

Flyway V117 adds credit usage fields and the owner-scoped `scenario_run` table.
Existing accounts receive their allowance without changing balances or holdings.
Existing paid US portfolios are provisioned once on account load, protected by the
subscription lock and existing unique region/plan/slot index. Competition accounts
remain separate. The current test-billing restrictions remain in force; this change
does not enable live billing.
