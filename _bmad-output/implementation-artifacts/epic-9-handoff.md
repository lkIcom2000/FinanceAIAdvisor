# Epic 9 Handoff

## State (2026-10-27, after Story 9.1)

- Epic 9 context: `epic-9-context.md`. Epic 8 state and leftovers: `epic-8-handoff.md` (joint Epic 8 + 9 retro at the end of Epic 9).
- Story 9.1 done (spec `spec-9-1-set-up-a-rule-with-explicit-limits.md`, sprint-status `review`): V22 `mandates` (`template` `SAFETY_CUSHION|EMERGENCY_FUND_TOP_UP`, `max_per_action`, `max_per_month`, `floor` = source account minimum after a transfer, `source/target_account_id`, `created_at`/`revoked_at` as simulated DATEs, partial unique index one active rule per template) and `actions.mandate_id` (FK, nullable, unset until 9.2). `actions.adapter.MandateRepository`; `ActionResetter` deletes actions then mandates; `PersonaOwnedTablesTest` entry.
- Domain: `actions.domain.RuleTemplate` (action type, title, `fromOvernight`), `RuleBounds`, `RuleTexts` (summary, will/won't, undo-and-stop, source, limit checks, text EUR format). Core: `core.domain.RuleLimits` (function `ruleLimits`), `CoreGateway.ruleLimits` (cushion: lowest forecast balance rounded up to 100 EUR, ×2 per month, floor = emergency-fund target; top-up: `emergencyFundTopUp` rounded up, floor 0).
- API: `Actions.rules/ruleSetup/checkRule/createRule` (`InvalidRuleException` 400, `RuleAlreadyActiveException` 409); `StoredMandates` replaces `NoMandates` (`covering` still empty, `active` counts non-revoked). Use case + controller in `monitoring` (`RulesUseCase`, `RuleController`): `GET /api/rules`, `GET /api/rules/setup`, `POST /api/rules/check`, `POST /api/rules` (201, `ACTION` Audit Record `event: rule_created`, Mandate id in the action-id slot).
- Frontend: `RuleSetupDialogComponent` (own 3-step indicator, server check before step 3), root `RuleSetupService` (opens it from Home and the chat chip "Set up a rule", snackbar, `started` counter), rule cards in the Home tab (Revoke visible, disabled until 9.4).
- verify 861/0/0, frontend 131. Commit: see `git log` "feat: Story 9.1".

## State after Story 9.2 (2026-10-27)

- Spec `spec-9-2-fine-acts-within-my-rule-and-tells-me-immediately.md` (sprint-status `review`). `StoredMandates.covering(scope, action, source, target, asOf)`: active rule of the template (`MandateRepository.activeOf`, row locked `FOR UPDATE`) on the same accounts; `MandateBounds` (id, template, limits, floor, `movedThisMonth` = `executedSum` of the month's EXECUTED Actions under the rule).
- Gate: per transfer, then per month, then `sourceAfter < floor` (`GateMeasures.sourceAfter`; `gateMeasures` now takes the target too, `overnightAfter` adds the amount when overnight is the target). `proposedAt(L3)` = `EXECUTED`; `propose` books via `BankPort` ("Fine rule transfer"), inserts with `mandate_id`, audit has `transfer` + `mandateId`.
- Monitoring: `PlannedNotification.done` text stored when the Action is EXECUTED at proposal ("I moved 90.00 EUR to your current account"); `ActionSummary.underRule` stops the "You confirmed it" suffix.
- Preset `uj1-shortfall-lena` (rule 300/600/2,000) → "3 notifications (1 For your information, 1 Recommendation, 1 Done for you), 1 rule active". `RuleTexts` is in `actions :: types`.
- verify 874/0/0. Commit: see `git log` "feat: Story 9.2".

## State after Story 9.3 (2026-10-27)

- Spec `spec-9-3-undo-with-one-tap.md` (sprint-status `review`). V23: state `UNDONE`, `actions.undoes_id` (UNIQUE, self-FK, only on `EXECUTED`).
- Domain `actions.domain.UndoWindow` (`types`): deadline = next Mon–Fri after `as_of`, undo allowed through the whole deadline date, text "Fri 16 Oct, 5 pm".
- `Actions.undo` → `ActionUndo(amount, backInOvernight)`; `NotUndoableException` (409 texts: already undone / "Undo was possible until …" / can't be undone). Only `EXECUTED` + `mandate_id` + not a compensating row; reverse booking "Undo of a Fine rule transfer" without gate or cover check (Commc decision); compensating row (accounts swapped, original level, no rule, `undoes_id`); original `UNDONE`; audit `event: undo` on the compensating id. Revoked rule does not block undo.
- `ActionSummary.undoUntil`; Notification API `undoUntil` (text) + `undoable`; body suffix "You can undo this until …" / "You undid this transfer, and Fine has moved the money back."; status `UNDONE`. `POST /api/actions/{id}/undo` → `{message}` (in `ConfirmActionUseCase`/`ActionController`).
- Frontend: Undo transfer button (no dialog, disabled + aria-busy in flight, snackbar, refresh), "Undo was possible until …" text. `RuleTexts.UNDO_AND_STOP` says "until 5 pm on the next business day".
- verify 889/0/0, frontend 135. Commit: see `git log` "feat: Story 9.3".

## State after Story 9.4 (2026-10-27)

- Spec `spec-9-4-stop-a-rule-at-any-time.md` (sprint-status `review`). `Actions.revokeRule` (row `FOR UPDATE`, `revoked_at` = asOf clamped to `created_at`, audit `event: rule_revoked`, `RuleAlreadyStoppedException` → 409, foreign/unknown → 404). Gate unchanged: `activeOf` ignores revoked rules → L2 `NO_COVERING_MANDATE`.
- `Actions.rules` returns active (oldest first) then stopped (latest stop first); `Rule.stoppedOn`, `Rule.undoUntil` (latest EXECUTED rule Action's deadline if open, `MandateRepository.lastExecuted`). API `RuleView.stoppedOn/undoUntil` (text), `POST /api/rules/{id}/revoke` → `RuleView`.
- Frontend: `RevokeRuleDialogComponent` ("Stop this rule?", Keep it / Stop this rule), `RuleSetupService.revoke`, `started` renamed `changed`; Home "Stopped rules (n)" `<details>`.
- verify 893/0/0, frontend 143. Commit: see `git log` "feat: Story 9.4".

## Next

- Epic 9 stories are all built (9.1–9.4). Epic 9 end: batched follow-up review, epic-end compose check, joint Epic 8 + 9 retro.

## Checklist per story (lean loop, memory build-loop-economy)

1. Read this note, the epic context and the story ACs (`planning-artifacts/epics.md` §Epic 9); targeted code reads.
2. Spec ≤ 1 page; ask scope questions batched before writing.
3. Implement; affected tests only (`JAVA_HOME=C:\Users\commc\.jdks\temurin-25`, wrapper in `backend/`: `./mvnw -q`; filter JSON log lines with `grep -v '^{'`). API change: `./mvnw -q test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true`, then `npm run api:generate`. Run prettier only on new files (it reformats untouched ones).
4. Diff (exclude generated files) → one combined sonnet reviewer; stories touching persona data also a separate Blind Hunter.
5. Fix, then full `verify -Dfine.it.required=true` (+ frontend test/build when touched); commit; update this note; /clear.
