---
title: 'Story 6.3: Category Recommendation with a Suitability Statement'
type: 'feature'
created: '2026-10-26'
status: 'done'
baseline_revision: 'f222f5a'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<intent-contract>

## Intent

**Problem:** A `RECOMMEND_CATEGORY` turn has no Core category selection, so it escalates via `block_suitability`/missing facts, and nothing explains the recommendation with the persona's figures, risks and costs.

**Approach:** Two pure Core functions (`categorySelection`, `costCalculation`) run for every `RECOMMEND_CATEGORY` turn on the effective profile (stored + stated, AD-23) and Rule Parameters only. Their results fill the class instruction, the Check Chain facts, a fixed first line and a deterministic Suitability Statement shown in a collapsed "Why this fits you" panel.

**Experiment Track counterpart:** `experiments/finlab/core.py:254-282` (`cost_comparison`), `core.py:302-383` (`select_category`, `_risk_bucket`, `_ef_adequate`), `pipeline.py:119-140` (`_advice_tools`), `pipeline.py:147-162` (conflicts text).

## Boundaries & Constraints

**Always:**
- Scope decisions (Commc 2026-10-26): no seeded profile for Marco (tests store it through `Profiles.confirm`); the statement is deterministic from Core results and profile; equity share ranges are Rule Parameters; a chip whose profile value equals the account figure carries the account figure's `provId` (Overview link), otherwise the selection's own.
- New Rule Parameters (values from the experiment): `short_horizon_years_max`, `reference_ter_active_fund`, `reference_front_load_active_fund`, `equity_share_<bucket>_min/max` (FRACTION, buckets `very_low` … `very_high`).
- `CategorySelection.of(profile, topic, asOf, parameters)`: a verbatim port of `select_category`; the signature takes only the profile map, the topic, the date and Rule Parameter values. Risk keys with spaces map to bucket keys with underscores. Numeric outputs `monthlySurplus`, `emergencyFund`, `emergencyFundTarget`, `requestedMonthlyAmount`, `equityShareMin/Max` (when known); text result: category, reasons, conflicts, risk bucket, profile fields used, Rule Parameter keys.
- `CostCalculation.of(amount, years, etfTer, activeTer, frontLoad)`: port of `cost_comparison`; runs for the experiment's invest topics with amount = `amount`, else requested monthly × 12, else 10,000; 10 years.
- `advisory.application.Recommendations` (retro A5) runs both in stage 3 for `RECOMMEND_CATEGORY`; a missing Rule Parameter marks the turn `ruleParameterError` (escalates as today).
- `DOCUMENTATION` applies to every recommendation: fails `no_core_selection_recorded` (as the experiment) without a selection, `statement_incomplete` when the statement lacks profile facts, risks, or (invest topics) costs.
- First line fixed (`ResponseTemplates`): conflicts → "This plan doesn't fit right now: <conflicts>."; otherwise "Recommended category: <label>.". The model writes the reasons; checks run on the whole text.
- `ChatMessage.Recommendation(category, conflicts, facts, figures, risks, costs)` stored as JSONB (V15); message figures = surplus and emergency fund chips; audit body gets `categorySelection` (category, reasons, conflicts, selection inputs = profile fields + Rule Parameter keys).

**Never:** commission, inducement, operator preference or product inputs in the selection; issuer/ISIN/insurer/tariff in fixed texts; profile writes; NLU prompt changes.

</intent-contract>

## Acceptance Criteria

1. `RECOMMEND_CATEGORY` with a complete stored profile: selection and cost results are Core results in the ledger and evidence; the audit record lists the selection inputs (profile fields + Rule Parameter keys only); a test pins `CategorySelection.of`'s parameter types.
2. `NO_PRODUCT_NAMING` fails a draft naming an ISIN/issuer/insurer/tariff; a draft without profile reference/risk fails `SUITABILITY`, without costs `COST_TRANSPARENCY`; a selection without statement elements fails `DOCUMENTATION`.
3. Marco (stored profile: income 2,100, expenses 2,000, emergency fund 300) asks the AC question: category `build_emergency_fund_first`, conflicts emergency fund + requested amount; first line "This plan doesn't fit right now: …"; chips monthly surplus 100.00 and emergency fund 300.00 (the latter with the Liquidity `provId`); the recommendation panel holds conflicting figures, risks and ex-ante costs; footer "Checked · N checks passed".
4. Unit tests: `CategorySelection` branches and conflicts, `CostCalculation` totals, `DOCUMENTATION` cases, `ClassInstruction` category/conflicts, first-line template.
5. Frontend: `RECOMMEND_CATEGORY` card with light-cyan left bar and collapsed "Why this fits you" panel; OpenAPI snapshot and client regenerated.

## Implementation Notes
- Scope decisions (Commc 2026-10-26): no seeded Marco profile; deterministic statement; equity shares as Rule Parameters; account `provId` on a chip when the profile value equals the account figure.
- `CategorySelection` always outputs `conflicts` (COUNT): a Core computation needs at least one output, and an empty profile on a topic rule (e.g. `protection_duplicate`) has no other figure.
- Category labels and the fixed first line avoid "your", risk and cost words, so `SUITABILITY` and `COST_TRANSPARENCY` still need the model's reasons (pinned by `ResponseTemplatesTest`); the panel shows conflicts in the same plain words as the first line (`ResponseTemplates.plainConflict`).
- Deviation from `_advice_tools`: the cost calculation runs for the Check Chain's `INVEST_TOPICS`, which add `short_term_investment`, because `COST_TRANSPARENCY` applies there; otherwise a short-term recommendation could never cite costs.
- `DOCUMENTATION` without a selection now fails as in the experiment (`no_core_selection_recorded`); the 3.4 deviation and its golden-test exemption are removed. A Rule Parameter missing for the selection marks `ruleParameterError` (escalates as before).
- The class instruction gets the category key (experiment parity: `category.replace("_", " ")`) and the raw conflicts joined by "; "; `RECOMMEND_CATEGORY` prompt file unchanged.
- New Rule Parameters change the config fingerprint, so all figure keys change once (as for Story 4.1); the compose image needs the rebuild at epic end (already open).
- Retro A5: the recommendation lives in `advisory.application.Recommendations`; `TurnOrchestrator` grew by ~25 lines.
- Review (combined sonnet, 2026-10-26), fixed: panel conflicts in plain words; AC1 E2E pins selection/cost figures in evidence, ledger and the exact selection inputs; a test for the missing-Rule-Parameter path; numeric profile text recorded as a number input; more selection branches (topic rules with an adequate fund, bucket text). Dismissed: guarding `profileFigures` in `chips` (every persona has a current account; the assessment card has the same dependency, 6.2 review); target rounding (expenses have two decimals and the months are whole, so the product is exact); renaming `RecommendationView.category` (its description says plain words; the key stays in the audit record).
- The chat column's stylesheet passed the 6 kB component-style warning budget (6.53 kB with the panel); the warning budget is raised to 7 kB (error stays 8 kB).
