# Security recovery and resource hardening

## Behavior changes

- Changing an email address requires `currentPassword` in the profile update request. Display-name changes do not require it. A successful email change signs out every session and sends a new verification link.
- Email changes, password changes, and password resets invalidate outstanding password-reset and verification links. Token operations and identity updates serialize on the user row to prevent stale links from racing an update.
- Watchlists accept at most 100 distinct items. Adding an existing item remains idempotent. Existing larger watchlists remain readable and can be reduced; additional items are rejected with HTTP 409.
- Both Caddy environments reject API request bodies above 64 KiB with HTTP 413.
- Sensitive rate-limit policies return HTTP 503 with `Retry-After: 60` when Redis cannot enforce limits. General reads continue. Restore Redis before retrying affected operations; providers should retry failed webhook deliveries.
- SMTP connections verify server identity and require STARTTLS when `MAIL_STARTTLS=true`.
- Next.js is updated to 16.3.8.

## Rollout

Deploy the backend and frontend together, then reload the matching Caddy configuration. No database migration is required. Keep `MAIL_STARTTLS=true` in production and confirm the SMTP provider supports STARTTLS with a valid certificate.

Before deploying, require CI to pass the PostgreSQL integration tests and browser acceptance tests. Verify email delivery with the production SMTP configuration. Check Redis health and identify watchlists already above the new limit so support can explain rejected additions.

After deployment, verify that an email change requires the password, signs out the user, and delivers a fresh verification link. Confirm earlier recovery links are rejected and a fresh password-reset link still works. Monitor HTTP 413, watchlist-limit HTTP 409, and rate-limit-unavailable HTTP 503 responses.
