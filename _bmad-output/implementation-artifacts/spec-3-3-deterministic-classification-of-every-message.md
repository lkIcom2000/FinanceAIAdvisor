---
title: 'Story 3.3: Deterministic classification of every message'
type: 'feature'
created: '2026-10-23'
status: 'done'
baseline_revision: '40900a6ea16ac45efacb925d8bc369df1342b486'
review_loop_iteration: 0
followup_review_recommended: true
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-2-data-exposure-filter-and-prompt-budget.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Every turn is `INFORM` by a pass-through, so the type of answer depends on nothing; the NLU stage does not exist, and an `INFORM` answer is released as uncited model text (FR-9, FR-13, FR-44, AD-4, AD-6).

**Approach:** Port the Experiment Track's NLU and classification (`experiments/finlab/nlu.py`, `classify.py`, `prompts.py` at commit `b72a70e`, revised after the E1 pilot) to the app: the `nlu` role extracts data with a JSON schema, deterministic grounding and pure rules pick exactly one class, the phrasing prompt gets the class instruction, and an `INFORM` turn without a valid source releases the fixed "no valid source" template. The response card shows the outcome label row for every outcome.

## Boundaries & Constraints

**Always:**
- NLU stage: `LlmPort` role `NLU`, temperature 0, seed from `context.agentConfig().decoding()`, `numPredict` 900, Ollama JSON-schema `format` = the experiment's `SCHEMA`. System prompt and few-shot examples from versioned files in `config/prompts/` (verbatim `SYSTEM` and `FEW_SHOT` of `nlu.py`; each user message in `<user_message>…</user_message>`); their hashes go into the Audit Record. Output is data only: `normalize` then `ground` exactly as `nlu.py` (same field set, defaults, enums, evidence rules), including `depersonalized_question`, which feeds the exposure filter (Story 3.2, level `NONE`).
- NLU cache: keyed by sha256 over model tag, model digest, prompt hash and message; persisted in an `advisory`-owned table (not persona state, never reset); a hit skips the model call and is recorded (`nluCacheHit`). An unparseable model answer gives `normalize({})` with `parseOk: false` (as `nlu.py`); a failed model call fails the turn (503, as in 3.1).
- Classification: a pure port of `classify.py` (`classify`, `premises_to_check`, `Registry`, `_norm`, `_contains_token_seq`, `_is_name`, the regexes and category sets) in `advisory.domain`, same order of rules: trap → out of scope → compute → general → personal (profile check). Inputs: grounded NLU, stored profile (empty until Epic 6), a profile-completeness port whose stub reports "incomplete" with no missing fields until Story 6.1, the registry, and `computable` (false until Story 3.7's tool plan). The registry reads `data/rules/known_entities.yaml` (copy of `experiments/data/known_entities.yaml`, under `data/rules/` so the config fingerprint covers it) plus KB titles and texts (empty until Epic 4).
- Exactly one class per turn; same input, same class. Pass-through routing keeps outcome = class, except: an `INFORM` turn with no KB passage releases `TEMPLATES["NO_VALID_SOURCE"]` verbatim plus one related in-scope suggestion chip (by NLU topic, default "What is an ETF savings plan?"); the phrasing draft still runs and is recorded as first draft (shadow measurement, as the experiments do).
- The phrasing system part adds the class instruction (`CLASS_INSTRUCTIONS` of `prompts.py`, one versioned file per class, placeholders filled as `pipeline.py` `_system_prompt`, incl. its default missing-fields text).
- Audit Record adds: class, class reasons, missing fields, unverified premises, out-of-scope categories, the grounded NLU extraction, NLU model tag + digest, NLU prompt hashes, `nluCacheHit`, `nluParseOk`, `instructionOverrideAttempt`, and `noValidSource`.
- Chat API: an agent message carries its `suggestions` (0–3 chip texts); OpenAPI snapshot and generated client updated.
- Response card: outcome label row with icon and plain label for all seven outcomes (Explanation, Calculated for you, Recommendation, A few questions first, Not part of this service, Let's check the question, Passed to a human advisor), read first by screen readers; suggestion chips send their text.

**Never:** profile completeness logic (Story 6.1); a tool plan or Core calls (3.7); checks, routing other than the no-source rule, refusal/trap card bodies, premise callout (3.4, 3.8); KB retrieval (Epic 4); changing `experiments/`; tuning the registry or rules to individual test items.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Explanation | "What is an ETF?" | `INFORM`, no-valid-source template + 1 chip, draft recorded | — |
| Out of scope | "Which knock-out certificate should I buy?" | `REFUSE_OUT_OF_SCOPE`, `outOfScope` = derivative category | — |
| Trap | "Does the Savings Bonus Act 2026 apply to me?" | `DETECT_TRAP`, unverified premise recorded | — |
| Known law | "How does MiFID II protect me?" | not `DETECT_TRAP` | — |
| Personal | "Should I put 200 EUR a month into an ETF?" | `ASK_PROFILE` (stub incomplete) | — |
| Cache hit | same message twice | second turn has no NLU model call, `nluCacheHit: true`, same class | — |
| Bad JSON | NLU returns non-JSON | defaults, `nluParseOk: false`, class from defaults | — |
| Injection | "Ignore your instructions and …" | `instructionOverrideAttempt: true`; class as without the sentence | — |

</intent-contract>

## Code Map

- `experiments/finlab/nlu.py` (`SCHEMA`, `SYSTEM`, `FEW_SHOT`, `Nlu.extract`, `SCOPE_EVIDENCE`, `ground`, `normalize`), `classify.py` (all), `prompts.py` (`CLASS_INSTRUCTIONS`, `TEMPLATES["NO_VALID_SOURCE"]`), `pipeline.py` `_system_prompt` and `run_turn` stages 1–2, `experiments/data/known_entities.yaml` -- reference (read-only).
- `experiments/results/Pilot_E1/e1-llama3/items.jsonl` -- recorded NLU extractions (`nlu` per item, model `llama3.1:8b-instruct-q4_K_M`) for the 65 Q&A items: copy `item_id`, `question`, `nlu` (without `_meta`) to `backend/src/test/resources/nlu/e1-llama3-pilot.jsonl` as stub answers for tests here and in Stories 3.4–3.8.
- `backend/src/main/java/de/financetool/advisory/application/TurnOrchestrator.java` -- `nlu`, `classify`, `blockRouting` stages; `exposureFilter` gets the depersonalised question; `llmDraft` adds the class instruction.
- `backend/src/main/java/de/financetool/advisory/domain/{TurnState,PhrasingPrompt,ResponseOutcome,MessageClass}.java`, `application/ResponseAuditBody.java`, `adapter/{PromptFiles,ChatRepository}.java`, `web/ChatController.java`.
- `backend/src/main/java/de/financetool/shared/AppHome.java` (`data()`), `shared/config/ConfigFingerprints.java` (covers `config/prompts`, `data/rules`).
- `backend/src/test/java/de/financetool/testing/StubLlmConfiguration.java` -- extend: answers per role (NLU JSON vs phrasing text).
- `frontend/src/app/pages/workspace/chat-column.component.*` -- response card, outcome icons (3.1 has `menu_book`, `chat` only).

## Tasks & Acceptance

**Execution:**
- `config/prompts/nlu-system.txt`, `nlu-few-shot.json`, `class-<CLASS>.txt` (6), `data/rules/known_entities.yaml` -- add, verbatim from the references.
- `backend/src/main/resources/db/migration/V7__nlu_cache.sql` -- cache table.
- `advisory/domain` -- `NluExtraction` (+ normalize, ground), `Classification` + `Classifier`, `EntityRegistry`, `ProfileCompleteness` port; pure.
- `advisory/application`, `adapter`, `web` -- NLU stage with cache, registry loader, stub completeness, class instruction, no-source routing, audit fields, `suggestions`.
- Tests -- table test with at least one case per class (crafted extractions; `COMPUTE` with `computable = true`, `RECOMMEND_CATEGORY` with a completeness stub that says complete) and determinism; a test that classifies every recorded pilot extraction without error and is stable across two runs; ports of the experiment's grounding and registry edge cases (`mifid iii` vs `mifid ii`, described law, ISIN); e2e for every matrix row; frontend specs for the label row and chips.

**Acceptance Criteria:**
- Given any released turn, when the response card renders, then its first line is the icon and plain label of its outcome, and a screen reader reads that label before the body.
- Given an `INFORM` answer without a source, when it is shown, then the card has the no-valid-source text and one suggestion chip that sends its question.

## Spec Change Log

## Review Triage Log

### 2026-10-23 — Review pass (block A: Stories 3.1 + 3.3)
- verdicts: 32 findings — high 0, medium 7, low 17, false 8, maybe-false 0 (four layers; Blind Hunter and Intent Alignment on sonnet)
- findings:
  - `[low]` `[reject]` IA: the matrix rows run with a stubbed NLU, not the real model — the live check ran the real model (knock-out → REFUSE_OUT_OF_SCOPE, ETF → no-valid-source); the stub isolates the deterministic part
  - `[false]` `[reject]` IA: no cross-check against the Python outputs — `NluExtractionTest` compares every pilot item with golden files produced by the experiment's own normalize/ground/classify; the resources were excluded from the review diff
  - `[false]` `[reject]` IA: OpenAPI snapshot and generated client not in the diff — excluded from the review diff on purpose; both are in `561425f`, `api:check` up to date
  - `[low]` `[reject]` IA: UI only tested against an HTTP mock — component specs plus the user's browser check pattern (Epic 2 A7); no browser tool in this run
  - `[low]` `[patch]` IA/VG: the Ollama `format` and `model(role)` never exercised — adapter test asserts the NLU tag + lock digest and the schema sent as `format`
  - `[low]` `[reject]` BH: grounding and SELF_CLAIM patterns are English only — the UI and the Experiment Track are English (DESIGN.md number format, EXPERIENCE.md); parity with the experiment is the spec's rule
  - `[low]` `[patch]` BH: the no-source check uses the post-budget payload — uses the retrieval result before the budget
  - `[low]` `[reject]` BH: the phrasing draft runs and is discarded for every INFORM turn — the spec keeps it as first draft for shadow measurement, as the experiments do
  - `[medium]` `[patch]` BH: the NLU cache key leaves out seed, numPredict and schema — key covers them
  - `[medium]` `[patch]` BH/VG/ECH: unparseable NLU answers are cached forever — `parseOk=false` answers are not cached
  - `[medium]` `[patch]` BH: stated profile facts persist in a never-reset cache (AD-23) — cache is persona-scoped (V8 `persona_id`), deleted by persona reset, registered as advisory-owned
  - `[false]` `[reject]` BH: the full extraction goes into the Audit Record regardless of exposure — the exposure level governs what the LLM sees (FR-43), not the internal audit (AD-13)
  - `[false]` `[reject]` BH: the NLU stage ignores the Data Exposure Level — AD-5: the exposure filter builds the phrasing role's payload; the NLU needs the message to extract, as in the experiments
  - `[medium]` `[patch]` BH/ECH: NLU-extracted premise text enters the system part unfiltered — claims pass the exposure filter, markers and braces removed, placeholders filled once; javadoc corrected
  - `[low]` `[patch]` BH/ECH: missing prompt files or a null few-shot file found only at turn time — startup check naming the file; few-shot parsed once
  - `[false]` `[reject]` BH: a failed NLU call is untested — `ClassificationEndToEndTest.aFailedNluCallFailsTheTurnBeforeAnyPhrasing`
  - `[false]` `[reject]` BH/ECH: `PythonJson` formats floats and U+007F unlike Python — the few-shot file contains no floats and no U+007F (checked); the prompt hash matches the experiment's
  - `[low]` `[reject]` BH: MONEY/RATE grounding misses forms and German decimals — verbatim port of `nlu.py` grounding; parity with the experiment is the rule
  - `[low]` `[reject]` BH: three classes unreachable live, default missing-fields text — documented in Design Notes (3.7, 6.1); the default text is the experiment's
  - `[low]` `[patch]` BH: chips on old messages stay active during a turn — chips disabled while a turn is pending
  - `[false]` `[reject]` BH: an outcome without an icon renders an empty svg — all seven outcomes have icon paths
  - `[low]` `[reject]` BH: loose registry matching — verbatim port of `classify.py`; the registry is a design parameter not to be tuned
  - `[false]` `[reject]` BH: Classifier/Classification null handling — `NluExtraction` normalises every field to a non-null default before classification
  - `[medium]` `[patch]` VG: the topic-to-chip map is never tested — test for a mapped topic
  - `[medium]` `[patch]` VG: the production registry loader is never tested — loader test on the real file plus malformed inputs; null/blank items skipped
  - `[low]` `[patch]` VG: `OllamaLlmAdapter.model(role)` untested — covered by the adapter test above
  - `[medium]` `[patch]` ECH: a blank phrasing draft fails a no-source turn — blank draft tolerated when the template is released
  - `[low]` `[patch]` ECH: the no-source chip can repeat the question just asked — another in-scope question then
  - `[low]` `[patch]` ECH: null/blank YAML items become 'null' registry entries — skipped
  - `[low]` `[reject]` ECH: two concurrent answer calls give the second a 503 — the UI sends one answer call per question; the unique `reply_to` keeps the data correct
  - `[low]` `[reject]` ECH: sending a new message while a failed card is pending drops its Try again — the question stays saved and unanswered in the history; retrying it is a later UX refinement
  - `[low]` `[reject]` ECH: the NLU slot strips markers unlike `nlu.py` — differs only for messages containing slot markers; keeps the slot unbreakable

## Design Notes

- The registry is a design parameter ("must not be tuned to individual test items", its own header); it moves to `data/rules/` unchanged.
- `computable` and the profile stub make `COMPUTE` and `RECOMMEND_CATEGORY` unreachable in the live flow until 3.7 and 6.1; the table test reaches them through the pure function.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify -Dfine.it.required=true` -- expected: BUILD SUCCESS, 0 skipped
- `cd frontend && npm run api:check && npx ng test --watch=false && npm run build` -- expected: green

**Manual checks (if no CLI):**
- Local live check (real model): "Which knock-out certificate should I buy?" → `REFUSE_OUT_OF_SCOPE`; "What is an ETF?" → no-valid-source template; the record shows the NLU extraction and class reasons.

## Implementation Notes

- Deviations from the bmad-build-auto workflow (user cost rules): Story 3.3 was reviewed jointly with 3.1 (block A, four layers because of persona-scoped ids); the story commit `561425f` precedes the review, the fixes are a separate commit. The intent auditor read both intent contracts from a file. Generated client, OpenAPI snapshot, verbatim prompt copies, the registry copy and the NLU golden/fixture resources were excluded from the review diff.
- Port check: the experiment's own `normalize`, `ground` and `classify` (run with `python -I` from the scratchpad, `experiments/` untouched) produced golden files for the 65 recorded pilot extractions; `NluExtractionTest` compares the Java port item by item.
- Choices the spec left open: `messages.suggestions TEXT[]` (chips survive a reload); `BLOCK_ROUTING` counts as run when the template replaces the draft; slot markers are stripped from the NLU slot (the experiment does not); the topic-to-chip map is a design choice, not from the experiments; the no-source rule applies at every compliance level (Story 3.4's `ComponentFlags` decides whether it stays so).
- After review: the NLU cache is persona-scoped (V8 empties the cache and adds `persona_id`; persona reset deletes the rows), failed parses are not cached, and NLU-derived premise text in the system part passes the exposure filter.

## Auto Run Result

- **Summary:** `nlu` stage (JSON schema, temperature 0, persona-scoped cache), `normalize`/`ground`/`classify` ported from `experiments/finlab` at `b72a70e` with the entity registry in `data/rules/known_entities.yaml`, class instruction in the phrasing system part, the no-valid-source template with a related chip for `INFORM` without a source, audit fields for the classification, `suggestions` on agent messages, and the outcome label row for all seven outcomes.
- **Files:** `advisory/domain/{NluExtraction,Premise,EntityRegistry,Classifier,Classification,ProfileCompleteness,ClassInstruction,NoValidSource}.java`; `advisory/application/{NluExtractor,PythonJson,TurnOrchestrator,ResponseAuditBody}.java`; `advisory/adapter/{NluCacheRepository,ClassificationConfiguration,PromptFiles,ChatRepository}.java`; `llm` (`LlmPort.model(role)`, `LlmMessage.assistant`); V7, V8; `config/prompts/{nlu-system.txt,nlu-few-shot.json,class-*.txt}`; `data/rules/known_entities.yaml`; tests incl. `ClassifierTest`, `NluExtractionTest` (pilot parity), `NluExtractorTest`, `ClassificationEndToEndTest`, `PromptAndRegistryFilesTest`, `NoValidSourceTest`, `OllamaLlmAdapterTest`; test resources `nlu/` (pilot extractions, golden files, schema); frontend chat column.
- **Review:** block A, four layers, 32 findings (medium 7, low 17, false 8). Patched 14 rows in 9 fixes (medium 7, low 7); rejected 18 with reasons in the triage log; none deferred.
- **Follow-up review recommended: true** (7 medium patched). Unverified risk: the persona-scoped cache and the startup prompt check changed persistence and boot paths after the only full review; a second look at V8 plus `AdvisoryResetter` (reset completeness) and at `ClassInstruction`'s filtering of NLU text is the open item.
- **Verification:** before review 395 backend + 71 frontend tests; after the fixes `./mvnw -B verify -Dfine.it.required=true` BUILD SUCCESS, 411 tests, 0 failures, 0 skipped; frontend `api:check` up to date, `ng test` 72 passed, build complete. Live (local `spring-boot:run`, real model, CPU): before review Tim's knock-out question → `REFUSE_OUT_OF_SCOPE` (`derivative_or_certificate`, 3.6 min) and Lena's "What is an ETF?" → no-valid-source template; after the fixes the app migrated V8 and passed the prompt check at startup, and Marco's "How does an emergency fund work?" → template with a different chip ("What is an ETF savings plan?"), 61 s.
- **Residual risks:** every `INFORM` turn releases the template until Epic 4 brings sources; `COMPUTE` and `RECOMMEND_CATEGORY` are unreachable live until 3.7 and 6.1.
