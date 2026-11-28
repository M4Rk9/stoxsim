# F5 billing, monitoring and Google Play release

The owner reported generating and finding the signed Android App Bundle. The repository candidate uses package `com.stoxsim.app`, version `0.6.0` (code 14), target API 36 and the production API. Record the exact bundle and installed build in the release evidence. The public association at `https://stoxsim.com/.well-known/assetlinks.json` needs to contain the **Play App Signing** SHA-256 fingerprint for the Play-delivered build. Compare it with **Play Console → App integrity** and verify domain handling on a Play-installed device.

## Verification by area

| Area | Repository evidence | Release evidence needed |
| --- | --- | --- |
| Razorpay | The existing checkout records the agreed price and validates captured invoices, webhook signatures, renewal, cancellation, expiry and refunds. `LiveBillingIntegrationTest` covers the live adapter with mocked responses. | Run [Razorpay Test Mode](https://razorpay.com/docs/webhooks/validate-test/) for a full subscription lifecycle, including failed payments and retries. Confirm merchant configuration, production plan IDs, 12-cycle disclosure and settlement evidence. |
| Google Play Billing | The backend verifies ownership and catalog, persists a pending purchase before acknowledgment, projects entitlements after acknowledgment and reconciles lifecycle events. | Configure Plus and Pro catalog and Play RTDN in the authorized environment. Record purchase, restore, renewal, expiry, cancellation and revocation with a Play-installed build. Use license testers in isolated acceptance. Production rejects purchases marked as license tests. |
| Cross-platform access | Both clients use the same server-side entitlement. The billing ledger serializes purchases by learner and rejects conflicting provider subscriptions. | Verify the same test account on both clients after purchase, restore, renewal and expiry. Confirm entitlement changes for Finwiz, Scenario Lab and sandbox accounts. |
| Finwiz monitoring | Grafana uses answer kinds, provider results, latency, failovers, context length, token estimates, reported usage and knowledge-cache hits. Separate paid cost graphs use reserved and reported amounts. | Confirm scrape freshness and controlled firing/resolved alert delivery. Review observed capacity for each tier before publishing allowances or enabling paid inference. |
| Billing and referrals | Aggregate gauges cover verified learner plans, billing state, observed Razorpay charges, referral outcomes and ledger mismatches. | Confirm the Grafana data and run F4 end to end with one approved dual-currency reward. Retain a zero-mismatch reconciliation result. |
| Resilience | Candidate CI exercises a fixed-latency mock provider and verified knowledge responses. Existing database suites cover billing callback retries, quota isolation and concurrent referral awards. | Run the [VPS capacity and restore checks](../M7_RELEASE_EVIDENCE.md) in an isolated stack with the production resource envelope. Review provider limits and sustained latency separately. |

## Load assumptions

The synthetic candidate uses 10% daily activity as a planning assumption, 2, 4 and 8 simultaneously active Finwiz sessions for the 1,000, 10,000 and 100,000 registration scenarios, and up to one request per session per second. Half the requests use an exact lesson and half use a local 150 ms Gemini response fixture. Treat the cache as cold at the start and warm on repeated lesson requests. Each profile requires at least 99% completed answers and records p50, p95 and p99 by route. An unavailability notice counts as an unsuccessful request. Registration figures label hypothetical user populations and do not establish a production capacity estimate. The existing authenticated baseline and resource sampler run in the same disposable stack.

The [F3 evaluation](F3_EVALUATION.md), `FinwizUsageBudgetIntegrationTest`, `LiveBillingIntegrationTest` and `ReferralIntegrationTest` record the automated fault and concurrency coverage. Measure sustained CPU, memory, connection-pool use, Redis health and provider quotas under the planned production envelope before claiming sustainable capacity.

## Security and privacy review

| Area | Controls reviewed | Remaining release verification |
| --- | --- | --- |
| Identity and access | Server-side authorization on protected routes and verified-admin controls. Provider callbacks authenticate transport separately. Billing access expires locally even if a provider is unavailable. | Exercise login, email and Google verification, password reset, session revocation and permission checks with controlled accounts. |
| Billing and virtual accounts | The backend verifies authoritative provider records, encrypts Play tokens and keeps reward practice accounts out of rankings. Refund handling and virtual corrections retain an audit history. | Confirm protected credential storage, encrypted backup retention, recovery of the Play token-encryption key and incident handling under [live subscriptions](../LIVE_SUBSCRIPTIONS.md). |
| Edge and dependencies | Existing CORS, CSP and API rate limits remain the release baseline. The candidate updates Next.js from 16.4.3 to 16.4.6, including the [November security fixes](https://nextjs.org/blog/november-2026-security-release). Metrics expose bounded aggregate labels with no purchase tokens, user identifiers or answer bodies. | Review current CodeQL, dependency and production verification results against the candidate. Verify metrics remain private and the backup drill succeeded. |
| Deletion and disclosure | Native and web deletion paths share the backend. The billing guard checks that renewal is stopped. Referral duplicate-identity holds have a documented 180-day expiry. | Exercise deletion with and without a subscription, then compare retention, SDKs and provider data flow to the privacy and Data safety declarations. |

## Android release evidence

| Check | Evidence to capture |
| --- | --- |
| Artifact | Commit, signed AAB SHA-256, package, version, target API, successful Play upload and installed test-track version. |
| Signing and links | Play signing certificate association, Google sign-in on the Play-installed app, verified email and reset links, invite routing and browser fallback. |
| Core flows | Account deletion, navigation and back, narrow screen and large font, error recovery, Finwiz lesson and generated answer, subscription or disabled purchase state, referral and reward history. |
| Listing | Review [listing copy and asset inventory](../../mobile/android/STORE_LISTING_DRAFT.md). Select screenshots of the actual signed build. Verify feature graphic, privacy and deletion URLs, support contact and reviewer access. |
| Play Console | Complete content rating, ads declaration, [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469) and [Financial features](https://support.google.com/googleplay/android-developer/answer/13849271) against actual behavior. StoxSim offers virtual trading practice and education. Check the Console's account-specific guidance for the applicable classification. |
| Testing and publication | Start with the appropriate test track and record feedback. If Play classifies the account as a new personal developer account, confirm the [closed-testing and production access requirement](https://support.google.com/googleplay/android-developer/answer/14151465). Record the final publication decision in the release ticket. |

Draft release notes for a build with the corresponding features available.

> Practise stock and ETF trading with virtual money, review your portfolio and explore market concepts with Finwiz AI, Scenario Lab and Learning Path.

The current non-payment listing draft describes a launch candidate with purchasing unavailable. Review any paid launch separately against [Play’s payments policy](https://support.google.com/googleplay/android-developer/answer/10281818) and the final feature settings. Production payment activation, referral activation and Play publication use their existing owner approvals.
