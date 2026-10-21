---
title: "Sprint Change Proposal: Scope Reduction before Implementation"
status: approved and applied (2026-10-21)
created: 2026-10-21
mode: batch
trigger: author decision (velocity), no story implemented yet
affects:
  - prds/prd-FinanceTool-2026-10-20/prd.md
  - prds/prd-FinanceTool-2026-10-20/addendum.md
  - architecture/architecture-FinanceTool-2026-10-20/ARCHITECTURE-SPINE.md
  - ux-designs/ux-FinanceTool-2026-10-21/EXPERIENCE.md
  - ux-designs/ux-FinanceTool-2026-10-21/DESIGN.md
  - epics.md
---

# Sprint Change Proposal: Scope Reduction before Implementation (2026-10-21)

## 1. Issue Summary

**Trigger.** After the epic breakdown (`epics.md`, 63 stories, draft), the author estimated the path to the user acceptance test at 4–6 months of solo work. The thesis timeline needs a faster route. No story has been implemented; the change lands before sprint planning, so nothing is rolled back.

**Problem statement.** The planned scope contains features that demonstrate the hybrid principle a second time, or serve comfort and robustness beyond the study setup. They add effort without adding evidence for the research questions.

**Issue type.** Strategic scope decision (MVP review), not a technical failure.

**Guiding rule.** Cut only what neither the main RQ (compliant, responsible, secure advice) nor the sub-RQ (deterministically enforced autonomy) depends on.

**Protected (never cut):** the turn pipeline with classification, Check Chain, Number Check and block routing; the Autonomy Gate with Mandate, L3, Undo and Revoke; the Document Grounding Check and Fact Card; the Q&A and Scenario Acceptance Suites.

## 2. Checklist Summary

| Item | Status | Finding |
|---|---|---|
| 1.1 Triggering story | N/A | No story implemented; trigger is the effort estimate. |
| 1.2 Core problem | Done | Strategic scope reduction. |
| 1.3 Evidence | Done | 63 stories; ~53 on the path to the user test; Documents MVP alone estimated at 2–3 weeks (proposal of 2026-10-21). |
| 2.1–2.5 Epic impact | Done | Epics 5, 6, 7, 8, 9, 10, 12 shrink; no epic is removed; order unchanged. |
| 3.1 PRD | Action-needed | FR-18, FR-19, FR-30, FR-34, FR-37, FR-43, §5, §6, §8.3, §8.4, SM-C3, UJ-3, §11 (see §4.1). |
| 3.2 Architecture | Action-needed | AD-5, AD-10, AD-15, AD-17, AD-19, Stack, Deferred, Capability map (see §4.3). |
| 3.3 UX | Action-needed | Research Console, waiting card, assessment card, re-confirm card, Adjust area, rule templates, responsive, accessibility, Flows 3 and 7 (see §4.4). |
| 3.4 Other artifacts | Done | `experiments/scenarios.yaml` not yet written; Colab track unaffected except a note on E4 (see §3, finding C). |
| 4 Path | Done | MVP Review (scope reduction) with Direct Adjustment of all artifacts. |
| 6.4 sprint-status.yaml | N/A | Not yet generated; sprint planning runs after this change. |

## 3. Impact Analysis

### Effort impact (corrected estimate)

The previous chat estimate ("~25 % fewer stories") was too optimistic. Most savings come from **slimmer stories**, not fewer ones:

- Stories in total: 63 → **53** (2 stretch removed; 6.5, 7.2 and 12.3 removed; 2.5+2.6, 4.1+4.4, 5.1+5.2, 8.2+8.4 and 12.1+12.2 merged).
- Stories on the path to the user test: ~53 → **~47**, several of them noticeably smaller (3.1, 3.2, 6.2, 6.4, 8.x, 9.2, 12.x).
- Expected effort reduction to the user test: **about 15–20 %**, plus removal of the Azure GPU-quota risk.
- Larger gains come from the reserve list (§6) and from the BMAD process (code review only for safety-critical stories).

### Slimmer UI (activated on the author's request, 2026-10-21)

The author activated the "slimmer UI" group of the reserve list immediately:

- **Protection moves into Home.** The Overview has four tabs (Home, Liquidity, Wealth & Retirement, Letters). Contracts, flags and the demands-and-needs statement appear as a "Your insurance" section on Home (FR-6 still met).
- **Wealth & Retirement tab minimal.** Savings plans and the goal projection range only; target vs. current allocation and drift are removed (FR-5 reduced, `AllocationDrift` removed from FR-20).
- **No Advisor View UI.** The advisor reads cases and posts replies through the `/api/advisor/**` endpoints via Swagger UI or `deploy/research/advisor.sh`. The customer side (ESCALATE card, human-advisor message, "Answered") is unchanged.
- **Letter template as static HTML.** The backend serves the filled template directly as a printable page (`GET /api/documents/{id}/letters/{templateId}`), with the on-screen banner inside the template (`@media screen`); there is no separate Angular print route. The `legal` Action, the L1 cap and "never sent" stay.

Effect: the path to the user test is about **20–25 % shorter** in total (instead of 15–20 %); Story 2.5 and the Advisor UI part of Story 5.1 disappear.

### Findings that need a decision recorded

- **A. Gate rows without a scenario.** Removing rebalancing (S7, S14) means no proposed Action is of class `investment` or irreversible. The gate rows "not reversible", "investment > 5 % of liquid assets" and "investment without suitability" are then verified **only by the gate table test**, not by a Scenario. PRD §8.3's boundary "the Action is irreversible" is re-worded accordingly. The sub-RQ is still answered: the gate is fully implemented and tested; the Scenario Suite exercises the Mandate, revoke, Data Confidence, floor and `legal` caps.
- **B. Accessibility.** Dropping 200 % zoom and narrow layouts means WCAG 2.2 AA criteria 1.4.4 (Resize Text) and 1.4.10 (Reflow) are not met. The accessibility floor becomes "WCAG 2.2 AA except 1.4.4 and 1.4.10", recorded as a limitation (threat to validity), since the study runs on a fixed 1366×768 laptop.
- **C. Data Exposure Levels.** The app implements only `aggregated` and `none`; `raw` and `pseudonymised` exist only in the Colab track (E4). **If E4 selects `raw` or `pseudonymised` as the winner, that level is added to the app** (a small change in the exposure filter) instead of overriding the experiment result.

## 4. Detailed Change Proposals

### 4.1 PRD (`prd.md`)

| Location | Change |
|---|---|
| §2.3 UJ-5 | OLD: "In the Advisor View, bank advisor Kaya sees three things…" NEW: "Through the advisor interface (API), bank advisor Kaya sees three things…" |
| §4.1 FR-2 | NEW: "A bank advisor can log in with an advisor role that grants access to the advisor endpoints (§4.11) and has no persona workspace." |
| FR-5 | NEW: "The user can see savings plans and holdings, and goal projections as ranges (median, 10th and 90th percentile)." |
| FR-20 | Remove the bullet "Allocation drift." |
| FR-42 | NEW lead: "The advisor retrieves the list of open cases through the advisor endpoints (no dedicated UI in the MVP)…" rest unchanged. Glossary "Advisor View": "The advisor endpoints, used through Swagger UI or scripts." |
| FR-59 | Add: "The template is served as a printable HTML page by the backend." |
| §2.3 UJ-3 | OLD: "Jonas changes the rate to 200 EUR, and the chart updates." NEW: "Jonas asks again with 200 EUR, and Fine shows the new range." |
| §3 Glossary, Trigger | OLD: "…or regulatory event, or rule-based drift, that starts…" NEW: "…or regulatory event that starts…" |
| §3 Glossary, Data Exposure Level | Add: "The application implements `aggregated` and `none`; `raw` and `pseudonymised` are studied only in the Experiment Track (E4)." |
| FR-18 | NEW text: "The user receives a short demands-and-needs statement covering liability, occupational disability and Duplicate Coverage. It is built from data already available through the Bank Adapter plus the missing facts, which are asked in one form card (e.g. whether the user has their own household). It contains no product selection." |
| FR-19 | Mark as **Removed (scope reduction 2026-10-21)**. Keep the heading for traceability. |
| §4.8 description | Remove "and rule-based drift (±5 pp)". |
| FR-30 | Remove the bullets "Upcoming contract due date", "Salary change", "Expiring promotional rate", "Allocation drift beyond ±5 pp". Add the sentence: "Contract due dates remain inputs to the Cash-Flow Forecast." Remaining Triggers: forecast shortfall, emergency fund below target, Duplicate Coverage, Rule Parameter or KB change (incl. ECB key rates), confirmed Document deadline. |
| FR-34 | Mark as **Removed (scope reduction 2026-10-21)**; a changed parameter set is a new what-if question. |
| FR-37 | Remove item 3 "Rebalancing via contributions". NEW lead: "Only the following two Actions can reach L3." |
| FR-43 | NEW: "The data passed to the LLM is configurable per Agent Configuration: `aggregated` or `none`. Under `none`, the LLM receives only Deterministic Core results and no persona attributes. The levels `raw` and `pseudonymised` are evaluated in the Experiment Track (E4); if E4 selects one of them, it is added to the application. Document processing uses the fixed level `redacted` (FR-55)." |
| §5 Privacy | OLD: "(local PC or EU-hosted Azure VM)" NEW: "(local PC; an EU-hosted Azure VM is deferred)". |
| §6.1 Autonomy Levels | OLD: "L3 for the three Actions in FR-37" NEW: "L3 for the two Actions in FR-37". |
| §6.1 Documents | Remove the stretch sentence (insurance policy, payment reminder). |
| §6.1 Platform | NEW: "Java Spring Boot backend and Angular frontend, running locally. The user acceptance test runs in person on the author's machine. Deployment to an EU-hosted Azure VM is deferred." |
| §6.2 Out of Scope | Add: "Stretch Document types (insurance policy, payment reminder); rebalancing Actions; salary, promotional-rate and drift Triggers; profile re-confirmation (FR-19); in-place parameter adjustment (FR-34); an Advisor View UI; a separate Protection tab; the allocation-drift view; a Research Console UI (research functions are REST endpoints); layouts below 1280 px and 200 % zoom; Azure deployment." |
| §8.3 boundaries | OLD: "the Action is irreversible;" NEW: "the Action is irreversible (verified by the Autonomy Gate table test, because no irreversible Action is proposed in the MVP);" |
| §8.4 | Add: "Sessions run in person on the author's laptop (1366×768, Chrome)." |
| §8.5 SM-C3 | OLD: "checked on Scenarios S1, S4 and S7" NEW: "checked on Scenarios S1 and S4". |
| §8.7 | Remove "Each stretch type adds 8 variants." |
| §10 Threats | Add: "Accessibility limited to the study laptop: WCAG 2.2 AA except 1.4.4 and 1.4.10." and "Gate rows for `investment` and irreversible Actions are verified only by table tests, not by Scenarios." |
| §11 | Add: "2026-10-21 (Correct Course, scope reduction): see `../../sprint-change-proposal-2026-10-21-scope.md`." |

### 4.2 Addendum (`addendum.md`)

| Location | Change |
|---|---|
| §A S7, S8, S10, S11, S14 | Mark each row **Removed (scope reduction 2026-10-21)**; keep ids so S-numbers stay stable. |
| §A S9 | Persona: "Any persona with a savings plan" (was "Same as S7"). Expected unchanged: no Trigger. Kept as the guard that price movements are never Triggers. |
| §B | Add: "Remaining Scenarios: S1–S6, S9, S12a, S12b, S13, S15–S19." |
| §C | Remove "Stretch types". |

### 4.3 Architecture Spine (`ARCHITECTURE-SPINE.md`)

| Location | Change |
|---|---|
| Front matter `binds`/`scope` | Remove "Azure" from scope; note "FR-19 and FR-34 removed". |
| AD-5 Exposure filter | Add: "Implemented levels: `aggregated`, `none` (PRD FR-43)." |
| AD-10 | OLD: "L3 is possible only for `EF_TOP_UP`, `SHORTFALL_TRANSFER` and `REDIRECT_CONTRIBUTIONS`." NEW: "L3 is possible only for `EF_TOP_UP` and `SHORTFALL_TRANSFER`." |
| AD-11 Research endpoints | Add: "They are used through Swagger UI and scripts under `deploy/research/`; there is no Research Console UI. The `RESEARCHER` login opens Swagger UI (research profile only)." |
| AD-12 / AD-16 / AD-24 binds | Remove FR-19, FR-34. AD-12 `profile` row: drop "Monitoring calls `markStale` (FR-19)". |
| AD-15 Scenario suite | OLD: "runs S1–S14" NEW: "runs S1–S6, S9, S12a, S12b, S13 and S15–S19". |
| AD-17 | Title NEW: "One image and one compose file". Services: drop `caddy`. Replace "Azure host" with "Azure host (deferred): one EU GPU VM (NCas_T4_v3) with Caddy, only if a remote study becomes necessary." |
| AD-19 | Remove FR-34 from binds; add: "Waiting-card steps are timer-driven; the polled API has no turn-status field." |
| AD-19 binds | Remove FR-42 (no advisor UI). |
| AD-27 Rendering | Add: "The rendered page is opened directly in a new browser tab; the on-screen banner is part of the template and hidden with `@media print`." |
| Source tree comment | `frontend/`: "chat \| Financial Overview" (no Advisor View). |
| Stack | Azure row: mark "(deferred)". |
| Environments diagram | Mark the Azure subgraph "(deferred)". |
| Capability map | 4.4: FR-17, FR-18; 4.9: FR-33. |
| Deferred | Replace the Azure bullet with "Azure deployment: deferred; the user study runs locally in person." |

### 4.4 UX (`EXPERIENCE.md`, `DESIGN.md`)

| Location | Change |
|---|---|
| IA table, tabs | Remove the Protection tab row; Home gains a "Your insurance" section (contracts, flags, demands-and-needs statement). Wealth tab: savings plans and goal range only. Tab bar: four tabs. |
| IA table, Advisor View; Roles table `ADVISOR` | Advisor uses Swagger UI / scripts (no `/advisor` route). Components "Advisor case list", "Advisor case detail" and State row "Advisor: no open cases" removed. Human-advisor message and ESCALATE card unchanged. |
| Letter print view | Served by the backend as static HTML (no Angular route); banner inside the template, hidden in print. |
| Flow 4 steps 2 and 5; Flow 5 steps 3–4 | Protection references → Home "Your insurance"; Kaya works via the advisor endpoints. |
| EXPERIENCE Foundation, Target device | NEW: "A laptop at 1366×768 or larger. Layouts below 1280 px and 200 % zoom are out of scope." |
| Roles table, `RESEARCHER` | Lands on Swagger UI (research profile only); no Research Console. |
| IA table | Remove the Research Console row. |
| Component: Waiting card | NEW: "Steps advance on a timer; unreported steps show no tick. No regeneration relabel." Remove the handoff note at the end of the spine. |
| Component: Assessment card | NEW: "One form card listing all missing fields at once; pre-filled values tagged 'From your account data' with Looks right / Change; **Save my answers** writes through the profile confirm control; Fine then resumes the original question. No stepper, no per-group progress." |
| Component: Profile re-confirm card | Remove (FR-19 removed). |
| Component: Range chart card | Remove the Adjust area and the re-calc behaviour; the State Patterns row "Simulation re-calc" is removed. |
| Component: Rule setup dialog | Two templates (Keep a safety cushion in my current account; Top up my emergency fund). |
| Component: Research Console; State row "Research action running" | Remove. |
| Accessibility Floor | Standard NEW: "WCAG 2.2 AA except 1.4.4 and 1.4.10 (recorded limitation)". Remove the 200 % zoom sentence. |
| Responsive & Platform | Keep only "≥ 1440 px" and "1280–1439 px"; below 1280 px is unsupported. |
| Flow 3 step 5 | NEW: "He types 'And with 200 EUR?'; a new proposal card and range chart follow." |
| Flow 7 | Researcher uses the `study/prepare` endpoint via Swagger UI or `deploy/research/prepare.sh`. |
| UAT anchors | Unchanged. |
| DESIGN Layout & Spacing | Remove the < 1024 px sentence; Research Console mention removed from the Advisor View line and the Components list. |

### 4.5 Epics and Stories (`epics.md`)

| Story | Change |
|---|---|
| Requirements Inventory | Update FR-18, FR-19 (removed), FR-30, FR-34 (removed), FR-37, FR-43, NFR7, NFR8, NFR9; remove AR24; mark UX-DR15, UX-DR28 removed; reduce UX-DR8, UX-DR14, UX-DR17, UX-DR23, UX-DR38. |
| 1.3 | `RESEARCHER` lands on Swagger UI (research profile); responsive AC reduced to ≥ 1280 px. |
| 2.4 | Wealth tab: savings plans and goal range only; `AllocationDrift` removed. |
| 2.5 + 2.6 | **Merged** into 2.5 "Home tab with summary and 'Your insurance'" (Duplicate Coverage detection stays); responsive AC reduced to 1440 px and 1366 px. |
| 5.1 | Advisor endpoints + customer-side reply delivery; no Advisor View UI (UX-DR27 removed). |
| 10.5 | Letter served as static HTML by the backend; no Angular print route. |
| 3.1 | Waiting card timer-driven; turn-status AC removed. |
| 3.2 | Exposure levels `aggregated` and `none` only. |
| 3.6 | Regeneration relabel AC removed (regeneration itself stays). |
| 4.1 + 4.4 | **Merged** into 4.1 "Versioned Rule Parameters and the Subsidy Calculator". |
| 5.1 + 5.2 | **Merged** into 5.1 "Advisor handles a case end to end". |
| 6.2 | Assessment as one form card. |
| 6.4 | Simplified IDD statement with one form card for missing facts. |
| 6.5 | **Removed** (FR-19). |
| 7.2 | **Removed** (FR-34). |
| 8.2 + 8.4 | **Merged** into 8.2 "Monitoring run with all MVP Triggers" (shortfall, emergency fund, Duplicate Coverage, Rule change incl. ECB and S12b escalation; price-drop guard). |
| 9.1 | Two rule templates. |
| 9.2 | Two L3 Actions; S14 reference removed. |
| 10.8, 10.9 | **Removed** (stretch). |
| 11.2 | Scenario list S1–S6, S9, S12a/b, S13, S15–S19; SM-C3 on S1, S4. |
| 11.5 | Matrix rows for FR-19 and FR-34 read "Removed by scope reduction 2026-10-21"; gate rows note table-test-only evidence (finding A). |
| 12.1 + 12.2 | **Merged** into 12.1 "Research scripts and study preset catalogue" (Swagger UI and `deploy/research/*.sh`, no UI). |
| 12.3 | **Removed** (Azure deferred). |
| 12.4 | Dry run on the author's laptop, in person. |
| FR Coverage Map | Updated; FR-19 and FR-34 point to the matrix rows in 11.5. |

## 5. Recommended Approach

**MVP Review with Direct Adjustment.** All edits are made in place before sprint planning. Alternatives considered: removing Documents (UJ-7) entirely (rejected by the author: it is the hardest test of the hybrid principle and carries the planted-error study task); keeping full scope and only speeding up the process (insufficient on its own).

- **Effort:** about half a day of document edits.
- **Risk:** low. Two documented limitations (findings A, B) and one conditional item (finding C).
- **Timeline impact:** path to the user test about 15–20 % shorter; Azure quota risk removed.

## 6. Reserve List (pre-justified, not active)

If the velocity measured after Epic 3 projects the user test too late, these cuts can be activated without a new Correct Course, in this order. Each activation is recorded as a one-line entry in PRD §11.

1. **Judge (Story 3.9)** only if E3 selects `A+B+C`; otherwise the reference configuration is `A+B` and FR-26 is marked "not active, justified by E3".
2. **Retrieval and KB (4.2, 4.3)** minimal (≤ 10 documents) or off if RAG does not win E1 (FR-24 allows off; `INFORM` uses the no-source template).
3. **User test tasks:** drop UJ-3 or UJ-4; the related stories keep their automated verification but skip UI polish.
4. **Exactly five personas:** Lena, Marco, Jonas, Petra, Tim (plus `p-shortfall` and `p-harness` as test-only fixtures).

**Decision point:** after Epic 3, compute actual days per story and project the user test date.

## 7. Implementation Handoff

- **Scope classification:** Moderate (backlog reorganisation and document edits; no architecture redesign).
- **Author as PM/Architect:** approve this proposal.
- **Claude (this session):** apply §4 edits to PRD, addendum, spine, UX spines and `epics.md`; re-run the FR coverage check; set `epics.md` to final after the author's confirmation.
- **Next:** `bmad-sprint-planning` to generate `sprint-status.yaml`; then `bmad-build` per story, with `bmad-code-review` only for safety-critical stories (Check Chain, Number Check, Autonomy Gate, persona isolation, redaction, grounding).
- **Success criteria:** every FR-1 to FR-61 is either covered by a story or marked removed with a matrix row; no protected item is reduced; the scenario list in the addendum, the spine (AD-15) and Story 11.2 is identical.
