# Epic 2 Context: Financial Overview with Traceable Figures

<!-- Generated from planning artifacts. Regenerate with compile-epic-context if planning docs change. -->

## Goal

A logged-in persona sees their liquidity, wealth and insurance situation in the Overview (Home, Liquidity, Wealth & Retirement tabs) and can open "Why?" on every figure to trace it to the Core function, inputs, assumptions and checks that produced it. This epic lays the data and number foundation for everything after it: the sandbox bank with seeded Synthetic Personas and Planted Ground Truth, the first pure Deterministic Core functions, the `CoreGateway` with stable figure keys (`provId`), and the persona-scoped provenance endpoint. Traceable figures are the product's main trust mechanism. Later chat answers, monitoring Triggers and Actions all reuse these figures and keys.

## Stories

- Story 2.1: Sandbox bank with seeded persona fixtures and Planted Ground Truth
- Story 2.2: Core gateway, figure keys and the "Why?" dialog
- Story 2.3: Liquidity tab with Cash-Flow Forecast and emergency fund
- Story 2.4: Wealth & Retirement tab with goal projection ranges
- Story 2.5: Home tab with summary and "Your insurance"

## Requirements & Constraints

- **Personas** (`data/personas/*.json`, validated by `data/personas/schema.json`, generated with a fixed seed, `fixtureVersion` = content hash, every transaction has a `category`, 12–24 months of history): `p-lena`, `p-marco`, `p-jonas`, `p-petra`, `p-tim`, `p-shortfall`, `p-harness` (no stored profile).
  - Lena: 2,400 EUR net, annual car premium 620 EUR due 1 Nov. Fixture calibrated so the UJ-1 forecast gives **−90 EUR on 1 Nov** (simulated date 15 Oct). With the later UJ-7 new premium of 744 EUR it gives **−214 EUR**.
  - Marco: 2,100 EUR income, 2,000 EUR expenses, 300 EUR emergency fund. Jonas: own private liability policy and also listed on his parents' family policy (Duplicate Coverage). Petra: Riester contract. Tim: about 80 EUR monthly surplus.
  - `p-shortfall` (backs Q&A item 56): variable monthly spending, and the premium debit falls before the next salary credit. The forecast on the premium due date must be **≥ 50 EUR below zero**, while fixed costs alone would stay positive. Reference calibration: 450 balance, salary on the 1st, premium debited on the 28th, about 400 variable on top of 1,800 fixed → 450 + 2,300 − 1,800 − 400 − 620 = −70 EUR (the addendum's monthly arithmetic); the daily reference forecast model planted in the fixture (Story 2.1) gives **−82.04 EUR** on 2026-11-28, which is the value later stories must reproduce. The experiments track uses exactly this (`experiments/data/personas/personas.json`: simulated date 2026-10-28, premium 2026-11-28).
- Liquidity view: current account and overnight deposit balances, a three-month daily Cash-Flow Forecast with any shortfall marked, emergency fund versus target. Wealth view: holdings, savings plans and goal projections as p10/p50/p90 ranges. **There is no allocation-drift view or `AllocationDrift`; this was removed in the 2026-10-21 scope cut.** Insurance appears as a "Your insurance" section on Home, not as a separate Protection tab. It shows contracts with type, premium, payment interval and next due date, plus Duplicate Coverage and missing-essential-cover flags (liability, occupational disability). It never shows an insurer recommendation or tariff comparison.
- Every forecast or projection carries its assumptions and is a range. A forecast without assumptions or a range fails validation.
- Determinism: identical inputs give identical outputs and identical `provId`s. Stochastic functions take an explicit seed. Domain time comes only from the persona's simulated date.
- Every persona-owned table registers with persona reset. The A2 test fails the build for any unregistered `persona_id` table.
- Language: English UI, plain words, no internal vocabulary.

## Technical Decisions

- **`bank` module** owns `accounts`, `transactions`, `contracts`, `holdings` and `savings_plans` (Flyway migration, registered with reset). The API is `bank.BankReadPort.snapshot(personaId, asOf)`, which returns only data up to `asOf`. Only `bank.BankPort` mutates bank tables. Story 2.1 must replace the reset placeholder (`2026-10-20` / `pre-fixtures`) in both `V3__persona_state_clock.sql` and `PersonaStateResetter` with the fixture start date and the fixtureVersion (retro item A3).
- **Core result shape** (`core` module, pure `..domain..` with `java.*` only and no `now()`): `CoreResult(function, inputs, outputs, assumptions, ruleParamVersions, List<SourcedNumber>)`. `SourcedNumber(provId, BigDecimal value, Unit unit, String label)` lives in `shared.domain`, with `Unit = EUR | FRACTION | PP | MONTHS | YEARS | COUNT`.
- **`provId`**: the first 16 hex characters of SHA-1 over personaId, function, canonical inputs, `asOf`, config fingerprint and output key. Every Core call goes through `core.CoreGateway`, including the ones monitoring and actions make later. The gateway persists each item via `audit.EvidenceStore.putIfAbsent`. A test asserts that the same call gives the same `provId`s.
- **Provenance API**: `GET /api/provenance/{provId}` in `audit.web`, persona-scoped. It reads only `evidence_items`. Another persona's id returns **404**. It returns the source (Core function in plain words, Rule Parameter version and validity, or KB citation), the inputs (each with its own `provId` where one applies), the assumptions and the check results.
- **Core functions in this epic**: `CashFlowForecast`, `ShortfallDetection`, `EmergencyFundTarget`, `Liquidity.liquidAssets`, `DataConfidence.of` (thresholds passed as arguments: ≥ 95 % categorised in the last 90 days, no gap > 5 days), `MonteCarloProjection`, `DuplicateCoverage.detect(contracts, persona)` and a missing-essential-cover rule. Monitoring and the Autonomy Gate reuse `DataConfidence`, `Liquidity` and the emergency-fund floor later, so each is defined once.
- **Monte Carlo** works on historical index data in `data/index/`. It uses `RandomGeneratorFactory.of("L64X128MixRandom").create(seed)` with the seed from `RequestContext.seeds`, never from the client. Its assumptions are return model, costs, inflation and horizon.
- **Reference values from the experiments prototype** (`experiments/finlab/core.py`, `experiments/data/rules/rule_parameters.yaml`): emergency fund target = `emergency_fund_months_min` 3 to `_max` 6 months of expenses; `projection_equity_mean_return` 0.06, `projection_equity_volatility` 0.15, `reference_ter_world_equity_etf` 0.002, `projection_inflation` 0.02. These are Rule Parameters with validity dates, and their versions go into `ruleParamVersions`. The prototype's forecast spreads variable spending evenly per day and books recurring flows on their day of month (clamped to month length).
- **Conventions**:
  - Money is `BigDecimal` scale 2 HALF_UP, rounded only at the Core output boundary, stored as `NUMERIC(14,2)`, and sent in JSON as a decimal string plus `currency: "EUR"`.
  - Percentages are fractions in the domain. Domain dates are `LocalDate`.
  - Errors are `ProblemDetail` with a closed `code`.
- **Transactions and context**: each use case is a `*UseCase` that is `@Transactional(REQUIRED)`. Module services put `MANDATORY` on their writing *methods*. Controllers call only use cases. These rules are now ArchUnit-enforced (retro item A1). Use cases build the `RequestContext` and unpack it into plain arguments for domain functions.
- **Frontend**: Angular standalone components, signals, and the generated `ng-openapi-gen` client (no hand-written DTOs; the build diff-checks the generated client). The OpenAPI document needs explicit `Money` / `ProblemCode` schemas. **No financial value is ever computed in TypeScript.** Every figure arrives with its `provId`. Charts use ngx-echarts.
- The first real `AuditWriter`/`EvidenceStore` caller should add an end-to-end check that its record appears in `/api/research/audit/export` (retro item A7).

## UX & Interaction Patterns

- **Workspace**: Overview on the left (min 560 px), Chat on the right (440 px, or 500 px from 1440 px wide). Four M3 tabs: Home, Liquidity, Wealth & Retirement, Letters. The active tab is in `?tab=home|liquidity|wealth|letters`. Each tab keeps its scroll position for the session. Below 1280 px is unsupported. Light theme only.
- **Overview card**: title, main figure with "Why?", supporting lines, and an optional risk callout. Show the callout only for a shortfall or an emergency fund below its floor. On cold load, show skeleton cards (3 per tab).
- **"Why?" dialog**: four sections in this order: **Where this number comes from**, **What went in**, **Assumptions**, **Checks**. Each check shows a tick or cross, a plain line, and the check id as a small caption. An input's "Why?" drills down in place with a Back link and never stacks a second dialog. Esc or Close returns focus to the trigger.
- **Cash-Flow chart**: a daily line over three months. Show the band only when the Core returns one; otherwise label the single line "Expected balance". Draw the zero line as a coral dashed line. The shortfall marker reads "−90 EUR on 1 Nov" (real minus sign) and opens "Why?". List the assumptions below the chart.
- **Range chart** (Wealth): a light-cyan p10–p90 band with blue edges and a navy median. Legend: "Middle outcome", "1 in 10 chance it's lower", "1 in 10 chance it's higher". The summary sentence ends "This is a range, not a promise." Follow it with an Assumptions list and "Why?". There is no Adjust area.
- **Home**: a three-figure summary (current account balance, lowest forecast balance in the next three months, emergency fund vs. target), each with "Why?", and the sections "Notifications", "Your rules" and "Your insurance". Empty Notifications text: "Nothing needs your attention right now. Fine checks your accounts regularly and will let you know." Empty rules text: "You haven't set up any rules. With a rule, Fine can take care of small things for you, like keeping your current account above zero." Flag lines are plain sentences, e.g. "You may be paying twice for liability insurance".
- **Number format**: `1,240.00 EUR` in confirmations and the dialog, `1,240 EUR` in running text. Use a real minus sign (−). Never reformat a backend figure to a different precision, so the same `provId` always shows the same value. Never show internal terms (`provId`, `L2`, Check Chain, Mandate) to customers; say "rules" and "checks".
- **Colours**: navy #000064 for text, action blue #0A5AE1 for links and "Why?". Coral #FF5A46 marks money risk only, on a tint, and never carries text. Negative-balance text uses error red #EB1700. Cyan #00BBFF is used only for the highlight ring. No green/red gain and loss colours.
- **Accessibility**: every chart has a one-sentence summary plus a visually hidden data table (date or year with p10/p50/p90). Status uses icon plus text. Targets are ≥ 24 px and buttons 44 px. Respect `prefers-reduced-motion`. Set `lang="en"`.

## Cross-Story Dependencies

- 2.1 comes first. Everything reads `BankReadPort` snapshots and the seeded fixtures. Its fixture calibration (−90 / −214 / −82.04, i.e. ≤ −50, under the daily reference model; −70 is only the addendum's monthly arithmetic) is asserted again later by the Planted Ground Truth checks (Epic 11) and by Scenarios S1–S6 and S13–S19.
- 2.2 (gateway, `provId`, provenance endpoint, dialog component) must exist before the figures in 2.3–2.5 can show "Why?".
- 2.5's Home summary reuses the 2.3 forecast and emergency-fund figures by the same `provId`.
- Later epics depend on this one:
  - Epic 3 uses figure chips with the same `provId`s and highlights the matching Overview elements.
  - Epic 8 puts Notifications and rules on Home, and monitoring calls the same Core functions through `CoreGateway`.
  - Epic 10 lets a confirmed letter premium (function `documentFact`) replace the expected payment in the forecast.
  - Epic 6 adds the demands-and-needs card to "Your insurance".
- Rule Parameter versioning (`knowledge.RuleParamPort`) is fully built in Epic 4. Epic 2 needs at least the emergency-fund and projection parameters with versions and validity.
