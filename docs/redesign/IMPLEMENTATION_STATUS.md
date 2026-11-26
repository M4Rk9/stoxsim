# StoxSim redesign status

## Milestone 1 — Foundation, authentication and web shell (in progress)
Base main: 80aecef64f2d50dcde7341bb6a4b4774bfc628f9. Reference: supplied written spec, desktop workspace SVG and Android mockups. The design palette uses #07110e and #10e886. Reference prices are illustrative and not used.

### First implementation batch
- Shared semantic palette, typography stacks and radii; original session/theme logic retained.
- Exact approved login/register headline and subheading. Existing consent, auth submission, Google sign-in, recovery logic retained.
- Public marketing and recovery styling.
- Collapsible desktop sidebar and existing five mobile web navigation destinations. Android navigation belongs to M3.
- Header shortcut to existing stock/ETF search, actual portfolio summary before compact research workspace.
- Original Brand and logo preserved; no backend, database, API, trading, entitlement or deployment changes.
- Navigation and auth regression tests added/updated.

### Verification and limitations
- Source reviewed; local checkout unavailable because container cannot resolve github.com. Builds, Playwright and visual tests not executed. Review PR CI and run visual QA before merging.
- Requested font stacks declared, font assets not bundled; fallback fonts apply.
- Reusable dialogs/tooltips/skeletons, component consolidation, mobile/global search parity, final dashboard layout, Google/recovery visual QA, light/dark and responsive route audit pending.
- Original ZIP received and reviewed in the second implementation batch.

### Next
Complete M1 pending items, test and resolve regressions, update journal. No merge or deployment or advancement to M2 without approval. M2 web, M3 Android and M4 QA not started.

### Second implementation batch — archive integration
- Reviewed archive README, CSS, AppShell, page and manifest. Archive uses hardcoded demo state, simulated fallback orders and local AI responses and removes SEO, analytics, billing, admin, campus and other production routes. Do not copy those modules, demo prices, mock branding or duplicate desktop dock.
- Adapted archive obsidian canvas (#050b09), terminal sidebar (#07110e), input surface (#0b1815), active navigation treatment and visual grouping; retained original logo and per-user theme.
- Added reusable desktop workspace search dialog using existing authenticatedRequest and production instruments/search API, explicit India/US market selection, debounce, cancellation, exchange/type filtering, accessible native dialog and truthful states. Existing mobile Markets search remains available; Finwiz full-screen workspace remains separate.
- Fixed invalid marketing step-list CSS declaration and added search regression test.
- Verification: source inspection and commit only; tests and visual rendering not yet executed. No production deployment. Fonts still pending: archive uses external Google CSS, not bundled assets; do not loosen production CSP for fonts.

### Browser acceptance follow-up (2026-11-26 IST)
- Supplied run: 162 passed, 6 failed. Three Finwiz launcher checks, old theme RGB expectations, mobile derivatives active navigation and Escape dismissal.
- Concurrent follow-up commits restored the desktop launcher, explicit Escape/cancel handling and breakpoint-aware active navigation. Preserved those changes.
- Retained reference canvas #050b09 and text #f2f7f5 and aligned exact theme assertions; extended responsive derivatives and dialog dismissal coverage. No test skipped, no trading or auth logic changes.
- CI results pending verification; earlier follow-up workflow returned action_required and no jobs. No merge or deploy.

### M1 completion candidate
- Baseline commit 1290a30a860a83baa268fcf788c80db63c79f38f verified successful: CI run 45689126338 (all jobs), CodeQL and dependency review.
- Vendored licensed Plus Jakarta Sans, Inter, JetBrains Mono, using same-origin font-face and font-display swap. Source SHAs and license files recorded. Full upstream TTF files; compression/subsetting deferred to performance work.
- Introduced reusable UI card, button, notice, badge, skeleton primitives and adopted in auth, dashboard and search; existing native dialog, form and table behavior retained. Further per-route component migration belongs to M2.
- Mobile/tablet search entry and Ctrl/Command+K added without removing existing market links. Added dashboard shortcuts for watchlist, portfolio and progress, explicit portfolio region, responsive financial metrics.
- Reworked Google sign-in card to use original branding and shared UI, password/verification screens use shared accessible notices. Provider and auth logic unchanged.
- Expanded browser tests across mobile/tablet/desktop; tests attach rendered screenshots of auth, recovery, search and dashboard. Existing CI runs full suite and uploads attached images.
- Candidate NOT verified until fresh CI and actual rendered screenshot review. Container clone failed due to DNS and no local app dependencies; no claim of local execution or human visual review. Do not merge/deploy until new checks reviewed.

### Rendered baseline evidence review
Inspected all six real Chromium Home screenshots from the successful prior candidate 1290a30 (artifact 15059565230): 320, 390 and 1280px in light and dark. Desktop sidebar, header, typography hierarchy and portfolio surfaces were visually coherent; mobile header avatar overlapped market selector at 320/390, and long account figures wrapped mid-number at 320. Reserved first header row and reduced narrow metric font size/padding; added responsive test to assert avatar and switcher separation. Prior screenshots use synthetic API fixtures, not live market prices. Expanded font check to require actual loaded FontFace records. Latest candidate screenshot inspection and final CI still pending; previous render is not evidence for the new commit.

## Milestone 2 — Complete web redesign
M1 PR #200 merged into main at 76f64e1cb45f41652fe285b3d7bfcab64e0f9287. Final M1 CI run 45691884176 passed; production smoke tests not independently verified. Original approved spec and frontend ZIP reviewed, no demo data or logic copied.

### Batch 2A — Markets, research and trading (candidate)
- Updated existing market, filter, watchlist and equity ticket surfaces; stock detail gets a real section navigation, prominent exact-listing price, accessible chart range/period selections, consistent charts, statements, news and virtual trade action hierarchy.
- Redesigned F&O terminal with contract/position navigation, distinct chain and risk-review surfaces, scrollable accessible option table, numeric typography, explicit risk and wallet styling. No changes to providers, freshness, order preview, expiry, fees, margin, acknowledgment, idempotency or execution rules.
- Existing regression suite retained; expanded derivatives navigation assertion. Static replacement preconditions and CSS brace checks performed. Local clone blocked by DNS; runtime verification via PR CI pending. Do not claim M2 release readiness.

### Batch 2B — Portfolio and intelligence (candidate)
- Added portfolio section shortcuts for actual balances, performance/risk, allocation, holdings and existing dashboard recent transactions; responsive numeric tables and analytics, benchmark chart palette, StoxScore component styling. No new transaction feed or invented history; existing entitlement and calculations retained.
- Refined Finwiz full-screen chat/sidebar, active history, messages and composer; added three editable starter prompts (never auto-submit). Account-scoped device history, provider calls, context controls, safety notices and subscription enforcement untouched.
- Refined journal and shared decision/reflection components. Added portfolio shortcut regression assertion. Static source preconditions and CSS brace checks passed. M2A CI started (45694934846); local runtime blocked by DNS, so per-batch browser results are not yet verified. All batches require full CI before merge.

### Batch 2C — Learning, engagement and administration (candidate)
- Refined Scenario Lab builder and result surfaces, learning map and progress, emerald mission/achievement states, competition and campus screens, settings, billing plans, notification/list components, owner analytics, public status and legal-document presentation. Added anchor navigation for learning, competitions and analytics. Existing actions and permission checks remain unchanged.
- Existing Home updates remain the feed surface; no new backend or feed route created. Billing pricing and provider rules unchanged. Status page uses semantic theme colours; legal copy unchanged. Added responsive status screenshots/test.
- Source replacement preconditions and CSS brace validation completed for each batch. All changes are candidates pending fresh GitHub CI and actual rendered review; per-batch runtime gating could not be completed locally (DNS). Do not mark all assigned tasks verified: comprehensive route-level visual review, existing transaction history scope and StoxScore integration should be checked, along with M2 cross-screen consistency. No merge, deploy, or Android changes.

## Milestone 3 — Native Android redesign (candidate)
- PR #202 merged; base main c2e6f1c910e0e1247bc80567ce85889d403ef73f. M2 latest CI 45701199975 passed. Web full transaction history and internal-label cleanup remain M4 follow-up; complete web visual audit remains outstanding.
- Adopted native Material 3 obsidian/emerald light/dark palette, shared card/control shapes, financial numerical styling and existing logo; refreshed authentication, email action, dashboard overview, market/portfolio and all Panel-based secondary surfaces.
- Primary destinations now Home, Markets, Finwiz AI, Portfolio, More; prominent circular Finwiz entry, full-screen hub and chat without bottom navigation, back/history access, preserved modules and session conversations. More links to learning, journal, scenarios, competitions, campus, account and billing. Existing stock subnavigation retained.
- Preserved native Kotlin/Compose, API clients, ViewModel, auth, encrypted storage, deep links, feature entitlements, notifications, activity tracking and all order safeguards. No fake market data or AI responses added. Removed raw charge schedule version from trade display only.
- Updated navigation and screenshot instrumentation assertions for approved navigation. Static source checks only so far. Android native workflow executes Gradle debug/release builds, unit tests, lint and emulator device tests; actual results and screenshots require verification. No merge, deploy or store release.
- Remaining before M3 completion: explicit per-screen refinement beyond shared theme, cross-screen visual review, responsive and large-text verification, font asset integration if needed, and final native CI.

### M3 typography and portfolio refinement
- First Android native run 45799446838 failed compiling the unavailable MoreHoriz icon; replaced with supported MoreVert. Emulator was skipped, not passed.
- Bundled existing licensed Plus Jakarta Sans, Inter and JetBrains Mono fonts into Android resources; use default variable axes and Compose weight synthesis. Glyph coverage and metrics need rendered verification.
- Home displays real account-scoped observed history via the existing 30-day history endpoint. No invented graph; line connections suppressed across reported data gaps. Added portfolio transaction-history entry, improved Finwiz starter flow and extended tests.
- Local Gradle execution blocked by unavailable Android SDK/Gradle and GitHub DNS. Native CI remains required; extra Home read should be measured in M4.

### M3 screen-specific refinement
- Refined derivative quote freshness presentation while retaining the exact contract, preview, acknowledgment and execution logic. Company financial chart colours, scenario builder and result surfaces, progress indicators, campus and account presentation now use shared tokens. All other existing Compose screens continue to inherit the new Material 3 theme and Panel design; no route removed.
- M3 implementation candidate now contains native navigation, typography, authentication, portfolio-first Home, Markets, company/trading surfaces, derivatives, portfolio history, full-screen Finwiz and More. Device screenshots and full native build/lint/unit/instrumentation results remain mandatory before marking M3 complete. M4 includes comprehensive route, parity, accessibility and visual inspection. No merge or release.

### M3 compact Home and screenshot coverage
- Reduced Home chart height to 100dp; balance details remain available via an explicit disclosure. Secondary links use reusable native touch targets. Added light/dark Home fixture renders using the same production overview and links, with visible portfolio and chart assertions. These are synthetic test data, not live quotes.
- Latest native workflow reruns on this head. Prior run was cancelled by workflow concurrency; no successful build or screenshot review claimed yet. No merge/deploy/store distribution.

### M3 native emulator regression follow-up
- Android native run 45800168642: build passed (debug/release, unit tests, lint and test APK); emulator ran 71 instrumentation tests, 68 passed and 3 failed. Logs identify outdated expectations in PremiumUiTest and WebParityScreenTest: full-screen Finwiz intentionally has no bottom nav, Orders belongs to Portfolio, and redesigned dark tokens are #050B09/#10E886/#123E2A.
- Reworked the 320dp / 1.6x font-scale regression to assert the absence of navigation in Finwiz, navigate back to Home, verify all five actual labels and their rendered bounds, and confirm Portfolio selection. Added adaptive vertically growing bottom navigation at font scales above 1.3x; retain default Material 3 navigation otherwise. No test disabled, and exact theme assertions retained with approved values.
- Changes are pending fresh Android workflow verification. Prior screenshots were collected by the failed emulator run; visual inspection remains outstanding. No merge, deploy or release.

## Milestone 4 — QA, optimisation and production readiness (in progress)
- M1 #200, M2 #202 and M3 #203 merged. Base main d9b381a429544a06f99fa5418518e9731f843c8c. M3 deployment reported by owner; production smoke not independently verified. M3 final Android run 45802541751: build and 71 device tests passed, 51 screenshot files collected. Full CI 45802541761, CodeQL and dependency review passed. Earlier pending statuses are historical, not the current M3 status.
- Closed known M2 trade-history gap with account-scoped executed equity/ETF history, filters, 25-row client paging and explicit loading/empty/error states. The existing authenticated endpoint returns all owned account trades; no new backend route or financial calculations. Derivatives remain separate. Removed charge-schedule version from dashboard display only. Corrected currency fallback in Stox Analysis to selected account currency independently of history response. Existing StoxScore component verified wired to account analytics endpoint.
- Added browser regression for >6 trades, pagination, filter, mobile overflow and internal-label suppression. New CI required. Further audit and release evidence pending. No deployment or release authorization assumed.

### M4 Android visual audit and corrective implementation
- Reviewed all 51 PNGs from successful M3 device artifact 15107393952 as contact sheets. Verified the expected shared palette, five primary tabs, dedicated Finwiz screen, original branding and major financial surfaces at fixture resolution. Found portfolio-analysis numbers wrapping mid-value and large-font market/navigation labels splitting words. Some captures are scroll-position/component-only fixtures rather than complete journeys.
- Fixed compact portfolio-analysis numeric columns with smaller financial typography and horizontal scrolling for values exceeding available width, preserving full precision; widened accessibility Portfolio/Markets tabs and enabled scrollable secondary market tabs at large font sizes. Added large-text secondary tab regression. Fixed isolated native chart observations so they remain visible even when no adjacent observed point exists. Fresh device validation required.
- Reviewed prior CI candidate evidence artifact 15108118591: 240 bounded authenticated requests (48 per surface), zero errors; history p95 8.78ms, portfolio p95 11.75ms; isolated backup restore compared 62 public tables successfully. These are disposable CI results for the prior M3 candidate, not production capacity or backup claims. No production credentials used.

### M4 web visual audit, runtime optimisation and release gates
- Reviewed 93 existing web screenshot thumbnails (artifact 15108427867, M3 candidate); confirmed login copy, shared theme, responsive search, stock charts, portfolio, F&O, journal, profile, status and Finwiz in the captured fixture states. Long full-page thumbnails are coverage evidence, not pixel-level verification of every route. Finwiz at 768px / 500px with enlarged text showed history and footer labels overlapping; fixed flex shrinking and added short-height regression.
- Added account-switching transaction test, including USD formatting when performance-history API is unavailable. Memoised currency formatting per account. Optimised frontend Docker runtime by copying Next standalone traced production dependencies, static assets and public assets rather than full node_modules and build output. Runtime remains non-root, port 3000, with existing configuration and healthcheck. Image-size reduction must be measured on the built image; new compose/browser CI is required.
- Added RELEASE_READINESS.md with evidence, remaining gates and rollback requirements. Production provider, email, billing, release-signing and VPS validation cannot be established by synthetic CI. Prior evidence applies to M3, not the M4 candidate. No deployment or signed release initiated.

### M4 standalone frontend health regression
- CI 45830318415: security, backend, frontend build and staging bundle passed; browser acceptance failed before Playwright because the frontend container remained unhealthy. Artifact 15121228990 confirms Next standalone reports Ready on 0.0.0.0:3000 without a startup exception. The exact health probe output was not captured; the likely cause is localhost resolving to IPv6 against the IPv4 listener.
- Updated local/CI, staging and production frontend health probes to use explicit 127.0.0.1 and report wget errors, retaining the existing HTTP content check and all retry/timeout settings. Public URLs and backend CORS settings unchanged. Added container health diagnostics (state health only, not environment/credentials) and avoid misleading missing-screenshot failure when browser tests never ran; screenshot artifacts remain mandatory after browser execution.
- Fresh CI required to confirm the hypothesis and complete browser regression. No deployment or merge performed.

### M4 owner-requested final web polish
- Previous candidate ca632ad verified: CI, Android native, CodeQL and dependency review all successful. Owner requested original typography, official social footer links and Finwiz history scrolling before merge.
- Restored the exact pre-M1 web font stack from main 80aecef (Inter/system UI/Apple/BlinkMacSystemFont/Segoe UI), using it for headings and numeric text as well. Removed web custom @font-face registrations so original system fallback behavior returns; existing font assets and licenses remain in the repository, Android fonts remain unchanged. Design palette, layout, branding and functionality unchanged.
- Shared social links added to authenticated Home, login/registration, marketing and legal footers using owner-provided URLs, with accessible labels and secure new-tab behavior.
- Finwiz history now scrolls independently from Learning map, Dashboard and history-management controls; outer sidebar remains scrollable at very short/zoomed sizes. Added 30-long-title browser fixtures at mobile and desktop, original-font assertions and social link checks. Fresh candidate CI and rendered verification required; no deployment.
