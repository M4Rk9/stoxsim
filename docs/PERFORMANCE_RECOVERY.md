# Recovery verification policy

The hosted `staging.stoxsim.com` environment and the staging performance/recovery workflow were retired before the v1.0.0 release. The previous staging runs remain historical evidence only and must not be treated as verification of the current production candidate.

## Current release requirement

Before public launch, verify that the latest production PostgreSQL backup and its SHA-256 checksum exist in encrypted off-host storage. Confirm that the production rollback image tag and deployment bundle are present.

A restore drill must use an isolated temporary PostgreSQL instance or a disposable environment. It must never replace, truncate or otherwise mutate the live production database.

The isolated drill must prove that:

1. the selected backup checksum is valid;
2. PostgreSQL accepts the archive;
3. migrations and the application can start against the restored copy;
4. a known non-sensitive marker can be read from the restored copy;
5. the temporary database and any credentials are removed after evidence is retained.

Record the backup identifier, candidate commit, verification date and private evidence reference in the release sign-off. Never commit database dumps, credentials or user data.
