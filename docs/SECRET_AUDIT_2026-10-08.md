# StoxSim secret exposure audit — 2026-10-08

Audited baseline: `c910ff4ab40959b1769c57cb8a0ed92e47fa91b8` (main).
The changes accompanying this report harden secret handling; they require a normal
review, merge and production deployment before taking effect on the live host.

## Scope and method

- Fetched every available remote branch and tag into a non-shallow clone: 834
  reachable commits. Gitleaks 8.30.1 examined 728 non-empty commit diffs across
  all fetched refs using `git --log-opts=--all`, including deleted content.
- Scanned a clean archive of all tracked source/configuration/documentation,
  not just the current frontend. Reviewed environment templates, Java property
  bindings and API responses, Dockerfiles, all Compose bundles and CI/deploy scripts.
- Reviewed every frontend application environment reference and provider-call
  path. Checked public static files and a fresh optimized Next.js build.
- Injected 22 independent fake backend credential/connection values at build time
  and checked 444 generated frontend artifacts, including JavaScript, assets,
  source maps where present, prerendered HTML/RSC and frontend server modules.
  No canary appeared. Gitleaks also found no credentials in `.next/static`.
- All scanner output was redacted. No real credential was reproduced in this report.

## Findings and fixes

| Finding | Assessment | Resolution |
| --- | --- | --- |
| Hardcoded fallback JWT signing secret in backend configuration and local Compose | High impact if any deployment used the fallback: a publicly known signing key could permit forged access tokens. Production Compose already requires an explicit key, but its actual value was not accessible to this audit. | Removed fallback; startup rejects missing/short keys, template values and the legacy development key. Integration test contexts generate ephemeral signing keys. |
| No frontend Docker context exclusions | A local `.env.production` or `.env.local` could enter the build stage through `COPY . .`; Next.js can consume it during build. No committed real environment file was found. | Added Docker exclusions to both build contexts for environment files, private keys, credential files, dependencies and generated outputs. |
| Git ignored `.env` and `.env.local` only | `.env.production`, `.env.development` and similar variants were eligible for accidental commits. | Ignore all `.env.*` variants, retain `.env.example` templates, and exclude common private-key/credential/dump formats. |
| Gemini raw error bodies/messages and Upstox stream diagnostics logged | Provider text or authorization URLs can echo credentials into logs. This is a potential logging path, not evidence that a real secret was exposed. | Log HTTP status or exception type instead of raw Gemini responses; omit raw Upstox stream details. Production Gemini preflight no longer prints provider bodies. |
| Fixed passwords in staging smoke/load scripts | Publicly predictable credentials for synthetic accounts, not production administrator or database credentials. | Generate fresh passwords per execution. |
| Default local database password | A development convenience, not a leaked production connection string. | Remove backend default and require an explicit environment-supplied local Compose password. |
| Old/current `docs/TRADING.md` idempotency UUID | False positive from the generic API-key detector. An idempotency UUID is a request identifier, not an authentication credential. | Exact-match exception in `.gitleaks.toml`, preserving all default rules. Review by 2026-11-22; no file-wide or provider-wide exclusions. |

No confirmed real Stripe, OpenAI, Firebase, Gemini, Alpaca, Upstox, AWS, SMTP,
private-key or database credential was found in the scanned source/history.
Templates contain placeholders and local addresses, not live connection secrets.
History rewriting is not indicated by these findings.

## Frontend/backend boundary

The only application environment value intentionally exposed to the browser is
`NEXT_PUBLIC_API_URL`. It is a public backend address, not a credential. Next.js
configuration rejects any other `NEXT_PUBLIC_*` variable and API URLs containing
userinfo, query strings or fragments. Do not put private values in Next.js `env`
configuration: that mechanism also exposes them to the client.

Gemini, Alpaca, Upstox and Razorpay signing credentials remain in Java backend
configuration, supplied at runtime from the server environment. No provider-secret
migration into a frontend proxy was needed: those operations already run on the
backend. Production frontend Compose receives no backend credentials.

Razorpay's checkout key ID is a public identifier; its key secret and webhook
secret are not returned to the browser. Learner-specific short-lived access tokens
and email/reset tokens are part of authentication flows, not shared service keys.

## Verification and ongoing prevention

- Complete available history and tracked-source Gitleaks scans: no actionable
  finding after the documented exact UUID exception.
- Optimized frontend build and 22-secret/444-artifact isolation check: passed.
- TypeScript typecheck and browser-JavaScript Gitleaks scan: passed.
- Negative probes: direct private variable reads, aliases, dynamic environment
  reads, public provider keys, and credential-bearing public URLs were rejected.
- Backend regression tests cover rejected JWT defaults and provider-error
  credential echoes in both logs and learner responses.
- CI now builds through `node scripts/verify-frontend-secrets.mjs`. The existing
  secret-history job explicitly scans all fetched refs with fully redacted output;
  it needs only read access and no pull-request write token.

Reproduce the principal checks after installing frontend dependencies:

```bash
node scripts/verify-frontend-secrets.mjs
(cd frontend && npm run typecheck)
gitleaks git . --config .gitleaks.toml --log-opts="--all" --redact=100
gitleaks dir frontend/.next/static --redact=100
(cd backend && mvn -B test)
```

## Deployment and remaining visibility limits

Keep server `.env` files untracked with mode 600 and independent random database,
Redis, JWT, SMTP/provider, monitoring and billing secrets. Compose passes backend
values at runtime; never pass private credentials as frontend build arguments.
Generate independent local values before starting the development stack:

```bash
openssl rand -hex 32
```

Existing production configuration already requires explicit credentials. This
change does not require new production environment names or a database migration.
If `JWT_SECRET` is the former fallback or a template, replace it with a fresh
random value before deployment; changing it invalidates existing access tokens.
If any real credential was previously shared outside the repository, rotate it
at its issuer; this code/history scan cannot establish that exposure.

This audit does not read GitHub secret values, VPS `.env` files, production images,
provider dashboards, old CI artifacts/log archives, external forks or unreachable
rewritten Git objects. It verifies the freshly built candidate's browser output,
not every historical deployed bundle. No actual production credential was used
for testing. A clean secret scan is not a complete penetration-test sign-off.
