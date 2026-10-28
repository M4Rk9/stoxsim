# Google sign-in: first implementation

This is an optional web sign-in path for **existing, explicitly linked StoxSim accounts**. It uses Google Identity Services / OpenID Connect identity tokens, not authorization to read Gmail, Drive or other Google data. Password registration, legal consent, password recovery and password-confirmed deletion remain the account lifecycle in this phase. Native Android Google login and Google-only account registration are not included.

## Behavior

1. Sign in normally, open Settings → Manage Google sign-in, enter the current StoxSim password, then choose a Google account.
2. Subsequent visits can use the linked Google account from the sign-in screen.
3. Disconnect Google using the StoxSim password. This removes the identity and revokes all StoxSim sessions. Signing out of Google alone does not revoke a StoxSim session; use StoxSim logout/session controls.

The Google subject is the identity key. Email addresses are never auto-linked, changed or marked verified. This avoids takeover through email collisions, including third-party email addresses for which Google is not authoritative. The account export includes the link; deletion cascades to the identity row. Provider tokens are neither persisted nor placed in redirect URLs. The API returns the usual access-token response and sets the existing HttpOnly refresh cookie; the API never accepts a Google token as a StoxSim bearer token.

## Configuration

Disabled by default. Configure these **backend** environment values in the local/staging/production environment file:

```dotenv
GOOGLE_SIGN_IN_ENABLED=true
GOOGLE_CLIENT_ID=<web-client-id>.apps.googleusercontent.com
```

Create a Google Cloud OAuth client of type **Web application**. Configure consent-screen branding, verified domains, support/privacy/terms links, audience and test users as appropriate. Add exact authorized JavaScript origins for each environment, such as `http://localhost:3000` and `https://stoxsim.com`. Use a separate client for staging where practical. This popup/callback GIS flow does not need a backend redirect URI or a client secret. Do not put any client secret in frontend variables. The public client ID is obtained at runtime from the backend, so no new NEXT_PUBLIC variable is introduced.

`FRONTEND_URL` must match the actual browser origin exactly (no trailing slash), and `AUTH_COOKIE_SECURE=true` is required on HTTPS deployments. Frontend and API must remain same-site for Strict cookies (the existing stoxsim.com / api.stoxsim.com arrangement qualifies); a cross-site preview/API arrangement needs a separately reviewed cookie/CSRF design. The API must reach Google's fixed signing-key endpoint and Redis. The browser must reach Google Identity Services. Google resource permissions are restricted to `/google-sign-in` in the CSP, with the provider script using the existing trusted per-response nonce.

The production Compose file reads these values from the host environment file. This change does not set Google Cloud configuration, production variables, merge code, dispatch deployment workflows, or enable the feature remotely.

## Security boundaries

- Nimbus verifies Google's RSA signature using the fixed Google JWK endpoint. Issuer, intended client audience, authorized party, required expiry/issued-at, subject and OIDC nonce are validated. The Google verifier is not registered as the API's JwtDecoder bean.
- A random five-minute HttpOnly/Secure-in-production/Strict challenge cookie binds a separate random OIDC nonce to the browser. Redis GETDEL makes challenges single-use across requests/replicas. Missing cookies, replay, expiration and storage failures fail closed. A new attempt replaces the browser's prior challenge (other tabs must restart).
- The exact frontend Origin is required for challenge, login, link and unlink operations. Auth rate limiting applies to all Google endpoints and fails closed if its store is unavailable.
- Linking/unlinking requires an active StoxSim JWT and current password. No automatic sign-up or linking by email. Unique constraints protect both Google subject and local user; one identity per account.
- User-row locking serializes link/unlink/login with existing credential changes and refresh operations. Login re-checks the association under the lock; unlink revokes sessions under the same lock.
- V125 is an additive identity table. Disable the feature to stop new Google logins without removing the table. Existing StoxSim sessions remain valid until logout/revocation/expiry. An older application image may reject the newer Flyway schema history: retain a migration-aware rollback build, and validate rollback on staging rather than assuming an old binary will start. Do not drop the table to roll back an application image.

## Validation and release gates

Local validation: Java 21 compilation (including integration tests), 228 unit tests, frontend typecheck/build, whitespace and security-merge-gate checks passed. The restricted local runtime has no Docker and prevents Chromium socket creation; PostgreSQL integration and browser execution are therefore delegated to the repository's normal CI. The added tests cover signed identity rejection cases, single-use challenges, database uniqueness/deletion, existing session revocation, origin checks, authenticated linking, and mocked GIS browser behavior.

Before enabling for real users: require all draft-PR CI checks green; execute a real Google consent/link/login/unlink cycle with a staging client, cancellation/denied consent, wrong account, expired challenge, multiple tabs, Redis outage, mobile browser/FedCM behavior, and Google signing-key refresh. Verify logout/recovery/deletion and account export. Review the optional sign-in privacy/cookie notice before release. No live Google credential exchange has been performed in this implementation session.

Future Google-only sign-up must explicitly collect legal consent, respect `PublicReleaseGuard`, initialize default accounts/subscription once, handle collision without silent linking, and provide passwordless reauthentication/recovery/deletion UX. Native Android should use its supported credential flow with separately registered client audiences, not an embedded webview.

References:

- https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
- https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid
- https://developers.google.com/identity/gsi/web/reference/js-reference
