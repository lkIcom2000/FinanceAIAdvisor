---
title: 'Story 3.1: Chat column with atomic turn release'
type: 'feature'
created: '2026-10-22'
status: 'done'
baseline_revision: '2f480bb7b6b9f3f2e02e9d0cafcbc11b366f9ad9'
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-3-context.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** A persona cannot talk to Fine: the chat column is an empty `<aside>`, and no turn pipeline, chat tables or `AuditWriter` caller exist (FR-28, AR6, Epic 1 A7).

**Approach:** Add the `advisory` chat backbone: tables, an orchestrator that runs the 11 stages in fixed code order (only the phrasing draft and audit + release are real; the rest are explicit pass-throughs), a persona-scoped chat API that saves the question first and then releases one complete, audited answer in one transaction, and the chat column UI (greeting, composer, waiting, failed and response cards).

## Boundaries & Constraints

**Always:**
- Stage order is fixed in code: dialog state (consulted first), NLU, classify, tool plan, KB retrieve, exposure filter, LLM draft, shadow checks, Rule Engine, Judge, block routing, audit + release. Pass-through stages are named in the Audit Record (`passThroughStages`). Pass-through classify yields `INFORM`; pass-through routing gives outcome = class.
- Two steps, two transactions: `POST /api/chat/messages` saves the user message; `POST /api/chat/messages/{id}/answer` runs the turn. The agent message, any evidence items and the `RESPONSE` Audit Record are written in the answer use case's one `REQUIRED` transaction; an exception anywhere (incl. the audit write) rolls all of them back, and the user message stays saved.
- Answering an already answered message returns the stored answer (no second turn, no second Audit Record; unique `reply_to`). A message id of another persona answers 404 exactly like an unknown id (AD-11).
- The persona comes only from `PersonaScope`; every repository method is persona-scoped. `RequestContext` from `RequestContextFactory`; fixture version from `PersonaDirectory`.
- LLM call only through `LlmPort` (role `PHRASING`, decoding from the context): system message = the role prompt file `config/prompts/role-<rolePrompt>.txt` (sha256 of its LF bytes recorded as prompt hash); the user text only inside a delimited data slot.
- Audit body: class, outcome, evidence `provId`s (empty), check results (empty), model tag + digest, prompt hash, `truncated`, first draft, final text, `regenerated: false`, compliance level, `passThroughStages`. Judge fields absent (it did not run).
- `advisory` implements `PersonaResettable` (deletes the persona's dialog states, messages, conversations); audit is never reset.
- Customer text never contains internal vocabulary or stack traces. UI copy exactly as in the ACs and `epic-3-context.md` (UX section).

**Never:** streaming or partial text; a turn-status field; chat history or persona data in the prompt (Story 3.2); real classification, checks, routing, Judge, KB retrieval or evidence (later stories, Epic 4, 3.9); the check badge on the response card before Story 3.4 has checks; `advisory` depending on `actions`; touching `config/agent/reference.yaml` or prompts (AD-25).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy path | Lena posts "What is an ETF?", answers it | 201 user message; 200 agent message `outcome: INFORM`, text = draft; 1 `RESPONSE` record with her `turnId` | — |
| Re-answer | answer called twice for one message | same agent message both times; still 1 record | — |
| Foreign id | Jonas answers Lena's message id | 404 `NOT_FOUND` | nothing written |
| LLM down | `LlmPort` throws | 503 problem, no agent message, no record; user message listed as unanswered | UI shows the failed card with Try again |
| Audit fails | `AuditWriter` throws | 503, no agent message, no evidence, no record | rolled back |
| Invalid text | blank or > 1,000 chars | 400 `VALIDATION_FAILED` | nothing saved |

</intent-contract>

## Code Map

- `backend/src/main/java/de/financetool/advisory/package-info.java` -- allowed deps already include `core :: results`, `llm`, `audit`, `personas`.
- `backend/src/main/java/de/financetool/audit/AuditWriter.java`, `AuditEntry.java` -- module API; `RESPONSE` needs `turnId` (= agent message id) and `fixtureVersion`. `AuditPayloadMapper` serialises the body (records sort alphabetically).
- `backend/src/main/java/de/financetool/llm/LlmPort.java`, `LlmMessage`, `LlmResult` (`modelTag`, `digest`, `truncated`), `LlmRole.PHRASING`.
- `backend/src/main/java/de/financetool/shared/RequestContextFactory.java`, `PersonaScope`, `shared/PersonaResettable.java`; `research/application/ResetPersonaUseCase.RESET_ORDER` already lists `advisory`.
- `backend/src/main/java/de/financetool/personas/PersonaDirectory.java` -- add `String fixtureVersion(PersonaScope)` (implementation in `personas.application`); `PersonaSummary.displayName` gives the greeting name.
- `backend/src/main/java/de/financetool/shared/AppHome.java` -- `config()` for the prompt files; `shared/config/ConfigBinding.java:23` resolves the same role prompt path.
- `backend/src/main/java/de/financetool/core/application/ShowHomeOverviewUseCase.java`, `core/web/OverviewController.java` -- use-case and controller patterns (`@Transactional` use case, `PersonaScope` parameter, `@Schema` records).
- `backend/src/main/java/de/financetool/shared/ProblemCode.java`, `shared/web/ProblemDetailsAdvice.java` -- problem responses; add a code for a failed turn.
- `backend/src/main/resources/db/migration/V5__bank.sql` -- migration style; default privileges already grant `fine_app` CRUD on new tables.
- `backend/src/test/java/de/financetool/PostgresIntegrationTest.java` -- shared Testcontainers context, no Ollama; `core/web/HomeOverviewEndToEndTest.java` -- end-to-end pattern; `research/PersonaOwnedTablesTest.java` -- new persona tables must belong to a registered owner.
- `backend/src/main/java/de/financetool/research/web/AuditExportController.java` -- `GET /api/research/audit/export` (research profile).
- `frontend/src/app/pages/workspace/workspace.page.html:29` -- empty chat `<aside>`; `workspace.page.scss` sets its width. Generated client via `npm run api:generate`.

## Tasks & Acceptance

**Execution:**
- `backend/src/main/resources/db/migration/V6__advisory.sql` -- `conversations` (one per persona), `messages` (`author` CHECK USER/AGENT/ADVISOR, `text`, `outcome`, `reply_to` UNIQUE, `created_at` default now()), `dialog_states` (`state` default `NONE`, `payload` JSONB).
- `backend/src/main/java/de/financetool/advisory/domain/*` -- `ResponseOutcome` (six classes + `ESCALATE`), turn state records, the stage list; pure.
- `backend/src/main/java/de/financetool/advisory/application/*` -- `PostMessageUseCase`, `AnswerMessageUseCase`, `ShowChatUseCase`, `TurnOrchestrator` (`MANDATORY`, stages as methods in fixed order), `AdvisoryResetter`.
- `backend/src/main/java/de/financetool/advisory/adapter/*` -- persona-scoped repository; `PromptFiles` (loads `config/prompts/*.txt` once, text + hash).
- `backend/src/main/java/de/financetool/advisory/web/ChatController.java` -- `GET /api/chat`, `POST /api/chat/messages`, `POST /api/chat/messages/{id}/answer`; OpenAPI snapshot updated.
- `backend/src/test/java/de/financetool/testing/StubLlmConfiguration.java` -- `@Primary` programmable `LlmPort` stub imported by `PostgresIntegrationTest` (one shared context).
- Backend tests -- orchestrator stage order + pass-throughs (unit); `ChatEndToEndTest` for every matrix row; Epic 1 A7: research-profile test that the export holds exactly the turn's `RESPONSE` line with its `turnId`, outcome and final text; reset removes chat rows.
- `frontend/src/app/pages/workspace/chat-column.component.*` (+ composer) -- greeting card with three suggestion chips, `role="log" aria-live="polite"` list, waiting card, failed card, response card (outcome label row + body), "New message" pill; component specs with fake timers.

**Acceptance Criteria:**
- Given a turn in progress, when the waiting card shows, then its four steps advance on a timer without ticks, after 20 s it adds "This is taking longer than usual. Fine runs on a local model and checks every answer.", and it never times out; the composer is disabled with placeholder "Fine is checking the answer…".
- Given the composer, when the user types, then Enter sends, Shift+Enter adds a newline, input stops at 1,000 characters and a counter shows from 800.
- Given a failed turn, when the answer call fails, then the waiting card becomes "Something went wrong on our side, so I couldn't finish the answer. Your question is saved." with **Try again**, which repeats only the answer call.
- Given an empty conversation, when the chat opens, then "Hi Lena, I'm Fine. I can explain money topics, look at your situation and calculate what-ifs. Every number I show you comes with a 'Why?'." shows with three suggestion chips that send their text; after logout and login the history is still there.
- Given the user is scrolled up, when a message arrives, then the scroll position stays and a "New message" pill appears.

## Design Notes

- `turnId` = the agent message's id, generated before the audit write, so the record and the message share it.
- The answer use case loads the user message persona-scoped first (404 path) and returns the existing reply if `reply_to` already has one; a concurrent duplicate hits the unique constraint and rolls back.
- Suggestion chips (until Epic 8 tailors them to notifications): "How does an emergency fund work?", "What is an ETF savings plan?", "How is my liquidity looking?".
- Outcome labels and icons: the six rows of `EXPERIENCE.md` (e.g. `INFORM` → "Explanation"); 3.1 only produces `INFORM`.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify -Dfine.it.required=true` -- expected: BUILD SUCCESS, 0 skipped
- `cd frontend && npm run api:check && npx ng test --watch=false && npm run build` -- expected: green

**Manual checks (if no CLI):**
- Local live check (`spring-boot:run` against compose `db`/`ollama`, real model): Lena asks one question, gets one complete answer; the research export shows the record.

## Implementation Notes

- Deviations from the bmad-build-auto workflow (user cost rules, memory `build-loop-economy`): no per-story review; Story 3.1 is reviewed jointly with 3.3 in block A, with all four layers because the chat API exposes persona-scoped message ids (persona isolation). The working tree held the user's unrelated experiment files (`experiments/`, `planning-artifacts/experiments/`) and the handoff note; they are left out of the story commit, so the "clean tree" checks of steps 1 and 4 were judged on the story's files only.
- The implementation subagent added copy the ACs do not give: composer placeholder "Ask Fine a question", "Your message couldn't be sent. Please try again." (text returned to the composer) and "We couldn't load the chat. Please reload the page.".
- A malformed message id answers 404 like an unknown id (the default 400 echoed the path value, which `PersonaIsolationTest` forbids).
- The forced audit failure is produced by a stub model digest containing `\u0000`, which PostgreSQL rejects in JSONB; only the Audit Record carries the digest, so the agent message insert succeeds first and is rolled back (one shared Spring context, no spy bean).
- Outcome icons are inline SVG; only `menu_book` (INFORM) and `chat` have paths so far, later stories add theirs.

## Auto Run Result

- **Summary:** `advisory` chat backbone: V6 tables (`conversations`, `messages`, `dialog_states`), `TurnOrchestrator` running dialog state plus the 11 stages in fixed order (LLM draft and audit + release real, the rest named pass-throughs), two-step chat API (save question, then answer in one transaction with the `RESPONSE` record), `AdvisoryResetter`, `PersonaDirectory.fixtureVersion`, `ProblemCode.TURN_FAILED`; chat column and composer in the workspace.
- **Files:** `advisory/{domain,application,adapter,web}/*` (new module code); `V6__advisory.sql`; `personas/PersonaDirectory.java` + service; `shared/ProblemCode.java`; tests `TurnOrchestratorTest`, `ChatEndToEndTest` (matrix rows), `ChatResearchEndToEndTest` (Epic 1 A7 export, reset), `StubLlmConfiguration` (shared `@Primary` stub), `PersonaOwnedTablesTest`; frontend `chat-column.component.*`, `chat-composer.component.*`, workspace wiring, regenerated API client and OpenAPI snapshot.
- **Review:** deferred to block A (3.1 + 3.3, four layers). No findings yet; follow-up recommendation `false` (no review pass ran).
- **Verification:** `./mvnw -B verify -Dfine.it.required=true` BUILD SUCCESS, 265 tests, 0 failures, 0 skipped (chat tests 7 + 2 ran). Frontend `npm run api:check` up to date, `ng test` 68 passed, `npm run build` complete. Matrix audit: all six rows covered by `ChatEndToEndTest`, all ran. Live check (local `spring-boot:run`, research profile, compose `db`/`ollama` via `docker-compose.local.yml`, CPU, `llama3.1:8b-instruct-q4_K_M`): Lena asked "What is an ETF savings plan?", one complete `INFORM` answer after 75 s; the export shows its `RESPONSE` record with the turn id, model tag and the ten pass-through stages. The README's local-run note (Epic 2 A4) worked as written.
- **Residual risks:** the LLM call holds the answer transaction open (~75 s on CPU) by design (atomic release); until Story 3.2–3.4 the released text is the unchecked draft (pass-through checks), acceptable only because nothing is released outside development.
