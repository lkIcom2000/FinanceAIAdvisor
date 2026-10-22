---
title: 'Story 2.4: Wealth & Retirement tab with goal projection ranges'
type: 'feature'
created: '2026-10-22'
status: 'done'
baseline_revision: '8c165943bb0ecfeebf7f3c6678dba7fbab4e22db'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-3-liquidity-tab-with-cash-flow-forecast-and-emergency-fund.md'
warnings: []
deferred:
  - summary: >-
      The Spring-wired CsvIndexReturns constructor resolving APP_HOME/data/index is not verified by a test.
    evidence: |-
      CsvIndexReturnsTest uses the package-private Path constructor; no series ships, so the integration tests always take the parametric path. Add an AppHome-based test when the first series lands.
    location: >-
      backend/src/main/java/de/financetool/core/adapter/CsvIndexReturns.java
    severity: low
---

<intent-contract>

## Intent

**Problem:** A persona with investments cannot see holdings, savings plans or where long-term saving may lead, and no seeded stochastic Core function exists (FR-5, FR-20, FR-21, AR23).

**Approach:** A pure, seeded `MonteCarloProjection` behind `CoreGateway`, holdings and savings-plan figures, `GET /api/overview/wealth`, and the Wealth & Retirement tab with a band chart, following the Story 2.3 patterns (gateway `record`, provenance, rule parameters, `FigureInput.figure` drill-down, chart setup).

## Boundaries & Constraints

**Always:**
- `MonteCarloProjection.project(initial, monthlyContribution, years, paths, seed, returnModel, ter)` in `core.domain`, randomness only via `RandomGeneratorFactory.of("L64X128MixRandom").create(seed)`; seed = `RequestContext.seeds().core()`. Same inputs + seed → identical result (test). Output: p10/p50/p90 at the horizon plus yearly p10/p50/p90 points, paid-in total, median in today's money; assumptions: return model, costs, inflation, horizon. Without assumptions or percentiles the result fails validation.
- Return model (decision, as in `experiments/finlab/core.py` `monte_carlo`): if `data/index/*.csv` (columns `month,return`, monthly fractions, > 24 rows) exists, bootstrap those returns; otherwise normal monthly returns from Rule Parameters `projection_equity_mean_return` 0.06 and `projection_equity_volatility` 0.15 (p.a.). No index file is shipped in this story; the assumption text names the model actually used. TER `reference_ter_world_equity_etf` 0.002 p.a. deducted monthly; inflation `projection_inflation` 0.02; horizon `projection_default_horizon_years` 30; paths 5,000. Add these Rule Parameters to `data/rules/rule_parameters.yaml` with version and validity (values from `experiments/data/rules/rule_parameters.yaml`).
- Projection per persona (decision): initial = sum of holding values (units × unitPrice, rounded at output), monthly contribution = sum of active savings-plan amounts; computed only when the persona has a holding or a savings plan.
- `GET /api/overview/wealth` → holdings (name, asset class, units, value figure), savings plans (name, monthly amount figure, day of month), projection (p10/p50/p90 figures with `provId`s, yearly points, assumptions, paid-in, `wholeEuros` strings for the summary) or null. Every figure keyed and stored via the gateway.
- UI: band chart light-cyan p10–p90 with blue edges and navy median; legend exactly "Middle outcome", "1 in 10 chance it's lower", "1 in 10 chance it's higher"; summary exactly "Between {p10} and {p90} EUR after {years} years. The middle outcome is {p50} EUR. This is a range, not a promise." (whole euros with thousands separators, from the API); then an "Assumptions" list; "Why?" on every figure; visually hidden table (year, p10, p50, p90); 3 skeleton cards; no Adjust area. Empty state for a persona without holdings or plans: "You don't have any investments or savings plans yet."

**Never:** no financial arithmetic in TypeScript; no chat what-if or `/api/simulate` (Epic 7); no real index data fetched from outside the repository.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Jonas | p-jonas, 2026-10-15 | holding + plan 100.00/month, projection p10 < p50 < p90, yearly points 1..30 | No error expected |
| Repeat | same call twice | identical figures and `provId`s, evidence stored once | No error expected |
| No investments | p-marco | holdings/plans empty, projection null, empty-state text | No error expected |
| Index file present | test with a temp `data/index` CSV | bootstrap model named in assumptions | Malformed CSV → startup/call fails naming the file |

</intent-contract>

## Code Map

- `backend/src/main/java/de/financetool/core/application/CoreGatewayService.java` -- add `wealth(...)`; reuse `record`, rule-parameter provenance
- `backend/src/main/java/de/financetool/core/domain/{CashFlowForecast,LiquidityFigures,FigureInput}.java` -- result/validation patterns
- `backend/src/main/java/de/financetool/knowledge/{RuleParamPort,adapter/YamlRuleParameters}.java` -- parameter source
- `backend/src/main/java/de/financetool/core/web/OverviewController.java` + `core/application/ShowLiquidityOverviewUseCase.java` -- endpoint pattern
- `backend/src/main/java/de/financetool/shared/AppHome.java` -- `data()` for `data/index`
- `frontend/src/app/pages/workspace/liquidity-tab.component.*`, `shared/{echarts-setup,why-link.component,figure.pipe,dates}.ts` -- tab, chart and figure patterns

## Tasks & Acceptance

**Execution:** domain projection + tests (determinism, seed change differs, validation, percentile order, bootstrap path with a fixture series) → index reader → rule parameters → gateway + use case + endpoint + integration test (Jonas values, provIds stored once, drill-down resolves, Marco null, 401/403) → OpenAPI snapshot + client → Wealth tab component + specs (legend, summary sentence, assumptions, empty state, hidden table, Why?).

**Acceptance Criteria:**
- Given Jonas, when the Wealth & Retirement tab opens, then holdings, the savings plan and the band chart show with the exact legend and the summary ending "This is a range, not a promise.", and every figure has "Why?".

## Spec Change Log

## Review Triage Log

### 2026-10-22 — Joint review pass 2.3–2.5 (Edge Case Hunter + Verification Gap; rows for Story 2.4)
- verdicts (joint pass, all stories): 21 findings — high 0, medium 5, low 13, false 3, maybe-false 0; Story 2.4 rows: 6
- findings:
  - `[false]` `[reject]` EC: monthly return below −100 % makes wealth negative — impossible with 4.3 % monthly volatility in the normal model; a bootstrap series of real index returns has no month below −100 %.
  - `[low]` `[reject]` EC: non-integer or huge horizon parameter — the value comes from the versioned rule file (30); a wrong value fails loudly.
  - `[low]` `[reject]` EC: series with ≤ 24 rows silently ignored — the assumption text names the model actually used.
  - `[low]` `[patch]` EC: uppercase .CSV not read — extension matched case-insensitively.
  - `[medium]` `[patch]` VG: projection seed from RequestContext unverified — gateway test with two seeds.
  - `[low]` `[defer]` VG: Spring-wired index directory never verified — no series ships; recorded in `deferred`.

## Implementation Notes

- No per-story review (user rule): joint review of Stories 2.3–2.5 after 2.5 (Edge Case Hunter + Verification Gap); done after verification with "review deferred to joint review 2.3-2.5".
- Live check: `data/rules` changes, so the image is rebuilt and checked on the compose stack.
- Open for the user: no historical index series exists in the repository; the projection uses the parametric model until a CSV is added to `data/index/`.
- Implementation decisions (2026-10-22):
  - Signature deviation: `MonteCarloProjection.project(initial, monthlyContribution, horizon, paths, seed, returnModel, ter, inflation)` — `initial`/`monthlyContribution` are keyed figures (drill-down), the horizon is the Rule Parameter itself (its version goes into `ruleParamVersions`), and `inflation` is an extra argument for the median in today's money. Return model is `Parametric(mean, volatility)` or `Bootstrap(source, returns)` (> 24 returns, else constructor throws). Simulation in `double` (StrictMath for pow/sqrt), path-major draw order; percentiles interpolate linearly (numpy default); outputs rounded to cents at the boundary.
  - `projection_default_horizon_years` (30) is not in `experiments/data/rules/rule_parameters.yaml`; added as a design parameter (Story 2.4). The other four values are copied from there.
  - New Core functions `holdingValue` (units × unit price), `savingsPlanAmount` and `investmentTotals` (starting value + monthly contribution, inputs drill down to each holding/plan figure); the projection's inputs drill down to the totals. Savings plans count as active when `startDate ≤ asOf`.
  - Index series: `core.adapter.CsvIndexReturns` reads `data/index/*.csv` once at startup (header `month,return`, `YYYY-MM`, return > −1, months strictly ascending); malformed file or more than one CSV fails startup naming the file(s). A series with ≤ 24 rows falls back to the parametric model (as in the prototype).
  - API: `projection.provId` = the p50 figure's key; also `startingValue`, `monthlyContribution`, `paidIn`, `p50TodaysMoney`, `years`; `wholeEuros` p10/p50/p90 are formatted in the backend (HALF_UP, thousands separators, real minus). Asset class is sent in plain words ("Equity ETF"). Without investments `projection` is `null`.
  - UI: one card per holding and per plan, a projection card with summary, band chart (custom polygon band `#CCF1FF`, p10/p90 lines `#0A5AE1`, median `#000064`), hidden year table, a figures list (p10, p50, p90, today's money, paid in — each with "Why?") and the Assumptions list.
- Live check 2026-10-22: image rebuilt (`docker compose up -d --build app`); `/api/overview/wealth` for p-jonas twice byte-identical: holding 2,004.76, plan 100.00, p10 43,189 / p50 86,595 / p90 184,571 EUR after 30 years, paid in 38,004.76, today's money 47,806.76, 30 points; p-marco `{"holdings":[],"savingsPlans":[],"projection":null}`. UI not inspected in a browser.

## Verification

**Commands:**
- `export JAVA_HOME=/c/Users/commc/.jdks/temurin-25 && cd backend && ./mvnw -B verify -Dfine.it.required=true` -- BUILD SUCCESS, 0 skipped
- `cd frontend && npx ng test --watch=false && npm run build` -- green

**Manual checks (if no CLI):**
- Compose stack rebuilt: `/api/overview/wealth` for Jonas returns the projection twice identically; Marco returns no projection.

## Auto Run Result

Status: done — review deferred to joint review 2.3-2.5.

**Summary:** Seeded `MonteCarloProjection` (L64X128MixRandom, seed from `RequestContext.seeds().core()`, 5,000 paths, 30 years; normal returns from Rule Parameters until a `data/index` CSV exists, then bootstrap), `Investments` figures (holding values, plan amounts, totals) with drill-down, `GET /api/overview/wealth`, and the Wealth & Retirement tab (holding and plan cards, band chart with the exact legend, summary sentence, projection figures with "Why?", assumptions, hidden year table, skeletons, empty state).

**Verification:** backend `verify -Dfine.it.required=true` 226 tests, 0 failures, 0 skipped; frontend 44 tests, build green. Live (image rebuilt, `data/rules` changed): two calls for p-jonas byte-identical; holding 2,004.76, plan 100.00, after 30 years p10 43,189 / p50 86,595 / p90 184,571 EUR, paid in 38,004.76; p-marco `projection: null`; health UP after the model check.

**Residual risks:** floating-point simulation is reproducible on one JVM; identical figures across JVM versions/platforms are not proven. No historical index series ships (user decision pending). UI not inspected in a browser.

**Joint review 2.3–2.5 (2026-10-22):** Edge Case Hunter + Verification Gap over the combined diff; 21 findings, 13 patched in one commit (rows for this story in the Review Triage Log), 1 deferred (2.4), 7 rejected. After the fixes: backend 243 tests, 0 failures, 0 skipped; frontend 54 tests, build green.
