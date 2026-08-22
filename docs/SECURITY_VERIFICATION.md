# Security verification and release gate

Milestone 6 turns security checks into evidence that can be reviewed before public release. It does not deploy production.

## Automated merge gates

Every pull request to main must pass:

- backend tests, including JWT issuer and refresh-cookie regression tests;
- frontend typecheck, production build, and authenticated browser journey;
- CodeQL extended queries for Java and JavaScript/TypeScript;
- dependency review for newly introduced high-severity vulnerabilities;
- Gitleaks scanning across complete Git history;
- Trivy filesystem scanning for high/critical dependency and configuration findings;
- deployment Compose, Caddy, monitoring, and shell validation.

Do not suppress a result merely to make a check green. Record why it is a false positive, scope the narrowest possible exception, and add an expiry/review date.

## On-demand deployed verification

The Security DAST workflow only accepts the repository's approved staging or production URLs.

For staging:

1. Deploy the exact candidate commit to staging.
2. During the regular United States trading session, open **Actions → Security DAST → Run workflow**. The workflow queries Alpaca's clock once with environment-scoped credentials; it does not expose that provider call through a learner API. Both temporary orders must execute so holdings, portfolio and ledger isolation are tested with real owner data.
3. Select **staging**.
4. Confirm the security smoke contract passes:
   - HTTPS security headers are present;
   - protected APIs reject missing and invalid bearer tokens;
   - /actuator/prometheus is not public;
   - authentication responses are marked no-store;
   - an untrusted CORS origin is not reflected.
5. Review the OWASP ZAP passive-baseline output. Resolve every failure and investigate warnings before sign-off.
6. Confirm the public-port audit reports only TCP 80 and 443 open. TCP 22, 3000, 3001, 5432, 6379, 8080, 9090 and 9093 must be closed or filtered from the GitHub-hosted runner.
7. Confirm the automated two-user authorization exercise passes: both temporary orders must execute, both learners must have non-empty holdings and ledger data, and neither learner may read or mutate the other learner's orders, sessions, watchlist items, holdings, ledger, events, or export. The exercise verifies deletion of both temporary accounts on exit.

After production deployment, repeat the workflow with **production** before enabling public announcements.

## Reviewed ZAP baseline exceptions

The committed `.zap/rules.tsv` changes only the severity of reviewed alerts. The workflow passes it directly to ZAP and continues to fail on every unknown warning or failure.

| Rule | Public-beta decision | Compensating control | Review deadline |
| --- | --- | --- | --- |
| 10019 Content-Type Header Missing | Accepted only for Next.js bodyless 308 canonical redirects. | Content-bearing pages and assets retain explicit content types. | Before general availability or 2026-11-22, whichever comes first. |
| 10055 CSP `unsafe-inline` | Temporarily accepted because Next.js server-rendered bootstrap scripts require inline execution in the current static/Caddy deployment. | `default-src 'self'`, `object-src 'none'`, and `frame-ancestors 'none'` remain enforced. The security smoke test rejects broad HTTPS and wildcard image or script sources. | Replace with per-request CSP nonces before general availability or 2026-11-22, whichever comes first. |

The other INFO entries cover documented framework behavior or non-security metadata. Re-review every entry before general availability and whenever the frontend framework, reverse proxy, or rendering mode changes.

## Host and secret review

On the deployment host and in the Lightsail firewall:

- expose TCP 80 and 443 publicly; expose UDP 443 only when HTTP/3 is desired. The Security DAST workflow automatically verifies the TCP contract against the environment's `STAGING_HOST` or `PRODUCTION_HOST` secret;
- restrict TCP 22 to the administrator's current IP or an approved management network;
- do not expose 3000, 3001, 5432, 6379, 8080, 9090, or 9093;
- confirm Grafana, Prometheus, and Alertmanager listen only on 127.0.0.1;
- use unique random values for database, Redis, JWT, metrics, and Grafana credentials;
- keep Resend, Gemini, Upstox, Alpaca, and deploy credentials out of shell history, logs, backups, and the repository;
- rotate any credential that was ever pasted into a public location or committed, even if later removed;
- require GitHub environment approval for production deployment and give workflows only the permissions they need.

## Release acceptance criteria

Milestone 6 is complete only when:

- all automated security jobs pass on the milestone PR;
- CodeQL, Dependabot, dependency review, Gitleaks, and Trivy show no unresolved actionable high or critical finding;
- staging DAST and the two-account authorization exercise pass against the candidate commit;
- the public port scan matches the intended Caddy-only exposure;
- a current backup restore test is recorded;
- security reporting through GitHub private vulnerability reporting is available;
- the reviewer records the candidate commit SHA, workflow run links, findings, exceptions, and approval date.

Market-data redistribution permission is a separate public-release gate and remains mandatory even when every security check passes.

## Sign-off record

Copy this section into the release issue or release PR:

- Candidate commit:
- Automated security run:
- Staging DAST run:
- Two-account authorization result:
- External port-scan result:
- Backup restore evidence:
- Open findings or approved exceptions:
- Reviewer:
- Approval date:
