---
type: architecture-review
lens: good-spine rubric
target: ../ARCHITECTURE-SPINE.md
spec: ../../../prds/prd-FinanceTool-2026-10-20/prd.md
date: 2026-10-20
reviewer: Claude (rubric review, spine not edited)
---

# Rubric Review: Architecture Spine, Autonomous Hybrid Finance Agent

## Verdict

The spine is strong. Its core invariants (pure domain, fixed pipeline, Rule-Engine-only approval, single Action path, atomic audit, simulated clock, pinned configs and models) are sharp, enforceable and match the PRD closely. It still leaves **four high-severity divergence points** open before epics are cut: the `prov_id` scheme does not work for the chat-to-panel linking, the transaction boundary across module-owned repos is undefined, multi-turn chat state (assessment, parameter echo, history exposure) does not fit the fixed pipeline, and the Azure GPU deployment path is unvalidated and heavier than needed. No critical findings.

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 4 |
| Medium | 9 |
| Low | 7 |

## Checklist Summary

| Criterion | Result | Notes |
|---|---|---|
| Fixes the real divergence points for epics, misses none | Partial | Misses H1, H2, H3, M1, M6 |
| Every AD Rule is enforceable and prevents its divergence | Mostly | AD-7 is not achievable as written without H2. AD-19's FR-8 linking fails under the AD-2 / Conventions `prov_id` format (H1). AD-1 depends on CI that is deferred (M5) |
| Nothing under Deferred lets two units diverge | Partial | The persona fixture format is a cross-unit contract (M6) |
| Named tech is current | Mostly | A few versions need a spot check (L1) |
| Covers the spec's capabilities | Mostly | FR-17/18 chat-native assessment, FR-19, FR-38 Undo and FR-40 L2 pending state are only mapped loosely (H3, M1) |
| Every owned dimension is decided, deferred or open | Partial | Azure GPU availability, seeding and migration in Azure, and the latency budget are missing (H4, M7, M8) |
| No over-engineering against "finish fast" | Mostly | The Azure topology and part of the port and ownership ceremony can be thinner (H4, M9) |

## High

### H1. The `prov_id` format breaks FR-8 linking and leaves non-turn figures unresolvable

- **Where:** AD-2, AD-19, Conventions (IDs: `prov_id = <turn_or_run>:<function>:<n>`).
- **Problem:** `prov_id` is scoped to a turn or run and numbered by position. The Financial Overview, Notifications (FR-32) and `/api/simulate` (FR-34) produce figures outside any chat turn or harness run, so their ids have no defined prefix and no persisted ledger to resolve them from. The Evidence ledger lives only in the per-turn Audit Record. FR-8 also requires that a chat figure highlights "the corresponding panel element". A turn-scoped chat id (`t42:cashflow_forecast:3`) can never equal the panel's id for the same figure, so the AD-19 matching rule will never fire. Epics for the Overview, chat and monitoring will each invent their own workaround.
- **Fix:** Make `prov_id` **content-addressed and deterministic**, for example `hash(function, canonical inputs, as_of, rule/KB snapshot ids, output key)`. The same figure then gets the same id wherever it is computed. Persist every `CoreResult` in one `provenance` table owned by `domain/core`'s application service, keyed by `prov_id` and written idempotently. `/api/provenance/{prov_id}` reads that table. Chat, Overview, Notifications and simulate all reference the same ids. The Audit Record keeps a copy in its Evidence ledger.

### H2. No unit-of-work rule, so AD-7's single transaction conflicts with AD-12's per-module ownership

- **Where:** AD-7, AD-10, AD-12, Stack (SQLAlchemy, psycopg).
- **Problem:** AD-7 requires the Audit Record, chat message, EscalationCase, executed Action and (implicitly) the sandbox bank balance changes to commit in one DB transaction. AD-12 says each module's repository writes its own tables, and other modules go through that module's service. Nothing says who opens the session or transaction, or how the `bank`, `actions`, `chat`, `escalation` and `audit` repos share it. Sync versus async SQLAlchemy is also undecided, and that choice shapes every repository and service signature. Left open, each epic will open its own session and AD-7 silently degrades.
- **Fix:** Add an AD: one `UnitOfWork` port, opened only by driving entry points (API request handler, harness item, monitoring run). It is passed into application services, and every repository (including the sandbox bank adapter) uses the session it carries. Services never commit. Pick **synchronous** SQLAlchemy 2.x with psycopg 3 (simpler, and fine for 5–10 personas). FastAPI runs sync endpoints in a threadpool.

### H3. Multi-turn chat state does not fit the fixed pipeline: assessment, parameter echo, history

- **Where:** AD-3, AD-4, AD-5, Capability Map 4.4 and 4.9.
- **Problem:** Three things that need conversational state are not placed in the AD-3 pipeline, and each spans at least two epics:
  1. **Chat-native assessment (FR-17, FR-18).** The assessment is a guided multi-turn dialog that ends in profile writes. AD-5 forbids deserialising LLM output into a profile write, and AD-12 makes `profile` the owner. The spine does not say whether assessment turns go through the orchestrator pipeline, through an `AssessmentService` mode, or how answers are extracted (NLU role?) and confirmed before writing.
  2. **Parameter echo (FR-33, FR-44).** "Extracted parameters are echoed to the user before computation" could mean a confirm-then-compute two-turn flow or showing them alongside the result. The pipeline has no pause stage.
  3. **Conversation history to the LLM.** Nothing says whether previous turns go into the `PromptPayload`. If they do, earlier assistant messages with raw figures would bypass the `none` and `aggregated` Data Exposure Levels (FR-43, E4).
- **Fix:** Add a rule for a per-conversation `DialogState` (`free | assessment(step) | awaiting_param_confirmation`) stored in `chat`. The orchestrator branches on it **before** stage 1. Assessment answers are extracted by the NLU role into a schema, shown to the user, and written only on explicit confirmation via `ProfileService`. Decide the echo semantics. The cheapest option is to show the parsed parameters with the result and let the user re-run through `/api/simulate`, which satisfies FR-34 too. Rule: history passed to the LLM also goes through `domain/exposure`. The simplest safe choice is no history, or user messages only.

### H4. Azure GPU path is unvalidated and heavier than "finish fast" needs

- **Where:** AD-17, Stack (Azure), Deferred (provisioning, model weights, ops).
- **Problem:** Azure Container Apps serverless GPU needs a GPU quota request. It is often unavailable or slow to obtain on student or sponsored subscriptions. It also cold-starts from zero, which means container pull plus loading several 4–5 GB GGUF models. AD-7 (no streaming) makes every turn a single long request: NLU, draft, possible regeneration and Judge, which can be up to four LLM calls on a T4. Container Apps ingress has a request timeout that a cold start can exceed. Azure Database for PostgreSQL Flexible Server allowlists its own `vector` version (via `azure.extensions`), which may differ from the local 0.8.2. That contradicts AD-17's "local and Azure behave the same". This is the riskiest dimension for the user study, and it has no open question or spike.
- **Fix:** Simplify AD-17 to **one Azure GPU VM (for example NCasT4_v3, Sweden Central or another EU region with quota) running the same `docker-compose.yml`**. That gives true environment parity, no Flexible Server, no ACR (build on the VM or pull from GHCR), and nothing to keep warm beyond starting the VM before study sessions. Record the GPU quota as an **open question with a day-1 spike** (request quota now), and name a fallback, for example running the study on the local machine exposed through a tunnel. If Container Apps stays, add `minReplicas=1` for `ollama` during study windows, an explicit request-timeout budget, and pin pgvector to the version Azure allows.

## Medium

- **M1. Action lifecycle, Undo path and Check Chain for Actions are unspecified.** FR-40 needs a pending `AWAITING_CONFIRMATION` state, and FR-38 needs Undo. AD-10 says only MonitoringService and user confirmation call `propose`, but does not say whether Undo goes through `propose`/the gate. FR-25 says every Action passes the Check Chain, while AD-10 only mentions the gate. *Fix:* define the `Action.status` state machine (`PROPOSED → AWAITING_CONFIRMATION → EXECUTED → UNDONE | REJECTED | EXPIRED`), state that Undo is a compensating Action that bypasses the gate but is audited, and state which Rule Engine checks run on Actions before execution.
- **M2. Persona reset contradicts AD-12.** Reset deletes rows owned by `bank`, `chat`, `monitoring`, `actions` and `escalation` from the `personas` module. *Fix:* reset calls a `reset(persona_scope)` method on each owning service inside one unit of work (H2), or AD-12 lists reset as the single explicit exception.
- **M3. Response Outcome under the pure-LLM baseline is undefined.** With classification and block routing off (§8.1), the baseline has no class, but SM-4 compares outcomes for the baseline too. *Fix:* state that the baseline outcome is assigned by the offline scorer role (AD-20) or by the same NLU-plus-rules classifier applied post hoc to the response, and record which.
- **M4. The Judge model is neither chosen nor deferred.** The reference configuration is `A+B+C`, so the Judge runs from the start, but the Stack lists no Judge model and Deferred covers only the scorer. *Fix:* name it (for example the pinned Llama 3.1 8B with a JSON schema) or add it to Deferred and OQ1.
- **M5. AD-1 relies on CI, which is deferred.** "An import-linter contract in CI enforces this", but CI/CD is deferred to the deploy epic. *Fix:* run import-linter as a pytest test (or pre-commit hook) from epic 1, so the rule is enforced locally without CI.
- **M6. The deferred persona fixture format is a cross-unit contract.** `data/personas/*.json` is produced by the data epic and consumed by the sandbox bank adapter, persona reset and the harness (AD-15). Deferring its shape lets them diverge. *Fix:* fix the fixture schema as a Pydantic model in the spine (persona, accounts, transactions with category, contracts, holdings, savings plans, `simulated_date`, `fixture_version`). Leave only the generator internals deferred.
- **M7. Azure operations (migrations, seeding, KB ingestion, model provisioning) are not decided or deferred.** How Alembic runs, how fixtures and KB embeddings reach the Azure DB, and how Modelfiles and `lock.json` digests are created there are all missing from Deferred. *Fix:* add one line: "on start, the `app` container runs `alembic upgrade head`; a `harness seed` command loads fixtures and ingests the KB; models are created from `models/` by an init script, and `lock.json` is verified at startup (AD-18)". Or defer it explicitly to the deploy epic.
- **M8. No latency budget for a non-streaming turn.** Up to four sequential LLM calls (NLU, draft, regeneration, Judge) with no streaming can mean tens of seconds per turn in the user study. *Fix:* state a target (for example p95 under 30 s locally), the HTTP timeout, the UI "thinking" state, and cache NLU results (already in AD-4). Consider keeping the NLU and phrasing models the same in the reference configuration to avoid model swaps on a single GPU.
- **M9. Some ceremony can be trimmed for speed.** Seven ports plus per-module repositories and the ten-module ownership table are more structure than one author needs. *Fix:* keep the pure-domain rule (AD-1), `LlmPort`, `BankPort` and `ClockPort` as real ports. Let `RuleParamPort` and `KnowledgePort` be plain application-layer modules until a second implementation exists. Treat AD-12 as "write through the owning service" without a separate repository class per table.

## Low

- **L1. Stack versions need a spot check.** I cannot independently confirm several versions against their known release cadence: Ollama 0.40.x, FastAPI 0.156.x, uvicorn 0.54.x, Typer 0.27.x. "TypeScript 5.x" may be stale if TS 6 is current. PostgreSQL 17 is a sound proven choice even though 18 exists. Re-verify and pin exact versions in the `uv` and npm lockfiles at setup.
- **L2. How persona and advisor credentials are stored is not stated.** One line is enough, for example: seeded users with argon2 or bcrypt hashes in `users`, fixed passwords listed in the study protocol.
- **L3. The location of Mandate templates (FR-36) is not stated.** Suggest `config/mandate_templates.yaml`, validated by Pydantic, alongside `autonomy.yaml`.
- **L4. RFC 9457 with a closed error-code enum is more than a prototype needs.** It is cheap, but a simple `{code, message}` body would also do.
- **L5. AD-4 uses an LLM for intent extraction, while FR-9 says "deterministically".** Temperature 0, a seed and caching make it reproducible, but not rule-based. Record this in §10 Threats to Validity.
- **L6. The FR-19 profile re-confirmation mechanism is not placed.** Monitoring cannot write `profile` tables (AD-12). State that monitoring calls `ProfileService.mark_stale(fields)`, and that the classifier treats stale fields as incomplete (which gives `ASK_PROFILE`).
- **L7. The Overview data flow is implicit.** Say whether `/api/overview` computes Core results on each request (deterministic, given H1 content-addressed ids) or reads cached results. Recomputing is simplest.

## What Is Good (keep as is)

- AD-1, AD-3, AD-6, AD-10 and AD-13 directly encode the thesis's design principles, and each has a testable rule.
- AD-8 (simulated clock) and AD-16 (idempotent monitoring, no scheduler) remove a whole class of reproducibility bugs at almost no cost.
- AD-14 and AD-18 (configuration as data, run manifests, digest-pinned models) are exactly what the DSR evaluation needs.
- AD-15 (harness in-process over the same services) prevents the classic "evaluated path differs from the demo path" problem.
- The Deferred list is honest and mostly safe. Gate values, KB content, scorer choice and UI library are correctly left open.
