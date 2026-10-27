---
date: 2026-10-27
author: Commc (with Dev agent)
trigger: Live check 2026-10-27 (persona p-lena), Epic 8/9 block
mode: batch
scope: moderate
status: approved (Commc, 2026-10-27); edits applied
---

# Sprint Change Proposal — Personal answers and follow-up questions in chat

## 1. Issue Summary

**Finding 1: personal questions get generic answers.** In the live check on 2026-10-27, persona p-lena asked "how much emergency fund should I aim for?". The NLU returned `general_explanation` with subject `generic`, so `Classifier.java:94` classified the turn as `INFORM`. `INFORM` runs no Deterministic Core tools (`TurnOrchestrator.toolPlan`, line ~270 passes every class except `COMPUTE`/`RECOMMEND_CATEGORY` through). Under exposure `none` the model therefore saw nothing about Lena. It asked for her income and expenses, which Fine holds and shows in the Liquidity tab.

**Finding 2: follow-up questions are meaningless.** Follow-ups such as "Why?" or "And with 200 €?" get answers without context. At exposure `none`, `ExposureFilter.java:77` replaces the history with an empty list. Neither the NLU nor the phrasing role knows Fine's previous answer. `DialogState` (AD-24) only covers the profile form (`ASSESSMENT`) and the what-if proposal (`PARAMETER_PROPOSAL`).

**Gap.** The PRD vision ("real advice") and the JTBD ("in my situation", §2.1) require personal answers and a normal conversation. No FR states this. FR-13 describes `INFORM` as purely general, FR-43 says `none` passes only Core results, and the architecture (AD-14, exposure filter) claims history is covered but the code drops it at `none`.

**Side finding (13.x).** A duplicate `POST /answer` for the same question runs the model twice in parallel. The second insert fails on `messages_reply_to_key` and returns `503 TURN_FAILED`. `AnswerMessageUseCase.java:54` calls `findReply` without a lock.

## 2. Impact Analysis

| Area | Impact |
|---|---|
| Epic 3 (done) | Behaviour of 3.2 (exposure filter), 3.3 (classification), 3.6 (Number Check) and 3.7 (tool plan) is extended, not reverted. No rollback. |
| Epic 13 (backlog) | Four new stories: C2 split, personal answers, one-turn context, duplicate answer. |
| Epic 12 (UAT) | Both behaviours are visible to every study participant. Stories 13.4 and 13.5 must land before 12.2 (dry run). |
| Epic 11 | Q&A Acceptance Suite (11.1) needs rows for personal `INFORM` questions and a follow-up pair. Noted for 11.1, no story change now. |
| PRD | New FR-62, FR-63; amendments to FR-12, FR-13, FR-43, FR-44; note in §8.1 (E4). |
| Architecture | AD-14 exposure-filter bullet; AD-24 note (follow-up context is not a `DialogState`). |
| UX | None. No new screens or components. |
| Experiment Track | E4 ran with single-turn prompts. One turn of context at `none` is a documented deviation (like 13.1); prompt hashes change. |
| Retro item C2 | Becomes a prerequisite story (13.3), because 13.4 and 13.5 change the tool plan and payload stages of `TurnOrchestrator` (638 lines). |

## 3. Recommended Approach

**Direct adjustment** within Epic 13. No rollback, no MVP cut.

- Order: 13.3 (C2 split) → 13.4 (personal answers) → 13.5 (one-turn context). 13.6 (duplicate answer) is independent and small.
- Effort: 13.3 medium (refactor, tests exist), 13.4 medium, 13.5 medium, 13.6 small. About one session each.
- Risk: 13.5 touches the privacy boundary at `none`. Mitigated by construction (only depersonalised question plus LLM text phrased from Core results) and by the template/advisor exclusions. 13.5 gets the separate Blind Hunter review (persona data).
- Timeline: four sessions before Epic 12. Epic 10 is not blocked.

## 4. Detailed Change Proposals

### 4.1 PRD (`prds/prd-FinanceTool-2026-10-20/prd.md`)

**New FR-62 (§4.3, after FR-16)**

```
#### FR-62: Answers about the user's own situation
When a question concerns the user's own situation ("I", "my", "should I"), the system answers with the user's data,
whatever the Expected Behavior class.
- The orchestrator adds the Deterministic Core figures for the recognised topic to the turn (one rule per topic,
  FR-44). The Number Check applies to them as to any Core output (FR-12).
- The system never asks the user for financial data that it already holds (accounts, income, expenses, balances,
  profile fields). It asks only for data it does not hold.
- A personal question stays `INFORM` when it asks for an explanation; it does not become a Recommendation.
```

**New FR-63 (§4.3, after FR-62)**

```
#### FR-63: Follow-up questions
The system understands a follow-up question on its last released answer ("Why?", "And with 200 €?").
- The NLU and the phrasing role receive exactly one previous released turn as context, at every Data Exposure
  Level (FR-43).
- At `none`, that turn consists of the depersonalised question and Fine's LLM-phrased answer. Template texts
  (Suitability Statement, demands-and-needs statement, refusals) and advisor replies are not passed as text; at
  most their Deterministic Core figures are.
- Numbers from the previous turn count as sourced only if they came from the Deterministic Core (FR-12).
```

**FR-12, sourced numbers bullet**

OLD: `Sourced numbers are Deterministic Core outputs, Rule Parameter values, numbers in the cited Knowledge Base passages, and numbers in the user's own message. Any other number is a mismatch.`

NEW: `Sourced numbers are Deterministic Core outputs (of this turn, and of the previous turn passed as context under FR-63), Rule Parameter values, numbers in the cited Knowledge Base passages, and numbers in the user's own message. Any other number is a mismatch.`

**FR-13, append**

NEW sentence: `A general explanation asked about the user's own situation also uses the user's Core figures (FR-62).`

**FR-43, append**

NEW sentence: `Under `none`, the one previous turn passed for follow-ups (FR-63) is the depersonalised question and an answer phrased from Core results, so it carries no persona attributes. E4 evaluated single-turn prompts; this context is a documented deviation from the Experiment Track (§8.1).`

**FR-44, first paragraph, append**

NEW sentence: `For questions about the user's own situation, the plan also includes the Core figures of the recognised topic (FR-62).`

**§8.1, after the E4 row's run plan paragraph**

NEW: `E4 compared exposure levels on single-turn prompts. The application passes one previous turn at every level (FR-63); this is recorded as a deviation, and prompt hashes in the Audit Record show it.`

### 4.2 Epics (`epics.md`)

**Epic 13 summary (Epic List)**

OLD: `**FRs covered:** none new (refines FR-9, FR-29)`

NEW: `**FRs covered:** FR-62, FR-63 (added 2026-10-27, sprint-change-proposal-2026-10-27.md); refines FR-9, FR-12, FR-13, FR-29, FR-43, FR-44`

**Requirements Inventory and FR Coverage Map**: add FR-62 and FR-63 lines and rows `| FR-62 | Epic 13 | 13.4 | Personal answers with Core figures |`, `| FR-63 | Epic 13 | 13.5 | One-turn follow-up context |`.

**New Story 13.3: Split TurnOrchestrator into stage classes (technical debt)**

```
As a developer,
I want each turn stage in its own class,
So that the tool plan and the prompt payload can change without touching a 638-line orchestrator.

**Gap:** retro 6/7 item C2 (open). Stories 13.4 and 13.5 change the tool plan and the payload stages.

**Acceptance Criteria:**

**Given** `TurnOrchestrator`
**When** the split is done
**Then** at least the tool plan (stage 3) and block routing (stage 10) are separate classes, the orchestrator only
sequences stages, and no behaviour changes: all existing advisory tests pass unchanged except for constructor wiring
```

**New Story 13.4: Fine answers questions about my situation with my data**

```
As a persona,
I want Fine to use what it knows about me when I ask about my own situation,
So that I get an answer for me, not a generic one or a question about data Fine already has.

**Gap:** live check 2026-10-27 (p-lena, "how much emergency fund should I aim for?" → generic `INFORM`, asked for
income and expenses). FR-62. **Needs 13.3.**

**Acceptance Criteria:**

**Given** a question in the first person ("I", "my", "should I", German "ich", "mein")
**When** the NLU extracts it
**Then** the subject is personal, not `generic`, with recorded-fixture tests for both languages

**Given** an `INFORM` turn with a personal subject and a recognised topic
**When** the tool plan runs
**Then** it adds the Core figures for that topic by one rule per topic (e.g. emergency fund → monthly expenses,
current reserve, target range; retirement → goal projection; protection → coverage summary), the figures are
recorded in the Audit Record, and the Number Check sources them

**Given** an `INFORM` prompt
**When** it is built
**Then** it forbids asking for financial data that the system holds, and a test with p-lena's emergency-fund
question at exposure `none` asserts the answer names her figures and asks for none of them

**Given** a topic without a rule, or a generic question
**When** it is planned
**Then** the turn stays as today (no Core figures), so general explanations are unchanged
```

**New Story 13.5: Fine understands a follow-up on its last answer**

```
As a persona,
I want to ask "Why?" or "And with 200 €?" after an answer,
So that the chat feels like a normal conversation.

**Gap:** `ExposureFilter` drops all history at `none`; `DialogState` covers only the profile form and the what-if
proposal. FR-63. **Needs 13.3.**

**Acceptance Criteria:**

**Given** a released turn followed by a new message
**When** the NLU and the phrasing role are prompted
**Then** both receive exactly one previous released turn, at every Data Exposure Level, and the prompt budget drops
it first when space runs out (AD-14)

**Given** exposure `none`
**When** the previous turn is built
**Then** it is the depersonalised question plus Fine's LLM-phrased answer; a Suitability Statement, demands-and-needs
statement, refusal or advisor reply passes only its Core figures, never its text

**Given** numbers in the previous turn
**When** the Number Check ledger is built
**Then** only those that came from Core outputs of that turn are sourced; other numbers in it are not

**Given** the reference configuration
**When** the context changes prompts
**Then** it is recorded as a deviation from the Experiment Track (E4), and prompt hashes change in the Audit Record

**Given** the turn after a `PARAMETER_PROPOSAL` or `ASSESSMENT` state
**When** it runs
**Then** `DialogState` keeps precedence (AD-24); the one-turn context does not replace it
```

**New Story 13.6: A repeated answer request returns the same answer**

```
As a persona,
I want a double click or a retried request to give me the one answer,
So that I never see "turn failed" for a question Fine already answered.

**Gap:** a duplicate `POST /answer` runs the model twice; the second insert fails on `messages_reply_to_key` and
returns `503 TURN_FAILED` (`AnswerMessageUseCase`, `findReply` without a lock).

**Acceptance Criteria:**

**Given** two concurrent `POST /answer` requests for the same question
**When** both run
**Then** the model runs once (the question is locked before the model call, e.g. `SELECT … FOR UPDATE` on the
question row), or a `DuplicateKeyException` on insert returns the stored reply; both requests get `200` with the
same answer, and an integration test with two parallel requests covers it
```

### 4.3 Architecture (`ARCHITECTURE-SPINE.md`)

**AD-14, exposure-filter bullet (line ~127)**

OLD: `The filter also covers the chat history, which is limited to the last released turns.`

NEW: `The filter also covers the chat history, which is exactly one previous released turn (PRD FR-63). At `none` that turn is the depersonalised question plus the LLM-phrased answer; template texts and advisor replies pass only their Core figures. The Number Check ledger sources previous-turn numbers only if they were Core outputs.`

**AD-24, append under States**

NEW: `Follow-up context (FR-63) is not a DialogState; it is the one-turn history in the prompt payload. A DialogState takes precedence when set.`

### 4.4 Sprint status (`sprint-status.yaml`)

Add under `epic-13`:

```
  13-3-split-turnorchestrator-into-stage-classes: backlog  # retro 6/7 C2; before 13.4, 13.5
  13-4-fine-answers-questions-about-my-situation-with-my-data: backlog  # before Epic 12
  13-5-fine-understands-a-follow-up-on-its-last-answer: backlog  # before Epic 12
  13-6-a-repeated-answer-request-returns-the-same-answer: backlog
```

Retro item `epic-6-retro-item-24-c2-…`: add note `→ Story 13.3`.

## 5. Implementation Handoff

- **Scope:** moderate (backlog additions, PRD/architecture amendments, no replan).
- **PM (Commc):** approve FR-62/FR-63 and the E4 deviation note.
- **Dev:** apply the edits in §4, then run 13.3 → 13.4 → 13.5 one per session; 13.6 any time. 13.5 gets the extra Blind Hunter review (persona data at `none`).
- **Success criteria:**
  - p-lena's emergency-fund question at `none` names her expenses and reserve and asks for neither.
  - "Why?" and "And with 200 €?" after an answer are answered on that answer, in a live check.
  - No persona attribute reaches the model at `none` (exposure tests extended to the previous turn).
  - Two parallel `POST /answer` return the same `200` answer.
  - Q&A Acceptance Suite (11.1) gets rows for personal `INFORM` and follow-up pairs.
