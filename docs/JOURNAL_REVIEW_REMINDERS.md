# Scheduled journal review reminders

Extends the research → strategy → practice → review journey tracked in #190.
An uncompleted strategy with a review date in the inclusive UTC window today−29
through today can produce a generic notification linking to `/journal`. The
journal's due list still uses the viewer's local calendar, so the inbox reminder
can arrive on a different local date near midnight. Both editors explain this.

The existing notification scheduler observes up to 100 owners per poll (normally
once a minute). Each owner has at most 1,000 saved notes. It sends at most one
journal notification per owner per UTC day, not one notification per strategy.
There is no repeated daily nag for unchanged dates. Already overdue dates within
the window are eligible on rollout; older dates remain visible in the journal
without a historical notification backlog. This is best-effort, not an alarm.

## Privacy and controls

The existing Portfolio learning preference, labelled **Portfolio learning and
review reminders** on web and Android, controls these notifications. Defaults
remain unchanged. Existing optional-notification daily caps apply. Suppressed
events are consumed; enabling the preference does not replay those dates.
The title/body contain no strategy text, symbols, note IDs or performance data.
These reminders are inbox-only even if email/push preferences are enabled.
No SMTP/FCM calls, AI ingestion, analytics activity, or review completion is caused
by the scheduler. Only a user's existing explicit review action marks completion.

Opening the notification navigates to the owned journal on web/native Android.
It does not automatically open/share a note or place any trade. Historical inbox
items may remain after a review is completed, rescheduled or deleted; the journal
always shows current state.

## Persistence and concurrency

Additive V136 adds `decision_reflection.review_reminded_on` and a partial due
index. The watermark is operational metadata, not a user-authored journal field;
legacy writes and export payloads stay compatible. Deleting the note/account
removes the watermark with the existing row. Generic inbox entries follow normal
90-day retention and account-deletion cascades.

The scheduler locks the owner then selects current eligible notes with `FOR
UPDATE SKIP LOCKED`; active edits and deleted/completed/rescheduled notes cannot
be published from an earlier candidate snapshot. Other schedulers skip the locked
owner. Publication and watermark updates commit together. Failed transactions
roll back and retry on a later poll. The watermark survives dedupe-log retention.
A changed review date can become eligible again; returning to a previously used
date is treated as a newly scheduled intention, subject to daily limits.

## Verification and release

Integration tests cover due-window and UTC boundaries, future/cleared/completed/
deleted dates, privacy, no external delivery or fabricated activity, preference
suppression, retention, rescheduling, duplicate polls and concurrent editing.
Browser tests exercise the inbox-to-journal route at 390px and 1440px and capture
labelled fixture screenshots. Native route tests assert `/journal` opens the
existing Reflections screen; Android build/UI suites remain required.

Apply V136 with the normal migration process. No provider entitlement, paid
service or new delivery configuration is needed. Disabling the existing
notification scheduler also disables this observer. Roll back app code rather
than undoing an applied migration. Physical-device/provider acceptance and
historical investment replay are separate. No retention uplift is claimed before
observing the existing review-completion and return cohorts after release.
