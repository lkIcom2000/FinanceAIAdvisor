---
title: 'Story 8.4: Confirm an Action exactly, and see why it needs my OK'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'ba1dd25'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A `NEEDS_YOUR_OK` Notification (8.3) has no control: Lena cannot see why Fine needs her OK, nor confirm the exact transfer (FR-39, FR-40, UX-DR20, UX-DR25).

**Approach:** The gate's capping criterion becomes a customer reason text stored on the Action row; the Notification list carries `actionId` and `reason`. A review read builds the L2 dialog (sentence, accounts with balances before/after from the Core, reason, "Why?"); `ActionService.confirm` re-runs gate and checks on the same row and executes via `BankPort` with the Audit Record in one transaction.

## Boundaries & Constraints

**Always:**
- V21: `actions.reason TEXT NULL`. `propose` writes the reason for every capped Action; a failed confirm overwrites it. Reason = template of the first capping criterion (table order) with the user's own numbers; UX table texts (EXPERIENCE.md §Autonomy & Control); `not_in_l3_set` "This needs your OK because Fine doesn't make this kind of transfer on its own."; failures: rule parameter missing, source account not covering the amount ("…holds only X now"), account gone. "Why?" = fixed explanation per criterion, computed on read. No internal vocabulary; amounts `200.00 EUR`.
- `Actions.states` becomes `summaries(scope, ids)` → `state, reason`; Notification item gains `actionId?`, `reason?` (ACTION outcome only).
- `GET /api/actions/{id}/review` (persona session): `sentence` ("Move 200.00 EUR from Overnight deposit to Current account"), `amount`, `from`/`to` (`name`, `before`, `after` via new `CoreGateway.transferBalances`, nothing stored), `reason`, `why`. Only `AWAITING_CONFIRMATION` transfer Actions (both accounts set); otherwise 409. Foreign/unknown/malformed id → 404 before anything else.
- `POST /api/actions/{id}/confirm` (no body): row locked (`FOR UPDATE`); state must be `AWAITING_CONFIRMATION` (else 409); re-check at the persona's simulated date: Rule Parameter present, gate level ≥ L2, both accounts exist, source balance ≥ amount. Success → `BankPort.transfer` (booking date = asOf), row `EXECUTED` with the new decision, `ACTION` Audit Record (event `confirm`, outcome, transfer ids), one transaction; answer `{done: true, amount}`. Failure → state stays `AWAITING_CONFIRMATION`, criteria and reason updated, Audit Record outcome `refused`; answer `{done: false, reason, why}`. Never a new Action row.
- Check Chain (FR-25) is not run for Actions; the "checks" are state, Rule Parameter and account cover (Commc, 2026-10-27).
- Frontend: expanded `NEEDS_YOUR_OK` item shows the reason; the item shows **Review and confirm** (opens the L2 dialog). Dialog: sentence, from/to with before → after, reason with inline "Why?" toggle, buttons **Confirm transfer of 200.00 EUR** / **Not now**. Success closes, snackbar "Done. 200.00 EUR moved.", immediate feed refresh (item flips to "Done for you"). Failure: dialog shows the reason (and Why?) with only **Close**; feed refresh shows the updated reason.

**Never:** a second Action row; executing `insurance`/`legal` or non-transfer Actions; Undo/L3 (Epic 9); LLM text; client-side computing of balances or reasons.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Lena confirms | shortfall Action `AWAITING_CONFIRMATION`, overnight covers | transfer booked, `EXECUTED`, Notification "Done for you" | N/A |
| Double click | second confirm after success | 409, nothing booked | ProblemDetail |
| Cover gone | overnight below amount at confirm | `done:false`, reason "…holds only X now", stays Needs your OK | N/A |
| Foreign id | review/confirm with another persona's id | 404, nothing changed | ProblemDetail |

</frozen-after-approval>

## Code Map

- `actions/domain/GateReasons.java` (new) -- reason/why texts per criterion and check failure.
- `actions/application/ActionService.java` -- reason in `propose`; `review`, `confirm`; `ConfirmActionUseCase` (context, fixture version, transaction); `actions/web/ActionController.java`.
- `actions/adapter/ActionRepository.java` -- `reason`, `findForUpdate`, `find`, `updateAfterCheck`; `summaries`.
- `core/CoreGateway.java` + `CoreGatewayService` -- `transferBalances`.
- `monitoring/application/ShowNotificationsUseCase`, `monitoring/web/NotificationController` -- `actionId`, `reason`.
- `db/migration/V21__action_reason.sql`; `actions/package-info.java` + `personas`.
- Frontend: api regen, `notification-list.component.*`, new `pages/workspace/confirm-action-dialog.component.*`, `NotificationsService.refresh()`.

## Tasks & Acceptance

**Acceptance Criteria:**
- Given an Action in `AWAITING_CONFIRMATION`, when Lena expands its Notification or opens Review and confirm, then the capping reason shows with her numbers, never the criterion name.
- Given the dialog opens, then sentence, accounts with balances before/after, reason, Why?, **Confirm transfer of X EUR** and **Not now** show.
- Given Confirm, then on success the transfer is booked with the Audit Record in one transaction, the dialog closes, the item flips to Done for you, the snackbar says "Done. X EUR moved."; no new Action row. If the re-check fails, the dialog shows the reason and only Close; the item stays Needs your OK with the updated reason.

## Implementation Notes

- Workflow deviation (lean loop, memory build-loop-economy): implemented inline, no implementation subagent.
- Module placement: `review`/`confirm` are `Actions` module API methods (implemented by `ActionService`); the use case (`monitoring.application.ConfirmActionUseCase`) and the controller (`monitoring.web.ActionController`, paths `/api/actions/{id}/...`) live in `monitoring`, because the fixture version for the Audit Record comes from `personas`, and `actions -> personas` is not in the AD-12 graph. API records `ActionReview`, `ActionConfirmation`, `ActionSummary`, `NotAwaitingConfirmationException` in the `actions` package; `Actions.states` became `summaries`.
- Reason choice deviates from the frozen text "first capping criterion": the deciding criterion is the first one with the lowest cap (`AutonomyGate.cap`), so an L1-capped Action never reads "needs your OK"; a failed check that is no gate criterion decides first.
- Balances before/after: new `CoreGateway.transferBalances` (function `accountBalance`, nothing stored); the web layer sends them as 2-decimal strings for the figure pipe. `TransferReview` carries `amountText` instead of a numeric amount.
- A refused re-check keeps the stored level (state stays `AWAITING_CONFIRMATION`); only criteria and reason change. Booking reference "Transfer you confirmed in Fine", booking date = persona asOf.
- `confirm` locks all of the persona's Action rows (`FOR UPDATE`) before reading the snapshot, so two confirmations of one persona serialise on the balance check.
- Frontend: dialog `pages/workspace/confirm-action-dialog.component`; cannot be dismissed while the POST runs; a lost confirm answer says it is unclear whether the transfer was made; every close refreshes the feed (`NotificationsService.refresh()`).

## Review Triage Log

Layers: one combined sonnet reviewer (edge-case, verification-gap, spec lenses) and a Blind Hunter (persona data, money movement).

| # | Source | Finding | Verdict | Route / evidence |
|---|--------|---------|---------|------------------|
| 1 | combined | Two Actions on one source account can both pass the cover check | medium | patch: persona-wide row lock in `findForUpdate` |
| 2 | combined | Refusal branches (rule parameter, gate below L2), non-transfer 409 untested | medium | patch: two `ActionServiceTest` cases (mocked Core) incl. self-transfer and unknown id |
| 3 | combined | Esc/backdrop closes dialog during POST, success snackbar lost | medium | patch: `disableClose` + disabled Not now while busy; spec |
| 4 | both | 5xx on confirm shows the load-failure text | medium | patch: `CONFIRM_FAILED` text; spec |
| 5 | blind | Source equals target not rejected | medium | patch: 409 guard (BankPort already refused it, as a 500) |
| 6 | blind | Booking not atomic with audit if BankPort had its own tx | false | `BankPort` runs in the calling use case's transaction (its contract) |
| 7 | combined | Sentence assertion built from the response's own amount | low | patch: compared with `NotificationTemplates.eur` of the stored amount |
| 8 | both | Refusal keeps the old level beside new criteria | low | rejected: state stays awaiting confirmation (AC); the Audit Record holds the full re-check |
| 9 | blind | 409 text after a lost double-click reads like a cancel | low | rejected: list refresh on close shows "Done for you" |
| 10 | blind | Booking back-dated if the simulated date moved before the Action's date | low | deferred: the clock moves forward in studies; reset deletes Actions |
| 11 | blind | Unknown criterion in `GateReasons` throws | low | rejected: all criteria are known constants, covered by `GateReasonsTest` |
| 12 | combined | Two `eur` formatters (`GateReasons`, `NotificationTemplates`) | low | rejected: `monitoring` may only see `actions :: types`; same format, both tested |
| 13 | combined | Further frontend specs (5xx, busy) | low | patch: one spec covers busy, Esc and 5xx |

## Verification

**Commands:**
- `./mvnw -q test -Dtest='ActionControllerTest,GateReasonsTest,ActionServiceTest,AutonomyGateTest,NotificationControllerTest,PersonaIsolationTest,ModulithTest,ArchitectureTest,MonitoringServiceTest,OpenApiSnapshotTest'` -- green
- `npm test -- --watch=false && npm run build` (frontend) -- green, 124 tests
- `./mvnw -q verify -Dfine.it.required=true` -- green, 836/0/0
