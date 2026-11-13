# Research, practice and review update — 13 November 2026

Baseline: `3ec514d1de04628fe70603f547e2052e25eeba2c` (main, PR #187).
Tracking: https://github.com/M4Rk9/stoxsim/issues/190

## Evidence and limits

This is a repository/source audit, not a production operational attestation. Read the actual Next.js routes/components, Spring domain/API/persistence code, Compose Android screens, migrations, analytics queries, provider adapters and deployment workflows before editing. The supplied screenshots inform hierarchy, restrained accent use and readable chart density; none of their artwork or product identities is reused. The existing `stoxsim-logo.png` is retained.

Local production frontend build succeeds. Browser, database integration and Android verification status is recorded separately in VALIDATION.md. No production account, private research note, credential or live order was used. Production host state, provider entitlement, current flag overrides and real-device behavior are unverified in this workspace.

## Findings

| Area | Existing implementation | Gap / implementation implication |
| --- | --- | --- |
| Web shell and brand | Next.js App Router, custom CSS modules and global tokens; fixed navigation; multiple text-only and split-colour brands | Consolidate mark and neutral wordmark, semantic accent foreground, spacing/radius tokens. Existing CSS remains incrementally adoptable; this milestone does not claim every screen is restyled. |
| Landing/onboarding | Trading-first marketing and first-executed-trade onboarding | Broaden positioning; retain server-owned execution milestone. A client must not mark an order executed merely because it was submitted. |
| Home | Real account values, watchlist, movers, learning recommendations, guided features | Add a research/strategy/review hub and due-review entry. Never invent competition activity or chart series. |
| Search/security detail | Exchange-aware search, ETF support, NSE/BSE/US routing, keyboard-inspectable line/candle history, sourced company fundamentals | Link evidence to a private strategy; simplify chart date language and put provider/interval/timezone in details. News is not an existing complete licensed feed to be advertised as shipped. |
| Notes/Finwiz | Owned private reflections; account/order FK ownership; explicit per-note AI consent; tier and usage budgets | Extend notes with structured intention and date fields. Preserve old-client writes. No implicit AI submission. Current Finwiz assistant input remains the existing bounded note excerpts. |
| Practice/accounting | Server-owned order execution, idempotency and account locks; separate market currencies and STANDARD/SANDBOX scopes | Link orders after submission without changing the execution transaction. Display actual persisted status/price; a pending order is not a trade and an execution is not a closed-position return. |
| Scenario Lab | Current-portfolio synthetic uniform shocks with explicit assumptions; tier credits and request IDs | Historical dated investment analysis requires point-in-time series, corporate-action policy and costs. Do not relabel current shock output as a backtest. |
| F&O | NSE Nifty/Bank Nifty master, real contract identity/lot/expiry, fresh bid/ask/last checks, separate wallet, futures + long options, expiry retry | No option writing, multi-leg atomic baskets, Greeks adapter or broker margin model. Flat 20% futures margin, no fees/daily MTM/liquidation. Later PR must preview those exact rules, not imply broker equivalence. |
| Android | Native Kotlin/Compose, Keystore refresh, direct authenticated API, foreground activity, stock detail, watchlist/orders, Finwiz, scenarios, learning, private notes, account/email flows | Extend native editor and home journal access using the same API. Native F&O parity is not established. Physical-device acceptance remains separate from web viewport checks. |
| Analytics | Current USER/non-test filter, deletion cascade, daily first-event dedupe, 180-day retention, explicit coverage boundaries | Preserve existing first-trade conversion and seven-day activation SQL. Add server-only strategy/review markers with their own start timestamp; acquisition/device and ordered journey cohort views belong in M2. No note text in activity. |
| Providers/flags | Upstox India data and instrument master; Alpaca US; provider permission switches; BSE/F&O switches false locally, true by production Compose default | Flags are not proof of live coverage. Existing owner authorization is respected; no flag changes made. Verify actual entitlement and deployment overrides before release. |
| Deployment | Manual production workflow, immutable images, migrations, backup/smoke/rollback, isolated monitoring; deploy.sh prunes old dangling images | No merge/deploy triggered. Tagged-image/release retention is not solved by dangling-image pruning; dedicated operational work should retain active and rollback releases explicitly. |
| Privacy/access | JWT identity, DB ownership, exports and account deletion, opt-in AI sharing | New fields included in export; retained private table deletion cascade. No analytics payload contains thesis, symbol, horizon, review text or order ID. |

## Screen coverage

Source inventory: [SURFACES.md](SURFACES.md). Reviewed areas include marketing/SEO pages; login/register/Google/email/reset/unsubscribe/delete flows; home/onboarding/markets/search; security detail/research/chart/order controls; portfolio/analysis/history; Finwiz; Scenario Lab; derivatives; competitions/campus; Learn/progress; account/settings/billing/admin; status/legal; loading/empty/error state handlers. Source coverage is not visual or interactive acceptance of every route.

## Reviewable sequence

1. **M1 — foundation + complete first journey:** shared brand/tokens, clearer positioning and market-data labels, home hub, private strategy fields, review dates, explicit completion, owned order linking, web journal and native editor/home access. Additive migration; no financial logic changes.
2. **M2 — research depth and measurement:** licensed sourced news; point-in-time historical scenario design and tests; stable acquisition/device categories; eligible-cohort views for D1/D7, days 1–7 and following week; ordered journey completion. Existing metric definitions stay unchanged. Historical/provider limitations must be shown where data is absent.
3. **M3 — F&O practice:** beginner/advanced layouts, underlying/contract context, deliberate server-consistent preview, supported baskets/payoff, explicit unbounded exposure and settlement assumptions. Atomic basket execution needs a dedicated transaction/idempotency design; never sequential one-click fills masquerading as one strategy.

All PRs remain unmerged. M1 is a functional slice, not completion of the whole product update.

## F&O reference study

Groww's 915 groups positions, option-chain information, baskets and payoff context; StoxSim can apply that workflow principle while requiring educational risk review and its own presentation. No assets or layouts copied.

- https://915.groww.in/ (inspected 13 November 2026)
- https://tech.groww.in/building-915-inside-growws-high-performance-trading-terminal-d2f05c46a9c7
- https://upstox.com/developer/api-documentation/get-full-market-quote/ (inspected 13 November 2026; endpoint availability is not proof of this account's entitlement)
- Existing supported subset and exact simulation assumptions: [VIRTUAL_DERIVATIVES.md](../VIRTUAL_DERIVATIVES.md)

## Tool limitation

Superdesign preflight was stopped by automatic approval review after an unsolicited telemetry request to an untrusted PostHog endpoint with unknown payload. It was not retried or bypassed. Implementation uses repository components and supplied visual references; no Superdesign canvas result is claimed.
