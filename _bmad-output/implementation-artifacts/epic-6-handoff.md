# Epic 6 Handoff

## State (2026-10-26)

- Epic 5 skipped (non-blocking, Commc 2026-10-26); Epic 6 started. Epic 4 retro inputs still in `epic-4-handoff.md` (folded into the Epic 6 retro).
- Story 6.1 done (spec `spec-6-1-…`, context `epic-6-context.md`): `profile` module (`Profiles.stored/confirm`, `ProfileCompleteness`, `ProfileFields`, V13 tables, `ProfileResetter`); the classifier and the prompt's profile context read the stored profile; `ASK_PROFILE` drafts start with `ResponseTemplates.WHY_ASK_PROFILE`; `RECOMMEND_CATEGORY` with strict fields unknown ends with the limitations sentence.
- Story 6.2 done (spec `spec-6-2-…`): `ASK_PROFILE` answers carry the assessment card (`ChatMessage.Assessment`, V14, DialogState `ASSESSMENT`), pre-filled from `CoreGateway.profileFigures` (new `monthlyNetIncome`); `POST /api/chat/messages/{id}/assessment` → `SaveAssessmentUseCase` → `Profiles.confirm` (validates via `profile.AssessmentFields`) → `TurnOrchestrator.resume`; stage 1 lives in `DialogStates`. Workspace turns (no `runId`) follow the strict FR-17 rule (`Classifier.classify(..., strict)`, `ProfileCompleteness.strictMissing`).
- Story 6.3 done (spec `spec-6-3-…`): pure Core `CategorySelection` / `CostCalculation` via `CoreGateway.recommendation` (new Rule Parameters incl. `equity_share_<bucket>_min/max`); `advisory.application.Recommendations` runs them in stage 3, builds the deterministic Suitability Statement (`ChatMessage.Recommendation`, V15) and the chips (emergency fund carries the Liquidity `provId` when equal); fixed first line `ResponseTemplates.recommendationLead`; `DOCUMENTATION` fails `no_core_selection_recorded` / `statement_incomplete`; audit body `categorySelection`; frontend "Why this fits you" panel with cyan bar.
- Live check of 6.3 done (spec 6.3 Implementation Notes): two app additions — NLU grounding moves "invest X per month" from `monthly_expenses` to `requested_monthly_amount`; `SUITABILITY`/`COST_TRANSPARENCY` failures on a recommendation get the one regeneration. Marco's AC question now releases `RECOMMEND_CATEGORY` with 9 checks passed.
- Story 6.4 done (spec `spec-6-4-…`): pure Core `DemandsAndNeeds` via `CoreGateway.needs` (Needs Profile + bank contracts, no provider names); `advisory.application.NeedsStatements` replaces the category selection for protection topics; `ChatMessage.NeedsStatement` (V16), `GET /api/chat/needs-statement` (204 without), Home "Your demands and needs" card; `IDD_NEEDS` fails `needs_facts_missing`; both protection topics need household/dependants/occupation (`profile-minimums.yaml`); own prompt `class-RECOMMEND_CATEGORY-needs.txt`.
- Next: live Ollama check of 6.4 (Jonas, local `spring-boot:run`), then Epic 6 epic-end tasks (compose rebuild, batched review, retro).

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
- Verify ECB rates against the ECB before moving the reference date (`data/rules/README.md`).
