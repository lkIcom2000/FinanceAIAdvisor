# Epic 3 Context: Ask Fine — Checked Answers in Chat

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

A persona can chat with Fine, and every turn follows one fixed, deterministic pipeline: rules classify the message, the orchestrator picks whitelisted tools, the LLM only phrases, the Rule Engine checks, one routing function decides the outcome, and the turn is released atomically with its Audit Record. Out-of-scope requests, false-premise traps and injection attempts get calm, transparent answers, and blocked cases are handed to a human advisor. This epic establishes the core claim of the prototype: the LLM is an untrusted text edge, and nothing reaches the user unchecked or unaudited.

## Stories

- Story 3.1: Chat column with atomic turn release
- Story 3.2: Data exposure filter and prompt budget
- Story 3.3: Deterministic classification of every message
- Story 3.4: Check Chain, Rule Engine and compliance levels
- Story 3.5: Hand over to a human advisor when the answer is blocked
- Story 3.6: Numbers only from the Deterministic Core — the Number Check
- Story 3.7: Calculated answers with parameter proposal and figure chips
- Story 3.8: Transparent refusals, trap detection and injection resistance
- Story 3.9: Block-only Judge under A+B+C — **skipped** (needs the E3 experiment result)

## Requirements & Constraints

- **Atomic release, no streaming.** The chat endpoint returns one complete released turn. Audit Record (`RESPONSE`), agent message, evidence items and any `EscalationCase` are written in one transaction; if the audit write fails, nothing is released. Each turn ends in exactly one `ResponseOutcome` (one of six classes or `ESCALATE`).
- **Deterministic classification.** Exactly one of `INFORM`, `ASK_PROFILE`, `RECOMMEND_CATEGORY`, `COMPUTE`, `REFUSE_OUT_OF_SCOPE`, `DETECT_TRAP` is chosen by rules before phrasing; class and final outcome go into the Audit Record. Same input always gives the same class.
- **Release only on pass.** A response is released only if every applicable check passes. Every block records which check fired. All shadow (first draft, every configuration) and enforcing results are persisted.
- **Numbers.** Every persona or calculation number must be in the per-turn Evidence ledger (Core items, Rule Parameter values, cited KB numbers, user-message numbers). Rounding to shown precision; "about"/"roughly" allows 5 %; "%" becomes a fraction; dates, list positions, article/paragraph numbers excluded; English and German number formats parsed. A mismatch triggers exactly one regeneration, then `ESCALATE`.
- **No valid source.** An `INFORM` turn with retrieval off or no valid source releases a fixed template with outcome `INFORM`, a related in-scope chip and no uncited model knowledge.
- **Scope and safety.** Out of scope: single stocks, derivatives (incl. knock-outs), crypto, forex/CFD, building-society savings, insurance tariff selection, individual tax advice, legal assessment of letters/contracts. No promised returns or capital protection, no buy/sell advice from expected price or rate movements. Injection text must not change the class or the Check Chain outcome and must not reveal instructions.
- **Data minimisation.** The phrasing role sees only what the Data Exposure Level allows; only `aggregated` and `none` are implemented (`raw`/`pseudonymised` not, unless E4 selects one).
- **Tone.** English, plain language, answer first, no pressure; every refusal ends with a next step. Internal vocabulary (`ASK_PROFILE`, `provId`, Check Chain) never appears in customer text; checks are "checks". No latency target, but turns over 20 s must be handled gracefully.

## Technical Decisions

- **Pipeline (AR6).** Only the `advisory` orchestrator runs a turn; it consults `DialogState` first, then runs 11 stages in fixed code order: NLU, classify, tool plan, KB retrieve, exposure filter + budget, LLM draft, shadow checks, Rule Engine, Judge, block routing, audit + release. Unimplemented stages are explicit pass-throughs. **KB retrieval (stage 4) is a pass-through in Epic 3** (Epic 4 delivers it), so `SOURCE_VALIDITY` is not applicable. **Judge (stage 9) is a pass-through** while 3.9 is skipped.
- **`ComponentFlags`** is the only interpreter of the compliance level: stage 8 shadow/enforcing/enforcing, stage 9 off/off/on, stage 10 off (outcome = class)/on/on for `A`/`A+B`/`A+B+C`. Under `A` the Audit Record notes Shadow Mode. Default level comes from `config/agent/reference.yaml`.
- **Experiment-dependent values stay configuration.** Model, role prompt, compliance level, exposure level and decoding live in `config/agent/reference.yaml` and `config/prompts/*` (with `derivedFrom` blocks); no experiment findings are patched into code. E1–E4 results are not yet available.
- **LLM edge.** All calls via `LlmPort.generate(role, messages, decoding, schema)` with roles `phrasing`, `nlu`, `judge`; output is text only. NLU runs at temperature 0, fixed seed, Ollama JSON-schema `format`, cached by model digest + prompt hash + input hash; its output is data only. User input and retrieved text go only into delimited data slots; system prompt from a versioned `config/prompts/` file, hash recorded per call. Chat history: last released turns only, through the same filter. Budget over `numCtx`: drop history first, KB passages second, never Core results or system part; record `truncated = true`.
- **Classification** is pure rules in `advisory.domain`. Profile completeness is a stub reporting "incomplete" until Story 6.1 supplies `profile.ProfileCompleteness`. Stated profile facts apply to the turn only and are never persisted.
- **Check Chain.** Closed `CheckId` enum (`SCOPE`, `PROFILE_COMPLETE`, `SUITABILITY`, `COST_TRANSPARENCY`, `NO_PRODUCT_NAMING`, `NO_GUARANTEE_OR_TIMING`, `CONFLICT_OF_INTEREST`, `IDD_NEEDS`, `DOCUMENTATION`, `NUMBER_MATCH`, `SOURCE_VALIDITY`) and `CheckResult(checkId, applicable, passed, reasonCode, details)`; unimplemented checks return `applicable = false` explicitly.
- **Routing.** `BlockRouting.route(results)` is the only FR-52 implementation: scope → `REFUSE_OUT_OF_SCOPE`; profile incomplete → `ASK_PROFILE`; Number Check → one regeneration; invalid Rule Parameter or KB source → `ESCALATE`; other failure → `ESCALATE`; Judge block/escalate → `ESCALATE`. Only `ESCALATE` creates an `EscalationCase`.
- **Tools.** Tool plan is a pure function of class + extracted parameters over one whitelist registry bean (Core, KB, bank-read). The LLM never names, selects or calls tools. `advisory` must not depend on `actions` (Modulith graph test).
- **Modules and tables.** `advisory` owns `conversations`, `messages` (`author ∈ USER, AGENT, ADVISOR`), `dialog_states`; `escalation` owns `escalation_cases` (origin `TURN|TRIGGER`, snapshot of question, draft, block reasons, status `OPEN|CLOSED`). Each new table gets a Flyway migration and registers with persona reset. Allowed deps for `advisory`: `core`, `core :: results`, `profile`, `knowledge`, `llm`, `audit`, `escalation`, `personas`. Use cases own the `REQUIRED` transaction; writing services are `MANDATORY`. Every use case receives an explicit `RequestContext`.
- **Core results** come only via `CoreGateway` (types in named interface `core :: results`); the per-turn ledger is built from these items. `WithdrawalDuration` reference: Q&A item 40 (500,000 EUR, 2,000 EUR/month, 4 %) gives 522 full withdrawals at 4 % p.a. effective, 538 at 4 %/12 nominal, each plus a reduced final withdrawal.
- **Audit payload** (via `AuditPayloadMapper`): `asOf`, class, outcome, evidence `provId`s, all `CheckResult`s, Judge verdict/parse status only when run, model tags/digests, prompt hashes, `truncated`, config id + fingerprint, seeds, first draft, final text, regeneration flag.
- **Testing.** Unit and integration tests use an `LlmPort` stub; real Ollama only in local live checks. Table tests for classification, flags, routing, exposure levels and Number Check (≥ 20 cases). Injection test runs over `experiments/redteam.csv` and Q&A item 65. The first real `AuditWriter` caller (turn audit + release, Story 3.1) adds an end-to-end test through `/api/research/audit/export` (research profile).

## UX & Interaction Patterns

- **Composer:** Enter sends, Shift+Enter newline, max 1,000 chars with counter from 800, disabled during a turn ("Fine is checking the answer…"); typing a new question cancels an open proposal card.
- **Waiting card:** timer-driven steps (Understanding your question, Getting your figures, Writing the answer, Checking the rules), no ticks for unconfirmed steps, slow-turn message after 20 s, never times out. **Turn-failed card** with Try again, no stack trace or red banner.
- **Empty chat:** greeting card with up to three suggestion chips (M3 assist chips, `chat` icon, max three per card). History persists across logins.
- **Response card:** outcome label row (icon + plain label, announced first by screen readers), body, optional blocks, footer "Checked · N checks passed" opening "Why?" on the Checks section. Refusal and trap cards show "No personal figures used" instead.
- **Refusal:** three-part body (what, why in one sentence, what instead + 1–2 chips). **Trap:** yellow-tint premise callout naming the false assumption, then what exists, then a chip; same calm tone for injections, no warning banner.
- **Escalate card:** fixed handover text, at most a plain reason class, never the draft or technical reason, status line "Waiting for advisor"; composer stays usable.
- **Parameter proposal** ("Here's what I understood"): rows with value, unit, source tag; nothing computed before **Calculate**; **Change values** makes rows editable; a new question marks it "Cancelled" and resets DialogState to `NONE`.
- **Figure chips:** open "Why?"; matching Overview elements get a 4 s cyan ring; other tab labels get a cyan dot without switching; "Show in overview" switches and scrolls; same `provId` shows the same rounded value; reduced motion jumps without fade.
- **Accessibility:** message list is an `aria-live="polite"` log; insertion without scroll jumps; "New message" pill when scrolled up.

## Cross-Story Dependencies

- 3.1 provides the orchestrator skeleton, tables, chat API and atomic audit + release that every later story plugs into.
- 3.2 (exposure filter) and 3.3 (classification) feed 3.4; 3.4's `CheckId`/`CheckResult` and `BlockRouting` are used by 3.5 (`ESCALATE` → case), 3.6 (`NUMBER_MATCH` + regeneration) and 3.8 (`SCOPE`, `NO_GUARANTEE_OR_TIMING`, `DETECT_TRAP`).
- 3.7 depends on 3.6's ledger and on Epic 2's `CoreGateway`, `provId`s and "Why?" dialog.
- External: Epic 4 replaces the KB pass-through and adds `SOURCE_VALIDITY` and Rule Parameter validity routing; Epic 5 builds the advisor side of escalation cases (`AdvisorReplyPosted`); Epic 6 replaces the profile-completeness stub; Epic 11 runs the Q&A suite against this pipeline.
