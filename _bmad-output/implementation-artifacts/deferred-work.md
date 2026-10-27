
## Deferred from: code review of spec-1-6-llm-behind-one-port-served-by-a-pinned-ollama-model (2026-10-21)

- `LlmResult.truncated` misses prompts Ollama cut or served from the KV cache (`prompt_eval_count < numCtx`). Maybe-false, medium if true; settle with the AD-5 prompt budget (own token count) in the advisory epic.

## Deferred from: epic-end review of Epics 6 and 7 (2026-10-26)

- `WhatIfEndToEndTest` covers only Jonas's happy path: no case for a non-eligible persona (subsidy 0), a contribution below the Riester floor, or a net-income figure that changed between proposal and Calculate. Low; add a parameterised case when Epic 7 grows.
- `ShowChatUseCase.needsStatement()` loads every message of the persona's chat to find the latest statement; a `ChatRepository` query (`needs_statement IS NOT NULL ORDER BY created_at DESC LIMIT 1`, scoped by persona) would keep Home loads flat. Low for pilot chat lengths.
- The fixed range text rounds amounts half up (`ResponseTemplates.amount`) while the Number Check rounds sourced values half even; harmless while Core EUR outputs carry two decimals (the blocked-range orchestrator test relies on this gap).

## Deferred from: code review of spec-8-1-autonomy-gate-and-action-proposals (2026-10-27)

- `CoreGateway.gateMeasures` derives the emergency fund after an Action only from the source account: an `EF_TOP_UP` into the overnight deposit leaves `emergencyFundAfter` unchanged, so a Mandate floor check could cap it at L2 wrongly. Low until Mandates exist; add the target account to `gateMeasures` in Epic 9.
- `Actions.propose` has no dedupe: two calls for the same trigger, type and as-of store two Actions and two Audit Records. Maybe-false, medium if true; settle in Story 8.2 (monitoring call pattern, dedupe per trigger).
