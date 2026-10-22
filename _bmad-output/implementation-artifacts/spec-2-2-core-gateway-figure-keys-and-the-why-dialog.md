---
title: 'Story 2.2: Core gateway, figure keys and the "Why?" dialog'
type: 'feature'
created: '2026-10-21'
status: 'in-review'
baseline_revision: '0fb8853f037dd4520ff89b22dc3821bb48ff128e'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/planning-artifacts/ux-designs/ux-FinanceTool-2026-10-21/DESIGN.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** No Core function, figure key or provenance exists yet, so no figure can be traced (FR-8), and `audit.EvidenceStore` has no production caller (retro A7).

**Approach:** Add the `core` result types (`CoreResult`, `SourcedNumber`, `Unit`), deterministic `provId`s, `core.CoreGateway` as the single entry point that persists every figure via `EvidenceStore.putIfAbsent`, the persona-scoped `GET /api/provenance/{provId}` in `audit.web`, and the Angular "Why?" dialog. The first real figure is the current account balance on the Home tab (the first of the three Story 2.5 summary figures), so the whole path runs end to end.

## Boundaries & Constraints

**Always:**
- `SourcedNumber(provId, BigDecimal value, Unit unit, String label)` and `Unit = EUR | FRACTION | PP | MONTHS | YEARS | COUNT` in `shared.domain`; `CoreResult(function, inputs, outputs, assumptions, ruleParamVersions, List<SourcedNumber>)` in `core.domain`.
- `provId` = first 16 lowercase hex chars of SHA-1 over UTF-8 of: personaId, function, canonical inputs, `asOf`, config fingerprint value, output key (newline-joined). Canonical inputs: keys sorted, BigDecimal as plain string, dates ISO, lists in order. Pure Java in `core.domain` (java.* only).
- Domain functions are pure and get every value as an argument (no clock, no repository); the gateway reads `BankReadPort.snapshot(personaId, asOf)` and the config fingerprint from the `RequestContext`.
- Evidence content is the `audit.Provenance` record (audit base package, built by `core`), serialised by `AuditPayloadMapper`; origin `OVERVIEW` for overview figures. `putIfAbsent` runs in the use case's transaction (AD-21).
- `/api/provenance/{provId}`: persona from `PersonaScope` only; reads only `evidence_items` filtered by provId AND persona; another persona's or unknown provId → 404 `NOT_FOUND`; malformed id (not 16 hex) → 400.
- Controllers call only use cases; use cases build the `RequestContext` via the factory.
- UI copy exactly: section headings "Where this number comes from", "What went in", "Assumptions", "Checks" in that order; buttons "Close" and "Back"; link "Why?". Money `1,240.00 EUR`, real minus `−` (U+2212), tabular numerals, Atruvia M3 theme tokens, `lang="en"`.
- Every figure the frontend shows arrives from the API with its `provId`; TypeScript only formats, never computes.

**Never:**
- No other Core functions (forecast, emergency fund, projection, coverage are Stories 2.3–2.5); no Check Chain (Epic 3); no chat figure chips or Overview highlight (Epic 3).
- No internal vocabulary in customer text (`provId`, function ids, "Check Chain"); the check id appears only as the small caption.
- Do not edit applied migrations; no new table (evidence_items exists).
- Do not hand-write API DTO types in the frontend; regenerate with `npm run api:generate` from the snapshot.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Home figure | p-lena at 2026-10-15 calls `GET /api/overview/home` | `currentAccount` = 1974.00 EUR with a 16-hex `provId`; one `evidence_items` row (origin `overview`) | No error expected |
| Same call twice | Same persona, date, config | Same `provId`, still one row | No error expected |
| Date changed | Researcher moves the simulated date | Different `provId` and value | No error expected |
| Own provenance | p-lena `GET /api/provenance/{own}` | 200: source, inputs, assumptions, checks (empty list) | No error expected |
| Foreign provenance | p-marco requests Lena's `provId` | Same as unknown id | 404 `NOT_FOUND` |
| Malformed id | `GET /api/provenance/xyz` | Rejected | 400 |
| No persona session | anonymous / advisor | Rejected | 401 / 403 as existing persona endpoints |
| Drill-down | Input with own `provId` clicked | Dialog content replaced by that figure, "Back" returns | No error expected |

</intent-contract>

## Code Map

- `backend/src/main/java/de/financetool/audit/{EvidenceStore,EvidenceItem,EvidenceOrigin}.java` -- existing API; `EvidenceItem.content` is `Object`
- `backend/src/main/java/de/financetool/audit/application/AuditService.java` -- `putIfAbsent` impl (MANDATORY); add a persona-scoped evidence read for the provenance use case
- `backend/src/main/java/de/financetool/audit/adapter/AuditRecordRepository.java` -- `insertEvidenceIfAbsent`; add `findEvidence(provId, personaId)`
- `backend/src/main/java/de/financetool/audit/application/AuditPayloadMapper.java` -- the only JSON (de)serialiser for audit content
- `backend/src/main/java/de/financetool/personas/RequestContextFactory.java` -- move to `shared.RequestContextFactory` (impl `personas.application.RequestContextService` stays) so `core` can build contexts without depending on `personas` (AD-12 graph has no core→personas edge)
- `backend/src/main/java/de/financetool/bank/BankReadPort.java` -- snapshot source; `BankSnapshot.Account.balance` is opening + bookings ≤ asOf
- `backend/src/main/java/de/financetool/shared/web/{PersonaScopeResolver,SecurityConfig,ProblemDetailsAdvice,OpenApiSchemas}.java` -- scope injection, `/api/**` rules, `NOT_FOUND`/`BAD_REQUEST` problem codes, explicit schemas
- `backend/src/main/java/de/financetool/personas/web/PersonaController.java` -- controller style reference (records with `@Schema(requiredMode=REQUIRED)`, `@Parameter(hidden=true) PersonaScope`)
- `backend/src/test/java/de/financetool/personas/web/PersonaIsolationTest.java`, `AuthIntegrationTest.login(...)` -- isolation test pattern and login helper
- `backend/src/test/java/de/financetool/shared/web/OpenApiSnapshotTest.java` -- `-Dopenapi.update=true` rewrites `frontend/openapi/openapi.json`
- `backend/src/test/java/de/financetool/{personas/application/RequestContextServiceTest,audit/application/AuditPayloadMapperTest,llm/adapter/OllamaLlmAdapterTest}.java` -- three hand-built `AgentConfiguration`/`RequestContext` fixtures (A5)
- `frontend/src/app/pages/workspace/workspace.page.{html,ts}` -- empty tab sections; Home tab gets the summary card
- `frontend/src/app/api/` -- generated client (`npm run api:generate`, checked by `prebuild`)
- `_bmad-output/planning-artifacts/ux-designs/ux-FinanceTool-2026-10-21/mockups/advice-marco.html:189-215` -- dialog composition; `mockups/workspace-lena.html:135-140` -- Home tile

## Tasks & Acceptance

**Execution:**
- `shared/domain/{Unit,SourcedNumber}.java`, `shared/RequestContextFactory.java` (moved; update imports).
- `core/domain/{CoreResult,CoreComputation,Figure,FigureInput,FigureKey,CanonicalForm,AccountBalance}.java` -- result types; pure `AccountBalance.of(account, asOf)` (inputs: opening balance with date, sum and count of bookings up to asOf); `FigureKey.of(...)`.
- `core/CoreGateway.java` (API) + `core/application/CoreGatewayService.java` -- `CoreResult currentAccountBalance(RequestContext, EvidenceOrigin)`: snapshot → domain → provIds → `Provenance` per figure → `putIfAbsent` (MANDATORY).
- `audit/Provenance.java` -- record: provId, function, source(kind `CORE_FUNCTION|RULE_PARAMETER|KB_CITATION`, description), label, value, unit, asOf, inputs(label, value?, unit?, text?, provId?), assumptions, ruleParameters, checks(id, passed, description).
- `audit/application/ShowProvenanceUseCase.java` + `audit/web/ProvenanceController.java` -- `GET /api/provenance/{provId}`.
- `core/application/ShowHomeOverviewUseCase.java` + `core/web/OverviewController.java` -- `GET /api/overview/home` → `{ currentAccount: Figure{provId,label,value,unit} }`.
- OpenAPI snapshot regenerated; `npm run api:generate`.
- Frontend: `shared/figure.pipe.ts` (format by unit, U+2212), `shared/why-link.component.ts` (opens dialog, focus returns), `shared/why-dialog.component.{ts,html,scss}` (MatDialog, four sections, title "{label}: {value}", drill-down stack with "Back", "Close", empty checks text), `pages/workspace/home-tab.component.*` (card "Current account", figure-lg + "Why?") wired into the Home tab.
- Tests: `FigureKeyTest` (stable; each component changes the key); `AccountBalanceTest`; `CoreGatewayIntegrationTest` (twice → same provIds, one row); `ProvenanceEndToEndTest` (A7 adapted: overview call → evidence row → provenance 200 for Lena, 404 for Marco, 400 malformed, 401 anonymous); `ArchitectureTest`/`ModulithTest` green; frontend specs for pipe, dialog (order, drill-down/back, close returns focus, no internal words), home tab.
- A5: one shared test fixture (e.g. `src/test/java/de/financetool/testing/TestContexts.java`) replacing the three hand-built fixtures; mark `epic-1-retro-item-5-a5-shared-test-fixture-for-agentconfigur` done via `.claude/skills/bmad-retrospective/scripts/sprint_status.py` (see `--help`).

**Acceptance Criteria:**
- Given Lena logged in, when the Home tab loads, then a "Current account" card shows `1,974.00 EUR` with "Why?", and the dialog shows the four sections in order with values from `/api/provenance/{provId}`.
- Given the dialog, when Esc or "Close" is used, then focus returns to the "Why?" trigger; an input "Why?" replaces the content in place and "Back" restores it, without a second dialog.
- Given the backend, when `ModulithTest` and `ArchitectureTest` run, then `core` depends only on its allowed modules and controllers call only use cases.

## Spec Change Log

## Review Triage Log

## Design Notes

- **Context factory in `shared` (decision):** `core` serves the overview but may not depend on `personas`; the factory interface moves to `shared` (dependency inversion), the implementation stays in `personas`. Alternative (adding core→personas to the AD-12 graph) rejected as an architecture change.
- **First figure (decision):** current account balance, function `accountBalance`, plain words "current account balance". Source text: "Calculated by Fine's calculation engine (current account balance), not by the language model. Based on your account data from {openingDate} to {asOf}." (dates `d MMM yyyy`). Assumptions: "Includes every booking up to the end of {asOf}." Checks: none yet; dialog shows "No checks apply to this figure."
- **Precision (decision):** EUR figures always show two decimals (`1,974.00 EUR`) in cards and dialog, so one `provId` always shows one value; the mockup's rounded tiles are illustrative.
- **A7:** this story adds the first production `EvidenceStore` caller and its end-to-end test. A7 stays open: `AuditWriter` gets its first caller in Epic 3, which must add the export test.
- **Review setup (user rule 2026-10-21):** all four layers; Blind Hunter and Intent Alignment on model `sonnet`, the other two on the session model (approved deviation from "same model capability"). Review diff excludes generated client, lockfiles and snapshots.

## Implementation Notes

Deviations from the bmad-build-auto workflow (user rules, 2026-10-21):
- Live check runs the app locally (`./mvnw spring-boot:run` against the compose `db` and `ollama`) instead of rebuilding the image; the image is rebuilt only for stories touching `pom.xml`, Dockerfile, compose or `data/`, and once at the end of Epic 2.
- Review: all four layers, but Blind Hunter and Intent Alignment run on model `sonnet` (approved deviation from "same model capability"); the review diff excludes the generated API client, lockfiles and the OpenAPI snapshot (tests stay in).
- Fix loops run only the affected test classes; the full backend verify and frontend test/build run before each commit.
- The story is committed after verification and before the review; review fixes follow as a separate commit.

- Live check 2026-10-22 (local `spring-boot:run`): `db` and `ollama` publish no host ports, so a scratchpad compose override (`127.0.0.1:5432`, `127.0.0.1:11434`, not committed) was used while the compose app was stopped. Results: p-lena `/api/overview/home` 1974.00 EUR; `/api/provenance/{id}` 200 with source, three inputs, one assumption, no checks; p-marco 404, malformed 400, anonymous 401; one `overview` evidence row. The UI was not inspected visually at 1366/1440 (no browser tool in this session); covered by the frontend specs only. Stack restored afterwards (default profile, no published db/ollama ports).

## Verification

**Commands:**
- `export JAVA_HOME=/c/Users/commc/.jdks/temurin-25 && cd backend && ./mvnw -B verify -Dfine.it.required=true` -- BUILD SUCCESS, 0 skipped
- `cd frontend && npx ng test --watch=false && npm run build` -- green, generated client matches snapshot

**Manual checks (if no CLI):**
- Live (local, no image rebuild): `./mvnw spring-boot:run` against compose `db`/`ollama` (compose app stopped meanwhile); login p-lena → `/api/overview/home` 1974.00 → `/api/provenance/{id}` 200; p-marco → 404. UI at 1366×768 and 1440: card and dialog render, focus returns.
