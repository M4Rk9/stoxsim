# Public release sign-off

Use this checklist for the first public beta and every subsequent production release. Keep private provider correspondence, credentials, database dumps and user data outside the public repository. Record only dated evidence references here or in a private release ticket.

## Release identity

- Candidate commit SHA:
- Version:
- Verification date:
- Release operator:
- Production deployment run:
- Private evidence location:

## Market-data permission gate

- [ ] Upstox written approval covers the deployed feed, authenticated end-user display, caching, derived values, WebSocket fan-out and required attribution.
- [ ] Alpaca written approval covers the deployed feed, authenticated end-user display, caching, derived values and required attribution.
- [x] SEC EDGAR technical and fair-access review is documented in [SEC_EDGAR_COMPLIANCE.md](SEC_EDGAR_COMPLIANCE.md).
- [ ] The production configuration and UI match every provider requirement.
- [ ] Approval dates and private evidence references are recorded without publishing correspondence.
- [ ] A provider can be disabled without breaking authentication, account access or account deletion.

Public launch remains blocked while either provider approval is missing.

## Automated release gates

Run every gate against the final candidate commit and retain links to successful runs:

- [ ] CI: backend tests, frontend typecheck/build, deployment validation and browser acceptance.
- [ ] CodeQL: Java/Kotlin and JavaScript/TypeScript analyses.
- [ ] Security DAST against **production**.
- [ ] Production uptime manual run.
- [ ] Production deployment completed using the same immutable candidate SHA.
- [ ] No unresolved critical or high-severity security alert applies to the candidate.

## Production acceptance

Use a new dedicated acceptance account and remove it after verification.

| Journey | Expected result | Evidence |
|---|---|---|
| Registration and consent | Account is created only after accepting current legal documents | |
| Email verification | Verification message arrives and the account becomes verified | |
| Sign out and sign in | Session ends cleanly and the user can authenticate again | |
| Password recovery | Reset message arrives, token is single-use and the new password works | |
| India portfolio | Starts at ₹5,00,000 and India market data is labelled correctly | |
| US portfolio | Starts at $10,000 and the deployed Alpaca feed is labelled correctly | |
| Watchlist | Add/remove persists after refresh and sign-in | |
| Research | Charts and provider-attributed fundamentals load without blocking the rest of the page | |
| Paper order | Buy and sell validation, execution, charges and order history are correct | |
| Holdings/portfolio | Current holdings, cash, value and P/L reflect executed paper trades | |
| Market separation | India and US balances, orders and holdings remain isolated | |
| FinWiz | Produces an educational response with its disclaimer and no secret/provider error leakage | |
| Appearance | First visit is light; the explicit user choice persists after sign-in | |
| Account export | Download contains only the authenticated user's data | |
| Account deletion | Confirmation deletes the acceptance account and revokes sessions | |
| Mobile/accessibility | Core journeys work at a narrow viewport and with keyboard navigation | |

Do not paste access tokens, email links, request bodies or personal data into release evidence.

## Operations and recovery

- [ ] All Prometheus targets report `up`.
- [ ] Grafana dashboard loads through the private SSH tunnel.
- [ ] Alertmanager health is green and a controlled firing/resolved notification reached the monitored inbox.
- [ ] The latest PostgreSQL backup and checksum exist in encrypted S3 storage.
- [ ] The most recent restore drill evidence is retained.
- [ ] `.previous-image-tag` and `.previous-deployment-bundle.tgz` exist on the production host.
- [ ] Available disk, memory and Docker log rotation are within operating limits.
- [ ] `support.stoxsim@gmail.com` receives inbound support and incident mail.
- [ ] The status page and independent five-minute uptime workflow are active.

## Legal and product review

- [ ] Terms, Privacy Notice, Cookie Notice and Risk Disclaimer display the current effective version.
- [ ] Registration records acceptance of the current legal version.
- [ ] StoxSim is consistently described as educational paper trading, not a broker or investment adviser.
- [ ] Provider attribution and delayed/limited-feed labels match written permissions.
- [ ] Known limitations are listed in the release notes.

## Release decision

- [ ] Rename the changelog's `Unreleased` section to the approved semantic version and date.
- [ ] Create an annotated `v1.0.0` tag from the verified production commit.
- [ ] Publish GitHub release notes linking the changelog and production URL.
- [ ] Monitor alerts, logs, support inbox and key user journeys closely for 24–48 hours.
- [ ] Record the final go/no-go decision and operator.

**Go/no-go:**  
**Signed by:**  
**Date:**  
**Notes:**
