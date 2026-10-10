# Security policy

## Supported versions

StoxSim is currently in public-beta preparation. Security fixes are made on the default branch and included in the next tagged release.

| Version | Supported |
|---|---|
| Default branch | Yes |
| Older snapshots and untagged deployments | No |

## Report a vulnerability

Do not open a public issue for a suspected vulnerability.

Use [GitHub private vulnerability reporting](https://github.com/M4Rk9/stoxsim/security/advisories/new) and include:

- the affected endpoint, component, or commit;
- reproduction steps or a proof of concept;
- the likely impact;
- any mitigation you have already tested.

Please avoid accessing other users' data, disrupting the service, or running destructive tests. Maintainers will acknowledge a complete report as soon as practical, coordinate a fix, and credit the reporter when requested.

## Secrets

Never commit credentials, API tokens, production environment files, database dumps, or user data. Revoke any exposed secret immediately and then remove it from the repository history.

Only `NEXT_PUBLIC_API_URL` may be exposed to the frontend. Provider keys, database
credentials, JWT signing keys, SMTP passwords and webhook secrets belong in the
server's untracked `.env` (mode 600), passed to backend services at runtime.
Never use Next.js `env` configuration to pass private values to frontend code.
`node scripts/verify-frontend-secrets.mjs` builds with fake backend credentials and
checks browser assets and prerendered output for leaks; CI runs it on every change.
See [the October 2026 secret audit](docs/SECRET_AUDIT_2026-10-08.md) for scope and findings.

## Account recovery and credential changes

Login holds the account's database lock from password verification through session
creation. Password changes and resets hold the same lock through revocation, so
an old-password login cannot create a surviving session after a credential update.

Password recovery is limited per account to one email per minute and five emails
in any rolling hour, shared across application instances. Suppressed requests
return the same generic `202 Accepted` response as unknown recipients and leave
the existing recovery link usable. Consumed and invalidated links still count
toward the issuance budget. Existing IP request limits also remain in effect.
The existing indexed token history supplies the recipient budget without schema changes.

Password inputs must be valid Unicode and fit within BCrypt's 72-byte UTF-8
limit. This applies to registration, login, recovery, password changes, email
changes and deletion. Oversized input returns validation error HTTP 400; no
password is truncated or pre-hashed, and existing hashes remain compatible.

## Market WebSocket resource limits

Each backend instance admits at most 256 WebSockets, 32 per client IP and four
per authenticated account. A STOMP CONNECT must authenticate within ten seconds;
heartbeats cannot extend that deadline. Its dedicated scheduler is isolated from
provider/database jobs. Every WebSocket message and every decoded
STOMP frame has a token-bucket quota (ten per second per socket, twenty per second
per account, and 256 per second per instance). Account budgets survive reconnects.
Only one subscription to the quote topic is allowed per connection.

Inbound messages/STOMP frames are limited to 8 KiB. Broker executors have bounded
queues and outbound buffering is capped at 64 KiB with a five-second send limit.
Violations close the transport; socket reservations are released on actual closure.
Expired or revoked credentials cannot continue receiving quotes. Connection/rejection
metrics use fixed reason tags, never token, account or IP tags.

Defaults are configurable through `stoxsim.security.websocket.*`. Limits apply
per instance, including per-account/IP limits; they are not cluster-wide quotas.
The production backend must remain private behind the existing trusted Caddy edge
because forwarded addresses supply client IPs. Shared-NAT users share the IP cap.
`WebSocketQuotaIntegrationTest` saturates a deliberately small quota on an isolated
real server and verifies refusal, cleanup, healthy quote delivery and HTTP readiness.
This proves enforcement and isolation, not a production capacity or user-count claim.

## Browser script policy

The frontend generates a fresh cryptographic nonce for each dynamic HTML response
and attaches it to theme, framework and hydration scripts. Script CSP allows no
`unsafe-inline` or production `unsafe-eval`; inline event handlers are forbidden.
Razorpay script/frame origins are allowed only on the billing route. Incoming nonce
and CSP headers are overwritten, HTML is not shared-cacheable, and Caddy preserves
the application policy while supplying a deny-all fallback if it is absent.
Inline styles remain allowed for React style attributes.

## Authentication record retention

Hourly cleanup removes up to 5,000 rows from each authentication table per run.
Refresh and account tokens are removed only after they have been expired for a day,
preserving unexpired session rotation history and recent recovery issuance budgets.
Security audit history is preserved. Indexed, ordered batches skip rows
locked by another transaction and have a ten-second transaction timeout. Migration
V118 adds an account-token expiry index; refresh-token expiry is already indexed.
