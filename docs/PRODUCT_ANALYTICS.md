# StoxSim Product Analytics

## Objective

The analytics system must answer three different questions without mixing them together:

1. **Product analytics:** How are learners using StoxSim and where do they stop?
2. **Business/admin analytics:** How many users, active users, portfolios and simulated orders exist?
3. **Operational analytics:** Is the product healthy, fast and receiving usable market data?

These should use separate data paths and access controls.

## Recommended first release

Use a hybrid architecture:

- **PostHog or an equivalent product-analytics service** for funnels, retention, cohorts, feature adoption and optional session replay.
- **StoxSim PostgreSQL + Spring Boot admin APIs** for authoritative user, portfolio and order metrics.
- **OpenTelemetry-compatible telemetry** for API latency, errors, database calls and provider health.
- **A protected `/admin/analytics` page** in the existing Next.js frontend for the product owner.

The admin dashboard must never be accessible only because someone knows its URL. Access must be enforced by the backend using an `ADMIN` role.

## Event taxonomy

Start with a small, stable set of high-value events:

| Event | When it occurs | Important properties |
|---|---|---|
| `user_registered` | Account creation succeeds | market region, acquisition source |
| `session_started` | Authenticated dashboard session starts | platform, app version |
| `stock_searched` | User submits a stock search | query length, result count |
| `stock_opened` | User opens a stock quote or research page | symbol, exchange, source |
| `watchlist_item_added` | User adds a stock | symbol, exchange |
| `paper_order_submitted` | Order passes validation | side, type, symbol, quantity band |
| `paper_order_executed` | Simulated execution completes | side, type, symbol, value band |
| `portfolio_reviewed` | User views holdings/portfolio | holding count band |
| `settings_updated` | Profile or password update succeeds | changed fields only |

Do not include passwords, access tokens, refresh tokens, full order payloads or unnecessary personal information in event properties.

## Activation definition

For the StoxSim MVP, treat a user as **activated** when they complete all of the following within seven days of registration:

1. Open at least one stock research page.
2. Add at least one stock to a watchlist.
3. Submit at least one valid paper order.

Track the conversion rate and median time from registration to activation.

## Core dashboard metrics

### Overview

- Total registered users
- New users today and in the last 7/30 days
- Daily, weekly and monthly active users
- Activated users and activation rate
- D1, D7 and D30 retention
- Total portfolios and total simulated account value
- Orders submitted, executed, rejected and cancelled

### Funnel

`Registered → Opened stock → Added watchlist item → Submitted first order → Executed first order`

Show both conversion percentage and median time between stages.

### Engagement

- Stock searches per active user
- Research pages viewed per active user
- Watchlist additions per active user
- Orders per active user
- Most researched symbols
- Most traded symbols
- Percentage of users returning after first order

### Reliability

- API error rate
- p50/p95 request latency
- Login and registration failure rate
- Market-data provider failures
- Percentage of quotes marked LIVE, STALE and UNAVAILABLE
- Historical-chart request failure rate

## Database design

Business metrics should be calculated from existing authoritative tables wherever possible. Product events that are not already represented in the domain model can use an append-only table.

```sql
create table product_events (
    id uuid primary key,
    user_id uuid null references app_users(id),
    anonymous_id varchar(100) null,
    session_id uuid null,
    event_name varchar(80) not null,
    occurred_at timestamptz not null,
    source varchar(30) not null,
    app_version varchar(40) null,
    properties jsonb not null default '{}'::jsonb
);

create index idx_product_events_name_time
    on product_events (event_name, occurred_at desc);

create index idx_product_events_user_time
    on product_events (user_id, occurred_at desc);
```

For faster dashboards, create a scheduled daily aggregation table after event volume becomes material:

```sql
create table analytics_daily_metrics (
    metric_date date not null,
    metric_name varchar(80) not null,
    dimension_key varchar(80) not null default 'all',
    metric_value numeric(20, 4) not null,
    calculated_at timestamptz not null,
    primary key (metric_date, metric_name, dimension_key)
);
```

## Backend APIs

Suggested protected endpoints:

```text
GET /api/v1/admin/analytics/overview?from=2026-08-01&to=2026-08-31
GET /api/v1/admin/analytics/funnel?windowDays=7
GET /api/v1/admin/analytics/retention?cohort=week
GET /api/v1/admin/analytics/engagement?from=...&to=...
GET /api/v1/admin/analytics/reliability?from=...&to=...
```

Every endpoint must require `ROLE_ADMIN`. Do not return raw password data, tokens or unrestricted personally identifiable information.

## Admin authorization

Add explicit role storage rather than using a hard-coded frontend check.

```text
users
- id
- email
- display_name
- role: USER | ADMIN
```

Enforce authorization in Spring Security using method or route-level checks. The frontend may hide the admin navigation for normal users, but the backend remains the security boundary.

## Frontend structure

```text
frontend/app/admin/analytics/page.tsx
frontend/app/admin/analytics/AnalyticsDashboard.tsx
frontend/app/admin/analytics/analytics.module.css
```

Recommended sections:

1. Date-range selector
2. KPI cards
3. Signup and active-user trend
4. Activation funnel
5. Retention cohorts
6. Feature-adoption table
7. Order-state breakdown
8. Reliability and provider-health panel

The page should query only the protected admin APIs. It should not connect directly to PostgreSQL.

## Delivery sequence

### Phase 1 — launch analytics

1. Add `USER` and `ADMIN` roles.
2. Track the nine core events.
3. Add PostHog or equivalent frontend/server event capture.
4. Build `/api/v1/admin/analytics/overview`.
5. Build a private `/admin/analytics` page with KPI cards and a 30-day trend.
6. Add a privacy notice and event-data retention policy.

### Phase 2 — product decisions

1. Add activation funnel and D1/D7/D30 retention.
2. Add symbol popularity and feature-adoption reports.
3. Add cohorts such as activated users, dormant users and high-engagement learners.
4. Add provider reliability and chart/fundamentals failure dashboards.
5. Add alerting for registration failures, API error spikes and market-data outages.

### Phase 3 — scale

1. Batch event ingestion.
2. Add a queue only when synchronous event writes become measurable overhead.
3. Move analytical workloads to a read replica or warehouse when they begin affecting transactional queries.
4. Add daily materialized aggregates and retention jobs.

## Initial success criteria

The first analytics release is complete when the product owner can answer:

- How many users registered today, this week and this month?
- How many users returned after one, seven and thirty days?
- What percentage reached their first executed paper order?
- At which onboarding step are users dropping out?
- Which stocks and research features are used most?
- Are market data, charts and fundamentals failing for real users?
