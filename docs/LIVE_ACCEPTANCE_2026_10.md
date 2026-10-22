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
| Paper order | A one-share RELIANCE buy queued while NSE was closed; cancellation removed it and released the blocked funds. | No new execution was observed. Existing first-trade completion cannot prove fresh-user execution guidance. No test order remains open. |
| Finwiz | One real response rendered without raw Markdown clutter. Journey progress advanced from 3/6 to 4/6. | Response ended mid-sentence, investigated below. |
| Scenario Lab | Preset selection updated the preview. With no remaining credits, Run stayed disabled and the plan link was shown. | No scenario run occurred; do not grant entitlements to bypass this gate. |
| Learning Path / Competitions | Learning Path navigation reached its loading state. | Completion, competitions and daily-streak display remain unverified. On resuming on 22 October, the app reported an expired/revoked refresh token; a fresh sign-in is required. |
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
