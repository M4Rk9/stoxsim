# Commercial development execution audit

Audit date: 30 October 2026. Baseline: `9c59184083e43dcab20959829f6765373375b566`, verified against `refs/heads/main`. This audit implements the owner's October master directive. Historical production evidence is attributed to its original document; it is not fresh production verification.

## Repository and dependency review

The application is a Java 21 / Spring Boot 4.1.1 modular monolith with PostgreSQL, Flyway and Redis; a Next.js 16.4.2 / React 19.3.1 web client; and a Kotlin/Compose Android app. Maven, npm lockfile, Gradle wrapper and existing CI remain the supported build paths. Migrations currently end at V126. Existing balances, account IDs, subscription plans and prices are preserved.

- PR #170 head `8d44f1065ec7061e04224f8fd8cbc14bc201e9f1` was fetched through Git and reviewed: its sole change is the Boot parent 4.1.1 → 4.1.2. The earlier [dependency review](dependabot-170-review.md) records historical green CI. Current check runs, reviews and open/closed state have not been reverified because `api.github.com` is blocked by the current runtime policy. Do not duplicate or merge this patch without those checks.
- PR #171 is already merged as `9cf9645`; #172 adds web/native Google registration at the baseline HEAD. Preserve these implementations. Do not implement a second OAuth flow.
- Current open PR discovery and PR creation require GitHub API access. A successful Git read proves neither API access nor check status.
- Base onboarding evidence: 359 backend tests passed, none skipped; web typecheck, production build and secret-isolation check passed. These are baseline results, not acceptance of new work.

## Capability evidence and gaps

Source state, automated testing and production verification are separate dimensions.

| Capability | Baseline source state | Evidence | Remaining work / acceptance |
|---|---|---|---|
| Password and Google authentication | IMPLEMENTED; baseline TESTED | `auth/google`, `AuthController`, Google integration tests, [Google sign-in](google-sign-in.md) | Current provider exchange, deployed web origins and signed Android credential audience need external acceptance; linking must remain explicit |
| Simulation and account isolation | IMPLEMENTED; baseline TESTED | Account/order/execution services, concurrency and routing tests | Regression suite after changes; no changes to balances, quotes, settlement or competition eligibility |
| Web navigation | PARTIALLY IMPLEMENTED for new directive | Existing trading Home, portfolio, progression, Finwiz, scenario, competition and settings pages | Five primary experiences with existing capabilities accessible; keyboard, mobile and dark-theme checks |
| Learner profile and adaptive Home | NOT IMPLEMENTED | Existing onboarding/guided journey can be reused | Optional three-field profile, deterministic recommendation and actual usage measurement |
| Subscription foundation | IMPLEMENTED; baseline TESTED | `subscription`, V107–V115, [subscriptions](SUBSCRIPTIONS.md) | Preserve Free/Plus/Pro and ₹99/₹199 draft monthly catalog; do not grant entitlements from clients |
| Live Razorpay and cross-provider lifecycle | NOT IMPLEMENTED | Existing admin-only test billing, [test billing](RAZORPAY_TEST_BILLING.md) | Separate authoritative live ledger, signatures, replay/order safety, renewal/grace/cancel/refund/revocation/expiry and reconciliation |
| Google Play Billing | NOT IMPLEMENTED | Native app has no purchase verifier/client at baseline | Current policy/library review, native purchase/restore, backend token verification/acknowledgment/RTDN; Console catalog and staging transactions external |
| Finwiz educational assistance | IMPLEMENTED; baseline TESTED | `finwiz`, [Finwiz](FINWIZ_AI.md), safety tests | Add daily/global call budgets and private-reflection consent path; retain deterministic calculations, fallback and tier limits |
| Decision reflections | NOT IMPLEMENTED | No reflection persistence or API | Bounded private entries, account/order ownership, export/deletion, explicit AI assistance only |
| Weekly learning reports | IMPLEMENTED; baseline TESTED | [weekly reports](WEEKLY_PORTFOLIO_REPORTS.md), report services | Preserve opt-in, verified-email checks, unique periods and three-attempt bound |
| Unified notifications | NOT IMPLEMENTED | Existing security events, achievements and reports provide source events | Inbox, preferences, deduplication, bounded optional delivery, suppression/unsubscribe and device ownership |
| Learning / global / private / campus competitions | IMPLEMENTED; baseline TESTED | [progression](LEARNING_PROGRESSION.md), [competitions](COMPETITIONS.md), [campus](CAMPUS_COMPETITIONS.md) | Improve discovery; preserve authoritative XP and separate standard-account rankings; no cash prizes |
| First-party owner analytics | IMPLEMENTED; baseline TESTED | `analytics`, [product analytics](PRODUCT_ANALYTICS.md) | Add genuine recommendation activity and verified billing/AI usage observations; preserve admin authorization, observation windows and incomplete cohort labels |
| Android essential parity | PARTIALLY IMPLEMENTED | Native trading/research/auth/portfolio/Finwiz/learning/competition/settings | Integrate new APIs, Play purchases and optional notifications; compile/unit tests plus external signed-device acceptance |
| Deployment, backups and monitoring | IMPLEMENTED tooling; external gates BLOCKED | [M7 evidence](M7_RELEASE_EVIDENCE.md), [runbook](M7_OPERATIONAL_RUNBOOK.md) | Real encrypted production-backup restore and VPS-equivalent capacity remain unverified; do not convert historical owner reports into new passes |
| Commercial permissions and launch | BLOCKED on external evidence | [market-data permissions](MARKET_DATA_PERMISSION.md), [release checklist](RELEASE_CHECKLIST.md) | Confirm subscription/commercial scope, benchmark retention, merchant KYC, tax/refunds, Play disclosures, sender DNS and final founder decisions |

## Security and operational boundaries

Existing code uses server-owned entitlements, transactional account/order locks, hashed refresh tokens, exact-origin CORS, bounded API inputs and administrator-only analytics. New public callbacks must be exact routes with provider verification; never broad billing exemptions. Test webhook forgery/replay, purchase ownership, refund/revocation and concurrent account operations. New personal data must participate in export and deletion without exposing tokens or provider secrets.

The latest directive authorizes refund/chargeback implementation and supersedes older cancellation-only development scope. It does not authorize live activation, real charges, bulk emails, production deployment or Play submission. External permission evidence for free educational display does not establish paid redistribution or benchmark retention rights.

See [roadmap](STOXSIM_EXECUTION_ROADMAP.md), [progress](STOXSIM_PROGRESS.md), [cost model](STOXSIM_COST_MODEL.md) and [release readiness](STOXSIM_RELEASE_READINESS.md) for the execution register.
