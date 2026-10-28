---
title: 'Story 13.4: Fine answers questions about my situation with my data'
type: 'feature'
created: '2026-10-28'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_revision: 'bd7089ad1b149ee64581d54bb82d29cba04c26dd'
baseline_commit: 'bd7089ad1b149ee64581d54bb82d29cba04c26dd'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-13-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Live check 2026-10-27: p-lena asked "how much emergency fund should I aim for?" and got a generic `INFORM` answer that asked for her income and expenses, which Fine holds (FR-62). German first-person questions are even grounded to `generic`, because `NluExtraction.PERSON` knows no German pronouns.

**Approach:** Grounding makes a first-person explanation question personal (`self`), in English and German. For an `INFORM` turn with subject `self` and a topic that has a rule, `ToolPlanStage` adds that topic's Core figures (one rule per topic); they reach the prompt as `TOOL RESULTS`, the Number Check ledger and the Audit Record through the existing `Tools.results` path. The `INFORM` class instruction forbids asking for financial data the system holds.

## Boundaries & Constraints

**Always:**
- Topic rules (in `ToolPlan`, advisory domain): `liquidity_management` → emergency fund (`ProfileFigures.emergencyFund`: current reserve, monthly expenses, target range); `retirement_strategy` → goal projection (`WealthFigures.projection` and `totals`, skipped when null); `protection_needs`, `protection_duplicate` → coverage summary (`InsuranceFigures.duplicates`, `missing`).
- Grounding: subject `generic` + request type `general_explanation` + first-person wording (`i, i'm, i've, i'd, my, mine, we, our, ours, ich, mein*, wir, unser*`; not object forms like "tell me"/"erklär mir") + no `CLIENT` wording → `self`, with a grounding note. `PERSON` gains the German pronouns so German personal questions are no longer downgraded to `generic`.
- The class stays `INFORM` (classification unchanged: `general_explanation` is `INFORM` whatever the subject).
- A topic rule whose Core call throws `NoValidRuleParameterException` leaves the turn without figures (as today), no escalation.
- Existing figure functions and keys only; `FigureKeyFreezeTest` unchanged.
- Prompt change in `config/prompts/class-INFORM.txt` is a documented deviation from E4; its hash changes in the Audit Record.

**Decisions (Commc, 2026-10-28):** no figure chips on personal `INFORM` answers; full spec kept (~1900 tokens, single goal).

**Never:** figure chips on `INFORM` answers; changing the no-valid-source route (retrieval is off in the reference configuration); upgrading the subject for other request types (would reroute to `ASK_PROFILE`/`RECOMMEND_CATEGORY`); changes to `Classifier`, `ExposureFilter`, `CheckChain`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Personal, rule | Lena, "How much emergency fund should I aim for?", NLU `generic`/`general_explanation`/`liquidity_management` | subject `self`; `INFORM`; emergency-fund figures in `TOOL RESULTS`, ledger and Audit `coreFigures` | N/A |
| German | "Wie viel Notgroschen sollte ich haben?" | subject `self`, same figures | N/A |
| Generic | "What is an emergency fund?" / "Explain to me what an ETF is" | subject `generic`, no figures, turn as today | N/A |
| Personal, no rule | "What does diversification mean for my portfolio?" (`diversification`) | `self`, no figures | N/A |
| Rule parameter missing | emergency-fund parameter invalid at as-of | no figures, turn as today | caught in `ToolPlanStage` |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/de/financetool/advisory/domain/NluExtraction.java` -- `PERSON` L106, `CLIENT` L111, `ground` subject block L238-242: add the German pronouns, the `FIRST_PERSON` pattern and the upgrade after the downgrade.
- `backend/src/main/java/de/financetool/advisory/domain/ToolPlan.java` -- add the topic → figures rule (enum or map), pure domain.
- `backend/src/main/java/de/financetool/advisory/application/ToolRegistry.java` -- add `personalFigures(context, rule)` → `List<CoreResult>` via `CoreGateway.profileFigures/wealth/insurance` with `EvidenceOrigin.TURN`.
- `backend/src/main/java/de/financetool/advisory/application/ToolPlanStage.java` -- before the `!= COMPUTE` pass-through: `INFORM` + subject `self` + rule → `withTools(new Tools(null, null, null, false, results, false)).ran(TOOL_PLAN)`.
- `backend/src/main/java/de/financetool/advisory/domain/TurnState.java` -- `Tools` record (results feed `numbers()`, ledger, audit `coreFigures`); reuse, add a factory only if needed.
- `config/prompts/class-INFORM.txt` -- one line; asserted verbatim in `ClassificationEndToEndTest` L155.
- `backend/src/test/resources/nlu/e1-llama3-pilot-expected.jsonl` -- items 11, 12 are first-person `general_explanation` with subject `generic`; their grounded subject and notes change (class unchanged). Mark as app deviation in the header comment.
- Tests: `NluExtractionTest`, `TurnOrchestratorTest` (tool plan), an e2e test like `ClassificationEndToEndTest` (StubLlm `nluAnswer`/`answer`, `turn()` reads answer + audit body, `lastSystemPart()`).

## Tasks & Acceptance

**Execution:**
- [x] `NluExtraction.java` -- German pronouns in `PERSON`; `FIRST_PERSON` upgrade in `ground`.
- [x] `ToolPlan.java`, `ToolRegistry.java`, `ToolPlanStage.java` -- topic rule, Core calls, stage branch; update Javadoc.
- [x] `config/prompts/class-INFORM.txt` -- append: when TOOL RESULTS hold the user's figures, explain with them; never ask for financial data the system holds (accounts, income, expenses, balances, profile fields).
- [x] `NluExtractionTest.java` -- recorded extractions (model said `generic`) for the English and German emergency-fund question → `self`; object-form and third-person cases stay `generic`.
- [x] `TurnOrchestratorTest.java` -- tool plan: personal + rule → results; generic, no rule, non-`INFORM`, rule-parameter error → as today.
- [x] `advisory/web/PersonalAnswerEndToEndTest.java` -- new, exposure `none`: Lena's emergency-fund question; the stubbed phrasing names her figures (values read from the audit `coreFigures`/prompt `TOOL RESULTS` of the turn).
- [x] `e1-llama3-pilot-expected.jsonl`, `ClassificationEndToEndTest` -- update expectations for the deviations.

**Acceptance Criteria:**
- Given Lena's emergency-fund question at exposure `none`, when it is answered, then the phrasing prompt holds her emergency-fund figures and the instruction never to ask for held data, the released `INFORM` answer naming them passes the Number Check, and the Audit Record lists their provIds.
- Given the full build, when `./mvnw -q verify -Dfine.it.required=true` runs, then all tests pass.

## Implementation Notes

- Upgrade to `self` runs after the `OWN_COVER` rule, so it never changes the request type (no reroute to `RECOMMEND_CATEGORY`); a model-`generic` "Which cover do I need?" now stays `INFORM` with the coverage summary.
- `FIRST_PERSON` excludes object forms (`me`, `mir`, `mich`, `uns`); `PERSON` gains `ich, mich, mir, mein*, wir, uns, unser*`.
- Pilot items 11/12 do not change: item 11 ("offers me ...") has only an object form, item 12 is already `personal_recommendation` before the subject block. `e1-llama3-pilot-expected.jsonl` stays as generated; no deviation marker needed.
- Rule figures: `ToolPlan.PersonalFigures` (topic map) + `ToolPlan.personalFigures(class, extraction)`; `ToolRegistry.personalFigures` calls `profileFigures().emergencyFund()`, `wealth()` (totals + projection, empty without a projection), `insurance()` (duplicates + missing). A `NoValidRuleParameterException` passes the stage through.
- No chips: `ReleaseStage` lists figures only for a spec, recommendation or needs statement, so personal `INFORM` results stay chip-free without a change there.
- Prompt deviation from E4: `class-INFORM.txt` appends the held-data sentences; `experiments/finlab/prompts.py` is unchanged (single-turn experiment baseline).

- Review deviation (lean loop): one combined sonnet reviewer (edge-case + verification-gap lenses) plus a Blind Hunter instead of three same-model layers; patches added `ToolRegistryTest`, `ToolPlanTest` cases and declined-form German possessives.
- Verified 2026-10-28: full `verify -Dfine.it.required=true` green with JDK 25, 0 failures, 0 skipped.

## Spec Change Log

## Review Triage Log

| # | Source | Location | Finding | Verdict | Evidence | Route |
|---|--------|----------|---------|---------|----------|-------|
| 1 | blind, edge | NluExtraction.java:88,94 | `mein\w*`/`unser\w*` match "Meinung", "meinetwegen"; a generic German explanation is upgraded to `self` and gets the user's figures | low | Real: "Was ist die Meinung zu einem Notgroschen?" matches `FIRST_PERSON`. Direct regex correction (declined forms only) | patch |
| 2 | verification-gap, blind | ToolRegistry.java:68-78, ToolPlan.java:142 | GOAL_PROJECTION and COVERAGE branches, null-projection path and topic mapping untested | medium | Pre-verified: only TurnOrchestratorTest (registry mocked) and the emergency-fund e2e reference them | patch |
| 3 | blind, edge | ToolRegistry.java:68-80 | Null figures in `List.of` → NPE | false | `CoreGatewayService.profileFigures`/`insurance` always record `emergencyFund`, `duplicates`, `missing`; `totals` is null only together with `projection`, which is checked | reject |
| 4 | edge | ToolPlanStage.java:73-80 | Empty results mark TOOL_PLAN as ran | low | The Core call did run (no holdings); empty `toolResults` adds no prompt header. Rare, fix adds a branch | reject |
| 5 | edge | ToolPlanStage.java:73-80 | `wealth()` records totals before a parameter throws, turn then has no evidence | low | Pre-existing `noRollbackFor` design shared by all `wealth()` callers; evidence is append-only | reject |
| 6 | blind, edge | NluExtraction.java:114 | German third-person/client wording ("mein Kunde") is not excluded | low | Real but rare for a B2C persona; German `sie` collides with formal `Sie`, so the fix is more than a direct correction | reject |
| 7 | blind | NluExtraction.java:114 | Quoted pronoun ("what 'my portfolio' means") upgrades to `self` | low | Rare; fix needs an intent heuristic | reject |
| 8 | edge | NluExtraction.java:87-88 | German object forms in `PERSON` stop the generic downgrade | false | Required by the spec ("`PERSON` gains the German pronouns") | reject |
| 9 | blind | ToolPlan.java:142 | Only four topics have a rule; no audit reason for others | false | The spec defines exactly these rules; other topics pass through as today | reject |
| 10 | blind | ToolPlanStage.java:73 | Positional `Tools(...)` constructor; swallowed exception leaves no trace | false | Same constructor idiom as the other branches; the spec requires "no figures, no escalation" as today | reject |
| 11 | blind | class-INFORM.txt:1 | Sentence applies to every INFORM turn | false | The spec asks for the INFORM class instruction to forbid asking for held data; it is conditional in wording ("When TOOL RESULTS hold …") | reject |
| 12 | verification-gap, edge | e1-llama3-pilot-expected.jsonl, prompts.py | Pilot file and finlab prompt unchanged though task ticked | false | Implementation Notes record why items 11/12 do not change and that prompts.py stays the experiment baseline; the pilot parity test passes | reject |

## Verification

**Commands:**
- `cd backend && JAVA_HOME=C:\Users\commc\.jdks\temurin-25 ./mvnw -q test -Dtest='NluExtractionTest,ClassifierTest,TurnOrchestratorTest'` -- expected: green
- `cd backend && JAVA_HOME=C:\Users\commc\.jdks\temurin-25 ./mvnw -q verify -Dfine.it.required=true` -- expected: 0 failures, 0 skipped
