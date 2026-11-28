# Referral rewards

A verified learner can create one invite link from **Account → Referral rewards** on the web or **More → Rewards** on Android. The Android App Link uses the existing `https://stoxsim.com` association and opens web registration when the app cannot handle it. Email and Google registration attach the code during account creation. Sign-in and clicks never create attributions.

## Qualification and activation

Keep the production feature disabled until the owner approves the eligibility settings and separate non-competitive accounts. The defaults require a newly referred ordinary account, verified email, a minimum age of 24 hours and a standard-account paper trade executed after verification and within 90 days of signup. A verified platform administrator reviews each qualified referral. Signup is rate-limited, and the program applies per-inviter caps and duplicate-identity checks. Gmail dot, plus and googlemail aliases are matched. Keyed email and Google identifier digests expire after 180 days and can block deleted-account reuse. An administrator should review unusual referral patterns and other evidence available through existing account operations before approving a batch.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `STOXSIM_REWARDS_ENABLED` | `false` | Accept new attributions, create codes, qualify referrals and issue approvals |
| `STOXSIM_REWARDS_IDENTITY_KEY` | Empty | Stable, independent secret of at least 32 characters for keyed identity checks. Required when enabled |
| `STOXSIM_REFERRAL_MINIMUM_AGE_HOURS` | `24` | Minimum account age at qualification and approval |
| `STOXSIM_REFERRAL_QUALIFICATION_DAYS` | `90` | Window to qualify a pending signup |
| `STOXSIM_REFERRAL_DAILY_ATTRIBUTION_LIMIT` | `20` | New attributions per inviter per UTC day |
| `STOXSIM_REFERRAL_WEEKLY_REWARD_LIMIT` | `5` | Rewards per inviter in any seven-day window |

Supply the key via the protected backend environment and retain it during a pause. Rotating it invalidates matching against previous identity holds. A pause preserves recorded balances, history, rejection and corrections.

## Reward accounting

An approval posts ₹1,00,000 and $1,000 to dedicated INR and USD Reward practice accounts. The accounts use the normal paper-trade engine. Each posting increases cash and contributed capital by the same amount. Trading profit uses equity less contributed capital. Realised and unrealised P&L come from trading and holdings. Historical charts treat contributions and corrections as cash-flow breaks and suppress normalized returns, benchmark comparisons and drawdown across those breaks.

StoxScore calculates holding weights within the selected portfolio, so a cash grant alone leaves its score unchanged. Standard-account results continue to feed onboarding, competitions, cohort milestones and standard trade analytics. Rewards practice accounts are marked ineligible for rankings and operate independently of Plus and Pro sandbox slots and entitlements. The balances can only fund simulated trading within their own practice account.

The append-only reward journal requires both currencies in each transaction and stores the source, source identifier, timestamp and reviewer. The unique source reference and approval row locks prevent repeated posting. The schema reserves an additional source for a future rewarded-ad implementation.

## Review and reconciliation

A verified platform administrator can approve or reject qualified signups on the web Rewards page. The same page offers a ledger check. It compares each active reward account’s contributed capital to its signed journal entries and checks that rewarded referrals have corresponding grants. Both counters should be zero.

For a correction, send an authenticated administrator request to `POST /api/v1/rewards/review/{referralId}/reverse`. This creates one compensating transaction linked to the original and leaves the grant recorded. Both wallets must have enough unreserved virtual cash. A partial correction rolls back. Retrying a successful approval or reversal returns its existing transaction ID. Use `GET /api/v1/rewards/reconciliation` for an administrator-only reconciliation report. A failed check calls for investigating the original transactions and balances before any correction.

Before activation, exercise email and Google signup on web and on an Android build whose signing certificate is published in the domain association. Verify a new learner, execute an eligible standard paper trade, review the referral, check the two reward accounts and run reconciliation.
