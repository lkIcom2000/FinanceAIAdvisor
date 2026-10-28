---
title: 'Epic 9 retro A3: split rule lifecycle out of ActionService'
type: 'refactor'
created: '2026-10-28'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `ActionService` (574 lines) carries both the Action lifecycle (propose, review, confirm, undo, gate) and the rule (Mandate) lifecycle (list, setup, check, create, revoke). Epic 10 adds Action types, so the class would keep growing (Epic 8/9 retro F1, item A3).

**Approach:** Move the rule lifecycle into its own module API `de.financetool.actions.Rules` (methods `activeRules`, `rules`, `revokeRule`, `ruleSetup`, `checkRule`, `createRule`, Javadoc moved verbatim from `Actions`) implemented by a package-private `RuleService` in `actions.application`, and point the callers at it. Pure move: behavior, Audit Records, transactions and exceptions unchanged.

</frozen-after-approval>

## Implementation Notes

- Naming: interface `Rules` / impl `RuleService`, parallel to `Actions` / `ActionService` and the public `Rule*` types (the retro's `MandateService` was an example).
- Shared helpers `requireSamePersona` and `account(snapshot, id)` become package-private static in `ActionService` and are reused by `RuleService` instead of copied.
- `ActionService` loses the `MandateRepository` field (only rules used it); it keeps `MandateLookup` for the gate.
- Callers: `monitoring.application.RulesUseCase`, `research.application.PrepareStudyUseCase`; tests `ActionServiceTest`, `RuleControllerTest` get `Rules` injected for their rule calls.
- Verify: `mvn verify` in `backend` (Docker Desktop running).
- Result: `ActionService` 574 → 398 lines, `RuleService` 218 lines. Two slips while moving fixed before verify (the `@Override` of `propose` and the `List` import); `RuleControllerTest` needs `this.rules` because a local `rules` shadows the field.
- verify green (995 tests, 0 failures, 0 skipped). Review skipped: pure move (as Story 13.3).
