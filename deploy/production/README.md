# Public production operations

This bundle runs the first StoxSim public beta on one AWS Lightsail Linux instance. Caddy terminates HTTPS and proxies WebSockets. PostgreSQL and password-protected Redis stay on an internal Docker network with no host ports. Every deployment uses immutable commit-SHA images, creates a database backup before changing a healthy stack and automatically rolls back when health or external smoke checks fail.

This is a cost-conscious public-beta profile, not the final scaling architecture. Move PostgreSQL and Redis to managed services before traffic, availability requirements or team size outgrow a single host.

## Minimum host and network

- Ubuntu LTS or another supported Linux distribution
- Docker Engine and Docker Compose v2
- At least 2 vCPU, 4 GB RAM, 80 GB SSD and 2 GB persistent swap
- A non-root deployment user allowed to run Docker
- TCP 22 restricted to known administrator addresses
- Public TCP 80 and 443; optional UDP 443 for HTTP/3
- No public PostgreSQL or Redis port
- Automated Lightsail snapshots plus PostgreSQL dumps copied to a private S3 bucket
- DNS A/AAAA records for `stoxsim.com`, `www.stoxsim.com` and `api.stoxsim.com`

Do not run the staging and production Caddy containers simultaneously on the same host: both own ports 80 and 443. Prefer separate instances. If budget requires reusing the staging instance, follow the promotion procedure below during a maintenance window.

## First-time setup

Install Docker, the AWS CLI and persistent swap. Create the production directory:

```bash
ssh deploy@your-production-host 'mkdir -p stoxsim-production'
scp deploy/production/.env.example deploy@your-production-host:stoxsim-production/.env
ssh deploy@your-production-host 'chmod 600 stoxsim-production/.env'
```

Edit the host-only `.env` and replace every placeholder. Generate independent PostgreSQL, Redis and JWT secrets. Keep the environment file mode `600`; deployments upload the operations bundle but never overwrite this file.

Configure AWS CLI access for the deployment user and create a private, versioned S3 bucket. Block all public access and apply a lifecycle rule appropriate to the retention policy. `BACKUP_S3_URI` must point to a private prefix. Do not store AWS credentials in the repository or production `.env`.

## DNS and HTTPS

Point these names to the production static IP:

| Name | Purpose |
|---|---|
| `stoxsim.com` | Public web app |
| `www.stoxsim.com` | Permanent redirect to the root domain |
| `api.stoxsim.com` | REST and WebSocket API |

Caddy obtains and renews certificates automatically. Keep the existing Resend records for `mail.stoxsim.com`; production web/API records do not replace them.

## Protected GitHub environment

Create a GitHub environment named `production`, add required reviewers and prevent self-review if another trusted reviewer is available.

Variables:

| Name | Value |
|---|---|
| `PRODUCTION_WEB_URL` | `https://stoxsim.com` |
| `PRODUCTION_API_URL` | `https://api.stoxsim.com` |
| `PRODUCTION_REMOTE_DIR` | `stoxsim-production` |
| `GEMINI_MODEL` | Approved model, currently `gemini-3.6-flash` |
| `ALPACA_DATA_FEED` | Feed covered by the approved plan |
| Provider tuning variables | Match the approved staging configuration |

Secrets:

| Name | Purpose |
|---|---|
| `PRODUCTION_HOST` | Production static IP or hostname |
| `PRODUCTION_PORT` | SSH port, normally `22` |
| `PRODUCTION_USER` | Non-root deployment user |
| `PRODUCTION_SSH_PRIVATE_KEY` | Dedicated production deploy key |
| `PRODUCTION_SSH_KNOWN_HOSTS` | Out-of-band verified host key |
| `PRODUCTION_GHCR_USERNAME` | Account allowed to read packages |
| `PRODUCTION_GHCR_TOKEN` | Token restricted to `read:packages` |
| `PRODUCTION_SMOKE_EMAIL` | Monitored base address, currently `support.stoxsim@gmail.com` |
| `GEMINI_API_KEY` | Production Gemini key |
| `ALPACA_API_KEY_ID` / `ALPACA_API_SECRET_KEY` | Approved production market-data credentials |

SMTP, database, Redis, JWT and Upstox secrets remain in the mode-`600` host `.env`. They are not copied through GitHub Actions.

## Release procedure

1. Merge only a commit with green CI, CodeQL and dependency review.
2. Run **Production candidate** from `main`. It publishes API and web images tagged with the immutable commit SHA and embeds `https://api.stoxsim.com` in the frontend.
3. Copy the 40-character SHA from the workflow summary.
4. Confirm the latest staging deployment used the same SHA successfully.
5. Run **Production deploy** with the SHA and enter `DEPLOY` in the confirmation field.
6. Approve the protected `production` environment.
7. Verify the workflow summary and manually inspect registration, email delivery, India/US portfolios, legal pages and live/stale data labels.

The deployment workflow validates provider credentials, pins the SSH host, uploads the production bundle, authenticates to GHCR, creates a pre-deployment backup, deploys the immutable images and runs self-cleaning HTTPS registration checks. Failed external checks trigger rollback.

## Backups and restore drills

Run daily:

```cron
15 2 * * * cd /home/deploy/stoxsim-production && flock -n /tmp/stoxsim-production-backup.lock ./backup.sh >> backup.log 2>&1
```

The backup is a PostgreSQL custom-format dump with a SHA-256 checksum. With the default `REQUIRE_OFFSITE_BACKUP=true`, the command fails unless both files reach the configured S3 prefix.

Quarterly, restore the latest dump on an isolated non-production host. For an approved production restore:

```bash
CONFIRM_PRODUCTION_RESTORE=replace-production-database \
  ./restore.sh backups/stoxsim-production-YYYYMMDDTHHMMSSZ.dump
```

The restore requires the matching checksum, stops public services and leaves them stopped if replacement fails.

## Rollback

```bash
cd ~/stoxsim-production
./rollback.sh
```

Rollback swaps the current and previous immutable image tags. Flyway migrations must therefore remain backward compatible. Destructive database changes require a separately rehearsed restore and maintenance plan.

## Promoting the existing staging host

If production must temporarily reuse the current Lightsail instance:

1. Schedule downtime and stop public access to staging.
2. Run `deploy/staging/backup.sh` and copy its dump/checksum off-host.
3. Stop staging: `docker compose --env-file deploy/staging/.env -f deploy/staging/compose.yml down`. Do not use `--volumes`.
4. Install this production bundle and configure a new production `.env`.
5. Start only PostgreSQL and Redis from the production bundle.
6. Restore the staging dump using the guarded production restore procedure.
7. Point production DNS to the Lightsail static IP.
8. Run **Production candidate** and **Production deploy**.
9. Confirm HTTPS, email, backups and rollback before opening registration.

The staging and production Compose projects use different named volumes, so the database does not move automatically. The verified dump/restore step is mandatory.
