# Review: thesis-architecture-chapter.md (structure + prose)

- **Date:** 2026-10-20
- **Lenses:** structure, then prose (bmad-review)
- **Reader:** humans (supervisor and examiners). Style baseline: Microsoft Writing Style Guide, adapted to academic register.
- **Purpose read:** This chapter helps the thesis supervisor and examiners see how the architecture makes the design principles hold by construction, and how it makes experimental claims measurable and reproducible.
- **Structure model:** explanatory/argumentative chapter (goals, then style, mechanisms, decisions, limitations). The document follows this model.
- **Size:** 2,340 words. Spine (ARCHITECTURE-SPINE.md) checked for every touched fact; no facts, ids, versions or decisions changed.

## Findings

| Pass | Original Text | Revised Text | Changes | Status |
|---|---|---|---|---|
| structure | §1–§10 overall order | PRESERVE | Goals, then style, then mechanisms, then decisions, then limitations is a sound argument order. Overview comes before details. | kept |
| structure | §9 decision table (overlaps rationale in §2, §3, §8) | PRESERVE | The table repeats points from the body, but it works as a recap examiners can scan. It is reinforcement, not redundancy. | kept |
| structure | §3 (505 words, the longest section) | PRESERVE | The four bold-lead paragraphs follow the pipeline diagram stage by stage. The length is justified. | kept |
| structure | Intro: identifiers "E1–E4", "Open Question n" are used but not introduced (FR/SM/AD are) | QUESTION: add one clause naming where experiments E1–E4 and the Open Questions are defined (for example the methodology chapter or the PRD) | Examiners who read this chapter alone meet undefined identifiers. Left for the author, because the source chapter is a content decision. | open |
| prose | The order is code, and the model output cannot change it. | The order is fixed in code, and model output cannot change it. | "is code" was elliptical | applied |
| prose | This covers the chat history as well. | This also applies to the chat history. | Clearer reference | applied |
| prose | Only the Rule Engine can approve. | Only the Rule Engine can approve a draft. | Added missing object | applied |
| prose | A mismatch causes one regeneration, then escalation to a human advisor. | A mismatch triggers exactly one regeneration. If the regenerated draft also fails, the turn is escalated to a human advisor. | Made the condition for escalation explicit. Matches the spine ("exactly one regeneration"). | applied |
| prose | It then calls the Autonomy Gate. | The service then calls the Autonomy Gate. | "It" came after a bullet list, so the antecedent was unclear | applied |
| prose | a rule not valid at the simulated date | a rule that is not valid at the simulated date | Readability | applied |
| prose | Each run writes a manifest. The manifest records: (list of paired items) | Each run writes a manifest that records: (one item per bullet) | Merged the two sentences. Split the arbitrary pairs ("the GPU type and the reference-data snapshots") into separate items. | applied |
| prose | (rejected in the forge) | ; rejected during concept development | "forge" is internal tool jargon that examiners will not know | applied |
| prose | fin-chat | finance-chat | Same term as §8 | applied |
| prose | (DSR split) | , calibrated after first Scenario runs (Open Question 2) | Opaque shorthand. Replaced with wording consistent with §5 and spine OQ2. | applied |
| prose | Mixed spelling: British "minimisation", "centre", "quantisation" next to American "Expected Behavior" | Consider: keep British spelling throughout, and keep "Expected Behavior" as a defined PRD term? | This is a deliberate spelling choice, so it is left for the author | open |

## Summary

- 15 rows: 4 structure (3 PRESERVE, 1 QUESTION) and 11 prose (10 applied, 1 open).
- Estimated reduction: about 0%. The chapter is already compact. Most edits make it clearer without making it shorter.
- No comprehension trade-offs. The mermaid diagrams are unchanged.
