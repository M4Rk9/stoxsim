# Unified notifications

The authenticated inbox is `GET /api/v1/notifications` (latest 50 items plus unread count). Mark one item with `POST /{id}/read`, or all items with `POST /read-all`. Every query and mutation scopes the authenticated user; foreign item IDs return 404.

`GET`/`PUT /api/v1/notifications/preferences` uses the shared seven-boolean contract: learning, competitions, portfolioEducation, productAnnouncements, promotionalMessages, pushEnabled and emailDigest. Learning, competition and portfolio education default on for the inbox. Marketing, announcements, push and email default off. Enabling email requires a verified address. Security and billing remain distinct from optional learning/marketing categories. Product offers must use `NotificationRequested.promotional=true`; both announcement and promotional consent are checked at creation and delivery. There is no public announcement/campaign sending endpoint.

V129 adds user-owned settings, inbox, opaque device registrations, deduplication receipts, delivery outbox, suppression and hashed unsubscribe-token tables. User deletion cascades this private data. Inbox history is retained for 90 days; deduplication receipts for 120 days. Maintenance deletes at most 1,000 rows per table per run.

The bounded materializer reads committed achievement, weekly-report and security events after the migration's observation timestamp, plus competition end reminders for enrolled users. It does not invent historical events or alter financial state. At most 100 source events are considered per minute, and optional in-app events are capped at 10 per user per 24 hours. Sources skipped due to user preferences are recorded and never replayed on later opt-in.

Other authoritative services can publish a Spring `NotificationRequested` event with a stable per-user event key. The listener runs after commit, creates the notification in an isolated transaction and contains optional failures. Billing hooks should link to `/settings`. Destinations are restricted to internal path/hash links; external URLs and query strings are rejected. Notifications do not contain provider secrets or financial predictions.

## Optional delivery

External delivery is disabled by default. `stoxsim.notifications.delivery-enabled=true` enables the bounded dispatcher only for configured transports. It claims database rows with `SKIP LOCKED`, sends at most 20 messages per run, permits at most one email update and three push updates per user per 24 hours, and checks current preferences, verified address and suppression before sending. The email update points to the inbox for other updates. Repeated job runs do not resend completed events.

A provider may request retry only after definite rejection; retries stop at three attempts with backoff. A send whose acceptance is unknown, including a worker crash after sending, becomes `UNCERTAIN` and is not automatically replayed. SMTP cannot guarantee exactly-once delivery. Reconcile an uncertain provider outcome before any manual intervention. Current metrics expose created/suppressed counts and delivery outcome/channel, without user identifiers.

SMTP reuses existing `stoxsim.auth.mail-*` configuration. Additionally enable `stoxsim.notifications.email-enabled`, supply an HTTPS frontend URL and `stoxsim.notifications.unsubscribe-api-url=https://<api-host>/api/v1/notifications/unsubscribe`. TLS and hostname checks remain required. Messages include one-click unsubscribe headers and a frontend confirmation link. `POST /api/v1/notifications/unsubscribe?token=...` is a public capability endpoint: a hashed, random 256-bit token disables only optional notification emails; invalid/repeated tokens receive the same 204 response. It does not change the separate legacy weekly-report preference. A GET must never mutate preferences.

An authenticated platform administrator can record verified bounce/complaint feedback through `POST /api/v1/notifications/admin/suppressions` with `{userId,reason}` (`BOUNCE` or `COMPLAINT`). Normal user opt-in can clear an unsubscribe suppression, but cannot clear a bounce/complaint. Provider-specific bounce/complaint webhook verification and DNS SPF/DKIM/DMARC evidence remain external delivery acceptance tasks; no DNS or deliverability approval is assumed.

## Android devices

`POST /api/v1/notifications/devices` accepts `{token,platform:"ANDROID"}` after push opt-in; `DELETE` accepts `{token}`. Up to five installation tokens are retained per user. An installation token can move to the newly authenticated account; the previous account cannot delete or retrieve the new binding. Clients unregister their token before logout; session revocation/password changes/logout-all must call `NotificationService.removeAllDevices`. Token values are transport credentials: never log, return, or include them in account export. Export only platform and update timestamp metadata.

No FCM transport is installed yet. PUSH outbox entries remain pending unless an actual configured `NotificationTransport` is provided, and are eventually removed with notification retention. FCM project/service identity, native configuration, token invalidation and device delivery acceptance are explicit remaining work. Do not enable or claim push delivery based solely on successful token registration. A future transport must resolve current device ownership immediately before sending, use generic content and safe internal destinations, and never route an old user's notification to a reassigned token.

## Verification and rollout

`mvn -B -ntp -C test -Dtest=NotificationSafetyTest,NotificationIntegrationTest -DargLine='-Xmx2g -XX:ActiveProcessorCount=2'` passed 15 tests with zero failures, errors or skips on 2026-11-05. Tests exercise PostgreSQL migration, ownership, mark-read idempotency, opt-in validation, deduplication, caps, device switching/removal, public unsubscribe, complaint suppression, delivery disablement, ambiguous outcomes, retry bounds, promotional consent at dispatch, source observation boundary and account deletion cascades. Transport tests use fakes; no email or push was sent.

Deploy V129 with delivery disabled, verify authenticated inbox/preferences and monitoring, then complete provider-specific acceptance before owner-controlled activation. Reverting application code may leave the additive tables in place; do not drop user data as a rollback. Existing weekly email behavior is preserved. Pause optional processing with `stoxsim.notifications.scheduler-enabled=false` if needed.
