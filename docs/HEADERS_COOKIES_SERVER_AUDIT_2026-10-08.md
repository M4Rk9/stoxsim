# Public headers, cookies and deployment configuration audit — 2026-10-08

Baseline: main `58ac9b0dfbc0401f2de0b0ee0e8b972256f686b2`. Live observations around 11:05–11:07 UTC (16:35–16:37 IST). Redacted header/status evidence: `PUBLIC_HEADER_AUDIT_2026-10-08.json`. No secret-file bodies were downloaded.

## Live observations

| Control | Website and API | Result / limit |
| --- | --- | --- |
| CSP | Website: self-only defaults, frame-ancestors none, object-src none; API: default-src none and frame-ancestors none | Present. Frontend still permits unsafe-inline scripts/styles for current Next.js hydration/theme code. Not a strict nonce/hash policy. Razorpay domains are allowed only on admin billing pages. |
| HSTS | max-age=31536000; includeSubDomains | Present on canonical website/API, including tested 404/401 responses. Missing on HTTPS www redirect before this fix. |
| X-Frame-Options | DENY | Present on canonical website/API. |
| X-Content-Type-Options | nosniff | Present on canonical website/API. |
| Referrer / permissions policies | strict-origin-when-cross-origin; camera/microphone/geolocation disabled | Present on canonical website/API. |
| HTTP redirect | HTTP web and API return 308 to corresponding HTTPS origin | Verified without following redirects. HTTPS www returns 301 to canonical website. |
| .env / .env.production / .git/config / .git/HEAD / nested/.env | Website 404; API 401 | Tested anonymous paths did not return files. Explicit proxy denial added, so these paths now return 404 before either app receives them. |
| Metrics | Public API /actuator/prometheus returns 404 | Public edge blocks scraping endpoint; internal scrape requires a separate token. |
| Server identification | Canonical web/API remove Server and X-Powered-By | HTTPS www and automatic HTTP redirects initially expose Caddy. www fixed; HTTP software banner alone does not expose credentials. |

A static `/admin/billing` page shell returns 200; actual private billing operations require backend authentication and database admin checks, as audited in PR #141. A public static page shell is not proof of unauthorized account data access.

## Cookies and session storage

`RefreshCookieService.issue` and `clear` share one builder: HttpOnly=true, SameSite=Strict, path=/api/v1/auth, host-only (no Domain). Issue Max-Age is the configured refresh lifetime; clear Max-Age is zero. Existing `RefreshCookieServiceTest` verifies both builders' flags. A live unauthenticated POST logout with an empty JSON body returned HTTP 204 and a clearing cookie with Secure, HttpOnly, SameSite=Strict and the expected path. No existing browser cookie was supplied. Production/staging Compose previously defaulted AUTH_COOKIE_SECURE to true but permitted `.env` to override false; now it is an unconditional true for both HTTPS deployments. Local development retains its separate HTTP-compatible setting.

Access JWTs are held in browser sessionStorage, not a session cookie, so HttpOnly cookie flags do not apply to them. This remains an XSS-sensitive design limitation, mitigated by escaped rendering, the existing CSP, short-lived tokens and revocable server sessions. The Spring API is stateless and does not use a JSESSIONID login session. No production user credentials were used or new accounts created for this audit.

## Deployment and file exposure review

- Caddy reverse-proxies fixed app services; neither production nor staging uses a generic host file server. The host `.env` is not mounted into frontend/backend web roots. Caddy only mounts its config and persistent certificate/config volumes.
- Frontend/backend Docker build contexts ignore `.git`, `.env` variants, private key formats, service-account/credential files, logs and test results. Final backend contains a packaged JAR; frontend contains runtime dependencies, `.next` and public assets, not a copy of the repository root. Current `frontend/public` contains only the app logo.
- Production/staging PostgreSQL and Redis have no published host ports and use an internal data network. Backend/frontend are exposed only inside Docker networks. Caddy publishes 80/443; monitoring UI ports bind 127.0.0.1. Redis requires a password; PostgreSQL credentials are environment-provided. App containers run as non-root users.
- Deploy workflow preserves the host `.env`; documentation requires mode 600 and independent secrets. Monitoring config rendering is protected. The actual VPS filesystem permissions, SSH configuration, firewall rules, running container mounts and all listening host ports cannot be confirmed from source alone; no authenticated VPS shell was available. The repository's full TCP audit is available through `scripts/public-port-audit.sh` / Security DAST.

## Changes and validation

1. Add headers to the HTTPS www redirect and remove its Server header.
2. Reject environment files and git/VCS/SSH/AWS/editor/build metadata paths at Caddy, including nested and percent-encoded forms, before proxying. Preserve legitimate `.well-known` routes and normal Next `_next` assets.
3. Defer frontend CSP and auth Cache-Control enforcement until response headers are written, so upstream headers cannot weaken them.
4. Force Secure refresh cookies in production/staging Compose.
5. Extend the read-only security smoke script to verify API headers and sensitive-path 404s; run it during production smoke regardless of registration state.
6. Add a real Caddy regression test for both bundles using an intentionally unsafe upstream: header overwrite attempts, sensitive-file routes, encoded paths, auth no-store and www redirect headers. CI validates actual Caddy syntax and runs this test.

Shell syntax and diff checks run locally. Full container/configuration and existing app checks run through the PR CI; Docker is unavailable in the local runner. No production deployment performed. After merging, build/deploy the candidate using the normal workflow, which now runs the expanded read-only smoke contract.

## Remaining CSP work

The frontend policy is present but not a strict script CSP because of unsafe-inline. Removing it without adapting Next's inline hydration and theme scripts would break the UI. A per-request nonce implementation requires dynamic rendering and coordinated headers/script nonces; a hash/SRI approach has different build constraints. This audit does not claim the current policy prevents every inline-script execution. That architectural change remains a separate follow-up.

References: Caddy header directive/deferred enforcement https://caddyserver.com/docs/caddyfile/directives/header ; Next.js CSP guide and dynamic-rendering constraints https://nextjs.org/docs/app/guides/content-security-policy .
