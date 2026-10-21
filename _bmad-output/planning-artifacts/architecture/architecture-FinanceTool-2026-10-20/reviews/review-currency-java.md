---
review: currency (Java/Angular final-app spine)
target: ARCHITECTURE-SPINE.md (status draft, 2026-10-20)
date: 2026-10-20
lens: every committed decision web-researched or reality-checked, not asserted from training data
verdict: PASS WITH FIXES (2 high, 4 medium, 4 low; no blocker)
---

# Currency review: Java 25 / Spring Boot 4.1.1 / Modulith 2.1.1 / Angular 22 spine

Sources were checked live on 2026-10-20: repo1.maven.org metadata and POMs, registry.npmjs.org, Docker Hub tags API, the Spring Security 7.1.1 source on GitHub, the hibernate-core 7.4.5 jar, the docs at docs.spring.io, springdoc.org, angular.dev (v22.2.3), docs.oracle.com (JDK 25), and Ollama v0.40.6 `docs/api.md`, plus GitHub READMEs and release notes. The memlog already covers release versions, the Ollama tags and digest, and the pgvector and ollama image tags, so this review does not re-check them.

## Findings

### H1: The spine says Flyway is "Boot-managed (13.x)", but Boot 4.1.1 manages Flyway 12.4.0 (high)
- **Evidence.** `spring-boot-dependencies-4.1.1.pom` contains `<flyway.version>12.4.0</flyway.version>`, and the Boot 4.1 release notes list "Flyway 12.4.0". The 13.10.3 in the memlog is the latest version on Maven Central, not the version Boot manages. The stack row mixes the two.
- **Also unstated.** Boot 4 is modularized, so it auto-configures Flyway only when `spring-boot-starter-flyway` is present. The Boot 4.0 migration guide says: "You now need to replace that with `spring-boot-starter-flyway`". Flyway 10+ also needs `org.flywaydb:flyway-database-postgresql`; version 12.4.0 exists on Central. PostgreSQL 17 has been supported since Flyway 10.20, so 12.4 is fine.
- **Fix.** Change the stack row to "Flyway 12.4.x (Boot-managed) via `spring-boot-starter-flyway` + `flyway-database-postgresql`". Do not override `flyway.version` to 13.

### H2: The spine says "JUnit 5", but Boot 4.1 ships JUnit 6, which affects the ArchUnit artifact (high)
- **Evidence.** The Boot 4.1.1 BOM contains `<junit-jupiter.version>6.0.3</junit-jupiter.version>`. ArchUnit 1.5.0 release notes say: "`archunit-junit6` supports ArchUnit with JUnit 6". `com.tngtech.archunit:archunit-junit6:1.5.1` exists on Central. The spine says "JUnit 5" in AD-15 and in the Tests convention, and lists "ArchUnit 1.5.1" without naming the artifact.
- **Fix.** Replace "JUnit 5" with "JUnit (Jupiter 6, Boot-managed)" and pin `archunit-junit6:1.5.1`. Do not use `archunit-junit5`.

### M1: ng-openapi-gen 1.x defaults to functions returning Promises, not Angular services (medium)
- **Evidence.** The ng-openapi-gen README says: "Starting with version 1.0 ... `services` is `false` ... `promises` is `true`". The tool generates functions plus one `Api` injectable. The spine (AD-19 and the Frontend convention) says it "generates the Angular services" and that the frontend uses "`HttpClient` with the generated services".
- **What does fit.** OpenAPI 3.1 is supported "since ng-openapi-gen 1.0". springdoc 3's `springdoc.api-docs.version` defaults to `openapi_3_1`. Generated code works with "Angular 16+", and the npm peer range is `@angular/core >=16`.
- **Fix.** Pin `ng-openapi-gen.json` with `"services": true, "promises": false`, or rewrite AD-19 to say functions/`Api`. Either way, make the choice explicit.

### M2: pgvector-java 0.1.6 is not the right fit for JPA on Hibernate 7 (medium)
- **Evidence.** `com.pgvector:pgvector:0.1.6` was last published on 2024-07-17. Its POM tests against `hibernate-vector 6.4.0.Final`, `spring-jdbc 5.3.31` and `postgresql 42.7.3`. Its README says Hibernate 6.4+ has its own vector module, which should be used instead (`@JdbcTypeCode(SqlTypes.VECTOR)` on a `float[]`). `org.hibernate.orm:hibernate-vector` is in the `hibernate-platform` 7.4.5 BOM, which Boot manages. The persistence convention says "Spring Data JPA for owned tables", including `kb_chunks`.
- **Fix.** Choose one access path in AD-9 / Persistence:
  - JPA: use `hibernate-vector` (Boot-managed 7.4.x) and drop pgvector-java.
  - Plain JDBC (`JdbcClient`) in the KB adapter: keep pgvector-java 0.1.6, which still works over JDBC, and note that it is an older library.

### M3: Seeding with `RandomGenerator.of("L64X128MixRandom")` does not compile as written (medium)
- **Evidence.** The JDK 25 `java.util.random` package lists `L64X128MixRandom` as available and not deprecated, so the algorithm name is fine. However, `RandomGenerator.of(String)` takes no seed, and `RandomGenerator` has no `setSeed`.
- **Fix.** Change the Randomness convention to `RandomGeneratorFactory.<RandomGenerator>of("L64X128MixRandom").create(seed)`.

### M4: The CSRF wiring is only half specified (medium)
- **Evidence for the Spring side.** Spring Security 7.1.1 `CsrfConfigurer.spa()` does exactly the following (source checked):
  ```java
  csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
  requestHandler = new SpaCsrfTokenRequestHandler();
  ```
  The handler sets `xor.setCsrfRequestAttributeName(null)`, which loads the token eagerly, so the `XSRF-TOKEN` cookie is written on the first response.
- **Evidence for the Angular side.** Angular v22.2.3 `HttpClient` reads the `XSRF-TOKEN` cookie and sends an `X-XSRF-TOKEN` header on "mutating requests ... to relative and same origin URLs". The two defaults match.
- **Gap.** AD-11 says only "CSRF token cookie read by Angular's HttpClient XSRF support". It does not name `csrf.spa()`. The default form login also answers with 302 redirects and an HTML login page, not JSON.
- **Fix.** In AD-11, name `http.csrf(c -> c.spa())`. Use form-login success and failure handlers that return 200/401 JSON, or a JSON login endpoint, for the SPA. Keep the SPA same-origin, which AD-17 already does.

### L1: `spring-boot-starter-web` is deprecated in Boot 4 (low)
- **Evidence.** Its 4.1.1 POM description says "deprecated in favor of spring-boot-starter-webmvc".
- **Related.** Boot 4 test starters are per technology: `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-security-test` and `-flyway-test` all exist at 4.1.1.
- **Fix.** Write "webmvc" in the stack row.

### L2: Testcontainers 2.0 renamed its modules (low)
- **Evidence.** `org.testcontainers:postgresql` and `ollama` stop at 1.21.4. The 2.0.5 artifacts are `testcontainers-postgresql`, `testcontainers-ollama` and `testcontainers-junit-jupiter`, all present in `testcontainers-bom:2.0.5`, and Boot 4.1.1 manages `testcontainers.version` 2.0.5.
- **Fix.** Name these artifacts in AD-15. Use `DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres")`. Wire `APP_OLLAMA_URL` through `@DynamicPropertySource`, because Boot has no `@ServiceConnection` for a custom Ollama client.

### L3: Ollama behaviour that the spine depends on (low; partly verified)
- **Verified in v0.40.6 `docs/api.md`.**
  - `/api/chat` accepts `format` as "`json` or a JSON schema".
  - `options.seed` and `keep_alive` are supported.
  - `/api/embed` exists.
  - `/api/tags` returns `digest`.
  - The chat response has `prompt_eval_count`.
- **Caveats.**
  - `/api/embed` `truncate` "defaults to `true`", so KB ingest could silently cut chunks. Send `truncate:false` so that an oversize chunk fails instead.
  - Ollama does not report chat-prompt truncation. AD-5's `truncated` flag must therefore come from the app's own prompt budget, cross-checked against `prompt_eval_count`.

### L4: Image tags are not pinned (low)
- **Evidence.** Docker Hub has `eclipse-temurin:25-jre-noble` / `25-jdk-noble`, `maven:3.10.0-eclipse-temurin-25` and `node:24.21-*`. Angular 22.2.3 declares `engines.node: ^22.22.3 || ^24.15.0 || >=26.0.0`, so "Node 24 LTS" has to be at least 24.15.
- **Fix.** Pin the build and runtime base images in the Dockerfile (for example `node:24.21-bookworm-slim`, `maven:3.10.0-eclipse-temurin-25`, `eclipse-temurin:25-jre-noble`) and state "Node >=24.15" in the stack.

## Confirmed fits (no action)

| Decision | Evidence |
|---|---|
| Spring Modulith 2.1.1 with Boot 4.1.1 | `spring-modulith-core-2.1.1.pom` compiles against `spring-boot-autoconfigure 4.1.1` and Spring Framework 7.0.9, the same as the Boot 4.1.1 BOM. Modulith 2.1 GA was released 2026-06-11. 2.2.0-M2 is a milestone, so staying on 2.1.x is correct. |
| springdoc 3.1.x with Boot 4.1 | The `springdoc-openapi-3.1.1` parent is `spring-boot-starter-parent 4.1.0`. Swagger UI is 5.32.14. Output defaults to OpenAPI 3.1. |
| JSONB via Hibernate without extra libraries | The hibernate-core 7.4.5 jar contains `Jackson3JsonFormatMapper`, and `JacksonIntegration` auto-detects `tools.jackson` (`JACKSON3_JSON_AVAILABLE`). Boot 4.1.1 manages Jackson 3.1.5 and Hibernate 7.4.5. Caveat: Hibernate builds its own `JsonMapper`, so the Spring Jackson customisations (money as a string) do not apply to the audit payload unless `hibernate.type.json_format_mapper` is set. Decide this in the audit story. |
| Testcontainers Ollama module | `testcontainers-ollama:2.0.5` exists. |
| ngx-echarts 22 / echarts 6.1 / Angular 22 | npm peer dependencies for ngx-echarts 22.0.0 are `@angular/core >=22.0.0` and `echarts >=5.0.0`. Released 2026-06-11. |
| ng-openapi-gen 1.1.0 with Angular 22 / TS 6 | Peer `@angular/core >=16`. Released 2026-09-23. OpenAPI 3.1 is supported since 1.0. No TypeScript peer restriction. The defaults are covered in M1. |
| Angular 22 XSRF with Spring Security 7.1 | Cookie and header names match. The details are in M4. |
| Java 25 | Supported by ArchUnit since 1.4.1 ("class file major version 69"). Temurin 25 images exist. `L64X128MixRandom` is present in JDK 25 (M3 covers the seeding API). |
| PostgreSQL JDBC | Boot-managed 42.7.13, which matches "42.7.x". The memlog's 42.7.14 is the latest version on Central, not the managed one. Harmless. |

## Not verifiable here / residual risk
- Whether Boot 4.1 itself wires Spring's Jackson 3 `JsonMapper` into Hibernate's FormatMapper. No release-note statement was found. Settle it with a one-entity test.
- Whether the Ollama `/api/chat` `options` block honours `num_ctx`. The docs list it only for `/api/generate` and say chat accepts Modelfile parameters. Confirm with the AD-18 startup smoke test.
