# StoxSim Feature Roadmap Implementation Register

This register keeps implementation batches aligned with the StoxSim Feature
Implementation Roadmap. It describes product readiness, not market-data display
rights; provider-dependent work remains gated by `MARKET_DATA_PERMISSION.md`.

## Current progression

| Roadmap area | Status | Notes |
| --- | --- | --- |
| Core public-release platform | Implemented | Authentication, separate India/US portfolios, paper orders, holdings, watchlists, market status, FinWiz, deployment, recovery and observability exist. |
| Guided onboarding | Implemented | Persisted introduction state and the first-trade walkthrough were deployed in PR #91. |
| StoxScore and portfolio risk | In progress | Current batch adds the versioned StoxScore v1 structure model, concentration analytics and dashboard explanation. |
| FinWiz portfolio feedback | Missing | Must remain educational: diversification, risk, attribution and learning guidance only. |
| Portfolio analytics | Partial | Core valuation and P/L exist; allocation, concentration, benchmark and risk views remain. |
| Weekly portfolio reports | Missing | Requires a report snapshot model and delivery preference; email infrastructure already exists. |
| Challenges, missions, XP and achievements | Missing | Must use auditable backend events and anti-abuse rules. |
| Improved leaderboards | Missing | Standard ₹5 lakh competition must remain isolated from paid sandboxes. |
| Private leagues and campus competitions | Missing | Requires league ownership, membership, seasons and moderation controls. |
| Plus and Pro architecture | Missing | Entitlements, billing-provider boundary and separate sandbox portfolios are required before charging users. |
| Scenario Lab and advanced history | Missing | Pro roadmap item; historical-data licensing and retention must be verified first. |

## Batch 1 — guided onboarding and first trade

- Three-step educational introduction for first-time learners.
- Persisted introduction and dismissal state.
- Two-step dashboard coach from instrument search to the paper order ticket.
- Backend-authoritative first-order milestone in the order transaction.
- Existing learners with prior orders are backfilled as having placed a first order.
- No new secret, provider, paid service or market-data redistribution.

## Boundaries carried forward

- StoxSim remains an educational market simulator, not an advisory service.
- FinWiz must not provide personalized buy/sell instructions, return guarantees or
  misleading price predictions.
- Free competitive portfolios remain fixed at ₹5 lakh.
- Plus ₹25 lakh and Pro ₹1 crore capital belong in isolated sandbox portfolios and
  cannot affect the standard leaderboard.
- Market-data-dependent features stay behind provider-neutral boundaries until
  display and redistribution permission is documented.

## Batch 2 — StoxScore and portfolio structure

- Versioned and documented 0–100 StoxScore formula.
- Breadth, weight-balance and largest-position concentration components.
- Effective holdings, concentration index, largest-position and top-three
  weights.
- Explicit pricing coverage, confidence and limited-data behavior.
- Authenticated market-scoped API and responsive dashboard card.
- Educational disclaimer and no predictive or buy/sell language.
- No new secret, provider, paid service or market-data redistribution.

## Next batch

Add backend-authoritative FinWiz feedback after an accepted paper trade. The
feedback will explain the trade's effect on diversification and concentration,
use the versioned StoxScore components, and remain educational rather than
providing personalized buy/sell instructions.
