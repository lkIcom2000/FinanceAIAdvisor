# PRD Quality Review — Autonomous Hybrid Finance Agent

## Overall verdict
The PRD is a solid DSR prototype spec. It has a clear thesis ("auditability, not truth"; "Mandate, not discretion"), a coherent feature arc from that thesis, an evaluation section that names counter-metrics, and honest scope. Three gaps put the experiments at risk, not the product. (1) The PRD never says what happens to a blocked response, so escalation and refusal paths conflict. (2) It never says which components each Compliance Configuration turns on, so E3 cannot be run as specified. (3) The flagship metric SM-1 is true by construction under FR-12. Fixing all three takes a few paragraphs, and they should be fixed before architecture starts.

## Decision-readiness — adequate
Decisions are stated as decisions, and the PRD says what was given up: L4 is deferred "deliberately" (§6.2), the English UI is recorded as a threat to validity (§5, §10), and Non-Goals say outright that "The prototype does not guarantee that answers are correct" (§7). The Open Questions in §9 are genuinely open, and Q1 (judge bias) and Q2 (gate thresholds) are real tensions. There are no `[NOTE FOR PM]` callouts, which is acceptable for a single-author thesis where all assumptions were confirmed (§11).

The one decision the PRD leaves to the reader is the experiment run plan. §8.1 lists four factors with 4 × 2 × 3 × 4 levels, but it never says whether they are run one factor at a time around a base configuration or crossed. For a thesis that has to finish fast, this decision sets the total effort of the evaluation.

### Findings
- **[medium]** Experiment run plan undefined (§8.1) — No base Experiment Configuration and no statement of one-factor-at-a-time versus factorial. "Overall comparison: hybrid versus the pure-LLM baseline" does not say *which* hybrid configuration. *Fix:* Name a reference configuration (e.g. Llama 3.x + retrieval, advisor prompt, `A+B`, `none`). State that E1–E4 each vary one factor from it, and that the baseline comparison uses it.

## Substance over theater — strong
Nothing here is furniture. The five JTBD in §2.1 each map to features: the advisor maps to §4.11 and the researcher to §4.13. The three named personas in the UJs (Lena, Marco, Jonas) are reused as Scenario seeds (addendum A) and as user-study tasks (§8.4). The Vision is specific to this product and could not be swapped into another PRD. §5 does not read like generic NFR boilerplate: it points to concrete FRs (FR-43, FR-27, FR-38). The tone rules ("lead with the answer before the caveats", "A refusal always ends with something the user can do next") are concrete enough to judge, even though they are not quantified.

### Findings
None.

## Strategic coherence — adequate
The thesis is explicit, and the features follow from it. The Deterministic Core, Check Chain and Autonomy Gate each map to a pillar and to a sub-question. The metrics include counter-metrics (SM-C1–C3) that directly answer the "refuse everything" exploit, which is unusually good. The MVP is clearly a problem-solving and research-instrument scope.

The weak point is the primary metric for the core claim. SM-1 measures "share of responses in which any number differs from the Deterministic Core output" with a target of 0%. However, FR-12 already says "A mismatch blocks the response." With the checker on, SM-1 is 0% by construction and measures nothing about the design. The informative quantities are (a) how often the LLM *drafted* a mismatched number that the check caught, and (b) the checker's own recall, i.e. mismatches it missed, which only manual review on a sample can reveal.

### Findings
- **[high]** SM-1 is tautological under FR-12 (§8.5 SM-1, §4.3 FR-12) — "Target for the hybrid: 0%" is guaranteed because FR-12 blocks every detected mismatch, so the metric cannot validate the core claim. *Fix:* Redefine SM-1 as the pre-check draft mismatch rate per configuration, which shows how much the guardrail is needed. Add a checker-recall measure: manually audit a sample of released responses for numbers the matcher missed. Blocked-by-FR-12 responses then count toward SM-C1/SM-C2.
- **[medium]** Counter-metrics have no thresholds (§8.5 SM-C1–C3) — "If it is too high, the agent gives no advice at all" (SM-C2) does not say what "too high" is. Without a bound, the counter-metrics cannot actually overrule a configuration that wins on SM-3. *Fix:* Give each counter-metric a tolerance (e.g. SM-C1 ≤ 10% of in-scope items, SM-C2 ≤ 15%, SM-C3 = 0 on addendum S1/S4/S7), or state explicitly that they are reported descriptively only.

## Done-ness clarity — adequate
Most FRs have a testable consequence. Examples: FR-1 ("Logging in as persona P never shows data belonging to persona Q"), FR-3 (same outputs after reset), FR-20 (worked example for item 40, which is arithmetically correct: 522.0 / 538.4 periods), FR-35 ("Identical inputs always yield the identical level"), FR-37 (an enumerated action list). Two cross-cutting behaviours are underspecified, and they matter for the harness more than for the UI.

### Findings
- **[high]** What happens after a block is undefined (FR-41 vs FR-10, FR-12, FR-14, FR-17, FR-22; §3 Expected Behavior) — FR-41 says that "When the Check Chain blocks a response … The case is placed in the advisor queue". However, the scope refusal is "itself a Guardrail" (§4.3), FR-17 blocks Recommendations while the profile is incomplete (which should lead to `ASK_PROFILE`, not escalation), and FR-12 blocks on a number mismatch. As written, every such block escalates. Escalation is also not an Expected Behavior class, yet FR-9 says that "Every response falls into exactly one Expected Behavior class". As a result, SM-4, SM-C2 and the harness's scoring of escalated items are all undefined. *Fix:* Add a short routing table: block reason → outcome (scope → `REFUSE_OUT_OF_SCOPE`; incomplete profile → `ASK_PROFILE`; invalid Rule Parameter or source, number mismatch, or Judge escalate → Escalation). Either add `ESCALATE` as an outcome that SM-4 scores, or state how escalated items are scored against `expected_behavior`.
- **[high]** The Compliance Configuration layers are not defined in terms of components (FR-25, FR-49, §3 Compliance Configuration, §8.1 E3) — `A` is "prompt only" and "the Rule Engine is bypassed". The PRD does not say whether, under `A`, deterministic classification (FR-9), the number check (FR-12), the profile gate (FR-17) or Deterministic Core tool calls are still active, or how `A` differs from the pure-LLM baseline (FR-49). Without this, E3 cannot be implemented or interpreted. *Fix:* Add a table: component × {baseline, `A`, `A+B`, `A+B+C`} → on/off. For example, the Deterministic Core is on in `A` but FR-9/FR-12/FR-17 checks are off, while the baseline has none of them.
- **[medium]** Gate criteria and expected levels are not scoreable (FR-35, FR-39; addendum A S10, S12, S13, S14) — "data confidence" and "regulatory class" have no defined values or computation. Several Scenarios have non-unique expected levels ("L0/L1", "L1 / Escalation", "≤ L2"). S13 expects "L1 (downgrade)" where FR-39's "highest level whose conditions are met" would seem to give L2. SM-5 targets 100% exact match. *Fix:* Define data confidence (e.g. share of months with complete transactions ≥ threshold) and the regulatory-class enum. Give every Scenario exactly one expected level, and state for each criterion which level a failure caps the Action at.
- **[medium]** Scope of the number matcher is fuzzy (FR-12) — "Every number … that is specific to the persona or the calculation". The automated check needs rules for numbers that come from the Knowledge Base or Rule Parameters (e.g. a subsidy amount), dates, percentages, rounding ("about 100 EUR" in UJ-2), and number formatting. *Fix:* State what the matcher counts: numbers from Core outputs, Rule Parameters and cited KB passages count as sourced, and any other number counts as a mismatch. Give a rounding tolerance and the EN/DE number formats the matcher accepts.
- **[medium]** The E1 models may not support tool calling (FR-44, FR-33, §8.1 E1) — FR-44 has "the LLM … invoke" tools, but Llama 2 and the fin-chat model have no reliable native function calling, so the E1 comparison could end up measuring tool-call failure instead of domain knowledge. This does not reopen E1. It only asks the PRD to fix the invocation mechanism. *Fix:* State that the orchestrator decides which tools to call and runs them deterministically, because FR-9 classification already happens before phrasing. The LLM receives tool results and only phrases the answer, so all E1 models are on equal footing.
- **[low]** Chat-to-panel linkage has no FR (§4.2 description) — "when an answer refers to a figure, the corresponding panel element is highlighted" is described but not required anywhere. *Fix:* Add it to FR-8 as a consequence, or drop it from the description.
- **[low]** User-study session setup is not specified (UJ-1, FR-31, §8.4) — UJ-1 assumes the transfer "has already" run when Lena logs in. The PRD does not say who runs monitoring at which simulated date before a participant session. *Fix:* Add a consequence to FR-3/FR-31 that the researcher can prepare a persona at a simulated date with the monitoring results applied before the session.

## Scope honesty — strong
Non-Goals (§7) and Out of Scope (§6.2) do real work: they say L4 is excluded deliberately, list the excluded asset classes, and exclude real banking. Product-scope exclusions match the forge's Rejected list. The advisor view's out-of-scope list (§4.11) keeps it from growing. Open-item density is low (4 Open Questions, 0 open assumptions), which is appropriate for this level of stakes.

### Findings
- **[low]** §11 and §8.4 contradict each other on the study instruments — §11 lists "§8.4: user-study instruments" as confirmed, while §8.4 says "Instrument details (e.g. SUS, a trust scale) are chosen in the study design". *Fix:* Change the §11 line to "user-study protocol (instruments deferred)".

## Downstream usability — adequate
The Glossary is thorough, and most terms are used consistently (Check Chain, Autonomy Gate, Mandate, Planted Ground Truth). FRs are traceable to SMs and UJs. Several capitalized domain nouns are not in the Glossary, "Revoke" means two different things, and two UJs have no named protagonist. See the Mechanical notes. None of these block architecture work.

### Findings
- **[low]** "Revoke" means two different things (UJ-1, FR-7, FR-38) — In UJ-1, Revoke undoes the transfer. In FR-7, Revoke is a control on the Mandate. FR-38 covers both. Stories will mix them up. *Fix:* Use "Undo" for reversing an Action and "Revoke" only for Mandates, and add both terms to the Glossary.

## Shape fit — strong
The PRD has the right shape for a chain-top research prototype. UJs are load-bearing because they double as user-study tasks and Scenario seeds. The Evaluation Harness is specified as FRs (§4.13), which is correct when the experiments are the main consumer. The PRD stays out of architecture detail and moves test-design depth to the addendum. It is not over-formalized: there are no unnecessary NFR sections and no persona sprawl.

### Findings
None.

## Mechanical notes
- **ID continuity:** FR-51 sits in §4.1 between FR-3 and FR-4, and it is a test-data requirement rather than a login feature. Consider moving it to §4.13 or a data subsection. The IDs stay unique.
- **Broken cross-ref:** §8.4 says "(Open Question 3)" for instrument choice, but OQ3 is about recruiting. Add an OQ5 for instruments, or point to the correct question.
- **Glossary gaps:** Cash-Flow Forecast, Duplicate Coverage, Subsidy Calculator, advisor queue and Rule change Trigger are used as capitalized terms (FR-4, FR-6, FR-20, FR-41) without Glossary entries.
- **UJ protagonists:** UJ-5 ("A user … the bank advisor") and UJ-6 ("A user") have no named protagonist, unlike UJ-1–4.
- **Leftovers:** "*Working title — confirm.*" under the H1, and front matter `status: draft`.
- **Item 40 convention:** the 522/538 values assume withdrawals at the end of the month. State this in FR-20, because withdrawals at the start of the month give different counts.
- **Assumptions Index roundtrip:** no inline `[ASSUMPTION]` tags remain, and §11 records them as confirmed, so the roundtrip is not applicable.
- **Addendum B:** the item-56 calibration (450 + 2,300 − 1,800 − 400 − 620 = −70 EUR) satisfies FR-51's "at least 50 EUR below zero".
