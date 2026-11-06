# Commercial release readiness

This register is for founder-controlled release decisions. It does not authorize merge, deployment, live charges, bulk messaging or Play publication.

| Decision | Current state | Required evidence |
|---|---|---|
| Ready for merge | Not yet | Focused PRs, all relevant final-commit tests/CI, payment/auth review and resolved findings |
| Ready for deployment | Not yet | Immutable approved SHA, migration compatibility, backup readiness, health/monitoring and tested rollback |
| Ready for live payments | Blocked | Real merchant/catalog configuration, provider staging lifecycle evidence, commercial/legal review and explicit activation approval |
| Ready for Play submission | Blocked | Signed release AAB, internal-track installation, purchase/restore/device acceptance, current Console policies/disclosures and owner approval |
| Ready for marketing | Existing public site; new commercial claims not yet approved | Accurate feature/pricing copy, working registration/onboarding, support and legal pages, measured analytics, no unsupported availability claims |

## Final candidate checks

- [x] Core combined backend unit/integration/security suite: 435 tests, no failures/errors/skips; fresh PostgreSQL migrations through V130 passed. Final optional FCM changes require their own validation.
- [x] Web typecheck/build/secret isolation and all 126 production Chromium tests passed, including ten local API journeys.
- [ ] Android compile/unit checks and signed device/session/offline/account-switch tests recorded separately.
- [ ] Payment success/pending/failure/duplicate/forged/out-of-order/renewal/cancellation/refund/revocation/expiry/cross-owner/interrupted/downtime cases pass.
- [x] Customer export/deletion includes new records; tests verify token exclusion, owner isolation, session cleanup and provider-aware deletion protection.
- [x] Inbox/email opt-in, suppression, unsubscribe, deduplication, bounded delivery and concurrency tests passed; no campaign was sent. FCM remains separately gated.
- [x] AI consent/ownership/quota/fallback tests passed; usage counters and unknown monetary costs remain distinct.
- [ ] Dedicated authorization/payment/security review has no unresolved critical findings; dependency/security scan limitations recorded.
- [x] Bounded local timing/functional smoke recorded 100 warmed reads with zero errors. Workload, limits and results are in the progress register; VPS capacity remains unverified.

## External evidence still required

- [ ] Current GitHub PR/check/review state, including separate PR #170; do not merge from historical green checks.
- [ ] Google deployed web/native provider acceptance for the actual origins, client IDs and signed package.
- [ ] Merchant KYC/live catalog/webhooks, recurring mandate requirements, refunds/cancellations and tax/invoice review by appropriate owner/adviser.
- [ ] Google Play account/catalog/service identity, token verification permissions, RTDN audience, financial features and Data Safety declarations.
- [ ] Provider permission covers commercial subscription use; benchmark retention remains separately gated.
- [ ] Official India 2027 market schedule is reviewed; do not invent trading holidays.
- [ ] SPF/DKIM/DMARC and sender identity verified against real DNS; actual inbox, bounce/complaint/suppression/unsubscribe evidence.
- [ ] Selected encrypted production backup restored to an approved isolated target with timings/data checks.
- [ ] VPS-equivalent bounded capacity exercise and monitoring/alert receipt verified.
- [ ] Final release identity, previous immutable image, schema compatibility and rollback procedure approved.

## Acquisition and handoff

Keep product name StoxSim and existing accounts/data. Verify homepage explanation, mobile registration, optional short learner profile, paid-plan differences/renewal/cancellation terms, support contact, privacy/terms/deletion pages, metadata/social cards, and screenshots of the actual candidate. Do not advertise paid functionality before activation/acceptance. Reports must distinguish implemented, tested, PR reviewed, merged, deployed and production verified.

For release procedure use [the existing checklist](RELEASE_CHECKLIST.md), [M7 evidence](M7_RELEASE_EVIDENCE.md), [deployment](DEPLOYMENT.md) and the [execution progress register](STOXSIM_PROGRESS.md). Retain credentials, customer data and private provider correspondence outside Git.
