---
title: 'Story 13.3: Split TurnOrchestrator into stage classes (technical debt)'
type: 'refactor'
created: '2026-10-28'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_revision: 'f94637a1ac136259f931bb91f80419693e5ecc3a'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-13-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `TurnOrchestrator` (638 lines) holds every stage's logic. Stories 13.4 (tool plan) and 13.5 (prompt payload/history) both change it; retro 6/7 item C2.

**Approach:** Move the stage logic into package-private `@Component` stage classes in `advisory.application`; the orchestrator keeps the public API (`run`, `calculate`, `resume`, `Continuation` records) and only sequences the stages. Pure move, no behaviour change.

## Boundaries & Constraints

**Always:**
- One class per stage group: `ClassifyStage` (stage 2), `ToolPlanStage` (3), `PromptStage` (5, exposure filter + ledger), `DraftStage` (6, incl. `rangeText`, `withFixedSentences`), `CheckStages` (7–9: shadow checks, rule engine, judge, `facts`, `passagesInPrompt`), `BlockRoutingStage` (10, incl. `chips`, `releasable`), `ReleaseStage` (11, audit and release, `snapshot`).
- Dialog state, NLU and KB retrieve stay one-line calls in the orchestrator (they already delegate or pass through).
- The orchestrator keeps package-private one-line stage methods (`blockRouting`, `judge`, `ruleEngine`, `shadowChecks`, `auditAndRelease`, …) that `run` sequences through, so existing tests call them unchanged.
- Stage classes follow the existing pattern (`Recommendations`, `NeedsStatements`: package-private `@Component`, constructor injection, no `@Transactional` — they run in the caller's MANDATORY transaction).
- Javadoc moves with the code; the orchestrator's class Javadoc names the stage classes.
- `TurnStage` audit markers (`ran`/`passedThrough`) stay where the logic is, unchanged.

**Never:** behaviour, prompt, audit-body or figure-key changes; new tests beyond wiring; moving the `Continuation` types; changing `DialogStates`, `NluExtractor`, domain classes.

</frozen-after-approval>

## Code Map

- `backend/src/main/java/de/financetool/advisory/application/TurnOrchestrator.java` -- source of all stages: `classify` L215, `toolPlan` L248, `exposureFilter` L314 + `level(DataExposureLevel)` L346, `llmDraft` L360 + `rangeText`/`withFixedSentences`, `shadowChecks` L410, `ruleEngine` L427, `judge` L456, `blockRouting` L468 + `chips`/`releasable`, `facts`/`passagesInPrompt`/`level(ComplianceLevel)` L514-541, `auditAndRelease` L550 + `snapshot`.
- `backend/src/test/java/de/financetool/advisory/application/TurnOrchestratorTest.java` -- constructs the orchestrator from 15 mocks (L108); calls stage methods on the orchestrator and statics `TurnOrchestrator.level(...)` (L432, L712) and `TurnOrchestrator.passagesInPrompt` (L489, L503).
- `DialogStates.java` -- references `TurnOrchestrator.Continuation/Confirmation/SavedAssessment`; untouched.
- `src/test/java/de/financetool/ArchitectureRules.java` -- AD-21: no `@Transactional` outside `*UseCase` except MANDATORY; stages need none.

## Tasks & Acceptance

**Execution:**
- [x] `advisory/application/{ClassifyStage,ToolPlanStage,PromptStage,DraftStage,CheckStages,BlockRoutingStage,ReleaseStage}.java` -- create; move each stage's method body, helpers and Javadoc verbatim; inject only the dependencies the stage uses.
- [x] `TurnOrchestrator.java` -- constructor takes `DialogStates`, `NluExtractor` and the seven stages; stage methods become one-line delegates; drop moved helpers and unused imports.
- [x] `TurnOrchestratorTest.java` -- wire the stages from the existing mocks; static references `TurnOrchestrator.level/passagesInPrompt` point to the owning stage class (wiring only).

**Acceptance Criteria:**
- Given the split, when `TurnOrchestrator` is read, then it contains no stage logic, only sequencing and delegates, and `ToolPlanStage` and `BlockRoutingStage` exist as separate classes.
- Given the full backend build, when `./mvnw -q verify -Dfine.it.required=true` runs, then all tests pass with only wiring changes in tests, including `ArchitectureRules`, `ModulithTest`, `OpenApiSnapshotTest` and `FigureKeyFreezeTest`.

## Implementation Notes

- Moved by line ranges with a script (verbatim bodies and Javadoc); imports pruned per class. `TurnOrchestrator` 638 -> 197 lines: public API, `Continuation` records, `run` sequencing, one-line stage delegates; dialog state, NLU and KB retrieve stay inline.
- Shared helpers: `PromptStage.passagesInPrompt` (used by `CheckStages`), `DraftStage.withFixedSentences` (used by the rule engine's regeneration), `PromptStage.level(DataExposureLevel)`, `CheckStages.level(ComplianceLevel)`.
- Tests: only `TurnOrchestratorTest` wiring changed (constructor, four static references to the owning stage).
- Review: none (pure move, user decision 2026-10-28 to save tokens); unchanged tests are the evidence.
- Final: `verify -Dfine.it.required=true` green, 0 failures, 0 skipped (Docker had to be started first).

## Spec Change Log

## Review Triage Log

## Verification

**Commands:**
- `cd backend && JAVA_HOME=C:\Users\commc\.jdks\temurin-25 ./mvnw -q test -Dtest='TurnOrchestratorTest,CalculateUseCaseTest,ArchitectureRules,ModulithTest'` -- expected: green
- `cd backend && JAVA_HOME=C:\Users\commc\.jdks\temurin-25 ./mvnw -q verify -Dfine.it.required=true` -- expected: 0 failures, 0 skipped
