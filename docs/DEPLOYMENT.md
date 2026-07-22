# Production deployment

StoxSim deploys as a Next.js frontend, one Spring Boot API instance, PostgreSQL and Redis. Keep the India MVP API at one replica because it owns the Upstox WebSocket and uses Spring's in-memory STOMP broker. Horizontal API scaling requires a shared message broker and coordinated upstream subscriptions first.

## Required services

- Container host with Java 21 support for `backend/Dockerfile`
- Next.js-compatible container or Node.js host for `frontend/Dockerfile`
- Managed PostgreSQL with automated backups and point-in-time recovery
- Managed Redis with authentication, encryption and persistence appropriate to the provider
- HTTPS ingress with WebSocket upgrade support
- Secret manager for JWT and Upstox credentials

## Production environment

```env
DATABASE_URL=jdbc:postgresql://database-host:5432/stoxsim
DATABASE_USERNAME=stoxsim
DATABASE_PASSWORD=use-a-secret-manager
REDIS_HOST=redis-host
REDIS_PORT=6379
FRONTEND_URL=https://stoxsim.com
NEXT_PUBLIC_API_URL=https://api.stoxsim.com
JWT_SECRET=at-least-32-random-bytes-from-a-secret-manager
UPSTOX_ANALYTICS_TOKEN=read-only-analytics-token
UPSTOX_STREAM_ENABLED=true
SIMULATED_SLIPPAGE_BASIS_POINTS=5
```

`NEXT_PUBLIC_API_URL` is embedded when the frontend image is built. `UPSTOX_ANALYTICS_TOKEN` and `JWT_SECRET` must only be supplied to the backend at runtime and must never use a `NEXT_PUBLIC_` name.

## HTTPS and WebSockets

The browser converts the public API URL to `wss://api.stoxsim.com/ws/market` and subscribes to `/topic/market/quotes`. The ingress must preserve the WebSocket upgrade:

```nginx
location /ws/market {
    proxy_pass http://stoxsim-api:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Set `FRONTEND_URL` to the exact public browser origin. Spring uses it for REST CORS and the STOMP handshake. Forwarded headers are enabled so redirects and scheme detection work behind HTTPS ingress.

## Release sequence

1. Back up PostgreSQL and verify restore access.
2. Deploy the backend; Flyway applies pending migrations before Spring reports ready.
3. Check `/actuator/health/liveness` and `/actuator/health/readiness`.
4. Verify the instrument synchronization completed or run it before market hours.
5. Build and deploy the frontend with the public API URL.
6. Sign in, load a quote, add it to the watchlist and verify `REAL-TIME STREAM` appears.
7. Restart the API and confirm saved watchlist subscriptions and the Upstox feed recover.

Use rolling deployment with a single active API instance for the MVP. Do not let two instances use the same Upstox token concurrently unless the provider limits and a coordinated subscription design explicitly support it.

## Staging checkpoint

The first private checkpoint uses a single Linux host and the bundle under `deploy/staging`. Caddy owns ports 80 and 443, obtains certificates and proxies both normal API traffic and WebSocket upgrades. PostgreSQL and Redis are reachable only through an internal Docker network.

The repository includes three manual GitHub Actions workflows:

1. **Staging candidate** builds the backend and frontend images and publishes both an immutable commit-SHA tag and the movable `staging` tag to GitHub Container Registry.
2. **Staging smoke** checks readiness, the rendered frontend, registration, both opening balances, authenticated identity and refresh-token rotation against the deployed HTTPS origins.
3. **Staging deploy** securely uploads the operations bundle over pinned SSH, deploys one immutable candidate, runs the HTTPS smoke and Chromium learner journey, and rolls back after failed external verification.

Deploy only the immutable SHA shown in the candidate workflow summary. The host must already contain a mode-`600` `.env`; deployments never copy secrets from the repository or replace that file:

```bash
cd deploy/staging
cp .env.example .env
# Set secrets, managed PostgreSQL/Redis hosts and the tested commit SHA.
docker compose pull
./deploy.sh "$STOXSIM_IMAGE_TAG"
```

Configure the protected `staging` GitHub environment and follow the [private staging operations runbook](../deploy/staging/README.md). Keep the staging host private until market-data display permission is confirmed.

### Rollback

Keep the previously healthy commit SHA. If readiness or smoke checks fail:

```bash
cd deploy/staging
./rollback.sh
```

Flyway migrations must remain backward compatible with the previous application image. If a future migration is destructive or not backward compatible, it requires a separately tested database restoration plan before deployment.

### Backup and restore

Run `deploy/staging/backup.sh` on a daily schedule and copy its custom-format PostgreSQL dump plus checksum to encrypted off-host storage. Regularly prove the restore procedure with `restore.sh`; an untested local-only dump is not a recovery plan.

## Monitoring and alerts

Alert on:

- readiness or liveness failures
- PostgreSQL or Redis connection failures
- Flyway migration failures
- Upstox disconnects and exhausted reconnect attempts
- quotes older than the configured stale threshold
- instrument synchronization failures before market open
- elevated authentication failures and HTTP 5xx responses

Application logs must not contain access tokens, JWT secrets, passwords or full authorization headers.

## Market-data permission

The SDK licence does not grant market-data redistribution rights. Before opening StoxSim to public users, obtain written confirmation that the selected Upstox/exchange agreement permits server-side display of real-time data to simulator users. Until then, keep access private or label an authorized delayed/demo feed accurately.
