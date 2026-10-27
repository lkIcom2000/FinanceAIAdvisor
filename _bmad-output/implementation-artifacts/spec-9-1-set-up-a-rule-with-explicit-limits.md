---
title: 'Story 9.1: Set up a rule with explicit limits'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'f177ee7'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-9-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Fine cannot act on its own because no persona can grant a rule (Mandate); the Home tab only shows the empty "Your rules" state (FR-36, FR-7).

**Approach:** A Mandate table in `actions`, written only by `ActionService`; a Core function suggests the limits per template; a three-step setup dialog on Home (and a starter chip in chat) creates the rule after a server-checked summary; active rules show as cards.

## Boundaries & Constraints

**Always:**
- V22: `mandates` (`id`, `persona_id`, `template` `SAFETY_CUSHION|EMERGENCY_FUND_TOP_UP`, `max_per_action`, `max_per_month`, `source_account_id`, `target_account_id`, `floor`, `created_at` DATE = simulated date, `revoked_at` DATE null), partial unique index one active rule per persona and template; `actions.mandate_id` UUID null (FK, set from 9.2). Reset deletes actions then mandates; `PersonaOwnedTablesTest` entry.
- Templates fix the accounts: cushion = overnight → current (`SHORTFALL_TRANSFER`), top-up = current → overnight (`EF_TOP_UP`). `floor` = minimum balance of the source account after a transfer (gate wiring in 9.2).
- Core function `ruleLimits` (pure, evidence stored, origin `OVERVIEW`): cushion max per transfer = forecast lowest balance below zero rounded up to the next 100 EUR, at least 100; max per month = 2 × that; floor = emergency-fund target. Top-up max per transfer = max per month = `emergencyFundTopUp` rounded up to 100, at least 100; floor = 0 EUR. Each figure has a provId ("Why?").
- Validation: amounts > 0, floor ≥ 0, max per month ≥ max per transfer, two decimals at most; else 400 ProblemDetail. Second active rule of a template → 409. A rule counts as active until revoked, whatever the simulated date (one definition for list, count and duplicate check).
- Endpoints (`PERSONA`, persona from the session): `GET /api/rules` (active rules: id, template, summary, bounds, `activeSince`, `actionCount`), `GET /api/rules/setup` (both templates: title, accounts, suggested limits with source + provId, `alreadyActive`), `POST /api/rules/check` (validated summary + will/won't/undo-and-stop texts, nothing stored), `POST /api/rules` (create; one transaction; `ACTION` Audit Record with `event: rule_created`). Use case + controller in `monitoring` (needs the fixture version, AD-12 as 8.4).
- Customer text says "rule", never "Mandate"; text numbers `2,000 EUR`. `MandateLookup.active` counts real rules; `covering` stays empty until 9.2.
- Frontend: "Your rules" lists rule cards (summary title, bounds label/value, "Active since …", "No actions under this rule yet" / count, **Revoke** visible but disabled until 9.4) and **Set up a rule**; M3 stepper dialog (Choose a rule → Set limits → Check and confirm, **Start this rule**); chat starter chip "Set up a rule" opens the dialog.

**Never:** chat creating a rule; deleting a Mandate; executing anything; LLM calls.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Lena cushion | suggested limits, floor 2,000 | card "Keep my current account above 0 EUR, using my overnight deposit, but never let the overnight deposit fall below 2,000 EUR" | N/A |
| Duplicate | cushion already active | template card disabled; POST → 409 | ProblemDetail |
| Bad limits | max per month < per transfer | 400, nothing stored | ProblemDetail |
| No overnight deposit | persona without one | setup lists no template | N/A |

</frozen-after-approval>

## Code Map

- `actions/Actions.java`, `actions/{RuleSetup,RuleLimitsInput,RuleView,…}.java` (new API records), `actions/application/{ActionService,MandateLookup,ActionResetter}.java`, `NoMandates` → `MandateRepository`-backed lookup.
- `actions/domain/{RuleTemplate,RuleTexts}.java` (new), `actions/adapter/MandateRepository.java` (new), `db/migration/V22__mandates.sql`.
- `core/CoreGateway.java`, `core/application/CoreGatewayService.java`, `core/domain/RuleLimits.java` (new).
- `monitoring/application/RulesUseCase.java`, `monitoring/web/RuleController.java` (new).
- Frontend: `home-tab.component.*`, `rule-setup-dialog.component.*` (new), `chat-column.component.*`, generated API client.

## Tasks & Acceptance

**Acceptance Criteria:**
- Given Home, when Lena clicks **Set up a rule** (or the chat chip), then the three-step dialog opens with both templates, Core-suggested limits with source, and a full summary with will/won't/undo/stop.
- Given **Start this rule**, then a Mandate row exists, a rule card shows the summary, bounds, "Active since …" and **Revoke**; customer text never says "Mandate".

## Implementation Notes

- Workflow deviation (lean loop, memory build-loop-economy): implemented inline, no implementation subagent.
- Scope decisions (Commc, 2026-10-27): Core-derived suggestions; one active rule per template, accounts fixed; starter chip in the frontend; `actions.mandate_id` now, count from 9.2.
- `MandateLookup.covering` stays empty until 9.2; `StoredMandates` replaces `NoMandates`.
- The `rule_created` Audit Record uses kind `ACTION` with the Mandate id in the `action_id` slot (no FK; avoids changing the audit kind constraint).
- The dialog uses its own three-step indicator instead of `mat-stepper` (programmatic step changes with server checks between steps); no "Why?" links inside the dialog (no modal stack, UX banned list), the source is shown as text. Revoke is visible but disabled until 9.4.
- Review (one combined sonnet reviewer + Blind Hunter): fixed amount length cap before parsing, explicit per-transfer cap, one definition of "active" (no date filter), dialog a11y (`aria-describedby`, `aria-disabled` focusable templates, no `aria-pressed`), edited limits kept when re-choosing the same template, floor-over-cap test. Not taken: GET `/api/rules/setup` stores evidence (same as the overview GETs); cushion floor = full emergency-fund target even if the overnight deposit is below it (9.2 then caps at L2 with the floor reason, intended); top-up suggestion at least 100 EUR when the fund is full; Home rule list reloads only on page load or rule start; race/no-overnight/reset-with-`mandate_id` integration tests (unique index + FK order cover them; revisit in 9.2 when `mandate_id` is set).
