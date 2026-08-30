# StoxSim Feature Roadmap Implementation Register

This register keeps implementation batches aligned with the StoxSim Feature
Implementation Roadmap. It describes product readiness, not market-data display
rights; provider-dependent work remains gated by `MARKET_DATA_PERMISSION.md`.

## Current progression

| Roadmap area | Status | Notes |
| --- | --- | --- |
| Core public-release platform | Implemented | Authentication, separate India/US portfolios, paper orders, holdings, watchlists, market status, FinWiz, deployment, recovery and observability exist. |
| Guided onboarding | Implemented | Persisted introduction state and the first-trade walkthrough were deployed in PR #91. |
| StoxScore and portfolio risk | Implemented | Versioned StoxScore v1 structure model, concentration analytics and dashboard explanation were deployed in PR #93. |
| FinWiz portfolio feedback | Implemented | Backend-authoritative post-trade structure feedback for executed paper orders was deployed in PR #94. |
| Portfolio analytics | Implemented | Cash/position allocation and fee-adjusted realized/unrealized attribution were deployed in PR #95; benchmark and historical risk views remain provider-gated. |
| Weekly portfolio reports | Implemented | Immutable snapshots, explicit opt-in, timezone-aware delivery, preview and history were deployed in PR #96. |
| Challenges, missions, XP and achievements | Implemented | Server-owned missions, idempotent XP, levels, daily learning streaks and persisted achievements were deployed in PR #97. |
| Improved leaderboards | Implemented | Opt-in quarterly ranking uses only entry-relative performance of the standard ₹5 lakh account; deployed in PR #98 and hardened in PR #99. |
| Private leagues and campus competitions | In progress | Capped invite-only leagues are deployed; the current batch adds institution verification and platform moderation before campus standings are opened. |
| Plus and Pro architecture | Implemented foundation | Entitlements, isolated account trading and controlled Pro multi-portfolio provisioning were deployed through PR #102; checkout remains intentionally disabled. |
| Scenario Lab and advanced history | Missing | Pro roadmap item; historical-data licensing and retention must be verified first. |

## Batch 1 — guided onboarding and first trade

- Three-step educational introduction for first-time learners.
- Persisted introduction and dismissal state.
- Two-step dashboard coach from instrument search to the paper order ticket.
- Backend-authoritative first-order milestone in the order transaction.
- Existing learners with prior orders are backfilled as having placed a first order.
- No new secret, provider, paid service or market-data redistribution.

## Boundaries carried forward

- StoxSim remains an educational market simulator, not an advisory service.
- FinWiz must not provide personalized buy/sell instructions, return guarantees or
  misleading price predictions.
- Free competitive portfolios remain fixed at ₹5 lakh.
- Plus ₹25 lakh and Pro ₹1 crore capital belong in isolated sandbox portfolios and
  cannot affect the standard leaderboard.
- Market-data-dependent features stay behind provider-neutral boundaries until
  display and redistribution permission is documented.

## Batch 2 — StoxScore and portfolio structure

- Versioned and documented 0–100 StoxScore formula.
- Breadth, weight-balance and largest-position concentration components.
- Effective holdings, concentration index, largest-position and top-three
  weights.
- Explicit pricing coverage, confidence and limited-data behavior.
- Authenticated market-scoped API and responsive dashboard card.
- Educational disclaimer and no predictive or buy/sell language.
- No new secret, provider, paid service or market-data redistribution.

## Batch 3 — FinWiz post-trade portfolio feedback

- Capture StoxScore snapshots immediately before and after an executed paper
  order without weakening order validation or settlement.
- Return additive, versioned feedback on the successful order response.
- Explain score, breadth, effective-holdings and largest-position changes.
- Do not claim an effect for an open order or when the before-trade snapshot is
  unavailable.
- Keep deterministic fallback behaviour so Gemini availability cannot block a
  paper trade.
- Display dismissible feedback beside the dashboard StoxScore with an explicit
  educational disclaimer.
- No database migration, new secret, provider call or market-data
  redistribution.

## Batch 4 — portfolio allocation and performance attribution

- Versioned `portfolio-insights-v1` contract isolated from the StoxScore formula.
- Cash versus invested allocation and per-position invested/account weights.
- Fee-adjusted realized contribution reconstructed from the complete trade ledger.
- Unrealized contribution derived from current server-valued holdings.
- Closed positions remain visible in realized attribution and totals reconcile to
  the account source of truth.
- Explicit confidence and pricing coverage when a quote uses the valuation
  fallback.
- India and United States accounts remain isolated.
- No database migration, new secret, provider call or market-data
  redistribution.

## Batch 5 — weekly portfolio learning reports

- Immutable versioned snapshots for both isolated market accounts.
- Explicit opt-in disabled by default and verified-email enforcement.
- Timezone-aware Monday delivery with unique per-user weekly periods.
- At most three delivery attempts, persisted status and safe scheduler retries.
- Current preview and the latest twelve saved reports in account settings.
- Account value, simulated P/L, allocation, activity count and data confidence;
  no raw quote feed or new provider field is persisted.
- Existing SMTP configuration is reused with no new paid service or secret.
- Report preference and history are included in account export and deleted by
  cascade with the account.

## Batch 6 — challenges and learning progression

- Versioned `learning-progression-v1` challenge and mission catalog.
- XP is awarded only from backend-authoritative onboarding, watchlist, order,
  trade, holding and consecutive-date state.
- Per-user pessimistic locking plus unique mission and achievement constraints
  prevent duplicate awards during concurrent requests.
- Existing learner activity is reconciled automatically; no artificial reset is
  required after deployment.
- Five level thresholds, six persisted achievements and a responsive learning
  path show progress without using profit, trade size, trade volume or rank.
- Daily check-ins are idempotent and use the learner's report timezone or the
  documented Asia/Kolkata default.
- Progression data is included in account export and removed by account cascade.
- No new secret, provider, paid service or market-data redistribution.

## Batch 7 — seasonal competitions and private leagues

- UTC quarterly seasons with explicit global opt-in.
- Entry-relative percentage ranking with visible join and valuation freshness.
- Strict eligibility for the standard ₹5 lakh India account; future paid
  sandbox capital cannot enter.
- Invite-only 25-member leagues with member-only standings and owner controls.
- High-entropy, one-time-visible invite codes stored only as SHA-256 hashes.
- Bounded join attempts, idempotent enrollment and database uniqueness guards.
- Competition data in account export with invite hashes excluded and account
  cascade deletion.
- No new secret, provider, paid service or market-data redistribution.

## Batch 8 — subscription entitlements and sandbox isolation

- Persisted Free, Plus and Pro plan state with a read-only authenticated API.
- Versioned product entitlements for capital, FinWiz, analytics, private
  leagues, Scenario Lab, multiple portfolios and premium competitions.
- Explicit `STANDARD` and `SANDBOX` account scopes with database constraints.
- Paid sandbox capital of ₹25 lakh for Plus and ₹1 crore for Pro, always marked
  ineligible for the standard leaderboard.
- Every existing user backfilled to Free and every existing account preserved as
  a standard account.
- Internal provider-neutral update boundary with no browser plan mutation and no
  enabled checkout.
- Responsive account-settings plan comparison and sandbox status view.
- No new secret, provider, paid service or market-data redistribution.

## Batch 9 — account-scoped sandbox trading

- Authenticated owned-account catalog for the Standard, Plus and Pro switcher.
- Account-ID-scoped order placement, listing, lookup, modification and
  cancellation with per-account idempotency.
- Account-ID-scoped holdings, trades, ledger, valuation, StoxScore and portfolio
  insights.
- Wrong-owner account and order IDs return `404`; inactive sandboxes are
  read-only and reject new or modified orders.
- Legacy market-scoped APIs remain standard-only for backward compatibility.
- Sandbox orders do not award standard onboarding/progression milestones or
  generate feedback against the wrong portfolio.
- Persistent responsive switchers label competitive and non-ranked contexts on
  both the dashboard and detailed portfolio view.
- No database migration, billing provider, secret or new market-data use.

## Batch 10 — premium capability enforcement and Pro portfolios

- Backend-owned feature requirements distinguish active plan access from UI
  visibility, beginning with the Pro-only multiple-portfolio capability.
- Active Pro learners can provision slots 2–5; Free, Plus and inactive plans
  are rejected by the authenticated API.
- Server-owned slot allocation and ₹1 crore capital prevent request tampering.
- Required idempotency keys and subscription row locking make retries and
  concurrent requests safe while the database enforces the five-slot limit.
- Plus and Pro slot identities are separated so plan transitions preserve
  locked portfolio history without weakening standard-account uniqueness.
- Provider entitlement refreshes reactivate every sandbox for the current plan
  and never attempt to provision a sandbox for Free.
- Settings shows authoritative capacity and creation status; every sandbox
  remains visibly excluded from standard rankings.
- Billing and public plan mutation remain disabled. No new secret, paid service
  or market-data use is introduced.

## Batch 11 — campus institution verification foundation

- Verified-email learners can submit one pending institution request with an
  official domain and optional HTTPS website.
- A default-deny `USER`/`ADMIN` platform role is enforced from the database on
  every moderation request; no browser or public API can promote an account.
- Administrators can review a bounded pending queue, approve requests or reject
  them with a required reason.
- Approval atomically creates a unique verified institution and scoped
  `ORGANIZER` membership for the requester.
- Institution names, domains, user memberships and pending requests have
  database uniqueness constraints; review transitions use row locking.
- Review actions create account audit events and aggregate metrics without
  logging submitted personal data.
- Campus request and membership data is included in account export and follows
  account-deletion cascades while historical approvals survive reviewer deletion.
- The competitions page exposes learner application status and an administrator
  moderation queue without changing any leaderboard or portfolio query.
- No new secret, provider, paid service or market-data use is introduced.

## Next batch

Add institution-scoped organizer controls, student membership requests and
campus competition creation/standings using only the existing standard ₹5 lakh
season entry. Paid checkout remains deferred until a billing provider and
webhook operating model are explicitly approved; market-data-dependent premium
analytics remain behind the documented provider-permission gate.
