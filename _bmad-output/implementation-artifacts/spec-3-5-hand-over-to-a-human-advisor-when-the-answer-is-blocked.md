---
title: 'Story 3.5: Hand over to a human advisor when the answer is blocked'
type: 'feature'
created: '2026-10-24'
status: 'in-review'
baseline_revision: 'c7cb65142d89b85ed2353f642404d46a077f1f12'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-4-check-chain-rule-engine-and-compliance-levels.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** A turn routed to `ESCALATE` releases the experiment's template and nothing else: no case is queued for an advisor (FR-52), and the card does not tell Petra what happens next (FR-41, UX-DR26).

**Approach:** The `escalation` module gets `escalation_cases` and a module API `EscalationCases`. The orchestrator opens a `TURN` case in the release transaction exactly when the outcome is `ESCALATE`. Chat messages carry a `handover` (plain reason class, case status) read from the case; the card shows the handover text, the reason class and "Waiting for advisor".

**Experiment Track counterpart:** `experiments/finlab/pipeline.py:268,300,352-353` (ESCALATE routes, `TEMPLATES["ESCALATE"]`); the experiment has no queue.

## Boundaries & Constraints

**Always:**
- `escalation_cases(id, persona_id, origin TURN|TRIGGER, turn_id UNIQUE, question, draft, block_reasons JSONB array, reason_class, status OPEN|CLOSED, created_at)`; `turn_id` is set exactly for `TURN`; no foreign keys across modules. Registered with persona reset (`escalation` resetter, `PersonaOwnedTablesTest`).
- Case snapshot: the user's original question, the draft routing decided on (regenerated text if regenerated), block reasons = the route name followed by `CHECK_ID:reason_code` per failed routed-on check.
- Written in `auditAndRelease`, same transaction as the agent message and the Audit Record (`MANDATORY`); the Audit Record names the case id (`escalationCaseId`).
- Card body = `ResponseTemplates.ESCALATE` = "Your question has been passed to a human advisor at your bank. You'll see their answer here in the chat." Reason class from the route only: `invalid_rule_parameter` → "because a rule this answer depends on isn't confirmed yet", `number_mismatch_after_regeneration` → "because I couldn't confirm every figure in my answer", any other route → none. Status line `OPEN` → "Waiting for advisor", `CLOSED` → "Answered".

**Never:** the draft or technical block reasons in the API or on the card; cases for any outcome other than `ESCALATE`; advisor side, `TRIGGER` cases, closing cases (Epic 5); blocking the composer.

</intent-contract>

## Acceptance Criteria

1. `ESCALATE` turn → one `TURN` case with snapshot, in the turn's transaction; a failed audit write leaves no case (rollback).
2. No other outcome creates a case (orchestrator test per outcome; `COMPUTE` is unreachable until Story 3.7's `computable` and is covered by the outcome guard, see Implementation Notes).
3. The card shows the handover text, at most the plain reason class and "Waiting for advisor"; the composer stays usable.
4. Persona reset deletes the persona's cases.

## Deliberate deviations

- ESCALATE text is the UX copy, not the experiment's `TEMPLATES["ESCALATE"]` (only outcomes are compared in the Q&A suite).
- Reason class for an invalid Rule Parameter is generic, not the UX example "for next year", because a parameter can also be expired.

## Spec Change Log

- 2026-10-24: created.

## Review Triage Log

Review runs jointly with Story 3.7 (block B).

## Implementation Notes

- Lean loop per `epic-3-handoff.md`: inline implementation, no review yet (block B review after Story 3.7), story commit before the review.
- `COMPUTE` cannot be produced before Story 3.7 (`computable` is always false), so the per-outcome orchestrator test covers `INFORM`, `ASK_PROFILE`, `RECOMMEND_CATEGORY`, `REFUSE_OUT_OF_SCOPE`, `DETECT_TRAP` (no case) and `ESCALATE` (one case); Story 3.7 should add `COMPUTE` to `outcomesWithoutACase`.
- The two plain reason classes are reachable only through Story 3.6 (`NUMBER_MATCH`) and 3.7 (Rule Parameter error); until then every live `ESCALATE` card has no reason line.
- The handover is not stored on the message: `Handovers` reads the case status from `escalation`, so Epic 5 can close a case without touching `messages`.
- Live check deferred to the block B live check after Story 3.7 (an `ESCALATE` turn needs a model draft that fails a check; the case path is covered against real PostgreSQL).

## Auto Run Result

- **Summary:** `escalation` module (V10 `escalation_cases`, `EscalationCases` API, service, repository, persona reset), a `TURN` case opened in `auditAndRelease` for `ESCALATE` only with snapshot and `escalationCaseId` in the Audit Record, UX handover text, `HandoverReason`, `handover` on chat messages and the card's reason line and "Waiting for advisor" status.
- **Verification:** `./mvnw -B -q verify -Dfine.it.required=true` 520 tests, 0 failures, 0 skipped; frontend `api:check` up to date, `ng test` 77 passed, build complete.
- **Review:** pending (block B with Story 3.7).
