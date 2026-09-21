# StoxSim Product Analytics

## Implemented owner overview (post-release M1)

The owner overview was merged in PR #123 and deployed. The owner confirmed
dashboard access on 21 September 2026; broader production acceptance is tracked in [issue #122](https://github.com/M4Rk9/stoxsim/issues/122).
See [the milestone sequence](POST_RELEASE_MILESTONES.md) for subsequent work.

`GET /api/v1/admin/analytics/overview` returns `owner-analytics-v1` aggregates.
`/admin/analytics` presents the same values, date filters and exact daily counts.
The account settings link is visible for the current administrator. The API
independently reloads the user's database `platform_role` on every request;
changing browser storage or JWT role claims cannot grant access. Missing
authentication/deleted requesters receive 401 and non-admin users receive 403.
Existing operator-controlled role provisioning is documented in
[campus administration](CAMPUS_COMPETITIONS.md#authorization); this feature
does not grant anyone a role or provide a public promotion endpoint.

| Value | Definition |
| --- | --- |
| Registered learners | All currently existing `USER` accounts created before the response timestamp; independent of selected dates |
| Verified email | Those current learner accounts whose email is verified by the response timestamp |
| New learners / daily registrations | Currently existing learners registered in the inclusive selected UTC dates |
| Completed a first trade | Distinct learners from that signup cohort with at least one executed STANDARD-account order in either market by the response timestamp, including trades after the selected period |
| First-trade conversion | Completed-a-first-trade count divided by new learners, times 100; null when the cohort is empty |
| Orders by status / market | STANDARD-account orders submitted during the selected UTC dates by all currently existing learners, grouped by their current status and market; includes persisted rejections, not requests rejected before an order is stored |

Every metric excludes platform administrators and deleted accounts. Removing
an account or changing its role changes past counts; these are current-record
aggregates, not immutable historical snapshots. Sandbox orders never count
toward conversion or order totals. The order table covers all learners and
is not restricted to the selected signup cohort. Different currencies are
not summed, and no market-value/provider calls are made.

Dates use `YYYY-MM-DD`; both bounds are inclusive and converted to UTC
`[from midnight, day-after-to midnight)`, capped at the response timestamp for
today. Default is 30 days ending today; a provided end date without a start
uses its preceding 29 days. A start without an end ends today. Only 1–90 days
from 1970 through today are accepted. Invalid dates/ranges return 400. Missing
signup days are zero-filled. Recent signup cohorts have less time to convert;
this measure is not seven-day activation or a fixed-window cohort comparison.

The response contains aggregate counts only: no emails, user IDs, token data,
order payloads or user-level records. It is `Cache-Control: no-store`, the
page is noindex, and aggregates are not persisted in browser storage. Queries
run in a read-only repeatable-read transaction with a ten-second timeout and
the existing API rate limiter. `V110` indexes signup and order dates. These
ordinary index builds may briefly block writes during migration; review table
sizes and the deployment window before rollout. Large-scale analytics needs
separate capacity evidence and may later use aggregates or a read replica.

## M2 — first-party activity and retention

M2 adds `GET /api/v1/admin/analytics/activity?from=YYYY-MM-DD&to=YYYY-MM-DD`
and an activity section on the owner page. It uses the same database ADMIN
check, inclusive UTC date validation (1–90 days), no-store response policy,
and current-learner/deleted-account exclusions as M1. M1 metrics are unchanged.

### Capture and privacy contract

- Migration V111 creates an observation start timestamp and `product_activity`.
  Nothing is backfilled: a pre-existing order/watchlist is not evidence of a
  newly observed event. Existing learners can contribute activity but their
  pre-tracking registrations are excluded from activation/retention cohorts.
- Schema v1 has five categories: `ACTIVE`, `STOCK_OPENED`, `WATCHLIST_ADDED`,
  `ORDER_SUBMITTED`, `ORDER_EXECUTED`. One row per learner/category/UTC day
  stores its first server-observed timestamp. This measures distinct learners,
  not event frequency, stock popularity or session counts.
- Authenticated `POST /api/v1/analytics/events` accepts exactly
  `{"version":1,"event":"ACTIVE"}` or `STOCK_OPENED`. Identity comes from the
  JWT subject, timestamp from the server. Extra fields, client timestamps,
  user IDs, arbitrary metadata, unknown versions and server-only events fail.
- Browser capture runs only for a visible signed-in page; page/login arrival,
  pointer/key/scroll activity and returning to a visible tab count as activity.
  Stock research is sent only after the stock page successfully loads its
  instrument and quote. No background heartbeat, anonymous tracking, new
  cookies, replay, third-party analytics, symbols or search terms are stored.
  Browser retries are at most once per five minutes per user/event/day and
  never refresh authentication or block the learner UI.
- Watchlist additions and valid STANDARD-account order submissions/executions
  originate in the domain services. Idempotent order replays, existing watchlist
  additions, immediately rejected orders and sandbox trading do not emit these
  markers. Later-cancelled accepted orders still count as valid submissions.
  BEFORE_COMMIT listeners store markers in the business transaction: a rollback
  cannot leave a successful event behind. This deliberately adds at most two
  bounded indexed inserts to an accepted standard order transaction; analytics
  database failure rolls that transaction back rather than acknowledging a
  partially recorded action. Monitor primary-database latency before scaling.
- Ingestion rejects bodies over 1,024 bytes, including chunked requests, before JSON parsing.
  It has an independent 12-requests/minute/user rate-limit bucket (the
  existing limiter's Redis failure behavior remains fail-open). The unique
  key bounds storage to five rows per learner/day even under retries/concurrency.
- The latest 180 UTC dates are retained. A minute-based cleanup deletes up to
  5,000 expired rows per transaction until caught up; physical deletion can lag
  this logical cutoff. Queries/export exclude expired data immediately.
- Account export includes retained activity. The foreign key cascades account
  deletion. Administrators are not captured, and aggregates also recheck the
  current role. Promoting/demoting a user can therefore change past counts.

### Metric definitions

| Metric | Exact definition |
| --- | --- |
| Active day | At least one recorded ACTIVE, STOCK_OPENED, WATCHLIST_ADDED or ORDER_SUBMITTED marker; ORDER_EXECUTED alone never counts because fills may happen in the background |
| DAU / WAU / MAU | Distinct current learners in the 1 / 7 / 30 UTC dates ending on the selected **To** date; today ends at the response time. The selected From date does not change these rolling windows |
| Daily active trend | Distinct active learners on each selected UTC date |
| Observed coverage | Later of migration start and UTC midnight 179 days before today. A day/window beginning before coverage is unavailable (`null`/—), not zero; this includes the partial deployment day |
| Activation eligibility | Learners registered within selected dates and observed coverage whose full 168-hour signup window has elapsed as of the response |
| Activation | Research, watchlist addition and valid standard order submission all observed in `[registeredAt, registeredAt + 168h)`, in any order |
| Activation steps | Cumulative intersections: eligible → research → research+watchlist → all three → all three+execution; all events must be inside that same seven-day window |
| Activation rate | All-three count / eligible registrations; null if no mature eligible registrations |
| Median activation time | Median hours from registration to the last of the three first qualifying actions, among activated learners |
| D1 / D7 / D30 retention | At least one active marker on the exact UTC date N days after registration date. The target day must have fully ended; each N has its own mature denominator |
| Maturing / unobserved | Eligible-by-coverage registrations that have not yet matured / registrations before observed coverage; both excluded from metric denominators |

Activation and retention use observation through the response timestamp, even
when the selected signup period ended earlier. A learner who returns only on
D6 or D8 does not count as retained on D7. Empty mature cohorts return null rates.
Browser events are best-effort observations and can be lost to connectivity,
expired sessions or blockers; they are not a billing or anti-fraud signal.
Daily deduplication intentionally discards event counts and repeated timestamps.
No session duration, advertising attribution or pre-deployment sessions are inferred.

### Production acceptance

1. Deploy the tested candidate and confirm V111 completes. Existing owner access
   is reused; no new environment variable or role promotion is needed.
2. Open owner analytics: coverage starts at deployment; longer active windows
   and retention start as —. These are expected, not broken metrics.
3. In a separate normal learner account, sign in, load a stock, add a new watchlist
   item and place a valid standard paper order during the relevant market session.
4. Reload owner analytics. Once a complete tracked UTC date begins, DAU reflects
   the learner. Repeat actions do not inflate distinct-learner counts. A normal
   account gets 403 for both admin APIs; signed-out requests get 401.
5. Use Account settings export to confirm activity is included. Use disposable
   staging accounts to validate deletion and expired-data cleanup.
6. D1 first becomes measurable after a tracked signup's next UTC day fully ends;
   seven-day activation after 168 hours; D7/D30 after their exact return days end.
   Do not alter production signup times to manufacture mature cohorts.
7. Check responsive light/dark layouts and retry/error behavior. Record candidate
   SHA and observations separately from CI results.

### Deferred measurement

Session counts/duration, feature frequency, symbol popularity, acquisition
attribution and operational latency/error dashboards remain outside M2.
Operational metrics continue through the existing monitoring stack. Add a
warehouse or read replica only after capacity evidence warrants it.
