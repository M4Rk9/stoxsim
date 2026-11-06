# Native commercial experience

Five navigation areas group existing capabilities: Home, Markets, Learn, Compete and Account. Markets retains search, watchlists, holdings and orders; Learn retains the guided journey, Finwiz, Learning Path and Scenario Lab alongside optional decision reflections. Campus competitions remain accessible within Compete. Account retains identity, security, export/deletion, wallet and subscription management.

Home reads deterministic server recommendations and current portfolio facts. It uses the backend profile and recommendation-use endpoint; analytics failure does not prevent navigation. A short optional learner profile persists experience, objective and preferred market through the shared API without changing portfolio state. Only known native destinations are accepted from notification/recommendation links, including the backend's first-trade, pending-order and reflection fragment URLs; external/scheme-relative/unknown links are rejected.

The notification center reads owner-scoped items, supports read/read-all and controls learning, competitions, portfolio education, announcements, promotions, push preference and optional digest settings. Security notices remain separate. Push preference records intent only: this build does not register a Firebase app or claim that notifications will be delivered to the device. Firebase project/app settings, delivery credentials, device permission/token lifecycle and actual push acceptance remain open. No new paid notification service was introduced.

Optional reflections support private creation, editing and deletion, with bounded fields and preserved account/order references. Draft text remains in Compose memory rather than saved instance state or disk. Failed saves retain the editor and show the error. Each AI assistance request requires its own explicit sharing confirmation. Reading/editing does not send text to Finwiz. Responses use the existing Finwiz renderer and educational disclaimer; no calculation or financial state is changed. The server enforces ownership and consent independently.

Native Play subscriptions are described in [ANDROID_PLAY_BILLING.md](ANDROID_PLAY_BILLING.md). The account-deletion confirmation explicitly warns that deleting StoxSim data does not cancel recurring Play billing.

## Verification

On 5 November 2026 the clean final run completed successfully in 6m43s (133 Gradle tasks):

```sh
./gradlew clean :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease :app:bundleRelease --console=plain
```

All **73 JVM unit tests passed**, with zero failures, errors or skips. Debug APK, instrumentation APK, debug lint, minified unsigned release APK and release AAB compilation passed. New contract tests cover profile clearing, bounded reflection data and ownership references, allowed navigation targets and rejected external links. Navigation instrumentation expectations now cover five areas, and new UI tests require explicit AI-sharing confirmation. Those device tests were compiled, not executed; no emulator or signed Play acceptance is claimed.

Artifact SHA-256 values for this local candidate (generated outputs are not committed):

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `17048f4cd708abd5a981bbaf9f1bd96f4409a1fe5a17eecadec2add6044d1310` |
| Instrumentation APK | `48214bf524b78fa7f70ec065deff66d5ea065339d118a935e6d7657d8362c155` |
| Unsigned release APK | `c1049399c19be3c469a52a6b7b00923b4b5fc2249368608f3bcefac016d9f2da` |
| Release AAB | `51344b56f20bba94e67e1df5a84bcbf8f7b2ada901ec494791358f285422af4b` |

An earlier overlapping incremental build failed while expanding the release profile because a generated Kotlin class was missing. All owned Gradle daemons were stopped and generated build state was cleaned; the sequential run above passed unchanged sources. No tests were disabled.

On a device or emulator, validate all five navigation areas at large font sizes, light/dark modes and narrow screens; re-login/account switching; unavailable optional endpoints; profile persistence across web/Android; owner-isolated notifications/reflections; failed-save draft retention; explicit AI consent; and billing restoration. Signed Play purchase/renewal/refund tests and FCM delivery require external configuration. No Android artifact has been published and no real purchase was made.

The implementation uses existing backend APIs and local infrastructure, adding no recurring service purchase. Provider use continues to depend on server quotas and owner-controlled activation.
