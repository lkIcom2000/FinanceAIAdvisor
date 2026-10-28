# Epic 13 Handoff

## State after Story 13.3 (2026-10-28)

- Spec `spec-13-3-split-turnorchestrator-into-stage-classes.md`. `TurnOrchestrator` (197 lines) only sequences; stage logic in package-private `@Component`s in `advisory.application`: `ClassifyStage` (2), `ToolPlanStage` (3, where 13.4 adds Core figures for personal `INFORM`), `PromptStage` (5, exposure filter, history via `chats.releasedTurnsBefore`, ledger; where 13.5 changes the history), `DraftStage` (6), `CheckStages` (7–9), `BlockRoutingStage` (10), `ReleaseStage` (11). Tests still call the orchestrator's one-line stage delegates.
- verify green (0 failures, 0 skipped). No review (pure move). Commit: see `git log` "refactor: Story 13.3".

## State after Story 13.4 planning (2026-10-28)

- Spec `spec-13-4-fine-answers-questions-about-my-situation-with-my-data.md` is `ready-for-dev` (approved, decisions: no figure chips, full spec kept). Not implemented; nothing committed. Code Map carries the investigation; start the next session with `bmad-build` on that spec (resumes at implementation).

## State after Story 13.4 (2026-10-28)

- Implemented and committed (`git log` "feat: Story 13.4"); verify green.

## State after Story 13.5 planning (2026-10-28)

- Spec `spec-13-5-fine-understands-a-follow-up-on-its-last-answer.md` is `ready-for-dev` (approved; ~2200 tokens kept as one goal). Not implemented; nothing committed. Code Map carries the investigation (migration V24 `messages.followup`, one filtered previous turn for NLU + phrasing, CORE-only ledger). Start the next session with `bmad-build` on that spec; it needs the separate Blind Hunter review.

## State after Story 13.5 (2026-10-28)

- Implemented and committed (`git log` "feat: Story 13.5"); verify green. Combined review + Blind Hunter ran; patched: `FollowUp.phrased` only at exposure `none` (a level switch cannot carry aggregated text into a `none` prompt). Rejected findings are in the spec's Review Triage Log; nothing deferred.

## Next

- Story 13.6 (small and independent) next. Epic 10 not blocked; before Epic 10 the open Epic 9 retro item A3 (split rule lifecycle out of `ActionService`).
- Integration tests need Docker Desktop running (`fine.it.required=true` fails otherwise).

## State after Story 13.6 (2026-10-28)

- Spec `spec-13-6-a-repeated-answer-request-returns-the-same-answer.md` (oneshot). `AnswerMessageUseCase` locks the question row (`ChatRepository.lock`) before `findReply`; a parallel `POST /answer` waits and returns the stored reply. Test: `ChatEndToEndTest.twoParallelAnswerRequestsRunOneTurnAndBothGetTheSameAnswer` (`StubLlm.holdPhrasing`). verify green (922 tests, 0 skipped). Blind Hunter ran; 3 low test patches, rest rejected (Triage Log). Commit: see `git log` "fix: Story 13.6".

## Next (after 13.6)

- Open in Epic 13: 13.1 (German answers, backlog), then optional epic-13 retrospective. Before Epic 10 the open Epic 9 retro item A3 (split rule lifecycle out of `ActionService`).

## State after Story 13.1 planning (2026-10-28)

- Spec `spec-13-1-fine-answers-in-german-when-written-to-in-german.md` is `ready-for-dev` (approved; ~1800 tokens kept as one goal). Not implemented; nothing committed. Decisions: ambiguous message → previous turn's language, else English; only `NumberParser` gets German formats, retro A6 moved to `deferred-work.md`. Code Map carries the investigation (deterministic `Language` detector in `TurnState`, answer-language line in `role-advisor.txt`, German variants in `ResponseTemplates`/`NoValidSource`/chips, German alternatives in `CheckChain` lists as a union, `language` in `ResponseAuditBody`, no migration). Start the next session with `bmad-build` on that spec (resumes at implementation).

## State after Story 13.1 (2026-10-28)

- Spec `spec-13-1-fine-answers-in-german-when-written-to-in-german.md` done, story `review`. Deterministic `Language` (de/en) in `TurnState`, answer-language line in `role-advisor.txt` (English prompt hashes change too), German texts/chips in `ResponseTemplates`/`NoValidSource`, German alternatives in `CheckChain`, German formats in `NumberParser`, `language` in the audit body. Cards stay English. verify green (993 tests, 0 skipped), `golden.json` unchanged. One combined reviewer: 6 patches, 1 deferred ("1,234" on German turns), 1 rejected (Triage Log). Commit `2f35ffc`.

## Next (after 13.1)

- All Epic 13 stories are built; 13.1, 13.5, 13.6 wait in `review` for the live check. Run one Epic 13 live check in a fresh session (local `spring-boot:run` against compose db/ollama): German question, German out-of-scope, German follow-up "150 EUR?", English regression turn; 13.4 situation question; 13.5 follow-up; 13.6 repeated answer. German texts need a native read in that check. Then set the stories `done`, close epic-13 (retro optional).
- Then the open Epic 9 retro item A3 (split rule lifecycle out of `ActionService`) before Epic 10; Epic 10 and 12 are the critical path to the UAT.
