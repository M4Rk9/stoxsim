# PR #170 review — 28 October 2026

Recommendation: **merge**, subject to the existing up-to-date branch checks. Do not close the patch or combine it with the Google sign-in feature. This review does not merge or deploy either change.

## Scope and compatibility

Reviewed PR head `8d44f1065ec7061e04224f8fd8cbc14bc201e9f1` against main `008eb40d858f1448e15843b30c5d71be5fc75bf3`. One changed file, `backend/pom.xml`, one-line parent bump from 4.1.1 to 4.1.2. No source, configuration, or database migration changes.

The POM targets Java 21; CI uses Temurin 21; the Docker build uses Maven 3.9.11 / Temurin 21 and the runtime is Temurin 21. Boot 4.1.2 supports Java 17–27 and Maven >=3.6.3. This is a patch within the existing Boot 4.1 generation, not a Jakarta or Java migration. Boot also manages build plugins and many transitive dependencies, so the actual runtime impact is broader than the single line.

The project pins PostgreSQL JDBC to 42.7.12 and Upstox SDK to 1.29. Those explicit versions are not automatically upgraded by this parent update. In particular, Boot's listed PostgreSQL 42.7.14 upgrade is overridden. Audit/remove that override separately after checking why it exists; do not assume this PR updates every dependency in the release notes.

## Application-specific impact

| Release area | StoxSim assessment |
| --- | --- |
| JWT issuer / JWK handling | `SecurityConfig` defines its own HS256 `NimbusJwtDecoder`, with issuer `TokenService.ISSUER` and `ActiveSessionJwtValidator`. It does not use Boot's issuer-uri / jwk-set-uri auto-configuration. Do not weaken issuer checks as part of this update. |
| OAuth2 configuration | Existing resource-server support validates StoxSim bearer tokens; it is not Google login. No custom authorities-claim-delimiter or OAuth2 client registration is configured. These auto-configuration fixes are mostly prospective. |
| Graceful shutdown | Directly relevant: servlet/Tomcat backend has graceful shutdown enabled. Production already grants a 45-second stop grace period. Test in-flight HTTP requests and WebSocket disconnect/reconnect during restart. |
| Netty | The Netty web-server shutdown fix is not the Tomcat server path. Netty can still be present through Redis/Lettuce and other clients, so exercise Redis, quotas and market-data connectivity after rollout. |
| MongoDB credentials | PostgreSQL/JPA application; no MongoDB dependency or connection configuration found. Not an active application path. |
| SSL | Public TLS terminates at Caddy, with sanitized forwarded headers to the backend. No embedded-Tomcat SSL bundle/customizer found. Retest HTTPS, secure cookies and WSS; Redis SSL is a separate optional client setting. |
| Docker Compose | The repository uses external `docker compose`, not the Spring Boot Docker Compose development module. These Spring integration fixes do not change its orchestration scripts. |
| Wider managed upgrades | Security, framework, Tomcat, Hibernate, Jackson and Netty updates can affect authentication, serialization, persistence, clients and packaging. CI exercises important paths, but production-provider behavior still needs rollout smoke tests. |

These are useful maintenance and security-hardening updates. The cited Boot release notes describe bug fixes and dependency upgrades; they do not establish that StoxSim has a particular exploitable CVE or that this is an emergency security patch.

## Checks inspected

All 10 check runs on the reviewed head completed successfully:

- backend: 335 tests, zero failures/errors/skips.
- frontend: typecheck/build and frontend-secret-isolation checks.
- browser-acceptance: 105 browser tests passed; disposable-stack startup, candidate load, backup restore and synthetic operational drills passed.
- staging-bundle: deployment/configuration checks.
- Secret history scan and Dependency and configuration scan.
- Java/Kotlin and JavaScript/TypeScript CodeQL analysis, plus the CodeQL result check.
- dependency-review.

The dependency-review bot comment says **Scanned Files: None**. Its green result is not proof that the resolved Maven dependency graph is vulnerability-free. Trivy also excludes unfixed findings and gates HIGH/CRITICAL only. Do not describe these checks as a comprehensive security certification.

The active Protect Main ruleset requires an up-to-date branch, resolved review threads, and backend/frontend/staging-bundle/browser-acceptance/dependency-review/both language CodeQL checks. There are no legacy commit-status entries; the legacy `pending` response with an empty list is not a failed or running check.

## Deployment and rollback

Use the existing Production candidate and explicitly dispatched Production deploy workflows with an immutable commit SHA. Merging is separate from deploying. Before rollout, preserve the last good image and verify the candidate against the current main revision. After rollout check health/readiness, password login, refresh/logout/session revocation, a simulated order, PostgreSQL/Flyway, Redis rate limiting, market-data HTTP/WebSocket connections, and shutdown/reconnect behavior. These deployment observations have not been performed on production during this review.

The parent-only update has no migration, so rollback to the previous immutable image does not require a schema rollback. Keep this upgrade separate from Google's V125 migration.

## Evidence

- https://github.com/M4Rk9/stoxsim/pull/170
- https://github.com/M4Rk9/stoxsim/actions/runs/40240792681
- https://github.com/M4Rk9/stoxsim/rules/22662682
- https://docs.spring.io/spring-boot/system-requirements.html
- https://github.com/spring-projects/spring-boot/releases/tag/v4.1.2
- Repository: `backend/pom.xml`, `backend/Dockerfile`, `SecurityConfig.java`, `application.yml`, CI/security workflows and production Compose/Caddy/deployment configuration.
