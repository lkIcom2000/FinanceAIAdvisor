# Epic 6 Handoff

## State (2026-10-26)

- Epic 5 skipped (non-blocking, Commc 2026-10-26); Epic 6 started. Epic 4 retro inputs still in `epic-4-handoff.md` (folded into the Epic 6 retro).
- Story 6.1 done (spec `spec-6-1-…`, context `epic-6-context.md`): `profile` module (`Profiles.stored/confirm`, `ProfileCompleteness`, `ProfileFields`, V13 tables, `ProfileResetter`); the classifier and the prompt's profile context read the stored profile; `ASK_PROFILE` drafts start with `ResponseTemplates.WHY_ASK_PROFILE`; `RECOMMEND_CATEGORY` with strict fields unknown ends with the limitations sentence.
- Next: Story 6.2 (assessment form card, DialogState `ASSESSMENT`, confirm endpoint over `Profiles.confirm`).

## Checklist per story (lean loop, memory build-loop-economy)

1. Read this note, the epic context and the story ACs (`planning-artifacts/epics.md` §Epic 6); targeted code reads.
2. Spec ≤ 1 page; ask scope questions batched before writing.
3. Implement inline; affected tests only (`export JAVA_HOME=/c/Users/commc/.jdks/temurin-25`, `./mvnw -q`; filter JSON log lines with `grep -v '^{'`).
4. Diff (exclude generated files) → one combined sonnet reviewer (diff path, AC list, lens prompts in `.claude/skills/bmad-build-auto/review-prompts/`); stories touching persona data also a separate Blind Hunter.
5. Fix, then full `verify -Dfine.it.required=true` + frontend test/build; commit; update this note; /clear.

## Open

- From the 6.1 review, for 6.2: validate value types per field and reject NUL characters at the form endpoint; test that `ProfileFields.NEEDS_TOPICS` matches the yaml.
- Retro A5: `TurnOrchestrator` ~535 lines; extract stage classes when 6.2 adds the assessment card.
- Live Ollama check due with 6.2 (6.1 changed `class-RECOMMEND_CATEGORY.txt` and the profile context).
- Whether Marco needs a seeded profile for the 6.3 scenario (fixtures hold none) is decided in 6.3.
- Verify ECB rates against the ECB before moving the reference date (`data/rules/README.md`).
