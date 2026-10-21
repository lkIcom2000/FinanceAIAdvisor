---
title: 'Story 1.3: Log in as a persona, an advisor or a researcher'
type: 'feature'
created: '2026-10-21'
status: 'done'
baseline_commit: '8953674a332384db94ad62e145b6c3311b653933'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Fine has no users: nobody can log in, and nothing stops one persona from reading another's data. Persona isolation is safety-critical for the study (FR-1, FR-2, AD-11).

**Approach:** The `personas` module gets tables `personas`, `users`, `persona_state` and seeded accounts; Spring Security 7 session login via JSON (`POST /api/auth/login`, SPA CSRF); persona endpoints take the persona only from the principal through a `PersonaScope`; role-based access for `/api/**`, `/api/advisor/**`, `/api/research/**` and Swagger UI. The Angular app gets the Atruvia M3 theme, a login page, the navy header and the two-column Workspace shell with the four tabs.

## Boundaries & Constraints

**Always:** persona id only from the authenticated principal; every persona-scoped repository method takes a `PersonaScope`; login and all errors are JSON (`ProblemDetail`), never redirects; `csrf(c -> c.spa())`; research endpoints and their Swagger group exist only under the `research` profile, and without it `/api/research/**` returns 404 and the login page shows no research hint; UI copy exactly as in the story; `lang="en"`; layout correct at 1366×768 and 1440 px; generated API client only.

**Never:** no Overview content, chat, notifications or persona fixtures (Epics 2–3); no simulated-date chip (Story 1.4); no advisor or research endpoints beyond what security needs (Stories 1.5, 1.7, 5.1); no hand-written DTOs; no external font CDN (Inter is bundled).

**Decisions (2026-10-21):**
- No Docker on the dev PC: integration tests use Testcontainers (`pgvector/pgvector:0.8.2-pg17`) and are skipped without Docker (`disabledWithoutDocker`); the story stays `review` and its code review waits until the user has run them with Docker.
- Seeded passwords are fixed demo passwords (one shared value `fine-demo` for every account), stored as BCrypt hashes in the Flyway seed and documented in the README; synthetic data only.
- Spec size (~2,400 tokens) kept as one spec.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid login | `POST /api/auth/login` `{username, password}` + CSRF | 200 JSON `{role, displayName}`, new session id | N/A |
| Wrong credentials | unknown user or wrong password | 401 `ProblemDetail` `code=BAD_CREDENTIALS`, no `Location` | same response for both cases |
| Missing CSRF | login or logout without `X-XSRF-TOKEN` | 403 `ProblemDetail` `code=FORBIDDEN` | N/A |
| Cross-persona read | `p-lena` session calls every `/api/**` mapping with `p-marco` in path, query and body | no response contains `p-marco` data | test sweeps all registered mappings |
| Wrong role | `PERSONA` → `/api/advisor/**`, `/api/research/**`, Swagger; `ADVISOR`/`RESEARCHER` → persona `/api/**` | 403 `ProblemDetail` | N/A |
| No session | any protected `/api/**` | 401 `ProblemDetail` `code=UNAUTHENTICATED` | SPA redirects to `/login` |
| Session expired | SPA was logged in, call returns 401 | `/login` shows "You were logged out after a while of inactivity. Please log in again." | N/A |
| Landing | `PERSONA` / `ADVISOR` / `RESEARCHER` logs in | `/` workspace / Swagger UI advisor group / Swagger UI research group | non-persona opening `/` is sent to its Swagger UI |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/de/financetool/personas/package-info.java` -- module may use `shared` only; owns `personas`, `users`, `persona_state`.
- `backend/src/main/java/de/financetool/shared/` -- open module; new cross-module types `Role`, `AuthenticatedUser`, `PersonaScope`, `domain.PersonaId`; `ProblemCode` gains `BAD_CREDENTIALS`, `UNAUTHENTICATED`, `FORBIDDEN`; `web.ProblemDetailsAdvice` is reused for JSON errors.
- `backend/src/main/resources/db/migration/V1__baseline.sql` -- next migration is `V2__personas.sql`.
- `backend/src/test/.../OpenApiSnapshotTest.java` + `frontend/openapi/openapi.json` -- regenerate snapshot and client after adding endpoints.
- `frontend/src/app/` -- CLI default app (`app.ts`, `app.routes.ts`, `app.config.ts`); `src/styles.scss` has the Material default theme; `src/app/api/` is generated.
- UX sources: DESIGN.md colour/typography/shape tokens; EXPERIENCE.md Roles & Surfaces, IA (routes, `?tab=home|liquidity|wealth|letters`), State Patterns.

## Tasks & Acceptance

**Execution:**
- [x] `backend/pom.xml` -- `spring-boot-starter-security`, test: `spring-security-test`, `spring-boot-testcontainers`, `testcontainers-postgresql` -- AC 1.
- [x] `db/migration/V2__personas.sql` -- `personas(id slug PK, display_name)`, `users(username PK, password_hash, role, persona_id NULL)`, `persona_state(persona_id PK, simulated_date NULL, fixture_version NULL)`; seed the 7 personas, `advisor-kaya`, `researcher` with `{bcrypt}` hashes of `fine-demo` -- AC 1.
- [x] `personas/` -- JPA entities and repositories (internal), `FineUserDetailsService`, `web/AuthController` (`login`, `logout`, `me`, `csrf`), `web/SecurityConfig` (rules below), `PersonaDirectory` API returning `PersonaSummary` by `PersonaScope` -- AC 1, 2.
- [x] `shared/` -- `Role`, `AuthenticatedUser`, `PersonaScope` (constructed only by `web.PersonaScopeResolver` from the principal), new problem codes, JSON entry point / access-denied handler -- AC 1, 2.
- [x] `shared/web/OpenApiSchemas.java` -- Swagger groups `customer`, `advisor`, `research` (`@Profile("research")`); landing `/swagger-ui/index.html?urls.primaryName=<group>` -- AC 3.
- [x] `application.yaml` -- session timeout `30m`, Swagger UI CSRF on.
- [ ] Backend tests (Testcontainers): `AuthIntegrationTest`, `PersonaIsolationTest` (sweeps all `/api/**` handler mappings), `RoleAccessTest` (with and without `research` profile); ArchUnit rule: only `PersonaScopeResolver` constructs `PersonaScope` -- matrix rows 1–6. **Written; not yet run (no Docker).** ArchUnit rule done and green.
- [x] `README.md` -- demo accounts and password.
- [x] `frontend/` -- bundled Inter; `styles.scss` M3 theme with DESIGN tokens (`--mat-sys-*` overrides, pill buttons 44 px, cards 20 px, flat); `provideHttpClient(withXsrfConfiguration)`; `AuthService` on the generated client; 401 interceptor; guards; `pages/login`, `shell/app-header`, `pages/workspace` (two columns, `mat-tab-group` synced with `?tab=`); Vitest specs for login error, expiry message, tab sync, role redirect -- AC 3, 4.
- [x] `frontend/openapi/openapi.json`, `src/app/api/` -- regenerate.

**Acceptance Criteria:**
- Given the seeded accounts, when each role logs in, then the JSON response carries role and display name, and the session id changes.
- Given `p-lena` is logged in, when every `/api/**` endpoint is called with `p-marco`'s id anywhere in the request, then no `p-marco` data is returned.
- Given a login per role, when the SPA navigates, then `PERSONA` sees the header ("Atruvia" wordmark, divider, "Fine", name, Log out) and the shell with tabs Home, Liquidity, Wealth & Retirement, Letters (tab in `?tab=`); `ADVISOR`/`RESEARCHER` land on their Swagger UI group and cannot open `/`.
- Given the login page, when credentials are wrong or a session expired, then the story's exact messages appear; logout clears the session.

## Implementation Notes

- Timing: start 2026-10-21T14:39:44+02:00, end 2026-10-21T14:57+02:00.
- Security policy lives in `shared.web.SecurityConfig` (app-wide); `personas` supplies the `UserDetailsService` (`AccountDetailsService`). Principal type `shared.web.FinePrincipal` wraps `shared.AuthenticatedUser`, so `shared` never depends on `personas`.
- Security errors go through `ProblemDetailsAdvice` via the `HandlerExceptionResolver` (entry point + access-denied handler); new codes `BAD_CREDENTIALS`, `UNAUTHENTICATED`, `FORBIDDEN`. Unknown user and wrong password give the same 401.
- Without the `research` profile, `/api/research/**` is `permitAll` so that every caller gets 404 (not 401/403), as the story requires.
- `GET /api/auth/csrf` issues the `XSRF-TOKEN` cookie; the SPA calls it before login. `GET /api/persona` is the first persona endpoint (`PersonaScope` parameter).
- `users.display_name` holds names of staff accounts only (CHECK constraint); personas use `personas.display_name`. `persona_state.simulated_date` / `fixture_version` are nullable until Stories 1.4 / 2.1.
- Swagger groups `customer`, `advisor`, `research` (`@Profile("research")`); the Angular client is still generated from the full `/v3/api-docs`. `springdoc.default-produces/consumes-media-type: application/json` was needed: with `*/*` the generated client used Blob responses (found by the frontend tests).
- Found by tests and fixed: `inject()` after `await` in `workspaceGuard`.
- `SecurityWebTest` (no database; mocked repositories, real security chain and BCrypt) covers login, 401/403 JSON, CSRF, roles, logout and principal-only scope on every build. ArchUnit: only `PersonaScopeResolver` constructs `PersonaScope` (negative fixture `archfixture.rules.web.ScopeFromRequest`).
- Frontend: Inter bundled via `@fontsource-variable/inter` 5.3.0 (CDN links from `ng add` removed); dev proxy `proxy.conf.json`; component style budget warning raised to 6 kB.
- Verified: backend `./mvnw verify` 43 tests, 30 run green; frontend 12 Vitest tests green; `npm run build` green.
- **Not verified (no Docker):** `AuthIntegrationTest`, `RoleAccessTest`, `PersonaIsolationTest` (13 tests: Flyway V2 seeds, session id change, full `/api/**` isolation sweep, research profile) were skipped. Story stays `review`; run `./mvnw verify` with Docker, then `bmad-code-review`.

## Spec Change Log

## Review Triage Log

- Review layers (blind-hunter, edge-case-hunter, verification-gap) skipped on the user's instruction (no optional review passes). This story is [code-review]: `bmad-code-review` follows once the integration tests have run.

## Design Notes

- Security rules (first match): permit `POST /api/auth/login`, `GET /api/auth/csrf`, `/actuator/health`, `/error`, SPA static files and `/login`; authenticated: `/api/auth/me`, `/api/auth/logout`; `ADVISOR`: `/api/advisor/**`, `/v3/api-docs/advisor`; `RESEARCHER`: `/api/research/**`, `/v3/api-docs/research`; `ADVISOR|RESEARCHER`: `/swagger-ui/**`; `PERSONA`: other `/api/**`; deny other `/v3/api-docs/**`. Entry point → 401 `ProblemDetail`, access denied → 403 `ProblemDetail`; `formLogin`, `httpBasic` off.
- Display names: Lena, Marco, Jonas, Petra, Tim, "Shortfall test persona", "Test harness persona", Kaya, Researcher.
- `PersonaIsolationTest` iterates `RequestMappingHandlerMapping` entries under `/api/` (except login/logout/csrf), fills path variables, a `personaId` query parameter and a JSON body `{"personaId":"p-marco"}` with `p-marco`, and asserts the body contains neither `p-marco` nor `Marco`. A future endpoint is covered automatically.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify` -- expected: BUILD SUCCESS; integration tests ran (not skipped).
- `cd frontend && npm run build && npx ng test --watch=false` -- expected: success.
