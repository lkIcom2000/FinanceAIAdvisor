# Epic 4 Handoff

## State (2026-10-26)

- Story 4.1 done (spec `spec-4-1-…`, context `epic-4-context.md`). Riester rules valid to 2026-12-31, ECB rates, `RiesterSubsidy` + `CoreGateway.riesterSubsidy/requireSubsidyRules`, stage 3 later-year subsidy check → route `rule_not_confirmed_next_year`.
- Next: Story 4.2 (KB ingest). First read the E1 result (`experiments/results/E1/`, reserve item 2: minimal/off if RAG does not win).

## Checklist per story (lean loop, memory build-loop-economy)

1. Read this note, the epic context and the story ACs (`planning-artifacts/epics.md` §Epic 4); targeted code reads.
2. Spec ≤ 1 page; ask scope questions batched before writing.
3. Implement inline; affected tests only (`export JAVA_HOME=/c/Users/commc/.jdks/temurin-25`, `./mvnw -q`).
4. Diff (exclude generated files) → one combined sonnet reviewer (diff path, AC list, lens prompts in `.claude/skills/bmad-build-auto/review-prompts/`); critical stories also a separate Blind Hunter.
5. Fix, then full `verify -Dfine.it.required=true` + frontend test/build; commit; update this note; /clear.

## Open

- Epic-end compose check: live UJ-5 turn with real NLU ("Should I cancel my Riester contract and switch to the new subsidised product next year?") must escalate with the next-year reason.
- Retro A5: check `TurnOrchestrator` size before 4.3 adds retrieval.
- Verify ECB rates against the ECB before moving the reference date (`data/rules/README.md`).
