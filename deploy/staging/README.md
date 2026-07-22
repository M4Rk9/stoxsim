# Private staging operations

This bundle runs the complete StoxSim staging environment on one private Linux host. Caddy terminates HTTPS and proxies WebSockets, while PostgreSQL and Redis remain on an internal Docker network with no host ports.

This layout is intended for the first private staging checkpoint. Production should move PostgreSQL and Redis to managed services with independent backups before public launch.

## Host prerequisites

- A Linux host with Docker Engine and Docker Compose v2
- A non-root deployment user allowed to run Docker
- Inbound TCP ports 22, 80 and 443; inbound UDP 443 is optional but enables HTTP/3
- DNS records for `staging.stoxsim.com` and `api.staging.stoxsim.com` pointing to the host
- No other process bound to ports 80 or 443

Only the deployment user and administrators should have SSH access. Pin the host key in GitHub; never disable SSH host-key checking.

## First-time host setup

Create the remote directory and copy the environment template:

```bash
ssh deploy@your-host 'mkdir -p stoxsim-staging'
scp deploy/staging/.env.example deploy@your-host:stoxsim-staging/.env
ssh deploy@your-host 'chmod 600 stoxsim-staging/.env'
```

Edit `.env` on the host and replace every placeholder. Keep `UPSTOX_STREAM_ENABLED=false` until the private deployment is healthy and the read-only token is configured.

Generate independent database and JWT secrets. Do not reuse a developer password, GitHub token or Upstox token as `JWT_SECRET`.

## GitHub staging environment

Create a protected GitHub environment named `staging` and configure:

| Type | Name | Example/purpose |
|---|---|---|
| Variable | `STAGING_WEB_URL` | `https://staging.stoxsim.com` |
| Variable | `STAGING_API_URL` | `https://api.staging.stoxsim.com` |
| Variable | `STAGING_REMOTE_DIR` | `stoxsim-staging` |
| Secret | `STAGING_HOST` | Hostname or IP used by SSH |
| Secret | `STAGING_PORT` | SSH port; use `22` normally |
| Secret | `STAGING_USER` | Non-root deployment user |
| Secret | `STAGING_SSH_PRIVATE_KEY` | Dedicated deployment private key |
| Secret | `STAGING_SSH_KNOWN_HOSTS` | Pinned `ssh-keyscan` output verified out of band |
| Secret | `STAGING_GHCR_USERNAME` | GitHub user that can read the images |
| Secret | `STAGING_GHCR_TOKEN` | Fine-grained token with package read access only |

Use environment reviewers if more than one person can deploy. The workflow serializes deployments so two releases cannot mutate staging simultaneously.

## Deploy

1. Run **Staging candidate** from the tested `main` commit. Set its public API URL to the configured `STAGING_API_URL`.
2. Copy the immutable 40-character SHA from the workflow summary.
3. Run **Staging deploy** with that SHA.

The deploy workflow:

1. Pins and verifies the SSH host identity.
2. Uploads this versioned operations bundle without replacing the host `.env`.
3. Authenticates the host to GitHub Container Registry.
4. Pulls and starts the immutable application images.
5. Waits for PostgreSQL, Redis, Spring Boot and Next.js health checks.
6. Runs the HTTPS API smoke test and deployed Chromium learner journey.
7. Rolls back to the previous healthy image tag if external verification fails.

## Backups

Create an encrypted/off-host copy of every generated backup. A dump on the same server is not sufficient disaster recovery.

```bash
cd ~/stoxsim-staging
./backup.sh
```

The script creates a PostgreSQL custom-format dump and a SHA-256 checksum under `backups/`, then removes local dumps older than `BACKUP_RETENTION_DAYS`.

Example daily cron entry:

```cron
15 2 * * * cd /home/deploy/stoxsim-staging && flock -n /tmp/stoxsim-backup.lock ./backup.sh >> backup.log 2>&1
```

Test restoration before relying on the backup:

```bash
cd ~/stoxsim-staging
CONFIRM_STAGING_RESTORE=restore ./restore.sh backups/stoxsim-YYYYMMDDTHHMMSSZ.dump
```

Restore stops all public application services, replaces the staging database, verifies the checksum when present and starts the stack only after the restore succeeds.

## Manual rollback and inspection

```bash
cd ~/stoxsim-staging
./rollback.sh
docker compose --env-file .env -f compose.yml ps
docker compose --env-file .env -f compose.yml logs --tail=200 backend caddy
```

The current image tag lives in `.env`; the immediately previous immutable tag lives in `.previous-image-tag`.
