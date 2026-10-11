# Native Android / website parity

The website is the product reference. Version 0.3.0 implements its consumer
trading/learning flows as native Compose screens using the same Spring APIs,
accounts and entitlements. It does not embed a WebView or launch a TWA.

## Design mapping

| Website source | Native mapping |
| --- | --- |
| `globals.css` light tokens | Paper #f4f6f1; white panels; ink #17211d; muted #6d7c75; green #0b8f55; borders #dfe5de |
| `theme.css` dark tokens | Paper #121212; panels #1b1b1b; ink #f1f1f1; muted #a3a3a3; mint #00d09c; borders #303030 |
| `stoxsim-logo.png` and two-colour wordmark | Same logo resource and Stox/Sim colours |
| Panel/index/metric hierarchy | Outlined native cards, kicker labels, large values and status/time badges |
| Desktop header/account menu/tools | Phone bottom tabs plus account button and Explore destinations |
| Light default and explicit dark preference | Light default; choice remembered for this user's email on this device |

The app uses Android's sans-serif text and accessible native controls rather than
shipping desktop HTML/CSS. Charts and cards fit a single phone column; longer
range/topic selectors scroll horizontally. Appearance preferences are currently
local to each client, matching the website's existing local preference model.

## Feature mapping

| Web feature | Native screen / contract | Status |
| --- | --- | --- |
| Auth, registration consent, recovery request | Native auth; existing `/auth` endpoints | Implemented |
| India/US account and standard/sandbox selection | Header/account selector; `/accounts` | Implemented; locked accounts remain visible |
| Balances and valuation | Home/Portfolio; `/accounts/{id}/portfolio` | Implemented; same account currency/status/time |
| Indices/movers | Home/Markets; `/market/status`, `/market/indices`, `/market/movers` | Implemented |
| Stock discovery, quote/history/fundamentals | Markets/stock screen; `/instruments` endpoints | Implemented; absent values stay absent |
| Live quotes | Authenticated `/ws/market` STOMP quote subscription | Implemented; foreground only, one subscription, reconnects |
| Watchlist | Markets/Explore; default watchlist GET/POST/DELETE | Implemented |
| Market/limit buy/sell, charges | Reviewed native order ticket; account-scoped orders and charge estimate | Implemented; whole shares and positive limit validation |
| Orders/trades/ledger/cancel | Orders; account-scoped endpoints | Implemented |
| Holdings/StoxScore/allocation/P&L attribution | Portfolio; analytics/insights endpoints | Implemented |
| Daily history/benchmark/drawdown/risk | Portfolio history; 30/90/365-day endpoint | Implemented; plan restrictions/data gaps respected |
| Scenario Lab presets/custom paths | Explore/Lab; scenarios/catalog/credits/account run | Implemented; 1–12 cumulative shocks, existing credit balance |
| Finwiz topics/stock questions | Explore/Finwiz; existing ask endpoint | Implemented; server service limits apply |
| XP/challenges/check-in/achievements | Explore/Learning path; progression endpoints | Implemented |
| Global leaderboard/private leagues | Explore/Competitions; current season and league endpoints | Implemented; create/join/rotate/leave/delete |
| Campus membership/verification and competition participation | Explore/Campus; existing campus endpoints | Implemented |
| Campus organizer create/review | Campus workspace | Implemented |
| Profile/security/delete/export | Account; existing authenticated controls | Implemented; destructive operations require in-app review |
| Weekly email preferences/report history/preview | Explore/Account/Weekly reports | Implemented |
| Existing plan benefits/additional sandbox | Account; subscription/provisioning endpoints | Implemented; server enforces eligibility |
| Purchasing/renewal/restoring Play subscriptions | Android Play Billing plus verified backend purchase lifecycle | Not implemented; separate A3 milestone |
| Email verification/reset links | Email link opens existing website; return to app and sign in/refresh | Native link completion pending |
| Remaining campus role/moderation/admin controls | Website organizer/admin pages | Pending native controls |
| Platform-admin analytics/billing operations | Website admin pages | Remain web-only |

## Write and session behaviour

The server remains the authority for holdings, cash, order execution, competition
scores, credits and entitlements. No real-money trading or provider credentials
are introduced. Transport refuses redirects/automatic retries. GETs can rotate an
expired session and retry once; writes never replay automatically. Writes rotate
an almost-expired access token beforehand. A rejected write reconnects the session
for the next explicit attempt without resubmitting the operation.

The ViewModel retains order/scenario/provisioning request keys after ambiguous
network failures for identical explicit retries. An order review identifies its
account, symbol, quantity, side and price/type. Check order history after a network
interruption or process restart before starting a different order. Request keys
are not persisted across process death. Order outcome/credits/balances are read
from the backend; no optimistic cash/position changes are fabricated.

Streams send only a CONNECT and one SUBSCRIBE for the allowed quote topic. Frame
size, header counts/lengths and body length are bounded; malformed frames close the
connection. No SEND frames, secrets in logs, background streaming, or generic
browser-cookie bridge. Out-of-order/future quote marks are rejected and stale
marks retain explicit price status. Portfolio accounting refreshes through the
backend rather than recalculating account balances from stream ticks.

## Validation and device acceptance

Run the build/unit/lint/release and device tests in `mobile/android/README.md`.
The Android workflow uses synthetic data only and captures light/dark screenshots
in `android-device-reports`. It makes no production orders, AI requests, purchases
or credit-consuming scenario runs. Automated tests cannot certify live providers,
account-specific plan eligibility, actual email delivery or production billing.

Owner device checklist:

1. Update the branch, sync Gradle and run with the same debug key; verify version
   0.3.0, native navigation and that no Chrome chrome appears in core screens.
2. Compare one India and one US account against the website. Select each sandbox;
   verify its account label, currency, balances/holdings and locked state.
3. Check light/dark appearance, rotation, Back, keyboard, narrow screens and large
   font sizes. The theme should read as StoxSim, not an unrelated Material app.
4. Search a stock; inspect quote status/time, chart ranges, fundamentals, watchlist
   add/remove. Background/resume, then disconnect/reconnect the network; streaming
   must stop in the background and reconnect without pretending stale marks are live.
5. Place a small virtual-money buy and sell; review the account and estimates;
   compare execution/charges, orders/trades/ledger and holdings with the website.
   Try a limit order and cancel it; inspect reserves before and after cancellation.
6. Inspect history/score/allocation/attribution. Check plan-gated ranges and missing
   observations; never expect a continuous line across unavailable data.
7. Run one scenario only when intentionally accepting its credit cost; compare
   result/remaining credits on the website. Ask Finwiz, check in, view challenges,
   opt into a competition, and create/join a private league where eligible.
8. Exercise campus requests/participation only with test institutional data and
   eligible roles. Confirm failed/denied actions do not change membership or scores.
9. Change profile/appearance/report preference, request verification, view reports,
   and save an account export to a chosen location. Test password/email change,
   session revocation/logout-all and account deletion only on a disposable account.
10. Do not mark this Play-release ready until A3–A5 and the outstanding parity items
    are complete and reviewed.
