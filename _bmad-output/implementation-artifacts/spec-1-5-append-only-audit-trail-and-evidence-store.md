---
title: 'Story 1.5: Append-only audit trail and evidence store'
type: 'feature'
created: '2026-10-21'
status: 'done'
baseline_commit: '00b341b8194a1fd0937a95d1295e03c9639bb7c0'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The thesis verifies requirements from Audit Records (FR-28, FR-50, AD-13), but there is no audit table, no evidence store and the app connects as the database owner, so it could alter any record.

**Approach:** The `audit` module's migration creates `audit_records` and `evidence_items` with triggers rejecting UPDATE, DELETE and TRUNCATE, and a non-owner app role with only INSERT and SELECT on them; Flyway keeps migrating as the owner. `audit.AuditWriter` and `audit.EvidenceStore.putIfAbsent(...)` write inside the caller's transaction, the payload is serialised only by `AuditPayloadMapper`, and the researcher exports records as JSONL.

## Boundaries & Constraints

**Always:** app datasource = non-owner role `fine_app`; Flyway = owner (`spring.flyway.user`); every app variable stays under `SPRING_DATASOURCE_*` or is a secret (`${…:?}`); writers run with `Propagation.MANDATORY`; payload JSON has a fixed, sorted format (own `JsonMapper`, not Hibernate's or Spring's); insertion order = identity column; export only under the `research` profile and for `RESEARCHER`.

**Never:** no UPDATE/DELETE path in code; no use of audit from reset (audit is never reset); no `CoreResult` type (Epic 2) or response/Action payload types (later epics); no Hibernate JSON mapping for payloads; no `now()` in application code (DB default `now()` for `created_at`).

**Decisions (2026-10-21):** spec above 1,600 tokens kept as one spec (fast path, as in Stories 1.3/1.4). The user asked to build 1.5–1.7 in a row and to review together after 1.7, so the checkpoint is approved and the review step is deferred to that joint review.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Write record | `AuditEntry(kind=RESPONSE, turnId, fixtureVersion, body)` + `RequestContext` | row with kind, runId, personaId, turnId, fixtureVersion, `schemaVersion=1`, payload `{config:{id, fingerprint, files}, kbSnapshot, seeds, body}` | N/A |
| No transaction | `AuditWriter.write` called outside a transaction | `IllegalTransactionStateException` | MANDATORY |
| Both or no ids | `turnId` and `actionId` both set / both null for RESPONSE or ACTION | rejected before insert | `IllegalArgumentException` |
| Evidence twice | `putIfAbsent` with an existing `provId` | no-op, first row unchanged | `ON CONFLICT DO NOTHING` |
| App UPDATE/DELETE | through the app datasource | `permission denied` | SQL error |
| Owner UPDATE/DELETE/TRUNCATE | through the owner connection | trigger raises `audit tables are append-only` | SQL error |
| Export | `GET /api/research/audit/export[?runId=r1]` as RESEARCHER | `application/x-ndjson`, one JSON object per line, insertion order, filtered by run | other roles 403; no profile 404 |
| Forced failure | use case writes evidence, then the audit write throws | evidence row not committed | rollback |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/` -- V1–V3 exist; new `V4__audit.sql`. Roles are cluster-wide: create `fine_app` only if missing (`DO $$ … $$`).
- `backend/src/main/resources/application.yaml` -- datasource defaults `fine`/empty; add `spring.flyway.user/password` and the app role.
- `deploy/docker-compose.yml`, `deploy/.env.example`, `deploy/.env` (git-ignored, local) -- `FINE_DB_PASSWORD` is the owner secret today; add `FINE_APP_DB_PASSWORD`.
- `backend/src/test/java/de/financetool/DeploymentConfigTest.java` -- `ALLOWED_APP_VARIABLES` set; extend with the owner variables.
- `backend/src/test/java/de/financetool/PostgresIntegrationTest.java` -- `@ServiceConnection` singleton container (container user is superuser); switch to `@DynamicPropertySource` so the app uses `fine_app` and Flyway the container owner, as in production.
- `backend/src/main/java/de/financetool/audit/package-info.java` -- module, depends only on `shared`. `shared.RequestContext` (`agentConfig.id()`, `configFingerprint.value()/fileHashes()`, `kbSnapshot`, `seeds`, `personaId`, `runId` as `String`).
- `backend/src/main/java/de/financetool/research/package-info.java` -- module exists, no code; research controllers use `@Profile(SecurityConfig.RESEARCH_PROFILE)`; security already maps `/api/research/**` to `RESEARCHER` under the profile.
- `backend/src/test/resources/application-webtest.yaml` -- DB-less tests exclude JDBC auto-config; `SecurityWebTest`, `OpenApiSnapshotTest` need a `@MockitoBean JdbcClient` (or the audit adapter bean mocked).
- Jackson 3.1 (`tools.jackson.databind`) is on the classpath.

## Tasks & Acceptance

**Execution:**
- [x] `db/migration/V4__audit.sql` -- tables, triggers (row BEFORE UPDATE OR DELETE, statement BEFORE TRUNCATE), role `fine_app` (LOGIN), grants: SELECT/INSERT/UPDATE/DELETE on existing non-audit tables, SELECT/INSERT on audit tables, default privileges for later tables, sequence usage, nothing on `flyway_schema_history` -- AC 1.
- [x] `db/callback/afterMigrate.sql` + `application.yaml` -- set the `fine_app` password from placeholder `appDbPassword` (`= spring.datasource.password`) on every migrate; `spring.flyway.user: ${SPRING_DATASOURCE_OWNER_USERNAME:fine}`, `password: ${SPRING_DATASOURCE_OWNER_PASSWORD:}`; datasource username default `fine_app` -- AC 1.
- [x] `deploy/` + `README.md` -- `SPRING_DATASOURCE_PASSWORD: ${FINE_APP_DB_PASSWORD:?}`, `SPRING_DATASOURCE_OWNER_PASSWORD: ${FINE_DB_PASSWORD:?}`; `.env.example` and the local `.env` get the new secret -- AC 1.
- [x] `audit/` API -- `AuditKind`, `AuditEntry(kind, turnId, actionId, fixtureVersion, Object body)`, `AuditWriter.write(RequestContext, AuditEntry)`, `EvidenceItem(provId, personaId, origin, originId, Object content)`, `EvidenceStore.putIfAbsent(List<EvidenceItem>)`, `AuditExport.writeJsonl(String runId, OutputStream)` -- AC 2, 3.
- [x] `audit/application/AuditPayloadMapper.java` -- own `JsonMapper` (sorted properties and map keys, ISO dates); builds the payload from `RequestContext` + body -- AC 2.
- [x] `audit/adapter/` -- `JdbcClient` inserts (`?::jsonb`), `ON CONFLICT (prov_id) DO NOTHING`, export query ordered by `seq`; services `@Transactional(propagation = MANDATORY)` (export `readOnly`) -- AC 2, 3.
- [x] `research/web/AuditExportController.java` + `research/application/ExportAuditUseCase.java` -- `@Profile("research")`, `GET /api/research/audit/export`, `application/x-ndjson` -- AC 3.
- [x] Tests -- `AuditTrailIntegrationTest` (Testcontainers): matrix rows incl. app-role UPDATE fails, owner triggers, idempotent evidence, MANDATORY, forced-failure rollback via a test `*UseCase`; export test under `research` (order, filter, 403, 404); `AuditPayloadMapperTest` (fixed format, sorted keys); update `DeploymentConfigTest` and DB-less tests -- matrix.

**Acceptance Criteria:**
- Given the migration runs as owner, when the app starts, then it connects as `fine_app`, which has only INSERT and SELECT on `audit_records` and `evidence_items`, and an UPDATE through the app connection fails (test).
- Given a record is written, then it holds kind, runId, personaId, turnId or actionId, fixtureVersion, schemaVersion and a payload with config id, fingerprint, per-file hashes and KB snapshot id.
- Given records exist, when the researcher exports, then JSONL in insertion order, optionally filtered by `runId`.
- Given a use case whose audit write throws, when the transaction ends, then nothing it wrote is committed.

### Review Findings

Joint code review 2026-10-21 of 00b341b..1e2e9e5 (blind-hunter, edge-case-hunter, verification-gap, acceptance-auditor).

- [x] [Review][Patch] The `fine_app` password is spliced into a single-quoted literal (a `'` breaks the migration or injects SQL as owner) and an unset `SPRING_DATASOURCE_PASSWORD` silently sets an empty password; use a dollar-quoted literal and fail on an empty secret [backend/src/main/resources/db/callback/afterMigrate.sql:2]
- [x] [Review][Patch] `?runId=` (empty) filters for the empty run id and exports nothing instead of all records [backend/src/main/java/de/financetool/research/web/AuditExportController.java:35]
- [x] [Review][Patch] Export query has no fetch size, so the PostgreSQL driver buffers the whole audit table before streaming [backend/src/main/java/de/financetool/audit/adapter/AuditRecordRepository.java:47]
- [x] [Review][Patch] Docs lag the role split: compose header names only `FINE_DB_PASSWORD`, `.env.example` offers a `SPRING_DATASOURCE_USERNAME` override that V4's grants do not follow, README lacks the owner variables for runs outside compose [deploy/docker-compose.yml:3]
- [x] [Review][Patch] Test name "evenTheOwnerCannotChangeAuditTables" overstates the protection: the owner can still disable or drop triggers; name and comment should state what is tested (trigger rejection on the owner connection) [backend/src/test/java/de/financetool/audit/AuditTrailIntegrationTest.java:158]
- [x] [Review][Patch] Research use cases use the literal `@Profile("research")` while controllers use `SecurityConfig.RESEARCH_PROFILE` [backend/src/main/java/de/financetool/research/application/ExportAuditUseCase.java:11]

Rejected:
- Grants miss existing sequences (false: V1–V3 create no sequences; `fine_app` grants are explicit for the three tables).
- DB lacks CHECKs for kind→turn/action id and prov_id format (low: `AuditService` is the only writer; the fix needs a V5 migration).
- `putIfAbsent` ignores differing content for an existing provId (low/by design: AD-2 makes the figure key deterministic incl. persona; idempotent insert is the specified semantics).
- Truncated export on mid-stream failure returns 200 (low: needs an end-marker protocol; unlikely at study scale).
- Over-long runId/fixtureVersion/originId fails the insert (false: loud failure that rolls back is correct).
- Shared cluster overwrites `fine_app` password (low: one database per cluster in every environment).
- `POSTGRES_USER` now follows the owner variable (low: no deployment overrides it yet).

## Implementation Notes

- Timing: start 2026-10-21T18:10+02:00.
- Implemented directly in this session instead of a fresh subagent (context already loaded; user's fast path).
- `fine_app` gets SELECT/INSERT/UPDATE/DELETE on the personas tables and SELECT/INSERT on the audit tables; default privileges give later tables full data access, so a later append-only table must `REVOKE UPDATE, DELETE` explicitly. Identity columns need no sequence grant.
- No foreign key from `audit_records`/`evidence_items` to `personas` (AD-12: id-only cross-module references).
- Found by tests and fixed: Jackson orders record components before other properties (`SORT_CREATOR_PROPERTIES_FIRST`), so `SORT_PROPERTIES_ALPHABETICALLY` alone left record bodies unsorted; disabled. `USE_BIG_DECIMAL_FOR_FLOATS` keeps `12.50` in the export.
- Forced-failure test uses `TransactionTemplate` as the use-case boundary (same transaction semantics as a `*UseCase`, no extra Spring context); the failure is a body whose serialisation throws.
- `PostgresIntegrationTest` now sets datasource and Flyway users explicitly (`@DynamicPropertySource`, no `@ServiceConnection`): app = `fine_app`, Flyway = container owner. `AuthIntegrationTest` is public so other packages can use `login(...)`.
- Export streams through `HttpServletResponse` inside a read-only use-case transaction; `application/x-ndjson`; every line has all record fields (nulls written) and the payload re-serialised in the fixed format.
- Verified: `./mvnw -B verify -Dfine.it.required=true` BUILD SUCCESS, 97 tests, 0 failures, 0 skipped (`AuditTrailIntegrationTest` 20, `AuditPayloadMapperTest` 2), plus `AuditEntryTest` 3.
- Verified with Docker Compose: existing volume migrated to V4, app connects as `fine_app` (`pg_stat_activity`), `/actuator/health` UP.
- Timing: end 2026-10-21T18:35+02:00.

- Review patches applied (2026-10-21): the `fine_app` password is now set by a Java Flyway callback (`shared.AppRolePasswordCallback`, after every migrate, on the owner connection) that lets PostgreSQL quote the secret from a bind parameter (`format('%L', ?)`) and refuses an empty secret. A dollar-quoted SQL callback was tried first and failed: Flyway does not replace a placeholder directly after a dollar-quote tag. `afterMigrate.sql` is gone; V4's header comment still names it (checksummed, left as is). The test password contains a `'`. Also: empty `?runId=` means no filter; export query uses fetch size 500; docs name both secrets and the owner variables for runs outside compose; owner-trigger test renamed with its limit stated; research use cases use `SecurityConfig.RESEARCH_PROFILE`.
- Verified after review: `./mvnw -B verify -Dfine.it.required=true` BUILD SUCCESS, 130 tests, 0 failures, 0 skipped; compose stack restarted: app connects as `fine_app` via the new callback, models verified, `/actuator/health` UP.

## Spec Change Log

## Review Triage Log

- Review layers (blind-hunter, edge-case-hunter, verification-gap) not run: the user will review Stories 1.5–1.7 together after 1.7.

## Design Notes

- Owner variables stay inside `SPRING_DATASOURCE_*` (`SPRING_DATASOURCE_OWNER_USERNAME/PASSWORD`) so AD-17's "environments differ only in …" list holds without a new prefix.
- The app password is set in a Flyway `afterMigrate` callback, not in a versioned migration: callbacks are not checksummed, so a changed secret never breaks validation, and no secret lands in `flyway_schema_history`.
- `audit_records(seq BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, id UUID DEFAULT gen_random_uuid(), kind, run_id, persona_id, turn_id, action_id, fixture_version, schema_version, payload JSONB, created_at TIMESTAMPTZ DEFAULT now())`; `evidence_items(prov_id PRIMARY KEY, persona_id, origin, origin_id, content JSONB, created_at)` (origin per review J5/H2: `turn|overview|monitoring|simulate|document`).
- JSONB normalises key order on storage; the export re-serialises each row with `AuditPayloadMapper`, so the exported line format is fixed.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify -Dfine.it.required=true` -- expected: BUILD SUCCESS, 0 skipped.
- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build` -- expected: app migrates V4, connects as `fine_app`, `/actuator/health` UP.
