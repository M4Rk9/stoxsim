# Milestone 2: research evidence and historical replay boundaries

## Review sequence

1. Research cohorts (PR #192): additive V134 signup categories, separate cohort endpoint, existing metric definitions unchanged.
2. Research evidence (stacked on #192): company news and research prompts, Finwiz source boundaries, honest Scenario Lab methodology. No portfolio/order migration; existing AI tiers and scenario credits stay enforced by their services.

## News: supported subset and activation

The authenticated company endpoint `/api/v1/instruments/{region}/{exchange}/{symbol}/news` resolves an active catalogue listing; users cannot supply a provider URL or query another provider account's holdings. Only equity/ETF listings are supported. News errors do not hide the chart, fundamentals or virtual order actions.

| Market | Integration | Activation dependency |
| --- | --- | --- |
| India | Upstox `/v2/news`, explicit instrument key, first page of 10 | Confirm this account's permission to publicly display headlines/links/times, then set `UPSTOX_NEWS_ENABLED=true` |
| US | Alpaca `/v1beta1/news`, exact catalogue symbol, content excluded, first page of 10 | Confirm news entitlement and headline-display rights, then set `ALPACA_NEWS_ENABLED=true` |

Both flags default false independently of public quote flags. Existing market-data gates and credentials must also permit access. No new paid service is purchased. Flags are wired into production, staging and development Compose. Disabling a flag restores the unavailable state after backend restart. A news permission does not authorize model ingestion: **articles are not sent to Finwiz**.

Return only headline, destination domain, HTTPS link and publication time; omit bodies, summaries, images and author guesses. No persistent news storage or backend cache; HTTP `no-store`. Requests are bounded to one page with 5-second connect/read timeouts. No polling, provider-account holdings calls, or publisher-page scraping. Display a selected, possibly delayed/incomplete seven-day window; this is not a comprehensive archive. Alpaca sorts the fetched page by update time; our selected items display in publication order. Empty valid results are distinct from malformed payloads, outages and disabled permissions. Unsafe links, missing/future/older publication dates and duplicates are omitted. Publisher destinations open with no referrer and no opener.

Official endpoint references checked 15 November 2026:
- https://upstox.com/developer/api-documentation/get-news/
- https://docs.alpaca.markets/us/reference/news-3

API availability does not establish this account's redistribution contract. Exact remaining decision: authorize headline display for each provider after verifying the applicable terms; leave the corresponding flag false otherwise. There is no requirement to block the rest of the milestone on this decision.

## Finwiz evidence

Company research now offers an explicit Finwiz link carrying only listing identifiers, never journal text. The user still reviews and submits the question. The existing tier/access/usage budget remains unchanged. Context includes provider, observation/retrieval time, status and a relative StoxSim source-page link. Current news and private journal notes are not injected.

Exclude simulated quote fallbacks. Request daily bars only through yesterday in the exchange timezone. Do not label unverified India adjustments as adjusted. Label extrema as highest/lowest **close**, not intraday high/low. Require 21 closes for a 20-session return, 20/50 for corresponding moving averages; shorter histories yield unavailable indicators. Flat-period RSI is neutral (50), not overbought (100). `dataAsOf` remains the latest timestamp for response compatibility; context explicitly prohibits treating it as freshness of all inputs. Fundamentals retrieval time is not a filing publication time. AI output still requires user verification; tests establish context behavior, not guaranteed model compliance.

## Historical investment replay: deliberately not enabled

Current Scenario Lab shocks the **current** virtual portfolio. It is not a date-based historical replay; the page now explains this boundary and offers a journal link. No historical-return result, activation toggle or credit charge is added.

Audit findings: US chart requests use `adjustment=all`; Indian historical candles lack a verified adjustment/completeness contract in the adapter; the catalogue is currently active listings. The current fundamentals/news services do not reconstruct the information set available on an arbitrary historical decision date. Reusing these as historical fills would risk hindsight, corporate-action and survivorship errors.

Required provider contract before implementation can honestly serve historical returns:

- Raw execution bars with exchange session/timezone, completed-session detection, pagination completeness, and point-in-time listing/symbol mapping (including delistings).
- Complete applicable split, dividend, merger and other corporate-action coverage over the holding period, including effective dates and cash/share adjustments. Unknown action coverage must reject a result, not assume none.
- Evidence publication/availability timestamps: only information available by decision time may enter the thesis context. Current ratios, revised filings and current news must not leak into past decisions.
- Explicit fill rule: decision at end of a completed session, earliest eligible fill in a later session; never use a same-session close chosen after seeing it. A weekend/holiday advances to a verified trading session.
- Explicit quantity/fractional-share rule, cash residual, currency, costs and slippage assumptions. User-entered assumptions must be labelled as such, not broker fees. No invented data for missing sessions or unsupported actions.

Acceptance cases for the future adapter/engine: split and cash dividend windows; delisting/symbol change; weekend start; future/incomplete bars; missing pagination; unavailable action coverage; publication after decision; cash insufficient after costs; deterministic results independent of later information. Existing synthetic Scenario Lab tests remain required and unchanged. These are future acceptance criteria, not a claim that a historical engine has been shipped or tested.

## Review evidence and intended outcome

Browser fixtures are explicitly synthetic: 390px and 1440px company views, sourced timestamps, HTTPS-only original links, disabled/empty news, Finwiz listing link, overflow and runtime-error checks. Mapper tests cover malformed, old/future/undated and unsafe articles; service tests ensure flags prevent provider calls and errors cannot expose diagnostics. Finwiz calculation tests cover insufficient history and flat-price edge cases.

Desired user outcome: a learner can identify the origin and age of evidence, challenge a thesis, and move into a saved strategy without confusing current data with past knowledge. Cohort measurement in #192 evaluates research → strategy → executed practice trade → written review. No retention improvement is claimed before observing mature post-release cohorts.

Android: these responsive web changes apply when opened in the web experience. This PR does not implement a separate native news screen or claim physical-device verification; native UI parity remains a follow-up. Existing native tier and order controls are unchanged.
