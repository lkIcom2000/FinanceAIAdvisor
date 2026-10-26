# Epic 7 Context: What-If Simulations

## Goal

A persona asks "what if" in chat, checks the understood parameters and sees a range chart with assumptions (UJ-3). A changed parameter set is a new question (FR-34 removed).

## Stories

- Story 7.1: What-if from chat with a range chart

## Requirements & Constraints

- One `SimulationSpec` per what-if (AD-2); the seed comes from `RequestContext.seeds().core()`, never from the client.
- Parameters are shown first on the "Here's what I understood" card (DialogState `PARAMETER_PROPOSAL`, AD-24, Story 3.7); nothing is computed before Calculate.
- Monte Carlo and the Subsidy Calculator run in the Deterministic Core with Rule Parameters; every result states its assumptions (FR-21) and carries "Why?".
- Range chart card per UX-DR17 (light-cyan p10–p90 band, blue edges, navy median, legend, "not a promise", Assumptions, "Why?", hidden data table; no Adjust area).

## Technical Decisions

- Builds on Story 3.7: `ToolPlan` (pure plan + spec), `ToolRegistry` (whitelist), proposal card and Calculate endpoint; Story 2.4: `MonteCarloProjection` and the Wealth band chart; Story 4.1: Riester Rule Parameters.
- Experiment counterpart: `experiments/finlab/pipeline.py:117-119` (`savings_projection` plan), `experiments/finlab/core.py:184-221` (`monte_carlo`, initial defaults to 0).
