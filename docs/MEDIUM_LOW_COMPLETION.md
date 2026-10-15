# Medium/low backlog completion — 16 October 2026

This change completes the repository implementation work below. Payment systems,
Android activity analytics, email delivery and Finwiz paid-tier behavior are outside
this batch. Live purchases remain disabled. No production data or permissions are
changed by merging these files.

## Delivered

- Website visits now record the existing daily check-in automatically on any
  foreground signed-in page, including restored sessions. The server's confirmed
  date/timezone controls day rollover; failures retry no more than once a minute.
  Anonymous/hidden pages do not record. Learning path shows status rather than a button.
- A separate quarterly Pro leaderboard has independent explicit enrollment and an
  independent baseline. Both read and enrollment require a current active Pro
  subscription with a future period end; Razorpay test benefits are excluded.
  It uses only the standard India ₹5 lakh account. Standard/global and private league
  enrollment is unaffected. There are no prizes or cash payments.
- Pro roster records remain after subscription expiry to preserve baseline/history;
  expired users cannot read, re-enroll or refresh the Pro board. Other participants
  see their last observed score, with its timestamp. Re-entry after renewal reuses
  the original baseline. The latest 50 ranked entries are displayed. This release
  has one Pro season per UTC quarter, not custom paid tournaments.
- Android now exposes pending limit-order modification with validation and an
  explicit before/after review; the existing server rechecks ownership, reservations
  and status. As with other writes, an ambiguous failure must be checked in Orders
  before another edit. No automatic write replay is introduced.
- Native campus controls now cover leaving, member removal/roles, cancellation,
  audit history, institution verification approval/rejection, suspension/restoration
  and organizer recovery. Sensitive actions show a reason/review dialog. Server
  authorization, independent affiliation checks and last-organizer guards remain
  authoritative. Platform analytics and billing administration remain web-only.
- Dependency updates from #118, #120, #154 and #156 are incorporated in this branch:
  Upstox SDK 1.29, Next 16.4/React 19.3, TypeScript 7/Playwright 1.64 and Actions updates.
  Existing dependency PRs become redundant only after this change is merged.
- Android 0.5.2 (version code 8) compiles AABs in normal CI. The manual signed-bundle
  workflow runs from main through the `android-release` environment, requires the
  protected upload key, and uploads only the AAB/checksum/commit. It never uploads
  to Play automatically. An unsigned build is not a distribution-ready release.

## External acceptance still required

| Item | Prepared implementation | Evidence needed to close |
| --- | --- | --- |
| Benchmark activation | Collector, entitlement checks, history calculations and default-off production flag already exist | Confirm privately held provider permission covers benchmark retention/display, then enable the flag on the deployment and observe real sessions. Do not fabricate past benchmark history. |
| Signed Play installation | AAB compilation, optional signing configuration and protected signed-bundle workflow | Configure upload-key secrets, produce/review the signed artifact, install through an internal track and record device results. |
| Device and store review | Native screens, deletion page and release checklist | Real device/large-font/offline/account-switch QA; current Play Console declarations, testing eligibility and review. Console state cannot be inferred from source. |
| Production backup recovery | Existing isolated workflow/runbook and tested guards | Dedicated validation host, read-only backup credentials, exact backup name and successful restore evidence. Never restore over production. |
| VPS capacity | Existing isolated workload and profile checks | Dedicated host matching production CPU/RAM; record latency/errors/resources and workload. CI synthetic tests do not measure production capacity. |

No live benchmark activation, signed-key use, Play publication, restore or production
load run was performed for this PR. Keep these rows open until actual evidence exists.

## Signing setup

Create the protected GitHub environment `android-release` and provision these secrets
outside chat/source: `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_STORE_PASSWORD`,
`UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD`. Preserve the existing app's upload key;
do not generate a replacement key casually. Dispatch **Android signed release bundle**
on main after normal checks pass. Download the artifact named with its commit SHA.
For Android Studio signing, use the same key locally or set all four
`STOXSIM_UPLOAD_*` environment variables documented in `app/build.gradle` and run
`./gradlew :app:verifyReleaseSigning :app:bundleRelease`. Never commit a keystore.

## Acceptance

- Open any signed-in website page; Learning path later shows today's recorded visit.
  Reload/resume on the same day without increasing the streak; test server-zone
  midnight and network recovery. Account changes must not inherit another visit.
- Pro board: Free/Plus/test/expired accounts receive 403. Verified active Pro access
  does not enroll automatically; enrollment cannot change the global roster or
  standard capital. Duplicate enrollment retains the baseline.
- Android: edit an OPEN LIMIT order, review changes, and compare reservations with
  the website. Try zero/invalid input and an order that fills before confirmation.
- Campus: use organizer/member/outsider/admin disposable fixtures. Confirm scoped
  denial, last-organizer protection, self-leave withdrawal and moderation reasons.
- Run Android build/unit/lint/emulator checks and full backend/frontend/browser CI.
  Actual private provider and Play acceptance remain separate from synthetic tests.
