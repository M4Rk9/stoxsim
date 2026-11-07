# Optional Android push notifications

Firebase Messaging 26.0.1 is pinned after checking Google's official Maven metadata. No Firebase project values or credentials are committed. The default build compiles and runs without Firebase configuration, with push unavailable and in-app notifications retained.

## Private release configuration

Supply an ignored `mobile/android/project/app/src/main/res/values/firebase-config.xml` in protected release CI containing the Android app's Firebase `google_app_id`, `google_api_key`, `gcm_defaultSenderId`, and `project_id` string resources. These are Android client configuration values, never a service-account private key. Register the actual `com.stoxsim.app` application in the intended Firebase project and review its API restrictions. The Google Services Gradle plugin is not required: Firebase reads these standard resources. The backend separately requires the FCM settings and service identity documented in [notifications](NOTIFICATIONS.md).

FCM auto-initialization and analytics collection default off in the manifest. The learner must enable the push preference and grant Android 13+ notification permission before the client requests/registers an installation token. Configuration alone does not enable delivery. Permission denial leaves the server preference unchanged. Disabling the preference or signing out clears the local account binding before attempting unregister; server logout/session revocation separately removes device registrations. Local binding stores only the opted-in user ID, and purchase/account/device secrets never appear in notification text.

Both compatibility `onNewToken` and current `onRegistered` callbacks trigger account-bound re-registration only while an authenticated matching session is present. Startup/foreground checks the saved account's current server preference and system permission before registering again. API registration/unregistration uses the existing session mutex and expected account ID so a delayed callback cannot submit a token under another user's session. Tokens are held transiently; Firebase maintains its own private installation storage. Delayed messages for signed-out or different accounts are dropped. Local logout/binding changes and notification display use the same lock, so an already received message cannot post after local logout has cleared notifications. The generic notification opens the native inbox only for the same account; detailed content is fetched through authenticated APIs.

## Acceptance still required

Local JVM tests and compilation do not verify real push delivery. Before activation, test a signed build on actual Android 12 and 13+ devices: explicit opt-in/denial, token rotation, foreground/background/force-stop, permission revocation, device/account switching, expired/revoked sessions, logout during a pending callback, invalid registrations, duplicated/out-of-order messages, and provider outage. Verify that no previous account's detailed content appears after a switch. Android background delivery is best-effort; normal-priority data messages can be delayed or suppressed. No Firebase activation, upload signing, store publication or provider sends were performed by development tasks.

## Local validation

The final optional-push sources passed all 75 JVM unit tests in 14 suites (zero failures/errors/skips), debug APK and instrumentation APK compilation, debug lint, and minified unsigned release APK/AAB builds. The sequential final run completed 131 tasks successfully on 6 November 2026. It included account-bound token mutation, rejected cross-account/delayed messages, the synchronized logout/display boundary and both token callbacks. Device instrumentation was compiled but not executed. The compatibility token getter/callback is deprecated by the pinned SDK; the current registration callback is handled as well.

Backend FCM/notification checks passed 29 tests, including current ownership, opt-out, invalid device registrations, partial delivery and uncertain outcomes and stalled/oversized response handling. These checks used local fixtures and mocks.

| Final local artifact | SHA-256 |
|---|---|
| `apk/androidTest/debug/app-debug-androidTest.apk` | `48214bf524b78fa7f70ec065deff66d5ea065339d118a935e6d7657d8362c155` |
| `apk/debug/app-debug.apk` | `e8e5db9919ab98992a7bad8540e32120e99b05ea8675326202c83585fb29a1e5` |
| `apk/release/app-release-unsigned.apk` | `7f7a6acf24c79f7e4376e9029bc6c079c3c833de2a912dbafee225ee47a430c2` |
| `bundle/release/app-release.aab` | `98617ae0c81f16f20585de6eb61436314392dabddc4f48ea23d1a3a2fba11e7f` |
