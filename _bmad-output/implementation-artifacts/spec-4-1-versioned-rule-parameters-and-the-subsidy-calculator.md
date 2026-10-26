---
title: 'Story 4.1: Versioned Rule Parameters and the Subsidy Calculator'
type: 'feature'
created: '2026-10-26'
status: 'done'
baseline_revision: '97698fe'
review_loop_iteration: 1
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-4-context.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** Rule Parameters are loaded and versioned (Epic 2), but the subsidy parameters and ECB key rates are missing, there is no Subsidy Calculator, and a subsidy question about a year without a confirmed rule is not escalated.

**Approach:** Add the Riester parameters (valid to 2026-12-31: the reform from 2027 is not confirmed) and the ECB key rates with sources to `data/rules/`, a README with the reference date; a pure `RiesterSubsidy` Core function behind `CoreGateway.riesterSubsidy`; the loader rejects duplicate `(key, version)`; stage 3 checks the subsidy rules for a later year the message asks about and routes `ESCALATE` with "because a rule for next year isn't confirmed yet".

**Experiment Track counterpart:** `experiments/finlab/core.py:223-250` (`riester_subsidy`), `experiments/data/rules/rule_parameters.yaml:52-100`.

## Boundaries & Constraints

**Always:**
- `RiesterSubsidy.compute(priorYearGrossIncome, childrenBirthYears, parameters)` is pure; outputs `basicAllowance`, `childAllowances`, `totalAllowances`, `minOwnContribution` (EUR) and `eligibleChildren` (COUNT); eligibility rests on assumptions (directly eligible, child benefit received); every used parameter's version in `ruleParamVersions`.
- The gateway loads all six Riester parameters at the requested date before computing; any missing one throws `NoValidRuleParameterException` (nothing is computed or stored).
- Chat: `ToolPlan.laterSubsidyYear(extraction, message, asOf)` returns 1 January of the later year when the message is about a subsidy (topic `subsidy_eligibility` or subsidy wording incl. Riester/Zulage/Altersvorsorgedepot; `contract_switch` alone is not enough, e.g. car insurance) and names next year (in words or as `in/ab/für <year+1>`; later years are horizons, not asks) or the new subsidised product (starts 2027 under the reform); stage 3 then asks the gateway whether the rules are valid then, for any class. Invalid → `ruleParameterError` + `ruleForLaterYear` → route `rule_not_confirmed_next_year`, reason text "because a rule for next year isn't confirmed yet".
- ECB key rates (deposit facility, main refinancing operations, marginal lending facility) as FRACTION with the ECB decision as source, including the previous version closed by `valid_to`.

**Never:** subsidy amounts in chat before the profile exists (Epic 6); a NLU prompt change; edited values of an existing `(key, version)`.

</intent-contract>

## Acceptance Criteria

1. Loader test: duplicate `(key, version)` and overlapping versions fail startup; `valid` at a date without a version throws `NoValidRuleParameterException`; the committed file loads and holds the Riester and ECB keys; changing a `data/rules` file changes the config fingerprint (existing test).
2. `RiesterSubsidyTest`: Petra-like case (income 24 000, children 2010 and 2014) gives 175 / 600 / 775 / 185, the versions used, and the floor case (60).
3. Gateway IT: `riesterSubsidy` at 2026-10-15 returns `SourcedNumber`s with provenance rule versions and validity; at 2027-01-01 it throws and stores nothing.
4. Orchestrator test: "Should I cancel my Riester and switch to the new subsidised product next year?" releases `ESCALATE`, creates an `EscalationCase`, shows the next-year reason; a current-year subsidy question is not escalated by the rule check.

## Deliberate deviations

- Append-only is enforced by the duplicate-version and overlap checks plus review, not against git history.
- The chat checks rule validity without computing (no profile inputs until Epic 6); `CoreGateway.subsidyRulesValid` is the Core's refusal without inputs.
- "The new subsidised product" is mapped to the next year because the reform starts 2027-01-01 (recorded in `data/rules/README.md`).

## Spec Change Log

- 2026-10-26: created (scope decisions: Core + UJ-5 path; Riester valid to 2026-12-31).

## Review Triage Log

Review 2026-10-26: one combined reviewer (edge-case + verification-gap + intent, sonnet), 7 findings:

- FIXED (medium) Any later year with subsidy wording escalated ("I retire in 2045, does Riester help?"): only the next year counts as a number; negative case added.
- FIXED (medium) "Altersvorsorgedepot" alone did not count as subsidy wording: added to the wording; `contract_switch` alone stays insufficient (car insurance), spec wording corrected.
- FIXED (low) Drift between the committed file and the keys the calculator needs: loader test asserts every `RiesterSubsidy.PARAMETERS` key at the fixture date.
- REJECTED (low) Orchestrator tests per skipped class / German case: the class guard is two comparisons; German wording is covered in `ToolPlanTest`.
- REJECTED (low) NPE on a missing extraction: `classify` already dereferences it for every class.
- REJECTED (low) Null or implausible birth years: no input path exists before the profile (Epic 6); `List.copyOf` rejects nulls.
- DEFERRED (low) Recording the missing rule key in the audit body: route `rule_not_confirmed_next_year` already names the cause.

## Auto Run Result

Done 2026-10-26: backend `verify -Dfine.it.required=true`, frontend test and build green. No local live check (no prompt or model-visible change; the real-NLU UJ-5 turn is part of the epic-end compose check). Deviation from the lean loop: none besides that deferral.
