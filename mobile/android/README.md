# StoxSim native Android

The Android Studio project is in `project/`. Current version: **0.5.7 (versionCode 13)**. The app replaces the previous
Trusted Web Activity launcher with Kotlin and Jetpack Compose. The app's main
screens run natively and call `https://api.stoxsim.com` directly. Chrome is not
needed for the main trading, portfolio and learning screens.
The application ID remains `com.stoxsim.app`; launcher artwork is retained.

## Website-aligned native experience

The UI follows the colours in `frontend/app/globals.css` / `theme.css`, uses the
same brand artwork and section names, and defaults to light appearance like the
website. Dark appearance is an explicit per-user device preference. Native bottom
navigation provides Stock, Finwiz AI, Scenario Lab and Competition, with stock-chart,
brain, lab-flask and trophy icons. Stock has Explore,
Holdings, Orders and Watchlist tabs. Global stock/ETF search sits at the top; the
account avatar opens market, appearance, wallet, subscription and security controls. Cards, charts and forms adapt to a phone rather than copying
the desktop columns.

- Login, consent-based registration, password-reset requests, encrypted session
  restoration/rotation, sign-out and session expiry.
- India/US markets, index snapshots, movers, symbol/company search, stock detail,
  historical price charts, company fundamentals and quarterly/yearly financials.
- Foreground-only authenticated quote streaming, connection/freshness indicators,
  reconnects, and manual refresh. Backgrounding/sign-out closes the connection.
- Watchlist add/remove, standard/sandbox account selection, locked-account states.
- Reviewed market/limit buy/sell orders, simulated charge estimates, open-order
  cancellation, order history, executed trades and cash ledger.
- Account valuation and holdings, StoxScore, allocation/attribution, daily history,
  benchmarks/drawdown/risk metrics where the existing plan and data permit them.
- Eight Finwiz lesson modules and session chat history with question bubbles,
  bounded follow-up context and explicit Send. Local read markers do not award XP.
- Scenario Lab presets/custom paths and server credit balances; Finwiz questions;
  learning path/automatic daily streaks/achievements; global and private-league competitions.
- Campus discovery, membership/verification requests, competition enrollment and
  standings, organizer competition creation and membership-request review.
- Profile/email/password controls, verification resend, active-session revocation,
  logout-all, native account deletion, user-selected account export, weekly reports
  and email preferences, and existing plan/sandbox benefits.

This native build includes scoped email verification/reset links with safe browser
fallback and campus moderation controls. Payments/Play Billing are deferred and
purchase buttons are unavailable. Platform analytics and billing administration
remain web-only. See [the parity map](../../docs/ANDROID_WEB_PARITY.md) and
[the current closure register](../../docs/NON_PAYMENT_RELEASE_CLOSURE.md).

## Open and test in Android Studio

From your repository terminal:

```bash
git fetch origin
git switch main
git pull --ff-only
```

Open `mobile/android/project`, allow Gradle sync, then press Run. Use JDK 17 or
21 as the **Gradle JDK**, rather than JDK 25. Install Android SDK Platform 36 and
Build Tools 35.0.0 when prompted. The included Gradle wrapper is 8.11.1; AGP 8.10.1
supports API 36. Minimum Android version is API 28.

Your previous debug installation can update in place if you use the same debug
signing key. It will require a fresh native sign-in because browser sessions are
not imported. If installation reports a signing mismatch, uninstall the old test
app and reinstall; the account and virtual portfolio remain on the server.
Do not replace or commit a private release keystore to resolve a debug mismatch.

Commands (Windows: use `gradlew.bat` instead of `./gradlew`):

```bash
cd mobile/android/project
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
# With a connected emulator or device:
./gradlew :app:connectedDebugAndroidTest
# Compile and shrink the unsigned release candidate; this does not publish it:
./gradlew :app:assembleRelease
```

The Android workflow builds/tests/lints the app and runs synthetic UI/Keystore
checks on an emulator. It uses no production account or signing credentials.

### Device checklist

1. Confirm launch shows the native welcome screen without Chrome navigation.
2. Log in with your existing account; check Home balances against the website.
3. Close the app completely and reopen; confirm the session restores.
4. Search an NSE stock and ETF (and a US stock in US mode); unsupported market availability must
   show the API's error rather than fabricated results.
5. Open Portfolio, change markets, refresh and compare holdings/valuation.
6. Test airplane mode on startup and while signed in; retry after reconnecting.
7. Sign out, reopen, and confirm private account/portfolio data is gone.
8. Revoke the Android session from web settings; the next authenticated request
   must refresh/reject and return to sign-in.
9. Check dark mode, rotation, larger font settings, keyboard visibility and Back.
10. Test registration only if public registration is enabled. Follow password
    reset/email verification links in the native app and browser fallback; confirm
    reset signs out existing sessions. Check Inbox and Spam/Junk after sending mail.

## Session boundary

The server omits refresh tokens from the auth JSON and issues a secure, HTTP-only
`stoxsim_refresh` cookie. The native client extracts only that host-only cookie
from successful login/register/refresh responses and encrypts its value with
AES-GCM under an Android Keystore key. It sends it back through the server's
existing JSON refresh/logout contract. Only the session inventory request attaches
that cookie to identify this device, under the same API origin/auth path. No generic cookie jar or browser bridge is
used. Access tokens stay in memory; passwords, account responses and portfolio
responses are not saved to disk or instance state, apart from an explicit user
export through the Android document picker. Appearance preferences are local. Backup and device transfer
are excluded, cleartext traffic is disabled, and redirects/automatic transport
retries/logging are disabled.

API calls are serialized around refresh rotation and sign-out. A 401 triggers at
most one refresh/retry for safe GET requests; mutations are not automatically
replayed. Transient refresh failures retain the encrypted token for reconnecting.
Sign-out clears local secrets even if remote revocation fails and reports that
failure. Key invalidation/corrupt ciphertext requires a fresh sign-in.

No provider, backend, Play service-account or signing secrets belong in this app.
Release signing stays in Android Studio/protected CI secrets. Existing asset-link
utilities and `twa-manifest.json` are retained as historical website-association
configuration; regenerating the old Bubblewrap shell would overwrite native work.
Digital Asset Links are required for verified email-link routing. Scoped handlers
exist; publish the actual Play app-signing certificate (not the upload certificate)
using `configure-asset-links.mjs`, then test the signed installation.

Read [the paid launch plan](../../docs/ANDROID_PLAYSTORE_LAUNCH.md) before release.

See [product UI mapping](../../docs/ANDROID_PRODUCT_UI.md) for the new navigation and chat behaviour.

0.4.1 fixes watchlist additions using the server market/exchange/symbol contract and
adds metric cards, quarterly/yearly financial bar charts and a short expandable company
description. See `../../docs/ANDROID_FUNDAMENTALS_UI.md` for validation and device checks.

## 0.5.0 UI upgrade

Holdings now has a current-value summary, invested/unrealized return, compact stock
rows, search/sorting and a dedicated portfolio-analysis screen. Cash and account P/L
stay separate from open holdings. Finwiz uses a brain icon and an accessible reactor
with connected module buttons; its composer starts a topic chat only after Send.
Scenario Lab has green/purple preset cards, a free price-path preview and custom
step controls. Run still uses the existing server contract and spends one credit.
Launch uses the StoxSim mark, a short native splash exit and honest restore/retry states.
See [the upgrade notes](../../docs/ANDROID_UI_0_5.md) for acceptance and screenshot evidence.

## 0.5.3 completion

Pending limit-order edits, campus moderation/member controls and a separate Pro
leaderboard are native. Build/unit/lint CI also compiles the AAB. Signed release
preparation and unresolved external acceptance are documented in
[MEDIUM_LOW_COMPLETION.md](../../docs/MEDIUM_LOW_COMPLETION.md). No live purchases
are enabled. Current website clients also record daily visits automatically.

Activity and native email link validation: [contract](../../docs/ANDROID_ENGAGEMENT_FINWIZ_LINKS.md).

## 0.5.4 search

- Suggestions appear after 2 characters and a 300 ms pause, without pressing Search.
- India search shows active NSE stocks and ETFs only. BSE listings, indices, debt and derivative series are excluded.
- Exact ticker matches and symbol/company prefixes rank first; `RIL` resolves to `RELIANCE`, and spaced names such as `HDFC Bank` match.
- Clearing or changing the query, opening a result, switching accounts, leaving the screen or signing out invalidates pending suggestions.
- Deploy the backend migration with this release to quarantine legacy debt records and correct ETF classifications. A full instrument sync applies provider series validation to the remaining catalogue.

## Current release preparation

Use the [non-payment closure checklist](../../docs/NON_PAYMENT_RELEASE_CLOSURE.md)
and [store listing draft](STORE_LISTING_DRAFT.md). The main branch contains merged
code; to test a pending PR, check out its branch instead before opening Android Studio.
Android 0.5.7 records stock research only after a successful quote load. Selecting a
suggestion or failing to load a quote does not complete the analytics research step.
Backend V124 adds daily server-recorded feature engagement for both web and Android.
