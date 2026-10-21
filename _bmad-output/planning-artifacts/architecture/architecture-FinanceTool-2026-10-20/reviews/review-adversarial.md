# Adversarial Review: Architecture Spine (FinanceTool, 2026-10-20)

**Lens:** build two units one level down (epics or stories built separately, e.g. by different coding agents). Each unit follows every AD exactly, yet the two still cannot fit together. Each such pair is a hole, closed by the smallest new or tightened AD.
**Reviewed:** `ARCHITECTURE-SPINE.md` (AD-1 to AD-20) against `prd.md`.
**Constraint honoured:** solo author who wants to finish fast. Each fix is one rule or one shared type, not detail for each story.

## Verdict

**Not yet safe to build on. The skeleton is sound, but the seams leak.** The rules about direction (AD-1, AD-3, AD-5, AD-6) are strong. The rules about how parts fit together are weak. AD-12 says who owns each table, but it does not say who opens a transaction, how one module causes a change in a table another module owns, or which shared types cross those boundaries. That leaves 12 incompatible pairs below. 8 of them are blocking. All 12 close with about 5 short new ADs plus amendments to AD-2, AD-10, AD-12 and AD-14. None of the fixes needs work on individual stories.

| # | Severity | Hole | Fix |
|---|---|---|---|
| H1 | blocking | Who owns the transaction across tables owned by different modules | new AD-21 Unit of Work |
| H2 | blocking | Evidence and `prov_id` are persisted only for chat turns; Overview, Notifications and simulate have none; highlighting cannot match | amend AD-2, new AD-22 |
| H3 | blocking | Profile writes from chat vs. assessment; confirmation; FR-19 staleness; two completeness functions | amend AD-12 (`profile` row) + AD-5 note |
| H4 | blocking | Notification vs. Action lifecycle; a second `propose()` on L2 confirm | amend AD-10 (state machine), AD-16 |
| H5 | blocking | Undo and Revoke: through the gate or around it | amend AD-10 |
| H6 | blocking | Advisor reply must enter chat `messages`; escalation cases that have no message | amend AD-12 + AD-7 |
| H7 | blocking | Data Confidence and gate context computed in two places | amend AD-10 |
| H8 | blocking | Config is a global in one unit and passed per call in another; KB snapshot drift; retrieval-off vs. source check | new AD-23 RunContext, amend AD-9 |
| H9 | major | Persona reset reaches across owned tables | amend AD-12 / AD-13 |
| H10 | major | `/api/simulate` vs. chat what-if: two parameter shapes, unaudited numbers | fold into AD-22 + shared `SimulationSpec` |
| H11 | major | `SourcedNumber.unit` is free-form, so the Number Check compares `0.05` against "5%" | amend AD-2 |
| H12 | minor | Actions started from chat: AD-10 allows "user confirmation" but nothing defines the proposer | amend AD-10 |

---

## H1. Transactions across owned tables (AD-7 vs. AD-12)

**Pair.** *Unit A (chat/orchestrator)* follows AD-12: it calls `AuditService.record()`, `EscalationService.open_case()` and `ChatRepo.add_message()`. Each service opens its own SQLAlchemy session and commits, which is the usual repository pattern. *Unit B (audit)* follows AD-7 and AD-13: append-only, its own repository. Result: the message commits, then the audit write fails, and the turn is released without an audit record. AD-7 is broken even though every unit obeyed it locally. Monitoring has the same problem with Trigger + Action + Audit + Notification: AD-7 names only the chat endpoint, so a monitoring unit can execute a bank transfer and write the Notification in separate transactions.

**Fix: AD-21 Unit of Work.** Repositories and services never commit. Every *use case* (chat turn, monitoring run per Trigger, action confirm/undo/revoke, mandate create, advisor reply, reset, set date) opens exactly one `UnitOfWork` at the driving adapter or application entry point. It passes the UoW down and commits once. Each service method that writes takes `uow`. AD-7's rule that all writes commit together or none do applies to every use case, not only chat.

## H2. Evidence persistence and `prov_id` linking (AD-2, AD-13, AD-19)

**Pair 1.** *Unit A (Overview, FR-4 to FR-6)* calls Core functions to render balances and the forecast. No turn exists, so it creates prov_ids like `overview:cashflow_forecast:1` and returns them (AD-19 requires prov_ids). *Unit B (`/api/provenance/{prov_id}`)* reads the Evidence ledger, which AD-13 says lives only inside `audit_records.payload` for kinds `response|action`. Every Overview "Why?" returns 404. Notifications (FR-32, "figures with their provenance") hit the same 404 for live monitoring runs, where `run_id` is null and no turn exists.

**Pair 2.** FR-8 highlighting. Chat's ledger produces `t-123:emergency_fund_target:1` and the Overview produces `overview:emergency_fund_target:1`. AD-19 says to highlight "elements with matching ids". These ids never match, because `prov_id` is unique per call by construction.

**Pair 3.** The provenance endpoint has to find a `prov_id` inside JSONB across all audit records. One unit adds a GIN index. Another parses the turn_id prefix. A third keeps an in-memory cache. All three are compliant, and none works with the others.

**Fix (amend AD-2 + new AD-22 Evidence store).**
- `SourcedNumber` gains `figure_key`. This is a deterministic, meaningful key such as `emergency_fund.target` or `cashflow.shortfall.2026-12`, built by the Core function from (function, subject, period). FR-8 highlighting matches on `figure_key`. `prov_id` is used only for lookup.
- Every `CoreResult`, from any caller (turn, overview, monitoring, simulate), is written to an append-only `evidence_items(prov_id PK, persona_id, origin: turn|overview|monitoring|simulate, origin_id, core_result JSONB, check_summary JSONB NULL, created_at)` table owned by `audit`. Use the same trigger that rejects updates and deletes. The Audit payload lists `prov_id`s and does not hold the ledger inline (or holds both, but `evidence_items` is the store the endpoint reads).
- `prov_id` = `<origin>:<origin_id>:<function>:<n>`. `/api/provenance` is a primary-key lookup scoped by persona. The "Check Chain result" shows only when origin = turn.
- Persona reset keeps `evidence_items`, as it keeps audit records.

## H3. Who writes profile fields (AD-5, AD-12, FR-17, FR-19)

**Pair 1.** *Unit A (orchestrator)*: the user types "my income is 3,400". NLU extracts `income=3400` (stage 1), and the orchestrator calls `ProfileRepo.update`. That breaks AD-12, so it routes through "the profile service" instead. *Unit B (assessment)*: AD-5 says nothing deserialises LLM output into "a profile write", and NLU is an LLM role. So Unit B builds `/api/profile` with form confirmation and ignores the chat. The capability map lists both `application/assessment` and a `profile` owner, but the application layer has no `ProfileService`. The two units ship two write paths, or none.

**Pair 2.** FR-19 says a salary-change Trigger marks fields for re-confirmation. *Monitoring* may not write `suitability_profiles` (AD-12), so it writes a `PROFILE_REVIEW` Notification. *Classification* (AD-4) computes "profile complete" from the profile table, which still says complete. The next Recommendation goes through without re-confirmation.

**Pair 3.** "Complete and confirmed" appears in three places: classification (AD-4), the Rule Engine profile gate (FR-25), and the autonomy criterion "investment without complete, confirmed Suitability Profile" (FR-35). Three units implement three predicates.

**Fix (amend AD-12 `profile` row, one line in AD-5).**
- `AssessmentService` is the only writer of profile tables. Each field is stored as `{value, source: prefill|user, confirmed_at|null, stale_since|null}`.
- A field becomes confirmed only through a structured confirm event: `POST /api/assessment/confirm {field, value}` sent from a confirm control rendered in the chat message. Never from NLU or LLM text. NLU may *propose* a value, which is shown for confirmation (the same pattern as FR-44 echo).
- Monitoring calls `AssessmentService.mark_stale(scope, fields, trigger_id)`.
- One pure predicate, `domain/profile.completeness(profile) -> {suitability_complete, needs_complete, stale_fields}`, is used by classification, the Rule Engine and the gate.

## H4. Notification creation vs. the Action lifecycle (AD-10, AD-12, AD-16, FR-32, FR-38, FR-40)

**Pair 1.** *Unit A (monitoring)* owns `notifications` and writes one for each Trigger at detection time, with outcome `recommended`. It then calls `ActionService.propose()`. *Unit B (actions)* follows FR-38 ("every L3 Action produces a Notification immediately"), but may not write `notifications`. So it emits nothing, or adds its own `action_events` feed that the UI polls separately. The result is a Notification that says "recommended" for an Action that was executed, or two feeds.

**Pair 2.** L2 confirmation (FR-40). *Unit A (UI/API)* posts `/api/actions/{id}/confirm`. *Unit B (actions)* reads AD-10's "an explicit user confirmation calls `propose`" and creates a *new* ProposedAction. The result is a duplicate Action row, a second gate decision, and an orphaned pending row. The Undo control on the Notification (FR-7) also needs an `action_id` that the Notification row does not carry.

**Fix (amend AD-10 + AD-16).**
- Action state machine, owned by `actions`: `PROPOSED → INFORMED (L0/L1) | AWAITING_CONFIRMATION (L2) | EXECUTED (L3) ; AWAITING_CONFIRMATION → EXECUTED | DECLINED ; EXECUTED → UNDONE`.
- `ActionService.confirm(action_id)` moves the *same row* forward. It re-runs the gate and the mandate check at that moment, and records a second gate decision in the audit.
- MonitoringService writes the Notification *after* `propose()` returns, in the same UoW (H1). It stores `trigger_id`, `action_id|null`, and the initial outcome taken from the gate result.
- The Notification outcome the UI shows is *derived* by joining to `actions.state`. Notification rows are never updated by `actions`.

## H5. Undo and Revoke (AD-10, FR-38)

**Pair.** *Unit A* reads "propose() is the only entry point" and implements Undo as `propose(compensating_action)`. The gate then caps it: Data Confidence `low` gives L1, and with no mandate covering the reverse direction it gives L2. The one-tap Undo becomes "please confirm", or is refused. *Unit B* implements `ActionService.undo()` as a direct `BankPort` call, matching "Undo is a compensating operation recorded as its own Action". Both units comply with the spine. They differ in behaviour and in SM-5 metrics. Revoke has the same split: one unit uses a soft `revoked_at`, the other deletes the mandate. Both then rely on "re-checked at execution time".

**Fix (amend AD-10).** `ActionService` has exactly three entry points that write: `propose`, `confirm`, and `undo/revoke`. Undo **bypasses the gate**, because it is user-initiated and restores the prior state. It is allowed only if the original is `EXECUTED`, is not yet undone, and falls inside its reversibility window by the simulated clock. It writes a new Action with `compensates=<id>` and its own audit record. Revoke sets `mandates.revoked_at = ClockPort.as_of`. Mandate rows are never deleted except by reset.

## H6. Escalation cases and advisor replies (AD-7, AD-11, AD-12, FR-41, FR-42)

**Pair 1.** *Unit A (escalation)* writes `advisor_replies`, as AD-12 says. *Unit B (chat UI + chat API)* polls `messages` for the conversation (AD-19 "advisor replies arrive by polling", but it does not say from which resource). The reply never shows up in the chat. If escalation inserts into `messages` instead, it breaks AD-12.

**Pair 2.** The ER diagram has `MESSAGE ||--o| ESCALATION_CASE`. The PRD's Scenario set allows an expected outcome of `ESCALATE` for monitoring, and AD-9 says `NoValidRuleParameter → ESCALATE` wherever Core runs, including inside monitoring. A monitoring-path escalation has no message, so *Unit A (monitoring)* cannot create a case, while *Unit B (escalation)* requires `message_id NOT NULL`.

**Pair 3.** AD-11 says the advisor can read "only EscalationCase". FR-42 requires the case to show the question, the drafted answer and the block reason, and those live in audit/messages. One unit copies them onto the case. Another stores an `audit_record_id` and joins, which the advisor-scope rule then forbids.

**Fix (amend AD-12 and AD-7).**
- `EscalationService.reply(case_id, text)` calls `ChatService.append_advisor_message(conversation_id, text, case_id)` in one UoW. `messages.author` is `user|assistant|advisor`. Advisor text bypasses the Check Chain and is labelled in the UI.
- `escalation_cases` has `origin: turn|action`, nullable `message_id`/`action_id`, and a **copied snapshot** (`question`, `first_draft`, `block_reasons`, `outcome`) plus `audit_record_id` for traceability. For action-origin cases, the reply goes to the persona's most recent conversation, or a new one if none exists.

## H7. Data Confidence and gate context (AD-10, FR-35)

**Pair.** `gate(action, context, thresholds)` is pure, but nothing says who builds `context`. *Unit A (monitoring)* computes Data Confidence in `domain/triggers`, since it already has the snapshot, with a 90-day window anchored on the latest transaction date. It passes it inside `ProposedAction`. *Unit B (actions)* computes it again in `domain/autonomy`, anchored on `as_of` and with thresholds from `config/autonomy.yaml`. The two give different levels for the same Scenario. "Liquid assets" (the 5% criterion) has the same problem, and so does "complete, confirmed profile" (H3).

**Fix (amend AD-10).** `ProposedAction` carries only `{type, amount, source_account, target_account, trigger_id, regulatory_class}`. `ActionService` alone builds `GateContext{mandate, liquid_assets, profile_completeness, data_confidence, reversible}` through owner read services at both `propose` and `confirm`. The only implementations are `domain/autonomy.data_confidence(transactions, as_of, thresholds)` (window ends at `as_of`) and `domain/core.liquid_assets(accounts)`.

## H8. Config loading, snapshots, and retrieval off (AD-9, AD-14, AD-15, AD-17)

**Pair 1.** *Unit A (orchestrator)* loads `ACTIVE_CONFIG` (AD-17) into a module-level singleton at startup. *Unit B (harness)* resolves `configs/e1-llama2.yaml` and calls the orchestrator in-process (AD-15). Every item silently runs with the reference config. Gate thresholds (`config/autonomy.yaml`) and seeds have the same problem.

**Pair 2.** AD-9 says the snapshot id is the "content hash" of the files. *Unit A (harness/manifest)* hashes `data/kb/*.md`. *Unit B (KB adapter)* serves whatever was last ingested into pgvector. After an edit without re-ingest, the manifest records snapshot X while retrieval serves Y.

**Pair 3.** With `retrieval: false` (E1, FR-24), *Unit A (orchestrator)* skips stage 4. *Unit B (rules)* applies the source-validity check to every `INFORM` and fails it because no citation exists. AD-9/FR-52 map that to `ESCALATE`, so the entire retrieval-off arm escalates. Even with retrieval on, FR-13 ("says so instead of answering") and FR-52 ("source not valid → ESCALATE") give different outcomes when nothing valid is retrieved.

**Fix (new AD-23 RunContext + amend AD-9).**
- `application/config.resolve(config_path|None) -> RunContext{config, config_hash, run_id|None, thresholds, thresholds_hash, rule_snapshot, kb_snapshot, seed_for(turn_id)}`. Services receive the RunContext as an argument on every call. Reading config at module level is forbidden, with an import-linter or grep check. The live app resolves once per request from `ACTIVE_CONFIG` (default `configs/reference.yaml`).
- `kb_snapshot` is computed at ingest and stored in a `kb` meta row. At resolve time the files are hashed and compared with the indexed snapshot, and resolve fails fast on a mismatch.
- `ComponentFlags` includes `retrieval`. When it is off, source checks return `applicable=false`. Routing: "retrieved nothing valid" gives `INFORM` with a fixed no-source template (FR-13). "Draft cites an expired or unknown source", or `NoValidRuleParameter`, gives `ESCALATE` (FR-52).

## H9. Persona reset across owned tables (AD-12, AD-13, FR-3)

**Pair.** *Unit A (personas/research)* implements reset as a single SQL script that deletes from every table except `audit_records`. That is fast, but it breaks AD-12. *Unit B (bank adapter)* expects reset to *reload fixtures* (accounts, transactions) and keeps sandbox state in its own tables, possibly in memory. A delete-only reset leaves the persona with no accounts. The harness (AD-15) calls a third implementation.

**Fix (amend AD-12).** `PersonaResetService.reset(scope)` calls each owner's `reset(scope, uow)` in a fixed order (escalation → chat → monitoring → actions → profile → bank `load_fixture` → persona_state) in one UoW. `audit_records` and `evidence_items` are kept. The research route and the harness both call this one service.

## H10. `/api/simulate` vs. chat what-if (FR-33, FR-34, AD-4, AD-19)

**Pair.** *Unit A (chat)* takes `{monthly_savings, horizon_years, …}` from NLU, runs Core, and stores the chart in the message. *Unit B (simulate API)* defines its own request model `{rate, years, lump}` and calls `domain/core` directly from the router. It writes no evidence and no audit, so "Why?" on an adjusted chart fails and the parameter names do not round-trip. Reloading the chat shows the original chart, and nothing says whether it should.

**Fix.** One `domain/core.SimulationSpec` Pydantic model is used by the NLU output mapping, the tool plan, the message payload and `/api/simulate`. `/api/simulate` goes through `application/simulation.run(scope, spec, ctx)`, which writes `evidence_items` with origin `simulate` (H2). An adjusted result does not change the chat message. The message keeps the original spec, and the UI holds the adjusted spec in client state.

## H11. Units in the Evidence ledger (AD-2, conventions)

**Pair.** *Unit A (Core)* follows the Percent convention and emits `SourcedNumber(value=0.05, unit="fraction")`. *Unit B (Number Check)* parses "5%" from the draft as `5` and compares raw values, which gives a mismatch, a regeneration, then `ESCALATE`. The same thing happens with `pp`, months vs. years, and the `count` of withdrawals (522).

**Fix (amend AD-2).** `unit` is a closed enum `EUR|fraction|pp|months|years|count|date`. The Number Check compares against unit-aware display variants (fraction ×100 as %). The user-message numbers in the ledger are extracted by the *same* `domain/rules/number_parse` the Number Check uses, never by NLU.

## H12. Actions started from chat (AD-10, FR-44)

**Pair.** "Move €500 to my savings": *Unit A (orchestrator)* builds a ProposedAction from NLU parameters after the user clicks confirm. *Unit B (actions)* follows AD-5 ("nothing deserialises [LLM output] into … an Action") and rejects it. The spine allows both readings.

**Fix (one line in AD-10).** In the MVP, Actions come only from monitoring Triggers. Chat never proposes Actions. It can refer the user to the pending Notification. If that changes later, add a structured action form, never NLU.

---

## Checked and found tight enough

- The LLM cannot select tools or Actions (AD-3, AD-5): single owner, no gap.
- Approval path and routing table (AD-6): there is one function, so no second implementation fits.
- Monitoring idempotency keys (AD-16) behave correctly across Undo and reset, once H4/H9 are in place.
- Advisor isolation (AD-11) holds once H6 copies the snapshot onto the case.

## Suggested patch size

Add **AD-21** (UoW), **AD-22** (Evidence store + `figure_key`) and **AD-23** (RunContext). Amend **AD-2** (unit enum, figure_key), **AD-9** (indexed snapshot, retrieval-off routing), **AD-10** (state machine, three entry points, GateContext built by ActionService, no chat-originated Actions), **AD-12** (profile write path + completeness predicate, advisor message via ChatService, case origin/snapshot, reset service) and **AD-16** (Notification after propose, outcome derived). That is about 25 lines of spine text.
