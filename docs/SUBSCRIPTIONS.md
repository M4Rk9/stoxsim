# Subscription and sandbox architecture

StoxSim has a provider-neutral entitlement foundation for Free, Plus and Pro.
Paid checkout is intentionally disabled until a billing provider, webhook
verification and operating procedures are approved.

## Product tiers

| Plan | Price target | Competitive portfolio | Sandbox allowance | Learning tools |
| --- | ---: | ---: | ---: | --- |
| Free | ₹0 | ₹5 lakh | None | Basic FinWiz and analytics |
| Plus | ₹99/month | ₹5 lakh | One India sandbox with ₹25 lakh | Expanded FinWiz and advanced analytics |
| Pro | ₹199/month | ₹5 lakh | Up to five India sandboxes, each with ₹1 crore | Full FinWiz, advanced risk, Scenario Lab and premium competitions |

The plan catalog describes the roadmap entitlement contract. Features are
enabled separately as their production batches ship; the catalog is not a claim
that paid checkout or every premium feature is live.

## Leaderboard integrity

Every `virtual_account` is classified as `STANDARD` or `SANDBOX`.

- Standard India accounts remain fixed at ₹5 lakh and are leaderboard eligible.
- Sandbox accounts are never leaderboard eligible.
- Existing order, holding, trade, ledger, analytics, report, progression and
  competition queries explicitly select standard accounts.
- The database prevents a second standard account for the same user and market,
  while allowing separately keyed sandbox slots.
- Authentication responses continue to contain standard accounts only. The
  subscription endpoint returns sandbox metadata separately.

These boundaries make it impossible for a larger paid balance to replace the
account used by the standard leaderboard through an ambiguous user/market
lookup.

## API

`GET /api/v1/subscription` is authenticated and read-only. It returns:

- the current plan and status;
- the `subscription-entitlements-v1` plan catalog and entitlements;
- provisioned sandbox accounts, including whether they are active or locked;
- `billingEnabled: false` while checkout is unavailable.

There is deliberately no public plan-change endpoint.

`GET /api/v1/accounts` returns every portfolio owned by the authenticated
learner. The browser uses the account ID for sandbox-safe trading and valuation:

- standard India and United States portfolios remain available to every user;
- active paid sandboxes can place, modify and cancel their own orders;
- inactive sandboxes retain read-only history and valuation;
- an account owned by another user is indistinguishable from a missing account;
- sandbox orders do not update onboarding or standard-portfolio FinWiz feedback.

The dashboard and detailed portfolio page persist the selected account locally
and always label whether it is competitive or excluded from rankings.

## Future billing adapter

`BillingSubscriptionUpdate` and `SubscriptionService.applyProviderUpdate` form
the internal boundary for a future billing integration. A production adapter
must be added in its own reviewed batch and must:

1. verify the provider webhook signature before parsing or applying an event;
2. deduplicate provider event identifiers;
3. resolve the StoxSim user from an opaque provider customer reference;
4. never accept a plan, user ID or price from the browser;
5. apply updates under the subscription row lock;
6. define grace-period, refund, cancellation and chargeback behaviour;
7. cancel or settle open sandbox orders before locking a downgraded sandbox;
8. keep provider secrets only in deployment secrets.

The internal service currently provisions or reactivates the first sandbox for
an active paid entitlement and locks all sandboxes for a non-active status. Pro
additional-portfolio creation remains a separate future batch.

Basic private leagues already released to Free learners remain available. Plus
and Pro can introduce expanded league allowances in a later gating batch without
silently removing that existing learning feature.

## Data lifecycle

Subscription state and sandbox accounts are included in the authenticated
account export. Both cascade-delete with the user. Provider references are
opaque identifiers; payment card or bank details are never stored by StoxSim.

## Deployment

Flyway migration `V107` backfills every existing learner to Free and marks all
existing accounts as standard. It is additive except for replacing the former
two-column account uniqueness constraint with a stricter scoped uniqueness
index. No environment variable, secret or paid service is required.
