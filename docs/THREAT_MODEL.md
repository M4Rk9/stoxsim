# StoxSim threat model

Last reviewed: 2026-08-27

## Scope

This model covers the public StoxSim web application, API, WebSocket market stream, PostgreSQL and Redis data stores, monitoring stack, GitHub Actions delivery path, and external market-data, AI, and email providers. StoxSim is a paper-trading product: it must never accept deposits, execute real brokerage orders, or present simulated holdings as real assets.

## Assets

- password hashes, email addresses, profile details, legal-consent records, and security-event history;
- access tokens, refresh-session records, email-verification tokens, and password-reset tokens;
- virtual balances, orders, trades, holdings, watchlists, weekly report snapshots, delivery preferences, learning progression, mission completions, achievements, competition entries, private-league memberships, and account exports;
- PostgreSQL backups and operational logs;
- JWT, database, Redis, SMTP, Gemini, Upstox, Alpaca, Grafana, and deployment credentials;
- provider agreements and the authorization to redistribute market data;
- release artifacts and the GitHub Actions path that publishes and deploys them.

## Trust boundaries

1. The public browser crosses TLS at Caddy before reaching the Next.js frontend or Spring API.
2. The API crosses an internal network boundary to PostgreSQL, Redis, and monitoring.
3. The API crosses the internet to Resend SMTP, Gemini, Upstox, Alpaca, and SEC endpoints.
4. GitHub Actions crosses into GHCR and the deployment host using environment-scoped secrets.
5. Operators cross SSH to localhost-bound Grafana, Prometheus, and Alertmanager interfaces.

Only Caddy ports 80 and 443 are intended to be public. Database, Redis, application, and monitoring ports must not be reachable from the internet.

## Threats and controls

| Threat | Primary controls | Required verification |
|---|---|---|
| Credential stuffing and account enumeration | Generic login/reset failures, bcrypt cost 12, Redis-backed auth rate limits, email verification | Confirm rate-limit alerts and identical password-reset responses |
| JWT forgery or token substitution | HS256 secret of at least 32 characters, required stoxsim issuer, expiration validation, 15-minute access lifetime | Unit-test foreign issuer and expired/invalid tokens; rotate JWT_SECRET on exposure |
| Refresh-token theft or replay | 48-byte random values, only hashes stored, rotation, session revocation, Secure/HttpOnly/SameSite=Strict host-only cookie | Verify cookie attributes over HTTPS and revoke all sessions after password reset |
| Cross-user data access (IDOR) | User ID comes only from the validated JWT subject; repository queries bind resource ID and owner ID; foreign resources return 404 | Test order, watchlist, session, export, portfolio, and ledger paths with two accounts |
| CSRF and malicious origins | Stateless bearer API, strict refresh cookie, exact-origin CORS allowlist, no wildcard credentials | Security smoke rejects an untrusted preflight origin |
| XSS, clickjacking, and unsafe embedding | CSP, frame-ancestors 'none', X-Frame-Options: DENY, output escaping, restrictive permissions policy | Browser acceptance plus deployed header and ZAP baseline checks |
| Secret exposure | Environment-only secrets, ignored production files, GitHub environments, Gitleaks history scan, generic error responses | Zero committed credentials; rotate immediately if a finding is confirmed |
| Vulnerable dependencies or images | Dependabot, dependency review, CodeQL, Trivy repository/config scan, non-root runtime users | No unresolved actionable high or critical finding at release sign-off |
| API abuse and provider cost exhaustion | Endpoint-specific limits, question/output bounds, provider timeouts, bounded logs and metrics | Exercise 429 behavior; investigate Redis fail-open alerts immediately |
| Unwanted or duplicate report email | Explicit opt-in, verified-email requirement, unique weekly periods, persisted status and three-attempt cap | Confirm disabled-by-default settings and idempotent scheduler tests |
| XP farming, duplicate awards, or client-forged progress | Server-owned mission rules, per-user row locks, unique mission/achievement constraints, no profit or volume rewards | Run concurrent/idempotency tests and reject any client-supplied XP or completion state |
| Invite guessing or private-standing disclosure | 144-bit random codes, hash-only storage, bounded join attempts, member-bound lookups and foreign-resource `404` responses | Test invalid invites, rotation, league capacity and two-account authorization |
| Paid capital influencing standard rank | Exact server-side standard-account eligibility and entry-relative percentage scoring independent of XP or plan | Reject non-₹5 lakh accounts and keep future sandbox account types structurally separate |
| Database or monitoring exposure | Docker internal networks, no database host ports, localhost-only monitoring ports, Caddy metrics denial | Verify the Lightsail firewall and scan the public host from outside the server |
| Supply-chain or deployment compromise | Protected main, required review/checks, commit-SHA image tags, restricted GitHub environments | Review workflow changes, verify image SHA, and preserve rollback evidence |
| Backup or log disclosure | Restricted host access, log redaction, bounded retention, encrypted-provider storage where available | Restore-test backups and confirm logs contain no tokens, passwords, or account exports |
| Market-data misuse | Launch gate requires written redistribution permission or an explicitly authorized delayed/demo feed | Keep public market features disabled until the separate licensing gate is satisfied |

## Important residual risks

- Rate limiting intentionally fails open when Redis is unavailable so an infrastructure fault does not lock out all users. The storage-failure alert is therefore a security signal and requires prompt response.
- A shared HMAC JWT secret means every component holding the secret can mint tokens. Limit the secret to the API host and rotate it after any suspected disclosure.
- The frontend CSP permits inline script and style execution for Next.js compatibility. Removing those allowances requires nonce-based rendering and remains a future hardening item.
- Passive DAST does not replace authenticated penetration testing. Before a larger launch, test two-account authorization paths with a dedicated non-production dataset.
- Third-party availability, data accuracy, and licensing remain outside StoxSim's direct control.

## Review triggers

Review this model whenever authentication, account export/deletion, a new public endpoint, a new provider, real-money functionality, deployment topology, stored personal data, or market-data rights change. Also review it after every security incident and before each public release.
