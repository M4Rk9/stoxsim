# Milestone 1 validation

## Local

- Frontend typecheck and production build: passed on initial implementation; rerun on final source before handoff.
- Browser test attempt: installed Chromium fails before page execution with `process_singleton_posix.cc: socket() failed: Operation not permitted`. The shared browser cannot access the execution environment's loopback (`ERR_CONNECTION_REFUSED`). No local screenshot or browser pass is claimed.
- Maven: installed a task-local Maven runner and explicit inherited proxy settings. The environment's advertised Java 21 runtime did not provide a usable Java 21 compiler; downloaded the official Adoptium JDK for a fresh compilation attempt.
- Android: no SDK/emulator in the local environment. Native build, lint and instrumentation run through the repository's existing Android PR workflow.

## Added acceptance coverage

- Backend integration: structured strategy validation; private ownership; cross-user and mismatched-symbol order-link rejection; idempotent same-order linking; old-client updates preserve plan fields; completion needs nonempty review text; first completion timestamp and daily marker deduplicate; existing AI consent, export/deletion and financial-isolation coverage remains applicable.
- Browser: create strategy from research context, save privately, link an existing executed order, compare actual outcome, complete review, and verify no AI request or localStorage note persistence. Runs at 390px dark and 1440px light/dark; screenshots attach to the Playwright report with explicit synthetic-fixture labels.
- Existing navigation, stock-detail buy/sell, chart inspection, private-reflection consent and learner-journey checks updated for intentional copy/navigation changes.
- Native: existing consent UI test updated for the order-link callback; learning navigation labels updated. Real-device keyboard/insets, large font, rotation and authenticated journey acceptance remain required before release.

Review CI results and attached screenshots before merging. Passing web viewport tests does not establish native Android acceptance. No production deployment or provider test has been performed.
