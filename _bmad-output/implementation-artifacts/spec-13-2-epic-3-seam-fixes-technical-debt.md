---
title: 'Story 13.2: Epic 3 seam fixes (technical debt)'
type: 'bugfix'
created: '2026-10-27'
status: 'done'
baseline_revision: 'cd37e55'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-retro-2026-10-26.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-7-calculated-answers-with-parameter-proposal-and-figure-chips.md'
---

<intent-contract>

## Intent

**Problem:** Four Epic 3 seams (retro R1–R5, items A1–A4): at level `A` a turn with a Rule Parameter error is released as a figure-less `COMPUTE` answer; after Calculate the prompt history pairs the question with the proposal text instead of the calculated answer; the open or cancelled proposal card says "Calculated for you"; a blank regeneration puts an empty draft into the escalation snapshot, and the Number Check ledger sources numbers the model never saw at exposure `none` while missing the class instruction's banded premises.

**Approach:** Local fixes, no schema change: one guard in `blockRouting`, one history query, one frontend label, one snapshot line and two ledger inputs.

## Boundaries & Constraints

**Always:**
- A1: `blockRouting` routes `ruleParameterError` through `BlockRouting.route` before the routing-off branch, so every level (incl. `A`) gets `ESCALATE` with its case; this also covers the 7.1 subsidy what-if escalation.
- A2: `ChatRepository.releasedTurnsBefore` uses, per question, the latest agent reply to the question's agent message when one exists (Calculate answer to its proposal, resumed answer to its assessment card), else the agent message itself. New E2E tests in `CalculatedAnswerEndToEndTest`: a failed Calculate (model down → 503 `TURN_FAILED`, proposal stays `OPEN`, a retry calculates) and Calculate → `ESCALATE` (wrong numbers twice → template, reply to the proposal, case opened); a repository-level check that the history carries the calculated answer.
- A3: a proposal card with status `OPEN` or `CANCELLED` shows the label "Check these values" (icon `calculate`); a `CALCULATED` proposal and all other cards keep their outcome label. EXPERIENCE.md gets the label.
- A4: the snapshot keeps the first draft when the regeneration is blank (`Checks` gets one accessor for the routed draft, used by `blockRouting` and `snapshot`). The ledger's `USER` source reads the payload message (what the model saw; the depersonalised question at `none`), and numbers in the filled class instruction join the `EXPOSURE` source ("numbers in the class instruction").
- Deviation from the experiment track (`pipeline.py` ledger reads the raw message) is recorded below.

**Never:** schema changes; changes to the routing-on behaviour beyond A1's guard; regeneration of a turn with a Rule Parameter error.

## Acceptance Criteria

As in `epics.md` §Story 13.2 (four Given/When/Then blocks).

</intent-contract>

## Implementation Notes

- Deviation: the Number Check ledger no longer mirrors `experiments/finlab/pipeline.py` exactly — `USER` comes from the payload message, and the class instruction's numbers are sourced. At `aggregated`/`full` the payload message equals the raw message except for filtered identifiers. The `USER` label stays the experiment's.
- Level `A` with a Rule Parameter error: the Audit Record now shows `shadowMode` true together with an enforced `ESCALATE` route (intended, tested).
- History: a Calculate that escalated puts the escalation template into later prompts' history (the answer the persona saw), where before the proposal text stood; kept on purpose.
- Review (one sonnet reviewer, 7 findings, none high): fixed — level-`A` test for the direct subsidy what-if path, explicit `OPEN`/`CANCELLED` label condition, ledger Javadoc. Not done: duplicate-agent-reply guard in the history query (one agent reply per question by construction), aggregated-level ledger test.
- Final: `verify -Dfine.it.required=true` 706 tests, 0 failures, 0 skipped; frontend 104 tests, build ok.
- Workflow: no deviation (spec, inline implementation, one combined reviewer, no Blind Hunter — no persona data or security surface touched).
