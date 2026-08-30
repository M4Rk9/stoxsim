# Campus competition trust and administration

Campus competitions build on the same standard ₹5 lakh India portfolio used by
the global learning season. Institution status never changes portfolio capital,
subscription entitlements or standard leaderboard eligibility.

## Verification lifecycle

The first campus batch establishes the trust boundary before campus standings
are opened:

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

- Requester email is returned only to that requester and authorized platform
  administrators.
- Requests and memberships are included in account data export.
- Deleting the requester removes their requests and membership.
- Deleting a reviewing administrator preserves the institution and review
  outcome while clearing the reviewer foreign key.
- Public institution listings and campus competition membership are deliberately
  deferred until scoped organizer controls are implemented.

## Deployment

Flyway migration `V109` adds the default-deny platform role, verification
requests, verified institutions and scoped memberships. No new secret,
environment variable, provider, paid service or market-data permission is
required. The feature is safe with zero administrators configured; requests
remain pending until an operator is explicitly assigned.
