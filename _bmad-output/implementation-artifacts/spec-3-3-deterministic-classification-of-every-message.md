---
title: 'Story 3.3: Deterministic classification of every message'
type: 'feature'
created: '2026-10-23'
status: 'in-progress'
baseline_revision: '40900a6ea16ac45efacb925d8bc369df1342b486'
review_loop_iteration: 0
followup_review_recommended: false
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

## Design Notes

- The registry is a design parameter ("must not be tuned to individual test items", its own header); it moves to `data/rules/` unchanged.
- `computable` and the profile stub make `COMPUTE` and `RECOMMEND_CATEGORY` unreachable in the live flow until 3.7 and 6.1; the table test reaches them through the pure function.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify -Dfine.it.required=true` -- expected: BUILD SUCCESS, 0 skipped
- `cd frontend && npm run api:check && npx ng test --watch=false && npm run build` -- expected: green

**Manual checks (if no CLI):**
- Local live check (real model): "Which knock-out certificate should I buy?" → `REFUSE_OUT_OF_SCOPE`; "What is an ETF?" → no-valid-source template; the record shows the NLU extraction and class reasons.
