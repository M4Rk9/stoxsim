# Live acceptance follow-up — 21–22 October 2026

Candidate deployed: PR #167, main commit
`cfd4eafd675214f596871ecbc96b01a7cec22f0a`.
Deployment: https://github.com/M4Rk9/stoxsim/actions/runs/39601699501.
This record distinguishes observed production behavior from automated fixtures.
No account identifiers, credentials, email tokens or portfolio details belong here.

## Web observations

| Check | Observed result | Limit |
| --- | --- | --- |
| Public home, status and privacy | Home rendered; status reported operational; privacy included feature engagement markers. | Point-in-time observation, not an uptime guarantee. |
| Sign-in and session restoration | Signed in through the normal form; page reload restored the session. | An existing account was used. Fresh registration/verification remains untested. |
| NSE search | `relian` suggested RELIANCE first with NSE equity matches; `niftybees` returned the NSE ETF. RELIANCE quote and historical chart loaded. | Does not exhaustively verify every listing. |
| Watchlist | Added and removed the test RELIANCE entry. | Existing entries were preserved. |
| Paper order | On 21 October a one-share RELIANCE buy queued while NSE was closed; cancellation removed it and released the blocked funds. On 22 October, during LIVE NSE status, a one-share buy executed, created a holding and showed post-trade Finwiz feedback. A sell of that test share executed, returned holdings to the original five and left zero open orders/blocked funds. | The round trip used virtual funds and left a net virtual cash decrease of 4.46 from execution prices and simulated charges. Trade history remains. Existing first-trade completion cannot prove fresh-user guidance advancement. |
| Finwiz | One real response rendered without raw Markdown clutter. Journey progress advanced from 3/6 to 4/6. | Response ended mid-sentence, investigated below. |
| Scenario Lab | Preset selection updated the preview. With no remaining credits, Run stayed disabled and the plan link was shown. | No scenario run occurred; do not grant entitlements to bypass this gate. |
| Learning Path / Competitions | Both loaded on 22 October; missions, achievements, season and standings displayed. The guide reached 6/6 explored. The Learning Path showed a two-day streak and today's visit recorded without a manual check-in. | The account already had executed-trade and Scenario Lab completion and was already enrolled in the season. No enrollment was performed. An initial refresh-token error cleared when the dashboard restored the session; its cause was not established. |
| Analytics | Feature progress was visible in the learner journey. | Owner event counts were not inspected. The account's test exclusion status was not verified. Journey progress is not proof of analytics counts. |

## Finwiz defect and follow-up

The live Basic answer ended with `If you make 20 trades` under a practice-question
heading. The provider payload was not inspected, so its actual finish reason is
unknown. Source inspection found that any returned text was treated as success,
regardless of `finishReason`, and that all candidate alternatives were joined.
The general prompt also requested five sections even for the 350-token Basic tier.

The follow-up accepts only one completed `STOP` candidate, skips thought parts,
and uses a transparent, tier-bounded educational fallback when no complete answer
exists. It makes no retry or extra provider call. Shorter depth-specific prompts
reserve the practice exercise for Full. Existing token/character/entitlement
ceilings remain unchanged. The fallback is a general lesson, not a claim to have
answered every part of a custom question.

Provider contract: https://ai.google.dev/api/generate-content#FinishReason.
Regression tests cover MAX_TOKENS at every tier, unknown/missing and blocked
finish reasons, empty/thought-only output, candidate isolation, no retries and
fallback observability. Live acceptance of this fix must follow its deployment.

## Android evidence and remaining device acceptance

Main Android CI: https://github.com/M4Rk9/stoxsim/actions/runs/39598928861.
The emulator job completed 54 tests on Android 15. This is automated evidence;
it does not complete physical-device or signed distribution acceptance.
Reviewed emulator screenshots included compact/large-text journey, Finwiz and
stock search. Normal CI publishes reports, not an installable APK artifact.

The available workspace has no attached Android device, ADB, emulator or Android
SDK. Use Android 0.5.7/versionCode 13 (or the subsequent reviewed build), following
`mobile/android/README.md`, and record device, OS, build SHA and results for:

1. Sign-in, restart/session restoration and automatic daily streak.
2. NSE stock/ETF search, quote loading and one executed paper trade during an
   open supported session; pending/cancelled orders must not complete the step.
3. Finwiz, a Scenario Lab run on an eligible account, Learning Path and optional
   Competitions exploration; verify navigation and persisted journey progress.
4. Offline/retry, account isolation, large text and reachable controls.
5. Actual verification/reset email links with the signed app installed (cold and
   warm), browser fallback and used/expired links. The user must enter any new
   password. No real email-link completion has yet been observed in this session.

Payments and VPS-equivalent load testing remain explicitly deferred. This record
does not close backup recovery, Play signing/review, or other operational gates in
`NON_PAYMENT_RELEASE_CLOSURE.md`.
