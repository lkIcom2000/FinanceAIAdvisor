---
title: 'Story 9.2: Fine acts within my rule and tells me immediately'
type: 'feature'
created: '2026-10-27'
status: 'done'
baseline_commit: 'b96fbd25474ccf24c46cf59c77d0acf66d6adf2d'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-9-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Rules exist (9.1) but cover nothing: `StoredMandates.covering` is empty and `ActionState.proposedAt(L3)` throws, so Fine never acts on its own (FR-35 L3 path, FR-37, FR-38 notify).

**Approach:** The active rule of the Action's template covers it; the gate checks per-transfer, per-month and floor bounds on the source account after the transfer; at L3 `ActionService.propose` books the transfer via `BankPort`, links the rule, and monitoring writes a "Done for you" Notification in the same transaction. A study preset sets up Lena's rule.

## Boundaries & Constraints

**Always:**
- Covering: the persona's non-revoked rule whose `template.actionType()` equals the Action type and whose source/target accounts equal the Action's. Re-read at every gate run (propose and confirm).
- Bounds (one criterion `amount_above_mandate_bounds`, cap L2): amount > `max_per_action`; or month sum + amount > `max_per_month` (month sum = amounts of `EXECUTED` Actions with this `mandate_id` and `as_of` in the calendar month of the simulated date); or source account balance after the Action < `floor`. Reason text picks the first failing bound in that order: per transfer (existing), per month ("…is more than your rule allows this month ({max} per month, {used} already moved)."), floor with the source account's name (overnight deposit / current account).
- `GateMeasures` gets `sourceAfter` (source balance after the Action) and `overnightAfter` adds the amount when the overnight deposit is the target (Epic 8 leftover). `GateContext.emergencyFundAfter` becomes `sourceAfter`; audit context keeps `emergencyFundAfter` and adds `sourceAfter` and the mandate's id and month bounds.
- L3 (`proposedAt(L3)` = `EXECUTED`): `propose` books `bookings.transfer(…, asOf, "Fine rule transfer")`, inserts the row as `EXECUTED` with `mandate_id`, audit body carries the transfer legs and `mandateId`. Only `SHORTFALL_TRANSFER` and `EF_TOP_UP` reach L3 (gate unchanged). Confirm never sets `mandate_id`.
- Monitoring: for an `ACTION` note whose proposal is `EXECUTED`, store the note's done text instead of the proposal text. Done texts (`NotificationTemplates`): title "I moved {amount} to your current account" / "…to your overnight deposit"; body "I moved {amount} from your overnight deposit to your current account, as your rule \"Keep a safety cushion in my current account\" allows." + the existing facts sentence(s) (top-up analogously with "Top up my emergency fund"). Same cited provIds.
- `ActionSummary` gets `underRule` (row has `mandate_id`); the Notification view appends `ACTION_DONE` only when not under a rule.
- Preset `data/study-presets/uj1-shortfall-lena.json` "UJ-1 shortfall, 15 Oct (Lena)" with setup step `{"type":"mandate","template":"SAFETY_CUSHION","maxPerAction":"300.00","maxPerMonth":"600.00","floor":"2000.00"}`. `StudyPresets` admits only type `mandate` with a known template and bounds that pass `RuleBounds` validation (startup fails otherwise); `PrepareStudyUseCase` creates the rule via `Actions.createRule` after setting the date, before monitoring.
- Customer text says "rule", never "Mandate".
- Decisions (Commc, 2026-10-27): Lena's fixture stays; her Done-for-you text reads "I moved 90.00 EUR …" (AC's 200 EUR is illustrative). The preset summary is the real run: "3 notifications (1 For your information, 1 Recommendation, 1 Done for you), 1 rule active"; the emergency-fund note is not suppressed.

**Never:** Undo or Revoke (9.3/9.4); an undo deadline in the text (9.3 adds it with the button); frontend changes beyond what the existing Done-for-you chip renders; LLM calls; deleting rows.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Lena 15 Oct, rule 300/600/2,000 | shortfall transfer from overnight (5,700) | L3, `EXECUTED`, `mandate_id` set, two `BANK_PORT` legs, Done-for-you note | N/A |
| Above per transfer | amount 350 | L2, "…350.00 EUR is more than your rule allows (300.00 EUR per transfer)." | N/A |
| Month used up | 550 executed this month, amount 90 | L2, month reason | N/A |
| Floor | overnight after < floor | L2, floor reason naming the overnight deposit | N/A |
| No rule / other template | top-up rule only, shortfall Action | L2 `no_covering_mandate` | N/A |
| Reset | executed Action with `mandate_id` | persona reset deletes actions then mandates | N/A |

</frozen-after-approval>

## Code Map

- `actions/application/StoredMandates.java`, `MandateLookup.java` -- `covering` gets source/target ids and `asOf`; returns `MandateBounds` incl. month sum.
- `actions/adapter/MandateRepository.java` -- `activeOf(scope, template)`, `executedSum(scope, mandateId, YearMonth)`; `ActionRepository.insert` overload with `mandateId`; `summaries` reads `mandate_id IS NOT NULL`.
- `actions/domain/{MandateBounds,GateContext,AutonomyGate,GateReasons,ActionState}.java` -- bounds (id, template, maxPerAction, maxPerMonth, floor, movedThisMonth), `sourceAfter`, month check, reasons, L3 → `EXECUTED`.
- `actions/application/ActionService.java` -- `gate(...)` passes accounts; `propose` L3 branch books via `bookings` (reuse confirm's legs body); `gateContextBody`.
- `core/domain/GateMeasures.java`, `core/application/CoreGatewayService.gateMeasures` (l.356) -- `sourceAfter`, overnight-as-target. No figure key changes.
- `actions/ActionSummary.java` (+`underRule`), `monitoring/application/ShowNotificationsUseCase.java` l.80.
- `monitoring/domain/{DetectedTrigger.PlannedNotification (+done Text),TriggerDetection (shortfall l.100, top-up l.138),NotificationTemplates}.java`, `monitoring/application/MonitoringService.java` l.115 (pick done text on `EXECUTED`).
- `research/application/{StudyPresets,PrepareStudyUseCase}.java`, `data/study-presets/uj1-shortfall-lena.json`.
- Tests: `AutonomyGateTest`, `ActionServiceTest`, `MonitoringServiceTest`, `StudyPresetsTest`, `PrepareStudyUseCaseTest`/research controller test, `ActionResetter`/reset test, `ActionStateTest`.

## Tasks & Acceptance

**Execution:**
- [x] `core` -- `sourceAfter` + overnight target fix in `gateMeasures` -- floor on the source.
- [x] `actions/domain` -- bounds, gate month check, reasons, `proposedAt(L3)` -- L3 path.
- [x] `actions/adapter`, `StoredMandates`, `ActionService` -- covering lookup, L3 execution, `mandate_id`, audit.
- [x] `monitoring` -- done texts, done-text selection, `underRule` in the view.
- [x] `research` + preset JSON -- `mandate` setup step.
- [x] tests for the I/O matrix, preset summary, reset with `mandate_id`, `proposedAt(L3)` guard.

**Acceptance Criteria:**
- Given Lena's preset, when it is prepared, then a rule is active, the shortfall Action is `EXECUTED` at L3 with `mandate_id`, two `BANK_PORT` legs exist, the Done-for-you Notification is in the same transaction, and the summary matches the decided wording.
- Given any Action type outside the L3 set with a covering-looking rule, then it stays at most L2.

## Implementation Notes

- Workflow deviations (lean loop, memory build-loop-economy): implemented inline instead of a dispatched implementation subagent; review ran as one combined sonnet reviewer instead of the three layers.
- `CoreGateway.gateMeasures` takes the target account too (overnight-as-target fix). `sourceAfter` is `null` when the source is neither the current account nor the overnight deposit; a covering rule's floor then counts as failed.
- `MandateBounds` carries id, template, both limits, floor and `movedThisMonth`; `GateContext.emergencyFundAfter` became `sourceAfter`; the audit context keeps `emergencyFundAfter` (from `overnightAfter`) and adds `sourceAfter`, rule id, `maxPerMonth`, `movedThisMonth`.
- The "Why?" text of `amount_above_mandate_bounds` now names per transfer, per month and the source account (was overnight only).
- `RuleTexts` joined the `actions :: types` named interface so `StudyPresets` can validate bounds at startup; `research` now declares `actions :: types`.
- ActionType has only the two L3-eligible types, so "outside the L3 set" is covered by the gate table rows (insurance/legal class, investment) with a covering rule, plus `StoredMandates` matching by template action type.

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Route / evidence |
|---|---------|---------|------------------|
| 1 | Month-cap read-then-write race between concurrent gate runs | medium | patch: `MandateRepository.activeOf` locks the rule row `FOR UPDATE`, so gate runs under the same rule serialise. |
| 2 | Confirmed transfers have no `mandate_id`, so they don't count toward the month cap | false (as specified) | The frozen spec says "Confirm never sets `mandate_id`"; the month sum counts only rule-executed Actions. Rejected: the fix would edit the spec. |
| 3 | Boundary equality and `sourceAfter == null` untested | false | `AutonomyGateTest` has rows for "amount equal to mandate max", "at the floor after", "month exactly reached" (all L3) and "source neither current nor overnight" (L2). |
| 4 | EF_TOP_UP L3 path, revoked rule and account mismatch untested | low | patch: added `withinATopUpRuleFineTopsUpTheEmergencyFundItself`. Revoke comes with 9.4; `createRule` always uses the template's accounts, so there is no mismatch to reach. |
| 5 | Done-text swap and `underRule` suppression have no direct test | false | `StudyResearchControllerTest.lenaPresetSetsUpHerRuleAndFineMovesTheMoneyItself` checks the stored Done-for-you title and body through `GET /api/notifications`, including the absence of "You confirmed it". The proposal-text path stays covered by the existing monitoring tests. |
| 6 | A preset with two steps for one template passes startup, then fails in prepare | low | patch: `StudyPresets` rejects a second step for the same template, with a test. A missing overnight account is caught by `createRule`'s 400. |
| 7 | The floor reason is misleading when `sourceAfter` is null | false | Unreachable: a covering rule's source is always the current account or the overnight deposit, so `sourceAfter` is set whenever a rule covers. |
| 8 | No index on `actions.mandate_id` | low, rejected | Per-persona tables stay tiny, and the fix adds a migration. |

## Verification

**Commands:**
- `cd backend && ./mvnw -q test -Dtest='AutonomyGateTest,ActionServiceTest,MonitoringServiceTest,StudyPresets*,PrepareStudy*'` -- expected: green
- `cd backend && ./mvnw -q verify -Dfine.it.required=true` -- expected: all green before commit
