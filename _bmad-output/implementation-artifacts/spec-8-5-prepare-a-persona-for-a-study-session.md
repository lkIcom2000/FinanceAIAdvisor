---
title: 'Story 8.5: Prepare a persona for a study session'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '1b8d00b'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Before a study session the researcher must reset a persona, set its date and run monitoring by hand; nothing guarantees every participant starts in the same state (FR-31).

**Approach:** Study presets are JSON files in `APP_HOME/data/study-presets/`; one research use case resets, sets the date, runs setup steps and monitoring in one transaction and reports what the participant will see.

## Boundaries & Constraints

**Always:**
- Preset file `<id>.json`: `id` (= file name), `title`, `personaId`, `simulatedDate`, `setup` (list). Loaded and validated at startup (research profile): unknown fields, id ≠ file name, unknown persona, date before the persona's fixture date, or any setup step fail startup. No setup step type exists yet (Mandates: Epic 9, Document facts: Epic 10); they add their types.
- `POST /api/research/study/prepare/{presetId}` (research profile, `RESEARCHER`): unknown preset → 404. One `@Transactional` use case: `PersonaFixtureLoader.load`, `PersonaClock.setSimulatedDate`, setup steps, `Monitoring.run`. Response: `presetId`, `personaId`, `simulatedDate`, `notifications` (`id`, `status`), `activeRules`, `summary` ("2 notifications (1 Done for you, 1 Recommendation), 1 rule active"; status labels as on Home, counts in `NotificationStatus` order, singular/plural).
- `Monitoring.Run` carries each Notification's status (`RunNotification(id, status)`, `notificationIds()` kept); `Actions.activeRules(scope)` over `MandateLookup` (0 until Epic 9).
- Presets: `uj4-duplicate-coverage-jonas` ("UJ-4 duplicate coverage (Jonas)") and `uj2-advice-marco` ("UJ-2 advice (Marco)"), both at 2026-10-15, no setup.

**Never:** a scheduler; Audit Records for the researcher's prepare (logged only, like reset); LLM calls.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Jonas preset | any prior state | reset, 2026-10-15, one Recommendation (duplicate liability) | N/A |
| Prepared twice | same preset | Triggers, Actions, Notifications, Core outputs identical (ids/timestamps aside) | N/A |
| Unknown preset | `nope` | 404, nothing changed | ProblemDetail |
| Bad preset file | unknown setup step | startup fails naming the file | IllegalStateException |

</frozen-after-approval>

## Code Map

- `research/application/StudyPresets.java` (new) -- loads and validates the preset files.
- `research/application/PrepareStudyUseCase.java` (new), `research/web/StudyResearchController.java` (new).
- `monitoring/Monitoring.java`, `monitoring/application/MonitoringService.java` -- `RunNotification` with status.
- `actions/Actions.java`, `actions/application/{ActionService,MandateLookup,NoMandates}.java` -- `activeRules`.
- `data/study-presets/*.json`.

## Tasks & Acceptance

**Acceptance Criteria:**
- Given a preset, when the researcher prepares it, then reset, date, setup and monitoring run in one use case and the response summarises what the participant will see.
- Given the same preset prepared twice, then Triggers, Actions, Notifications and Core outputs are identical (test).
- The presets "UJ-4 duplicate coverage (Jonas)" and "UJ-2 advice (Marco)" exist.

## Implementation Notes

- Workflow deviation (lean loop, memory build-loop-economy): implemented inline, no implementation subagent.
- Scope decisions (Commc, 2026-10-27): `setup` field with validation only; both presets at the fixture date; rule count over the Mandate seam.
- AC1 "setup steps run": no step runs yet; the execution point is a comment in `PrepareStudyUseCase.prepare` between date and monitoring. Epic 9/10 add the step types in `StudyPresets` and their dispatch there.
- Summary counts follow `NotificationStatus` (chip) order, so the AC example reads "1 Recommendation, 1 Done for you".
- Jonas at 2026-10-15 also gets the emergency-fund top-up: "2 notifications (1 Recommendation, 1 Needs your OK), 0 rules active".
- `Monitoring.Run` now carries `RunNotification(id, status)`; `notificationIds()` stays as a derived accessor.
- No persona lock in `prepare`: one researcher prepares sessions.

## Review Triage Log

Layers: one combined sonnet reviewer (edge-case, verification-gap, spec lenses). No Blind Hunter: the story writes persona data only through the existing reset, date and monitoring paths.

| # | Source | Finding | Verdict | Route / evidence |
|---|--------|---------|---------|------------------|
| 1 | combined | Twice-prepared test would pass without a reset (`insertIfAbsent`) | medium | patch: dirty date + stray Trigger between runs, compare summaries |
| 2 | combined | Jonas test does not prove stale rows are removed | low | patch: same `dirty` before prepare, exact Trigger list |
| 3 | combined | No setup-step execution seam | medium | defer to Epic 9/10; noted above (Commc scope decision) |
| 4 | combined | Chip order differs from AC example | low | reject: example only; chip order noted |
| 5 | combined | Response exposes `Monitoring.RunNotification` | low | reject: same as `MonitoringResearchController` |
| 6 | combined | No concurrency guard | low | reject: single researcher; noted |
| 7 | combined | Empty preset directory accepted; `../data` relative path | low | reject: same as other file-backed tests |
