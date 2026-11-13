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
- Native: existing consent UI test updated for the order-link callback; learning navigation labels updated. Added completion/plan-preservation and matching-existing-order tests plus labelled light/dark journal screenshots. The completion checkbox exposes a single labelled, selectable row. Native debug/release build, unit tests and lint passed for `1169527`; the additional tests await the next CI run. Real-device keyboard/insets, large font, rotation and authenticated journey acceptance remain required before release.

## CI evidence

- [Initial CI run](https://github.com/M4Rk9/stoxsim/actions/runs/43415513761): backend, frontend, deployment configuration, dependency/configuration and secret-history checks passed. Browser acceptance was still running at this checkpoint.
- [Initial native run](https://github.com/M4Rk9/stoxsim/actions/runs/43415513779): build/unit/lint passed; emulator tests were still running at this checkpoint.
- [CodeQL](https://github.com/M4Rk9/stoxsim/actions/runs/43415513789): Java/Kotlin and JavaScript/TypeScript analysis passed. Dependency review also passed.
- Web captures are attached to the Playwright report (journal at 390px dark and 1440px light/dark, home at 390px dark and 1280px light). Native captures use `strategy-journal-light.png` / `strategy-journal-dark.png` in `android-device-reports`. Capture definitions are not proof of a successful render; inspect the produced artifacts before review approval.

Review CI results and attached screenshots before merging. Passing web viewport tests does not establish native Android acceptance. No production deployment or provider test has been performed.
