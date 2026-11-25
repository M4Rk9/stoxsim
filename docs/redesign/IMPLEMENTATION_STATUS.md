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
