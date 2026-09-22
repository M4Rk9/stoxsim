# Campus competition trust and administration

Campus competitions build on the same standard ₹5 lakh India portfolio used by
the global learning season. Institution status never changes portfolio capital,
subscription entitlements or standard leaderboard eligibility.

## Verification lifecycle

The institution verification foundation establishes the trust boundary:

1. A learner with a verified StoxSim email submits an institution name,
   official email domain and optional HTTPS website.
2. The request is persisted as `PENDING`; only one pending request is allowed
   per learner.
3. A database-authorized platform administrator compares the request with
   official public sources and approves or rejects it with an audit trail.
4. Approval atomically creates one verified institution and makes the requester
   its first `ORGANIZER`.
5. Institution names, domains and user memberships are unique. Approval cannot
   silently attach one learner to multiple institutions.

An email domain supplied in a form is not proof of affiliation. Administrators
must not approve from the domain alone. Review the official institution site
and use an out-of-band contact when the request is ambiguous.

## Authorization

`app_user.platform_role` is `USER` by default. The browser cannot change this
field, JWT claims do not grant campus administration, and every moderation call
reloads the authenticated user from the database.

There is intentionally no public administrator-promotion endpoint. A production
operator may bootstrap a reviewed administrator with a one-time database change:

```sql
UPDATE app_user
SET platform_role = 'ADMIN', updated_at = CURRENT_TIMESTAMP
WHERE lower(email) = lower('<reviewed operator email>');
```

Confirm the affected row count is exactly one. Administrator access is
revocable by changing the value back to `USER`. Do not grant this role to campus
organizers; organizer permissions are scoped to their verified institution.

## API

Authenticated learner endpoints:

- `GET /api/v1/campus` — current membership, latest request and administrator flag.
- `POST /api/v1/campus/verification-requests` — submit an idempotent pending request.

Database-authorized administrator endpoints:

- `GET /api/v1/campus/admin/verification-requests`
- `POST /api/v1/campus/admin/verification-requests/{id}/approve`
- `POST /api/v1/campus/admin/verification-requests/{id}/reject`

Rejection requires a reason. Approval and rejection are row-locked, terminal
transitions and generate account audit events plus aggregate metrics. The API
rate limiter applies the existing authenticated write policy.

## Privacy and account lifecycle

- Institution verification requester email is returned only to that requester and platform admins. Membership request email is additionally visible to organizers of the requested institution.
- Requests and memberships are included in account data export.
- Deleting the requester removes their requests and membership.
- Deleting a reviewing administrator preserves the institution and review
  outcome while clearing the reviewer foreign key.
- Signed-in learners can search verified institution names/domains; suspended institutions are visible in the directory only to platform admins. No member identities are exposed in directory results.

## Deployment

Flyway migration `V109` adds the default-deny platform role, verification
requests, verified institutions and scoped memberships. No new secret,
environment variable, provider, paid service or market-data permission is
required. The feature is safe with zero administrators configured; requests
remain pending until an operator is explicitly assigned.


## M3 membership and organizer workflow

Open `/campus` from Learning competitions. Verified StoxSim email is required to
request membership. Students provide a short affiliation note, not identity
documents. An organizer checks affiliation independently and approves/rejects
with a note visible to the requester. A matching email domain is not proof.
One pending membership request and one institution membership per account are
enforced in PostgreSQL and serialized with user locks. Requests can be cancelled
before review. Approval never automatically enrolls a learner in a competition.

Organizers can promote/demote other members, remove members and create/cancel
competitions in their own institution. Members may leave. The last organizer
cannot leave, be removed or be demoted until another organizer is assigned.
Deleting an account remains available regardless of its role; an ADMIN can
recover an orphaned institution by assigning an existing verified account after
checking affiliation. Recovery does not move someone from another institution.

Limits: 200 members, 200 pending requests, ten upcoming/open competitions per
institution. Directory results are capped at 50, workspace history at the latest
50 competitions, audit at 50 actions, and standings at 200 participants. Narrow
directory searches by institution name when needed. Membership queue is visible
only to that institution's organizers and platform admins. Moderator role checks
use current database state, not browser controls or token role claims. Missing
and unauthorized scoped resources return 404.

## Scoring and consent

- Organizers choose title (3–100 characters), start (now or within 90 days), end
  (one hour–90 days after start), and capacity (2–200). These are immutable.
- Only approved, email-verified members may explicitly enroll during OPEN.
  Admin visibility alone is not enrollment eligibility. Global enrollment is
  independent and is never triggered by campus enrollment.
- The server selects the standard India account; custom paid sandboxes cannot
  enter. Configured starting capital must be ₹500,000. Actual positive account
  value on entry becomes the immutable baseline, so different join times may
  produce different opportunity windows. No account is reset on enrollment.
- Return = `(latest value - entry baseline) / entry baseline * 100`, rounded to
  four decimals. Ties share rank (competition ranking: 1, 1, 3). Display name,
  return, entry time and price freshness are visible only to campus members and
  platform admins. Absolute balance is returned only for the requesting learner.
- Opening the board is read-only. An explicit POST refreshes only the requester's score while OPEN.
  Other rows are their last observed valuations. Holding prices marked UNAVAILABLE
  prevent enrollment and retain the last usable score on refresh. Cash-only
  accounts remain eligible without market prices. STALE/CLOSED data is labeled.
- Once ended, the board freezes last observed values; these are **not official
  closing-price results**. No background full-campus price fan-out is introduced.
- Withdrawal is permanent for that competition. Removing/leaving membership
  withdraws all entries. Rejoining the institution cannot reset prior baselines.
  Deleting the account removes its entries and requests via foreign keys.
- Organizers may cancel an upcoming/open event with a visible reason. Admins may
  suspend/restore an institution; suspension pauses requests, approvals, new
  competitions, enrollment and valuation refresh. Existing members retain board
  access and may withdraw/leave. Moderation records remain accessible.

## M3 API

All routes below are under `/api/v1/campus`; reads use `Cache-Control: no-store`.
Existing institution verification endpoints remain unchanged.

| Route | Permission / behavior |
| --- | --- |
| `GET /institutions?q=` | Authenticated directory search, max 160 characters |
| `GET /membership-request` | Own latest membership request; empty body if none |
| `POST /institutions/{i}/membership-requests` | Verified nonmember; `{note}` |
| `POST /membership-requests/{r}/cancel` | Request owner |
| `GET /institutions/{i}` | Member or ADMIN workspace |
| `GET /institutions/{i}/manage` | Scoped organizer or ADMIN; members, queue, audit |
| `POST /institutions/{i}/membership-requests/{r}/review` | Manager; `{approve,note}` |
| `POST /institutions/{i}/members/{u}/role` | Manager; `{role: ORGANIZER or MEMBER}` |
| `POST /institutions/{i}/members/{u}/remove` | Manager or self; `{note}` |
| `POST /institutions/{i}/organizer-recovery` | ADMIN; `{email,note}` |
| `POST /institutions/{i}/suspension` | ADMIN; `{suspended,note}` |
| `POST /institutions/{i}/competitions` | Manager; `{title,startsAt?,endsAt,capacity}` |
| `GET /institutions/{i}/competitions/{c}` | Member or ADMIN; read-only standings |
| `POST /institutions/{i}/competitions/{c}/refresh` | Member or ADMIN; requester-only refresh |
| `POST /institutions/{i}/competitions/{c}/enroll` | Verified member; server-owned account/score |
| `POST /institutions/{i}/competitions/{c}/withdraw` | Own entry only |
| `POST /institutions/{i}/competitions/{c}/cancel` | Manager; `{note}` |

Institution row locks serialize mutation/capacity decisions; user locks protect
cross-institution membership assignment. Standard account locks align valuations
with trading. The lock remains held during a price lookup, so a slow provider can
delay other operations in that institution (transaction timeout 15 seconds).
This is a bounded first campus release, not evidence of large-campus throughput;
load/capacity acceptance belongs in M7. Existing write rate limits apply.

Campus audit stores action, actor, target, competition reference and time; no application/review notes
are copied into the shared activity view. Removal/recovery reasons enter the
acting moderator's account security audit (detail limited to 200 characters). Account deletion clears actor/target
references while retaining actions. Own join requests, entries and associated
moderation actions are included in account exports. No new service/provider,
secret or environment setting is required.

## M3 deployment and acceptance

`V112` adds campus requests, competitions, entries, audit and a suspension flag.
It is additive; existing institution verification, global competition entries,
portfolios and subscriptions are unchanged. Deploy the immutable reviewed image
pair using the existing production workflow. Application rollback can retain
these additive tables; do not drop them or alter production data to roll back.

After deployment, use separate organizer/member/outsider test accounts:

1. Open `/campus`; approve an affiliation request as organizer. Confirm an
   outsider cannot view its workspace or moderation data.
2. Create an event, enroll as member with the explicit disclosure, and confirm
   the standard India baseline appears only to that learner.
3. Refresh a score; verify timestamp and entry-relative return. Test withdrawal
   and confirm re-enrollment is refused.
4. Check rejection/cancellation, organizer promotion and last-organizer guard.
5. As ADMIN, inspect recovery and suspension controls; use a disposable test
   institution for these changes. Record candidate SHA and acceptance results.

Do not use production accounts for concurrency/load testing. Automated tests use
isolated PostgreSQL and browser fixtures. Production acceptance is separate from
CI and must be recorded after the reviewed candidate is deployed.
