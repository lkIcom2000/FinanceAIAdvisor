# Epic 13 Handoff

## State after Story 13.3 (2026-10-28)

- Spec `spec-13-3-split-turnorchestrator-into-stage-classes.md`. `TurnOrchestrator` (197 lines) only sequences; stage logic in package-private `@Component`s in `advisory.application`: `ClassifyStage` (2), `ToolPlanStage` (3, where 13.4 adds Core figures for personal `INFORM`), `PromptStage` (5, exposure filter, history via `chats.releasedTurnsBefore`, ledger; where 13.5 changes the history), `DraftStage` (6), `CheckStages` (7–9), `BlockRoutingStage` (10), `ReleaseStage` (11). Tests still call the orchestrator's one-line stage delegates.
- verify green (0 failures, 0 skipped). No review (pure move). Commit: see `git log` "refactor: Story 13.3".

## State after Story 13.4 planning (2026-10-28)

- Spec `spec-13-4-fine-answers-questions-about-my-situation-with-my-data.md` is `ready-for-dev` (approved, decisions: no figure chips, full spec kept). Not implemented; nothing committed. Code Map carries the investigation; start the next session with `bmad-build` on that spec (resumes at implementation).

## Next

- Story 13.4 implementation (spec ready), then 13.5 (separate Blind Hunter review). 13.6 small and independent. Epic 10 not blocked; before Epic 10 the open Epic 9 retro item A3 (split rule lifecycle out of `ActionService`).
- Integration tests need Docker Desktop running (`fine.it.required=true` fails otherwise).
