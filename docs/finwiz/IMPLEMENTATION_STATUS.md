# Finwiz reliability and monetisation programme
## F1 — provider gateway
Base main b8e9897. PR #206.
- Four adapters (Groq, Gemini, Cloudflare Workers AI, pinned OpenRouter free upstream), fixed preferred order, opt-in flags, one bounded attempt per provider, cooldown/Retry-After, concurrency semaphores and durable attempt limits. Existing server-selected depth and fallback contract retained.
- V137 request leases, successful allowance settlement and aggregate reported token counters. Earlier CI 46039840263, CodeQL 46039840474 and dependency review 46039840258 passed on 2eabf54.
- V138 durable rolling 60-second request and estimated-token admission plus daily token estimates. Conservative UTF-8-byte estimate, completion allowance and reasoning headroom; not actual provider tokenization, neuron accounting or billing guarantee.
- Full-body network timeouts with JDK async cancellation, bounded 256 KiB response body and shared dispatch deadline; DB and market-context latency are outside the network deadline. Mock slow-body failover regression.
- V139 optional user-scoped client request UUID; web and Android generate UUID once per submission; duplicates return 409 rather than storing private responses for replay. Existing clients without IDs retain compatibility. Retention approximately 24–48h; duplicate protection does not cover different UUIDs.
- Production preflight checks enabled providers independently, one probe each, requires at least one complete answer, no mandatory Gemini secret. New provider flags remain off by default. No real credentials, paid inference or deployment used.
- PR #206 merged at `b2e937f`. The owner reported a successful production deployment after transient and permission-related preflight failures. Individual live-provider health, account entitlements and upstream caps still require authorized verification. Rate caps are application admission estimates; actual provider-side limits and spending caps must be checked before activation. Per-process cooldown and concurrency are not distributed.
## F2 — pricing, capacity and cost controls

| Requirement | Status | Components | Verification |
| --- | --- | --- | --- |
| ₹0 / ₹199 / ₹499 monthly catalog and grandfathered agreements | Implemented in candidate | Subscription API, Razorpay live/test, Play catalog, Android, web, V140 | Pricing and purchase regression tests, including renewals at the historical Razorpay amount and preservation of Play base plans |
| Provider resilience and degraded rollout acknowledgment | Implemented in candidate | Gateway, recovery circuit, production preflight | Mock HTTP classification and outage handling. Production provider account checks pending |
| Independent Free, Plus and Pro capacity | Implemented in candidate | Admission lease, provider request/token pools, local concurrency, V141 | PostgreSQL concurrent admission and rate-limit tests. Sustainable production allowances remain unverified |
| Paid inference controls | Implemented in candidate, disabled by default | Separate optional Gemini project, spend ledger, alerts, admin report, V142 | Mock usage and transactional budget tests. Pricing and ceilings await owner approval |
| Tier-specific educational functionality | F2 context and output limits implemented | Backend entitlements and context selection | Versioned verified knowledge base and evaluation remain F3 |

F2 starts from `b2e937f`. [PR #207](https://github.com/M4Rk9/stoxsim/pull/207) carries the current test evidence. The owner confirmed successful production rollout. Account-specific provider capacity and live billing verification remain separate.

## F3 — Finwiz intelligence

The candidate adds the source-checked lesson catalog (V143), deterministic research routes, tiered public-only Redis caching, bounded follow-up context, financial output checks and web/Android answer metadata. [Evaluation](F3_EVALUATION.md) records coverage and remaining benchmark work.

## Later milestones (not started)
- F4: referrals, ₹100000 INR and $1000 USD virtual capital ledger.
- F5: monitoring, billing, Google Play candidate.
- F6: post-publication advertising only; each qualifying ad earns same virtual rewards. No ad SDK before launch.
