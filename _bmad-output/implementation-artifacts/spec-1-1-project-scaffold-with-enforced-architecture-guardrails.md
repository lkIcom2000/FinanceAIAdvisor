---
title: 'Story 1.1: Project scaffold with enforced architecture guardrails'
type: 'feature'
created: '2026-10-21'
status: 'done'
baseline_commit: '13c7bf9f960e6d95a1e811a3fae95c9cc48d4b96'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no code yet. Every later story needs a backend and frontend skeleton in which module boundaries, domain purity and the generated API contract are checked automatically, so that the hybrid architecture cannot erode silently.

**Approach:** Scaffold `backend/` (Maven wrapper, Java 25, Spring Boot 4.1.1, Spring Modulith 2.1.1) with the 14 AD-12 modules and their allowed dependencies declared in `package-info.java`, ArchUnit purity/clock rules, Springdoc with explicit `Money`/`ProblemCode`/`ProblemDetail` schemas, and `frontend/` (Angular 22.2, standalone, Angular Material) whose build regenerates the `ng-openapi-gen` client from a committed OpenAPI snapshot and fails on drift.

## Boundaries & Constraints

**Always:** versions exactly as in the spine stack table; packages `de.financetool.<module>.<layer>`; `ddl-auto` off, Flyway only; JSON logs to stdout; LF for `config/`, `data/`, `models/`; code and docs in English.

**Never:** no tables or Flyway migrations (first migration is Story 1.2); no Spring Security, login, theme or shell UI (Story 1.3); no Dockerfile/compose (Story 1.2); no Testcontainers or experiment tooling; no hand-written DTOs in the frontend; no H2 or other substitute database.

**Decisions (2026-10-21):**
- Java 25: Temurin 25.0.4.1 portable ZIP in `C:\Users\commc\.jdks\temurin-25`, used via `JAVA_HOME` for builds; no system change.
- Repository content: everything (code, `_bmad-output/`, `experiments/`, `_bmad/`, `.claude/`), ignoring generated `_bmad/render/`.
- Branching: commit directly to `main` of `https://github.com/lkIcom2000/FinanceAIAdvisor.git`, on top of the existing remote history.
- Spec size (~1,800 tokens) kept as one spec.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Clean skeleton | `./mvnw verify` | Modulith verify, ArchUnit, OpenAPI snapshot tests pass | N/A |
| Forbidden module dependency | a module uses a type of a module outside its AD-12 list | `ApplicationModules.verify()` fails | test proves the declared graph equals AD-12 |
| Impure domain | `..domain..` class imports Spring/Jackson/JPA | ArchUnit rule fails | negative test on a test fixture |
| Wall clock | `..domain..`/`..application..` calls `LocalDate.now()`, `Instant.now()`, `Clock.systemDefaultZone()` | ArchUnit rule fails | negative test on a test fixture |
| API drift | backend OpenAPI differs from committed snapshot | backend test fails (update via `-Dopenapi.update=true`) | message names the update flag |
| Client drift | regenerated client differs from committed `src/app/api` | `npm run build` fails | message names `npm run api:generate` |
| Error response | unhandled exception in a controller | RFC 9457 `ProblemDetail` with closed `code` | `INTERNAL_ERROR`, no stack trace |

</frozen-after-approval>

## Code Map

- Repository is greenfield: no code. Remote `lkIcom2000/FinanceAIAdvisor` holds only `README.md` and an Angular-style `.gitignore` (commit `919ffe61c3f2ad5093fe49cf0479d880c69e5eea`); keep both, extend `.gitignore`.
- `_bmad-output/implementation-artifacts/epic-1-context.md` -- module graph, conventions, stack (source of truth for allowed dependencies).
- Toolchain: Node 24.20 / npm 11.19 present; no Maven (bootstrap wrapper with a downloaded Maven 3.9.11 in the scratchpad); no Docker (not needed here).

## Tasks & Acceptance

**Execution:**
- [x] `.gitattributes`, `.gitignore` -- LF for `config/**`, `data/**`, `models/**`, `mvnw`; CRLF for `*.cmd`; ignore `target/`, `node_modules/`, `.angular/`, `_bmad/render/` -- AC 4.
- [x] `config/`, `data/`, `models/` (`.gitkeep`) -- directories exist for later stories.
- [x] `backend/pom.xml`, `backend/mvnw*`, `backend/.mvn/` -- Boot 4.1.1 parent, Java 25, starters webmvc, validation, data-jpa, actuator, flyway + `flyway-database-postgresql`, postgresql, modulith BOM 2.1.1 (core + test), springdoc 3.1.1, `archunit-junit6` 1.5.1 -- AC 1.
- [x] `backend/src/main/java/de/financetool/FinanceToolApplication.java` + `<module>/package-info.java` ×14 -- `@ApplicationModule(allowedDependencies = …)` per AD-12; `shared` is `Type.OPEN` -- AC 1, 2.
- [x] `shared/domain/Money.java`, `shared/ProblemCode.java`, `shared/web/{ProblemDetailsAdvice, OpenApiSchemas}.java` -- pure `Money` record (scale 2 HALF_UP), closed enum, `@RestControllerAdvice` emitting `ProblemDetail` with `code`, `OpenApiCustomizer` registering `Money`, `ProblemCode`, `ProblemDetail` -- AC 3.
- [x] `backend/src/main/resources/application.yaml`, `db/migration/.gitkeep` -- `ddl-auto: none`, `spring.mvc.problemdetails.enabled`, `logging.structured.format.console: ecs` -- AC 4.
- [x] `backend/src/test/java/de/financetool/architecture/{ModulithTest, ModuleGraphTest, DomainPurityTest}.java` + fixtures under `de.financetool.archfixture` -- verify, declared-graph = AD-12 table, ArchUnit rules with negative fixtures -- AC 2.
- [x] `backend/src/test/java/de/financetool/shared/{OpenApiSnapshotTest, ProblemDetailsAdviceTest, MoneyTest}.java` -- web context without datasource; compare `/v3/api-docs` with `frontend/openapi/openapi.json` -- AC 3.
- [x] `frontend/` -- `ng new` (standalone, SCSS, routing, no SSR) + `ng add @angular/material`; `ng-openapi-gen.json` (`services: true, promises: false`), `scripts/check-api-client.mjs`, `prebuild` + `api:generate` scripts, committed `src/app/api` -- AC 1, 3.

**Acceptance Criteria:**
- Given the repo, when the scaffold exists, then `backend/` builds with the committed wrapper on Java 25 and `frontend/` is an Angular 22.2 standalone workspace with Angular Material.
- Given the 14 modules, when `./mvnw verify` runs, then Modulith verification and ArchUnit pass, and each negative fixture is reported as a violation.
- Given the OpenAPI snapshot, when `npm run build` runs, then the client is regenerated and the build fails on any difference to the committed client.
- Given the conventions, when inspecting the scaffold, then LF attributes, Flyway starter + `flyway-database-postgresql`, `ddl-auto: none` and JSON console logging are present.

## Implementation Notes

- Timing: start 2026-10-21T14:03:59+02:00, end 2026-10-21T14:20:17+02:00.
- Git: local repo initialised on top of remote `main` (`919ffe61c3f2ad5093fe49cf0479d880c69e5eea`); existing planning files committed as baseline `13c7bf9f960e6d95a1e811a3fae95c9cc48d4b96`; `.gitignore` extended (Maven, nested Node, Python, `_bmad/render/`).
- Toolchain: Temurin 25.0.4.1 in `C:\Users\commc\.jdks\temurin-25` (use `JAVA_HOME`); Maven wrapper 3.3.4 (`only-script`, Maven 3.9.11) bootstrapped with a scratchpad Maven.
- Every module also lists `shared` in `allowedDependencies`; `shared` is `Type.OPEN` with `{}`. `ModulithTest` strips `shared` before comparing with the AD-12 map.
- Architecture tests live in the root test package `de.financetool` (not a module); negative fixtures live outside it in `archfixture.rules` and `archfixture.modulith` (deviation from the planned `de.financetool.archfixture`, so they never become production modules). Spring Modulith skips test classes by default, so the fixture verification passes an include-all `ImportOption`.
- The wall-clock rule also forbids `LocalDateTime/ZonedDateTime/...now()`, `Clock.systemUTC()` and `System.currentTimeMillis()`.
- `OpenApiSnapshotTest` runs under test profile `webtest`, which excludes the DataSource/JPA/Flyway auto-configurations (no database before Story 1.2). The app itself does not start without PostgreSQL yet.
- `Money` has no Jackson wire mapping yet; the OpenAPI `Money` schema fixes the contract for the first story that returns money.
- Frontend: `npm run build` runs `prebuild` → `api:check` (`scripts/check-api-client.mjs`), regenerating into a temp dir and comparing file by file (CRLF-normalised). Angular CLI 22.2.3 defaults: zoneless, Vitest.
- Verified: `./mvnw verify` 14 tests green; snapshot tampering fails `OpenApiSnapshotTest`; tampering a generated file fails `npm run build`; `ng test` 2 passed.

## Spec Change Log

## Review Triage Log

- Review layers (blind-hunter, edge-case-hunter, verification-gap) skipped on the user's instruction (no optional review passes; story not safety-critical). No findings recorded.

## Design Notes

- ArchUnit rules live in one class with `static ArchRule` fields; negative tests import only `de.financetool.archfixture..` and assert `rule.evaluate(classes).hasViolation()`. Production import excludes `archfixture`.
- `ModuleGraphTest` reads each module's `allowedDependencies` via `ApplicationModules.of(...)` and compares with a hard-coded AD-12 map, so a loosened `package-info.java` fails too.

## Verification

**Commands:**
- `cd backend && ./mvnw -q verify` -- expected: BUILD SUCCESS, all architecture tests green.
- `cd frontend && npm run build` -- expected: success; altering one generated file makes it fail.
- `cd frontend && npm test -- --watch=false` -- expected: default app spec passes.
