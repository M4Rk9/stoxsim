# StoxSim redesign — M4 release readiness

## Candidate and evidence

- M1 #200, M2 #202, M3 #203 merged; owner reports M3 deployed. M4 PR #204 targets main.
- Baseline M3 head `0af83e5`: [Android native](https://github.com/M4Rk9/stoxsim/actions/runs/45802541751) 71/71 instrumentation tests, build/unit/lint passed; [CI](https://github.com/M4Rk9/stoxsim/actions/runs/45802541761) passed. Prior screenshot evidence: Android artifact 15107393952 (51 PNGs); web artifact 15108427867 (93 PNGs). Thumbnail audit found native number wrapping and large-font tabs plus short-height web Finwiz overlap; fixes in this PR require fresh verification.
- Baseline CI candidate artifact 15108118591: 240 requests, 0 errors, history p95 8.78 ms, portfolio p95 11.75 ms; 62 public tables compared in successful isolated restore. Not production load/restore evidence.

## M4 implementation

Account-scoped executed equity/ETF trade history, filtering and client pagination, readable trade charges, account currency independent of analytics history; native compact financial values, accessibility navigation and isolated chart points; web Finwiz overflow; standalone frontend container. Backend contracts, entitlement gates, trading validation and security model unchanged.

## Release gate matrix

| Gate | Evidence / remaining action |
| --- | --- |
| Web and backend build, unit/integration and acceptance | Require latest PR CI; existing suites include market data, orders, authorization, entitlements, Google/auth, billing, legal, analytics and migration checks. |
| Native debug/release build, lint, emulator and keystore | Require latest Android workflow and new screenshots; verify all 72 device tests if suite count unchanged except new test. |
| Visual/accessibility | Reviewed prior 144 captures at thumbnail resolution. Recheck changed states and inspect remaining uncovered secondary screens, keyboard/focus/large text, both themes and native glyphs on actual devices. |
| Functional parity | Core shared backend routes retained; manually compare secondary screens and supported feature entitlements. Do not treat test coverage as proof of every route. |
| Size/runtime | Compare old/new frontend image sizes, CI resources/latency and APK/AAB size; measure VPS disk/RAM/CPU under bounded load. No production capacity claim. Font subsetting/compression remains unmeasured; original licensed assets retained. |
| Production config and operations | Follow docs/RELEASE_CHECKLIST.md and existing deployment runbook; verify provider permissions/flags, current legal/SEO metadata, mail delivery, actual Google OAuth, monitoring, backups, rollback image/bundle, and production smoke. |
| Play distribution | Require protected signing workflow and verified signed AAB, version code, signing fingerprint and controlled track smoke; do not distribute unsigned CI bundle. |

## Deployment and rollback

Only the owner may authorize merge/deploy. Use existing production workflow with immutable candidate SHA; retain previous healthy image and deployment bundle. Run existing production smoke and controlled acceptance checklist. If core journeys regress, use existing rollback procedure and verify health, authentication and account balances on restored version. Do not perform live destructive restore or change provider flags as part of this PR.

## Decision

**HOLD for fresh candidate CI and operational evidence.** This is a reviewable implementation candidate, not a production or Play release approval. Record final candidate SHA, workflow URLs, visual review findings, signed build evidence and operator sign-off here or in a private release ticket before declaring M4 complete. Do not publish credentials or user data.

### Startup regression follow-up
CI run 45830318415 failed at stack readiness; the frontend log reported Ready, but the original localhost health probe did not pass. Explicit IPv4 probes now match the standalone listener in CI, staging and production. New health diagnostics will capture probe output if the failure recurs. Do not approve the standalone runtime until fresh browser CI confirms readiness and acceptance.

### Owner-requested pre-merge web polish
The prior ca632ad candidate passed CI and Android checks. The final web-only typography restoration, official social footer links and independently scrollable Finwiz history are in a subsequent commit and must pass fresh CI and visual review before release. Android fonts and existing redesign colours/layout retained.

### Browser regression follow-up
Run 45836779173 had 185 passes, one repeatable narrow-navigation failure and one flaky chart test. The compact navigation and range-specific chart response handling have been corrected in this PR without reverting the original font or removing test assertions. Require fresh browser acceptance before merge.
