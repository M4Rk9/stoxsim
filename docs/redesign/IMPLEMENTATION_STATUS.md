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
- Reusable dialogs/tooltips/skeletons, component consolidation, true cross-route global search, final dashboard layout, Google/recovery visual QA, light/dark and responsive route audit pending.
- Original ZIP mentioned by spec was not attached in this turn; SVG and screenshots used.

### Next
Complete M1 pending items, test and resolve regressions, update journal. No merge or deployment or advancement to M2 without approval. M2 web, M3 Android and M4 QA not started.
