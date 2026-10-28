---
title: 'Epic 13 live check fixes: personal figures in follow-ups, no advice in refusals'
type: 'bugfix'
created: '2026-10-28'
status: 'done'
route: 'oneshot'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Epic 13 live check 2026-10-28 (p-lena, German, exposure `none`, real model). (1) "Wie viel Notgroschen sollte ich mir zurücklegen?" got her figures, "Warum?" was fine, but "150 EUR?" answered "Da ich keine Informationen über Ihre spezifischen Ausgaben habe …": grounding set the follow-up to subject `generic` (no person named), so `ToolPlanStage` added no Core figures, and the previous turn ("Warum?") carried none either, so its `FollowUp.provIds` were empty. (2) "Soll ich Bitcoin-Zertifikate kaufen?" was correctly refused, but the phrased refusal added "Wir könnten Ihnen beispielsweise empfehlen, in ein ETF-Sparplan zu investieren …": a recommendation inside a refusal, which no check caught.

**Approach:** (1) An `INFORM` turn about nobody (`generic`) on a topic with a personal-figures rule whose previous released turn carried Core figures runs the user's figures for that topic, as a `self` question does; the turn's own figures then carry the chain to the next follow-up. The subject itself stays unchanged (no class change). (2) The refusal prompt offers the alternative as something Fine can look at or explain, never as a recommendation, and the `SCOPE` check fails a refusal that recommends (`refusal_recommends`, English and German wording), so block routing releases the deterministic refusal template.

</frozen-after-approval>

## Implementation Notes

- Not an epic story: no sprint-status key; it closes the Epic 13 live check for 13.1/13.5/13.6. Both findings kept in one oneshot spec (user decision "b": fix both, then re-check).
- (1) `ToolPlan.personalFigures(cls, extraction, previousHadFigures)`; `ToolPlanStage` passes `turn.previous()` with non-empty `coreFigures()` (read back from evidence by `PromptStage.earlier`, any exposure level). Subject stays `generic` (13.4 Never: no subject upgrade that could reroute the class); `specific_client` never qualifies. The re-run figures land in the turn's `FollowUp.provIds`, so "Warum?" → "150 EUR?" keeps the chain.
- (2) `class-REFUSE_OUT_OF_SCOPE.txt`: the alternative is something to look at or explain, never a recommendation (prompt hash changes; deviation from E4, `experiments/finlab/prompts.py` stays the experiment baseline). `CheckChain.REFUSAL_RECOMMENDS` (EN/DE, negated "nicht/keine … empfehlen" excluded) → SCOPE `refusal_recommends`; `BlockRouting` already releases the refusal template on a failed SCOPE of a refusal, so no routing change.
- Tests: `ToolPlanTest`, `CheckChainTest.aRefusalThatRecommendsItsAlternativeFailsScope`, `TurnOrchestratorTest.aFollowUpAboutNobodyOnAnAnswerWithCoreFiguresRunsTheUsersFiguresAndCarriesThemOn`; `ClassificationEndToEndTest` asserts the new prompt text.
- Review: one Blind Hunter (sonnet, per the user's lean-loop rule instead of the workflow's same-model rule).
- Verification: `./mvnw -B verify -Dfine.it.required=true` BUILD SUCCESS, 995 tests, 0 skipped (before the review patch; the patch touched only the regex, `CheckChainTest` re-run green).
- Live re-check 2026-10-28 (local `spring-boot:run`, research profile, real model), p-lena German chain: emergency-fund question → INFORM with her figures (TOOL_PLAN ran, 5 evidence); "Warum?" → INFORM, but NLU topic `regulation_or_concept` (no rule) so no figures; "150 EUR?" → this time a COMPUTE proposal (savings projection 150 EUR/month, 3 years), no held-data claim; Bitcoin certificates → REFUSE_OUT_OF_SCOPE offering to look at liquidity, ETF savings plans and insurance needs without recommending (finding 2 fixed). Finding 1's rule is unit/orchestrator-tested; live it was not reached because of the topic. Deferred: topic gap and a "[S1]" removal artifact (`deferred-work.md`).

## Review Triage Log

| # | Finding | Verdict | Evidence / route |
|---|---|---|---|
| 1 | `refusal_recommends` has no routing or regeneration entry | false | `BlockRouting.route` L75 releases the refusal template on any failed SCOPE of a refusal; `BlockRoutingTest.aFailedScopeOnARefusalReleasesTheRefusalTemplate` |
| 2 | Pattern flags "ich empfehle Ihnen nicht …"; misses "ich rate", "consider investing" | low | patch: negative lookahead after "ich empfehle" + test case. Misses rejected: the prompt now forbids recommending; a miss only releases the phrased refusal as before |
| 3 | Any generic INFORM after a personal answer gets figures, e.g. "What is an ETF?" | false | `PersonalFigures.forTopic` gates it; ETF topics have no rule; same-topic figures are the intended answer data |
| 4 | Inline `previous` check in `ToolPlanStage`, move to helper | low | reject: one expression, covered by the orchestrator test; style only |
| 5 | Prompt file lost its trailing newline; no German example | false | the file had no trailing newline before (`od -c`); prompt language follows `role-advisor.txt`'s answer-language line; live re-check done |

