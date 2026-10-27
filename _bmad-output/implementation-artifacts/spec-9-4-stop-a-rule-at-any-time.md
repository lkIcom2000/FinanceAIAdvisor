---
title: 'Story 9.4: Stop a rule at any time'
type: 'feature'
created: '2026-10-27'
status: 'done'
baseline_commit: '176be50'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-9-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Lena can start a rule (9.1) and Fine acts under it (9.2), but she can't stop it (FR-38 Revoke, FR-7 Revoke control); the Revoke button is disabled.

**Approach:** `Actions.revokeRule` sets `revoked_at` to the simulated date (never deletes) and writes an Audit Record, in one transaction. Revoke on the rule card opens a dialog naming the last open undo deadline; stopped rules move to a collapsed "Stopped rules" list with their stop date. The gate needs no change: `MandateRepository.activeOf` already ignores revoked rules, so a later Action gets at most L2.

## Boundaries & Constraints

**Always:**
- `POST /api/rules/{id}/revoke` → 200 `RuleView` of the stopped rule. Unknown/foreign id → 404; already stopped → 409 "This rule has already been stopped.". The row is locked `FOR UPDATE`; `revoked_at = asOf`.
- One `ACTION` Audit Record on the rule id: `event: rule_revoked`, `mandateId`, `template`, `revokedAt`.
- `GET /api/rules` returns active rules (oldest first) then stopped rules (latest stop first). `RuleView` gets `stoppedOn` (date, absent while active) and `undoUntil` (text "Fri 16 Oct, 5 pm", absent unless the rule's latest `EXECUTED` Action with `as_of ≤ asOf` is still in its `UndoWindow`).
- Dialog (UX-DR24): title "Stop this rule?"; body "Fine won't move money under this rule anymore. Transfers already made stay as they are; you can still undo the last one until {undoUntil}." — without `undoUntil` the body ends after "stay as they are.". Buttons **Keep it** (closes, no change) and **Stop this rule** (disabled + aria-busy in flight). Success: snackbar "Your rule is stopped.", list reloads. A 409/failure: snackbar with the detail (or a generic text), list reloads.
- Home: active cards keep Revoke (enabled); stopped rules in a `<details>` "Stopped rules (n)" listing summary and "Stopped on {date}", no Revoke. A new rule of the same template can be started after stopping (partial unique index).
- Undo of transfers under a stopped rule keeps working (9.3).

**Never:** deleting a rule; restarting a stopped rule; stopping from chat.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Lena stops her rule after a 90.00 EUR transfer on 15 Oct | revoke on 15 Oct | `revoked_at` 15 Oct, audit `rule_revoked`, dialog named "Fri 16 Oct, 5 pm" | N/A |
| Next shortfall after stop | propose | L2, criterion `NO_COVERING_MANDATE`, no booking | N/A |
| Stop again | revoked rule | — | 409 |
| Foreign / unknown id | revoke | — | 404 |
| Keep it | dialog | closes, no request | N/A |

</frozen-after-approval>

## Code Map

- `actions/{Actions,Rule,RuleAlreadyStoppedException}.java`, `actions/adapter/MandateRepository.java` (`all`, `findForUpdate`, `revoke`, `lastExecuted`), `actions/application/ActionService.java` (`rules`, `revokeRule`).
- `monitoring/application/RulesUseCase.java`, `monitoring/web/RuleController.java`.
- Frontend: `revoke-rule-dialog.component.{ts,html,scss}`, `rule-setup.service.ts`, `home-tab.component.{ts,html,scss,spec.ts}`, generated API client.

## Tasks & Acceptance

**Execution:**
- [x] repository, `revokeRule`, `rules` with stopped rules and `undoUntil`
- [x] use case, endpoint, OpenAPI snapshot, client
- [x] dialog, service, stopped list
- [x] tests: `ActionServiceTest` (revoke → L2, audit, twice → exception, undo deadline), `RuleControllerTest` (endpoint, 404/409), frontend

**Acceptance Criteria:** as in `epics.md` Story 9.4.

## Implementation Notes

- Workflow deviations (lean loop): implemented inline; defaults chosen without a scope round (endpoint shape, 409 on a second stop, stopped list order); one combined sonnet reviewer, no Blind Hunter (no bookings, persona scoping covered by `PersonaIsolationTest` and the controller test).
- `RuleSetupService.started` became `changed` (start and stop both reload "Your rules"). Without active rules but with stopped ones, Home says "You have no active rules.".
- A simulated date before the rule's start stops it on its start date (V22 check `revoked_at >= created_at`).
- The dialog's `undoUntil` is the list's snapshot; a date change while the dialog is open is not reflected (accepted).

## Review Triage Log

| # | Finding | Verdict | Route / evidence |
|---|---------|---------|------------------|
| 1 | `asOf` before `created_at` breaks the V22 check → 500 | medium | patch: clamp to the start date; `aRuleStoppedOnADateBeforeItsStartIsStoppedOnItsStartDate`. |
| 2 | Dialog deadline is a snapshot of the list | low, accepted | Simulated date doesn't move while the dialog is open. |
| 3 | `revoke()` false branch unreachable under `FOR UPDATE` | low, kept | Defensive. |
| 4 | No API test of the `undoUntil` text | low, rejected | Date in `ActionServiceTest`, format in `UndoWindowTest`, mapping is one call. |
| 5 | No service-level foreign-scope test | low, rejected | `PersonaIsolationTest` + Marco 404 in `RuleControllerTest`; every query takes the scope. |
| 6 | Stopped rules order has no final key | low | patch: `id` added. |
| 7 | `RuleSetupService.revoke` untested | medium | patch: `rule-setup.service.spec.ts`. |
| 8 | Keep it not tested | low, rejected | `mat-dialog-close`, no handler. |

## Verification

- `cd backend && ./mvnw -q test -Dtest='ActionServiceTest,RuleControllerTest'`
- `cd backend && ./mvnw -q verify -Dfine.it.required=true`; `cd frontend && npm test && npm run build`
