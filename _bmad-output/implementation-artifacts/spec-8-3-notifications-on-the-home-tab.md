---
title: 'Story 8.3: Notifications on the Home tab'
type: 'feature'
created: '2026-10-27'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: 'b3cf6798c2ea11761b385b08b8698922b6e37745'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-8-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Story 8.2 stores Notifications, but the Home tab still shows the empty state: Lena never sees what Fine noticed (FR-7, FR-32, UX-DR19/36/39/40).

**Approach:** A persona endpoint lists the persona's Notifications newest first with a status derived from the linked Action state (AD-10), figure chips resolved from the evidence store, read state and a prepared chat question; a root `NotificationsService` polls it (10 s visible, 60 s hidden) and feeds the Home list and the tab badge.

## Boundaries & Constraints

**Always:**
- `GET /api/notifications` (persona session, `PersonaScope`): items `id, status, title, body, date (as_of), figures: Figure[], read, question?`, newest first (`created_at DESC, id`). `POST /api/notifications/{id}/read` → 204, idempotent; foreign or unknown id → 404 (id check before any body).
- Status: outcome `INFORMED` → `FOR_YOUR_INFORMATION`, `RECOMMENDED` → `RECOMMENDATION`, `ESCALATED` → `WITH_AN_ADVISOR`; `ACTION` → from the Action state: `AWAITING_CONFIRMATION` → `NEEDS_YOUR_OK`, `EXECUTED` → `DONE_FOR_YOU`, `INFORMED`/`RECOMMENDED`/`ESCALATED` as above. `UNDONE` is in the enum and chip map but no state yields it before Epic 9. Chip words: For your information, Recommendation, Needs your OK, Done for you, Undone, With an advisor (icon + word).
- Figures come from the persona's own evidence items by cited provId (label, value, unit), in cited order; nothing is recomputed. A missing item is left out.
- New `actions` API read `states(scope, actionIds)`; new `audit` API read of figures by provIds; `monitoring` gains a `web` package. No new module dependencies.
- V20: `notifications.read_at TIMESTAMPTZ NULL`, `notifications.question TEXT NULL`. Reset already deletes the rows.
- Home: list replaces the empty state when items exist; collapsed = chip, title, date, figure chips with "Why?"; unread bold; expanding or 5 s ≥ 50 % in view marks read (one POST); tab label shows the unread count. Expanded = body (plain explanation incl. outcome) and the figure chips as triggering data; `RECOMMENDATION` items with a question show **Ask Fine about this**.
- Polling: one in-flight request at a time, 10 s while `document.visibilityState === 'visible'`, 60 s while hidden; items keyed by id so existing DOM is kept; new items insert without changing the scroll position. Failed poll keeps the last list silently.

- Decisions (Commc, 2026-10-27): read state is server-side (`read_at`, survives reload, reset clears it); the **Ask Fine about this** question is a fixed per-template question written by monitoring for each L1 text (duplicate coverage, subsidy change, premium monthly). Full spec kept in one story (cheaper than a split).

**Never:** LLM text in Notifications; internal vocabulary in customer text; client-side computing of figures or status; a scheduler; changing 8.2 trigger detection or released figure keys; confirm/undo controls (8.4, Epic 9).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| UJ-1 Lena | monitoring run at 15 Oct | shortfall item `NEEDS_YOUR_OK` with figures; premium-monthly `RECOMMENDATION` with question; EF `FOR_YOUR_INFORMATION`; badge 3 | N/A |
| No notifications | fresh persona | existing empty state text | N/A |
| Action executed | Action state → `EXECUTED` | next poll shows "Done for you" | N/A |
| Foreign id | read POST with another persona's id | 404, nothing changed | ProblemDetail |
| Poll fails | 5xx | list unchanged, no error banner | retry next interval |

</frozen-after-approval>

## Code Map

- `backend/.../monitoring/adapter/NotificationRepository.java` -- add `read_at`, `question` to `Row`/`findAll` (newest-first variant), `markRead(scope, id)` returning rows updated.
- `backend/.../monitoring/domain/NotificationTemplates.java`, `DetectedTrigger` note, `MonitoringService:108` -- carry `question` for the 3 L1 texts (duplicate coverage, subsidy change, premium monthly).
- `backend/.../monitoring/Monitoring.java` -- add read API (`notifications(scope)`, `markRead(scope, id)`); new `monitoring/web/NotificationController.java` (pattern: `core/web/OverviewController`, `FigureView`, `@Parameter(hidden = true) PersonaScope`).
- `backend/.../actions/Actions.java`, `adapter/ActionRepository.java` -- `Map<UUID, ActionState> states(scope, ids)`.
- `backend/.../audit/` -- module API read `figures(scope, provIds)` → `List<SourcedNumber>` via `AuditService` (`provenance(scope, provId)` at :55 shows the persona-scoped read).
- `backend/.../db/migration/V20__notification_read_state.sql`.
- Tests: `MonitoringServiceTest` (Lena setup), new `NotificationControllerTest`, `PersonaIsolationTest` (probes every endpoint), `OpenApiSnapshotTest -Dopenapi.update=true`, ModulithTest unchanged.
- `frontend/src/app/api/` -- regenerate (`npm run api:generate`).
- `frontend/src/app/shared/notifications.service.ts` (new, root) -- poll, `items`, `unread` signals, `markRead`.
- `frontend/src/app/shared/chat-prompt.service.ts` (new, root) -- `ask(text)` signal; `chat-column.component.ts:217` `send` consumes it (if busy: put text in composer via `composer().restore`).
- `frontend/src/app/pages/workspace/home-tab.component.*` -- list in "Notifications" section (empty state kept); chip markup from `chat-column.component.html:264` `.figure-chip` + `app-why-link`.
- `frontend/src/app/pages/workspace/workspace.page.html` -- unread badge on the Home label (pattern: `tab-dot`).

## Tasks & Acceptance

**Execution:**
- [x] `V20__notification_read_state.sql` -- columns.
- [x] `actions`, `audit` -- read APIs + repository queries.
- [x] `monitoring` -- templates' questions, repository, API, `NotificationController`.
- [x] Backend tests -- controller IT (status mapping per state incl. EXECUTED via direct row update, figures, newest first, read idempotent, 404 foreign), isolation, snapshot.
- [x] Frontend -- api regen, `NotificationsService`, `ChatPromptService`, Home list, badge, chat hook; specs with fake timers (polling cadence by visibility, 5 s read, expand read, Ask Fine sends, scroll unchanged on insert).

**Acceptance Criteria:**
- Given Lena after a monitoring run at 15 Oct, when the Home tab renders, then three items show newest first with chips "Needs your OK", "Recommendation", "For your information", figure chips with "Why?", and the Home label shows 3 unread.
- Given an unread item, when it is expanded or stays 5 s in view, then it is no longer bold and the badge drops by one, also after reload.
- Given a Recommendation, when the user clicks **Ask Fine about this**, then its question is sent as a chat message.

## Implementation Notes

- Workflow deviation (lean loop, memory build-loop-economy): implemented inline, no implementation subagent.
- The read side is `monitoring.application.ShowNotificationsUseCase` (list, markRead) used by `monitoring.web.NotificationController`, not new methods on the `Monitoring` module API: no other module reads Notifications. Status mapping is the pure `monitoring.domain.NotificationStatus.of(outcome, actionState)`.
- The audit read is a new module API interface `audit.EvidenceReader.figures(scope, provIds)` (implemented by `AuditService`, one `IN` query); `Actions.states(scope, ids)` likewise one query. Both run in the caller's transaction.
- Order: the spec's `created_at DESC, id` is followed. Within one run this lists Lena's items as For your information, Recommendation, Needs your OK (the AC names the chips, not their order).
- `POST /api/notifications/{id}/read`: a malformed id answers 404 like an unknown one; read time is the DB clock (`COALESCE(read_at, clock_timestamp())`, the first read time stays).
- Questions (fixed per template): premium monthly "Should I pay my premium for {type} monthly instead of yearly?", duplicate coverage "Do I need all of my contracts that cover {cover}?", subsidy change "What does the change to the {name} mean for me?". The question also goes into the `NOTIFICATION` Audit Record payload.
- Frontend: the list is `NotificationListComponent` inside the Home "Notifications" section; the empty state shows while the list is empty (also before the first poll; no skeleton). The workspace page starts and stops the root poll. Read-on-view is `shared/ReadOnViewDirective` (IntersectionObserver, threshold 0.5, 5 s); a read marked locally survives an older poll answer and is reverted if the POST fails. "Ask Fine about this" goes through `ChatPromptService`; the chat column sends it once loaded, or puts it into the composer while a turn is pending.

## Spec Change Log

## Review Triage Log

Layers: one combined sonnet reviewer (edge-case + verification-gap lenses) and a Blind Hunter (persona data), per the lean loop; no separate edge-case and verification-gap layers.

| # | Source | Finding | Verdict | Route / evidence |
|---|--------|---------|---------|------------------|
| 1 | both | Root `NotificationsService.stop()` keeps the list; after logout/login the previous persona's items and badge show until the first poll, and a late answer can repopulate it | medium | patch: `stop()` unsubscribes the in-flight request and clears list, local reads and `loaded`; spec test added |
| 2 | combined | Figures test checks only keys, not cited order, stored values, missing or foreign keys | medium | patch: `figuresAreTheStoredEvidenceInCitedOrderWithoutMissingOrForeignKeys` |
| 3 | blind | One evidence query per Notification on every 10 s poll (N+1) | low | patch: one `EvidenceReader.figures` call per list, mapped back in cited order |
| 4 | blind | `aria-controls` points at a details element that exists only when expanded | low | patch: set only while open |
| 5 | combined | Scroll test cannot show scroll behaviour in jsdom | low | patch: renamed to what it checks (DOM nodes kept); anchoring is CSS `overflow-anchor` |
| 6 | both | ACTION Notification without Action state makes `NotificationStatus.of` throw, 500 for the list | low | rejected: Actions and Notifications are written in one transaction and deleted only together by reset; only a reset committing between the two reads of one poll hits it, and the next poll recovers |
| 7 | combined | Read timer keeps running when the browser tab goes hidden | low | rejected: needs extra visibility state in the directive; an item already half in view for most of 5 s is a minor misread |
| 8 | both | `composer.restore` while busy overwrites a draft / `composer()` undefined loses the question | false | the composer is disabled and emptied while a turn is pending, and it is always rendered once the chat is ready |
| 9 | both | Pending prompt stays when the chat load failed | low | rejected: chat-failed state already shows an error; adds a branch |
| 10 | blind | No limit/paging on the list; missing index | low | rejected: MVP personas have a handful of Notifications; index `notifications_persona (persona_id, created_at)` exists (V19) |
| 11 | blind | `UNDONE` never produced | false | spec keeps it in enum and chip map until Epic 9 |
| 12 | blind | Duplicate-coverage / subsidy questions hidden if INFORMED | false | both templates are emitted with outcome RECOMMENDED (`TriggerDetection`) |
| 13 | blind | Failed first poll shows the empty state; no backoff | low | rejected: spec says a failed poll keeps the last list silently |
| 14 | blind | No immediate refresh on hidden-to-visible | low | rejected: spec cadence is 10 s visible |
| 15 | blind | Tall expanded items cannot reach 50 % in view; no live region | low | rejected: items are a few lines; not required |
| 16 | blind | Missing tests for unauthenticated access and foreign evidence | false | `PersonaIsolationTest` sweeps the new endpoints, security rejects anonymous `/api/**`; foreign evidence now covered by #2 |
| 17 | blind | `question: null` in audit payload; old rows have no question | low | rejected: payload is a record of what was written |
| 18 | combined | `ORDER BY` built by string concatenation | low | rejected: private method, two constant orders |

## Verification

**Commands:**
- `./mvnw -q test -Dtest='NotificationControllerTest,MonitoringServiceTest,PersonaIsolationTest,OpenApiSnapshotTest,ModulithTest'` -- green
- `npm test -- --watch=false && npm run build` (frontend) -- green
- `./mvnw -q verify -Dfine.it.required=true` -- green before commit
