---
title: 'Story 1.7: Persona reset for reproducible sessions'
type: 'feature'
created: '2026-10-21'
status: 'done'
baseline_commit: '2cd2c75d0f95fcdf09ed53e208909e1af47ef250'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Scenarios and study sessions must start from identical data (FR-3, NFR2), but there is no way to reset a persona or set its simulated date.

**Approach:** A `shared.PersonaResettable` contract (`module()`, `reset(PersonaId)`) implemented by every module that owns persona state; the `research` module's `ResetPersonaUseCase` runs all registered owners in one transaction in a fixed reverse-dependency order, and `SetSimulatedDateUseCase` updates `persona_state.simulated_date`. Both are exposed as `POST /api/research/personas/{personaId}/reset` and `…/simulated-date` under the `research` profile for `RESEARCHER`.

## Boundaries & Constraints

**Always:** one transaction per call (`*UseCase` `REQUIRED`, owners `MANDATORY`); audit tables never touched; a `PersonaResettable` whose module is not in `RESET_ORDER` stops startup; `RESET_ORDER` is checked against the AD-12 module graph (every module before the modules it depends on); persona id from the path only for these researcher endpoints; unknown persona 404, malformed id 400; responses 204.

**Never:** no persona fixtures (Story 2.1 owns them; until then the personas owner resets to the V3 placeholder `2026-10-20` / `pre-fixtures`); no reset of `knowledge` (not persona-owned), `audit` or `llm`; no UI; no `PersonaScope` built from the path.

**Decisions (2026-10-21):** as agreed for 1.5–1.7, the checkpoint is approved and review is deferred to the joint review after 1.7.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Reset | modified `simulated_date`/`fixture_version` | 204; state equals the fixture start (`2026-10-20`, `pre-fixtures`) | N/A |
| Reset twice | modify, reset, modify, reset | identical `persona_state` row both times; audit row count unchanged | N/A |
| Set date | `{"date":"2026-11-06"}` | 204; next `RequestContext.asOf` = 2026-11-06 | missing/invalid date 400 |
| Unknown persona | `p-nobody` | 404 `NOT_FOUND` | N/A |
| Bad id | `nobody` | 400 | N/A |
| Roles | persona, advisor | 403 | N/A |
| No profile | any `/api/research/**` | 404 | N/A |
| Unregistered owner | resettable with module `x` | startup fails naming `x` | fail fast |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/de/financetool/shared/` -- open module; put `PersonaResettable` here (owners cannot depend on `research`).
- `backend/src/main/java/de/financetool/personas/adapter/PersonaStateRepository.java` -- Spring Data `Repository`; add `@Modifying` updates and `existsByPersonaId` (the raw-id rule only forbids `findByPersonaId`/`findById` reads).
- `backend/src/main/java/de/financetool/personas/` -- new API `PersonaClock` (`exists`, `setSimulatedDate`), owner `PersonaStateResetter`.
- `backend/src/main/java/de/financetool/research/` -- `ExportAuditUseCase` + `web/AuditExportController` (Story 1.5) show the profile-gated pattern.
- `backend/src/test/java/de/financetool/ModulithTest.java` -- `AD12_GRAPH`; check `RESET_ORDER` against it there.
- `backend/src/test/java/de/financetool/audit/AuditTrailIntegrationTest.java` -- nested `@ActiveProfiles("research")` integration test pattern; `login(mvc, "researcher")`.

## Tasks & Acceptance

**Execution:**
- [x] `shared/PersonaResettable.java` -- contract -- AC 1.
- [x] `personas/` -- `PersonaClock` API + service (`MANDATORY`), `PersonaStateResetter` (module `personas`, start values as constants), repository updates -- AC 1, 3.
- [x] `research/application/ResetPersonaUseCase.java`, `SetSimulatedDateUseCase.java` -- `RESET_ORDER` = `monitoring, advisory, actions, escalation, profile, core, bank, documents, personas`; sort owners, fail on unknown module -- AC 1, 2, 3.
- [x] `research/web/PersonaResearchController.java` -- the two endpoints, `@Profile("research")` -- AC 1, 3, 4.
- [x] Tests -- `PersonaResetIntegrationTest` (matrix rows 1–7), `ResetPersonaUseCaseTest` (order, unknown module), `ModulithTest` order check -- matrix.

**Acceptance Criteria:**
- Given a researcher under the `research` profile, when reset is called, then each registered owner runs in reverse dependency order in one transaction, `simulatedDate` returns to the fixture start and audit tables are untouched.
- Given modified state, when reset twice, then the resulting state (incl. `fixtureVersion`) is identical.
- Given a new simulated date, then the next `RequestContext.asOf` for that persona equals it.
- Given no `research` profile, then every `/api/research/**` path returns 404 and the login page shows no research hint.

## Implementation Notes

- Timing: start 2026-10-21T19:08+02:00, end 2026-10-21T19:30+02:00. Implemented directly in this session (fast path).
- `personas.PersonaClock` (`exists`, `setSimulatedDate`) takes a `PersonaId`: the research path supplies the persona, and `PersonaScope` stays principal-only. Repository writes are JPQL `@Modifying` updates plus `existsByPersonaId`; the raw-id read rule is untouched.
- Found by tests and fixed: class-level `@Transactional(MANDATORY)` on `PersonaStateResetter` made `module()` need a transaction, so `ResetPersonaUseCase`'s constructor check failed; `MANDATORY` now sits on `reset` only.
- The unregistered-owner check runs when `ResetPersonaUseCase` is created, i.e. under the `research` profile; without it, `ResetPersonaUseCaseTest` and `ModulithTest.theResetOrderRespectsTheModuleGraph` guard the order on every build.
- Unknown persona → `ResponseStatusException(404)` → `ProblemDetail` `NOT_FOUND`; malformed id → 400 `REQUEST_REJECTED`/`VALIDATION_FAILED` via the existing advice.
- Frontend: login spec asserts the page mentions neither research nor Swagger.
- Verified: backend `./mvnw -B verify -Dfine.it.required=true` BUILD SUCCESS, 123 tests, 0 failures, 0 skipped (`PersonaResetIntegrationTest` 8, `ResetPersonaUseCaseTest` 3); frontend 18 Vitest tests green. Not run against the compose stack (it runs without the `research` profile; the integration tests use the same app and PostgreSQL image).

## Spec Change Log

## Review Triage Log

- Review layers not run: joint review of Stories 1.5–1.7 with the user follows.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify -Dfine.it.required=true` -- expected: BUILD SUCCESS, 0 skipped.
