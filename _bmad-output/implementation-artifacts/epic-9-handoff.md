# Epic 9 Handoff

## State (2026-10-27, after Story 9.1)

- Epic 9 context: `epic-9-context.md`. Epic 8 state and leftovers: `epic-8-handoff.md` (joint Epic 8 + 9 retro at the end of Epic 9).
- Story 9.1 done (spec `spec-9-1-set-up-a-rule-with-explicit-limits.md`, sprint-status `review`): V22 `mandates` (`template` `SAFETY_CUSHION|EMERGENCY_FUND_TOP_UP`, `max_per_action`, `max_per_month`, `floor` = source account minimum after a transfer, `source/target_account_id`, `created_at`/`revoked_at` as simulated DATEs, partial unique index one active rule per template) and `actions.mandate_id` (FK, nullable, unset until 9.2). `actions.adapter.MandateRepository`; `ActionResetter` deletes actions then mandates; `PersonaOwnedTablesTest` entry.
- Domain: `actions.domain.RuleTemplate` (action type, title, `fromOvernight`), `RuleBounds`, `RuleTexts` (summary, will/won't, undo-and-stop, source, limit checks, text EUR format). Core: `core.domain.RuleLimits` (function `ruleLimits`), `CoreGateway.ruleLimits` (cushion: lowest forecast balance rounded up to 100 EUR, ×2 per month, floor = emergency-fund target; top-up: `emergencyFundTopUp` rounded up, floor 0).
- API: `Actions.rules/ruleSetup/checkRule/createRule` (`InvalidRuleException` 400, `RuleAlreadyActiveException` 409); `StoredMandates` replaces `NoMandates` (`covering` still empty, `active` counts non-revoked). Use case + controller in `monitoring` (`RulesUseCase`, `RuleController`): `GET /api/rules`, `GET /api/rules/setup`, `POST /api/rules/check`, `POST /api/rules` (201, `ACTION` Audit Record `event: rule_created`, Mandate id in the action-id slot).
- Frontend: `RuleSetupDialogComponent` (own 3-step indicator, server check before step 3), root `RuleSetupService` (opens it from Home and the chat chip "Set up a rule", snackbar, `started` counter), rule cards in the Home tab (Revoke visible, disabled until 9.4).
- verify 861/0/0, frontend 131. Commit: see `git log` "feat: Story 9.1".

## Next: Story 9.2

- `StoredMandates.covering`: active rule of `template.actionType()`; gate floor check must use the source account after the Action (today `GateContext.emergencyFundAfter` = overnight after; for `EF_TOP_UP` the source is the current account) and `max_per_month` (sum of the month's executed Actions under the rule); set `actions.mandate_id`.
- `ActionState.proposedAt(L3)` throws (deferred Epic 8): L3 → execute via `BankPort` + "Done for you" Notification in the same transaction.
- Preset "UJ-1 shortfall, 15 Oct (Lena)": `mandate` setup step in `StudyPresets` + dispatch in `PrepareStudyUseCase` → "2 notifications (1 Done for you, 1 Recommendation), 1 rule active".
- Add a reset test with an action carrying `mandate_id` (FK order).

## Checklist per story (lean loop, memory build-loop-economy)

1. Read this note, the epic context and the story ACs (`planning-artifacts/epics.md` §Epic 9); targeted code reads.
2. Spec ≤ 1 page; ask scope questions batched before writing.
3. Implement; affected tests only (`JAVA_HOME=C:\Users\commc\.jdks\temurin-25`, wrapper in `backend/`: `./mvnw -q`; filter JSON log lines with `grep -v '^{'`). API change: `./mvnw -q test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true`, then `npm run api:generate`. Run prettier only on new files (it reformats untouched ones).
4. Diff (exclude generated files) → one combined sonnet reviewer; stories touching persona data also a separate Blind Hunter.
5. Fix, then full `verify -Dfine.it.required=true` (+ frontend test/build when touched); commit; update this note; /clear.
