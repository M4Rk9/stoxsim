# Portfolio allocation and performance attribution

StoxSim exposes authenticated, market-scoped portfolio insights at
`GET /api/v1/portfolio/insights?marketRegion=INDIA`. The response is versioned
as `portfolio-insights-v1` so its accounting meaning can evolve without
silently changing historical explanations.

## Allocation

Allocation is calculated from the same server-valued portfolio returned by
`GET /api/v1/portfolio`:

- cash is available cash plus cash blocked by an open paper order;
- invested allocation uses current holding market value;
- each holding includes its share of invested value and total account value;
- India and United States accounts are calculated independently;
- unavailable quotes use the existing cost-basis valuation fallback and lower
  reported confidence.

No sector or benchmark classification is inferred because the current market
data contracts do not guarantee licensed classification or index-constituent
data for display.

## Performance attribution

Realized contribution is reconstructed chronologically from the complete
executed-trade ledger. Buy-side simulated charges enter the position cost pool;
sell-side charges reduce proceeds. The moving-average cost calculation mirrors
the settlement engine.

Unrealized contribution is the current market value minus the fee-adjusted cost
basis already stored on each holding. A fully sold position remains visible in
realized attribution even though it is absent from current allocation.

Per-symbol total contribution is:

```text
realized contribution + unrealized contribution
```

Account impact is that contribution divided by starting simulated capital. The
API reconciles attribution with the account-level realized P/L source of truth;
any older activity that cannot be replayed is identified as `UNATTRIBUTED`
rather than silently assigned to a stock.

## Product boundary

These analytics explain simulated allocation and past paper-trading results.
They do not forecast performance, recommend a security, or provide investment
advice. The endpoint makes no additional provider request and introduces no
new data-retention or redistribution requirement.
