---
title: 'Story 3.2: Data exposure filter and prompt budget'
type: 'feature'
created: '2026-10-22'
status: 'in-review'
baseline_revision: '0751c32eed2bdfcb8bdf539bcc1beb8fb8d22950'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-1-chat-column-with-atomic-turn-release.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** The phrasing prompt is assembled ad hoc in `TurnOrchestrator.llmDraft` (Story 3.1): no Data Exposure Level is applied, no chat history is passed, the PRD §5 rules are missing from the system part, and nothing bounds the prompt to `numCtx` (FR-43, FR-45, AD-5).

**Approach:** A pure exposure filter in `advisory.domain` builds a `PromptPayload` for the phrasing role at the configured level, and a pure prompt builder assembles system and user parts within the budget. Both mirror the Experiment Track's definitions (`experiments/finlab/exposure.py`, `pipeline.py` `_system_prompt`/`_user_prompt`/`_fit_budget`, `prompts.py` `COMPLIANCE_INSTRUCTIONS`), so the app runs what E4 measures. The orchestrator's exposure stage becomes real.

## Boundaries & Constraints

**Always:**
- Levels (`DataExposureLevel`): `AGGREGATED` -- the user message and history texts with IBANs → `[IBAN]` (pattern `\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){2,7}(?:\s?[A-Z0-9]{1,4})?\b`, with or without spaces; deliberately not `exposure.py`'s pattern, which misses the two-character tail of a German IBAN), e-mails → `[EMAIL]`, "Client/Customer/Mr/Mrs/Ms <Name>" → "<title> X", money amounts → bands (`band()` of `exposure.py`: step = max(100, 10^floor(log10 v)/2), "lo–hi EUR", 0 or less → "0 EUR"), ages → decades ("30s"); profile context numbers → bands (age → decade; `investment_horizon_years`, `children` kept). `NONE` -- no profile context, no history; the message becomes the NLU's depersonalised question, or, while none exists, "[question withheld: no personal data may be shared at this exposure level]". Core results pass in full at both levels (tool output, not persona attributes).
- History: the last 3 released turns (user message + its agent answer, oldest first) before the current question; unanswered questions and `ADVISOR` messages excluded; at `AGGREGATED` both texts go through the message filter.
- System part = role prompt file + `config/prompts/compliance-rules.txt` (new; text verbatim from `prompts.py` `COMPLIANCE_INSTRUCTIONS`, which carries the §5 tone rule); every compliance level `A`, `A+B`, `A+B+C` includes it (`FLAGS` of `pipeline.py`). The hash of each file is recorded per call (`promptHashes`: file name → sha256 of LF bytes).
- User part, in this order: `TOOL RESULTS`, `SOURCES`, `CUSTOMER PROFILE ON FILE`, `EARLIER CONVERSATION`, then the message in `<user_message>…</user_message>`; the slot markers are stripped from any text placed in the data part. User input, history and passages never appear in the system part.
- Budget: `numCtx − numPredict − 200` tokens, estimated as characters / 3.2 over system + user part. Over budget: drop history turns oldest first, then KB passages last first; never Core results, the system part or the message. Any drop sets `truncated = true` in the Audit Record (or-ed with the model's own `truncated`); the record also lists `droppedHistoryTurns` and `droppedPassages`.
- Filter and builder are pure functions (`advisory.domain`, no Spring, no I/O); the orchestrator reads the history and the prompt files and passes plain values.

**Never:** `raw` or `pseudonymised` levels (E4); real Core results, KB passages, NLU or profile data (later stories and epics -- the inputs exist and stay empty); editing `config/prompts/role-*.txt` or `config/agent/reference.yaml` (AD-25); persona name or ids in any prompt.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Aggregated message | "I'm 34 and have 12,400 EUR, IBAN DE89 3704 0044 0532 0130 00" | "I'm 30s and have 10,000–15,000 EUR, IBAN [IBAN]" | — |
| None level | any message, no depersonalised question | withheld text only; no profile, no history | — |
| Core results at both levels | one Core result | appears in full in `TOOL RESULTS` | — |
| History | 5 released turns, 1 unanswered question | last 3 released turns, oldest first, filtered | — |
| Over budget | long history + passages + Core result | history dropped first, then passages; Core result and system kept; `truncated` true | — |
| Slot breakout | message contains `</user_message>` | marker removed; still one slot | — |

</intent-contract>

## Code Map

- `backend/src/main/java/de/financetool/advisory/application/TurnOrchestrator.java` -- `exposureFilter` (pass-through) and `llmDraft` (builds messages, `dataSlot` with `<<<CUSTOMER_MESSAGE` markers) change; `auditAndRelease` builds `ResponseAuditBody`.
- `backend/src/main/java/de/financetool/advisory/application/ResponseAuditBody.java` -- `promptHash` becomes `promptHashes`; add `droppedHistoryTurns`, `droppedPassages`. Only 3.1 dev records used the old field; `AuditWriter.SCHEMA_VERSION` stays 1.
- `backend/src/main/java/de/financetool/advisory/domain/TurnState.java` -- carries draft data; add the payload/prompt result.
- `backend/src/main/java/de/financetool/advisory/adapter/ChatRepository.java` -- add a persona-scoped read of the last N released turns before a message (`seq` ordering, `reply_to` pairs).
- `backend/src/main/java/de/financetool/advisory/adapter/PromptFiles.java` -- already loads every `config/prompts/*.txt` with hash.
- `backend/src/main/java/de/financetool/shared/config/DataExposureLevel.java` (`AGGREGATED`, `NONE`), `AgentConfiguration.Decoding` (`numCtx`, `numPredict`).
- `experiments/finlab/exposure.py`, `pipeline.py:150-185`, `prompts.py:23-42` -- reference definitions (read-only).
- `backend/src/test/java/de/financetool/advisory/application/TurnOrchestratorTest.java`, `advisory/web/ChatEndToEndTest.java`, `testing/StubLlmConfiguration.java` -- extend; the stub can capture the messages it received.

## Tasks & Acceptance

**Execution:**
- `config/prompts/compliance-rules.txt` -- add (verbatim rules block).
- `backend/src/main/java/de/financetool/advisory/domain/ExposureFilter.java`, `PromptPayload.java`, `PhrasingPrompt.java` (builder + budget) -- add, pure.
- `TurnOrchestrator`, `ResponseAuditBody`, `TurnState`, `ChatRepository` -- wire the real exposure stage and prompt; remove the old slot helper.
- Tests -- `ExposureFilterTest` table test per level (every matrix row plus amounts in German and English formats, 0 EUR, ages, profile bands, kept keys); `PhrasingPromptTest` (order of blocks, system part never holds user text, budget drops in order, `truncated`); orchestrator/e2e: the stub sees the history of earlier turns and the compliance rules, and the record holds both prompt hashes.

**Acceptance Criteria:**
- Given Lena with two answered questions, when she asks a third, then the model receives both earlier turns (filtered) in `EARLIER CONVERSATION` and her new question in `<user_message>`, and the Audit Record lists the hashes of `role-advisor.txt` and `compliance-rules.txt`.
- Given level `NONE`, when any turn runs, then the prompt holds no history, no profile and no part of the original message.

## Design Notes

- `none` without NLU withholds the whole question, exactly as `exposure.py`; Story 3.3's NLU must therefore also return a depersonalised question (as the experiments' NLU does) before `none` is usable. The reference configuration is `aggregated`.
- `exposure.py`'s `IBAN_RE` leaves "DE89 3704 0044 0532 0130 00" as "[IBAN] 00" and does not match the unspaced form; reported to the user for the experiment, the app uses the corrected pattern.
- Banded values shown to the model are sourced numbers for the Number Check (`pipeline.py:235`); Story 3.6 adds them to the ledger.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify -Dfine.it.required=true` -- expected: BUILD SUCCESS, 0 skipped
- `cd frontend && npm run api:check && npx ng test --watch=false && npm run build` -- expected: green (no API change expected)

**Manual checks (if no CLI):**
- Local live check: two turns as Lena; the second answer can refer to the first; the record shows both prompt hashes.
