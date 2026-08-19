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
