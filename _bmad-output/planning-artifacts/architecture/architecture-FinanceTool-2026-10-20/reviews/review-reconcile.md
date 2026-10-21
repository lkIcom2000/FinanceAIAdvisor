---
review: reconcile
target: ../ARCHITECTURE-SPINE.md
inputs:
  - prds/prd-FinanceTool-2026-10-20/prd.md (final, FR-1..FR-52)
  - prds/prd-FinanceTool-2026-10-20/addendum.md
  - forge/autonomous-hybrid-finance-agent/forged-idea.md
  - experiments/sampleQuestions_variantB.csv (65 items)
extra_constraint: prototype on local PC, final deployment to Azure; Llama 3.x, Llama 2, fin-chat; local/EU hosting
date: 2026-10-20
verdict: NEEDS REVISION (no blocker to the paradigm; 3 high, 7 medium, 7 low findings)
---

# Reconcile Review: Architecture Spine vs. PRD, Addendum, Forge, Test Set

## Verdict

**Needs revision before epics.** The paradigm (hexagonal monolith, fixed turn pipeline, pure domain, Audit-in-transaction, config-as-data, one image for local and Azure) fits the inputs well, and most FRs have a clear home. The user's hosting constraint (local PC prototype, Azure EU deployment, Llama 3.x / Llama 2 / fin-chat via Ollama) is satisfied by AD-17 and AD-18.

Three gaps are high severity because they decide whether the thesis metrics can be measured at all:

1. The Check Chain only exists inside the chat pipeline, while the PRD requires it for every Recommendation **and every Action**, including those coming from monitoring.
2. The Q&A test set's profile model (profiles stated inside the message, for no logged-in persona) has no architectural home. Under the spine, roughly 12 `RECOMMEND_CATEGORY` items would classify as `ASK_PROFILE`, which threatens SM-4 ≥ 90%.
3. The pure-LLM baseline and the `A` level are not defined well enough for "stage 7 always runs" to produce SM-1(a), SM-3 and SM-4 values.

## Coverage Matrix (FR → spine home)

| FR | Home | Status |
|---|---|---|
| FR-1, FR-2, FR-3 | AD-11, AD-12, AD-8 | Covered |
| FR-4–FR-7 | AD-19, `api/overview` | Covered (data contract only; fine) |
| FR-8 | AD-2, AD-19 | Covered; see L-3 for simulate results |
| FR-9 | AD-3, AD-4 | Covered for the persona case; **gap H-2** for message-supplied profiles; **gap M-2** for baseline outcome |
| FR-10, FR-17 | AD-4 ("profile completeness"), AD-6 | Partial: "confirmed" field state and its owner are not stated (M-3) |
| FR-11, FR-14, FR-15, FR-16 | AD-6 (generic `Check`) | Partial: check registry membership and detection approach missing (M-4) |
| FR-12 | AD-2, AD-6 | Covered |
| FR-13 | AD-9 → ESCALATE | **Contradiction-risk** (M-5) |
| FR-18 | `application/assessment` | Covered (thin) |
| FR-19 | AD-16 lists it in Binds, no rule | **Gap** (M-3) |
| FR-20–FR-22 | AD-1, AD-2, AD-8, AD-9 | Covered |
| FR-23, FR-24 | AD-9, AD-14 | Covered |
| FR-25 (Recommendation + Action) | AD-3, AD-6 for chat only | **Gap H-1** for Actions and monitoring Recommendations |
| FR-25 (shadow on first draft) | AD-3 stage 7 | Covered for `A+B`/`A+B+C`; **undefined for baseline** (H-3) |
| FR-26 (Judge only under A+B+C) | AD-3 ComponentFlags, AD-6 | Covered |
| FR-27, FR-28, FR-29 | AD-13, AD-6 | Covered for responses and Actions; Notifications missing (H-1) |
| FR-30, FR-31, FR-32 | AD-16, AD-8, AD-11 | Covered; Escalation from monitoring missing (M-6) |
| FR-33 | AD-4 | Covered |
| FR-34 | AD-19 `/api/simulate` | Covered; provenance/audit of simulate results open (L-3) |
| FR-35 | AD-10 | Covered for shape; Data Confidence computation has no owner (M-1) |
| FR-36–FR-40 | AD-10, AD-19 | Covered (Undo = compensating Action; Revoke re-checked at execution) |
| FR-41, FR-42 | AD-6, AD-7, AD-11 | Covered |
| FR-43 | AD-5 | Covered; pseudonym reverse-mapping not placed (L-4) |
| FR-44 | AD-3, AD-4, AD-10 | Covered, except "shown **before** computed" conflicts with AD-7 (M-7) |
| FR-45 | AD-5 | Covered |
| FR-46 | AD-14, AD-18 | Covered; context-window parity across E1 models missing (M-8) |
| FR-47–FR-50 | AD-13, AD-14, AD-15 | Covered |
| FR-51 | AD-15 (`item_personas.yaml`) | Covered |
| FR-52 | AD-6 `route()` | Covered |
| §5 Tone for vulnerable users | none | **Gap** (M-9) |
| §5 English only | Conventions "Language" | Covered |
| §5 GDPR / EU or local hosting | AD-17 (Sweden Central), AD-18 (Ollama) | Covered; real participant text in the study not addressed (L-5) |
| SM-7 red-team set | `experiments/redteam.csv` in tree; format deferred | Partial (L-6) |
| FR-31 study prep | AD-8, AD-11 research route | Covered; needs `RESEARCH_MODE=1` in Azure (L-7) |

## Findings

### High

**H-1. The Check Chain never sees Actions or monitoring-produced Recommendations.**
PRD §4.7: "Every Recommendation and every Action passes the Check Chain before it reaches the user or executes." SM-2 measures Check Chain completeness for "Recommendations **and Actions**". In the spine, the Check Chain runs only as stages 8–10 of the chat pipeline (AD-3, AD-6). AD-10 routes Actions through the Autonomy Gate only. MonitoringService produces Notifications that contain Recommendations (UJ-1 "switch to monthly premium", S10, S11, S12a at L1) and figures with provenance (FR-32), and none of that passes the Check Chain. AD-13 also restricts `kind` to `response|action`, so Notifications have no Audit Record.
*Fix:* add a rule (extend AD-6 or AD-10): `ActionService` runs the applicable Check Chain subset before the gate (or before execution) and persists the `CheckResult`s. MonitoringService builds Notification content from `CoreResult`s via an Evidence ledger and passes Recommendation-bearing Notifications through the same `domain/rules` evaluation. Add `kind: notification` to AD-13. State whether Notification text is templated (deterministic) or LLM-phrased; if it is LLM-phrased, it needs the Number Check too.

**H-2. No architectural home for profiles stated in the message (the Q&A set's main input pattern).**
The test set has 12 `RECOMMEND_CATEGORY` items. Most carry the profile in the prompt ("Client profile: Age 30, Risk Class 'High'…", items 31–39, 51, 52), and several are knowingly partial yet still expect `RECOMMEND_CATEGORY` (item 31: "must flag missing suitability data"). AD-15 runs non-persona items "inside a fresh conversation", but AD-11 makes chat persona-scoped, and AD-4 computes the class from "profile completeness" without saying *whose* profile. With a stored-profile-only reading, these items become `ASK_PROFILE` under FR-17's "present and confirmed" rule, which costs about 18% of SM-4 by itself (target ≥ 90%). The Category Recommendation function (FR-20) also needs a profile input that is not the stored one.
*Fix:* add an AD that defines the **profile context of a turn**: (a) the stored persona profile (live use, persona-backed items), or (b) a transient, turn-scoped `StatedProfile` extracted by the NLU role from the message (third-party "Client A…" or hypothetical), which is never persisted and never confirmed. Define which mandatory fields the classification rules need per class, given this source. Also name the principal for non-persona Q&A items (for example, a neutral harness persona `p-anon` with an empty profile and no accounts).

**H-3. Baseline and `A` component semantics are under-specified for measurement.**
PRD §8.1: under the baseline, Core tool results, classification and retrieval are **off**, yet the Rule Engine runs in Shadow Mode, and SM-1(a) reports a draft mismatch rate "for the baseline". AD-3 says "stage 7 always runs" and AD-14 says the baseline is just a compliance level. A Number Check without Core results has no reference values, so every persona number is a mismatch. Profile, scope and class checks need a class that does not exist when classification is off. FR-9 and SM-4 need a Response Outcome for baseline answers, but no deterministic routing exists. Under `A`, the class must be "passed to the prompt as an instruction", which AD-3 does not mention, and the Number Check regeneration edge must be off.
*Fix:* make ComponentFlags distinguish **computed for measurement** from **exposed to the LLM**. Under the baseline, stages 1–5 still run as shadow (classification, tool run, Evidence ledger), but their results are withheld from the prompt. State that the baseline Response Outcome is labelled post hoc by the scorer role (AD-20) and recorded as such. Publish the full ComponentFlags table (baseline/A/A+B/A+B+C × stages 1–11, including the regeneration edge and "class in prompt") inside AD-3.

### Medium

**M-1. Data Confidence has no owner and no data source.** It is a gate input (FR-35, S13): at least 95% of transactions in the last 90 days categorised, and no gap longer than 5 days. The spine does not say where transaction categories come from (fixture field vs. a categoriser) or which pure function computes Data Confidence. *Fix:* put `data_confidence(transactions, as_of, thresholds)` in `domain/autonomy` (or `domain/core`). Categories are a fixture attribute of the sandbox bank. Thresholds come from `config/autonomy.yaml`, and the computed value and inputs go into the gate's Audit Record.

**M-2. Response Outcome for configurations without routing.** See H-3. SM-4 and SM-C1/SM-C2 need an Outcome per item in every configuration. *Fix:* one sentence: "Outcome = routed outcome when FR-52 routing is on; = classified class under `A`; = scorer-assigned label under baseline."

**M-3. FR-17 confirmation state and FR-19 re-confirmation have no mechanism.** AD-16 lists FR-19 in Binds but gives no rule. AD-12 forbids monitoring from writing `profile` tables. *Fix:* profile fields carry `source (derived|user)`, `confirmed_at` and `stale_since`. Monitoring calls `ProfileService.mark_stale(fields, trigger_id)` on a salary-change Trigger. The classification rules treat stale fields as incomplete, which yields `ASK_PROFILE` and re-confirmation before the next Recommendation.

**M-4. The Check Chain membership is not fixed.** The PRD names the checks (suitability, cost transparency, documentation, conflicts of interest, IDD needs, scope, number match, source validity), plus FR-11 (no ISIN, issuer, insurer or tariff), FR-16 (no guarantees, no market timing) and the class-violation rules behind SM-3. AD-6 fixes only the `Check` shape. Because these checks are the thesis contribution and SM-3 reads them, the registry and each check's detection approach (deterministic lexicon/regex, ISIN pattern, structured-field check) belong in the spine. *Fix:* add a closed `CheckId` enum with applicability per class, and state that every check is deterministic (no LLM inside `domain/rules`).

**M-5. FR-13 vs. FR-52 for missing sources.** FR-13: "If no valid source exists, the system says so instead of answering from model knowledge" (an `INFORM` outcome). FR-52: "Rule Parameter or Knowledge Base source not valid → `ESCALATE`". AD-9 maps everything to `ESCALATE`. With 24 `INFORM` items and 25 `general`-scope items, collapsing to `ESCALATE` risks SM-C2 (≤ 15%). *Fix:* distinguish "no retrievable source for a general explanation" (released `INFORM` that says no source is available) from "a source or Rule Parameter required for a personal computation or recommendation is invalid at `as_of`" (`ESCALATE`, UJ-5). Encode this in `route()`.

**M-6. Escalation from monitoring is not routed.** S12b expects `ESCALATE` from a rule-change Trigger with no valid source. AD-6 routing and `EscalationCase` (linked to `MESSAGE` in the ER diagram) assume a chat turn. *Fix:* allow `EscalationCase` to reference a Trigger or Notification. MonitoringService calls EscalationService when a Core call raises `NoValidRuleParameter`/`NoValidSource`.

**M-7. FR-44 "parameters shown to the user before the result is computed" conflicts with AD-7 (one atomic, non-streamed turn).** AD-4 says parameters are "echoed before computation", but stage 3 computes in the same turn that the echo is released in. *Fix:* decide explicitly. Either (a) the what-if turn returns the extracted parameters plus a pending simulation, and the user confirms or edits them (via `/api/simulate`, which also satisfies FR-34), or (b) relax the requirement to "shown together with the result and editable". Record the PRD deviation if (b) is chosen.

**M-8. Prompt budget across E1 models.** Llama 2 7B Chat and AdaptLLM finance-chat (Llama-2-based) have a 4,096-token context window. Llama 3.1 has a much larger one. If the system prompt, compliance instructions, KB passages, tool results and history do not fit 4k, the Llama 2 arms get truncated input, which confounds E1. *Fix:* add a rule in AD-5 or AD-14. Every `PromptPayload` is built to a single fixed token budget (≤ ~3k input, which leaves room for the answer) for all models in a run, with a deterministic truncation order (history first, then passages). Overflow is recorded in the Audit Record. Set `num_ctx` explicitly in the Modelfiles.

**M-9. Tone for vulnerable users (§5) has no home.** It requires plain language, technical terms explained on first use, no shaming or pressure, the answer before the caveats, and a refusal that ends with a next step. None of this is mentioned. *Fix:* assign it to the versioned role prompt (`prompts/`) and to the SM-9 scoring rubric. Optionally add one deterministic check for "refusal contains a next-step offer" (it already overlaps FR-14).

### Low

**L-1. The gate needs post-Action balances.** S2 expects a downgrade when the shortfall transfer would breach the emergency-fund floor, but the FR-35 table has no explicit "floor" criterion. *Fix:* in AD-10, say that Mandate bounds include floors and the gate evaluates bounds on the projected post-Action state.

**L-2. Undo eligibility.** AD-10 makes Undo a compensating Action. It does not say whether Undo is time-limited, whether it passes the gate (it should bypass the L3 caps but still be audited), or how a `REDIRECT_CONTRIBUTIONS` undo works. This is mostly story-level. Just state that Undo is user-initiated, always permitted for executed L3 Actions, and audited.

**L-3. Provenance of `/api/simulate` results.** The `prov_id` format is `<turn_or_run>:<function>:<n>`, but simulate calls (FR-34) are neither turns nor runs, and FR-8 "Why?" must resolve them. *Fix:* add a `sim:` scope to the `prov_id` convention, and decide whether simulate calls write an Audit Record (recommended: yes, `kind: simulation`).

**L-4. Pseudonymised exposure needs a reverse mapping.** Under `pseudonymised`, the LLM output carries pseudonyms. The spine does not say where re-identification happens relative to the Number Check and the Audit Record. *Fix:* `domain/exposure` owns both directions. Checks run on the re-identified text, and the audit stores both.

**L-5. Real participant input in Azure.** Personas are synthetic, but user-study participants type free text into append-only Audit Records in Azure, and think-aloud material may contain real personal data. *Fix:* in Deferred or AD-17, note a study data-handling rule (consent, the instruction not to enter real data, export then delete the Azure database after the study). The append-only trigger conflicts with GDPR erasure, so plan an environment-level deletion.

**L-6. Red-team set shape.** SM-7 means "does not change the outcome", which implies paired items (a base prompt and the same prompt with an injection) or an expected-outcome column. *Fix:* constrain `redteam.csv` to the Q&A CSV schema plus `attack_type` and `paired_item_id`, so that `run-qa` handles it unchanged.

**L-7. Local PC and Azure operational notes.** (a) The local machine is Windows 10 Home. Docker Desktop then needs the WSL2 backend, and Ollama GPU passthrough needs an NVIDIA GPU. Allow `OLLAMA_URL` to point at a native Windows Ollama as a fallback (this is already possible through env and is worth one sentence). (b) Study prep (FR-31) uses research routes, which requires `RESEARCH_MODE=1` in Azure during the study, so the researcher principal must be the only gate. (c) Serverless GPU cold starts and non-streamed turns that make several model calls (NLU, draft, possible regeneration, Judge) may give long latency in the study. Plan minimum replicas of 1 during sessions. None of this changes an AD.

## Things Checked and Found Consistent

- Judge only under `A+B+C`, block-only, and never consulted after a Rule Engine block (AD-3, AD-6, FR-26).
- Shadow checks on the first draft in every configuration (AD-3 stage 7), subject to H-3 for the baseline.
- Undo vs. Revoke are distinct: Revoke is re-checked at execution, and Undo is a linked compensating Action (AD-10, FR-7, FR-38).
- FR-51 persona-backed items via `item_personas.yaml` with reset and simulated date (AD-15), and item 56 calibration (addendum B).
- FR-31 study preparation via a research route and the simulated clock (AD-8, AD-11, AD-16).
- English-only (Conventions). GDPR/EU hosting: local Ollama, with Azure Sweden Central and no third-party LLM APIs (AD-17, AD-18).
- Models match E1 (Llama 3.x, Llama 2 control, fin-chat Llama-2-based, no fine-tuning). Q4_K_M parity across chat models (AD-18).
- Price movements are never Triggers, and there is no scheduler (AD-16, FR-30, FR-31, S9).
- Rule versioning, validity windows and snapshot ids (AD-9, FR-22, FR-23, FR-50).
- Pure-LLM baseline as configuration, not a separate code path (AD-14, FR-49). Its semantics still need H-3.
- The scorer differs in model family from the models under test (AD-20, OQ1).
