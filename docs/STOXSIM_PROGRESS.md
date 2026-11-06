# Execution progress

Baseline: `9c59184` on `main`. Updated 6 November 2026. Implementation is in `integration/stoxsim-commercial`, with a focused review stack prepared locally. No feature PR, merge, deployment, live charge, bulk message or Play publication is claimed.

| Milestone | Implemented | Validation / remaining work |
|---|---|---|
| M0 audit and navigation | Capability/release/cost registers; five-area web and native navigation | Audit branch pushed earlier; final focused feature PRs and current GitHub CI still pending |
| M1 learner profile and Home | Optional experience/objective/market profile, deterministic recommendations and real usage events | PostgreSQL ownership/profile tests and browser profile tests pass; complete production browser regression passed |
| M2 subscriptions | Authoritative Razorpay/Play adapters, encrypted tokens, lifecycle ledger, local access deadlines, cancellation, account deletion guard, web checkout/native restore | Mocked-provider integration and transport tests pass, including historical refunds, grace and Play restart; real provider staging and signed Play acceptance remain required |
| M3 reflection and bounded AI | Private reflection CRUD, explicit per-request assistance consent, per-user/global quotas, usage reporting | Reflection isolation, consent, quotas, fallback and PostgreSQL tests pass; no live AI usage/cost claim |
| M4 notifications | Inbox/preferences, unsubscribe/suppression, bounded SMTP outbox, committed source/billing events, token registration/export cleanup | 18 notification tests passed, including opt-out concurrency; FCM transport/native delivery implementation is still being completed; actual delivery acceptance remains external |
| M5 Android parity | Five areas, profile/recommendations, reflections, inbox/preferences and native Play flows | 73 JVM tests, debug/instrumentation APK compilation, lint and unsigned release APK/AAB passed; device instrumentation, signing and internal-track purchase tests not run |
| M6 hardening and analytics | Verified-charge observations, learning/AI usage coverage, dedicated billing/auth/notification review and fixes | Full combined backend run passed 435 tests, zero failures/errors/skips; complete production Chromium suite passed 126 tests; optional FCM checks are still being completed |

## Recorded checks

- Frontend production build/typecheck and secret isolation passed: 22 backend canaries absent from 388 browser artifacts. Production CSP tests passed. The corrected initial-account/search race and navigation/layout assertions passed the final full browser run: 126 tests, zero failures/skips, including ten journeys against the local API.
- Billing/competition review regressions: 32 tests passed. Transport bounds: 6 tests passed. Deletion/restart and corrected competition fixture: 36 tests passed. These suites overlap; do not add their counts as unique coverage.
- Local API smoke passed registration, HttpOnly refresh cookie, INR 500,000 and USD 10,000 starting portfolios, refresh, logout, persistent login and fixture deletion.
- Android clean sequential validation passed 73 tests with zero failures/errors/skips, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`, `assembleRelease` and `bundleRelease`. The AAB is unsigned. See [Android evidence](ANDROID_COMMERCIAL_EXPERIENCE.md).
- Local commercial HTTP smoke also passed profile persistence, reflection isolation/consent rejection, disabled billing, token-free export and logout cleanup. A warmed, single-user sample of 20 reads per surface (100 total) returned no errors: profile p50/p95 12.95/30.32 ms; Home 13.67/38.43 ms; inbox 13.33/22.20 ms; reflections 13.55/34.99 ms; billing 10.30/37.50 ms. The local API used a 1 GiB heap and four active processors, with other validation work sharing the host. This small observation does not establish production capacity. Both disposable accounts were removed.
- A backend test JVM has occasionally needed Surefire's forced shutdown after completed tests. Preserve this diagnostic; it is distinct from assertion outcomes.

## Review fixes and known limits

Dedicated review led to fixes for historical unobserved-charge refunds/disputes, consistent grace access, expired test sandbox deadlines, bounded provider response time/body size, notification lock ordering, raw device-token exclusion and cleanup, exact callback routing, and fresh verification before deleting a restartable subscription. An explicit provider restart after account deletion cannot be atomically prevented by this API. Dispute recovery after a favorable outcome remains a support reconciliation task.

Billing stays disabled by default. Linked Play upgrades, license-test entitlement strategy, complete historical settlement revenue and token-encryption key rotation need acceptance/design work before claims of commercial readiness. FCM and actual sender/provider configuration are separate from passing in-app tests.

GitHub API access remains denied by the active environment network policy; Git remote reads and branch pushes work. A configuration draft was prepared earlier, but it has not been applied or published. Focused review branches will be pushed only after their final code is assembled; no PR URL is claimed without confirmation.

See [release readiness](STOXSIM_RELEASE_READINESS.md), [live subscription operations](LIVE_SUBSCRIPTIONS.md), [notifications](NOTIFICATIONS.md) and [cost assumptions](STOXSIM_COST_MODEL.md). Owner-controlled external acceptance, merge, release and activation remain separate states.
