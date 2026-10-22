---
title: 'Story 2.5: Home tab with summary and "Your insurance"'
type: 'feature'
created: '2026-10-22'
status: 'done'
baseline_revision: 'bb75de716e9ec76efd64414ce8dc04ba9df3cb68'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-2-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-2-3-liquidity-tab-with-cash-flow-forecast-and-emergency-fund.md'
warnings: []
deferred: []
---

<intent-contract>

## Intent

**Problem:** The Home tab shows only the current account; the persona cannot see the summary, their insurance contracts or a Duplicate Coverage flag before chatting (FR-4 summary, FR-6, FR-20).

**Approach:** Pure `DuplicateCoverage.detect` and `MissingEssentialCover` Core functions behind `CoreGateway`, contract figures, an extended `GET /api/overview/home`, and the full Home tab: three-figure summary reusing the Story 2.3 figures (same `provId`s), "Notifications" and "Your rules" empty states, and "Your insurance". Plus per-tab scroll memory in the workspace.

## Boundaries & Constraints

**Always:**
- `DuplicateCoverage.detect(contracts, personaId)`: two or more contracts covering the same `coverage` for the persona (as policyholder or co-insured) → one flag citing every contract involved. `MissingEssentialCover.detect(contracts)`: essential covers `LIABILITY` and `OCCUPATIONAL_DISABILITY` (decision: Riester/pension contracts do not count), flag when none covers it. Both pure, in `core.domain`, results with assumptions (e.g. "Whether the family policy still covers you depends on its conditions, such as having your own household.").
- Contract figures: premium (EUR) per contract with `provId` (contracts without premium show "Paid by {policyholder}"), next due date rolled forward as in Story 2.3; flags carry the `provId` of their Core result.
- Summary = the Story 2.3 figures for current account balance, lowest forecast balance (with date) and emergency fund vs. target, computed through the same gateway path so the `provId`s equal those of the Liquidity tab (test asserts equality). A shortfall shows the Story 2.3 risk callout text on the summary tile.
- UI copy exactly: section titles "Notifications", "Your rules", "Your insurance"; empty states "Nothing needs your attention right now. Fine checks your accounts regularly and will let you know." and "You haven't set up any rules. With a rule, Fine can take care of small things for you, like keeping your current account above zero."; Duplicate Coverage line "You may be paying twice for liability insurance" (pattern "You may be paying twice for {coverage in plain words} insurance"); missing-cover line "You don't have {coverage in plain words} insurance." Each contract row: type in plain words, premium, payment interval ("monthly"/"quarterly"/"yearly"), next due date (`d MMM yyyy`), "Why?". No insurer recommendation, no tariff comparison, no provider advice.
- Workspace: each tab keeps its own scroll position for the session (scroll container `.overview`); chat width stays 440 px / 500 px from 1440 px; nothing overlaps at 1366 and 1440 (spec test on the grid template and the media rule).
- Decision: no "Set up a rule" button yet (rules arrive in Epic 9; a dead button would mislead).

**Never:** no notifications or rules data (Epics 8/9) — empty states only; no financial arithmetic in TypeScript; no LLM.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Jonas | p-jonas | flag "You may be paying twice for liability insurance" citing his own and the family policy; missing occupational disability flag | No error expected |
| Lena summary | p-lena, 2026-10-15 | 1,974.00 EUR; lowest −90.00 EUR on 1 Nov with risk callout; emergency fund vs. target; `provId`s equal to the Liquidity tab | No error expected |
| No contracts | p-tim | "Your insurance" lists nothing and shows both missing-cover lines | No error expected |
| Tab switch | scroll Home, switch to Liquidity and back | Home scroll position restored | No error expected |

</intent-contract>

## Code Map

- `backend/src/main/java/de/financetool/core/application/{CoreGatewayService,ShowHomeOverviewUseCase,ShowLiquidityOverviewUseCase}.java` -- gateway `record`, liquidity figures, existing home endpoint
- `backend/src/main/java/de/financetool/core/domain/*` -- Core function and figure patterns (Story 2.3/2.4)
- `backend/src/main/java/de/financetool/bank/{BankSnapshot,Coverage,InsuredRole,PaymentInterval}.java` -- contract data
- `backend/src/main/java/de/financetool/core/web/OverviewController.java` -- `/api/overview/home`
- `frontend/src/app/pages/workspace/{home-tab,liquidity-tab,workspace.page}.component.*`, `shared/*` -- cards, callout, figure, Why?

## Tasks & Acceptance

**Execution:** domain functions + tests (Jonas duplicate citing both contracts, Tim missing covers, Lena none duplicated, rolled due date) → gateway + extended home use case/endpoint + integration test (summary `provId`s equal Liquidity's, contracts and flags, 401/403) → OpenAPI snapshot + client → Home tab (summary tiles with callout, Notifications/Your rules empty states, Your insurance rows and flag lines) + workspace scroll memory + specs.

**Acceptance Criteria:**
- Given Jonas logs in, when Home opens, then the summary shows three figures with "Why?", both empty states, and "Your insurance" lists his contracts with the line "You may be paying twice for liability insurance", without any insurer recommendation.

## Spec Change Log

## Review Triage Log

### 2026-10-22 — Joint review pass 2.3–2.5 (Edge Case Hunter + Verification Gap; rows for Story 2.5)
- verdicts (joint pass, all stories): 21 findings — high 0, medium 5, low 13, false 3, maybe-false 0; Story 2.5 rows: 3
- findings:
  - `[low]` `[patch]` EC: "Policies held by Jonas and Jonas" — holder names de-duplicated.
  - `[low]` `[patch]` EC: scroll position stored under the cached tab after an external ?tab= change — tab derived from the selected index.
  - `[false]` `[reject]` EC: interval words could violate the OpenAPI enum — PaymentInterval has exactly the three published values.

## Implementation Notes

- No per-story review (user rule): after this story the joint review of Stories 2.3–2.5 runs with Edge Case Hunter + Verification Gap only; triage per finding into the affected story's spec; fixes as one commit "fix: code review of Stories 2.3-2.5".
- Live check: local `spring-boot:run` (no `pom.xml`/Dockerfile/compose/`data/` change); the full compose check including the research profile runs once at the end of Epic 2.
- Implementation decisions (2026-10-22):
  - Domain: `InsuranceContract` (own `Cover` enum with plain words and type name, since `core.domain` may not import `bank`), `InsurancePremium` (function `contractPremium`, one figure per paid contract; `nextDueAfter` rolls the stored date forward as in Story 2.3), `DuplicateCoverage` and `MissingEssentialCover`, `CoverageFlag`, `InsuranceFigures`. Pension contracts (`RIESTER_PENSION`) are neither compared for Duplicate Coverage nor counted as cover (assumption lines say so).
  - Flag keys: `duplicateCoverage` has the output `duplicates` (number of flags, so the result is keyed even without a flag) plus one output `contracts.<COVER>` (number of contracts) per flag; `missingEssentialCover` has one output `covered.<COVER>` per essential cover. A flag carries the key of its own output. The co-insured assumption ("Whether the family policy still covers you depends on its conditions, such as having your own household.") is added only when a co-insured contract is involved.
  - Gateway `CoreGateway.insurance(context, origin)`; `ShowHomeOverviewUseCase` calls `liquidity` and `insurance` in one transaction, so the summary keys equal the Liquidity tab's (`HomeOverviewEndToEndTest` asserts equality for current account, lowest balance, shortfall and emergency fund).
  - `GET /api/overview/home` keeps `currentAccount` and adds `lowestBalance`, `shortfall` (nullable), `emergencyFund` (same shape as Liquidity) and `insurance` (`contracts` with id, type, policyholder, coInsured, premium?, paymentInterval? `monthly|quarterly|yearly`, nextDueDate?; `flags` with kind, coverage in plain words, provId, contractIds). The insurer name is not sent.
  - Home tab: summary tiles "Current account", "Lowest balance in the next three months" (date line plus the Story 2.3 callout from `shortfall.wholeEuros`), "Emergency fund"; flag sentences are built in the template; a duplicate flag adds "Policies held by {policyholders}"; a contract without premium shows "Paid by {policyholder}" and no "Why?".
  - Scroll memory: `mat-tab-group` gets `preserveContent` (tabs stay alive, no reload on switch); `WorkspacePage` stores `.overview.scrollTop` per tab on `selectedIndexChange` and restores it on `animationDone`. `--fine-chat-width` (440/500 px media rule) moved from `styles.scss` into `workspace.page.scss` so the spec can check the grid template and media rule.
- Live check deviation: the compose `db` port is not published, so instead of `spring-boot:run` the app image was rebuilt (`docker compose up -d --build app`). Results 2026-10-22: p-lena 1974.00, lowest −90.00 on 2026-11-01, callout amount −90, EF 5,700.00 of 5,993.49, all summary keys equal `/api/overview/liquidity`, car 620.00 yearly due 2026-11-01, liability 62.00 yearly due 2027-04-01, flag missing occupational disability; p-jonas duplicate liability citing 2 contracts + missing occupational disability, family policy without premium; p-tim no contracts, both missing-cover flags. UI not inspected in a browser.

## Verification

**Commands:**
- `export JAVA_HOME=/c/Users/commc/.jdks/temurin-25 && cd backend && ./mvnw -B verify -Dfine.it.required=true` -- BUILD SUCCESS, 0 skipped
- `cd frontend && npx ng test --watch=false && npm run build` -- green

**Manual checks (if no CLI):**
- Local run: Jonas `/api/overview/home` returns the duplicate flag citing both contracts; Lena's summary `provId`s equal `/api/overview/liquidity`.

## Auto Run Result

Status: done

**Summary:** Pure Core functions `DuplicateCoverage`, `MissingEssentialCover` and `InsurancePremium` behind `CoreGateway.insurance`; `GET /api/overview/home` with the three-figure summary (same `provId`s as the Liquidity tab), shortfall callout, contracts and flags; Home tab with summary tiles, "Notifications" and "Your rules" empty states, "Your insurance" with plain-language flags; per-tab scroll memory; chat width rule moved into `workspace.page.scss`.

**Verification:** before the review: backend 237 tests, frontend 51, build green; live (compose image rebuilt by the implementer instead of a local run, see Implementation Notes): Lena 1,974.00 / lowest −90.00 on 1 Nov with callout / emergency fund 5,700.00 of 5,993.49, summary keys equal Liquidity's; Jonas duplicate liability citing two contracts plus missing occupational disability; Tim both missing-cover lines.

**Joint review 2.3–2.5 (2026-10-22):** Edge Case Hunter + Verification Gap over the combined diff; 21 findings, 13 patched in one commit (rows for this story in the Review Triage Log), 1 deferred (2.4), 7 rejected. After the fixes: backend 243 tests, 0 failures, 0 skipped; frontend 54 tests, build green.

**Residual risks:** UI not inspected in a browser; scroll restore relies on the tab group's animation-done event; hidden-tab charts not checked after a window resize.
