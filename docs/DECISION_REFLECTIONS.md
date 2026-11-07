# Private decision reflections and bounded Finwiz assistance

Decision reflections are optional notes about simulated decisions, integrated into Learn. They do not change orders, portfolios, scores, entitlements or competition ranking.

## Authenticated API

- `GET /api/v1/reflections`: newest 50 owned reflections, as an array.
- `POST /api/v1/reflections`: creates a reflection (201).
- `PUT /api/v1/reflections/{id}`: replaces the owned note fields.
- `DELETE /api/v1/reflections/{id}`: deletes the owned note (204).
- `POST /api/v1/reflections/{id}/assistance` with `{"consent":true}`: explicitly sends bounded note excerpts to the existing Finwiz service. Missing, false or null consent is rejected. Consent is required for every request.

Write fields are nullable `accountId` and `orderId`, required nonblank `reason`, and optional `assumptions`, `risks`, `expectedOutcome`, `reflection`. Text fields allow at most 2,000 characters each. Responses add `id`, `createdAt` and `updatedAt`; absent optional text becomes an empty string. An owned order can supply its account implicitly. Mismatched accounts return 400, inaccessible notes or simulator records return 404. At most 1,000 reflections are retained per user; creation is serialized per user to enforce this bound. Account export is the way to retrieve all notes beyond the newest-page limit.

Reads, writes, deletion and background tasks never send notes to AI. Assistance sends up to 300 characters from each note field, labels them as unverified learner claims and retains the existing educational boundary and server-selected answer tier. Input is not verified market data. Notes and AI answers are not added to usage logs. Provider failure or a used-up allowance returns the clearly labelled deterministic lesson. A successful assistance response records the existing Finwiz journey event. Saving notes records only actual daily `REFLECTION_SAVED` activity; no historical notes or use are inferred.

## Provider budgets

V128 adds daily PostgreSQL counters, shared across instances. A short independent transaction reserves one attempt before each configured Gemini call. Both per-user and global limits are atomic under concurrency. Timeouts and failed calls retain their reservation; retrying a request cannot recover already consumed capacity. The provider network call runs outside this transaction.

Defaults per UTC day are 10 Basic, 30 Expanded, 60 Full requests, with a global limit of 500. These are operating safeguards, not a statement about Gemini's current free quota. Configure Spring properties under `stoxsim.finwiz`: `basic-daily-provider-requests`, `expanded-daily-provider-requests`, `full-daily-provider-requests`, `global-daily-provider-requests`. Setting a limit to zero disables the affected provider calls while retaining deterministic lessons. Existing per-minute Finwiz limits also cover reflection assistance. New connections time out after 5 seconds and provider responses after 20 seconds.

Global daily counters retain provider-reported total tokens when supplied, including incomplete answers. Missing token metadata is not fabricated. Token counts are attributed to the reserved UTC day even if a response arrives after midnight. Counts are not currency costs or invoices. Per-user usage cascades on account deletion; anonymous global counters remain to prevent quota resets by account removal. Hourly cleanup retains 180 UTC days, deleting per-user records in bounded batches.

`GET /api/v1/admin/analytics/learning?from=YYYY-MM-DD&to=YYYY-MM-DD` returns actual daily-use/AI aggregates for an administrator only. It reports tracking start dates, retained coverage and separate completeness flags. Recommendation/reflection learner counts are null for incomplete windows. Provider attempts and reported tokens are observed partial-window counts; `responsesWithTokenUsage` shows measurement coverage. Free quota, provider prices and invoices require separate verification. No new paid infrastructure is introduced.

## Data and rollout

V128 adds `decision_reflection`, `finwiz_usage_daily`, `finwiz_global_usage_daily` and observation coverage. Composite foreign keys enforce linked order/account ownership at the database boundary. Notes and private usage cascade on user deletion. The combined release includes `decisionReflections` and `finwizUsage` in account export through the existing lifecycle service.

Deploy additive migrations and backend before the clients. An application rollback may retain these additive tables and counters. Never edit or reverse a deployed migration. Real Gemini delivery, production acceptance and operating cost must be verified in the intended environment; local provider substitutes do not establish them.

## Validation evidence

- `mvn -B -ntp -C test -Dtest=FinwizServiceTest,ApiRateLimitFilterTest`: 32 passed, zero failures/errors/skips.
- `mvn -B -ntp -C test -Dtest=DecisionReflectionIntegrationTest,FinwizUsageBudgetIntegrationTest`: 10 passed with real PostgreSQL Testcontainers, zero failures/errors/skips. Tests cover ownership, composite constraints, consent, no automatic AI, unchanged balances, validation/storage caps, deletion, concurrent quota reservation, global limits, retention and token-day accounting.
- After adding the admin usage endpoint and provider token metadata assertion: `mvn -B -ntp -C test -Dtest=FinwizUsageBudgetIntegrationTest#adminUsageReportsArePrivateAndExplicitAboutIncompleteCoverage,FinwizServiceTest`: 25 passed, zero failures/errors/skips. Non-admin requests are forbidden; incomplete coverage and unverified invoice status are explicit.
