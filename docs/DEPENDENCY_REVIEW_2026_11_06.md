# Dependency review — 6 November 2026

The successful Git push reported **six dependency alerts on the default branch: four critical and two high**. Alert IDs, affected packages, fixed versions and current review/check states could not be retrieved because `api.github.com` remains blocked by the active environment policy. These alerts are unresolved release gates, not inferred to be fixed by the feature work.

`npm audit --json` against the candidate frontend lockfile returned zero reported vulnerabilities across 85 dependencies (including development/optional metadata). This result covers the npm advisory response only; it does not override GitHub's repository alerts or assess Maven/container dependencies.

The default branch still points to `9c59184083e43dcab20959829f6765373375b566`. The following Git PR refs were fetched and inspected. Git refs alone do not establish whether a PR is open, approved, or currently green in CI.

| PR | Inspected head | Actual change | Review disposition |
|---|---|---|---|
| [170](https://github.com/M4Rk9/stoxsim/pull/170) | `b044ddc9346680bc8a5587911c0d6a1a9aab4854` | Spring Boot parent 4.1.1 → 4.1.2; one POM line | Still isolated from feature branches. The current head differs from the previously inspected head; historical CI is not sufficient. Obtain current security mapping and run full candidate validation before merge. |
| [173](https://github.com/M4Rk9/stoxsim/pull/173) | `9e2a667db98c425e87538c51008a5c67fafec289` | Next 16.4.2 → 16.4.3; package and lockfile | Patch candidate, not automatically assumed to fix the six alerts. Requires typecheck, production secret isolation and browser regressions. |
| [174](https://github.com/M4Rk9/stoxsim/pull/174) | `4c18642bb60c0dd08e7fc7d0caef13d3f8b72c1e` | Runtime image Java 21 → Java 24 | Changes the Java major, not merely the image patch. Keep separate from a Java 21 security patch and require an explicit runtime compatibility/support review. |
| [175](https://github.com/M4Rk9/stoxsim/pull/175) | `9facb917334d49adc4377eaf9ba5ddba295ca70e` | Maven build image uses Java 26 instead of Java 21 | Changes the build JDK major and drops the exact Maven patch tag. Do not treat this as an interchangeable Java 21 image refresh. |
| [176](https://github.com/M4Rk9/stoxsim/pull/176) | `a3d79a7b917a4a212f8663125fbdf0847d5e00f2` | Node type definitions 26.6.5 → 26.6.6 | Development typing change; no runtime vulnerability-remediation claim. |

No dependency PR was merged, no main-branch update was pushed, and no Docker/JDK major upgrade was applied. Required next evidence is the actual [Dependabot alert list](https://github.com/M4Rk9/stoxsim/security/dependabot), exact fixed-version mapping, and checks for the reviewed heads. The feature candidate's passing tests are not evidence that these dependency alerts are resolved.
