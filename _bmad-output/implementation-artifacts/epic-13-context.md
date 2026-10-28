# Epic 13 Context: Follow-up Fixes

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Close gaps found after their epics were closed (Commc, 2026-10-26 and sprint-change-proposal-2026-10-27): German answers, Epic 3 seam fixes, a structural split of the turn orchestrator, personal answers built from the persona's own Core figures, one turn of follow-up context, and idempotent answer requests. Each story names the gap it closes; none adds screens.

## Stories

- Story 13.1: Fine answers in German when written to in German
- Story 13.2: Epic 3 seam fixes (done)
- Story 13.3: Split TurnOrchestrator into stage classes (technical debt)
- Story 13.4: Fine answers questions about my situation with my data (FR-62)
- Story 13.5: Fine understands a follow-up on its last answer (FR-63)
- Story 13.6: A repeated answer request returns the same answer

## Requirements & Constraints

- Personal questions ("I", "my", "should I", "ich", "mein") are answered with the persona's Core figures for the recognised topic, one rule per topic; Fine never asks for financial data it holds; a personal explanation stays `INFORM`. Generic questions and topics without a rule stay unchanged.
- One previous released turn goes to the NLU and phrasing prompts at every exposure level and is the first thing the prompt budget drops. At exposure `none` it is the depersonalised question plus Fine's LLM-phrased answer; template texts (suitability, demands-and-needs, refusal, advisor reply) pass only their Core figures. The Number Check sources only the previous turn's numbers that came from Core outputs.
- `DialogState` (parameter proposal, assessment) keeps precedence over the follow-up context.
- Prompt changes from 13.1 and 13.5 are documented deviations from the experiment track (E4 ran single-turn); prompt hashes change in the Audit Record.
- A duplicate `POST /answer` runs the model once and both requests get the same 200 answer.

## Technical Decisions

- Turn pipeline stays deterministic around the LLM: classification, tool plan, Core figures, exposure filter and prompt budget, phrasing, Rule Engine / Number Check, block routing, release. Numbers come only from the Deterministic Core.
- Follow-up context is not a `DialogState`; the exposure filter owns what history reaches the model.
- Persona-data-touching stories (13.5) get a separate Blind Hunter review in addition to the combined reviewer.

## Cross-Story Dependencies

- 13.3 first: 13.4 changes the tool plan stage, 13.5 the payload/history stage.
- Order 13.3 → 13.4 → 13.5, one session each, all before Epic 12; 13.6 any time. Epic 10 is not blocked.
