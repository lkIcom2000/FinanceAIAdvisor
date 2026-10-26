---
title: 'Story 7.1: What-if from chat with a range chart'
type: 'feature'
created: '2026-10-26'
status: 'done'
baseline_revision: 'a498bd3'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-7-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-7-calculated-answers-with-parameter-proposal-and-figure-chips.md'
---

<intent-contract>

## Intent

**Problem:** Only the withdrawal duration is plannable, so "what if I save 150 EUR a month for 30 years, with the state subsidy?" never reaches the Core, and the chat has no range chart (FR-33, UJ-3).

**Approach:** `ToolPlan` gains the tool `savingsProjection` (monthly amount, years, optional initial, subsidy row with eligibility from account data). Calculate runs a pure Core `WhatIfProjection` (the Monte Carlo of Story 2.4 plus the Riester basic allowance credited yearly) and releases a fixed range sentence with a range chart card; no phrasing call.

**Experiment Track counterpart:** `experiments/finlab/pipeline.py:117-119`, `experiments/finlab/core.py:184-221`.

## Boundaries & Constraints

**Always:**
- Scope decisions (Commc 2026-10-26): full Riester basic allowance when eligible, with the assumption that own payments reach the minimum own contribution (gross income is not in the account data); answer text fixed, no LLM; start value 0 unless the question states one; the subsidy row is not editable.
- Plan: topic `savings_projection`, or `monthly_contribution` and `years` extracted; monthly amount > 0 and ≤ 1,000,000,000 EUR, years a whole number 1–60, a stated initial > 0 becomes a row. Withdrawal duration keeps precedence.
- Subsidy row only if the message uses subsidy wording (`ToolPlan.SUBSIDY`): key `subsidy`, new `Unit.FLAG` (1 yes, 0 no), source `ACCOUNT_DATA` with the `provId` of the Core's `monthlyNetIncome` (eligible = salary income above 0); label says why. Eligibility is an account-data read before Calculate, like the 3.7 portfolio.
- `WhatIfProjection` (pure, `core.domain`, function `whatIfProjection`) shares the simulation loop with `MonteCarloProjection` (wealth figures unchanged); inputs monthly, years, initial, optional subsidy (net income figure, Rule Parameters `riester_basic_allowance` and `riester_min_contribution_floor`: allowance only if 12 × monthly ≥ floor), TER, inflation, return model, paths, seed `RequestContext.seeds().core()`. Outputs p10, p50, p90, paid in, allowances total (with subsidy), years, p50 real; yearly points. Assumptions: return model, costs, inflation, horizon, subsidy rule (key and version, or why none).
- Answer: `ResponseTemplates.range(p10, p90, p50, years)` = "Between … and … EUR after … years. The middle outcome is … EUR. This is a range, not a promise."; Check Chain runs and is recorded; no regeneration of a fixed draft. `ChatMessage.RangeChart(points, assumptions, provId of p50)` as JSONB (V17); chips = p10, p50, p90, paid in, allowances.
- Frontend: shared `RangeChartComponent` (from the Wealth tab's band chart) in the chat card with the sentence, Assumptions, "Why?" and a visually hidden data table; Calculate on a range proposal (`ChatProposal.range`) shows only "Getting your figures" and "Checking the rules"; FLAG rows show "Yes"/"No" and stay read-only on Change values.

**Never:** a Core run before Calculate; a client seed; LLM wording of the range; product or provider names.

</intent-contract>

## Acceptance Criteria

1. `ToolPlan` table test: Jonas's question → `savingsProjection` with monthly 150 EUR and 30 years "from your question" and subsidy Yes "from your account data"; without subsidy words no subsidy row; withdrawal questions unchanged; invalid years/amounts not computable.
2. Jonas (E2E with LLM stub): first turn releases the proposal (three rows, no Core projection, no phrasing call); Calculate releases outcome `COMPUTE` with the range sentence, the chart (30 points), the assumptions incl. the subsidy rule version, figure chips; no phrasing call; a second Calculate run of the same spec gives identical figures.
3. `WhatIfProjectionTest`: same seed → same result; subsidy raises the outcome and adds the allowances total; floor not reached → no allowance; `MonteCarloProjectionTest` unchanged and green.
4. Frontend: range card renders chart, sentence, assumptions, "Why?", hidden table; two-step waiting card on a range Calculate; FLAG row read-only; OpenAPI snapshot and client regenerated.

## Implementation Notes
- Scope decisions (Commc 2026-10-26): full basic allowance with the minimum-contribution assumption, fixed answer text without LLM, start value 0 unless stated, subsidy row read-only.
- Deviation from the experiment: the allowance is an app addition (`monte_carlo` has none); it is credited at each year end; child allowances and the tax deduction are left out (stated as an assumption), and eligibility is "salary income above zero" in the account data (`monthlyNetIncome`), because gross income and child benefit are not in the fixtures. The Riester rules are valid to the end of 2026; the assumption names the version and says the rules are taken as unchanged.
- `MonteCarloProjection.simulate` is shared with the goal projection; with a year-end credit of zero the arithmetic is unchanged (the full `verify` incl. the wealth tests is green).
- New `Unit.FLAG` (yes/no) for the subsidy row; `ChatProposal.range` tells the client to show the two-step waiting card. The range sentence is fixed (`ResponseTemplates.range`), runs through the Check Chain, and is never regenerated; a blocked answer drops the chart.
- Shared `RangeChartComponent` replaces the Wealth tab's own chart code; hosts provide ngx-echarts so specs can fake it.
- Review (one combined sonnet reviewer, 2026-10-26). Fixed: tests for the allowance floor boundary, a stated start, the plan/Core horizon bound, FLAG formatting. Dismissed: net income evidence stored before a rule-parameter failure (the same figure key was already stored by the proposal); a stale eligibility figure runs the confirmed value (as the 3.7 portfolio); the finite-value guard before the topic check (the withdrawal path already refused non-finite values); no golden values for the wealth projection (the refactor keeps the arithmetic order). Deferred to the epic-end review: an orchestrator unit test for a blocked range answer.
- Live Ollama check pending (joint Epic 6/7 block).

