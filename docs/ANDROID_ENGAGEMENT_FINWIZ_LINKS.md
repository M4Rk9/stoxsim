# Android engagement, Finwiz depth and email links — 16 October 2026

## Email guidance

Registration, password recovery, email changes/resends, account settings and weekly
report settings tell learners to check Inbox and Spam/Junk for StoxSim emails and
mark misplaced mail as not spam. Password recovery keeps its generic response.
This is user guidance; it does not claim to repair sender authentication or guarantee
inbox delivery. Email verification is still required.

## Android activity (M2)

Android 0.5.3 sends only version-1 ACTIVE and STOCK_OPENED to the existing
`POST /api/v1/analytics/events`. A stock open means opening the native stock detail
screen, not typing a search or receiving a background quote. Foreground signed-in
visits count; no device ID, symbol, email, screen history or background service is
sent. The existing backend excludes administrators/controlled test accounts and
uniquely records user/event/UTC-day markers across both web and Android. Metrics
remain aggregate across clients, not separate app session counts.

Per-sign-in memory suppresses acknowledged events for that UTC day and retries
failed attempts at most every five minutes, while foreground. App restart may send
again; server uniqueness is authoritative. Analytics skips busy session operations,
uses existing unexpired access only, has a five-second timeout and never rotates
credentials, signs out a user or changes a trading result on failure. Each account
gets fresh in-memory state. No historical app activity is backfilled.

## Server-selected Finwiz depth

| Effective plan | Depth | Provider output cap | Response character cap | Suggested follow-ups |
| --- | --- | --- | --- | --- |
| Free | Basic: concise concepts and key limitations | 350 tokens | 1,800 | 2 |
| Plus | Expanded: worked numerical examples and interpretation mistakes | 650 tokens | 3,600 | 3 |
| Pro | Full: comparisons, sensitivity to assumptions and practice exercises | 900 tokens | 6,000 | 4 |

The deployment's existing FINWIZ_MAX_OUTPUT_TOKENS is a ceiling on every tier.
All topics remain available; depth does not authorize advice or new market data.
The authenticated user ID and fresh database subscription state determine depth on
every request. Inactive, missing-end or expired paid periods resolve to Basic.
Existing verified administrator test benefits work only during their permitted
window. No plan/tier claim from a request or JWT can upgrade access. The applied
`finwizTier` is returned with the answer; plan descriptions come from the backend.

Provider instructions and generation budgets differ by depth, and oversized output
is bounded server-side. Generated wording can vary; token/character caps are not
minimum lengths. The deterministic fallback also differs: Expanded adds a topic
specific worked example; Full adds a comparison/exercise. Safety, grounding and
missing-data constraints apply equally. Billing remains disabled.

## Native email actions (A2)

The existing HTTPS `/verify-email?token=...` and `/reset-password?token=...` links
on **stoxsim.com** are scoped Android App Links. Opening one, signed in or out,
shows a native confirmation/reset form. No token is consumed simply by opening a
link. Reset requires matching 8–72-character passwords of at most 72 UTF-8 bytes;
the server still enforces one-time purpose, expiry and session revocation.
Public completion requests contain no current account bearer/cookie and never
replay automatically. Successful reset clears the local session; verification
refreshes the current profile without assuming the link belongs to that profile.

Parsing rejects foreign hosts, HTTP, credentials, ports, other paths, duplicate
parameters, fragments, redirect parameters and malformed tokens. Tokens/passwords
remain in memory only, never saved state, analytics or preferences; the incoming
activity intent is scrubbed. A new incoming link resets the form. Process death
requires reopening the email. Expired/used links show recovery guidance.

When the app is absent or not verified as a handler, the same HTTPS link continues
on the website. The native form also offers an explicit browser chooser excluding
StoxSim to avoid a link loop; cancellation leaves the native form available.
Keep the existing assetlinks.json association and supply the actual distribution
app-signing certificate through `mobile/android/configure-asset-links.mjs` for Play
builds. Never substitute an upload certificate for Play's app-signing certificate
or publish a private key. Real mail-client/device App Link verification still needs
the matching signed build and deployed website association.

## Validation and remaining acceptance

Automated coverage exercises depth budgets/fallbacks, actual database plan changes,
forged JWT plan claims, expiry, test-benefit eligibility, email URL rejection,
public completion transport, analytics failure isolation, UTC day deduplication,
and native form confirmation/password rules. Browser coverage checks email guidance
and applied depth. No real messages, AI calls or purchases are needed by tests.

After deployment, use disposable accounts to verify Android activity in owner
analytics, real email links in a warm/cold app and browser, invalid/expired links,
network failure and device rotation. Validate the actual distribution signing
certificate association on-device. Payments and provider email configuration are
outside this PR.
