# Epic 6 Handoff

## State (2026-10-26)

- Epic 5 skipped (non-blocking, Commc 2026-10-26); Epic 6 started. Epic 4 retro inputs still in `epic-4-handoff.md` (folded into the Epic 6 retro).
- Story 6.1 done (spec `spec-6-1-…`, context `epic-6-context.md`): `profile` module (`Profiles.stored/confirm`, `ProfileCompleteness`, `ProfileFields`, V13 tables, `ProfileResetter`); the classifier and the prompt's profile context read the stored profile; `ASK_PROFILE` drafts start with `ResponseTemplates.WHY_ASK_PROFILE`; `RECOMMEND_CATEGORY` with strict fields unknown ends with the limitations sentence.
- Story 6.2 done (spec `spec-6-2-…`): `ASK_PROFILE` answers carry the assessment card (`ChatMessage.Assessment`, V14, DialogState `ASSESSMENT`), pre-filled from `CoreGateway.profileFigures` (new `monthlyNetIncome`); `POST /api/chat/messages/{id}/assessment` → `SaveAssessmentUseCase` → `Profiles.confirm` (validates via `profile.AssessmentFields`) → `TurnOrchestrator.resume`; stage 1 lives in `DialogStates`. Workspace turns (no `runId`) follow the strict FR-17 rule (`Classifier.classify(..., strict)`, `ProfileCompleteness.strictMissing`).
- Next: Story 6.3 (category selection + Suitability Statement). Live check showed a saved-profile recommendation escalating via `block_suitability` until 6.3 fills `CheckChain.Facts` category selection.

## Checklist per story (lean loop, memory build-loop-economy)

1. Read this note, the epic context and the story ACs (`planning-artifacts/epics.md` §Epic 6); targeted code reads.
2. Spec ≤ 1 page; ask scope questions batched before writing.
3. Implement inline; affected tests only (`export JAVA_HOME=/c/Users/commc/.jdks/temurin-25`, `./mvnw -q`; filter JSON log lines with `grep -v '^{'`).
4. Diff (exclude generated files) → one combined sonnet reviewer (diff path, AC list, lens prompts in `.claude/skills/bmad-build-auto/review-prompts/`); stories touching persona data also a separate Blind Hunter.
5. Fix, then full `verify -Dfine.it.required=true` + frontend test/build; commit; update this note; /clear.

## Open

- Retro A5: `TurnOrchestrator` ~545 lines after extracting stage 1; extract more stages if 6.3 grows it.
- Whether Marco needs a seeded profile for the 6.3 scenario (fixtures hold none) is decided in 6.3; the card now lets him enter one live (stored values: amounts 2 decimals, read back as doubles; choices as option keys, risk tolerance `very low`…`very high`).
- 6.3 category rules map risk tolerance keys with spaces (`very low`) to `category_rules.yaml` keys with underscores (`very_low`).
- Compose image predates V14: rebuild at epic end (compose check).
- Verify ECB rates against the ECB before moving the reference date (`data/rules/README.md`).
