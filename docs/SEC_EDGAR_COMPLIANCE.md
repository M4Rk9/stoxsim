# SEC EDGAR access and attribution review

**Review date:** 2026-08-25  
**Owner:** StoxSim release operator  
**Status:** Technically reviewed; production configuration must be verified before release

StoxSim uses public SEC EDGAR submissions and extracted XBRL company facts to provide educational company fundamentals for United States instruments. It does not submit filings, access non-public filer data, or reproduce complete filing documents.

## Data accessed

The backend requests only the records needed for a user-selected instrument:

- `https://www.sec.gov/files/company_tickers.json` for ticker-to-CIK mapping;
- `https://data.sec.gov/submissions/CIK##########.json` for public company metadata;
- `https://data.sec.gov/api/xbrl/companyfacts/CIK##########.json` for public XBRL facts.

StoxSim derives ratios and financial-history summaries from these public facts. The UI identifies the provider as **SEC EDGAR**, labels financial statements as **SEC XBRL financial statements**, and does not imply that the SEC endorses StoxSim or validates its calculations.

## Fair-access controls

The SEC currently asks automated clients to identify themselves, download only what they need, and remain at or below 10 requests per second.

StoxSim applies the following controls:

- `SEC_USER_AGENT` identifies `StoxSim/1.0` and a monitored contact address;
- `SEC_MAX_REQUESTS_PER_SECOND` defaults to **8** and the application rejects values outside 1–10;
- one process-wide throttle covers both `sec.gov` and `data.sec.gov` requests;
- the ticker catalogue is cached for 24 hours;
- available stock insights are cached for six hours by default;
- partial responses are cached for 30 minutes and failed lookups for 10 minutes;
- requests are made only for an instrument selected by a user; StoxSim does not bulk-crawl filing archives.

Production must use:

```env
SEC_USER_AGENT=StoxSim/1.0 support.stoxsim@gmail.com
SEC_MAX_REQUESTS_PER_SECOND=8
```

## Attribution and limitations

The product must continue to:

- display **SEC EDGAR** as the source of US filing-derived fundamentals;
- describe calculated ratios and summaries as StoxSim-derived educational information;
- avoid SEC logos or language suggesting endorsement;
- preserve filing dates and units where they are displayed;
- avoid presenting calculated values as investment advice;
- keep the public risk disclaimer and provider attribution accessible.

## Release verification

Before each public release:

- [ ] Confirm the production user agent contains a monitored contact address.
- [ ] Confirm the configured request ceiling is between 1 and 10 requests/second.
- [ ] Open a US stock research page and confirm SEC EDGAR attribution is visible.
- [ ] Confirm repeated requests are served from the application cache.
- [ ] Review the current SEC fair-access guidance for material changes.
- [ ] Record the review date in the release evidence without storing user data.

## References

- [SEC Developer Resources](https://www.sec.gov/about/developer-resources)
- [Accessing EDGAR Data](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)
- [EDGAR APIs](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)
