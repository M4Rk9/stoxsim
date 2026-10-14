# Android UI upgrade — 0.5.0

This package interprets the owner's Groww holdings/analysis references and dark
course-app reference as native StoxSim screens. It retains StoxSim's own mark,
four product tabs, account boundaries and existing API contracts.

## What changed

- **Holdings:** current open-position value, invested cost and unrealized return;
  separate cash/account P/L cards; compact tappable rows, search and name/value/return
  sorting. The analysis button opens performance, allocation and StoxScore sections.
- **Scenario Lab:** green/purple hero, selectable server-catalogue cards, cumulative
  price-path preview, custom step slider and ±5%/add/remove controls. Editing or
  previewing makes no request. Only Run uses the server and its one-credit cost.
- **Finwiz:** brain icon, monochrome arc reactor with eight connected topic buttons,
  selected-module summary and Read action, plus a question composer underneath.
  Selecting topics makes no AI request. Send creates a conversation for the selected
  topic and invokes the existing bounded-context request flow once.
- **Shared UI:** neutral phone surfaces, consistent rounded panels, clearer type,
  pill stock tabs, quieter bottom navigation and visible product entries on Explore.
- **Launch:** branded starting window on API 28–30; masked-safe StoxSim mark and a
  short native exit on API 31+. Actual restore/retry uses a branded screen. No fixed
  delay, extra splash activity, WebView, or network work is added to a fresh sign-in.
  The last chosen appearance is used during restore; per-user appearance remains
  authoritative after authentication. System-bar contrast follows the app theme.

## Data and interaction boundaries

The holdings number sums server-valued positions; it excludes cash. Holdings return
uses the supplied unrealized P/L and invested cost. Account P/L remains the server's
total account P/L. Unavailable data remains unavailable. No unsupported 1D return,
XIRR, comparison curve or company logo is invented to match the reference.

Charts show the existing observed history/benchmark and preserve gaps, cash-flow
breaks and plan restrictions. Scenario previews show cumulative input shocks,
not predicted returns or portfolio balances; server results remain authoritative.
Custom paths retain the 1–12-step, −100%…100% and two-decimal limits. Repeated
explicit retries retain the ViewModel's existing idempotency behavior.

Finwiz drafts and conversations stay in memory. The question limit, account/market
scope, explicit Send, server entitlements and existing session handling apply.
Passwords stay out of saved instance state. No provider or signing credentials,
production data, dependency or database changes are part of this package.

## Verification

The Android workflow compiles debug, release and test APKs, runs JVM tests and lint,
then runs the emulator suite. `PremiumUiTest` covers holdings accounting/display,
search-to-stock navigation, reactor/composer callbacks, free scenario edits versus
explicit Run, invalid inputs, zero credits, large-text module reachability and
restore retry. Tests use synthetic data and callback fakes only.

`VisualSmokeTest` captures real Compose screens in light/dark appearance, including
holdings, portfolio analysis, Scenario Lab and Finwiz. Screenshots and connected-test
reports are in the workflow's `android-device-reports` artifact. They are evidence
of the native implementation, not generated design mockups.

Owner acceptance:

1. Fetch `fix/android-bottom-navigation`, sync `mobile/android/project`, then Run
   with JDK 17 and Android SDK Platform 36/Build Tools 35.0.0. Check version 0.5.0.
2. Cold-launch, reconnect offline, retry and rotate. Check light/dark appearance,
   system-bar contrast, keyboard insets and a narrow phone with larger text.
3. Compare holdings current value, invested cost and P/L with the website. Search,
   sort and open a holding; inspect the separate performance/allocation/score view.
4. Select each Finwiz module, read a lesson and Send one intentional question.
   Verify topic selection/read alone submits nothing; the correct topic is retained.
5. Choose every Scenario preset, build a custom path and move its controls. Verify
   only an intentional Run spends a credit; inspect steps/assumptions and retry errors.
6. Check all four bottom tabs, Back from portfolio analysis, account controls and
   existing order review. The package does not publish an APK to Play or change the
   backend, billing, growth metrics or market-data permissions.
