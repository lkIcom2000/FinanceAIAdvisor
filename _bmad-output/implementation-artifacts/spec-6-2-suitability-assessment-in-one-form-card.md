---
title: 'Story 6.2: Suitability assessment in one form card'
type: 'feature'
created: '2026-10-26'
status: 'done'
baseline_revision: '8ea00db'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<intent-contract>

## Intent

**Problem:** An `ASK_PROFILE` answer only lists the missing fields as text; the persona cannot answer them, so no profile is ever stored and a workspace recommendation can rest on facts stated in one message.

**Approach:** An `ASK_PROFILE` answer carries one assessment card (DialogState `ASSESSMENT`) with every missing field, account-derived values pre-filled from Core results. **Save my answers** writes through `Profiles.confirm` and resumes the original question as the card's reply. Workspace turns follow the strict FR-17 rule.

**Experiment Track counterpart:** none (the experiment has no stored profile); completeness as `experiments/finlab/profile.py:45-66`.

## Boundaries & Constraints

**Always:**
- Strict FR-17 (Commc 2026-10-26): turns without `runId` (persona workspace) are `RECOMMEND_CATEGORY` only if the stored profile alone meets the topic minimum and holds every strict field (needs topics: minimum only); otherwise `ASK_PROFILE` with missing fields = unmet groups + missing strict fields. Acceptance runs (`runId` set) keep the AD-23 rule with limitations.
- Card fields (pure `Assessment.fields(missingFields)`): each strict field as listed, each unmet group by its first field unless a member is already listed; order of the catalog.
- `profile.AssessmentFields`: field catalog (label, input `CHOICE`/`EUR`/`YEARS`/`SLIDER`/`TEXT`, options) and `parse(field, text)`; `Profiles.confirm` rejects unknown fields, wrong types, unknown options, out-of-range numbers and NUL characters (plain problem sentences → 400).
- Pre-fill: new pure Core function `monthlyNetIncome` (mean SALARY credits of the last 12 complete months) and `CoreGateway.profileFigures` (net income + `emergencyFundTarget`): `net_income`, `monthly_expenses`, `emergency_fund` pre-filled with their `provId` when the card lists them (net income only if > 0).
- Save: all card fields answered, keys only from the card; `Profiles.confirm(scope, answers, now)`; card `SAVED`, DialogState `NONE`; the original question's turn runs in the same transaction, reply to the card. A saved card returns its stored reply; a cancelled one → 409.
- A new question cancels an open card (`CANCELLED`, DialogState `NONE`); stage 1 moves into `DialogStates` (retro A5), proposal behaviour unchanged.

**Never:** profile writes outside `Profiles.confirm`; persisting `statedProfile`; a stepper or per-group progress; NLU prompt changes.

</intent-contract>

## Acceptance Criteria

1. Workspace `ASK_PROFILE` turn: agent message with an `OPEN` assessment listing the card fields, pre-filled account fields tagged with `provId`; dialog state `ASSESSMENT` with `{assessmentId, fieldQueue}`.
2. Save: rows in `suitability_profiles` with `confirmed_at`; card `SAVED`; the reply answers the original question (classified on the stored profile) with `replyTo` = card id; dialog state `NONE`; saving again returns the same reply.
3. A new question cancels the card; saving a cancelled card → 409; invalid value / missing field / foreign key / NUL → 400, nothing stored.
4. Strict rule: stored profile meets the minimum but lacks strict fields → `ASK_PROFILE` in the workspace, `RECOMMEND_CATEGORY` with limitations under a `runId`; stated facts never satisfy the strict rule.
5. Unit tests: `AssessmentFields` parsing, `Assessment.fields`, `MonthlyNetIncome`, strict classifier cases, `ProfileFields.NEEDS_TOPICS` matches the yaml's needs topics.
6. Frontend: the card renders chips / EUR field / labelled slider, **Looks right** / **Change** on pre-filled rows, **Save my answers** posts and appends the reply, "Cancelled" after a new question. OpenAPI snapshot and client regenerated.

## Implementation Notes

- Scope decisions (Commc 2026-10-26): strict FR-17 for turns without `runId`; card = strict + topic minimum; new Core function `monthlyNetIncome`; retro A5 limited to extracting stage 1 into `DialogStates` (the orchestrator keeps a one-line `dialogState` stage method, so the stage-method test holds).
- The strict rule needs the strict fields the stored profile lacks even when the topic minimum is unmet: `profile.ProfileCompleteness.strictMissing(stored, topic)` and a default method on the advisory port (the adapter is now an anonymous class). The 6.1 `limitations` semantics are unchanged.
- `Profiles.confirm` validates and normalises every card field via `AssessmentFields.normalize` (closes the 6.1 deferral: value types per field, NUL characters). Amounts are stored with two decimals, numbers as integers, choices as option keys; risk tolerance keys follow the NLU wording (`very low` … `very high`). Every `ProfileFields` field is in the catalog.
- `confirmedAt` is the persona's simulated as-of date at 00:00 UTC (AD-8 forbids wall-clock reads in the application layer; `ArchitectureTest`).
- Pre-filled values are Core figures recorded with origin `turn` when the card opens. A figure key is stored once per persona, so the Overview tests now accept origin `overview` or `turn` for keys an assessment card created first.
- The UI requires **Looks right** or **Change** on every pre-filled row before Save (FR-17 "pre-filled and confirmed"); the slider has no answer until moved.
- The 6.1 `NEEDS_TOPICS` check against the yaml exempts `contract_switch` (all its fields are needs fields, but a pension contract switch is a suitability question); recorded in the test.
- Workspace E2E expectations changed by the strict rule (`ClassificationEndToEndTest`, `CheckChainEndToEndTest`): the stored-minimum-with-limitations case now asks; the AD-23 path with limitations is covered by `TurnOrchestratorTest` under a `runId`, since no HTTP path runs a turn with a `runId` yet.
- Not built (not in the ACs): the "Paused / Continue" state of an interrupted card (EXPERIENCE.md).
- `SaveAssessmentRequest.answers` defaults to empty like `CalculateRequest`: a bean-validation 400 echoed the request path as `instance`, which `PersonaIsolationTest` caught.

- Review (combined sonnet + Blind Hunter, 2026-10-26), fixed: Save follows the `replyTo` chain back to the user's question (a card answering a saved card replied to that card); the card row is locked (`SELECT … FOR UPDATE`) so a double Save returns the first reply instead of 409; an `IllegalArgumentException` from `confirm` maps to 400; a stored card field no longer in the catalog renders as a text field instead of failing `GET /api/chat`; clicking the slider thumb answers with the middle option; **Change** dedupes and clears the error; the closed-card orchestrator test now exercises the failed claim; a test that every field a yaml minimum names is askable.
- Dismissed: catching a pre-fill failure in `AssessmentCards.open` (a `CoreGateway` exception marks the transaction rollback-only, so the turn fails either way; the Liquidity view has the same dependency on the rule parameters and the current account); dividing net income by the months with data (fixtures hold 16 months, and the persona confirms or changes the value); strict rule on non-computable `computation` requests (intended: computable ones are `COMPUTE` before the profile check); legacy profile values (fixtures hold no profile, 6.1). Not added: a concurrent-Save test (the row lock is the guarantee).
- Live Ollama check (local `spring-boot:run`, Marco, 2026-10-26): "Which savings plan fits me for my retirement?" → `ASK_PROFILE` with a ten-field card (topic `retirement_strategy` adds `pension_situation`), income 2,100.00 / expenses 2,000.00 / emergency fund 300.00 pre-filled; Save → `RECOMMEND_CATEGORY` (strict profile complete), released as `ESCALATE` by `block_suitability`, as expected until 6.3 supplies the category selection and Suitability Statement.

