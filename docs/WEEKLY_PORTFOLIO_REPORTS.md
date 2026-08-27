# Weekly portfolio reports

Weekly portfolio reports are optional educational reviews of a learner's two
simulated market accounts. Delivery is disabled by default and can be enabled
only after the account email is verified.

## API

All endpoints require the existing bearer authentication policy.

- `GET /api/v1/reports/weekly/preferences`
- `PUT /api/v1/reports/weekly/preferences`
- `GET /api/v1/reports/weekly`
- `GET /api/v1/reports/weekly/preview?zoneId=Asia/Kolkata`

The preference accepts an `enabled` flag and an IANA timezone. The preview is
never persisted or emailed. Report history returns the latest twelve immutable
snapshots.

## Generation and delivery

The scheduler checks enabled preferences hourly. A report becomes due on
Monday after 08:00 in the learner's selected timezone and covers the preceding
Monday through Sunday as a stored seven-day period. A unique
`(user_id, period_end)` constraint prevents duplicate weekly snapshots.

Delivery requires both an enabled preference and a currently verified email.
Existing StoxSim SMTP settings are reused. Delivery status and attempt time are
persisted; failed delivery is retried up to three times during the due window.

## Snapshot contents

Snapshots are versioned as `weekly-portfolio-report-v1` and include, separately
for India and the United States:

- simulated account value and week-over-week change;
- realized, unrealized and total simulated profit/loss;
- cash and invested allocation;
- executed paper-trade count for the period;
- largest current allocation and largest absolute contribution;
- pricing coverage and confidence labels.

Only aggregate learning information is stored and emailed. The feature reuses
the existing provider-neutral valuation boundary; it does not persist or email
raw quotes, add provider-specific fields, recommend securities, forecast prices
or promise returns.

Report preferences and snapshots are included in account data export. Both are
deleted automatically when the learner deletes their account.
