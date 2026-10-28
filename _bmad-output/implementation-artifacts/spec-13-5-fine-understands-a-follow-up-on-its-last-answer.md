---
title: 'Story 13.5: Fine understands a follow-up on its last answer'
type: 'feature'
created: '2026-10-28'
status: 'done'
baseline_commit: 'f0b7a4458eb4d55cb2c8072c33a526f84358a05f'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-13-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Follow-ups like "Why?" or "And with 200 €?" get answers without context: at exposure `none`, `ExposureFilter.apply` drops all history, and the NLU never sees history at any level (FR-63). `DialogState` covers only the profile form and the what-if proposal.

**Approach:** Each agent message stores its follow-up context (depersonalised question, whether its text was LLM-phrased, its Core figure provIds). Before the NLU, the turn builds exactly one previous released turn through the exposure filter and gives it to both the NLU and the phrasing prompt; the budget drops it first. The ledger sources only that turn's Core figures.

## Boundaries & Constraints

**Always:**
- Exactly one previous turn (`HISTORY_TURNS = 1`), the same filtered turn for NLU and phrasing, at both levels.
- `aggregated`: user and agent text through `filterMessage` (as today).
- `none`: user text = the stored depersonalised question (`WITHHELD` if none); agent text = the released text only if LLM-phrased (draft released, outcome `INFORM`, `COMPUTE` or `ASK_PROFILE`, no parameter proposal); otherwise (Suitability Statement, demands-and-needs statement, refusal/trap, escalation, proposal, any routed template) only its Core figures as a fixed-format line, never its text. A message without stored context (older rows) gives no previous turn at `none`.
- ADVISOR-authored messages never enter the context (the history query joins `AGENT` only).
- Ledger: previous-turn numbers are sourced only as `CORE` entries (label: provId) for the stored figures, read back via `EvidenceReader.figures` (AD-2), and only when the budget kept the turn. The current deviation "numbers in an earlier user message" is removed.
- NLU cache key includes the previous-turn text; NLU drops the previous turn if its prompt would exceed `numCtx − 900 − 200` estimated tokens.
- `DialogState` keeps precedence (stage 1 runs first, unchanged).
- Prompt changes (`nlu-system.txt`, `role-advisor.txt`) are a documented E4 deviation; `experiments/finlab` stays unchanged; prompt hashes change in the Audit Record.

**Never:** more than one previous turn; raw persona text at `none`; template texts at `none`; recomputing previous figures; changing `DialogStates`, `Classifier`, `CheckChain`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Follow-up at none | Lena: emergency-fund `INFORM` answer, then "Why?" | NLU and phrasing get depersonalised Q + phrased answer; answer quoting the earlier Core figure passes Number Check | N/A |
| Template at none | previous turn a Suitability Statement / refusal / escalation | agent text = figures line only (or the empty-figures marker) | N/A |
| Aggregated | previous turn with IBAN/amounts | filtered texts as today, one turn only | N/A |
| No previous turn / old row | first message; row without context at none | no history block, NLU prompt as today | N/A |
| Over budget | small `numCtx` | previous turn dropped first, `droppedHistoryTurns = 1`, its figures not in ledger | N/A |
| Open proposal | `PARAMETER_PROPOSAL` then "yes" | DialogState route as today | N/A |

</frozen-after-approval>

## Code Map

- `backend/src/main/resources/db/migration/V24__message_followup_context.sql` -- new: `messages.followup JSONB` (AGENT only, CHECK like V15).
- `advisory/domain/ChatMessage.java` -- add nullable `FollowUp(String question, boolean phrased, List<String> provIds)`; `ChatRepository.insert` / row mapping write and read it.
- `advisory/adapter/ChatRepository.java:138` `releasedTurnsBefore` -- also select the context of the chosen message (`f` row if present, else `a`); returns raw rows (user text, agent text, `FollowUp`).
- `advisory/application/ReleaseStage.java:55-85` -- build `FollowUp` from `turn.nlu().extraction().depersonalizedQuestion()`, `draftReleased`, outcome, `proposal`, provIds of `turnTools.numbers()`/`results()`.
- `advisory/domain/ExposureFilter.java:74-84` -- new `previousTurn(level, row)` → `Optional<HistoryTurn>`; `apply` takes the already-filtered history; `HISTORY_TURNS = 1`.
- `advisory/domain/PromptPayload.java:29` `HistoryTurn` -- add `List<SourcedNumber> coreFigures` (keep a 2-arg constructor if tests need it).
- `advisory/domain/NumberLedger.java:75-80` -- replace USER history entries by CORE entries of kept history turns.
- `advisory/application/TurnOrchestrator.java:141` NLU stage -- fetch + filter the previous turn (new `TurnState` field, e.g. `previous`), pass to `NluExtractor.extract`; `PromptStage.exposureFilter` reuses it instead of querying again.
- `advisory/application/NluExtractor.java:61-118` -- `messages(...)` adds the previous turn as one data block in the final user message before the slot (`EARLIER CONVERSATION` + `Customer:`/`Assistant:` lines via `PhrasingPrompt.oneLine/data`); key includes it; budget via `PhrasingPrompt` estimate.
- `config/prompts/nlu-system.txt`, `config/prompts/role-advisor.txt` -- one sentence each: the current message may be a follow-up on EARLIER CONVERSATION; extract/answer the current message, resolve references from the earlier turn.
- Tests: `ExposureFilterTest`, `NumberLedgerTest`, `PhrasingPromptTest`, `NluExtractorTest`, `TurnOrchestratorTest`, `ChatEndToEndTest` (history assertions), new `advisory/web/FollowUpEndToEndTest.java` (pattern: `PersonalAnswerEndToEndTest`).

## Tasks & Acceptance

**Execution:**
- [x] `V24__message_followup_context.sql`, `ChatMessage.java`, `ChatRepository.java` -- store and read the follow-up context.
- [x] `ReleaseStage.java` -- write `FollowUp` on every agent message.
- [x] `ExposureFilter.java`, `PromptPayload.java` -- one-turn filter incl. `none` rules and the figures line.
- [x] `TurnState.java`, `TurnOrchestrator.java`, `PromptStage.java`, `NluExtractor.java` -- build once before NLU, pass to NLU and phrasing; NLU budget + cache key.
- [x] `NumberLedger.java` -- CORE-only sourcing of the previous turn.
- [x] `nlu-system.txt`, `role-advisor.txt` -- follow-up sentence.
- [x] Unit tests for each matrix row; `FollowUpEndToEndTest` at `none`: Lena's emergency-fund question then "Why?"; NLU and phrasing prompts hold the depersonalised question and phrased answer, no raw message; stubbed answer citing the earlier figure is released; a template previous turn shows no text.

**Acceptance Criteria:**
- Given a follow-up at `none`, when the turn runs, then the Audit Record's `nluPromptHashes`/`promptHashes` reflect the changed prompt files and its ledger lists the previous turn's figures as `CORE`.
- Given the full build, when `./mvnw -q verify -Dfine.it.required=true` runs, then all tests pass.

## Implementation Notes

- E4 deviation (documented): `config/prompts/nlu-system.txt` and `role-advisor.txt` each gained one follow-up sentence; the NLU call and the phrasing prompt carry one `EARLIER CONVERSATION` turn at both exposure levels, where `experiments/finlab` (`nlu.py`, `exposure.py`) ran single-turn and dropped history at `none`. `experiments/finlab` is unchanged; the Audit Record's `nluPromptHashes`/`promptHashes` change with the files.
- `ExposureFilter.StoredTurn` is the raw row (`ChatRepository.releasedTurnsBefore`, follow-up context of the `f` row if present, else `a`); `PromptStage.previousTurn` reads the figures via `EvidenceReader.figures` and filters it once in the NLU stage (`TurnState.previous`); `PromptStage.exposureFilter` reuses `TurnState.history()`.
- Template figures line at `none`: `ExposureFilter.TEMPLATE_FIGURES` (`[fixed answer, not shown at this exposure level; its Core figures: <label: value unit; ...> | none]`), figures formatted like `TOOL RESULTS` (`ToolResults.figure`).
- NLU: the block goes before the slot in the final user message; dropped when all NLU messages exceed `numCtx − 900 − 200` estimated tokens (`PhrasingPrompt.estimatedTokens`). Cache key unchanged without a previous turn (old entries stay valid), else `sha256(key|earlier|block)`.
- `FollowUp.phrased` = phrased at exposure `none`, draft released, no proposal, outcome `INFORM`/`COMPUTE`/`ASK_PROFILE` (review patch: a text phrased at `aggregated` counts as a template, so a level switch to `none` cannot carry banded profile text into the next prompt). `provIds` = distinct provIds of `turnTools.numbers()`.
- Test stub `StubLlm` now finds the NLU message after a preceding block (last `<user_message>`).
- Workflow deviation (build-loop economy): review ran as one combined reviewer (edge case + spec conformance + verification gap) plus a separate Blind Hunter (persona data), not three layers; patches applied inline.
- Side effect: the NLU cache key now includes the previous turn, so a repeated message after a different previous turn is a cache miss (`ClassificationEndToEndTest` adjusted).

## Spec Change Log

## Review Triage Log

| # | Source | Finding | Verdict | Route / evidence |
|---|--------|---------|---------|------------------|
| 1 | Blind Hunter | At `none` a phrased previous answer passes verbatim even if it was phrased at `aggregated` before a config switch | medium | patch: `ReleaseStage.followUp` sets `phrased` only at `NONE`; exposure level is one config file read at start, conversations persist across restarts |
| 2 | Combined + BH | NLU and phrasing budgets may drop the previous turn independently | low | reject: spec defines both budgets; NLU prompt ≈1.9k of 3k tokens, divergence rare; fix adds an extract return channel |
| 3 | Combined + BH | `phrased` rule not asserted for all template outcomes (RECOMMEND_CATEGORY, ESCALATE, DETECT_TRAP) | low | patch: `aTemplateAnswerIsNotPhrased` iterates every `ResponseOutcome` |
| 4 | Combined | `ASK_PROFILE` fixed prefix reaches the model at `none` | false | spec lists `ASK_PROFILE` as phrased; the prefix is generic text without persona data |
| 5 | Combined + BH | Evidence lookup returning fewer figures than provIds is silent/untested | low | reject: evidence is append-only, unlikely; safe direction (fewer CORE sources) |
| 6 | BH | `ExposureFilter.apply` no longer guards history at `none` itself | low | reject: single caller passes `previousTurn` output; no demonstrated leak |
| 7 | BH | Removing USER ledger source for earlier user numbers regresses `aggregated` | false | frozen intent: "ledger sources only that turn's Core figures" |
| 8 | BH | Earlier Core figures sourced without freshness check | false | frozen intent: read back, never recompute |
| 9 | BH | Continuation turns also get a previous turn, untested | low | reject: DialogState precedence unchanged; `CalculatedAnswerEndToEndTest` covers proposal→answer |
| 10 | BH | NLU cache hit rate drops, undocumented | low | reject as defect: follows from spec's cache-key rule; documented in Implementation Notes |
| 11 | BH | Vestigial multi-turn API (`limit`, list, `reduce`) with `HISTORY_TURNS = 1` | low | reject: cosmetic, signature churn |
| 12 | BH | Test gaps (aggregated E2E ledger, null NLU, repository round-trip, CHECK constraint) | low | reject: covered at unit level or pre-existing patterns; low everyday risk |
| 13 | BH | Code quality: FQN `java.util.Set`, missing `withPrevious` Javadoc, mixed `turn`/`withPrevious`, long Javadoc line | low | patch: direct corrections |

## Verification

**Commands:**
- `cd backend && JAVA_HOME=C:\Users\commc\.jdks\temurin-25 ./mvnw -q test -Dtest='ExposureFilterTest,NumberLedgerTest,PhrasingPromptTest,NluExtractorTest,TurnOrchestratorTest'` -- expected: green
- `cd backend && JAVA_HOME=C:\Users\commc\.jdks\temurin-25 ./mvnw -q verify -Dfine.it.required=true` -- expected: 0 failures, 0 skipped
