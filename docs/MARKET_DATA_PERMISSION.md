# Market-data public-release permission gate

**Release status: BLOCKED until provider permissions are evidenced.**

Running provider SDKs or holding API credentials does not by itself grant StoxSim the right to display, cache, derive from, or redistribute market data to public users. Do not make production registration public until every required row below is approved.

## Approval register

| Source | StoxSim use | Required evidence | Status |
|---|---|---|---|
| Upstox / Upstox Analytics | India quotes, candles, indices, movers, instrument catalogue and company fundamentals | Written approval or contract/plan language covering display to StoxSim end users, caching, derived movers/portfolio values, attribution and the intended public-beta user count | **Pending** |
| Alpaca Market Data | United States quotes, bars, benchmarks, movers and derived portfolio values | Written approval or plan terms covering end-user display and redistribution for the selected feed, including any SIP/IEX limitations, caching and attribution | **Pending** |
| SEC EDGAR | United States filing-derived fundamentals | Recorded review of SEC fair-access guidance, request identification, rate limits, source attribution and treatment of third-party material within filings | **Review pending** |

Useful provider references:

- [Alpaca Market Data FAQ](https://docs.alpaca.markets/docs/market-data-faq)
- [Alpaca market-data plans](https://alpaca.markets/data)
- [SEC developer resources](https://www.sec.gov/about/developer-resources)

Provider terms can change. The approval evidence, not this checklist, controls whether a source is releasable.

## Questions each approval must answer

Ask the provider to confirm all of the following in writing:

- May StoxSim show the data to authenticated public-beta users?
- Which exchanges, feeds, fields and delay classes are permitted?
- May StoxSim cache quotes, candles, fundamentals and instrument metadata, and for how long?
- May it calculate and display derived values such as movers, returns, P/L and portfolio value?
- Are screenshots, demos, support diagnostics or downloadable account exports allowed to contain data?
- What attribution, logos, timestamps and delayed-data labels are required?
- Are there user-count, geography, non-commercial, educational or pricing limits?
- Are WebSocket delivery and server-side fan-out to users permitted?
- What deletion, audit, incident-reporting or access-control obligations apply?
- Does public launch require a different contract or data vendor agreement?

## Evidence handling

For each source, keep the following outside the public repository:

1. Provider contact name and official email address.
2. Dated email, ticket, signed agreement or exact terms/plan snapshot.
3. Approved StoxSim use case, feed and audience.
4. Required attribution and technical controls.
5. Renewal or re-review date.
6. Named owner who confirmed the production configuration matches the approval.

Record only the evidence reference and approval date in the release ticket. Never commit provider credentials, private contracts or personal contact details.

## Release decision

Before public launch, the release owner must check:

- [ ] Upstox approval evidence is complete and the production plan matches it.
- [ ] Alpaca approval evidence is complete and the production plan matches it.
- [ ] SEC fair-access and attribution review is recorded.
- [ ] Required attribution appears in the product.
- [ ] Cache retention and WebSocket fan-out match the approved rights.
- [ ] A fallback exists to disable a source without breaking authentication or account access.
- [ ] Legal counsel or the accountable operator has reviewed the public Terms, Privacy Notice, Cookie Notice and Risk Disclaimer.
- [ ] `support.stoxsim@gmail.com` receives inbound mail; switch the public contact to domain aliases when they are configured.
- [ ] The legal operator name and contact details are confirmed before the draft PR is made ready.

If any required item is unchecked, keep the release private. A provider outage is an operational issue; missing redistribution permission is a release blocker.
