---
title: 'Story 8.1: Autonomy Gate and Action proposals'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_revision: '1974dd423aab36de37a35ccf8e12b1f287fc24ed'
baseline_commit: '1974dd423aab36de37a35ccf8e12b1f287fc24ed'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The `actions` module is an empty package: nothing assigns an Action its maximum autonomy level, so monitoring (8.2) and confirmation (8.4) have no gate and no Action row to work on (FR-35, AD-10, AR12).

**Approach:** A pure `AutonomyGate.decide(action, context, thresholds)` in `actions.domain` implements the FR-35 table. `ActionService.propose` is the only creator of `actions` rows: it builds the gate context from one bank snapshot, maps the level to the Action state, stores the row and writes an `ACTION` Audit Record in the caller's transaction.

## Boundaries & Constraints

**Always:**
- Gate input `GateAction(type, regulatoryClass, amount, reversible)`; context `GateContext(liquidAssets, dataConfidenceHigh, suitabilityComplete, Optional<MandateBounds(maxPerAction, floor)> mandate, emergencyFundAfter)`; thresholds `AutonomyThresholds` (already bound from `config/autonomy.yaml`; no number in code).
- Criteria (ids in `cappingCriteria`, table order): `no_covering_mandate` or `amount_above_mandate_bounds` (amount > maxPerAction or emergencyFundAfter < floor) → L2; `not_reversible` → L2; `investment_above_liquid_share` (amount > share × liquidAssets) → L2; `suitability_incomplete` (investment) → L1; `insurance_class` → L1; `data_confidence_low` → L1; `legal_class` → L1. Level = lowest cap; none failed → L3 for `EF_TOP_UP`/`SHORTFALL_TRANSFER`, else L2 with criterion `not_in_l3_set`. `cappingCriteria` lists every failed criterion, not only the lowest. Null amount counts as 0.
- Enums: `AutonomyLevel L0–L3`, `RegulatoryClass payment|investment|insurance|legal`, `ActionType SHORTFALL_TRANSFER|EF_TOP_UP` (both `payment`, reversible; 8.2/AD-27 add more), `ActionState INFORMED|RECOMMENDED|AWAITING_CONFIRMATION|EXECUTED|ESCALATED`. State: L0→INFORMED, L1→RECOMMENDED, L2→AWAITING_CONFIRMATION; L3 → `IllegalStateException` (no Mandates before Epic 9, so unreachable; execution comes with Epic 9).
- `ProposedAction(type, regulatoryClass, amount, sourceAccountId, targetAccountId, triggerId)`; `propose(RequestContext, PersonaScope, String fixtureVersion, ProposedAction)` → `ActionProposal(actionId, state, level, cappingCriteria)`, `@Transactional(MANDATORY)`. `fixtureVersion` is a parameter because `actions` may not depend on `personas`.
- Context: one `BankReadPort.snapshot(personaId, asOf)` handed to a new `CoreGateway.gateMeasures(context, snapshot, sourceAccountId, amount)` → `GateMeasures(liquidAssets, DataConfidence.Result, overnightAfter, emergencyFundTarget)` computed with `Liquidity`, `DataConfidence.of`, `EmergencyFundTarget.of` (thresholds from `context.thresholds()`), no evidence stored; `suitabilityComplete` = `ProfileCompleteness.strictMissing(Profiles.stored(scope), "investment_decision").isEmpty()`; `MandateLookup` returns empty (Epic 9 seam).
- No valid Rule Parameter for the emergency-fund target → row stored as `ESCALATED`, level null, criterion `rule_parameter_missing`; audit still written.
- Table `actions` (V18; id-only references, no FKs), persona scope on every read/write, `ActionResetter` (`module() = "actions"`), `PersonaOwnedTablesTest` entry. Audit body `{type, class, amount, accounts, triggerId, state, level, cappingCriteria, context}`.

**Never:** a `BankPort` call or execution path in 8.1; insurance/legal ever executed; thresholds or level literals outside the gate and config; a second creator of `actions` rows; `Instant.now()` in application/domain.

</frozen-after-approval>

## Code Map

- `backend/src/main/java/de/financetool/actions/package-info.java` -- deps `core`, `core :: results`, `bank`, `profile`, `audit`, `shared` (ModulithTest line 41 already expects them).
- `shared/config/AutonomyThresholds.java`, `config/autonomy.yaml` -- reuse; `RequestContext.thresholds()` carries them.
- `core/application/CoreGatewayService.java:88-135` -- pattern for liquid assets / EF target / DataConfidence from a snapshot (`BankMapping.entries`, `parameter(...)`, `AccountType.OVERNIGHT`); reuse for `gateMeasures`, but no `record(...)`.
- `core/CoreGateway.java` -- add `gateMeasures`; result record `GateMeasures` in `core.domain` with `@NamedInterface("results")`.
- `escalation/adapter/EscalationCaseRepository.java`, `V10__escalation.sql`, `escalation/application/EscalationResetter.java` -- JdbcClient repository, migration and reset patterns.
- `audit/AuditWriter.java`, `AuditEntry(kind ACTION, null, actionId, fixtureVersion, body)`.
- `profile/Profiles.java`, `profile/ProfileCompleteness.java:65` -- suitability completeness.
- `research/PersonaOwnedTablesTest.java:24` -- add `actions` → `actions`.

## Tasks & Acceptance

**Execution:**
- [x] `actions/domain/` -- enums, `GateAction`, `GateContext`, `MandateBounds`, `GateDecision`, `AutonomyGate` -- pure FR-35 table.
- [x] `core/domain/GateMeasures.java`, `core/CoreGateway.java`, `core/application/CoreGatewayService.java` -- `gateMeasures`.
- [x] `db/migration/V18__actions.sql` -- table `actions` (id, persona_id, type, regulatory_class, amount, source/target account, trigger_id, state, level, capping_criteria JSONB, as_of, created_at).
- [x] `actions/ProposedAction.java`, `ActionProposal.java`, `Actions.java` (module API `propose`), `application/ActionService.java`, `MandateLookup` + empty impl, `adapter/ActionRepository.java`, `application/ActionResetter.java`.
- [x] Tests: `AutonomyGateTest` (table incl. combinations, determinism), `ActionServiceTest` (Postgres IT: Lena shortfall transfer → L2 `AWAITING_CONFIRMATION` with `no_covering_mandate`; insurance → RECOMMENDED; ACTION audit record; reset deletes rows; missing Rule Parameter → ESCALATED), `PersonaOwnedTablesTest`.

**Acceptance Criteria:**
- Given the FR-35 table, when `AutonomyGateTest` runs every row and combinations, then each yields the expected level and failed criteria; the investment and irreversibility rows are covered only here.
- Given identical inputs, when decided twice, then decisions are equal.
- Given Lena at 15 Oct, when `propose(SHORTFALL_TRANSFER, payment, amount, overnight → current)`, then one `actions` row `AWAITING_CONFIRMATION`, level L2, criteria `[no_covering_mandate]`, and one `ACTION` Audit Record with that decision.
- Given an Action of class `insurance` or `legal`, when proposed, then state `RECOMMENDED` and nothing executes.
- Given a persona reset, then the persona's `actions` rows are gone.

## Implementation Notes

- The gate takes a domain record `GateThresholds(investmentShareOfLiquidAssets)` instead of `AutonomyThresholds`: ArchitectureTest keeps domain code free of `shared.config`. `ActionService` builds it from `context.thresholds()`, so the number still comes only from `config/autonomy.yaml`.
- `GateMeasures` carries Data Confidence as plain fields (`dataConfidenceHigh`, `dataConfidenceCoverage`, `dataConfidenceLongestGapDays`) instead of `DataConfidence.Result`: exposing `Result` would expose `CheckResult` via `toCheck()` and break `ModulithTest.theResultsInterfaceIsExactlyWhatTheGatewayReturns`.
- A missing emergency-fund Rule Parameter is caught in Core and returned as `emergencyFundTarget = null` (`actions` may not depend on `knowledge`). `emergencyFundAfter` = overnight balance minus amount, only when the source is the overnight deposit.
- The four action enums are `@NamedInterface("types")`; Story 8.2 must add `actions :: types` to monitoring's allowed dependencies.
- `not_in_l3_set` is unreachable until an Action type outside the L3 set exists; untested for now.
- V18 adds DB checks: ESCALATED ⇔ level null; insurance/legal never EXECUTED.

## Spec Change Log

## Review Triage Log

Layers: one combined reviewer (edge-case + verification-gap lenses) and a Blind Hunter (persona-owned table), per the lean loop.

| # | Source | Finding | Verdict | Route / evidence |
|---|---|---|---|---|
| 1 | BH1, CR1 | `overnightAfter` ignores a target overnight account (EF_TOP_UP into overnight leaves the fund unchanged for the floor check) | low | defer: only matters once Mandates exist (Epic 9); fix needs a new `gateMeasures` parameter beyond the spec's signature |
| 2 | BH5, CR2 | Negative `amount` accepted; passes `maxPerAction`, raises `overnightAfter` | low | patch: reject in `ProposedAction`, DB check `amount >= 0` |
| 3 | BH2 | `proposedAt(L3)` throws, a future mandate would crash `propose` | false | spec mandates `IllegalStateException` for L3 until Epic 9 |
| 4 | BH3 | `created_at DEFAULT now()` ties within a transaction; "oldest first" order nondeterministic | low | patch: `clock_timestamp()` |
| 5 | BH4 | No state↔level DB check, no `type` check | low | rejected: unlikely to be met, adds constraints beyond a direct correction; only `ActionService` writes |
| 6 | BH6, CR7 | Scope-mismatch `IllegalArgumentException` and `MANDATORY` untested | low | patch: two tests (other gaps: data-confidence/suitability are covered in `AutonomyGateTest`; rollback rejected as framework behaviour) |
| 7 | BH7 | L0 unreachable through the gate | false | spec's FR-35 table has no L0 row; Javadoc documents the state mapping, which is correct |
| 8 | BH8 | Javadoc "returns the deleted rows" but returns a count; static `JsonMapper`; `findAll` unbounded | low | patch Javadoc; mapper/limit rejected (no named harm, test-only reader) |
| 9 | CR3 | Missing Rule Parameter escalates insurance/legal Actions too | false | spec: no valid Rule Parameter → row `ESCALATED`, regardless of class; AC on insurance assumes a decidable gate |
| 10 | CR4 | `ProposedAction` type and class can disagree | false | spec defines both as independent inputs; no insurance/legal type exists yet, the AC test needs the combination |
| 11 | CR5 | No current account → `IllegalStateException`, nothing stored | false | loud failure on an unshown state; every persona fixture has a current account |
| 12 | CR6 | `propose` twice for one trigger duplicates rows and audits | maybe-false (medium) | defer: depends on 8.2 monitoring's call pattern; settle in 8.2 with a dedupe per trigger |
| 13 | CR8 | Floor boundary tested only below the floor | low | patch: row with fund-after = floor → L3 |

## Verification

**Commands:**
- `./mvnw -q test -Dtest='AutonomyGateTest,ActionServiceTest,PersonaOwnedTablesTest,ModulithTest,ArchitectureTest'` -- green
- `./mvnw -q verify -Dfine.it.required=true` -- green before commit
