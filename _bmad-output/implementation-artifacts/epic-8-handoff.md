# Epic 8 Handoff

## State (2026-10-27, after Story 8.3)

- Epic 8 context: `epic-8-context.md`. Earlier epics: `epic-6-handoff.md` (Epics 4, 6, 7, Story 13.2).
- Story 8.1 done, commit `3e81510` (spec `spec-8-1-autonomy-gate-and-action-proposals.md`, sprint-status `review`): pure `actions.domain.AutonomyGate.decide(GateAction, GateContext, GateThresholds)` implements the FR-35 table (every failed criterion listed, lowest cap wins, `not_in_l3_set` for types outside the L3 set); `Actions.propose(context, scope, fixtureVersion, ProposedAction)` → `ActionService` is the only creator of `actions` rows (V18): one `BankReadPort.snapshot`, new `CoreGateway.gateMeasures` (no evidence stored), suitability via `ProfileCompleteness.strictMissing(..., "investment_decision")`, `NoMandates` (Epic 9 seam), row + `ACTION` Audit Record in the caller's transaction (`MANDATORY`). Missing emergency-fund Rule Parameter → `ESCALATED`, level null, `rule_parameter_missing`. `ActionResetter`, `PersonaOwnedTablesTest` entry. verify 783/0/0.
- 8.1 deviations (spec Implementation Notes): gate takes `GateThresholds` (domain may not see `shared.config`); `GateMeasures` carries Data Confidence as plain fields; Core returns `emergencyFundTarget = null` for a missing Rule Parameter.
- Story 8.2 done, commit `b3cf679` (spec `spec-8-2-monitoring-run-at-a-simulated-date-with-all-mvp-triggers.md`): `MonitoringService.run`, tables `triggers`/`notifications` (V19), templated texts in `monitoring.domain.NotificationTemplates`, `POST /api/research/personas/{id}/monitoring/run`. verify 761/0/0.
- Story 8.3 done (spec `spec-8-3-notifications-on-the-home-tab.md`, sprint-status `review`): `GET /api/notifications`, `POST /api/notifications/{id}/read` (`monitoring.web.NotificationController` over `monitoring.application.ShowNotificationsUseCase`), status from `monitoring.domain.NotificationStatus.of(outcome, actionState)`, new reads `Actions.states(scope, ids)` and `audit.EvidenceReader.figures(scope, provIds)`, V20 `read_at`/`question` (fixed per-template questions in `NotificationTemplates.Text`). Frontend: root `shared/NotificationsService` (poll 10 s/60 s, started/stopped by the workspace page, cleared on stop), `NotificationListComponent` in the Home tab, `ReadOnViewDirective`, `ChatPromptService` consumed by the chat column, unread badge on the Home label. verify 780/0/0.
- Story 8.4 done (spec `spec-8-4-confirm-an-action-exactly.md`): `actions.reason` (V21) from `actions.domain.GateReasons` (deciding criterion = lowest cap, `AutonomyGate.cap`); `Actions.review`/`confirm` (same row, persona-wide row lock, re-check: state, Rule Parameter, gate ≥ L2, accounts, cover; `BankPort.transfer` + `ACTION` Audit Record `event: confirm`, `outcome: executed|refused`); `Actions.states` → `summaries`; `CoreGateway.transferBalances`. Use case + controller in `monitoring` (`ConfirmActionUseCase`, `ActionController`, `GET /api/actions/{id}/review`, `POST /api/actions/{id}/confirm`) since `actions -> personas` is outside AD-12. Notification item gains `actionId`, `reason`. Frontend: `ConfirmActionDialogComponent`, "Review and confirm" on Needs-your-OK items, snackbar, `NotificationsService.refresh()`. verify 836/0/0, frontend 124.
- Next: Story 8.5 (study presets: `POST /api/research/study/prepare/{presetId}`, reset + date + setup + monitoring in one use case, reproducibility test).

## Notes for 8.2

- The action enums (`ActionType`, `ActionState`, `AutonomyLevel`, `RegulatoryClass`) are `@NamedInterface("types")`: add `actions :: types` to monitoring's `allowedDependencies`.
- `propose` has no dedupe per trigger (deferred-work, maybe-false medium): decide in 8.2 whether monitoring or `actions` guarantees one Action per trigger/type/as-of.
- `ProposedAction` rejects negative amounts; `ActionType` holds class, reversibility and L3 eligibility — new types (AD-27) go there.
- `ActionServiceTest` runs under `@ActiveProfiles("research")` (reset use case).

## Checklist per story (lean loop, memory build-loop-economy)

1. Read this note, the epic context and the story ACs (`planning-artifacts/epics.md` §Epic 8); targeted code reads.
2. Spec ≤ 1 page; ask scope questions batched before writing.
3. Implement; affected tests only (`JAVA_HOME=C:\Users\commc\.jdks\temurin-25`, wrapper in `backend/`: `./mvnw -q`; filter JSON log lines with `grep -v '^{'`).
4. Diff (exclude generated files) → one combined sonnet reviewer (diff path, AC list, lens prompts); stories touching persona data also a separate Blind Hunter.
5. Fix, then full `verify -Dfine.it.required=true` (+ frontend test/build when touched); commit; update this note; /clear.

## Open

- Deferred from 8.4: confirming after the simulated date moved before the Action's `as_of` books back-dated (low; reset deletes Actions).

- Deferred from 8.1: `gateMeasures` ignores a target overnight account for the emergency fund after an `EF_TOP_UP` (fix with Mandates in Epic 9).
- Retro 6/7 items C2 (orchestrator stages) and C3 (needs text agrees with the profile) fit before or into Epic 8; C4/C5 are Commc decisions.
- Retro A5: `TurnOrchestrator` ~575 lines.
- The Riester rules end 2026-12-31: a subsidy what-if after that date escalates until 2027 rules exist.
- Verify ECB rates against the ECB before moving the reference date (`data/rules/README.md`).
