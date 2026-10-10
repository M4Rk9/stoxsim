# StoxSim Android and paid Google Play launch

Owner decision: Android first with native app screens. Personal Play Console
signup has started; the owner reports the $25 registration payment is still
pending. Plus/Pro purchases must be available at launch. This is a new release track;
the existing web-release milestones do not prove Android release readiness.

## Milestones

| Milestone | Work | Current state / completion evidence |
| --- | --- | --- |
| A1 — Native foundation | Kotlin/Compose UI, backend login/session, native balances, instrument search and portfolio reads | Implemented on the native foundation branch; automated build and owner device checks are tracked in its PR. |
| A2 — Native trading experience | Stock detail/charts, watchlists, order entry/history, sandbox selection and account controls | Pending; the previous TWA shell is superseded. |
| A3 — Verified paid subscriptions | Play catalog/client, server verification, acknowledgement, renewals, cancellation/refund and restore | Pending. Existing Razorpay functionality is test-only; public checkout is disabled. |
| A4 — Installable release and review readiness | Real-device QA, signing/AAB/internal Play installation, store listing, Data safety/deletion, app access and declarations | Pending native trading/billing and paid Play registration. |
| A5 — Testing and publication | License purchases, lifecycle/security QA, required closed testing, production-access application and review | Pending A2–A4. Publication is subject to Google's review. |

## Architecture and security boundary

The Android UI uses Kotlin and Jetpack Compose and communicates directly with the
existing Spring API at `https://api.stoxsim.com`. Keep the existing user accounts,
virtual-money accounting, provider integration and entitlement logic on the
backend. Do not duplicate market/provider credentials on the phone. The native
app retains `com.stoxsim.app`; it does not require Chrome for its main screens.

A1 supports the existing authentication contract without weakening the backend:
refresh values are read from secure HTTP-only response cookies and encrypted
under Android Keystore; bearer access tokens stay in memory. Refresh/logout use
the backend's existing JSON request contract. No browser sessions are imported,
no credential JavaScript bridge is added and no private responses are cached.
Core API requests use HTTPS, reject redirects and serialize refresh rotation.
Legal/help pages and email reset/verification links deliberately open the system
browser until scoped native handlers are implemented. A1 is not a trading or
purchase release.

The app remains an online educational simulator using virtual money. Live trading,
deposits, broker credential collection and cash prizes are not part of this plan.
Test back navigation, large fonts, keyboard/window insets, rotation, password
reset/email verification, revoked/expired sessions, connection loss and account
changes on a real device. Synthetic emulator tests and successful compilation do
not prove real-device or Play-installed release readiness. Preserve pricing-status
and valuation-time indicators so stale/unavailable data is not shown as live.

## Paid subscriptions (A3)

Google Play's standard payments policy covers digital functionality/subscriptions.
Do not simply expose the website's Razorpay checkout inside the Play app or link
users to another checkout. An eligible alternative-billing program would need
separate enrollment and compliant implementation; no such enrollment is assumed.

The existing catalog has Free, Plus (₹99/month) and Pro (₹199/month). Treat these
as draft price intentions, not Play-configured prices. The owner must configure
products/base plans, availability, taxes and billing terms in Play Console.
Candidate product IDs are `stoxsim_plus` and `stoxsim_pro`, each with a monthly
auto-renewing base plan. Confirm IDs before implementing server mappings.
Display Google's localized product prices and renewal terms in the app.

Implement the following as a separately tested adapter:

1. Detect actual Play billing capability rather than trusting a URL flag, user
   agent or browser-supplied entitlement. Select a native client supported by
   current Play Billing requirements; audit the resolved Android dependency.
   The foundation intentionally exposes no purchase or checkout UI.
2. Start purchases from a signed-in account. Bind purchase intent to an opaque
   backend-issued account identifier where the supported billing interface permits it.
3. Send purchase tokens to an authenticated, rate-limited Spring endpoint.
   Never log tokens, accept browser-supplied plan/expiry, or embed service-account
   credentials in Android assets or frontend code.
4. Verify the configured package and product, purchase state, expiry, existing
   ownership and linked replacement token with Google Play Developer API.
   Enforce token uniqueness and ownership transactionally; pending purchases
   grant no entitlement. A restore must not move another user's subscription.
5. Apply verified updates through `SubscriptionService.applyProviderUpdate`,
   retaining existing sandbox and leaderboard isolation. Store purchase linkage
   separately with appropriate encryption and limited access.
6. Acknowledge valid initial purchases within Google's required window, using
   retry-safe backend processing. Never treat frontend success as verification.
7. Implement authenticated Real-time Developer Notifications and server
   reconciliation. Verify Pub/Sub delivery identity/audience before processing,
   deduplicate notifications and re-query Google; notification content alone is
   not authoritative subscription state.
8. Handle expiry, renewal, cancellation at period end, grace/account hold,
   upgrades/downgrades, linked tokens, revocation and refunds. Coordinate
   acknowledgment, entitlements and Scenario Lab period credit refills so retries
   cannot mint extra benefits. Respect Play refunds; do not promise 'no refunds'.
9. Provide restore and manage/cancel subscription actions. Define account
   deletion with active subscriptions so users are warned and can manage billing;
   deleting a StoxSim account must not silently claim it cancels Play billing.
10. Test active website/test-provider subscription conflicts so providers cannot
    overwrite each other's entitlements or create accidental double subscriptions.

Required scenarios: new purchase, user cancellation, pending payment,
duplicate/replayed token, cross-user token, restore, renewal, subscription
cancellation, expiry, refund/revocation, linked replacement, out-of-order RTDN,
Google API/network failure and deletion with an active paid subscription.

## Owner inputs needed from Play Console

- App entry and confirmation of application ID; store availability countries.
- Whether the personal account was created after 13 November 2023 and whether
  production access/device/identity verification is complete.
- Public SHA-256 app-signing certificate; never share the private signing key.
- Subscription product and base-plan IDs and approved displayed pricing.
- Linked merchant/payments profile and licensing test accounts.
- A service account with least-privilege Android Publisher access, stored through
  backend deployment secrets, plus RTDN topic/authentication configuration.

Ask the owner for public identifiers and setup state; never request private keys
or secret JSON in chat. Credentials must be provisioned through deployment secrets.

## Review and personal-account eligibility

New personal accounts created after 13 November 2023 need at least 12 testers
opted in continuously for 14 days before applying for production access. Account
age and current Console status must be checked rather than assumed.

Complete the Financial features declaration accurately. Google's account guidance
says providers of financial products/services, including stock trading, should
use an organization account. StoxSim's current scope is virtual-money education;
do not claim it is a brokerage or that a personal account is automatically
eligible. Confirm the Console classification/review requirements for this actual
product before investing in the public release.

Data safety must reflect the website, backend, Android dependencies, support,
diagnostics and any future billing data. Publish actual retention periods and
active-subscription deletion behavior when A3 ships. Provide reviewer access to
login and paid features; do not bypass account security globally for review.

## Official references (checked 10 October 2026)

- [Jetpack Compose setup](https://developer.android.com/develop/ui/compose/setup)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Google Play payments policy](https://support.google.com/googleplay/android-developer/answer/10281818)
- [Play Billing integration](https://developer.android.com/google/play/billing/integrate)
- [Subscription lifecycle](https://developer.android.com/google/play/billing/lifecycle/subscriptions)
- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Account type guidance](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)

Recheck policies, supported billing libraries and Console requirements at submission.
