---
title: 'Story 2.3: Liquidity tab with Cash-Flow Forecast and emergency fund'
type: 'feature'
created: '2026-10-22'
status: 'done'
baseline_revision: '0dff7067cc5a5c45675b22acdc85dbd605e90db0'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-2-core-gateway-figure-keys-and-the-why-dialog.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** Lena cannot see her balances, a forecast with the planted shortfall, or her emergency fund against its target (FR-4, FR-20, FR-21).

**Approach:** Pure Core functions behind `CoreGateway` (every figure keyed and stored as evidence, as in Story 2.2), a minimal versioned Rule Parameter source for the emergency-fund months, one overview endpoint for the Liquidity tab, and the Angular Liquidity tab with cards, forecast chart and "Why?" on every figure.

## Boundaries & Constraints

**Always:**
- Pure functions in `core.domain`, thresholds and parameters as arguments: `VariableSpending.monthly`, `CashFlowForecast`, `ShortfallDetection`, `EmergencyFundTarget`, `Liquidity.liquidAssets`, `DataConfidence.of(transactions, asOf, minCoverage, maxGapDays)` (thresholds from `RequestContext.thresholds`, i.e. `config/autonomy.yaml`).
- Forecast = Story 2.1 reference model (`backend/src/test/java/de/financetool/fixtures/ReferenceForecast.java`) for the expected line: start balance at end of `asOf`; per day recurrences (transactions with `recurrenceId` seen in the last two complete months; amount and day of the latest occurrence, day clamped), then `V / daysInMonth`, then contract premiums due that day (`nextDueDate` rolled forward by `paymentInterval` until it is after `asOf`; this resolves the 2.1 deferred item). V = mean variable spending of the last 3 complete months (debits on the current account without `recurrenceId`, `contractId`, category `TRANSFER`). Horizon: day after `asOf` to the same day three calendar months later.
- Range (decision): `low`/`high` lines use variable spending × (1 + band) / × (1 − band), band = Rule Parameter `forecast_variable_spending_band` = 0.15. Expected line is the middle. A `CashFlowForecast` result without assumptions or without the low/high lines fails validation (constructor throws).
- `ShortfallDetection`: first day the expected balance is below zero → that day's balance and date (Lena 2026-10-15: −90.00 on 2026-11-01; p-shortfall: −82.04 on 2026-11-28). Also report the lowest expected balance and its date.
- Emergency fund: current = overnight deposit balance; monthly expenses = mean of current-account debits over the last 12 complete months excluding `TRANSFER` and `SAVINGS_PLAN`; target = `emergency_fund_months_min` (3) and `_max` (6) × expenses, versions in `ruleParamVersions` and in provenance `ruleParameters` with validity.
- Rule Parameters (decision): `data/rules/rule_parameters.yaml` with the three keys above (key, value, unit, valid_from, valid_to, source, version), read by a minimal `knowledge.RuleParamPort.valid(key, asOf)` (refuses a key not valid at `asOf`). Epic 4 extends it.
- Lena fixture (decision): salary moves from the 3rd to the 2nd, so the lowest expected balance is −90.00 on 1 Nov (with the 3rd it would be −121.00 on 2 Nov); regenerate fixtures, ground-truth tests stay green.
- `GET /api/overview/liquidity` → current account, overnight deposit, liquid assets, emergency fund (current, target, months), forecast (daily points date/low/expected/high, assumptions, provId of the forecast), shortfall (amount, date, provId) or null, lowest balance; every figure with `provId`. Forecast inputs that are figures (current balance, variable spending) carry their own `provId` (drill-down).
- UI copy: cards "Current account", "Overnight deposit", "Emergency fund" (sub line "of {target} target (3 months of expenses)"); chart legend "Expected balance", band "Possible range"; coral dashed zero line; marker label "−90 EUR on 1 Nov" (no decimals in the marker, real minus) opening "Why?" on the shortfall; risk callout on the Current account card: "Your forecast balance drops to {amount} on {d MMM}."; one-sentence chart summary + visually hidden data table; 3 skeleton cards while loading. Chart: `ngx-echarts` + `echarts` (pinned versions ≥ 2 weeks old).

**Never:** no financial arithmetic in TypeScript (the summary sentence and table only render API values); no Home summary changes (Story 2.5); no edits to applied migrations.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Lena | p-lena, asOf 2026-10-15 | shortfall −90.00 on 2026-11-01, lowest −90.00, current 1974.00 | No error expected |
| Shortfall persona | p-shortfall, 2026-10-28 | shortfall −82.04 on 2026-11-28 | No error expected |
| No shortfall | p-marco | `shortfall` null, no callout, no marker | No error expected |
| Date moved past due date | researcher sets Lena to 2026-11-05 | premium rolled to 2027-11-01, no crash | No error expected |
| Invalid rule parameter | key not valid at asOf | Core refuses | `IllegalStateException` → 500 problem |

</intent-contract>

## Code Map

- `backend/src/main/java/de/financetool/core/application/CoreGatewayService.java` -- add gateway methods; reuse private `record`
- `backend/src/main/java/de/financetool/core/domain/{AccountBalance,CoreComputation,FigureInput}.java` -- patterns; `FigureInput.provId` enables drill-down
- `backend/src/main/java/de/financetool/core/web/OverviewController.java`, `core/application/ShowHomeOverviewUseCase.java` -- endpoint/use-case pattern
- `backend/src/main/java/de/financetool/bank/BankSnapshot.java` -- data shape (recurrenceId, contractId, account types)
- `backend/src/main/java/de/financetool/shared/config/AutonomyThresholds.java` -- data-confidence thresholds
- `backend/src/main/java/de/financetool/knowledge/package-info.java` -- module (depends on `shared`)
- `backend/src/test/java/de/financetool/fixtures/{PersonaFixtureGenerator,ReferenceForecast,PlantedGroundTruthTest}.java` -- Lena spec and reference model
- `frontend/src/app/pages/workspace/home-tab.component.*`, `shared/{why-link,figure.pipe}.ts` -- card and figure pattern

## Tasks & Acceptance

**Execution:** domain functions + unit tests (Lena/p-shortfall values, rolled due date, validation failure, data confidence thresholds) → `RuleParamPort` + YAML → gateway methods → `ShowLiquidityOverviewUseCase` + controller → integration test (endpoint values, provIds stored, drill-down provIds resolve via `/api/provenance`) → generator change + regenerated fixtures → OpenAPI snapshot + client → Liquidity tab component + specs (cards, marker text, callout, summary, hidden table, skeletons, Why?).

**Acceptance Criteria:**
- Given Lena at 15 Oct, when the Liquidity tab opens, then it shows 1,974.00 EUR, the overnight balance and the emergency fund vs. target, each with "Why?", a chart with the marker "−90 EUR on 1 Nov" and the risk callout.
- Given the forecast "Why?", when opened, then "What went in" lists the current balance and variable spending with their own "Why?" (drill-down).

## Spec Change Log

## Review Triage Log

### 2026-10-22 — Joint review pass 2.3–2.5 (Edge Case Hunter + Verification Gap; rows for Story 2.3)
- verdicts (joint pass, all stories): 21 findings — high 0, medium 5, low 13, false 3, maybe-false 0; Story 2.3 rows: 12
- findings:
  - `[low]` `[patch]` EC: spending band not range-checked — CashFlowForecast rejects a band outside [0, 1).
  - `[low]` `[reject]` EC: emergency-fund expenses with less than 12 months of history — all fixtures have 15 months; revisit when a shorter-history persona exists.
  - `[low]` `[reject]` EC: variable spending with less than 3 months of history — same as above.
  - `[low]` `[patch]` EC: SAVINGS_PLAN debit without recurrenceId counted as variable — excluded, as in EmergencyFundTarget.
  - `[medium]` `[patch]` EC: DataConfidence ignores the gap before the first booking — leading (and trailing) gap included.
  - `[low]` `[reject]` EC: second CURRENT/OVERNIGHT account dropped — the catalog enforces one CURRENT account; no fixture has two overnight deposits.
  - `[false]` `[reject]` EC: contract starting after asOf enters the forecast — BankReadPort already filters contracts by startDate <= asOf (Story 2.1).
  - `[low]` `[patch]` EC: default-locale toLowerCase — Locale.ROOT.
  - `[medium]` `[patch]` VG: liquidAssets value never asserted — asserted for p-lena and p-tim.
  - `[medium]` `[patch]` VG: shortfall data-confidence check and failing thresholds unverified — end-to-end assertion and a failing-threshold gateway test.
  - `[medium]` `[patch]` VG: chart markPoint click never exercised — spec triggers chartClick.
  - `[low]` `[reject]` VG other: test copies of the gateway mapping can drift — the end-to-end tests cover the same personas through the real gateway.

## Implementation Notes

- No per-story review (user rule 2026-10-21): joint review of Stories 2.3–2.5 after 2.5 (Edge Case Hunter + Verification Gap only); this spec goes to done after verification with "review deferred to joint review 2.3-2.5".
- Live check: this story changes `data/` (rules, fixtures), so the image is rebuilt and checked on the compose stack.
- Implementation decisions (2026-10-22):
  - One gateway method `CoreGateway.liquidity(context, origin)` returns `core.domain.LiquidityFigures` (balances, liquid assets, variable spending, emergency fund, forecast + daily points, shortfall/lowest); each Core function is recorded separately, so every figure has its own `provId`. Variable spending is its own Core figure (`variableSpending`), so the forecast's inputs "Current account balance" and "Average monthly variable spending" drill down.
  - `accountBalance` now carries the account (`CURRENT`/`OVERNIGHT`) in its key inputs, so the overnight balance gets its own key; this changes the Home current-account `provId` once (values unchanged).
  - The forecast uses the rounded V figure (scale 2) as input; it equals the reference V for every fixture.
  - The forecast's `provId` is that of its output `expectedEnd` (expected balance at the horizon end); `lowEnd`/`highEnd` are also stored.
  - `DataConfidence` is shown as the check `data-confidence` on the forecast and the shortfall figures (thresholds from `RequestContext.thresholds`).
  - Emergency fund response: `current`, `target` (min), `targetMax`, `months` (the min parameter, MONTHS), `monthlyExpenses`; without an overnight deposit (p-tim, p-harness) the fund is 0.00 and `overnightDeposit` is null.
  - Marker / callout amount: the backend sends `wholeEuros` (HALF_UP to whole euros) so the frontend does no rounding. Range band is a custom ECharts polygon from the API's low/high strings (no arithmetic in TypeScript). ECharts is loaded lazily (`echarts/core` parts, SVG renderer); `ngx-echarts@22.0.0`, `echarts@6.1.0` pinned exactly.
  - Rule Parameters load with SnakeYAML (`knowledge.adapter.YamlRuleParameters`); startup fails on malformed entries or overlapping versions; `valid` throws `IllegalStateException` for an unknown key or a date outside every version (→ 500 via the generic handler).
- Live check 2026-10-22: image rebuilt (`docker compose up -d --build app`), Lena re-seeded with the new fixture: p-lena 1974.00, shortfall −90.00 on 2026-11-01, lowest −90.00 on 2026-11-01, EF 5,700.00 of 5,993.49; p-shortfall −82.04 on 2026-11-28; p-marco no shortfall, EF 300.00 of 6,000.00. UI not inspected visually (no browser tool); covered by component specs.

## Verification

**Commands:**
- `export JAVA_HOME=/c/Users/commc/.jdks/temurin-25 && cd backend && ./mvnw -B verify -Dfine.it.required=true` -- BUILD SUCCESS, 0 skipped
- `cd frontend && npx ng test --watch=false && npm run build` -- green

**Manual checks (if no CLI):**
- Compose stack rebuilt: Lena re-seeded (new fixture hash), `/api/overview/liquidity` returns the values above; Marco has no shortfall.

## Auto Run Result

Status: done — review deferred to joint review 2.3-2.5.

**Summary:** Pure Core functions (variable spending, cash-flow forecast with ±15 % band, shortfall detection, emergency-fund target, liquid assets, data confidence), minimal `knowledge.RuleParamPort` over `data/rules/rule_parameters.yaml`, `GET /api/overview/liquidity`, and the Liquidity tab (cards, ECharts forecast with band, zero line and marker, risk callout, summary sentence, hidden table, skeletons, "Why?" with drill-down). Lena's salary moved to the 2nd; the contract due date rolls forward (2.1 deferred item resolved).

**Verification:** backend `verify -Dfine.it.required=true` 207 tests, 0 failures, 0 skipped; frontend 38 tests, build green; `ngx-echarts@22.0.0` (2026-06-11) and `echarts@6.1.0` (2026-05-19) pinned. Live (image rebuilt, `data/` changed): Lena re-seeded with the new hash; `/api/overview/liquidity` current 1974.00, shortfall −90.00 on 2026-11-01, 92 daily points (1 Nov: low −166.65, expected −90.00, high −13.35), 5 assumptions; p-shortfall −82.04 on 2026-11-28; Marco no shortfall (implementer's check).

**Residual risks:** UI not inspected in a browser (chart band and marker layout only covered by component specs with a stubbed chart); the Home current-account `provId` changed once (account type is now a key input).

**Joint review 2.3–2.5 (2026-10-22):** Edge Case Hunter + Verification Gap over the combined diff; 21 findings, 13 patched in one commit (rows for this story in the Review Triage Log), 1 deferred (2.4), 7 rejected. After the fixes: backend 243 tests, 0 failures, 0 skipped; frontend 54 tests, build green.
