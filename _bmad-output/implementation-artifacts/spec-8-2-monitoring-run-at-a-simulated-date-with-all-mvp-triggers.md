---
title: 'Story 8.2: Monitoring run at a simulated date with all MVP Triggers'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_revision: 'a1e19e742b27fa05ef113edbb721ae67dbf59292'
baseline_commit: 'a1e19e742b27fa05ef113edbb721ae67dbf59292'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `monitoring` is an empty package: nothing detects Triggers at a simulated date, so no Action is ever proposed and no Notification exists for 8.3–8.5 (FR-30, FR-31, FR-16, AD-16).

**Approach:** `MonitoringService.run(ctx, personaId)` (one transaction) gathers Core results into a price-free `MonitoringSnapshot`, calls pure `TriggerDetection.detect(snapshot, asOf, rules)`, skips existing Trigger keys, proposes Actions via `Actions.propose`, writes templated Notifications with `NOTIFICATION` Audit Records and opens `TRIGGER` escalation cases. A research endpoint runs it.

## Boundaries & Constraints

**Always:**
- Trigger types and keys `(personaId, type, subjectId, period)`, unique in DB; an existing key is skipped with all its outputs (this is the one-Action-per-trigger guarantee deferred from 8.1):
  - `FORECAST_SHORTFALL` (current account id, shortfall date): `SHORTFALL_TRANSFER` overnight → current, amount = −lowest expected balance (Core `shortfall` output), plus a second L1 Notification "Paying the premium monthly would avoid this every year." when an `ANNUAL` contract's next due date ≤ shortfall date.
  - `EF_BELOW_TARGET` (overnight id or `none`, `yyyy-MM` of asOf), when Core EF current < target: with a forecast shortfall → L0 informative Notification only; else `EF_TOP_UP` current → overnight with the new Core figure `emergencyFundTopUp` = min(target − current, max(0, net income − monthly expenses)); amount 0 → L0 only.
  - `DUPLICATE_COVERAGE` (cover name, year of asOf) per Core duplicate flag → L1 Notification, no Action.
  - `RULE_CHANGE` (key, version): a Rule Parameter version with `validFrom` in (asOf − 30 days, asOf]. Subsidy keys (`riester_*`) for a persona with net income > 0 → L1 Notification (S12a); ECB keys (`ecb_*`) for a persona with an overnight deposit → one L0 Notification on the overnight deposit rate and overdraft cost, old → new rate, never buy/sell wording (S19). Other keys: no Trigger.
  - `RULE_ANNOUNCEMENT` (announcement id, effective date): an announcement from new `data/rules/announcements.yaml` (empty list in real data) with `announcedOn ≤ asOf ≤ effectiveFrom`, topic subsidy, persona with net income > 0, and no source or no valid Rule Parameter for its keys at `effectiveFrom` → `EscalationCase` origin `TRIGGER` plus a Notification with outcome `ESCALATED`, no Action (S12b).
- Every figure comes from `CoreGateway` with `EvidenceOrigin.MONITORING`; new `CoreGateway.ruleChanges(context, origin, since)` returns changed versions (value and previous value as keyed Core results) and the announcements; monitoring never depends on `knowledge` or `bank`.
- Notifications: templated English text (no LLM, English number format, real minus, no internal vocabulary), columns incl. trigger id, action id (nullable), outcome `INFORMED|RECOMMENDED|ACTION|ESCALATED`, title, body, cited provIds; status for `ACTION` derives from the Action state (8.3).
- Tables `triggers`, `notifications` (V19, id-only refs), `MonitoringResetter` in `RESET_ORDER`, `PersonaOwnedTablesTest` entries.
- `POST /api/research/personas/{personaId}/monitoring/run` (research profile, `RESEARCHER`, unknown persona 404) returns new Triggers, Actions (type, state, level, cappingCriteria) and Notification ids; asOf = persona's simulated date.
- Planted rule changes and announcements exist only in tests (pure detection tests + IT with a test `RuleParamPort`).

**Never:** a scheduler; reading prices, holdings or index data in monitoring (no dependency on `WealthFigures`, `Investments`, `IndexReturns`, `BankSnapshot.Holding`); a second `actions` writer; `Instant.now()` in application/domain; changing released figure keys.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior |
|----------|--------------|---------------------------|
| UJ-1 | Lena, 15 Oct | `FORECAST_SHORTFALL` → `SHORTFALL_TRANSFER` 90.00, L2 `[no_covering_mandate]` + premium-monthly L1; `EF_BELOW_TARGET` → L0 only |
| S4-like | Marco, 15 Oct | `EF_BELOW_TARGET` → `EF_TOP_UP` ≈ surplus, L2 |
| S6 | Jonas, 15 Oct | `DUPLICATE_COVERAGE` LIABILITY → L1, no Action |
| S12a / S19 / S12b | planted versions / announcement | L1 / L0 rate note / `TRIGGER` case, no Action |
| S9 | no rule change, prices ignored | no rule Trigger; architecture test forbids price types in monitoring |
| Re-run | same persona and date | 0 new Triggers, Actions, Notifications, audit records |

</frozen-after-approval>

## Code Map

- `monitoring/package-info.java` -- add `actions :: types`; ModulithTest line 44 expectations.
- `core/CoreGateway.java`, `core/application/CoreGatewayService.java` -- reuse `liquidity`, `insurance`, `profileFigures`; add `emergencyFundTopUp`, `ruleChanges` (results in `core.domain`, `@NamedInterface("results")`); `core/domain/RuleParameterValue.java` for keyed rule values.
- `knowledge/RuleParamPort.java`, `adapter/YamlRuleParameters.java` -- add versions-valid-from range, previous version, announcements loader.
- `escalation/EscalationCases.java`, `EscalationService`, `adapter/EscalationCaseRepository.java` -- add `openForTrigger(scope, TriggerSnapshot(triggerId, summary, reasons))`; V10 allows `TRIGGER` with null `turn_id`, `question` NOT NULL holds the summary.
- `actions/Actions.java` -- `propose(context, scope, fixtureVersion, ProposedAction)`, `MANDATORY` tx; `personas.PersonaDirectory.fixtureVersion(scope)`; `shared.RequestContextFactory.forPersona(scope, runId)`.
- `audit/AuditWriter`, `AuditKind.NOTIFICATION`; `research/application/ResetPersonaUseCase.RESET_ORDER`; `research/web/PersonaResearchController.java` -- endpoint pattern.
- `test/.../actions/application/ActionServiceTest.java:91` -- Lena 15 Oct setup pattern; `test/resources/figure-keys/` -- pins.

## Tasks & Acceptance

**Execution:**
- [x] `core/...` -- `EmergencyFundTopUp` function + `RuleChangeFigures`; gateway methods; FigureKeyFreezeTest pins (`-Dfine.figureKeys.update=true`).
- [x] `knowledge/...`, `data/rules/announcements.yaml` -- versions/announcements API.
- [x] `escalation/...` -- `openForTrigger`.
- [x] `db/migration/V19__monitoring.sql` -- `triggers` (unique key), `notifications`.
- [x] `monitoring/domain/` -- `MonitoringSnapshot`, `DetectedTrigger`, `TriggerDetection`, `NotificationTemplates`.
- [x] `monitoring/Monitoring.java` (API), `application/MonitoringService.java`, `MonitoringResetter`, `adapter/` repositories.
- [x] `research/web/MonitoringResearchController.java` -- endpoint.
- [x] Tests: `TriggerDetectionTest` (matrix incl. S12a/S12b/S19/S9), `MonitoringServiceTest` (IT: Lena, Marco, Jonas, re-run, planted rules, reset), `MonitoringResearchControllerTest`, ArchitectureTest rule, ModulithTest, PersonaOwnedTablesTest.

**Acceptance Criteria:**
- Given Lena at 15 Oct, when the endpoint runs, then the response lists the shortfall transfer at L2 with `no_covering_mandate`, and each Notification has one `NOTIFICATION` Audit Record.
- Given a failure while writing the audit, when monitoring runs, then no Trigger, Action or Notification remains.
- Given a reset, then the persona's triggers and notifications are gone.

## Implementation Notes

- Implemented inline instead of a dispatch subagent (user's lean-loop rule: subagents only for review).
- AD-21/AD-11: `Monitoring.run(context, scope)` is `MANDATORY`; the transaction starts in new `research.application.RunMonitoringUseCase` (persona check -> 404, then `PersonaScopeResolver.forResearcher(personaId)`, a researcher-only factory, because only the resolver may build a `PersonaScope`). Story 8.5's prepare use case can call the same API.
- ECB Trigger key: one `RULE_CHANGE` per ECB decision, `(ecb_key_rates, validFrom)`, instead of one per `(key, version)`, so a decision that moves several key rates gives exactly one L0 Notification (S19). Subsidy changes keep `(key, version)`.
- No overnight deposit: `FORECAST_SHORTFALL` and `EF_BELOW_TARGET` only inform (L0); there is no account to move money from or to.
- Premium hint: one L1 Notification for the earliest-due annual contract with a due date <= shortfall date.
- New `CoreGateway.accountIds` (ids only, no evidence): monitoring may not read `bank`, but Actions need account ids. New output-name constants on `CoreGateway` (`SHORTFALL`, `LOWEST_BALANCE`, `EMERGENCY_FUND_TARGET`, `PREMIUM`, `EMERGENCY_FUND_TOP_UP`, `RULE_VALUE`, `RULE_PREVIOUS_VALUE`) keep `core.domain` functions behind the gateway.
- `NOTIFICATION` Audit Records carry the Action id in the body, not in `audit_records.action_id` (that column stays for `ACTION` records). TRIGGER cases store `TRIGGER:<triggerId>` as first block reason.
- New `data/rules/announcements.yaml` changes the runtime config fingerprint (AD-14 hashes `data/rules/`), as any rule-data change does. Released figure keys are unchanged: FigureKeyFreezeTest only gained the new `emergencyFundTopUp` and `monthlyNetIncome` lines.
- Verification: affected classes green; full `./mvnw verify -Dfine.it.required=true` green (761 tests, 0 skipped). Frontend untouched.

## Spec Change Log

## Review Triage Log

Layers: one combined sonnet reviewer (edge-case + verification-gap lenses) and a separate Blind Hunter (persona scope / security), per the lean-loop rule.

| # | Source | Finding | Verdict | Evidence | Route |
|---|--------|---------|---------|----------|-------|
| 1 | blind | `forResearcher` public, callable anywhere | low | Requires an authenticated RESEARCHER, who may act on any persona (AD-11); a persona session gets 403. | reject (low, fix adds a rule) |
| 2 | blind | ACTION text says "prepared" though the gate may escalate | false | ESCALATED only without emergency-fund Rule Parameters; `liquidity()` reads the same keys first and fails the run before `propose`. | reject |
| 3 | blind | no FKs on `notifications.trigger_id/action_id` | false | Id-only references are the house rule (V18 comment); every write takes the run's own scope. | reject |
| 4 | blind | researcher not audited, `Principal` null NPE | false | Researcher actions are logged, not audited (review decision 2026-10-21); the research filter chain guarantees a principal. | reject |
| 5 | blind | null run id | false | `RequestContextFactory` documents `null` outside acceptance runs; same as other research use cases. | reject |
| 6 | blind | any module may call `Monitoring.run` | false | ModulithTest pins the AD-12 graph: only `research` depends on `monitoring`. | reject |
| 7 | blind | no persona-isolation test | low | All repository reads/writes take the `PersonaScope`; unique key includes `persona_id`. | reject (low) |
| 8 | blind | dedupe never retries a failed Action | false | Intended: one Action per Trigger key (frozen intent). | reject |
| 9 | combined | EF key per month: first run during a shortfall blocks the top-up that month | low | Real, but the key `(overnight id, yyyy-MM)` is frozen intent. | defer |
| 10 | combined | 500 without current account / rule gap | false | No persona lacks a current account; real rules are valid at all simulated dates; loud failure on an unreachable state. | reject |
| 11 | combined | audit/vocabulary checks only in Lena test | medium | Gap filed pre-verified. | patch: planted-rules test asserts one audit per notification and the vocabulary denylist |
| 12 | combined | failing-audit test ignores evidence items | low | Gap filed pre-verified; Action and its audit are written before the failing call and are already covered. | patch: `evidence_items` in `counts()` |
| 13 | combined | new Core functions untested directly | low | Gap filed pre-verified. | patch: `EmergencyFundTopUpTest` (3 cases); ruleChange keys stay IT-covered |
| 14 | combined | premium due on the shortfall date: "before" is wrong | low | Filter is `<=` per spec, text said "before". | patch: wording "no later than the day", test added |
| 15 | combined | price rule does not block `CoreGateway.wealth()`; no wall-clock rule | low | Calls to `wealth` were not caught; wall clock is covered by `NO_WALL_CLOCK`. | patch: rule forbids `wealth`/`investmentTotals` calls |
| 16 | combined | announcement boundary asOf == effectiveFrom untested | low | Gap. | patch: boundary test |

## Verification

**Commands:**
- `./mvnw -q test -Dtest='TriggerDetectionTest,MonitoringServiceTest,MonitoringResearchControllerTest,ModulithTest,ArchitectureTest,PersonaOwnedTablesTest,FigureKeyFreezeTest'` -- green
- `./mvnw -q verify -Dfine.it.required=true` -- green before commit
