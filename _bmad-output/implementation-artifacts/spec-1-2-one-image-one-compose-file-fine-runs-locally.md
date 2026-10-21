---
title: 'Story 1.2: One image, one compose file — Fine runs locally'
type: 'feature'
created: '2026-10-21'
status: 'done'
baseline_commit: 'f71ba7349cd1cd34d427eaf8cf0b032179000644'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The scaffold from Story 1.1 builds and tests, but nothing runs: there is no image, no database and no model server, so no later story can be tried end to end.

**Approach:** Add one multi-stage `deploy/Dockerfile` (Angular build → Spring Boot jar with the SPA as static resources → JRE runtime with `APP_HOME=/app`) and one `deploy/docker-compose.yml` with `app`, `db` (`pgvector/pgvector:0.8.2-pg17`) and `ollama` (`ollama/ollama:0.40.6`, `OLLAMA_KEEP_ALIVE=-1`); mount `config/`, `data/`, `models/` read-only; apply a Flyway baseline migration that enables `vector`; `/actuator/health` reports UP.

## Boundaries & Constraints

**Always:** environments differ only in `SPRING_DATASOURCE_*`, `APP_OLLAMA_URL`, `APP_AGENT_CONFIG`, `SPRING_PROFILES_ACTIVE` and secrets; secrets only from environment variables (`deploy/.env`, git-ignored, with a committed `deploy/.env.example`); image tags pinned exactly; the app reads `config/`, `data/`, `models/` only below `APP_HOME` and fails fast at startup if one is missing.

**Never:** no tables (baseline migration only enables the extension); no separate owner/app DB roles (Story 1.5); no model pulls, `models/lock.json` or LLM client (Story 1.6); no agent config file (Story 1.4); no Azure/Caddy; no second compose file or override.

**Decisions (2026-10-21):**
- Docker is not available on the dev PC: implement and verify everything that needs no Docker; the user runs the `docker compose up` / health check later. The story stays `review` in sprint status until that check passes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Image build | `docker compose -f deploy/docker-compose.yml build` | one image; `/app/app.jar` serves the Angular `index.html` at `/` | frontend `api:check` failure fails the build |
| Startup | `docker compose up` with `deploy/.env` | `db` healthy, `ollama` started, `app` migrates V1 and `/actuator/health` = UP | `app` waits for `db` health |
| Missing APP_HOME dir | `APP_HOME` without `models/` | startup stops with a message naming the missing path | fail fast, no partial start |
| Missing secret | `FINE_DB_PASSWORD` unset | `docker compose` refuses to start and names the variable | `${VAR:?}` interpolation |
| Env parity | compose `app.environment` | only the allowed variable names; no literal secret | unit test fails otherwise |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/application.yaml` -- add `app.home`, actuator exposure; datasource placeholders already exist.
- `backend/src/main/java/de/financetool/shared/` -- open module; new `AppHome` goes in its base package (cross-module API).
- `backend/src/main/resources/db/migration/.gitkeep` -- replace with `V1__baseline.sql`.
- `backend/src/test/resources/application-webtest.yaml` -- DB-less test profile from 1.1; `OpenApiSnapshotTest` must keep passing (it will need `app.home` resolvable).
- `frontend/dist/frontend/browser/` -- Angular build output copied into the jar's `static/`.
- `.gitignore` -- root-anchored `/.env` only; add `deploy/.env`.
- Toolchain: JDK 25 at `C:\Users\commc\.jdks\temurin-25` (`JAVA_HOME`); no Docker CLI.

## Tasks & Acceptance

**Execution:**
- [x] `.dockerignore` -- exclude `node_modules`, `target`, `dist`, `.angular`, `.git`, `_bmad*`, `.claude`, `experiments` -- small, reproducible build context.
- [x] `deploy/Dockerfile` -- stages `node:24.15-bookworm` (npm ci + `npm run build`), `eclipse-temurin:25-jdk` (copy SPA into `backend/src/main/resources/static`, `./mvnw -B package -DskipTests`), `eclipse-temurin:25-jre` (non-root user, `APP_HOME=/app`, `/app/app.jar`) -- AC 1.
- [x] `deploy/docker-compose.yml`, `deploy/.env.example` -- three services, pinned images, `db` healthcheck + volume, `ollama` volume, read-only mounts, `app` env limited to the allowed names, port 8080 -- AC 2, 3.
- [x] `backend/src/main/java/de/financetool/shared/AppHome.java` -- `@ConfigurationProperties("app")` record with `home`; resolves `config()`, `data()`, `models()`; validated at startup (directories exist) -- AC 1.
- [x] `backend/src/main/resources/application.yaml` -- `app.home: ${APP_HOME:..}`, actuator `health` only, `show-details: never` -- AC 2.
- [x] `backend/src/main/resources/db/migration/V1__baseline.sql` -- `CREATE EXTENSION IF NOT EXISTS vector;` -- AC 2.
- [x] `backend/src/test/java/de/financetool/shared/AppHomeTest.java` -- missing directory fails with the path in the message -- matrix row 3.
- [x] `backend/src/test/java/de/financetool/DeploymentConfigTest.java` -- parse compose YAML: pinned images, `OLLAMA_KEEP_ALIVE=-1`, `:ro` mounts, allowed env names, secrets only as `${…:?}` -- matrix rows 4, 5.
- [x] `README.md` -- "Run locally" section.

**Acceptance Criteria:**
- Given `deploy/Dockerfile`, when the image is built, then a multi-stage build yields one image with the Angular build inside Spring Boot's static resources, reading `config/`, `data/`, `models/` from `/app`.
- Given the compose file, when `docker compose up` runs, then `app`, `db`, `ollama` start, the three directories are mounted read-only, Flyway applies V1 enabling `vector`, and `/actuator/health` is UP.
- Given the compose file, when environments are compared, then they differ only in the allowed variables and secrets come only from the environment.

## Implementation Notes

- Timing: start 2026-10-21T14:23:34+02:00, end 2026-10-21T14:32+02:00.
- Base images pinned exactly: `node:24.20.0-bookworm-slim`, `eclipse-temurin:25.0.4.1_1-jdk-noble` / `-jre-noble` (tags verified on Docker Hub).
- The image also contains copies of `config/`, `data/`, `models/`; compose mounts the repository copies read-only over them.
- `AppHome` binds `app.home` as a string (default `..`, i.e. repository root when run from `backend/`; `APP_HOME` env maps to it). `AppHomeCheck` (static `BeanFactoryPostProcessor`) validates it before any bean exists: verified by starting the jar with an empty `APP_HOME` — it fails naming the missing directory, with no database connection attempt. Without this, Flyway ran first.
- Secrets: compose requires `FINE_DB_PASSWORD` via `${…:?}`; `deploy/.env` is git-ignored, `deploy/.env.example` committed. Single DB role for now (owner/app split in Story 1.5).
- Verified without Docker: `./mvnw verify` 22 tests green; `npm run build` green; a jar packaged like Dockerfile stage 2 contains `static/index.html` and `db/migration/V1__baseline.sql`.
- **Not verified (no Docker on the dev PC):** image build, `docker compose up`, Flyway V1 against pgvector, `/actuator/health` UP (matrix rows 1–2). The user runs the README "Run locally" commands; the story stays `review` until then.

## Spec Change Log

## Review Triage Log

- Review layers (blind-hunter, edge-case-hunter, verification-gap) skipped on the user's instruction (no optional review passes; story not safety-critical). No findings recorded.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify` -- expected: BUILD SUCCESS incl. `AppHomeTest`, `DeploymentConfigTest`.
- `cd frontend && npm run build` -- expected: success.
- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build` then `curl -s localhost:8080/actuator/health` -- expected: `{"status":"UP"}`; `curl -s localhost:8080/` returns the Angular `index.html` (only if Docker is available).
