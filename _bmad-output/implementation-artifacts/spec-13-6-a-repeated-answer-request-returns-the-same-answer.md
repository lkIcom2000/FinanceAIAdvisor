---
title: 'Story 13.6: A repeated answer request returns the same answer'
type: 'bugfix'
created: '2026-10-28'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A duplicate `POST /api/chat/messages/{id}/answer` (double click, retry) runs the model twice; `AnswerMessageUseCase.answer` checks `findReply` without a lock, so the second insert fails on `messages_reply_to_key` and returns `503 TURN_FAILED` for a question Fine already answered.

**Approach:** Lock the question row (`ChatRepository.lock`, `SELECT … FOR UPDATE`, as `SaveAssessmentUseCase` does) before the stored-reply check, so the second request waits for the first transaction, then finds and returns the stored reply. Both requests get `200` with the same answer and the model runs once; an integration test with two parallel requests covers it (the stub LLM holds the first turn in the phrasing call until the second request waits on the row lock).

</frozen-after-approval>

## Implementation Notes

- `AnswerMessageUseCase.answer` calls `chats.lock(scope, messageId)` after the question check and before `findReply`. Postgres READ COMMITTED takes a fresh snapshot per statement, so the waiting request's `findReply` sees the committed reply. The lock is held for the whole turn (AD-7 already runs the turn in one transaction).
- Test support: `StubLlm.holdPhrasing(entered, release)` holds phrasing calls on latches (cleared by `reset()`). `ChatEndToEndTest.twoParallelAnswerRequestsRunOneTurnAndBothGetTheSameAnswer` holds the first turn in phrasing, waits until `pg_stat_activity` shows a session waiting on a lock, then releases: both 200 with the same body, one NLU and one phrasing call, one reply, one RESPONSE record. Without the lock the test fails (second request 503).
- Out of scope, unchanged: `CalculateUseCase` (`findReply` without lock) is guarded by the conditional proposal status transition (`ProposalClosedException`); `SaveAssessmentUseCase` already locks.
- Workflow deviation (build-loop economy): oneshot route; one Blind Hunter review only.

## Review Triage Log

- Lock held without `lock_timeout` during the model call — false/rejected: a waiter waits at most as long as the first turn, which the LLM client's timeout bounds; before the fix it would have run the same model call itself.
- Waiter runs a second turn after the first fails — rejected (intended): the failed turn rolls back, the question stays saved, and the retry answers it (same as "Try again"); `whenTheModelIsDownTheQuestionStaysSavedAndUnanswered` covers the retry.
- Question read before the lock — false: a user message row is never updated, so the earlier read cannot be stale.
- Executor closes before `release` on an early assertion failure — low, patched: release, then close in `finally`.
- Lock-waiter probe counts any waiting session — low, patched: filtered on the `messages … FOR UPDATE` query.
- Stub latches written in two volatile fields — low, patched: `release` is written before `entered`.
- Helper placement / poll constant — rejected: cosmetic.
- Second response not proven to come from the stored reply — false: the bodies match including `$.id`, and there is one NLU call, one phrasing call and one reply.

