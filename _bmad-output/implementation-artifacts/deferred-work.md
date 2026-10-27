
## Deferred from: code review of spec-1-6-llm-behind-one-port-served-by-a-pinned-ollama-model (2026-10-21)

- `LlmResult.truncated` misses prompts Ollama cut or served from the KV cache (`prompt_eval_count < numCtx`). Maybe-false, medium if true; settle with the AD-5 prompt budget (own token count) in the advisory epic.

## Deferred from: epic-end review of Epics 6 and 7 (2026-10-26)

- `WhatIfEndToEndTest` covers only Jonas's happy path: no case for a non-eligible persona (subsidy 0), a contribution below the Riester floor, or a net-income figure that changed between proposal and Calculate. Low; add a parameterised case when Epic 7 grows.
- `ShowChatUseCase.needsStatement()` loads every message of the persona's chat to find the latest statement; a `ChatRepository` query (`needs_statement IS NOT NULL ORDER BY created_at DESC LIMIT 1`, scoped by persona) would keep Home loads flat. Low for pilot chat lengths.
- The fixed range text rounds amounts half up (`ResponseTemplates.amount`) while the Number Check rounds sourced values half even; harmless while Core EUR outputs carry two decimals (the blocked-range orchestrator test relies on this gap).

## Deferred from: code review of spec-8-1-autonomy-gate-and-action-proposals (2026-10-27)

- `CoreGateway.gateMeasures` derives the emergency fund after an Action only from the source account: an `EF_TOP_UP` into the overnight deposit leaves `emergencyFundAfter` unchanged, so a Mandate floor check could cap it at L2 wrongly. Low until Mandates exist; add the target account to `gateMeasures` in Epic 9.
- `Actions.propose` has no dedupe: two calls for the same trigger, type and as-of store two Actions and two Audit Records. Maybe-false, medium if true; settle in Story 8.2 (monitoring call pattern, dedupe per trigger).
- source_spec: `_bmad-output/implementation-artifacts/spec-8-2-monitoring-run-at-a-simulated-date-with-all-mvp-triggers.md`
  summary: An EF_BELOW_TARGET Trigger is keyed per month, so a first run during a forecast shortfall stores the info-only note and no EF_TOP_UP is proposed later that month after the shortfall clears.
  evidence: Trigger key `(overnight id, yyyy-MM)` is frozen in the Story 8.2 intent; changing it needs a human decision (e.g. a variant in the period).

## Deferred from: Epic 8 epic-end review (2026-10-27)

- `GateReasons.eur` and `NotificationTemplates.eur` format amounts twice (`ActionController` uses the monitoring copy for the dialog sentence, `GateReasons` its own for reasons). Low; move one formatter to `shared` when a third caller appears.
- `ActionState.proposedAt(L3)` throws: once Epic 9 lets `MandateLookup.active`/`covering` return a Mandate, every L3-eligible Action would roll back the monitoring run and study prepare. Epic 9 must add L3 execution (or map L3 to L2 with a criterion) in the same story that returns the first Mandate; add a guard test there.
- A superseded transfer (a newer Action of the same type waits for the OK) still shows "Needs your OK" until the user confirms it and gets the "newer suggestion" refusal. Low; a `SUPERSEDED` state (migration + chip) would hide it.

## Deferred from: Epic 9 epic-end review (2026-10-27)

- The monthly rule limit counts only `EXECUTED` rule transfers (`MandateRepository.executedSum`), so an undone transfer frees its budget again (net reading: the limit caps money that stays moved). Kept as is; revisit if the study needs a gross cap (`state IN ('EXECUTED','UNDONE')`).
- `undoable` in the Notification list does not check `asOf >= executedOn`; unreachable today because the list hides items after the simulated date.
