# Production operations and incident response

This runbook is the operating guide for the StoxSim public beta. It covers the single-host AWS Lightsail production checkpoint defined in `deploy/production`.

## Service objectives

| Objective | Public-beta target | Measurement |
|---|---:|---|
| Monthly availability | 99.5% | External HTTPS probes for the web application and API readiness |
| API latency | p95 below 2 seconds | Spring HTTP server histogram over a rolling five-minute window |
| Server error rate | Below 5% | HTTP 5xx responses divided by total API responses |
| Recovery time objective | 2 hours | Time from a declared critical incident to restored service |
| Recovery point objective | 24 hours | Daily verified PostgreSQL backup copied to encrypted off-host storage |

These are operational targets, not contractual service-level agreements.

## Monitoring architecture

- Spring Boot emits Prometheus JVM, HTTP, datasource and custom StoxSim metrics.
- `X-Request-ID` is returned on API responses and included in structured ECS JSON logs.
- Prometheus retains 15 days of metrics locally.
- Blackbox Exporter probes `https://stoxsim.com` and API readiness from the production host.
- Node Exporter reports Lightsail CPU, memory, disk and filesystem health.
- Alertmanager sends firing and resolved alerts through the verified Resend SMTP account to `support.stoxsim@gmail.com`.
- Grafana provisions the **StoxSim Production Overview** dashboard.
- GitHub Actions performs an independent external probe every five minutes after `PRODUCTION_UPTIME_ENABLED=true` is configured.
- `https://stoxsim.com/status` provides a user-facing web/API status view.

Prometheus metrics require a separate 32-character scrape token. Caddy returns 404 for public requests to `/actuator/prometheus`, even if the application-level token is supplied.

## Required production variables

Add these to the mode-`600` `deploy/production/.env` on the host:

```env
ALERT_EMAIL_TO=support.stoxsim@gmail.com
METRICS_SCRAPE_TOKEN=generate-a-separate-random-value-of-at-least-32-characters
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=generate-a-long-unique-password
LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
```

Generate secrets on the host:

```bash
openssl rand -hex 32
```

Do not reuse the JWT secret, Redis password, database password or Resend key as the metrics or Grafana credential.

## First monitoring startup

The deployment script renders secret-bearing Alertmanager configuration and the Prometheus token file from `.env`. It does not print secret values.

```bash
cd ~/stoxsim-production
chmod 600 .env
python3 render-monitoring-config.py
docker compose --env-file .env -f compose.yml config --quiet
docker compose --env-file .env -f compose.yml up -d --wait
```

Verify targets and alerts through SSH tunnels from your workstation:

```bash
ssh -L 3001:127.0.0.1:3001 \
    -L 9090:127.0.0.1:9090 \
    -L 9093:127.0.0.1:9093 \
    ubuntu@PRODUCTION_STATIC_IP
```

Then open:

- Grafana: `http://127.0.0.1:3001`
- Prometheus targets: `http://127.0.0.1:9090/targets`
- Prometheus alerts: `http://127.0.0.1:9090/alerts`
- Alertmanager: `http://127.0.0.1:9093`

Every Prometheus target must show **UP**. Change the Grafana admin password immediately if the host `.env` ever used a temporary value.

## External uptime workflow

In repository settings, add this Actions variable:

```text
PRODUCTION_UPTIME_ENABLED=true
```

Enable it only after the production DNS records and deployment are live. Before then, use the workflow's manual dispatch to test without creating scheduled pre-launch failures.

GitHub Actions failures are a second signal; they do not replace the on-host alerts.

## Log handling

Production sets Spring Boot's console format to ECS JSON. Docker must rotate logs so a noisy service cannot fill the Lightsail disk. Configure `/etc/docker/daemon.json`:

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "5"
  }
}
```

Restart Docker during a planned maintenance window:

```bash
sudo systemctl restart docker
```

Logs must never include passwords, access or refresh tokens, API keys, full authorization headers, reset links, verification tokens, request bodies or email addresses. Use request IDs, endpoint templates, response status and bounded operational categories instead.

Search one request without copying all logs:

```bash
docker compose --env-file .env -f compose.yml logs --no-color backend \
  | grep 'REQUEST_ID_FROM_RESPONSE'
```

## Alert response guide

| Alert | First checks | Immediate action |
|---|---|---|
| PublicEndpointDown | Caddy, backend/frontend health, DNS and certificate | Restore the previous image if the outage followed a deploy |
| ApiMetricsUnavailable | Backend health and `METRICS_SCRAPE_TOKEN` rendering | Restore token agreement or restart the backend |
| HighHttp5xxRate | Request IDs, backend logs, database/Redis health | Stop the failing change; rollback if correlated with deployment |
| HighApiLatency | CPU, memory, DB pool and provider latency | Reduce load, inspect slow dependency, rollback regression |
| DatabasePoolSaturation | PostgreSQL health, long queries and active connections | Stop runaway work; restart only after capturing evidence |
| RateLimitStorageUnavailable | Redis health, memory and authentication | Restore Redis immediately; rate limiting is failing open |
| MarketProviderFailures | Provider status, credentials, quota and network | Disable affected live feature or clearly show stale/unavailable data |
| UpstoxReconnectExhausted | Upstox token, provider status and stream logs | Restart once after cause is known; keep India live data unavailable if unresolved |
| FinwizProviderFallbacks | Gemini response codes, quota and model availability | Keep deterministic educational fallback; avoid repeated blind retries |
| HostDiskSpaceLow | Docker images/logs and backup retention | Preserve verified backups, rotate logs, then prune unused images |
| HostMemoryLow | Container memory, Java heap and OOM events | Stop nonessential work; resize Lightsail if pressure is sustained |

## Incident severity

- **SEV-1:** Complete public outage, authentication unavailable, data corruption, credential exposure or unauthorized access.
- **SEV-2:** Major feature unavailable, sustained elevated errors, rate limiting failing open or materially stale/mislabelled market data.
- **SEV-3:** Limited degradation with a working fallback and no security or data-integrity risk.

## Incident procedure

1. **Acknowledge:** Record UTC start time, severity, affected components and the first alert.
2. **Stabilize:** Stop further deployments. Prefer rollback over live production edits.
3. **Protect data:** If integrity is uncertain, stop write-capable public services before investigation.
4. **Collect evidence:** Save request IDs, image SHA, alert timeline, relevant bounded logs and health output. Never copy secrets into an issue.
5. **Communicate:** Update the public status page message when possible and use `support.stoxsim@gmail.com` for user reports.
6. **Recover:** Run health checks, production smoke checks and the core learner journey after rollback or repair.
7. **Observe:** Watch error rate, latency and provider metrics for at least 30 minutes.
8. **Close:** Record end time, user impact, cause, recovery action and follow-up owner.
9. **Review:** Complete a blameless incident review within three days for SEV-1 and SEV-2 incidents.

## Safe diagnostic commands

```bash
cd ~/stoxsim-production
docker compose --env-file .env -f compose.yml ps
docker compose --env-file .env -f compose.yml logs --no-color --tail=300 backend caddy
curl --fail --silent https://api.stoxsim.com/actuator/health/readiness | jq
curl --fail --silent https://stoxsim.com/status >/dev/null
```

Do not run restore, delete volumes, rotate secrets or modify DNS as a diagnostic shortcut.

## Recovery

If a release caused the incident:

```bash
cd ~/stoxsim-production
./rollback.sh
```

For database recovery, follow `deploy/production/README.md`. Restoring a backup permanently replaces the production database and requires the explicit confirmation guard in `restore.sh`.

After recovery:

```bash
PRODUCTION_API_URL=https://api.stoxsim.com \
PRODUCTION_WEB_URL=https://stoxsim.com \
PRODUCTION_SMOKE_EMAIL=support.stoxsim@gmail.com \
bash scripts/production-smoke.sh
```

Use a Gmail plus-address-capable monitored inbox for the smoke account. The script removes its temporary account after the test.

## Incident record template

```markdown
# Incident YYYY-MM-DD: short title

- Severity:
- Started (UTC):
- Resolved (UTC):
- Release SHA:
- User impact:
- Detection:
- Timeline:
- Root cause:
- Recovery:
- Data integrity assessment:
- Follow-up actions and owners:
```
