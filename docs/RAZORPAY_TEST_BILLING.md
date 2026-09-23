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
3. Place sandbox limit orders. Choose **Cancel renewal** and confirm. Its
   benefits show `ENDING` and a fixed paid-access end date. Existing orders and
   sandboxes remain usable through that date. After expiry, sandboxes become
   read-only, pending buys/sells are cancelled, and reserved cash and shares are
   released. Holdings and history remain available; standard orders are untouched.
   **Disable test benefits** is an administrator testing control: it locks
   sandboxes and restores Free immediately without cancelling provider renewal.
4. A successful renewal with a higher verified `paid_count` extends access to the
   paid period end. A `pending` renewal allows **72 hours from the last confirmed
   paid period end**. Repeated events never restart grace. Confirmed cancellation
   caps access at its recorded end date, without grace. Without a local confirmed
   cancellation, `halted`, `paused`, `cancelled`, `completed` and `expired` lock
   access immediately; external administrative shutdowns still revoke access.
5. The backend reconciles opted-in subscriptions every minute, up to 20 per pass,
   oldest checked first. A provider outage cannot extend the stored deadline:
   provisioning, order placement and settlement enforce it directly. Reconciliation
   releases remaining reservations after expiry, even when the provider is down.
   Revoking ADMIN or email verification also removes test access.

No new keys or environment values are needed for this phase. Migration V114 is
additive and defaults existing records to benefits off. Disabling the test billing
configuration also revokes projected benefits on the next reconciliation pass.

## Cancellation-only policy

The owner selected a cancellation-only, non-refundable purchase policy. There is
no refund API, refund button, automatic refund, or refund webhook in StoxSim.
The policy is displayed before test checkout and in Terms of Use.

Paid cancellation calls Razorpay with `cancel_at_cycle_end=true`. Unpaid or
already-ended paid periods use immediate cancellation. The backend stores a
`REQUESTED` intent and its original paid-through boundary before sending the
mutation. Only an identity-validated successful response or an authoritative
terminal status changes it to `CONFIRMED`. A generic `has_scheduled_changes` flag
is not proof of cancellation. Provider `active` status is expected until cycle
end, so confirmed intent is stored independently of that status.

If the request times out, **Reload billing**, then **Refresh status**. Pending
intent explicitly warns that renewal may still occur. **Retry cancellation**
keeps the original access boundary; confirmed repeats never resubmit cancellation.
If Razorpay accepted the request but its response was lost and it rejects a retry,
verify in the test dashboard or contact provider support; do not manually mark
the local request confirmed. The terminal webhook or reconciliation will resolve
it when the subscription ends. There is no automated cancellation reversal.

Migration V115 is additive; old rows default to `NONE`, so previously immediate
cancellations are not retroactively reactivated. No additional credentials or
webhook events are required. A delayed provider event or higher payment count
cannot extend access beyond a confirmed cancellation's fixed boundary.

Provider reference: [cancel a subscription](https://razorpay.com/docs/api/payments/subscriptions/cancel-subscription/).

### Final test acceptance

- With a successful paid test and enabled benefits, cancel renewal. Check the
  displayed date and provider dashboard; refresh/reload must preserve `ENDING`.
- Confirm sandbox access and reserved orders survive scheduling. Repeated clicks
  must not create subscriptions or change the end date.
- At expiry, check locked sandboxes and released reservations. Automated tests
  exercise this boundary without waiting a month; do not edit production dates
  to accelerate acceptance.
- Cancel an unpaid checkout and confirm it closes immediately.
- Ordinary learners must still receive 403; live keys must remain rejected.

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

The M4 test-mode engineering scope includes checkout, signed webhooks, benefits,
renewal/grace, cancellation and sandbox locking. The owner confirmed PR #127's
deployed lifecycle works. This final cancellation phase still requires deployment
acceptance. Refund functionality is excluded by owner decision, not pending work.

Commercial launch remains gated on Razorpay account activation, explicit approval
to enable live purchases, and a separately reviewed live-mode integration and
learner checkout rollout. Test credentials and administrator-only endpoints must
not be relabelled as live billing. Catalog flags for FinWiz, advanced analytics
and Scenario Lab do not implement those future product features. Automated tests
use a mocked provider and cannot prove merchant account access or payment-method
availability.
