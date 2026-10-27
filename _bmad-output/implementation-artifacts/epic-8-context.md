# Epic 8 Context: Fine Watches My Finances — Monitoring, Notifications and the Autonomy Gate

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Monitoring detects Triggers at a simulated date, proposes Actions through the deterministic Autonomy Gate, and shows Notifications on the Home tab. Actions that need the user's OK are confirmed exactly, with the downgrade reason in plain words. Lena hears about a problem (e.g. a forecast shortfall) before it happens, and no Action can ever run above its permitted autonomy level.

## Stories

- Story 8.1: Autonomy Gate and Action proposals
- Story 8.2: Monitoring run at a simulated date with all MVP Triggers
- Story 8.3: Notifications on the Home tab
- Story 8.4: Confirm an Action exactly, and see why it needs my OK
- Story 8.5: Prepare a persona for a study session

## Requirements & Constraints

- The gate gives each Action its maximum level (L0-L3); the lowest cap wins. Caps: no covering Mandate or amount above bounds, or not reversible -> L2; `investment` above 5 % of liquid assets -> L2; `investment` without complete confirmed suitability, `insurance`, `legal`, or Data Confidence `low` -> L1. With all criteria met, L3 only for `EF_TOP_UP` and `SHORTFALL_TRANSFER`, otherwise L2. Thresholds live in `config/autonomy.yaml`, never in code. Identical inputs give identical decisions.
- Triggers: forecast shortfall, emergency fund below target, Duplicate Coverage, Rule Parameter/KB change (incl. ECB key rates). Contract due dates are only forecast inputs. Market price moves (e.g. -20 % index drop) never produce a Trigger and no detector reads prices.
- Outcomes: shortfall -> Action plus L1 Recommendation; Duplicate Coverage and new subsidy with valid source -> L1 Recommendation; announced subsidy without valid Rule Parameter/source -> `EscalationCase` origin `TRIGGER`; ECB rate change -> informative L0 Notification only, never buy/sell advice. No Mandates exist before Epic 9, so the shortfall transfer is capped at L2 ("no covering rule").
- Notifications are templated (no LLM), written with a `NOTIFICATION` Audit Record; Actions write an `ACTION` Audit Record with gate decision and capping criteria. `insurance` and `legal` Actions are never executed.
- No scheduler: monitoring runs only via research endpoints (`RESEARCHER`, research profile), study preparation and acceptance suites. Runs must be idempotent and reproducible.
- Customer text never uses internal vocabulary (L2, Mandate, Check Chain); say "rule" and "checks"; English number format with real minus sign.

## Technical Decisions

- `ActionService` is the only writer of `actions`/`mandates` and the only builder of gate context, from one `BankReadPort.snapshot(personaId, asOf)`, `core.domain` measures (`DataConfidence.of`, `Liquidity.liquidAssets`, emergency-fund floor after the Action), `ProfileCompleteness.suitabilityComplete` and the Mandate lookup. `AutonomyGate.decide(action, context, thresholds)` is a pure domain function returning `GateDecision(level, cappingCriteria)`.
- Entry points: `propose` (monitoring only; `ProposedAction` carries type, class, amount, accounts, `triggerId`), `confirm(actionId)` (re-runs gate and checks on the same row, then executes via `BankPort`; no duplicate row), `undo` (Epic 9). States: `INFORMED`, `RECOMMENDED`, `AWAITING_CONFIRMATION`, `EXECUTED`, `ESCALATED`; Notification status is derived from the linked Action state.
- `monitoring` module: tables `triggers` and `notifications` (registered with reset); `MonitoringService.run(ctx, personaId)` is one use case in one transaction calling pure `TriggerDetection.detect(snapshot, asOf, rules)`. Trigger key `(personaId, triggerType, subjectId, period)`; existing keys skipped. Every cited figure comes from `CoreGateway`.
- Time comes from the persona's simulated clock; one transaction holds audit, notification, action state change and bank mutation (fail audit -> nothing released).
- Endpoints: `POST /api/research/personas/{personaId}/monitoring/run`, `POST /api/research/study/prepare/{presetId}`. Study presets are files (persona, simulated date, optional setup steps); prepare = reset, set date, setup, monitor in one use case; same preset twice gives identical Triggers, Actions, Notifications, Core outputs. Presets: "UJ-4 duplicate coverage (Jonas)", "UJ-2 advice (Marco)".
- Table test of the gate is the only evidence for `investment` and irreversibility rows (no such Action in MVP).

## UX & Interaction Patterns

- Home tab: Notifications newest first; status chip (icon + word: For your information, Recommendation, Needs your OK, Done for you, Undone, With an advisor), title, date, figure chips with "Why?"; unread bold until expanded or 5 s in view; unread badge on the tab. Expanded view explains the event in plain language with triggering data and outcome; Recommendations offer **Ask Fine about this** (sends a prepared chat question).
- Polling every 10 s (60 s hidden) inserts new items without scroll jumps; skeleton cards on cold load; empty states for no notifications/rules.
- L2 confirmation dialog: Action as a sentence, from/to accounts with balances before/after (from Core), plain reason with "Why?", buttons **Confirm transfer of X EUR** / **Not now**; success closes dialog, flips the Notification to "Done for you", snackbar "Done. X EUR moved."; failed re-check shows the plain reason and Close only, Notification stays "Needs your OK". Downgrade reasons use customer templates with the user's own numbers per gate criterion (legal: Fine never sends letters).

## Cross-Story Dependencies

- 8.1 -> 8.2 (monitoring proposes via `ActionService.propose`) -> 8.3 (Notifications UI) and 8.4 (confirm uses gate re-check and Notification state) -> 8.5 (presets run monitoring).
- Builds on earlier epics: Core/`CoreGateway`, Bank Adapter ports, profile completeness, Rule Parameters/KB, `EscalationCase`, Audit Records, simulated clock, Home tab shell.
- Epic 9 adds Mandates, L3 execution and Undo; Epic 8 must leave the Mandate lookup as an empty-capable seam. Later stories add UJ-1 and UJ-7 presets.
