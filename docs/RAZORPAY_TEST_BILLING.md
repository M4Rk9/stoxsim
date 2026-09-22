# Razorpay test billing (M4 test phase)

This integration is disabled by default and available only to verified platform
administrators at `/admin/billing` (also linked from Settings). It creates real
Razorpay **test-mode** subscription resources with simulated payments, in a separate
StoxSim test ledger. It never upgrades real subscriptions, grants premium features,
changes portfolios or affects competition eligibility. Live keys are rejected at
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
4. Edit the protected `deploy/production/.env` on your server:

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
  Confirm the actual plan, trading capital and competition access are unchanged.

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
milestone. Actual premium entitlement activation, refund/grace rules, scheduled
cancellation, renewal reconciliation, sandbox portfolio locking/settlement and
live commercial approval remain separate work. Test refunds can be exercised in
the provider dashboard but do not update StoxSim entitlements. External acceptance
requires configured test credentials; automated tests use a mocked provider and
cannot prove merchant account access or payment-method availability.
