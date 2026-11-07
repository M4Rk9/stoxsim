# Focused commercial review stack

Eight review branches were successfully pushed to `M4Rk9/stoxsim`. No feature PR has been created or merged. The GitHub API is blocked by the active environment policy; branch publication uses the available Git remote. Each branch is based on the preceding branch, so reviews must use the base listed below rather than treating all branches as independent changes against main.

| Branch | Intended PR base | Scope |
|---|---|---|
| [review/01-execution-register](https://github.com/M4Rk9/stoxsim/tree/review/01-execution-register) | `main` | Document commercial execution scope and release gates |
| [review/02-learner-reflections](https://github.com/M4Rk9/stoxsim/tree/review/02-learner-reflections) | `review/01-execution-register` | Add learner preferences, private reflections and bounded AI usage |
| [review/03-notification-infrastructure](https://github.com/M4Rk9/stoxsim/tree/review/03-notification-infrastructure) | `review/02-learner-reflections` | Add consent-based inbox and bounded notification delivery |
| [review/04-verified-billing](https://github.com/M4Rk9/stoxsim/tree/review/04-verified-billing) | `review/03-notification-infrastructure` | Add provider-verified subscriptions and account lifecycle safeguards |
| [review/05-web-experience](https://github.com/M4Rk9/stoxsim/tree/review/05-web-experience) | `review/04-verified-billing` | Connect five-area web navigation to learner and billing flows |
| [review/06-android-experience](https://github.com/M4Rk9/stoxsim/tree/review/06-android-experience) | `review/05-web-experience` | Add native learner parity and account-bound Play subscriptions |
| [review/07-release-hardening](https://github.com/M4Rk9/stoxsim/tree/review/07-release-hardening) | `review/06-android-experience` | Record integrated validation and commercial acceptance limits |

| [review/08-optional-push-delivery](https://github.com/M4Rk9/stoxsim/tree/review/08-optional-push-delivery) | `review/07-release-hardening` | Optional bounded FCM delivery and explicit native push consent |

Backend layers 02, 03 and 04 each passed independent test compilation. The stage07 tree exactly matched the tested integration snapshot: 435 backend tests, 126 Chromium tests and 73 Android JVM tests passed. These counts refer to the core candidate before the separate optional FCM extension. Individual GitHub CI results and approvals have not been verified.

PR titles, bodies and exact base/head mappings are prepared in `/workspace/stoxsim-work/review-prs/manifest.json`. Creating the PRs remains pending network access. No review/merge recommendation supersedes the six unresolved default-branch dependency alerts documented in [the dependency review](DEPENDENCY_REVIEW_2026_11_06.md).

The optional FCM follow-on layer passed 29 focused backend transport/notification tests and 75 Android JVM tests, plus lint and debug/instrumentation/unsigned release builds. Those backend suites overlap core coverage; do not add the counts as unique tests. Actual configured device delivery remains an acceptance gate. See [progress](STOXSIM_PROGRESS.md) for current validation and external limits.
