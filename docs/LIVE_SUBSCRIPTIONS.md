# Verified subscription billing

Live billing is implemented behind disabled-by-default provider flags. Local tests use mocked provider responses and PostgreSQL; they do not establish merchant, mandate, settlement, or Google Play acceptance. Prices remain Plus ₹99/month and Pro ₹199/month. No live transaction, provider activation, or publication has been performed.

## Activation configuration

All providers require `STOXSIM_LIVE_PAYMENTS_APPROVED=true`, set only after the owner approves activation and external acceptance is complete. Enabling a provider without this approval causes startup to fail.

Website billing requires `STOXSIM_BILLING_LIVE_ENABLED`, live key ID/secret, a webhook secret of at least 32 characters, and distinct Plus/Pro plan IDs (`STOXSIM_BILLING_LIVE_KEY_ID`, `STOXSIM_BILLING_LIVE_KEY_SECRET`, `STOXSIM_BILLING_LIVE_WEBHOOK_SECRET`, `STOXSIM_BILLING_LIVE_PLUS_PLAN`, `STOXSIM_BILLING_LIVE_PRO_PLAN`). The server fetches each plan and checks a monthly interval, INR currency and exact 9900/19900 minor-unit amount. Current checkout creates 12 recurring cycles; review and disclose that finite duration before activation.

Google Play requires `STOXSIM_PLAY_BILLING_ENABLED`, distinct product IDs and monthly base-plan IDs (`STOXSIM_PLAY_PLUS_PRODUCT`, `STOXSIM_PLAY_PRO_PRODUCT`, `STOXSIM_PLAY_PLUS_BASE_PLAN`, `STOXSIM_PLAY_PRO_BASE_PLAN`), a least-privilege service-account credential file at `STOXSIM_PLAY_CREDENTIALS_FILE`, a stable 32-byte hex encryption key at `STOXSIM_PLAY_TOKEN_ENCRYPTION_KEY`, and authenticated RTDN audience/service identity (`STOXSIM_PLAY_NOTIFICATION_AUDIENCE`, `STOXSIM_PLAY_NOTIFICATION_EMAIL`). Store credentials outside Git and Android resources. Losing the encryption key prevents reconciliation of existing purchases; retain it in the approved secret-backup process. No key rotation migration is implemented yet.

The server binds Play purchases to a random account reference supplied to the native client. It checks package, product, base plan, ownership, acknowledgment, lifecycle state, expiry and voided purchase evidence. License-test purchases cannot grant production entitlements. Linked upgrade/replacement tokens require support reconciliation; automatic plan replacement is not implemented. Test-license/internal-track acceptance therefore needs an explicitly isolated test entitlement strategy before launch, not a production bypass.

## Lifecycle and ownership

Checkout reserves a local UUID before requesting a provider subscription. Repeating the same request key returns that reservation; a timeout cannot automatically create another subscription. A pending creation must be reconciled using a provider reference whose authoritative notes match the reservation. A frontend success callback never grants access.

Paid access requires authoritative provider reads. Website payments must match the subscription invoice, currency and catalog amount and be captured with no refund. Provider event signatures/Google OIDC are verified, event IDs deduplicated, and current provider state fetched before projection. Exact POST callbacks are public transport endpoints; adjacent authenticated APIs and other HTTP methods remain protected. Provider failures are sanitized.

`billing_access_until` is an independent local deadline for live subscription benefits and sandboxes. Website grace is at most 72 hours after the last verified paid period; repeated reconciliation cannot extend it. Play grace uses Google's verified expiry. A grace period must not replenish credits. Expiry gates access even during reconciliation outages. Refunds and unresolved disputes revoke access without claiming that recurring billing stopped. An older charge webhook resolves payment → invoice → locally owned subscription if that charge was not previously observed.

Website cancellation is requested persistently and confirmed only after provider acknowledgment. Paid access can continue to the verified cancellation deadline. Google cancellation remains in Play. Account deletion is blocked while any remote renewal is unresolved; deleting an account cannot safely stand in for cancellation. Export contains purchase metadata, not purchase tokens, encryption material or transport credentials.

## Operations and limits

V130 is additive after V127–V129. One reconciliation worker considers at most 20 purchases per run, prioritizing older observations. Disable it with `stoxsim.billing.reconciliation-enabled=false` during an incident; local deadlines still expire access. Disabling a provider prevents new verification and purchase operations, so do not represent this as remote cancellation. Never drop ledger data as an application rollback.

Revenue at `GET /api/v1/billing/admin/revenue` requires a verified platform administrator. It reports observed active subscriptions, captured/refunded website charges, failures and cancellations. Historical invoice coverage is incomplete; Play settlements, taxes, fees and net revenue remain unknown. Do not multiply entitlements by list price and label the result cash revenue.

External acceptance still requires real catalog and provider permissions, staging lifecycle evidence (renewal, cancellation, refund/dispute, webhook retries, outage and recovery), tax/invoice/legal review, authenticated RTDN delivery, signed Play device tests, current official policy checks and owner activation approval. See [release readiness](STOXSIM_RELEASE_READINESS.md) and [native billing](ANDROID_PLAY_BILLING.md).
