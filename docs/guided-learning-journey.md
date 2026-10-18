# Guided learning journey

Web and Android 0.5.5 share `/api/v1/onboarding/journey`. Home exposes Finwiz AI, Scenario Lab, Learning Path and Competitions above market exploration. Hiding guidance keeps all shortcuts available; Resume guide restores the next unfinished step.

Suggested sequence: home introduction → executed standard-account trade → successful Finwiz question → completed scenario → review Learning Path → explore Competitions. Features remain accessible out of order. Pending orders show a waiting step; cancelled/rejected/expired orders and sandbox trades do not complete activation. Competition enrolment is optional.

Progress is server-owned and shared across devices. V122 stores successful Finwiz use and Learning Path/Competitions page loads idempotently. Existing trade and scenario records supply those steps. Historical Finwiz conversations were not stored on the server, so their completion starts when this release records a successful request. Automatic daily check-ins do not mark Learning Path viewed. There is no client completion endpoint.

Home guidance checks progress while visible and after actions or returning to the app. Feature-page guidance explains what to do and links onward or home. Existing credits, plan limits and competition consent still apply. Guidance never submits a trade, question, scenario or enrolment for a user.

Deploy the backend with V122 before web/Android. Existing onboarding routes remain compatible. Android 0.5.5 uses versionCode 11.

Validation covers execution gating, sandbox exclusion, failed Finwiz requests, scenario outcomes, private/idempotent progress, dismissal/resume, navigation and responsive cards. Browser captures cover 320, 390 and 1280 px in light/dark; native captures cover 320 dp and 1.5× text.

After release, compare first-execution conversion and mature D1/D7 cohorts. Retention improvements require measurement after deployment.
