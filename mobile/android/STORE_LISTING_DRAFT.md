# Non-payment Android listing draft

Review against the signed build before submission. This is copy and an asset
inventory, not evidence of Play Console completion or approval.

## App name

StoxSim

## Short description

Practise stock trading with virtual money and learn how markets work.

## Full description

Build your stock-market knowledge through practice with StoxSim.

Explore supported stocks and ETFs, follow a watchlist and place paper trades with
virtual money. Review simulated charges, orders, holdings and portfolio performance.
A guided journey helps you move from your first executed practice trade to learning
with Finwiz AI, exploring Scenario Lab and discovering missions and competitions.

- Learn concepts with Finwiz AI lessons and educational answers.
- Explore hypothetical portfolio changes in Scenario Lab, within available credits.
- Follow Learning Path missions and build an automatic daily visit streak.
- Choose whether to join supported competitions and private leagues.
- Manage your account, sessions and data through native account controls.

StoxSim is an educational simulator. It does not accept trading deposits, hold
securities or place real exchange orders. Simulated results do not predict real
returns. Market data availability varies; fallback virtual prices are labelled.
Finwiz answers are educational and may be inaccurate. Feature access depends on
existing account entitlements. Purchases are unavailable in this build.

## Asset and review inventory

| Item | Prepared source | Before upload |
| --- | --- | --- |
| App icon | `project/store_icon.png`; native launcher assets | Preview actual exported icon and confirm Console requirements |
| Screenshots | Native CI captures under `native-review/` in the device report artifact | Capture/review the signed candidate with test data, no email tokens or private balances; choose representative home/search/Finwiz/scenario screens |
| Feature graphic | `store/feature-graphic.svg`, rendered to opaque 1024×500 PNG | Review rendered copy against the candidate and current Console requirements |
| Privacy notice | https://stoxsim.com/privacy | Check live URL and actual data practices |
| Account deletion | https://stoxsim.com/delete-account | Verify live instructions and native deletion flow |
| Support | support.stoxsim@gmail.com | Confirm monitored mailbox |
| Reviewer access / Data safety / financial declarations | Existing launch checklist | Owner supplies actual Console answers/access; do not infer them from this draft |

No paid-plan pricing, purchase links, guaranteed returns, cash prizes or claim of
real brokerage trading belongs in this non-payment listing. Any later billing
release needs its own product terms, purchase verification and store review.

## Prepare the review assets

After `npm ci` in `frontend`, run from the repository root:

```bash
node mobile/android/render-store-assets.mjs
```

The output is in `mobile/android/generated/store-assets/`: feature graphic PNG,
512×512 icon PNG and this listing draft. CI publishes the same files in the
`android-store-review-assets-<candidate SHA>` artifact. Artwork is promotional
copy, not a screenshot of the app. Screenshot selection and final Console review
remain manual; no signing credential or upload permission is used by this script.
