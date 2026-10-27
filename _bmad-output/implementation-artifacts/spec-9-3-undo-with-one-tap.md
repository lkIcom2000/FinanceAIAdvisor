---
title: 'Story 9.3: Undo with one tap'
type: 'feature'
created: '2026-10-27'
status: 'done'
baseline_commit: '1b11107'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-9-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Fine executes transfers within a rule (9.2), but Lena can't reverse one (FR-38 Undo, FR-7 Undo control).

**Approach:** `Actions.undo` books the reverse transfer via `BankPort` without the gate, stores a compensating Action linked by `undoes_id`, and sets the original to `UNDONE`, in one transaction. The Done-for-you Notification shows the deadline and an **Undo transfer** button; after the window it shows "Undo was possible until …".

## Boundaries & Constraints

**Always:**
- Undoable: the persona's Action in state `EXECUTED` with `mandate_id` set (rule-executed, L3), `undoes_id` null, and simulated date ≤ deadline. Deadline = the next business day (Mon–Fri, no holidays) after the Action's `as_of`, 5 pm; the date-only clock allows undo on the whole deadline date. Text format "Thu 16 Oct, 5 pm" (English).
- Undo (AD-10, AD-21, one transaction, rows locked like confirm): `bookings.transfer(target → source, amount, asOf, "Undo of a Fine rule transfer")` even if the target no longer covers it (decided Commc 2026-10-27); insert a compensating row (same type/class/amount/trigger, accounts swapped, `EXECUTED`, original level, no criteria, no reason, no `mandate_id`, `undoes_id` = original); original → `UNDONE`; one `ACTION` Audit Record on the compensating id (`event: undo`, `undoesId`, amount, accounts, transfer legs).
- Rejections (`NotUndoableException` → 409 `ProblemDetail`): already undone "This transfer has already been undone."; window ended "Undo was possible until Thu 16 Oct, 5 pm."; anything else "This transfer can't be undone.". Foreign/unknown id → 404 like confirm.
- Month cap: the `UNDONE` original drops out of `executedSum`; the compensating row has no rule. A revoked rule does not block undo.
- Migration V23: state check adds `UNDONE`; `undoes_id UUID UNIQUE REFERENCES actions(id)`; `undoes_id` only on `EXECUTED` rows.
- `ActionSummary` gets `undoUntil` (deadline date for a rule-executed Action, else null). Notification view: `undoUntil` text and `undoable` (state `EXECUTED` and date ≤ deadline). Body suffix: undoable → "You can undo this until {deadline}."; `UNDONE` → "You undid this transfer, and Fine has moved the money back." Status `UNDONE` → chip "Undone".
- `POST /api/actions/{id}/undo` → 200 `{message}`: "Transfer undone. {amount} is back in your overnight deposit." (top-up: "…current account."), amount as `90.00 EUR`.
- Frontend: Done-for-you item with `undoUntil`: undoable → **Undo transfer** button with "until {deadline}", no dialog, snackbar with the message, then refresh; else text "Undo was possible until {deadline}". A 409 shows its detail in the snackbar and refreshes.
- `RuleTexts.UNDO_AND_STOP` says "until 5 pm on the next business day".

**Never:** a redo; an undo for confirmed (L2) transfers or for a compensating Action; gate or bound checks on the undo; deleting rows; Revoke (9.4).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Lena Wed 15 Oct, undo on 15 or 16 Oct | rule transfer 90.00 EUR | reverse legs, compensating row, original `UNDONE`, chip "Undone" | N/A |
| Friday transfer | undo on Mon | allowed (deadline Mon) | N/A |
| Window ended | undo on Fri 17 Oct | — | 409 "Undo was possible until Thu 16 Oct, 5 pm." |
| Repeated undo | original `UNDONE` | — | 409 "already been undone" |
| Confirmed L2 transfer / compensating row | undo | — | 409 "can't be undone" |
| Foreign persona's id | undo | — | 404 |

</frozen-after-approval>

## Code Map

- `resources/db/migration/V23__action_undo.sql`; `actions/domain/{ActionState,UndoWindow}.java`; `actions/{Actions,ActionSummary,ActionUndo,NotUndoableException}.java`.
- `actions/adapter/ActionRepository.java` -- insert with `undoesId`, `undoable(scope,id)` facts, `markUndone`, summaries with `as_of`.
- `actions/application/ActionService.java` -- `undo`.
- `monitoring/{application/UndoActionUseCase (in ConfirmActionUseCase), application/ShowNotificationsUseCase, domain/NotificationStatus, domain/NotificationTemplates, web/ActionController, web/NotificationController}`.
- Frontend: `notification-list.component.{ts,html,scss,spec.ts}`, generated API client.

## Tasks & Acceptance

**Execution:**
- [x] migration + domain (`UNDONE`, `UndoWindow`) + `RuleTexts` sentence
- [x] repository + `ActionService.undo` + exception
- [x] monitoring view, texts, endpoint, OpenAPI snapshot, client
- [x] frontend button, late text, snackbar
- [x] tests for the I/O matrix, `UndoWindow`, notification view, frontend

**Acceptance Criteria:** as in `epics.md` Story 9.3, with Lena's amount 90.00 EUR.

## Implementation Notes

- Workflow deviations (lean loop): implemented inline; one combined sonnet reviewer plus a Blind Hunter (bank bookings are persona data).
- Lena's preset date 15 Oct 2026 is a Thursday, so her deadline reads "Fri 16 Oct, 5 pm" (the AC's "Thu 16 Oct" is the 2025 calendar).
- The undo deadline sentence is appended when the Notification is shown, not stored, so it disappears once the window ends; `ActionSummary.undoUntil` carries the date, the view formats it via `UndoWindow.text`.
- `ActionUndo.backInOvernight` comes from the rule template of the Action type (1:1 today) and is computed before the booking.
- Foreign-persona undo (404) is covered by `PersonaIsolationTest`, which probes every endpoint.

## Review Triage Log

| # | Finding | Verdict | Route / evidence |
|---|---------|---------|------------------|
| 1 | Month cap may still count the undone amount | false | `executedSum` filters `state = 'EXECUTED'` and `mandate_id`; the compensating row has none; tested in `lenaUndoesTheRuleTransferOnTheDeadlineDate` (rule 300/300). |
| 2 | Booking before `markUndone` could double-reverse if BankPort used its own tx | false | `BankPort` runs in the caller's transaction (its contract); use case is one `@Transactional`. |
| 3 | `backInOvernight` lookup after booking; template mapping implicit | low | patch: computed before booking; top-up undo test pins the mapping. |
| 4 | `undoable` ignores `asOf < as_of` (date rewind) | false | Notifications are listed only up to the simulated date, and their date equals the Action's `as_of`. |
| 5 | `summaries` lacks `undoes_id IS NULL` | low, rejected | Compensating rows never carry a `mandate_id`. |
| 6 | Constraint name `actions_state_check` assumed | false | Default name for V18's inline check; migration ran in Testcontainers. |
| 7 | Test gaps: foreign persona, Friday→Monday, top-up undo | low | patch: top-up undo test; Friday→Monday in `UndoWindowTest`; foreign id in `PersonaIsolationTest`. |
| 8 | In-flight button has no busy signal | low | patch: `aria-busy`. |

## Verification

- `cd backend && ./mvnw -q test -Dtest='ActionServiceTest,UndoWindowTest,NotificationStatusTest,StudyResearchControllerTest,ActionControllerTest'`
- `cd backend && ./mvnw -q verify -Dfine.it.required=true`; `cd frontend && npm test && npm run build`
