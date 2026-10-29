# Google sign-in: web registration and native Android

This is an optional Google sign-in path for existing linked accounts and new Google-only accounts, on web and native Android. It uses Google Identity Services / OpenID Connect identity tokens, not authorization to read Gmail, Drive or other Google data. Google registration explicitly collects a display name and legal consent, respects public registration and market-data release gates, and creates the same practice accounts and free subscription. Password registration remains available; accounts are never automatically linked by email.

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

`FRONTEND_URL` must match the actual browser origin exactly (no trailing slash), and `AUTH_COOKIE_SECURE=true` is required on HTTPS deployments. Frontend and API must remain same-site for Strict cookies (the existing stoxsim.com / api.stoxsim.com arrangement qualifies); a cross-site preview/API arrangement needs a separately reviewed cookie/CSRF design. The API must reach Google's fixed signing-key endpoint and Redis. The browser must reach Google Identity Services. Google resource permissions are restricted to the three Google sign-in pages in the CSP, with the provider script using the existing trusted per-response nonce.

The production Compose file reads these values from the host environment file. This change does not set Google Cloud configuration, production variables, merge code, dispatch deployment workflows, or enable the feature remotely.

## Security boundaries

- Nimbus verifies Google's RSA signature using the fixed Google JWK endpoint. Issuer, intended client audience, authorized party, required expiry/issued-at, subject and OIDC nonce are validated. The Google verifier is not registered as the API's JwtDecoder bean.
- A random five-minute HttpOnly/Secure-in-production/Strict challenge cookie binds a separate random OIDC nonce to the browser. Redis GETDEL makes challenges single-use across requests/replicas. Missing cookies, replay, expiration and storage failures fail closed. A new attempt replaces the browser's prior challenge (other tabs must restart).
- The exact frontend Origin is required for challenge, login, link and unlink operations. Auth rate limiting applies to all Google endpoints and fails closed if its store is unavailable.
- Linking/unlinking requires an active StoxSim JWT and current password. Registration requires explicit consent; no automatic linking by email. Unique constraints protect both Google subject and local user; one identity per account.
- User-row locking serializes link/unlink/login with existing credential changes and refresh operations. Login re-checks the association under the lock; unlink revokes sessions under the same lock.
- V125 is an additive identity table. Disable the feature to stop new Google logins without removing the table. Existing StoxSim sessions remain valid until logout/revocation/expiry. An older application image may reject the newer Flyway schema history: retain a migration-aware rollback build, and validate rollback on staging rather than assuming an old binary will start. Do not drop the table to roll back an application image.

## Google-only account lifecycle

New users choose Create an account with Google, enter a display name and explicitly accept the Terms and Privacy Notice. Google must establish current ownership of a verified Gmail or hosted Workspace address. Third-party Google email addresses use email registration followed by explicit linking; an old Google email_verified claim alone does not prove current ownership.

Google-only accounts have a null password hash, exposed as passwordLoginEnabled=false. Password login rejects these accounts. Settings offers fresh Google confirmation to add an initial password or permanently delete the account. Adding a password revokes every session and invalidates email action links. A different Google subject cannot reauthenticate the account. Email password recovery also creates a password, so losing Google access does not require a new account. Existing passwords cannot be replaced through the initial-password endpoint.

Google cannot be disconnected until a password exists. Add a password before changing the account email. Existing password accounts must sign in/recover and explicitly link Google; matching emails never merge accounts. Deletion uses existing cleanup and the identity cascade. V126 permits a null password hash without modifying existing hashes. Use a migration-aware rollback build that supports passwordless accounts. Keep email recovery available if Google is disabled.

## Android configuration

The app uses Android Credential Manager with GetSignInWithGoogleOption, server-issued nonces and existing encrypted refresh-token storage. No WebView, Firebase project, client secret or Google access token is needed. Google credentials and challenge bindings are not persisted or logged. Logout clears Credential Manager state and the StoxSim session.

1. In the same Google Cloud project as the existing Web client, create an OAuth client of type **Android**, with package **com.stoxsim.app**.
2. Register the SHA-1 certificate fingerprint of the installed build. Run `./gradlew signingReport` in `mobile/android/project` for local debug signing. For Play builds use **Play Console → App integrity → App signing key certificate**, not the upload certificate. Register separate clients for debug and production certificates. Locally signed release APKs may need another client.
3. Set backend `GOOGLE_ANDROID_CLIENT_IDS` to the comma-separated Android client IDs. Keep `GOOGLE_CLIENT_ID` as the existing **Web** client ID: Android requests that server audience. Empty Android IDs keep native endpoints unavailable.
4. Recreate the backend through the deployment flow and install Android **0.6.0 / version code 14**. Public client configuration is read at runtime; no credentials are compiled into the APK.
5. Test on a device with Google Play services and the matching signing certificate. A debug client is insufficient for a Play-signed release.

Native endpoints under `/api/v1/auth/google/native/` accept JSON and reject any Origin header. Native Redis challenges are isolated from browser cookies. Web endpoints still enforce exact Origin and the HttpOnly cookie. Tokens require the Web audience and an approved authorized party. Native client registration is not device attestation; rate limits and signature validation remain enforced.

Existing password users link Google in website Settings first, then sign in natively. New Android users can register with a display name and consent, without filling email/password fields. Google-only users can add a password or delete their account natively after confirming Google. Link/disconnect management remains on the website.

## Validation and release gates

Automated tests cover signed-token rejection, challenge isolation/replay, consent/release gates, provisioning, email collisions, last-login-method protection, reauthentication ownership, revocation, deletion, web UI flows and Android request/session contracts. Provider interactions are mocked. The PR records actual CI results; automation does not establish a successful live provider exchange.

Before release, test web signup with a new account, practice-account initialization, recovery, add-password and deletion. Check existing-email collisions, wrong Google accounts, cancelled picker, expired/replayed challenges, registration closed and native disabled. On Android test linked login, signup, relaunch/refresh, logout, global revocation and recovery for both debug and release certificates. Previously validated production linked-account web login is the regression baseline.

References:

- https://developers.google.com/identity/gsi/web/guides/verify-google-id-token
- https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- https://developers.google.com/identity/sign-in/android/backend-auth
