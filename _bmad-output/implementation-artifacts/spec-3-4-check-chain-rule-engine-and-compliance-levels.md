---
title: 'Story 3.4: Check Chain, Rule Engine and compliance levels'
type: 'feature'
created: '2026-10-23'
status: 'in-progress'
baseline_revision: '7ae916e26973f887faadd02006867315bce89965'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
  - '{project-root}/_bmad-output/implementation-artifacts/spec-3-3-deterministic-classification-of-every-message.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Stages 7, 8 and 10 are pass-throughs: no draft is checked, the compliance level changes nothing, and the outcome is the class (FR-25, FR-29, FR-52, AD-3, AD-6).

**Approach:** Port the Experiment Track's Check Chain and routing (`experiments/finlab/checks.py` `run_checks`, `pipeline.py` `FLAGS`, stages 7–10 of `run_turn`, `_route`, `prompts.py` `TEMPLATES`, at commit `b72a70e`) into `advisory.domain`: a closed `CheckId` enum, `CheckResult`, a pure Check Chain, `ComponentFlags` as the only interpreter of the compliance level, and `BlockRouting.route` as the only FR-52 table. The response card gets the check footer that opens "Why?" on its Checks.

## Boundaries & Constraints

**Always:**
- `CheckId` is closed with exactly: `SCOPE`, `PROFILE_COMPLETE`, `SUITABILITY`, `COST_TRANSPARENCY`, `NO_PRODUCT_NAMING`, `NO_GUARANTEE_OR_TIMING`, `CONFLICT_OF_INTEREST`, `IDD_NEEDS`, `DOCUMENTATION`, `NUMBER_MATCH`, `SOURCE_VALIDITY`. `CheckResult(checkId, applicable, passed, reasonCode, details)`. Each check is a verbatim port of its `run_checks` block (regexes, word lists, reason codes). `NUMBER_MATCH` returns `applicable = false` until Story 3.6. The experiment's `FALSE_PREMISE` and `ROLE_INTEGRITY` are not ported (closed enum, AD-6).
- `SOURCE_VALIDITY` as in the experiment: it applies to `INFORM` turns when retrieval is on; then no KB passage fails it with `no_valid_source` (template + chip via routing), missing or invalid citations trigger the regeneration. With retrieval off (reference configuration after E1) it is not applicable and a checked `INFORM` draft is released. This replaces Story 3.3's direct no-source rule (decision Commc 2026-10-23).
- `ComponentFlags` per level, the only place the level is read: `A` — stage 8 shadow, 9 off, 10 off (outcome = class, draft released); `A+B` — enforcing, off, on; `A+B+C` — enforcing, on, on. The Judge stays a pass-through (Story 3.9 skipped): under `A+B+C` it is recorded as `judgeNotImplemented`, never as a verdict.
- Stage 7 runs the chain on the first draft in every configuration (shadow results). Stage 8 enforcing: on failed `NUMBER_MATCH`, or `SOURCE_VALIDITY` with `missing_citation`/`invalid_citation`, exactly one regeneration with the experiment's targeted fix text (previous messages + the draft as assistant + the fix as user, same decoding), then the chain runs again on the regenerated text.
- `BlockRouting.route(failed, classification, ruleParameterError)`, exactly `_route` plus the invalid-Rule-Parameter row: invalid Rule Parameter → `ESCALATE`; `REFUSE_OUT_OF_SCOPE` with `SCOPE` failed → `REFUSE_OUT_OF_SCOPE` + its template; `PROFILE_COMPLETE` failed → `ASK_PROFILE` + its template (default missing text); only `SOURCE_VALIDITY` `no_valid_source` → `INFORM` + the no-valid-source template and chip; `NUMBER_MATCH` failed → `ESCALATE`; any other failure → `ESCALATE`; no failure → released, outcome = class. Templates verbatim from `TEMPLATES`. The rule-parameter input stays false until Story 3.7.
- A routed (template) release keeps no final checks (the template is pre-approved text, `pipeline.py` comment); the draft's results stay as shadow results.
- Audit Record: shadow results (first draft), final results, `route` (e.g. `released`, `block_scope`, `no_valid_source`), `regenerated`, the regenerated text, the fired check ids, `shadowMode: true` under `A`, the flags applied.
- Agent message carries its final applicable check results (id, passed, plain description) for the card; persisted with the message; API, snapshot and client updated.
- Card footer "Checked · N checks passed" (N = applicable final checks that passed) only when final checks exist; it opens the "Why?" dialog on its Checks section: each check with tick or cross, its plain description, the check id as a caption (same section layout as Epic 2's dialog).

**Never:** the Number Check itself (3.6), Judge calls (3.9), escalation cases (3.5), refusal/trap card bodies and "No personal figures used" (3.8), ESCALATE card copy (3.5 — the experiment's `TEMPLATES["ESCALATE"]` text is released until then); changing `experiments/`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Level A | out-of-scope draft recommending a certificate | draft released, outcome = class, `shadowMode: true`, shadow results show `SCOPE` failed | — |
| Out of scope, A+B | refusal draft without an alternative | `REFUSE_OUT_OF_SCOPE` template, `route: block_scope`, no final checks | — |
| Ask profile, A+B | draft says "Yes, invest" | `ASK_PROFILE` template | — |
| INFORM, no source | retrieval on, no passage | no-valid-source template + chip, `route: no_valid_source` | — |
| INFORM, retrieval off | compliant draft | draft released, `SOURCE_VALIDITY` not applicable | — |
| Product named | draft names "iShares" in a recommendation | `ESCALATE`, fired `NO_PRODUCT_NAMING` | — |
| Regeneration | draft cites `[S3]` with 1 passage | one regeneration; passes → released with `regenerated: true` | second failure → routed |
| All pass | compliant draft | released, footer N = passed applicable checks | — |

</intent-contract>

## Code Map

- `experiments/finlab/checks.py` (all regexes, word lists, `run_checks`, `failed`), `pipeline.py:34-43` (`FLAGS`), `pipeline.py:255-310` (stages 7–10), `pipeline.py:336-353` (`_route`), `prompts.py:60-71` (`TEMPLATES`) -- reference (read-only).
- `backend/src/main/java/de/financetool/advisory/application/TurnOrchestrator.java` -- `shadowChecks`, `ruleEngine`, `judge`, `blockRouting` stages; 3.3's no-source rule moves into routing.
- `backend/src/main/java/de/financetool/advisory/domain/{TurnState,ResponseOutcome,MessageClass,Classification,NoValidSource}.java`, `application/ResponseAuditBody.java`.
- `backend/src/main/java/de/financetool/shared/config/ComplianceLevel.java` -- the level enum in `AgentConfiguration`.
- `backend/src/main/java/de/financetool/core/domain/CheckResult.java` -- the Core's own check type (Data Confidence); the advisory chain keeps its own record.
- `frontend/src/app/shared/why-dialog.component.*` -- Checks section layout to reuse; `frontend/src/app/pages/workspace/chat-column.component.*` -- card footer.

## Tasks & Acceptance

**Execution:**
- `advisory/domain` -- `CheckId`, `ChainResult`/`CheckResult`, `CheckChain` (port), `ComponentFlags`, `BlockRouting`, `ResponseTemplates`; pure.
- `TurnOrchestrator`, `ResponseAuditBody`, chat persistence (migration for the message's checks), `ChatController` -- wire stages 7–10, regeneration, audit, API.
- Frontend -- footer and checks dialog.
- Tests -- per-check table tests (each experiment check case, ≥ 2 per implemented check, pass and fail), `ComponentFlags` table test per level, `BlockRouting` table test per row (incl. fired check recorded), orchestrator tests for shadow vs enforcing and regeneration, e2e for every matrix row at the configured level (flags switchable in tests), frontend specs.

**Acceptance Criteria:**
- Given level `A`, when any turn runs, then the Audit Record says the Rule Engine ran in Shadow Mode and holds the shadow results.
- Given a released turn with final checks, when the user opens the footer, then the dialog lists every applicable check with tick or cross, plain description and id caption.

## Spec Change Log

- 2026-10-23, user decision (not a review finding): E1 selected the arm without retrieval, whose `INFORM` answers come from model knowledge; Commc chose the experiment's `SOURCE_VALIDITY` semantics (applies only with retrieval on) and an FR-13 amendment. Amended: the `SOURCE_VALIDITY` rule and two matrix rows inside the intent contract, the Design Note removed. Avoids: every `INFORM` turn releasing the no-valid-source template while retrieval is off. KEEP: the template + chip path with retrieval on.

## Review Triage Log

## Design Notes

- `FALSE_PREMISE` and `ROLE_INTEGRITY` exist only in the experiment; adding them needs a spine change of AD-6 (open question for the user, Story 3.8).

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify -Dfine.it.required=true` -- expected: BUILD SUCCESS, 0 skipped
- `cd frontend && npm run api:check && npx ng test --watch=false && npm run build` -- expected: green

**Manual checks (if no CLI):**
- Local live check (real model, `A+B+C`): Tim's knock-out question → refusal template or released refusal with checks; Lena "What is an ETF?" → template with `route: no_valid_source`; the export shows shadow and final results.
