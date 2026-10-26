---
title: 'Story 3.7: Calculated answers with parameter proposal and figure chips'
type: 'feature'
created: '2026-10-24'
status: 'in-progress'
baseline_revision: '47fa19e'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-6-numbers-only-from-the-deterministic-core-the-number-check.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** Stage 3 (tool plan) is a pass-through, so nothing is computable, `COMPUTE` never fires, the Core never runs in a turn and answers carry no `provId`s (FR-8, FR-20, FR-44, AD-24).

**Approach:** A pure `ToolPlan` turns the NLU parameters into a `SimulationSpec`; classify uses it as `computable`. A `COMPUTE` turn first releases a parameter proposal (no LLM call, DialogState `PARAMETER_PROPOSAL`); **Calculate** runs the turn again with the confirmed spec: the whitelisted tool runs through `CoreGateway`, its numbers enter the prompt and the ledger as `CORE`, the answer lists its figures. The frontend shows the proposal card, figure chips and the Overview highlight.

**Experiment Track counterpart:** `experiments/finlab/pipeline.py:101-116` (`_tool_plan`), `experiments/finlab/core.py:21-56` (`withdrawal_duration`), `experiments/finlab/pipeline.py:209-219` (computable, `add_core`), `experiments/finlab/prompts.py:97-113` (`format_tool_results`).

## Boundaries & Constraints

**Always:**
- `ToolPlan` (pure, `advisory.domain`): withdrawal duration if topic `withdrawal_duration` or portfolio + monthly withdrawal are extracted, and all three parameters are known. Portfolio missing but the message speaks of the user's own savings ("my savings/portfolio/investments/capital") → the row comes "from your account data" (Core investment totals, value filled in stage 3).
- `ToolRegistry` is the one whitelist bean (`advisory.application`): tool id → `CoreGateway` call. The LLM gets only the tool results text.
- DialogState is stored per persona (`dialog_states`): `PARAMETER_PROPOSAL` with the proposal message id; a new question marks the proposal `CANCELLED` and resets to `NONE`; Calculate marks it `CALCULATED` and resets to `NONE`, all in the turn's transaction.
- Every number of each Core result of the turn enters the ledger as `CORE` with its `provId` as label (3.6 deferral).
- A Core call failing for a missing Rule Parameter version sets the turn's rule-parameter error, which routes to `ESCALATE` (replaces the `RULE_PARAMETER_ERROR` constant).

**Never:** a Core computation before Calculate; the LLM naming or choosing a tool; client-side maths (chips format the backend's decimal string); `advisory` depending on `actions`.

</intent-contract>

## Acceptance Criteria

1. `ToolPlan` table test; the orchestrator runs only registry tools; the Modulith test asserts `advisory` does not depend on `actions`.
2. Q&A item 40 (500,000 EUR, 2,000 EUR/month, 4 %): the first turn releases outcome `COMPUTE` with a proposal (three rows, unit, source "from your question"), DialogState `PARAMETER_PROPOSAL`, no phrasing call, no Core call. Calculate (optionally with changed values) releases the calculated answer with figures; a new question instead cancels the card. Calculate on an already calculated proposal returns the stored calculated answer; on a cancelled proposal: 409.
3. `WithdrawalDuration` reference test: 522 full withdrawals effective, 538 nominal, reduced final withdrawals, assumptions stated.
4. Frontend: proposal card (rows, Calculate, Change values → fields, "Cancelled" grey); figure chips open "Why?"; matching Overview elements get a 4 s cyan ring, other tabs a cyan dot, "Show in overview" switches and scrolls; reduced motion jumps without fade.

## Deliberate deviations

- The experiment computes at once; the app shows the proposal first (AD-24). The harness auto-confirm (AD-15) comes with the Q&A suite.
- The proposal text is a fixed template; the Check Chain still runs on it (recorded), the card shows no checks.
- The calculated answer replies to the proposal message, so the history of later turns holds the proposal text, not the calculated answer.
- The spec is stored on the proposal message; the dialog state holds only the proposal id.
- KB and bank-read tools join the registry when a turn needs them (Epic 4 / later stories); 3.7 registers the Core's withdrawal duration and investment totals.
- Overview matching: the client indexes the `provId`s of the three overview responses, since unvisited tabs are not rendered.
- NLU grounding fix: the portfolio regex skips the amount at the monthly withdrawal's position, so "my savings … if I withdraw 200 EUR a month" has no portfolio. The experiment's `nlu.py` has this bug; noted for the final summary.
- The Wealth tab labels the investment total "Value of your investments today", so the chip link from an account-data portfolio lands on a matching element.
- A portfolio or `initial` of 0 from the model counts as not stated (llama3.1 fills `initial: 0.0` for "my savings", which blocked the account-data path live).
- Fixed tool-result texts carry no digits ("twelve equal monthly rates", assumptions in words, rate unit "as a fraction of one"; experiment: `experiments/finlab/prompts.py:112`, `core.py` assumptions): the Number Check flagged a faithful echo of "12" and escalated live.
- `FigureView` moved to `shared.web` (used by `advisory` and `core` web layers).
- `EvidenceStore` removed from the orchestrator (unused since 3.6's ledger).
- A missing Rule Parameter version raises `NoValidRuleParameterException` (knowledge API), which the orchestrator turns into the rule-parameter error.

## Spec Change Log

- 2026-10-24: created.
- 2026-10-26: AC2 corrected (calculated proposal returns the stored answer, only cancelled → 409); deviations from implementation added; live check: two fixes (zero portfolio, digit-free tool texts).

## Review Triage Log

