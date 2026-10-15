# Android Finwiz readability and automatic daily visits

Both inline answers and the full conversation use a native CommonMark renderer.
Headings, emphasis, lists and code remain selectable; tables become labeled rows
that wrap on phones. Model HTML is not executed, links are labels, and images are
alt text only. Ordinary user messages remain literal. Common existing LaTeX formula
wrappers are converted to readable text; the server also requests plain-text formulas
for new answers. This is not a complete TeX typesetter.

The redundant “Educational AI · Verify answers” composer text is removed. Existing
answer metadata/disclaimers are separate from the composer.

A signed-in foreground Android session records a visit through the existing
`POST /api/v1/progression/check-in`. Login, restored sessions and foreground returns
need no visit to Learning path and no button. A server-confirmed date/zone prevents
extra calls that day in the same session. A foreground timer notices midnight and
retries failed recording at most once per minute. It pauses during explicit user
operations and stops on background/sign-out. It does not send emails or award XP
locally; server locking, timezone rules, missing-day resets and unique mission
awards remain unchanged. Older clients and the website can still use manual check-in.
No historical visits are invented or backfilled.

Validation includes native inline/full-chat UI tests, Markdown/formula/table unit
tests, and scheduler tests for duplicate visits, timezones, midnight, transient
failure and new accounts. Server streak/XP tests remain in the backend suite.

On device, log in or restore, then open Learning path: today's visit should already
be recorded. Background/reopen on the same day without gaining another day. After
network loss, foreground use should retry; never show a successful visit before the
server acknowledges it. Check dark/light and large-font answers with selection.

Verification-email guidance is described in EMAIL_DELIVERABILITY.md. Inbox placement
requires evidence from the deployed sender; it is not solved by UI changes.
