---
title: 'Story 3.6: Numbers only from the Deterministic Core — the Number Check'
type: 'feature'
created: '2026-10-24'
status: 'done'
baseline_revision: 'aa8e640'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-4-check-chain-rule-engine-and-compliance-levels.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** `NUMBER_MATCH` is `applicable = false`, so a draft can state any figure; the regeneration and the `number_mismatch_after_regeneration` route from Story 3.4 can never fire (FR-12, FR-52, SM-1a).

**Approach:** A pure per-turn `NumberLedger` (AD-2) holds every sourced number with its source; a pure `NumberCheck` ports the experiment's matching and becomes `NUMBER_MATCH` in the Check Chain. The orchestrator builds the ledger at the exposure stage, so shadow results (every level), the one regeneration and routing (`A+B`, `A+B+C`) use it unchanged; the ledger goes into the Audit Record.

**Experiment Track counterpart:** `experiments/finlab/numbercheck.py:102-136` (`matches`, `check`), `experiments/finlab/evidence.py:69-88` (`Evidence`), `experiments/finlab/pipeline.py:224-243` (ledger sources), `experiments/finlab/checks.py:175-178` (`NUMBER_MATCH`).

## Boundaries & Constraints

**Always:**
- Ledger sources in the experiment's order: Core items (`provId` as label; none until Story 3.7), every Rule Parameter valid at the turn's as-of date (`RuleParamPort.allValid`), numbers in the user's message (`NumberParser.numbersIn`), numbers in the KB passages in the prompt, numbers in what the exposure filter shows (message and profile context).
- Matching verbatim: a number or its absolute value, a percentage also as a fraction, against each sourced value, its absolute value and both ×100; exact means equal after rounding both to the decimals shown (half-even, Python's `quantize` default); "about"/"roughly" etc. means within 5 % of the sourced value.
- `NUMBER_MATCH` is applicable on every draft; reason `number_mismatch`; details `mismatches` (text, value, approximate, ok) and `checked` (count), as the experiment.
- Shadow results (with `NUMBER_MATCH`) are recorded at every level; the Audit Record gets `numberLedger` (value, source, label per entry).

**Never:** the model sees the ledger; a second regeneration; Core tool calls (Story 3.7).

</intent-contract>

## Acceptance Criteria

1. Table test (≥ 20 cases) of `NumberCheck`: rounding to shown precision, 5 % approximation, "%" as fraction, dates, list positions and article/paragraph numbers excluded, `1,000.50` and `1.000,50`; expectations match the experiment's `numbercheck.check`.
2. Under `A+B`/`A+B+C` a mismatching draft is regenerated once (fix in the Audit Record); a second mismatch routes to `ESCALATE` with reason class "because I couldn't confirm every figure in my answer"; a fixed regeneration is released.
3. At every level the first draft's `NUMBER_MATCH` is in `shadowChecks` and the ledger in `numberLedger` of the exported Audit Record.

## Deliberate deviations

- Numbers in earlier user messages of the prompt history are sourced too (the experiment is single-turn); otherwise a follow-up repeating "my 500,000" would escalate.
- Experiment bug fixed: `matches` rounds a percentage's fraction to the decimals shown for the percentage, so "5%" (0.05 → 0) matches any sourced value below 0.5; the app compares the fraction at shown decimals + 2. The table records both (`experimentPassed`); affects SM-1a comparability for percentages.
- The experiment tries every sourced value ×100, so a Rule Parameter of 6 sources "600 EUR"; the app takes the hundredfold only of fractions (|value| < 1). Table case `hundredfold of an integer is not a source`.
- With Core results in the prompt, the number fix restates them under the experiment's `TOOL RESULTS` header ("regenerated with the Core results restated"); inactive until Story 3.7.

## Spec Change Log

- 2026-10-24: created.

## Review Triage Log

Combined reviewer (edge case + verification gap + intent, sonnet) and Blind Hunter (sonnet), 14 findings:
- Fixed: history turns dropped by the budget were sourced (now only kept turns); ×100 variants restricted to fractions (deviation above); Double profile values flattened as "1.0E7" (now plain digits); kept-passage count computed twice (one helper); `experimentPassed` unchecked in the table test (asserted to differ); weak `doesNotContain("TOOL RESULTS (")` (uses the header constant); AC3 asserted only at level `A` (escalation test now checks `shadowChecks` and `numberLedger` in the Audit Record under `A+B+C`); table gained the sourced-1000.4-shows-1,000 case.
- Deferred to Story 3.7: Core numbers are not in the ledger until Core items are added as `CORE` entries (the experiment's `add_core`, not parsed from the tool text); 3.7 must add them, or every correct figure fails.
- Rejected: `allValid` "one per key" (loading already rejects overlapping versions); ledger size/duplicates in the audit body (small, kept as the record of sources); per-call variant precomputation (negligible); `str(Decimal)` exponent form (the experiment multiplies by ints, no exponent arises); golden cases with digits (golden expectations exclude `NUMBER_MATCH`).

## Implementation Notes

- Lean loop per `epic-3-handoff.md`: inline implementation; table expectations generated with `experiments/finlab/numbercheck.py` (scratch script, not committed).
- `RuleParamPort.allValid` added (the experiment's `all_valid`); the ledger holds every valid Rule Parameter, as the experiment, even before a tool reads one.
- `NUMBER_MATCH` is now applicable on every draft, so cards under `A+B`/`A+B+C` show one more check. Parser parity has a visible effect: "question 4" masks the 4 as a reference, so a draft repeating it is a mismatch (test answers in `ChatEndToEndTest` made digit-free).
- Live check (`A+B+C`, retrieval off): Lena's 12,000/2,000 EUR question passed with the exposure band limits sourced; Jonas' "4% of 500,000 EUR" first draft invented 525,000 / 21,000 / 1,750, was regenerated once and the number-free regeneration released. The escalation after a second mismatch is covered by `TurnOrchestratorTest` (not reproduced live).


## Auto Run Result

- **Summary:** `NumberLedger` (per-turn sources), `NumberCheck` (port of `matches`/`check` with two documented fixes), `NUMBER_MATCH` live in the Check Chain, `RuleParamPort.allValid`, number fix restating Core results, `numberLedger` in the Audit Record.
- **Verification:** `./mvnw -B verify -Dfine.it.required=true` green, 0 skipped; frontend `api:check`, `ng test` (77), build green; live check see Implementation Notes.
- **Review:** combined reviewer + Blind Hunter; 8 fixed, 1 deferred to 3.7, 5 rejected (see Review Triage Log).
