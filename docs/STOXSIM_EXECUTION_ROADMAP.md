# Execution roadmap

Deliver focused, reviewable branches. Reuse the existing modular monolith, database, queues, UI and authoritative accounting. No new subscription tiers, paid infrastructure or production activation is part of implementation approval.

| Milestone | Implementation | Required acceptance | Dependencies |
|---|---|---|---|
| M0 — Audit and navigation | Audit/register; five primary web/native areas; preserve deep links and trading workflow | Type/build, keyboard/mobile/navigation regressions, baseline performance | Existing source and GitHub review evidence |
| M1 — Identity and personalization | Preserve merged Google auth; optional experience/objective/market profile; deterministic Home action; measured use | Ownership, persistence across clients, safe recommendations, no fabricated usage, existing login tests | Profile contract; real Google setup for external acceptance |
| M2 — Commercial billing | Disabled-by-default live Razorpay and Play adapters; one authoritative lifecycle/ledger; purchase/restore/cancel/history UI | Signed/verified callbacks, duplicate/out-of-order events, grace/expiry/refund/revocation, cross-account denial, provider downtime, migration and sandbox isolation | Provider catalogs, policy review and staging credentials; separate activation approval |
| M3 — Finwiz and reflection | Private bounded reflections, explicit AI opt-in, grounded context, deterministic fallback, bounded provider usage | Consent, ownership/export/deletion, quota concurrency, safety and fallback tests | Existing Finwiz; optional Gemini credential |
| M4 — Notifications and delivery | One inbox; preferences; bounded deduplicated event processing/outbox; optional email/push; suppression/unsubscribe | Isolation, consent, token ownership, deduplication, retries and failure isolation | Existing authoritative events; real SMTP/FCM configuration for delivery acceptance |
| M5 — Android and discovery | Native profile/learning/compete/account integration, Play purchase/restore, notification center | Android compile/unit tests; account switching/offline/session recovery; signed internal-track and device acceptance | Shared APIs, SDK, Play/FCM configuration and owner-controlled signing |
| M6 — Hardening and handoff | Dedicated security review, business metrics with honest coverage, bounded local performance evidence, cost and launch register | Full backend/web/browser suites, Android checks, CI on exact PR SHA, migration/export/deletion checks | Real backup recovery, VPS capacity, market calendar/licensing and release operator evidence |

## Ownership and contracts

Track A owns frontend; Track B owns backend modules; Track C owns Android. Independent clones use focused branches, not concurrent changes to one checkout. Root integration owns global security, account export/deletion and cross-domain validation. Migrations are allocated V127 profile, V128 reflections/AI usage, V129 notifications, V130–V134 billing; never edit deployed migrations.

Profiles use `GET/PUT /api/v1/learner/profile`; recommendations use `GET /api/v1/learner/home` and explicit `POST /api/v1/learner/recommendations/{id}/used`. Reflection CRUD uses `/api/v1/reflections`, and only explicit `POST /{id}/assistance` with consent may send selected content to Finwiz. Notification inbox/preferences/device registration are under `/api/v1/notifications`. Billing contracts are provider-neutral and owned by the billing module. Authenticated owner IDs always come from the validated session.

## Additional identity providers

Retain Google and password login for this release. Apple requires developer configuration and policy review; Microsoft requires tenant/application registration and provider-specific issuer validation; Facebook requires app review and scopes. None is essential to the first commercial release without observed demand. Never merge accounts merely because provider emails match. These evaluations do not claim registration or approval with any provider.

## Explicit external gates

Current GitHub API/check access; merchant KYC/live catalog and webhook verification; Play account/products/service identity/internal test track/signing; SMTP SPF/DKIM/DMARC and actual inbox/bounce/complaint evidence; FCM project/credentials if enabled; commercial market-data/benchmark retention rights; official India 2027 calendar; production-equivalent capacity and selected encrypted-backup restore; final immutable release identity, rollback proof and owner approval. Continue independent implementation while these remain open.
