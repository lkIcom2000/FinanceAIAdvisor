---
title: 'Story 6.1: Profile module and the completeness gate'
type: 'feature'
created: '2026-10-26'
status: 'done'
baseline_revision: '6c92d1b'
review_loop_iteration: 1
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** Classification asks a stub that reports every profile incomplete; there is no profile storage, so no personal recommendation can ever be given, and `ASK_PROFILE` answers do not say why Fine asks.

**Approach:** A `profile` module with `suitability_profiles` / `needs_profiles` (one row per field, `confirmed_at`), a confirm control as the only writer, reset registration, and the pure `profile.ProfileCompleteness` behind the advisory port. The orchestrator reads the stored profile for classification and the prompt's profile context; `ASK_PROFILE` drafts start with the fixed sentence, `RECOMMEND_CATEGORY` drafts end with a fixed limitations sentence.

**Experiment Track counterpart:** `experiments/finlab/profile.py:45-66`, `experiments/finlab/classify.py:141`, `experiments/finlab/pipeline.py:211,240`.

## Boundaries & Constraints

**Always:**
- `ProfileCompleteness.evaluate(stored, stated, topic)`: effective profile = stored overridden by non-empty stated values; AND over groups, OR within; unknown topic → incomplete with `topic_not_supported_for_personal_recommendation`; result `(complete, missingFields, limitations)`, limitations = strict FR-17 fields absent from the effective profile (none for the needs topics `protection_needs`, `protection_duplicate`).
- `Profiles` module API: `stored(scope)` (both tables merged, field → value) and `confirm(scope, answers, confirmedAt)` (upsert, field → table by `ProfileFields`, unknown field rejected). Nothing else writes the tables.
- `ProfileResetter` (`profile` in `RESET_ORDER`) deletes the persona's rows; fixtures hold no profile.
- `ASK_PROFILE` draft prefixed (before the checks) with "Before I can say whether this fits you, I need to know a bit more about your situation. It takes about 3 minutes." unless it already starts with it; the routing template starts with it too.
- `RECOMMEND_CATEGORY` with limitations: draft ends with "This is based only on what I know so far. Not known yet: <fields>."; reasons record the limitations (audit); `{limitations}` in the class instruction.

**Never:** persisting `statedProfile`; profile writes from the orchestrator; NLU prompt changes.

</intent-contract>

## Acceptance Criteria

1. `ProfileCompletenessTest`: stored only, stated overrides stored, empty stated values ignored, OR groups, unknown topic, limitations (and none for needs topics); bound from the committed `config/profile-minimums.yaml`.
2. `ProfileRepository` IT: confirm writes rows with `confirmed_at` into the right table, `stored` reads them persona-scoped, reset deletes only that persona's rows.
3. Turn IT: a turn whose NLU states profile facts leaves both profile tables empty.
4. Orchestrator tests: stored profile reaches the classifier and the profile context; `ASK_PROFILE` draft starts with the fixed sentence and passes `PROFILE_COMPLETE`; a recommendation draft on an incomplete profile still routes `ASK_PROFILE`; stated minimum met + strict fields missing → `RECOMMEND_CATEGORY` with the limitations sentence.
5. Modulith verify and the reset order check pass.

## Implementation Notes

- Scope decisions (Commc 2026-10-26): fixtures hold no profile, reset deletes; the ASK_PROFILE sentence is prefixed deterministically; limitations via prompt placeholder + fixed sentence + reasons.
- Deviation: the AC sentence says "about 3 minutes"; the app writes "about three minutes", because a digit in a fixed sentence fails the Number Check (`NUMBER_MATCH`) and escalates every `ASK_PROFILE` turn.
- The routing template `ResponseTemplates.ASK_PROFILE` now starts with the same sentence ("I need: {missing}" replaces "about you: {missing}").
- The fixed sentences are applied to the first draft and to a regeneration (`TurnOrchestrator.withFixedSentences`), so the checks always see the released text; a blank draft stays blank. The prefix also satisfies the `PROFILE_COMPLETE` "asks" pattern ("I need to know").
- The advisory domain keeps its `ProfileCompleteness` port (pure, lambdas in tests); `ClassificationConfiguration` adapts it to `profile.ProfileCompleteness`. `Classification` and the port `Result` gained `limitations` with the old constructors kept.
- `Profiles.stored` and `confirm` are MANDATORY (AD-21); the IT wraps them in a `TransactionTemplate`.
- Limitations are not computed for the needs topics (`protection_needs`, `protection_duplicate`): the strict FR-17 fields are suitability fields.
- `TurnOrchestrator` grew to ~535 lines (retro A5): extraction of stage classes is due when 6.2 adds the assessment card.

- Review (combined sonnet + Blind Hunter, 2026-10-26), fixed: limitations sentence idempotent on regeneration; the unsupported-topic marker kept out of `missingFields` (adapter); underscores replaced in user-facing missing fields; the prompt no longer asks the model to restate the limitations; `confirm` rejects empty values; tests for `{limitations}`, a stored-profile E2E turn (`RECOMMEND_CATEGORY` with limitations, banded profile context) and a persona-filtered IT query.
- Deferred to 6.2 (form endpoint): value-type validation per field and NUL characters in values; a check that `ProfileFields.NEEDS_TOPICS` matches the yaml.
- Dismissed: the fixed sentence has no digit (Number Check safe); the orchestrator always runs in a transaction (E2E tests cover it).
- Existing E2E expectations updated for the prefix and the real missing fields (`ChatEndToEndTest`, `CheckChainEndToEndTest`). No live Ollama check for this story (prompt change limited to `class-RECOMMEND_CATEGORY.txt`; one live check per block, next with 6.2).
