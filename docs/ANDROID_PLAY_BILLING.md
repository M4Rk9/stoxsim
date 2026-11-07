# Native Google Play subscriptions

The app uses Google Play Billing 9.1.0, checked against Google's Maven metadata on 30 October 2026. Purchases remain unavailable until the backend enables its adapter and the owner configures approved products/base plans. No Razorpay checkout or external purchase link is offered inside Android.

Authenticated backend configuration supplies the package, opaque account identifier and Plus/Pro product/base-plan mappings. The app queries Google and displays localized regular recurring prices and renewal terms. Unknown packages, duplicate mappings, unsupported intervals and implicit introductory/personalized offers fail closed. Existing paid benefits disable new purchase buttons; the backend remains the authoritative duplicate-subscription check.

Callbacks and restored purchases are submitted only for the configured package/product and bound StoxSim account in PURCHASED state. Pending payments grant nothing. The backend verifies the provider, enforces ownership, acknowledges and updates existing entitlements. The client refreshes authoritative subscription/account state after verification; it never acknowledges, consumes or grants benefits. Purchase verification is bound to the original user inside the API session mutex, including after refresh, so a delayed callback cannot transmit an earlier account's token under another session.

Purchase tokens remain transient in memory and are neither persisted nor logged. Restore runs on billing connection and return to foreground, with an explicit Restore purchases action. Interrupted verification directs the user to restore rather than repurchase. Sign-out closes billing and cancels verification. Google Play's management URL supports cancellation; account deletion does not cancel Google billing. Billing history comes from the authenticated backend.

## Contract

- `GET /api/v1/billing/play/config`: `{enabled, packageName, obfuscatedAccountId, products:[{plan,productId,basePlanId}]}`.
- `POST /api/v1/billing/play/verify`: `{productId,purchaseToken}` → verified billing entry.
- `GET /api/v1/subscription`: existing authoritative benefits.
- `GET /api/v1/billing`: billing history.

## Validation and outstanding acceptance

Native compilation and all 70 unit tests passed on the restored branch on 5 November 2026 using `:app:testDebugUnitTest`. Coverage includes pending/unbound/cross-account purchases, malformed catalogs and delayed verification ownership. The Gradle distribution checksum comes from the official Gradle checksum endpoint. No real Play transaction was executed.

Run the complete Android CI debug/lint/instrumentation and unsigned release/AAB checks. Signed Play internal-track tests must cover new purchase, interruption/restart, restore, pending→paid, cancellation, renewal, decline, refund/revocation, provider outage, wrong StoxSim account and account deletion. Validate backend reconciliation, acknowledgment and RTDN identity/replay handling. Unit tests do not establish Play transaction readiness.

Owner inputs remain: approved catalog/prices, merchant/legal gates, least-privilege Android Publisher credentials, authenticated RTDN configuration, license testers, upload signing, actual Play signing certificate, internal-track installation and explicit activation/publication approval. Secrets do not belong in Android resources. Current official policy/integration pages at developer.android.com and support.google.com were blocked by environment networking when checked; recheck current submission requirements. No compliance, payment activation or publication is claimed.
