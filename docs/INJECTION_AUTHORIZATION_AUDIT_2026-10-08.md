# StoxSim injection and authorization audit — 2026-10-08

Baseline: main `f5e1e2b1ce0dcb60ee0eea9392d544ac12d5ed62`. Review/fixes: PR #141.

## Scope and findings

Reviewed 112 REST method/path pairs across all 31 application controllers, the Spring Security filter chain and JWT decoder, STOMP inbound/outbound channels, input DTOs and forms, URL/query parameters, JPA repositories, runtime JDBC SQL, Redis use, and React rendering sinks. No Next.js API route handlers or server actions are present. Next metadata routes (robots, sitemap, manifest, Open Graph and Twitter images) return fixed public content. Page components do not replace backend authorization.

| Finding | Severity | Result |
| --- | --- | --- |
| Revoked/deleted sessions could retain valid access JWTs until expiry; shared endpoints did not always perform a user lookup | High | JWT carries `sid`; decoder requires matching live user/session record before controller execution. Shared HTTP and STOMP validation. |
| Authenticated stream clients could send messages to the quote broker and subscribe to unrestricted destinations | High | Read-only inbound policy; only exact quote-topic subscription; anonymous or expired/revoked subscriptions denied. |
| Open WebSocket subscription could continue after token revocation/expiry | Medium | Revalidate before outbound quote delivery; invalid connections receive no further quotes; disconnect removes session map entry. |
| Concurrent refresh could rotate the same refresh token twice; rotation could race bulk session revocation | Medium | Lock owner before refresh record; single-use rotation; revocation uses the same owner lock. Old refresh logout revokes the whole owning session. |
| Billing authorization followed locks on the requested owner's resources | Low | Database admin and actor-scoped subscription lookup now precede resource locks. Existing ownership restriction preserved. |
| Search LIKE wildcards broadened matches; query lacked maximum length | Low/hardening | Literal parameterized substring matching; 2–160 character query. This was not SQL injection. |
| External campus website link trusted stored strings | Defense in depth | Parse URL and allow only HTTPS without credentials before generating a link. Backend already rejects non-HTTPS submissions. |

No confirmed SQL injection, NoSQL injection, executable user-content XSS, or cross-user resource disclosure was found in the reviewed application query/rendering/ownership paths. This is evidence from the inspected code and tests, not a guarantee against every possible attack.

## Authentication and intentional public surfaces

Protected REST routes inherit `.anyRequest().authenticated()` in `SecurityConfig`; authentication is centralized and executes before MVC binding/controller logic. It validates JWT signature, issuer, expiry and active owned session, rather than trusting frontend state or an arbitrary subject/admin claim. Resource permissions are enforced in application services/repositories using the JWT subject and current database roles/memberships. Repeating authentication independently in every controller would create inconsistent checks.

The nine public application routes below are explicit exceptions: login/registration/refresh/logout/reset/verification, the signed webhook, and aggregate system status. Anonymous CORS preflight is not application data access. Framework `/actuator/health` is public health; `/actuator/prometheus` uses a separate fail-closed, constant-time metrics token filter. Other actuator paths inherit authentication and management exposure configuration. `/ws/market` permits the HTTP handshake only; CONNECT requires bearer authentication, and quote subscriptions/delivery require a live session. All client SEND frames and wildcard/private destinations are denied.

## Injection and rendering review

- **SQL:** Runtime JDBC queries bind request/user/resource/date values with `?`; JPA `@Query` statements bind named parameters, and derived repository methods build predicates safely. Campus `REQUEST`/`COMPETITION` concatenations combine fixed SQL constants and fixed suffixes, and the optional `FOR UPDATE` is a server boolean; no request string is concatenated into SQL. Analytics intervals and retention values are bound numbers. Scenario result/request JSON is a bound value, not SQL source. Privileged fixture/maintenance scripts are not reachable from HTTP; generated integer/hex literals there are not request input.
- **NoSQL/Redis:** No MongoDB or document-query operators are accepted. Redis caches use fixed/prefixed keys and typed JSON values. Rate limiting runs a fixed Lua script with separate KEYS/ARGV; inputs never become Lua source.
- **Frontend XSS:** Names, search terms/results, scenario titles, campus notes/review notes, league names, errors and stock descriptions use React text nodes/attributes. Finwiz parses markdown into React nodes, never raw HTML; links remain text, formulas remain text. The only raw-HTML sinks are fixed JSON-LD/theme scripts: JSON-LD escapes `<` and the theme is a fixed light/dark whitelist. No user strings flow into these sinks. React escaping is the appropriate control for plain text; destructive input sanitization is unnecessary.
- **URLs/forms:** Stock URLs encode symbol/exchange path components. Search, Finwiz context and campus query strings use URL encoding; backend enums/UUIDs/dates/numeric constraints remain authoritative. Reset/verification tokens are submitted as JSON and consumed server-side. Profile/auth/password/orders/league/campus/weekly preference forms submit JSON to guarded routes, not to an HTML or SQL evaluator. Website anchors now have a scheme/credential allowlist for legacy/imported data as well as submitted records.

## Route-by-route inventory

Each protected row also receives signature/issuer/expiry/active-session verification in the shared security filter chain. Exact resource guards are noted below. Framework endpoints are described above separately.

| Method | Route | Controller | Authentication / resource permission |
| --- | --- | --- | --- |
| GET | `/api/v1/account/ledger` | `LedgerController` | Live JWT session; subject-derived user/region; user-scoped queries. |
| GET | `/api/v1/accounts` | `AccountController` | Live JWT session; user-scoped account list/create; server-side subscription limits. |
| POST | `/api/v1/accounts/sandboxes` | `AccountController` | Live JWT session; user-scoped account list/create; server-side subscription limits. |
| GET | `/api/v1/accounts/{accountId}/holdings` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/accounts/{accountId}/ledger` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/accounts/{accountId}/orders` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| POST | `/api/v1/accounts/{accountId}/orders` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| DELETE | `/api/v1/accounts/{accountId}/orders/{orderId}` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/accounts/{accountId}/orders/{orderId}` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| PUT | `/api/v1/accounts/{accountId}/orders/{orderId}` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/accounts/{accountId}/portfolio` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/accounts/{accountId}/portfolio/analytics` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/accounts/{accountId}/portfolio/history` | `PortfolioHistoryController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/accounts/{accountId}/portfolio/insights` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/accounts/{accountId}/portfolio/sectors` | `SectorAllocationController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| POST | `/api/v1/accounts/{accountId}/scenarios` | `ScenarioController` | Live JWT session; catalog shared; credits subject-owned; run account ownership before valuation/credit debit; user-scoped idempotency. |
| GET | `/api/v1/accounts/{accountId}/trades` | `AccountTradingController` | Live JWT session; requireOwned(user, account); nested orders additionally match account + owner. |
| GET | `/api/v1/admin/analytics/activity` | `ActivityAnalyticsController` | Live JWT session; database ADMIN before aggregate queries; bounded date range; no personal data returned. |
| GET | `/api/v1/admin/analytics/overview` | `AnalyticsController` | Live JWT session; database ADMIN before aggregate queries; bounded date range; no personal data returned. |
| POST | `/api/v1/analytics/events` | `ProductActivityController` | Live JWT session; user from JWT; fixed allowed browser event enum; client user IDs rejected. |
| POST | `/api/v1/auth/email-verification/confirm` | `AuthController` | Public: Atomic consume of hashed, unexpired, single-use verification token. |
| POST | `/api/v1/auth/email-verification/resend` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| GET | `/api/v1/auth/events` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| POST | `/api/v1/auth/login` | `AuthController` | Public: Password verification before issuing tokens. |
| POST | `/api/v1/auth/logout` | `AuthController` | Public: Possession of hashed refresh token; revokes only its owning session. |
| POST | `/api/v1/auth/logout-all` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| DELETE | `/api/v1/auth/me` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| GET | `/api/v1/auth/me` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| PATCH | `/api/v1/auth/me` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| POST | `/api/v1/auth/me/export` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| PATCH | `/api/v1/auth/me/password` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| POST | `/api/v1/auth/password/forgot` | `AuthController` | Public: Non-enumerating response; sends bounded single-use token to registered email. |
| POST | `/api/v1/auth/password/reset` | `AuthController` | Public: Atomic consume of purpose-specific, hashed, unexpired one-time reset token before changing password. |
| POST | `/api/v1/auth/refresh` | `AuthController` | Public: Hashed active refresh cookie/body token; locks owner then token; rejects replay before rotation. |
| POST | `/api/v1/auth/register` | `AuthController` | Public: Registration validation, legal consent and rate limit; creates only new user resources. |
| GET | `/api/v1/auth/sessions` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| DELETE | `/api/v1/auth/sessions/{sessionId}` | `AuthController` | Live JWT session; subject-derived user; sessions scoped by user + session ID; password proof for sensitive changes/deletion. |
| GET | `/api/v1/billing/test` | `RazorpayTestController` | Live JWT session; database ADMIN; subscriptions require actor ownership before lock/provider side effects. |
| POST | `/api/v1/billing/test/subscriptions` | `RazorpayTestController` | Live JWT session; database ADMIN; subscriptions require actor ownership before lock/provider side effects. |
| POST | `/api/v1/billing/test/subscriptions/{id}/benefits` | `RazorpayTestController` | Live JWT session; database ADMIN; subscriptions require actor ownership before lock/provider side effects. |
| POST | `/api/v1/billing/test/subscriptions/{id}/cancel` | `RazorpayTestController` | Live JWT session; database ADMIN; subscriptions require actor ownership before lock/provider side effects. |
| POST | `/api/v1/billing/test/subscriptions/{id}/reconcile` | `RazorpayTestController` | Live JWT session; database ADMIN; subscriptions require actor ownership before lock/provider side effects. |
| POST | `/api/v1/billing/test/subscriptions/{id}/refresh` | `RazorpayTestController` | Live JWT session; database ADMIN; subscriptions require actor ownership before lock/provider side effects. |
| POST | `/api/v1/billing/test/webhook` | `RazorpayTestController` | Public: 64 KiB raw-byte limit; constant-time HMAC verification before JSON parsing or mutations; event idempotency. |
| GET | `/api/v1/campus` | `CampusController` | Live JWT session; profile/request current user; admin review/list require database ADMIN. |
| GET | `/api/v1/campus/admin/verification-requests` | `CampusController` | Live JWT session; profile/request current user; admin review/list require database ADMIN. |
| POST | `/api/v1/campus/admin/verification-requests/{requestId}/approve` | `CampusController` | Live JWT session; profile/request current user; admin review/list require database ADMIN. |
| POST | `/api/v1/campus/admin/verification-requests/{requestId}/reject` | `CampusController` | Live JWT session; profile/request current user; admin review/list require database ADMIN. |
| GET | `/api/v1/campus/institutions` | `CampusCompetitionController` | Live JWT session; shared institution directory; bound literal search; suspended visibility restricted to admin. |
| GET | `/api/v1/campus/institutions/{institution}` | `CampusCompetitionController` | Live JWT session; current institution membership or ADMIN; competition belongs to institution; enrollment also requires real membership; actor-only score update. |
| POST | `/api/v1/campus/institutions/{institution}/competitions` | `CampusCompetitionController` | Live JWT session; current institution ORGANIZER or ADMIN; target request/member/competition constrained to institution; self-leave allowed. |
| GET | `/api/v1/campus/institutions/{institution}/competitions/{competition}` | `CampusCompetitionController` | Live JWT session; current institution membership or ADMIN; competition belongs to institution; enrollment also requires real membership; actor-only score update. |
| POST | `/api/v1/campus/institutions/{institution}/competitions/{competition}/cancel` | `CampusCompetitionController` | Live JWT session; current institution ORGANIZER or ADMIN; target request/member/competition constrained to institution; self-leave allowed. |
| POST | `/api/v1/campus/institutions/{institution}/competitions/{competition}/enroll` | `CampusCompetitionController` | Live JWT session; current institution membership or ADMIN; competition belongs to institution; enrollment also requires real membership; actor-only score update. |
| POST | `/api/v1/campus/institutions/{institution}/competitions/{competition}/refresh` | `CampusCompetitionController` | Live JWT session; current institution membership or ADMIN; competition belongs to institution; enrollment also requires real membership; actor-only score update. |
| POST | `/api/v1/campus/institutions/{institution}/competitions/{competition}/withdraw` | `CampusCompetitionController` | Live JWT session; current institution membership or ADMIN; competition belongs to institution; enrollment also requires real membership; actor-only score update. |
| GET | `/api/v1/campus/institutions/{institution}/manage` | `CampusCompetitionController` | Live JWT session; current institution ORGANIZER or ADMIN; target request/member/competition constrained to institution; self-leave allowed. |
| POST | `/api/v1/campus/institutions/{institution}/members/{target}/remove` | `CampusCompetitionController` | Live JWT session; current institution ORGANIZER or ADMIN; target request/member/competition constrained to institution; self-leave allowed. |
| POST | `/api/v1/campus/institutions/{institution}/members/{target}/role` | `CampusCompetitionController` | Live JWT session; current institution ORGANIZER or ADMIN; target request/member/competition constrained to institution; self-leave allowed. |
| POST | `/api/v1/campus/institutions/{institution}/membership-requests` | `CampusCompetitionController` | Live JWT session; self-scoped latest/cancel request; verified requester; cross-institution constraints. |
| POST | `/api/v1/campus/institutions/{institution}/membership-requests/{request}/review` | `CampusCompetitionController` | Live JWT session; current institution ORGANIZER or ADMIN; target request/member/competition constrained to institution; self-leave allowed. |
| POST | `/api/v1/campus/institutions/{institution}/organizer-recovery` | `CampusCompetitionController` | Live JWT session; database ADMIN; bounded moderation reason. |
| POST | `/api/v1/campus/institutions/{institution}/suspension` | `CampusCompetitionController` | Live JWT session; database ADMIN; bounded moderation reason. |
| GET | `/api/v1/campus/membership-request` | `CampusCompetitionController` | Live JWT session; self-scoped latest/cancel request; verified requester; cross-institution constraints. |
| POST | `/api/v1/campus/membership-requests/{request}/cancel` | `CampusCompetitionController` | Live JWT session; self-scoped latest/cancel request; verified requester; cross-institution constraints. |
| POST | `/api/v1/campus/verification-requests` | `CampusController` | Live JWT session; profile/request current user; admin review/list require database ADMIN. |
| GET | `/api/v1/competitions/current` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| POST | `/api/v1/competitions/current/enroll` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| POST | `/api/v1/finwiz/ask` | `FinwizController` | Live JWT session; bounded question and stock symbol; shared market context only; no user/resource ID accepted. |
| GET | `/api/v1/holdings` | `PortfolioController` | Live JWT session; subject-derived user/region; user-scoped queries. |
| GET | `/api/v1/instruments/search` | `InstrumentController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/instruments/{marketRegion}/{exchange}/{symbol}` | `InstrumentController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/instruments/{marketRegion}/{exchange}/{symbol}/candles` | `MarketDataController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/instruments/{marketRegion}/{exchange}/{symbol}/insights` | `MarketDataController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/instruments/{marketRegion}/{exchange}/{symbol}/quote` | `MarketDataController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/leagues` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| POST | `/api/v1/leagues` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| POST | `/api/v1/leagues/join` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| DELETE | `/api/v1/leagues/{leagueId}` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| GET | `/api/v1/leagues/{leagueId}` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| POST | `/api/v1/leagues/{leagueId}/invite/rotate` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| POST | `/api/v1/leagues/{leagueId}/leave` | `CompetitionController` | Live JWT session; global leaderboard participation is opt-in; private league requires membership; rotate/delete require owner; joining requires hashed invite. |
| GET | `/api/v1/market/indices` | `IndexController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/market/movers` | `MarketMoversController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/market/status` | `MarketStatusController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/onboarding` | `OnboardingController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| POST | `/api/v1/onboarding/dismiss` | `OnboardingController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| POST | `/api/v1/onboarding/introduction/complete` | `OnboardingController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| POST | `/api/v1/onboarding/resume` | `OnboardingController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| GET | `/api/v1/orders` | `OrderController` | Live JWT session; owner-scoped order lookup; owner checks before mutation; account locks and recheck. |
| POST | `/api/v1/orders` | `OrderController` | Live JWT session; owner-scoped order lookup; owner checks before mutation; account locks and recheck. |
| DELETE | `/api/v1/orders/{orderId}` | `OrderController` | Live JWT session; owner-scoped order lookup; owner checks before mutation; account locks and recheck. |
| GET | `/api/v1/orders/{orderId}` | `OrderController` | Live JWT session; owner-scoped order lookup; owner checks before mutation; account locks and recheck. |
| PUT | `/api/v1/orders/{orderId}` | `OrderController` | Live JWT session; owner-scoped order lookup; owner checks before mutation; account locks and recheck. |
| GET | `/api/v1/portfolio` | `PortfolioSummaryController` | Live JWT session; subject-derived user/region; user-scoped queries. |
| GET | `/api/v1/portfolio/analytics` | `PortfolioSummaryController` | Live JWT session; subject-derived user/region; user-scoped queries. |
| GET | `/api/v1/portfolio/insights` | `PortfolioSummaryController` | Live JWT session; subject-derived user/region; user-scoped queries. |
| GET | `/api/v1/progression` | `ProgressionController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| POST | `/api/v1/progression/check-in` | `ProgressionController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| GET | `/api/v1/reports/weekly` | `WeeklyReportController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| GET | `/api/v1/reports/weekly/preferences` | `WeeklyReportController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| PUT | `/api/v1/reports/weekly/preferences` | `WeeklyReportController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| GET | `/api/v1/reports/weekly/preview` | `WeeklyReportController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| GET | `/api/v1/scenarios` | `ScenarioController` | Live JWT session; catalog shared; credits subject-owned; run account ownership before valuation/credit debit; user-scoped idempotency. |
| GET | `/api/v1/scenarios/credits` | `ScenarioController` | Live JWT session; catalog shared; credits subject-owned; run account ownership before valuation/credit debit; user-scoped idempotency. |
| GET | `/api/v1/subscription` | `SubscriptionController` | Live JWT session; subject-derived user; user-scoped reads/mutations; typed preferences and bounded inputs. |
| GET | `/api/v1/system/status` | `SystemStatusController` | Public: Public aggregate provider/service status; no private user resources. |
| GET | `/api/v1/trades` | `TradeController` | Live JWT session; subject-derived user/region; user-scoped queries. |
| GET | `/api/v1/trading/charges/estimate` | `ChargeEstimateController` | Live JWT session; shared instrument/market data; typed enums/dates/numbers, bounded search. |
| GET | `/api/v1/watchlists/default` | `WatchlistController` | Live JWT session; current user default watchlist; item delete scoped to item + owned watchlist. |
| POST | `/api/v1/watchlists/default/items` | `WatchlistController` | Live JWT session; current user default watchlist; item delete scoped to item + owned watchlist. |
| DELETE | `/api/v1/watchlists/default/items/{itemId}` | `WatchlistController` | Live JWT session; current user default watchlist; item delete scoped to item + owned watchlist. |

## Regression evidence

`RoutingSecurityIntegrationTest` discovers actual Spring controller mappings and exercises all 103 protected routes with anonymous, malformed and genuinely signed-but-revoked JWTs: 309 requests must return 401 before request binding. Additional real signed-JWT tests cover foreign account portfolio/analytics/insights/holdings/trades/ledger/orders/history/sectors and scenarios, foreign session revocation, refresh replay and concurrent rotation, and injection/metacharacter searches against PostgreSQL. Added checks also cover nested/global order read/modify/cancel, foreign watchlist deletion, subject/session mismatch, deleted/logout-all users, and non-admin sessions.

`ActiveSessionJwtValidatorTest` verifies session ownership, missing/malformed subject/session claims, revocation and expiry. `MarketWebSocketAuthInterceptorTest` verifies bearer CONNECT, anonymous/wildcard rejection, client quote-publishing rejection, and revoked subscription/outbound-delivery rejection. Existing campus, league, billing and analytics suites verify current database membership/owner/admin boundaries and forged-role denial.

Playwright tests submit hostile stored scenario titles and campus names/notes, hostile URL and Finwiz question/generated output, and unsafe external schemes/embedded credentials; they assert text display and absence of injected elements/execution. Full CI includes the authenticated learner journey and prior secret-canary bundle verification, complete-history Gitleaks, dependency/configuration scanning and CodeQL.

Validation: [CI run 37763700857](https://github.com/M4Rk9/stoxsim/actions/runs/37763700857) passed 233 backend tests (0 failures/errors/skips), including 8 real signed-JWT/PostgreSQL security integration tests and all 309 discovered-route authentication requests. Frontend typecheck and secret-canary build passed. The initial browser security suite also passed in [CI run 37763092086](https://github.com/M4Rk9/stoxsim/actions/runs/37763092086); final Finwiz/browser and workflow conclusions are available on PR #141 checks. Browser checks require CI Chromium; the local runner has no installed Chromium binary.

## Rollout and limits

Older access JWTs lack `sid` and receive 401 after deployment; normal browser refresh with an active refresh cookie issues an upgraded JWT. A revoked session cannot refresh. Existing session expirations and auth cookies remain in effect. Validation performs a database lookup for each protected request and delivered quote; it intentionally does not cache revocation results. Database failures fail closed. No production deployment, live VPS secret access, destructive git history rewrite, or provider credential rotation was performed in this change.
