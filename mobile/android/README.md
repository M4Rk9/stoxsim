# StoxSim Android

This is the **Android preparation batch**, not a paid release candidate.
The Android source project is in `project/`. It opens `https://stoxsim.com`
using a Trusted Web Activity (TWA), reusing the existing Next.js frontend,
Spring API, accounts and portfolios. The app depends on connectivity and a
compatible browser. Website updates continue to ship through normal deployment.

The draft application ID is `com.stoxsim.app`. Confirm it before the first
Play upload; changing the application ID later creates a different app.
The shell currently has no purchase UI or Play Billing adapter. The owner-provided
debug signing certificate is associated for emulator testing after website deployment.
The Play app-signing certificate must still be configured for a Play-distributed build.
Billing must be completed before the requested paid launch.

## Open and build

1. Install Android Studio, JDK 17 and Android SDK Platform 36/build tools.
2. Open `mobile/android/project` in Android Studio. Allow Gradle synchronization.
3. Set the local SDK path through Android Studio (`local.properties` is ignored).
4. Build a debug APK with `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).
5. Use Android Studio's **Generate Signed App Bundle / APK → Android App Bundle**
   for an internal-test release. Keep the upload keystore under `../private/`
   or another protected directory outside the repository. Keep passwords in a
   password manager; do not put them in tracked Gradle files or logs.
6. Enroll in Play App Signing. Upload the signed `.aab` to the correct app's
   internal-testing track after completing the needed Play Console setup.

The project targets API 36 and has minimum API 28. Do not downgrade the target
to work around tooling errors. The Gradle wrapper is included. APK/AAB outputs,
SDK paths, signing keys and service-account credentials are ignored by Git.

### Website association

`frontend/public/.well-known/assetlinks.json` now includes the owner's debug
certificate fingerprint, supplied after the owner built and launched the app in
Android Studio. It authorizes `com.stoxsim.app` builds signed with that certificate.
It takes effect only after the website change is deployed. Full-screen launch is
still unverified. This is not the Play app-signing certificate; replace the debug
association with the actual Play certificate before production release unless
continued debug access is intentionally needed.

Copy the **SHA-256 app-signing certificate** from Play Console's App integrity /
App signing page. It is a public fingerprint, not a private key. The upload
certificate is not necessarily the certificate on the Play-delivered application.

From `mobile/android`, run:

```bash
node configure-asset-links.mjs "YOUR_ACTUAL_COLON_SEPARATED_SHA256_FINGERPRINT"
```

The script rejects placeholders or malformed fingerprints, writes the association
for the configured application ID, and supports multiple certificate arguments
when legitimately needed. Commit the public JSON and deploy the website.
Verify the JSON is served without a redirect at
`https://stoxsim.com/.well-known/assetlinks.json`, then install through Google Play
and check the app opens full screen. Debug builds need their own certificate;
do not add a shared, untrusted or arbitrary certificate to production.

### Public resources

- Web manifest: `https://stoxsim.com/manifest.webmanifest`
- Icons: `/app-icons/icon-192.png`, `/app-icons/icon-512.png`, `/app-icons/maskable-512.png`
- Privacy: `https://stoxsim.com/privacy`
- Account deletion help: `https://stoxsim.com/delete-account`

The new website resources become public only after this PR is merged and deployed.
The deletion help uses the existing authenticated settings deletion flow; it does
not create an unauthenticated deletion endpoint.

## Validation

```bash
node --test asset-links.test.mjs
```

The frontend Android preparation tests exercise the production HTTP manifest,
actual image dimensions, maskable background, public deletion help and the configured
certificate association. Run them against a started website:

```bash
cd ../../frontend
PLAYWRIGHT_BASE_URL=http://localhost:3000 npx playwright test e2e/android-preparation.spec.ts
```

The Android project was generated from GoogleChromeLabs/bubblewrap 1.27.0
templates, then retained as editable native source. Apache-2.0 notices are
preserved in derived files. No Bubblewrap npm dependency is added to StoxSim:
the generator's development dependency audit reported unresolved issues, so its
dependency tree is not committed or included in the website/runtime build.
`twa-manifest.json` records the original configuration; editing that JSON alone
does not update native source. Use Android Studio/native source for subsequent edits.

Read [the paid launch plan](../../docs/ANDROID_PLAYSTORE_LAUNCH.md) before release.
