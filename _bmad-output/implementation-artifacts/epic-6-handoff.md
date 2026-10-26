# Epic 6 Handoff

## State (2026-10-26, after Story 7.1)

- Epic 5 skipped (non-blocking, Commc 2026-10-26); Epic 6 started. Epic 4 retro inputs still in `epic-4-handoff.md` (folded into the Epic 6 retro).
- Story 6.1 done (spec `spec-6-1-…`, context `epic-6-context.md`): `profile` module (`Profiles.stored/confirm`, `ProfileCompleteness`, `ProfileFields`, V13 tables, `ProfileResetter`); the classifier and the prompt's profile context read the stored profile; `ASK_PROFILE` drafts start with `ResponseTemplates.WHY_ASK_PROFILE`; `RECOMMEND_CATEGORY` with strict fields unknown ends with the limitations sentence.
- Story 6.2 done (spec `spec-6-2-…`): `ASK_PROFILE` answers carry the assessment card (`ChatMessage.Assessment`, V14, DialogState `ASSESSMENT`), pre-filled from `CoreGateway.profileFigures` (new `monthlyNetIncome`); `POST /api/chat/messages/{id}/assessment` → `SaveAssessmentUseCase` → `Profiles.confirm` (validates via `profile.AssessmentFields`) → `TurnOrchestrator.resume`; stage 1 lives in `DialogStates`. Workspace turns (no `runId`) follow the strict FR-17 rule (`Classifier.classify(..., strict)`, `ProfileCompleteness.strictMissing`).
- Story 6.3 done (spec `spec-6-3-…`): pure Core `CategorySelection` / `CostCalculation` via `CoreGateway.recommendation` (new Rule Parameters incl. `equity_share_<bucket>_min/max`); `advisory.application.Recommendations` runs them in stage 3, builds the deterministic Suitability Statement (`ChatMessage.Recommendation`, V15) and the chips (emergency fund carries the Liquidity `provId` when equal); fixed first line `ResponseTemplates.recommendationLead`; `DOCUMENTATION` fails `no_core_selection_recorded` / `statement_incomplete`; audit body `categorySelection`; frontend "Why this fits you" panel with cyan bar.
- Live check of 6.3 done (spec 6.3 Implementation Notes): two app additions — NLU grounding moves "invest X per month" from `monthly_expenses` to `requested_monthly_amount`; `SUITABILITY`/`COST_TRANSPARENCY` failures on a recommendation get the one regeneration. Marco's AC question now releases `RECOMMEND_CATEGORY` with 9 checks passed.
- Story 6.4 done (spec `spec-6-4-…`): pure Core `DemandsAndNeeds` via `CoreGateway.needs` (Needs Profile + bank contracts, no provider names); `advisory.application.NeedsStatements` replaces the category selection for protection topics; `ChatMessage.NeedsStatement` (V16), `GET /api/chat/needs-statement` (204 without), Home "Your demands and needs" card; `IDD_NEEDS` fails `needs_facts_missing`; both protection topics need household/dependants/occupation (`profile-minimums.yaml`); own prompt `class-RECOMMEND_CATEGORY-needs.txt`.
- Plan (Commc 2026-10-26): Epic 7 (only Story 7.1) is done before the Epic 6 epic-end tasks, which then cover Epics 6 and 7 together.
- Story 7.1 done (spec `spec-7-1-…`, context `epic-7-context.md`): `ToolPlan` tool `savingsProjection` (monthly amount, years, optional start, subsidy row `Unit.FLAG` from `monthlyNetIncome`); pure Core `WhatIfProjection` (shares `MonteCarloProjection.simulate`, Riester basic allowance per year) via `CoreGateway.whatIf`/`netIncome`; fixed answer `ResponseTemplates.range`, no LLM, no regeneration; `ChatMessage.RangeChart` (V17); shared frontend `RangeChartComponent` (Wealth tab + chat card), two-step waiting card on `ChatProposal.range`.
- Live check of 6.4 and 7.1 done (spec Implementation Notes): 7.1 passed unchanged; 6.4 needed two app additions — NLU grounding turns a first-person protection `general_explanation` into `personal_recommendation`; the needs prompt and the cost-free `RegenerationFix` text ask for "you", the three Needs Profile facts and the risk each cover protects against. Jonas's Needs Profile is now stored in the local DB (single, 0, "Software developer"), plus one open escalation case from the first attempt.
- Next, as one block: (1) a batched epic-end review of Epics 6 and 7; (2) a compose image rebuild with the full compose check (incl. research profile); (3) a joint retro for Epics 6 and 7 (Epic 4 inputs folded in).

## Checklist per story (lean loop, memory build-loop-economy)

1. Read this note, the epic context and the story ACs (`planning-artifacts/epics.md` §Epic 6); targeted code reads.
2. Spec ≤ 1 page; ask scope questions batched before writing.
3. Implement inline; affected tests only (`export JAVA_HOME=/c/Users/commc/.jdks/temurin-25`, `./mvnw -q`; filter JSON log lines with `grep -v '^{'`).
4. Diff (exclude generated files) → one combined sonnet reviewer (diff path, AC list, lens prompts in `.claude/skills/bmad-build-auto/review-prompts/`); stories touching persona data also a separate Blind Hunter.
5. Fix, then full `verify -Dfine.it.required=true` + frontend test/build; commit; update this note; /clear.

## Open

- Retro A5: `TurnOrchestrator` ~575 lines; 6.4 should put IDD logic in its own component like `Recommendations`.
- Marco has no seeded profile (decided 6.3); tests store it via `Profiles.confirm`, live he fills the card.
- Compose image predates V14–V16 and the new Rule Parameters: rebuild at epic end (compose check).
- Frontend component-style warning budget raised to 7 kB (chat column stylesheet 6.5 kB).
- Epic-end review: include the 6.4 diff follow-ups and the 6.3 live-check fixes (`NluExtraction.ground` invest rule, `RegenerationFix` recommendation fix).
- 6.3 residual: the selection's category for Marco is only `build_emergency_fund_first`, so the text never adds "then a small savings plan in a broad world equity fund"; decide at the retro whether the AC wording needs a follow-up category.
- Epic-end review: add the 7.1 deferral (orchestrator unit test for a blocked range answer); the Riester rules end 2026-12-31, so a what-if after that date escalates until 2027 rules exist.
- Compose rebuild must also cover V17.
- Epic-end review: include the 6.4 live-check fixes (`NluExtraction.ground` own-cover rule, `RegenerationFix` needs text, `class-RECOMMEND_CATEGORY-needs.txt`). Retro: the 6.4 model text quality (repetition, "a good thing" for duplicate cover, "your insurance provider" instead of the parents).
- Verify ECB rates against the ECB before moving the reference date (`data/rules/README.md`).
