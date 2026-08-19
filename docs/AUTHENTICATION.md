# Authentication and account lifecycle

## Endpoints

| Method | Endpoint | Authentication |
|---|---|---|
| POST | `/api/v1/auth/register` | Public, rate limited |
| POST | `/api/v1/auth/login` | Public, rate limited |
| POST | `/api/v1/auth/refresh` | HttpOnly refresh cookie |
| POST | `/api/v1/auth/logout` | HttpOnly refresh cookie |
| POST | `/api/v1/auth/password/forgot` | Public, enumeration-safe |
| POST | `/api/v1/auth/password/reset` | Public, single-use token |
| POST | `/api/v1/auth/email-verification/confirm` | Public, single-use token |
| POST | `/api/v1/auth/email-verification/resend` | Bearer access token |
| GET/PATCH/DELETE | `/api/v1/auth/me` | Bearer access token |
| PATCH | `/api/v1/auth/me/password` | Bearer access token |
| POST | `/api/v1/auth/me/export` | Bearer access token |
| GET | `/api/v1/auth/sessions` | Bearer access token |
| DELETE | `/api/v1/auth/sessions/{sessionId}` | Bearer access token |
| POST | `/api/v1/auth/logout-all` | Bearer access token |
| GET | `/api/v1/auth/events` | Bearer access token |

## Registration consent

Registration requires `termsAccepted: true`. The API rejects missing or false acceptance even if a client bypasses the browser checkbox. Each new account stores `terms_accepted_at`, `terms_version`, and `privacy_version`; the account export includes those fields. Existing private-staging accounts remain valid and may have null acceptance fields.

Changing a legal document version requires updating both the public page effective date and `LegalDocumentVersions`. If the change is material, add a signed-in re-acceptance flow before public deployment rather than silently treating earlier consent as current.

## Token boundaries

- Access tokens are HS256 JWTs with a 15-minute lifetime and remain in tab-scoped `sessionStorage`.
- Refresh tokens are 384-bit random values delivered through an HttpOnly, SameSite=Strict cookie.
- Only SHA-256 hashes of refresh and account-action tokens are stored.
- Refresh rotates the token while retaining a stable session identifier.
- Password changes and password resets revoke every refresh session.
- Production must set a unique `JWT_SECRET` of at least 32 characters and `AUTH_COOKIE_SECURE=true`.

## Email verification

Registration creates a single-use verification token that expires after 24 hours. Resending invalidates any earlier unused verification link. Changing the sign-in email marks it unverified and sends a new link.

Configure SMTP with `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, and `MAIL_STARTTLS`. When SMTP is absent, development registration remains available but the service logs that delivery is disabled; it never logs the token.

## Password recovery

The forgot-password endpoint always returns HTTP 202, regardless of whether the address exists. This prevents account enumeration. A reset link expires after 30 minutes, can be used once, changes the BCrypt password hash, and revokes every session.

## Session management and audit

Each refresh session records a random session ID, device user-agent label, start time, last-use time, and expiry. Users can revoke one non-current session or log out all devices.

Account registration, sign-in, sign-out, verification, recovery, profile/password changes, exports, session revocations, and deletion are written to a user-visible security event history.

## Export and deletion

The export endpoint returns JSON containing the profile (including legal-acceptance timestamp and versions), virtual accounts, holdings, orders, trades, ledger entries, watchlists, and recent security events. It never includes password hashes or authentication tokens.

Confirmed deletion permanently removes the user and all user-owned simulator data through database cascades. The security-event rows are detached from the deleted user and identifying detail is cleared, retaining only anonymous event type and time for operational integrity.
