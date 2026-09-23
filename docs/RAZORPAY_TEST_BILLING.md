# Razorpay test billing (M4 test phase)

This integration is disabled by default and available only to verified platform
administrators at `/admin/billing` (also linked from Settings). It creates real
Razorpay **test-mode** subscription resources with simulated payments, in a separate
StoxSim test ledger. Benefits stay off until an administrator explicitly enables
them after a verified payment. Test benefits provision separate Plus/Pro sandboxes;
they never replace an existing paid subscription or affect standard portfolios or
competition eligibility. Live keys are rejected at
startup when this feature is enabled. The ordinary pricing page remains disabled.

## Configure on the server

1. In the Razorpay dashboard, switch to **Test mode** and generate test API keys.
   Keep the key secret on the server; never paste it into chat, source control or
   a `NEXT_PUBLIC_*` variable.
2. Create two **test** plans: monthly, interval 1, INR, Plus **9900 paise (₹99)**
   and Pro **19900 paise (₹199)**. Their IDs must be distinct. The backend checks
   currency, frequency and amount before creating a subscription.
3. Add a test-mode webhook for
   `https://api.stoxsim.com/api/v1/billing/test/webhook` (use your API domain).
   Set a separate random webhook secret of at least 32 characters. Subscribe to
   `subscription.authenticated`, `subscription.activated`, `subscription.charged`,
   `subscription.pending`, `subscription.halted`, `subscription.cancelled`,
   `subscription.completed`, `subscription.paused`, `subscription.resumed`,
   and `subscription.updated` where available in the dashboard.
4. Edit the protected `.env` in your deployed Compose directory. On the current
   production VPS this is `/home/ubuntu/stoxsim-production/.env` (the repository
   template is `deploy/production/.env.example`):

   ```dotenv
   STOXSIM_BILLING_TEST_ENABLED=true
   STOXSIM_BILLING_TEST_KEY_ID=rzp_test_REPLACE
   STOXSIM_BILLING_TEST_KEY_SECRET=REPLACE
   STOXSIM_BILLING_TEST_WEBHOOK_SECRET=REPLACE_WITH_RANDOM_SECRET
   STOXSIM_BILLING_TEST_PLUS_PLAN=plan_REPLACE_PLUS
   STOXSIM_BILLING_TEST_PRO_PLAN=plan_REPLACE_PRO
   ```

5. Deploy the merged image through the existing production deployment procedure.
   Recreate the backend after environment changes and reload Caddy with this
   release's configuration. Checkout needs the scoped CSP on `/admin/billing`.
   No frontend secret or frontend rebuild is required for credential changes.

Razorpay permits test mode while account activation is pending. Live payments
still require the provider's activation process. References:
[quickstart](https://razorpay.com/docs/payments/quickstart/),
[API keys](https://razorpay.com/docs/api/authentication/),
[subscription integration and test details](https://razorpay.com/docs/payments/subscriptions/integration-guide/),
[webhook validation](https://razorpay.com/docs/webhooks/validate-test/).

## Acceptance checklist after configuration

- Sign in with a verified ADMIN account, open Settings → Test subscriptions,
  acknowledge simulated payments and start Plus. Use Razorpay's documented test
  payment details, never a real card. Repeat for Pro after cancelling Plus.
- Complete checkout, then Refresh status. The browser success callback alone is
  not proof of payment; the backend fetches the provider resource. Verify its
  status and paid count against the test dashboard.
- Dismiss checkout, reopen it and try a failed test payment. Reloading or retrying
  must preserve the existing subscription rather than creating a duplicate.
- Deliver a signed webhook, replay it and replay an older activation after
  cancellation. The ledger must retain Razorpay's **current** status. Unsigned or
  altered bodies must return 401; duplicate delivery is acknowledged without
  repeated state changes. Failed provider fetches return an error for retry.
- Cancel the test subscription and refresh. Confirm cancellation in both places.
- Confirm ordinary learners receive 403 even with a forged browser admin flag.
  Without opting in, confirm the actual plan and portfolios are unchanged.

## Test benefits and lifecycle acceptance

1. After checkout, use **Refresh status**. Once the subscription is `active` with
   at least one confirmed payment, choose **Enable test benefits** and confirm.
2. Open **Account settings → Plan & sandboxes**. The plan is marked **TEST**.
   Plus provisions one ₹25 lakh sandbox; Pro allows up to five ₹1 crore sandboxes.
   Repeating enable/renewal reuses portfolios and preserves balances and history.
   Standard ₹5 lakh competitive portfolios and premium competition eligibility
   do not change. Test benefits are available only to verified administrators.
3. Place sandbox limit orders. Cancel the test subscription, then refresh. Its
   sandboxes become read-only, pending buys/sells are cancelled, and reserved cash
   and shares are released. Holdings and trade history remain available. Standard
   portfolio orders are untouched. **Disable test benefits** also locks sandboxes
   and restores the Free plan without cancelling the provider subscription.
4. A successful renewal with a higher verified `paid_count` extends access to the
   paid period end. A `pending` renewal allows **72 hours from the last confirmed
   paid period end**. Repeated events never restart grace. `halted`, `paused`,
   `cancelled`, `completed` and `expired` lock access without grace.
5. The backend reconciles opted-in subscriptions every minute, up to 20 per pass,
   oldest checked first. A provider outage cannot extend the stored deadline:
   provisioning, order placement and settlement enforce it directly. Reconciliation
   releases remaining reservations after expiry, even when the provider is down.
   Revoking ADMIN or email verification also removes test access.

No new keys or environment values are needed for this phase. Migration V114 is
additive and defaults existing records to benefits off. Disabling the test billing
configuration also revokes projected benefits on the next reconciliation pass.

## Timeout recovery and operation

The backend commits an opaque checkout reservation before requesting a provider
subscription. It deliberately does not retry an ambiguous creation automatically.
If a checkout remains `CREATING`, find its `stoxsim_test_reference` in the test
dashboard and use **Reconcile test subscription** with its `sub_...` ID. The server
fetches it using test credentials and checks the owner, reference, mapped plan and
quantity before linking it. A matching webhook can also finish reconciliation.
Never attach an unrelated subscription. If no provider resource exists, verify
the request outcome with the provider before an operator closes the orphaned
local reservation; do not blindly delete rows or create another subscription.

Keep the plan IDs stable while test subscriptions are open. Rotate API credentials
within the same test account. Coordinate webhook secret rotation with provider
retries; this initial integration accepts one current webhook secret. Disable by
setting `STOXSIM_BILLING_TEST_ENABLED=false` and recreating the backend; this also
rejects webhook processing, so cancel open tests before shutdown. Local account
deletion cascades test records but does not cancel remote test resources; cancel
them first. Account export includes the local test subscription ledger. No card
data, webhook payloads, API secrets or provider error bodies are stored or logged.

## Scope still remaining before live billing

This is the M4 provider test phase, not completion of the paid-subscription
milestone. Refund handling, scheduled cancellation and live commercial approval
remain separate work. Catalog flags for FinWiz, advanced analytics and Scenario
Lab do not implement those future product features. Test refunds can be exercised in
the provider dashboard but do not update StoxSim entitlements. External acceptance
requires configured test credentials; automated tests use a mocked provider and
cannot prove merchant account access or payment-method availability.
