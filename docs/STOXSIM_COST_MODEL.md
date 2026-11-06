# Operating cost model

The owner's directive reports domain coverage through September 2027 and the current OVH VPS prepaid through March 2027. PostgreSQL, Redis, monitoring and backups already exist; Gemini currently uses its free tier. These are owner-provided facts, not verified invoices. Current billed amounts, quota terms, storage prices and provider fee agreements were not supplied.

Target incremental early-stage spend: ₹3,000–₹5,000/month or less, excluding transaction fees, mandatory compliance and prepaid resources. This is a budget, not a proven cost forecast or capacity claim. No replacement VPS, paid AI provider, SaaS analytics or marketing platform is proposed.

## Workload scenarios (assumptions, not observations)

For comparison only: MAU = 20% of registrations, 8 active days/MAU/month, 2 optional AI requests/active day before server caps, one opted-in weekly digest to 25% of MAU, 3% of MAU subscribing. Peak concurrency cannot be inferred from registered users; measure it with the guarded capacity harness.

| Registrations | Assumed MAU | Active learner-days/month | Requested AI calls before caps | Opted-in digest emails/month (4 weeks) | Assumed paying subscribers |
|---:|---:|---:|---:|---:|---:|
| 1,000 | 200 | 1,600 | 3,200 | 200 | 6 |
| 5,000 | 1,000 | 8,000 | 16,000 | 1,000 | 30 |
| 10,000 | 2,000 | 16,000 | 32,000 | 2,000 | 60 |
| 50,000 | 10,000 | 80,000 | 160,000 | 10,000 | 300 |

Actual AI calls are bounded by per-user/global quotas and provider availability. Free-tier limits may make these workloads infeasible without fallback. Never interpret a zero provider invoice as unlimited available quota. Notifications must have bounded batches, retries, retention and per-recipient limits.

## Cost inputs and formulas

| Cost | Current evidence | Calculation / decision |
|---|---|---|
| VPS/domain | Reported prepaid; renewal amounts unknown | Allocate verified monthly equivalent separately from incremental cash expense |
| AI | Free-tier usage reported; token billing unknown | Observed input/output tokens × reviewed model-specific rates; unavailable rates mean unknown cost, not ₹0 |
| Email | Existing sender; pricing/quotas unknown | Actual delivered/attempted messages and provider bill; include bounce/complaint and retry traffic |
| Razorpay | Merchant agreement/tax details unverified | Captured gross × contracted processing rate + fixed fees/taxes, less refunds per actual settlement |
| Google Play | Applicable program/fee schedule unverified | Verified subscription transactions × applicable service fee; do not assume one universal percentage |
| Backups | Existing infrastructure; retention/GB bill unknown | Measured stored GB and transfer × confirmed storage rates |
| Monitoring/DB/Redis | Existing self-hosted services | Included in VPS consumption; measure CPU/RAM/disk before proposing upgrades |

Monthly operating cost per active learner = measured attributable monthly cost / observed MAU (undefined when MAU is zero). Cost per paying subscriber uses observed active verified paying subscribers and is undefined at zero. MRR uses verified recurring subscription amounts normalized to a month, not test payments, checkout starts, registered users or simulated portfolio capital. Cash received, MRR and gross revenue are different metrics.

## Budget controls

Default live billing and optional deliveries off until configured; preserve output/token budgets and deterministic AI fallback; cap per-user/global AI calls; bound background jobs/outbox retries; surface observed usage separately from unknown monetary costs. Alert on measured quotas/failed delivery/backlog and review growth weekly. Any new recurring expenditure requires an explicit cost proposal and owner authorization before purchase.
