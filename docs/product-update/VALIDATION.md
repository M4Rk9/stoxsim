# Milestone 1 validation

## Local

- Frontend typecheck and production build: passed locally; frontend CI and browser secret isolation passed for `1169527`. Typecheck also passed after adding home screenshot captures.
- Browser test attempt: installed Chromium fails before page execution with `process_singleton_posix.cc: socket() failed: Operation not permitted`. The shared browser cannot access the execution environment's loopback (`ERR_CONNECTION_REFUSED`). No local screenshot or browser pass is claimed.
- Java production and test compilation passed locally using an official Adoptium JDK 21. The complete backend test suite passed in CI for `1169527` (run `43415513761`).
- Android: no SDK/emulator in the local environment. Native build, lint and instrumentation run through the repository's existing Android PR workflow.

## Added acceptance coverage

- Backend integration: structured strategy validation; private ownership; cross-user and mismatched-symbol order-link rejection; idempotent same-order linking; old-client updates preserve plan fields; completion needs nonempty review text; first completion timestamp and daily marker deduplicate; existing AI consent, export/deletion and financial-isolation coverage remains applicable.
- Browser: create strategy from research context, save privately, link an existing executed order, compare actual outcome, complete review, and verify no AI request or localStorage note persistence. Runs at 390px dark and 1440px light/dark; screenshots attach to the Playwright report with explicit synthetic-fixture labels.
- Existing navigation, stock-detail buy/sell, chart inspection, private-reflection consent and learner-journey checks updated for intentional copy/navigation changes.
- Native: existing consent UI test updated for the order-link callback; learning navigation labels updated. Added completion/plan-preservation and matching-existing-order tests plus labelled light/dark journal screenshots. The completion checkbox exposes a single labelled, selectable row. Native debug/release build, unit tests, lint and instrumentation compilation passed for `6198fd5`; device execution is tracked separately. Real-device keyboard/insets, large font, rotation and authenticated journey acceptance remain required before release.

## CI evidence

- [Corrected browser/CI run](https://github.com/M4Rk9/stoxsim/actions/runs/43461415467): backend, frontend, deployment configuration, security scans and browser test stage passed at this checkpoint. Initial failures in outdated label/theme assertions and a journal selector were corrected; the native test's unsupported `exact` argument was removed.
- [Mobile home refinement CI](https://github.com/M4Rk9/stoxsim/actions/runs/43461893847): backend (469 tests), frontend build, deployment configuration and security checks passed at this checkpoint.
- [Native validation](https://github.com/M4Rk9/stoxsim/actions/runs/43461894020): build, unit tests, lint and instrumentation compilation passed. Emulator executed 61 tests: the 4 new strategy/screenshot tests passed; one existing theme test still expected the old palette and is updated in this commit. Final rerun results are linked on the PR.
- [CodeQL](https://github.com/M4Rk9/stoxsim/actions/runs/43415513789): initial Java/Kotlin and JavaScript/TypeScript analysis passed. Dependency review passed. Refer to the PR checks for analysis of the latest commit.
- Initial desktop/mobile home and 320px security screenshots were downloaded and visually inspected. Findings drove the compact mobile totals, five readable navigation destinations, wrapped stock header actions, and HTML chart date labels that remain readable on mobile.
- Web captures are attached to the Playwright report (journal at 390px dark and 1440px light/dark, home at 390px dark and 1280px light). Native captures use `strategy-journal-light.png` / `strategy-journal-dark.png` in `android-device-reports`. Capture definitions are not proof of a successful render; inspect the produced artifacts before review approval.

The current tested commit, final screenshots and any remaining failures are recorded in [PR #191](https://github.com/M4Rk9/stoxsim/pull/191); this document records checkpoints rather than a claim that every subsequent head has passed. Review CI results and attached screenshots before merging. Passing web viewport tests does not establish native Android acceptance. No production deployment or provider test has been performed.
