---
title: 'Story 1.4: Simulated clock, request context and versioned agent configuration'
type: 'feature'
created: '2026-10-21'
status: 'done'
baseline_commit: '0543f411228c9f98f448ad1120884e0ba0d0f113'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Results must never depend on the real date or on hidden settings (AD-8, AD-14, AD-22, AD-25), but there is no simulated clock, no agent configuration and no fingerprint yet.

**Approach:** Give every persona a stored `simulatedDate` and `fixtureVersion`; load `config/agent/<APP_AGENT_CONFIG>.yaml`, `config/autonomy.yaml` and `config/profile-minimums.yaml` as validated `@ConfigurationProperties` (each with a `derivedFrom` block); compute one `ConfigFingerprint` plus per-file hashes at startup; build an immutable `RequestContext` per use case with `asOf` = the persona's simulated date; show "Today: <date>" in the header only under the `research` profile.

## Boundaries & Constraints

**Always:** configuration values are the PRD starting values (§8.1 initial Reference Configuration; FR-35 thresholds 5 % of liquid assets, Data Confidence 95 % / 5 days) and the experiment harness defaults (`experiments/finlab`: decoding, seeds, profile minimums), each marked `derivedFrom: PRD starting value` until Colab results exist; `RequestContext` holds plain records only; domain stays pure; LF-normalised hashing.

**Never:** no LLM client or model lock check (Story 1.6); no Rule Parameters or KB (Epic 4; `kbSnapshot` is the fixed value `none`); no persona fixtures (Story 2.1); no feature flags beyond these files; no `now()` in domain or application code.

**Decisions (2026-10-21):** spec size (~2,100 tokens) kept as one spec.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Startup | valid `reference.yaml`, `autonomy.yaml`, `profile-minimums.yaml` | config bound; fingerprint + per-file hashes available | N/A |
| Missing agent file | `APP_AGENT_CONFIG=nope` | startup stops naming `config/agent/nope.yaml` | fail fast |
| Invalid field | e.g. `compliance: B`, blank `llm.phrasing`, missing `derivedFrom` | startup stops naming the property | Boot bind/validation failure report |
| One byte changed | any fingerprinted file | different fingerprint and that file's hash | N/A |
| Line endings | same file with CRLF vs LF | identical fingerprint | N/A |
| Use case | persona with `simulatedDate` 2026-10-20 | `RequestContext.asOf` = 2026-10-20, `personaId`, config id, fingerprint, thresholds, seeds; `runId` null | N/A |
| Date chip | `research` profile on / off | `GET /api/persona` carries `today` / omits it; header shows / hides "Today: 20 Oct 2026" | N/A |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/de/financetool/shared/` -- open module; `AppHome` (reads `config/`, `data/`, `models/`), `PersonaScope`, `domain.PersonaId`; new `shared.config` package goes here.
- `backend/src/main/resources/application.yaml` -- `app.home: ${APP_HOME:..}`; add `spring.config.import`.
- `backend/src/main/resources/db/migration/V2__personas.sql` -- `persona_state(simulated_date NULL, fixture_version NULL)`; next is `V3`.
- `backend/src/main/java/de/financetool/personas/` -- `PersonaDirectory`/`PersonaSummary` API, `web/PersonaController` (`GET /api/persona`), `adapter` repositories taking `PersonaScope`.
- `backend/src/test/.../SecurityWebTest.java`, `OpenApiSnapshotTest.java` -- DB-less tests mock `PersonaRepository`; extend mocks for persona state.
- `experiments/finlab/pipeline.py` (`ExperimentConfig`), `llm.py` (`Decoding`), `prompts.py` (`ROLE_PROMPTS["advisor"]`), `data/rules/category_rules.yaml` (`profile_minimums`), `profile.py` (`STRICT_SUITABILITY_FIELDS`) -- source of starting values; copy, do not import.
- `frontend/src/app/shell/app-header.component.ts`, `core/session.service.ts` -- header gets the chip; client regenerated from snapshot.

## Tasks & Acceptance

**Execution:**
- [x] `config/agent/reference.yaml`, `config/prompts/role-advisor.txt`, `config/autonomy.yaml`, `config/profile-minimums.yaml` -- starting values with `derivedFrom {experiment, resultFile, date, note}` -- AC 2, 3.
- [x] `shared/config/` -- `AgentConfiguration` (`id, derivedFrom, llm{phrasing,nlu,judge,embedding}, retrieval, rolePrompt, compliance, dataExposure, decoding, seeds`), `ComplianceLevel` (`A`, `A_B`, `A_B_C` ↔ `A`, `A+B`, `A+B+C`, binding converter), `DataExposureLevel`, `AutonomyThresholds`, `ProfileMinimums`, `DerivedFrom`; all `@Validated` records; role prompt file must exist -- AC 2, 3.
- [x] `application.yaml` -- `spring.config.import` of the three files from `${APP_HOME:..}` (agent file selected by `${APP_AGENT_CONFIG:reference}`, not optional) -- AC 2.
- [x] `shared/config/ConfigFingerprint.java` + `ConfigFingerprints.java` -- SHA-256 over the AD-14 file set in sorted relative-path order; per-file hashes; computed once at startup -- AC 4.
- [x] `shared/RequestContext.java`, `shared/Seeds.java` -- immutable record `(agentConfig, configFingerprint, thresholds, kbSnapshot, seeds, asOf, personaId, runId)` -- AC 1.
- [x] `db/migration/V3__persona_state_clock.sql` -- set `simulated_date = 2026-10-20`, `fixture_version = 'pre-fixtures'` for all personas, then `NOT NULL` -- AC 1.
- [x] `personas/` -- `PersonaStateEntity` + repository (scoped), `RequestContextFactory` API (`forPersona(PersonaScope, runId)`), `application/ShowPersonaUseCase` (first `*UseCase`, `@Transactional(readOnly)`, builds the context), `PersonaController` returns `today` only under `research` -- AC 1, 5.
- [ ] Tests: `ConfigFingerprintsTest` (byte change, CRLF, order), `AgentConfigurationBindingTest` (valid, missing file, invalid values), `RequestContextFactoryTest`, ArchUnit "only `RequestContextFactory` holds config beans as fields" with negative fixture; update DB-less tests; Testcontainers test for V3 -- matrix rows. **V3 test written; not yet run (no Docker).**
- [x] `frontend/` -- regenerate client; header chip "Today: 20 Oct 2026" (`d MMM y`, `en`) when `today` is present; spec for present/absent -- AC 5.

**Acceptance Criteria:**
- Given any `*UseCase`, when it runs, then it builds one `RequestContext` with `asOf` from `persona_state` and passes it explicitly; no service injects configuration or reads time itself.
- Given the three config files, when the app starts, then they are bound and validated, each carries `derivedFrom`, and an invalid or missing field stops startup with a clear message.
- Given the AD-14 file set, when the app starts, then one fingerprint and per-file hashes exist, and changing any byte changes the fingerprint.

## Implementation Notes

- Timing: start 2026-10-21T15:00:00+02:00, end 2026-10-21T15:12+02:00.
- The three files are loaded with `spring.config.import` (`file:`, not optional), so a missing agent file fails before any bean exists; property prefixes `agent`, `autonomy`, `profile-minimums`. `ComplianceLevel` binds from `A`/`A+B`/`A+B+C` via a `@ConfigurationPropertiesBinding` converter. Map keys with underscores (`savings_plan`) bind unchanged (tested).
- `config/prompts/role-<rolePrompt>.txt` must exist (checked when the fingerprint bean is created). Advisor prompt text copied from `experiments/finlab/prompts.py`.
- Seeds are `AgentConfiguration.Seeds(core, llm)`; no separate `shared/Seeds.java` (deviation from the task list; same content).
- First `*UseCase`: `personas.application.ShowPersonaUseCase` (`@Transactional(readOnly = true)`), behind `GET /api/persona`. `RequestContextService` (implements `personas.RequestContextFactory`) is the only holder of the config beans; ArchUnit rule `CONFIG_ONLY_THROUGH_REQUEST_CONTEXT` with negative fixture `archfixture.rules.application.ConfigReadingService`.
- `GET /api/persona` returns `today` only under the `research` profile; the field is omitted (`@JsonInclude(NON_NULL)`), not `null`.
- Found by tests and fixed: the header chip formatted a date-only string in UTC and showed the previous day in CEST; now local time (tested in two time zones).
- Verified: backend `./mvnw verify` 60 tests, 45 run green (fingerprint, binding incl. missing file / invalid values, context factory, ArchUnit, controller); frontend 13 tests and build green.
- **Not verified (no Docker):** `PersonaStateMigrationTest` (V3 against PostgreSQL, use case on the stored date) and the 13 Story 1.3 Testcontainers tests were skipped.
- Verified with Docker (2026-10-21): `PersonaStateMigrationTest` 2/2 green against pgvector 0.8.2-pg17 with Flyway V1–V3; full `./mvnw -B verify` 69 tests, 0 skipped. The shared-container fix in `PostgresIntegrationTest` is described in the Story 1.3 spec.

## Spec Change Log

## Review Triage Log

- Review layers (blind-hunter, edge-case-hunter, verification-gap) skipped on the user's instruction (no optional review passes; story not safety-critical). No findings recorded.

## Design Notes

- Fingerprint digest input per file: relative path (`/`-separated), `\0`, LF-normalised bytes, `\0`; files sorted by path. Including the path makes a rename visible. Files of the set that do not exist yet (`models/lock.json`, `config/documents.yaml`, `config/letters/`, `data/rules/`) are simply absent from the hash list.
- The initial simulated date 2026-10-20 is a placeholder (`fixture_version = 'pre-fixtures'`) until Story 2.1 seeds per-persona fixture start dates.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify` -- expected: BUILD SUCCESS (Testcontainers tests skipped without Docker).
- `cd frontend && npm run build && npx ng test --watch=false` -- expected: success.
