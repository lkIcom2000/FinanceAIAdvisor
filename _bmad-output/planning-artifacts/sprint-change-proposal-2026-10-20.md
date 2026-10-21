---
title: "Sprint Change Proposal: Split experiments from the final application"
date: 2026-10-20
status: approved
mode: batch
scope: moderate
---

# Sprint Change Proposal: Split Experiments from the Final Application

## 1. Issue Summary

**Trigger.** On 2026-10-20, while the architecture was being created, the author clarified how the project is structured. No story triggered the change; there are no epics or stories yet.

**Problem statement.** The final PRD (`prds/prd-FinanceTool-2026-10-20/prd.md`) treats the experiments and the application as one system:

- the evaluation harness (§4.13) is a feature of the application;
- the application is described as the instrument that makes the research questions "answerable through experiments" (§1);
- the evaluation (§8) measures the experiment metrics against the application.

The author's intended setup is different:

1. **Experiment track.** E1–E4 and the hybrid-vs-pure-LLM baseline run as standalone Google Colab scripts, one per experiment, fully independent of the final application. They determine the design *parameters* (model, role prompt, compliance layers, data exposure, thresholds) and produce architecture insights.
2. **Final application.** A Java Spring Boot backend and an Angular frontend. It runs on the local PC and is deployed to Azure (one EU GPU VM).
3. **Artifact evaluation.** The thesis evaluates the artifact in two ways:
   - by verifying that all requirements are met, meaning the compliance requirements and the requirements set in the DSR relevance and rigor cycles;
   - by a user acceptance test, in which users accept the product and rate it against criteria.

**Issue type.** Misunderstanding of original requirements, together with a clarified strategic scope.

**Evidence.**
- The author's statements in the architecture session on 2026-10-20.
- The architecture memlog entries "Scope split", "Spine scope = final application only" and "Thesis evaluation = requirements verification".

## 2. Impact Analysis

### Checklist status

| Item | Status | Finding |
|---|---|---|
| 1.1 Triggering story | N/A | No stories yet. The trigger came up during architecture. |
| 1.2 Core problem | Done | Experiments and the app were merged; see §1 above. |
| 1.3 Evidence | Done | Author statements and the architecture memlog. |
| 2.1–2.5 Epic impact | N/A | No epics exist yet. The change lands **before** epic planning, so nothing is rolled back. |
| 3.1 PRD | Action-needed | §0, §1, §2.1, §3, FR-24/43/46, §4.13 (FR-47–FR-51), §5, §6, §8, §9, §10 and §11 need updates. See §4. |
| 3.2 Architecture | Done | The spine has already been re-targeted: Spring Boot + Angular, experiments out of scope, AD-25 handoff, AD-15 acceptance suites. The thesis chapter was updated. |
| 3.3 UI/UX | N/A | No UX specification exists. UI scope is unchanged except the user acceptance test (§8.4). |
| 3.4 Other artifacts | Done / Action-needed | The experiments brief exists. Addendum §B needs one note. The PRD memlog needs entries. |
| 4.1 Direct adjustment | Viable | Edit the PRD sections in place; low effort, low risk. |
| 4.2 Rollback | Not viable | Nothing has been built. |
| 4.3 MVP review | Viable (partial) | The MVP feature set is unchanged. Only the evaluation scope moves: the harness features leave the app. |
| 4.4 Selected path | Done | **Direct Adjustment** of the PRD, plus a small MVP clarification of the evaluation scope (hybrid). |

### Epic impact

None. No epics exist yet. Future epics should be planned from the updated PRD and the final spine:

- an experiment track outside this repo's app epics;
- an epic for the verification suites and the audit export;
- an epic for the user acceptance test setup.

### Artifact impact

| Artifact | Impact |
|---|---|
| PRD | Moderate. Requirements text changes in about 14 places. The MVP features are unchanged, and the evaluation section is restructured. |
| Addendum | Low. One note on where the test sets are used. |
| Architecture spine | Already aligned (status final). |
| Experiments brief | Already written. It serves as the handoff for the Colab track. |
| Thesis architecture chapter | Already aligned. |

### Technical impact

- The app no longer needs these experiment features:
  - a pure-LLM baseline mode;
  - per-run manifests;
  - GPU and runtime recording;
  - an offline scorer.
- The app needs these instead:
  - requirement acceptance suites;
  - an audit export;
  - a config fingerprint that traces settings back to their experiment.
- The Colab scripts need lean versions of the deterministic pieces (Core, checks, exposure filter). Some logic will exist twice, in Python for the experiments and in Java for the app. The author accepts this.

## 3. Recommended Approach

**Direct Adjustment of the PRD**, with a clarification of the evaluation scope.

- **Rationale.** No code or epics exist, so editing the PRD now costs little and prevents the epics from being planned against a wrong scope. The MVP feature set (FR-1 to FR-46, FR-52) stays. Only the evaluation features (§4.13) and §8 change their home.
- **Effort.** Low, about one editing pass.
- **Risk.** Low. The main residual risk is that results from the Python experiments do not carry over one-to-one to the Java implementation. AD-25 and the verification suites mitigate this, and §10 lists it as a threat to validity.
- **Timeline impact.** None negative. The Colab track can start immediately, in parallel with app development.

## 4. Detailed Change Proposals (PRD)

All edits target `_bmad-output/planning-artifacts/prds/prd-FinanceTool-2026-10-20/prd.md`.

### P1. §0 Document Purpose: add the two-track note

OLD:
> ... §8 specifies the evaluation, which is the main consumer of the prototype. ...

NEW:
> ... §8 specifies the evaluation. It has two tracks: separate Colab experiments that determine design parameters, and the evaluation of the final application by requirement verification and a user acceptance test. ...

Rationale: sets the frame for every later section.

### P2. §1 Vision: last sentence

OLD:
> The prototype is the DSR artifact that makes both questions answerable through experiments.

NEW:
> The application is the DSR artifact. Separate experiments determine its design parameters. The artifact itself is evaluated by verifying that its requirements are met and by a user acceptance test.

### P3. §2.1 Researcher JTBD

OLD:
> - **Researcher, i.e. the author (functional):** "Switch experiment configurations and rerun the test sets reproducibly, so every DSR claim is backed by a measurement."

NEW:
> - **Researcher, i.e. the author (functional):** "Verify with evidence from the system that every requirement is met, and apply the experiment results as traceable configuration."

### P4. §3 Glossary: add and adjust entries

ADD:
> - **Agent Configuration** — The active configuration of the application: model per role, retrieval on or off, role prompt, Compliance Configuration, Data Exposure Level and decoding parameters. Its values are taken from the experiment results.
> - **Acceptance Suite** — An automated test suite that runs the Q&A test set or the Scenario Test Set against the application to verify requirements.
> - **Experiment Track** — The standalone Colab scripts for E1–E4 and the pure-LLM baseline. They run independently of the application.

CHANGE:
> - **Experiment Configuration** — The combination of LLM, retrieval on or off, role prompt, Compliance Configuration and Data Exposure Level used for a run **in the Experiment Track**.

### P5. FR-24, FR-43, FR-46: configuration wording

OLD (FR-24):
> Retrieval can be switched on or off per Experiment Configuration (E1).

NEW:
> Retrieval can be switched on or off per Agent Configuration.

OLD (FR-43, first sentence):
> The data passed to the LLM is configurable per Experiment Configuration: `raw`, `pseudonymised`, `aggregated`, or `none` (E4).

NEW:
> The data passed to the LLM is configurable per Agent Configuration: `raw`, `pseudonymised`, `aggregated`, or `none`.

OLD (FR-46):
> The LLM is selected per Experiment Configuration without code changes (E1, E2).

NEW:
> The LLM is selected per Agent Configuration without code changes.

### P6. §4.13: from "Evaluation Harness" to "Requirement Verification"

OLD heading and description:
> ### 4.13 Evaluation Harness
> **Description:** The research instrument. It runs the test sets under each Experiment Configuration and computes the metrics in §8. It serves the researcher, not end users.

NEW:
> ### 4.13 Requirement Verification
> **Description:** Built-in support for verifying that the application meets its requirements. It serves the researcher, not end users. The experiments E1–E4 and the pure-LLM baseline are not part of the application. They run in the Experiment Track (§8.1).

OLD FR-47:
> The harness runs every item of `sampleQuestions_variantB.csv` under a given Experiment Configuration. It stores the response, the detected Expected Behavior class, the Audit Record and the scores per item.

NEW FR-47 (Q&A acceptance suite):
> An Acceptance Suite runs every item of `sampleQuestions_variantB.csv` against the application under the active Agent Configuration. Per item it records the response, the Response Outcome compared with `expected_behavior`, and the Audit Record, and it writes a verification report.

OLD FR-48:
> The harness runs every Scenario: it sets the persona state and simulated date, runs monitoring, and compares the detected Triggers, proposed Actions and assigned Autonomy Levels with the expected values (§8.3).

NEW FR-48 (Scenario acceptance suite):
> An Acceptance Suite runs every Scenario. It resets the persona, sets the simulated date and runs monitoring. It then compares the detected Triggers, proposed Actions and assigned Autonomy Levels with the expected values (§8.3).

OLD FR-49:
> The harness can run the same test sets against a pure-LLM baseline, with no Deterministic Core, Rule Engine or retrieval, for the hybrid-versus-baseline comparison.

NEW FR-49:
> *Moved to the Experiment Track (§8.1).* The application has no pure-LLM baseline mode.

OLD FR-50:
> Every run records the Experiment Configuration, the model identifiers, the decoding parameters, the Knowledge Base and Rule Parameter versions, and the random seeds. Its results are exported in a machine-readable format.

NEW FR-50 (Traceable configuration and audit export):
> Every Audit Record identifies the active Agent Configuration, the model identifiers and digests, the Knowledge Base and Rule Parameter versions, and a fingerprint of all configuration files. Each configuration file names the experiment result it was derived from. Audit Records can be exported in a machine-readable format for verification. Reproducible run manifests for the experiments belong to the Experiment Track.

FR-51: unchanged. Its second bullet now reads "...are run while logged in as the backing persona *in the Acceptance Suite*".

### P7. §5 Constraints: privacy note for the Experiment Track

OLD:
> - **Privacy:** only Synthetic Personas, no real customer data. GDPR-conformant hosting is a design principle (EU-hosted or local models), and data minimisation towards the LLM applies (FR-43).

NEW:
> - **Privacy:** only Synthetic Personas, no real customer data. GDPR-conformant hosting is a design principle for the application (local PC or EU-hosted Azure VM), and data minimisation towards the LLM applies (FR-43). The Experiment Track runs on Google Colab, which is not guaranteed to be EU-hosted. This is acceptable only because it processes nothing but synthetic data, and it is recorded as a threat to validity (§10).

### P8. §6 MVP Scope

OLD (§6.1, Evaluation bullet):
> - **Evaluation:** the Q&A test set, the Scenario Test Set, experiments E1–E4, and a user study with 5–6 participants.

NEW:
> - **Evaluation:** experiments E1–E4 and the pure-LLM baseline as standalone Colab scripts (Experiment Track); requirement verification of the application with the Q&A and Scenario Acceptance Suites and the audit export; a user acceptance test with 5–6 participants.
> - **Platform:** Java Spring Boot backend and Angular frontend, running locally and on one EU-hosted Azure VM.

ADD to §6.2:
> - Experiment tooling inside the application (pure-LLM baseline mode, run manifests, automated answer-quality scoring). These live in the Experiment Track.

### P9. §8 Evaluation: two-track structure

ADD at the top of §8, replacing the current first paragraph:

> The evaluation has two tracks.
>
> - **Experiment Track (§8.1–§8.2, §8.5 marked *[E]*).** Standalone Google Colab scripts, one per experiment, independent of the application. They decide the design *parameters* empirically (model, role prompt, compliance layers, Data Exposure Level, thresholds), using the Q&A test set and the metrics marked *[E]*. Their results enter the application only as Agent Configuration and configuration files.
> - **Artifact Evaluation (§8.3, §8.4, §8.6, metrics marked *[V]*).** The final application is evaluated by **requirement verification** (§8.6) and a **user acceptance test** (§8.4). The design *principles* (Deterministic Core, Autonomy Gate, block-only Judge, data minimisation) are justified normatively and shown to hold in the artifact.

§8.1: prepend one sentence:
> These experiments run in the Experiment Track. The component table describes what each Colab script must implement. The application implements `A`, `A+B` and `A+B+C` as Agent Configuration values, but not the pure-LLM baseline.

§8.3: append one sentence:
> The Scenario Test Set is run by the Scenario Acceptance Suite against the application (FR-48).

### P10. §8.4: from "User Study" to "User Acceptance Test"

OLD heading and first sentences:
> ### 8.4 User Study
> The study has 5–6 participants who match the C-class profile as closely as possible. ... The study ends with a short questionnaire on comprehensibility, trust and perceived control over autonomous Actions. Instrument details (e.g. SUS, a trust scale) are chosen in the study design (Open Question 5).

NEW:
> ### 8.4 User Acceptance Test
> 5–6 participants who match the C-class profile as closely as possible accept the product and rate it against predefined acceptance criteria. Each participant logs in as an assigned Synthetic Persona and completes task scenarios that follow UJ-1 to UJ-4, thinking aloud. After each Recommendation they answer comprehension questions, such as "why was this recommended?" and "what could go wrong?". The test ends with a rating against the acceptance criteria and a short questionnaire on comprehensibility, trust and perceived control over autonomous Actions. The criteria and instruments are chosen in the test design (Open Question 5).

### P11. §8.5 Metrics: tag each metric with its track

Tag metrics without changing their definitions or targets:

| Metric | Track |
|---|---|
| SM-1 (a, b) | [E] |
| SM-2 | [V] |
| SM-3 | [E] [V] |
| SM-4 | [E] [V] |
| SM-5 | [V] |
| SM-6 | [E] |
| SM-7 | [E] [V] |
| SM-8 | [V] (user acceptance test) |
| SM-9 | [E] |
| SM-C1 | [E] |
| SM-C2 | [E] |
| SM-C3 | [V] |

ADD the sentence:
> [E] metrics select the winner of each experiment. [V] metrics are acceptance criteria for the final application under its Agent Configuration.

### P12. New §8.6 Requirement Verification

ADD:
> ### 8.6 Requirement Verification
> Every requirement is mapped to a verification method and to evidence in a **verification matrix**, which is a deliverable of the thesis. The requirements covered are:
>
> - all FRs;
> - the §5 constraints;
> - the requirements derived from the DSR relevance cycle (regulatory and user needs) and the DSR rigor cycle (theory and design knowledge).
>
> The verification methods are:
>
> - automated Acceptance Suites (FR-47, FR-48);
> - Deterministic Core reference tests with known values (e.g. item 40);
> - inspection of exported Audit Records (FR-50);
> - architecture tests;
> - the user acceptance test (§8.4).
>
> A requirement counts as met when its evidence satisfies its acceptance criteria and, where defined, the target of the [V] metric linked to it.

### P13. §9 Open Questions

OLD (1):
> 1. How is SM-9 scored automatically without biasing the comparison, given that the Judge is itself an LLM? ...

NEW (1):
> 1. *(Experiment Track)* How is SM-9 scored automatically without biasing the comparison, given that the Judge is itself an LLM? ...

OLD (5):
> 5. Which instruments does the user study use (e.g. SUS, a trust scale, a comprehension score per Recommendation)?

NEW (5):
> 5. Which acceptance criteria and instruments does the user acceptance test use (e.g. SUS, a trust scale, a comprehension score per Recommendation)?

ADD (6):
> 6. Which requirements from the DSR relevance and rigor cycles are added to the verification matrix beyond the FRs and §5?

### P14. §10 Threats to Validity

ADD:
> - The Experiment Track runs on Google Colab, which is not guaranteed EU hosting; only synthetic data is processed.
> - The experiments run a Python implementation, while the final application is in Java. Results transfer through configuration (Agent Configuration, prompts, thresholds) and are re-checked by the Acceptance Suites.

CHANGE:
> - A small user-acceptance-test sample (5–6) that supports only qualitative conclusions.

### P15. §11: record the change

ADD:
> - 2026-10-20 (Correct Course): the evaluation is split into an Experiment Track (Colab, independent of the application) and an Artifact Evaluation (requirement verification plus user acceptance test). The platform is Java Spring Boot and Angular, deployed locally and on Azure. See `sprint-change-proposal-2026-10-20.md`.

Frontmatter: `updated: 2026-10-20`; status stays `final`, revised by Correct Course.

## 5. Other Artifacts

- **Addendum §B, add a note:**
  > The Q&A test set is used in the Experiment Track (E1–E4) and in the Q&A Acceptance Suite against the application. The Scenario Test Set (§A) is used only in the Scenario Acceptance Suite.
- **PRD memlog:** append `(change)` entries for P1–P15 and an `(event) Correct Course applied`.
- **Architecture spine:** no change. It is already aligned: AD-15 acceptance suites, AD-25 configuration handoff, FR-49 and FR-50 manifests out of scope. Once the PRD is updated, update the spine's `binds` to include FR-50 (the new audit export). This is a minor update.

## 6. Implementation Handoff

**Scope: Moderate.** It is a PRD restructuring before epic planning, with no code impact.

| Role | Responsibility |
|---|---|
| PM (bmad-prd, update intent) | Apply P1–P15 to the PRD, the addendum note and the memlog entries |
| Architect (bmad-architecture, update) | Add FR-50 (audit export) to the spine `binds`. No other change |
| Author / experiment track | Build the Colab scripts from `experiments/experiments-brief.md`, independent of the app |
| PM / PO (bmad-create-epics-and-stories) | Plan the app epics from the updated PRD and the spine, including a verification epic (Acceptance Suites, audit export, verification matrix) and a user-acceptance-test epic |

**Success criteria:**
- The PRD no longer describes experiment tooling as an application feature.
- Every metric is tagged [E] or [V].
- §8.6 defines the requirement verification.
- The PRD and the spine agree on FR-47 to FR-51.
- The epics can be planned without reference to Colab.
