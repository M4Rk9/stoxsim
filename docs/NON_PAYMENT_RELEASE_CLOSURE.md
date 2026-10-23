# Non-payment release closure — updated 23 October 2026

Scope: the web and native Android learner experience, analytics, account flows,
price/history correctness, release preparation and repository documentation.
Live payments/merchant activation remain excluded by owner decision. The owner
requested the remaining non-payment work on 23 October and reported Android
testing successful. Capacity testing is still awaiting an isolated suitable target;
the earlier lack of another VPS/machine has not been resolved by that request.
This register separates implementation from operational evidence;
it does not assert that merging a PR deploys it or completes Play review.

## Review and verification map

| Area | Implementation and automated evidence | External acceptance |
| --- | --- | --- |
| Home → first trade → Finwiz → Scenario Lab → Learning Path → Competitions | Server-owned journey, completed STANDARD trades only, pending/cancelled order handling, dismissal/resume and cross-user isolation. `GuidedJourneyIntegrationTest`, `GuidedJourneyServiceTest`, `guided-journey.spec.ts`, native `GuidedJourneyTest`. | One real learner journey on the deployed version and signed app; guidance does not force enrollment or consume credits. |
| Stock research / M2 | Home now records research only after a quote loads. Android 0.5.7 rejects selection-only/loading/failed-quote activity. Daily deduplication remains shared across clients. `stock-search.spec.ts`, `activity-analytics.spec.ts`, `StockResearchTest`, `ProductActivityTrackerTest`, `StoxSimApiTest`. | Confirm observed events after deployment; never alter signup dates to fabricate mature retention. |
| Feature engagement | V124 adds four server-only daily categories and independent observation coverage. Owner dashboard reports distinct learners per feature in the selected UTC range. Tests cover successful actions, failed requests, direct-ingestion denial, duplicates, test/admin exclusion, deletion and unknown windows. | Counts begin at migration, not historical journey completion. First partial date and earlier ranges show unavailable. |
| Finwiz tiers | Basic/Expanded/Full selected from database entitlement, bounded provider output and deterministic fallback. `FinwizAccessTest`, `FinwizServiceTest`, `FinwizTierIntegrationTest`, browser/native answer tests. | Real provider response quality can vary; synthetic tests do not certify every generated answer. No billing is enabled. |
| Email / account lifecycle | Contextual spam guidance; scoped native verification/reset with explicit confirmation and browser fallback; encrypted native refresh storage; backend one-time token/session revocation rules. `EmailActionLinkTest`, `EmailLinkLifecycleTest`, `StoxSimApiTest`, auth integration tests, email guidance/layout browser tests. | Actual mail-client routing, delivery and distribution-signing association require the real signed app/email. Inbox placement cannot be guaranteed by source changes. |
| Calendar / prices / history | PR #166 is on main. Verified 2026 sessions, early-close/DAY expiry safeguards, fallback notices and legacy/gap suppression; USD millions formatting. Scenario Lab now additionally rejects SIMULATED/COST_BASIS/unknown marks without spending credits or recording completion. Calendar/history/scenario integration tests and pricing browser/native tests. | Observe new snapshots after deployment; benchmark retention remains off until permission is confirmed. India calendar coverage ends 31 December 2026; US coverage now ends 31 December 2027. |
| Competitions / Learning Path | Scoped campus roles, optional enrollment, last-organizer protection, automatic daily visits and persistent progress. `CampusCompetitionIntegrationTest`, `CompetitionServiceTest`, `ProgressionServiceTest`, browser competitions/daily-visit tests and native completion tests. Removed stale manual-check-in mission copy. | Actual organizer/member acceptance remains a deployment check. |
| Android release | 0.5.7 / versionCode 13; normal CI builds APK/AAB, unit/lint and emulator tests. Protected manual signing workflow and website certificate utility exist. Updated Android Studio instructions, store listing draft and deterministic icon/feature-graphic review artifact. | Owner reports Android testing passed (23 October). Upload-key setup, signed internal-track installation, actual Play signing certificate, screenshots and Console declarations/review remain external. |
| Dependencies | Incorporates the four package/lock updates proposed in #165: STOMP 7.5, Next 16.4.2, React/React DOM 19.3.1. Existing scanner API alias remains unchanged. | #165 becomes redundant only after this candidate merges; it is not merged/closed by this work. |

## Deployment order and rollback

1. Review this candidate's backend, frontend, browser, Android, security and CodeQL
   checks on the exact head SHA. Record the run links in the PR.
2. Use the existing production deployment/backup process. Deploy backend first so
   Flyway V124 completes before the web or Android candidate sends new requests.
   No new credentials, provider permission or billing flags are introduced.
3. Deploy web and distribute the reviewed Android build. Production and device
   acceptance are evidence for that version, not automatically inherited from CI.
4. V124 is additive: previous application code can run with it. Roll back binaries
   using `deploy/production/rollback.sh`; do not drop event rows/columns or manually
   edit Flyway history. Retain the backup and verify normal health checks.

Store review assets are generated by `node mobile/android/render-store-assets.mjs`
and published by the frontend CI job with the candidate SHA. These are reviewed
promotional assets, not substituted device screenshots or a store submission.

## Short owner acceptance checklist

Record candidate SHA, date, app version, result and any defect. Use controlled test
accounts for repeated QA and keep them excluded from growth analytics. Do not
unmark QA accounts just to make the owner dashboard increase.

- Web and Android: restore a session; search NSE stock/ETF; open a quote; add to
  watchlist; place and review one paper order. Confirm guidance advances only on
  execution, then use Finwiz and Scenario Lab; verify portfolio balances unchanged
  by the scenario. Check no-credit/fallback-price errors are understandable.
- Switch accounts/markets and sign out; private state and progress belong to the
  right learner. Test offline/retry and large text without overlapping controls.
- Verification and reset: cold/warm native app, browser fallback, expired/used link,
  resend confirmation, and reset revoking existing sessions. Never include tokens
  or credentials in evidence screenshots/logs.
- Owner analytics: normal learners cannot read it; observed feature ranges show
  counts/zeroes and pre-coverage ranges show unavailable. Real usage needs elapsed
  observation time; D7/D30 results cannot be completed instantly.
- Signed app: validate package/version/certificate, install through internal track,
  and verify app links from an actual email. Review the listing draft against the
  build and fill Console declarations from actual behavior.

## 23 October completion update

- Restored the circular **Launch Finwiz AI** Home link on the web. It stays visible
  while scrolling, works with keyboard navigation, respects device safe areas and
  reduced-motion preferences, and keeps the feature cards. Additional bottom space
  lets the last page controls scroll clear of the floating links. Opening Finwiz
  does not automatically ask a question or spend AI quota.
- Android already has a persistent **Finwiz AI** bottom-navigation button on Home.
  The owner reports successful Android testing on 23 October. No native code or
  version changes are required for the web launcher fix. Device model, OS, exact
  build SHA and distribution-signing details were not supplied; this report is not
  evidence of a Play-installed signed build or specific email-client routing.
- Extended the US core-session calendar through 2027 from the published NYSE
  schedule, including observed holidays and the Thanksgiving early close. India
  still ends in 2026 pending a reviewed official schedule. A weekly coverage check
  reports approaching expiry before the existing fail-closed behavior takes effect.
- Repository scan found no additional explicit unimplemented consumer feature
  marker outside Play subscriptions. The remaining rows below are concrete source,
  configuration, production acceptance or distribution dependencies; this does not
  assert that searching TODO markers proves the entire product defect-free.

## What stays explicitly open

- Live payments and Play Billing: deferred; purchase buttons remain disabled.
- VPS-equivalent capacity evidence: existing guarded workflow needs an isolated
  CPU/RAM-matched target. CI measurements are not production capacity evidence.
- Real production-backup restore: existing workflow needs the isolated runner,
  exact offsite backup and read-only backup credentials. Synthetic CI restore
  remains a useful check, not proof that the production backup can be recovered.
- Deployment/provider acceptance: fresh learner journey, eligible Scenario Lab run,
  owner feature counts, real Finwiz tier responses and actual email-link delivery/
  routing still need evidence not supplied by the general Android pass. D7/D30
  retention also requires elapsed observation time.
- Signed distribution/Play review: supply upload-key secrets in the protected
  environment, use the actual Play app-signing certificate with
  `mobile/android/configure-asset-links.mjs`, install through an internal track,
  complete current Console declarations/screenshots and submit for review. Never
  substitute the upload/debug certificate for the distribution certificate.
- Benchmark activation: provider retention/display permission must be confirmed
  before enabling the existing default-off deployment flag. Do not fabricate
  historical observations.
- India 2027 calendar: obtain and review the official cash-equity trading schedule,
  update exceptions/coverage and deploy before year-end. US 2027 is implemented.

A second VPS is not a prerequisite for code completion. Existing recovery/load
scripts enforce isolated-target safeguards; do not point them at production or
bypass guards. A same-host temporary restore would need a separately reviewed
resource/volume/network plan before execution. This PR does not loosen those guards.
