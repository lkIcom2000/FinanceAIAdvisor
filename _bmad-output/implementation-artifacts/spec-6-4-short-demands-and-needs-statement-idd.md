---
title: 'Story 6.4: Short demands-and-needs statement (IDD)'
type: 'feature'
created: '2026-10-26'
status: 'done'
baseline_revision: '0fd0a63'
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-6-context.md'
---

<intent-contract>

## Intent

**Problem:** A protection question with a complete Needs Profile runs the investment category selection (`protection_needs` lands on `balanced_etf_portfolio`), asks for `existing_contracts` the bank already knows, and leaves no demands-and-needs statement for chat or Home.

**Approach:** A pure Core function `demandsAndNeeds` (Needs Profile + bank contracts) yields, per area (liability, occupational disability, Duplicate Coverage), the need, what the persona has and what to check, plus keyed counts. `advisory.application.NeedsStatements` (retro A5) runs it instead of the category selection for protection topics; the statement is stored on the answer and shown in chat and in "Your insurance" on Home.

**Experiment Track counterpart:** none for the statement (the experiment's protection topics fall through `select_category`); `checks.py:164-169` (`IDD_NEEDS`), `pipeline.py:135-136` (`duplicate_coverage`).

## Boundaries & Constraints

**Always:**
- Scope decisions (Commc 2026-10-26): deterministic Core statement, the model writes only the chat text; protection topics run no `CategorySelection` and no cost calculation; contracts always from bank data; Home shows the latest released statement through `GET /api/chat/needs-statement` (204 without one).
- `config/profile-minimums.yaml`: both protection topics need `[[household], [dependants], [occupation]]`; `existing_contracts` is never asked.
- `DemandsAndNeeds.of(needsProfile, contracts, personaId)`: outputs `liabilityContracts`, `disabilityContracts`, `duplicates` (COUNT); areas in fixed order with `need`, `have`, `check` sentences; inputs = the three profile fields + contract citations (policyholder, role, cover; never the provider). Own household = any `household` except `parents`. A co-insured family policy with an own household → "likely no longer covers you, check with <policyholder>, note the cancellation period of your own policy".
- `CoreGateway.needs(context, origin, needsProfile)` records the computation; `NeedsFigures(result, statement)`.
- Check Chain facts for protection: `category = demands_and_needs`, `statement_complete` = all three areas, `needs_facts_collected` = household, dependants and occupation all in the profile the statement used. `IDD_NEEDS` on a `RECOMMEND_CATEGORY` protection turn fails `needs_facts_missing` unless collected (insurer/tariff naming as before). `DOCUMENTATION` treats the statement as the Core record.
- Class instruction file `class-RECOMMEND_CATEGORY-needs.txt` (needs level, no product, insurer or tariff); fixed first line `ResponseTemplates.NEEDS_LEAD`; the regeneration fix asks for costs only on invest topics.
- `ChatMessage.NeedsStatement(areas, facts, figures)` as JSONB (V16); chips = the three counts; audit body `needsStatement` (areas, profile fields used).
- Frontend: chat card panel "Your demands and needs" (reuses the `why-fits` styles); Home "Your insurance" shows the statement card with "Why?" on each figure.

**Never:** product, insurer, provider or tariff names in the statement or fixed texts; profile writes outside `Profiles.confirm`; NLU prompt changes.

</intent-contract>

## Acceptance Criteria

1. Protection question, Needs Profile missing: `ASK_PROFILE`, card asks household, dependants, occupation only (never `existing_contracts`); saving resumes the turn.
2. Jonas with household `single`, 0 dependants, occupation stored: `RECOMMEND_CATEGORY`, no category selection in the ledger/audit, statement areas liability / occupational disability / Duplicate Coverage, liability check names "Parents of Jonas", no provider name anywhere; chips liability contracts 2, disability contracts 0, duplicates 1.
3. `IDD_NEEDS` fails `needs_facts_missing` on a protection recommendation without collected facts and `insurer_or_tariff_named` as before; `DOCUMENTATION` passes with a complete statement.
4. `GET /api/chat/needs-statement` returns the latest statement (204 before); Home renders it with "Why?" links.
5. Unit tests: `DemandsAndNeeds` branches (own/parents household, co-insured, no contracts, dependants), CheckChain IDD cases, templates; OpenAPI snapshot and client regenerated.

## Implementation Notes
- Scope decisions (Commc 2026-10-26): deterministic Core statement; protection topics skip `CategorySelection` and costs; contracts from bank data and the three Needs Profile facts for both protection topics; Home reads the latest released statement from the chat.
- `config/profile-minimums.yaml` deviates from the experiment for `protection_duplicate` (`[[existing_contracts]]` → household, dependants, occupation). The pilot parity test has no `protection_duplicate` item, so it is unaffected. The new prompt file and the minimums change the config fingerprint, which changes the figure keys once.
- `IDD_NEEDS` (app addition): a protection recommendation fails `needs_facts_missing` unless the Core statement recorded household, dependants and occupation. `DOCUMENTATION` takes the statement as the Core record (`category = demands_and_needs`). `COST_TRANSPARENCY` does not apply, and the regeneration fix asks for costs only on invest topics (`RegenerationFix.of(..., costsApply)`).
- Separate class instruction `class-RECOMMEND_CATEGORY-needs.txt`, because the category prompt says "Recommend the product category …" and asks for costs. Fixed first line `ResponseTemplates.NEEDS_LEAD`; it contains no "your" and no risk wording.
- The statement never cites a contract's provider: contracts are cited by cover, policyholder and role. The E2E test checks that no provider or policy number reaches the answer.
- Retro A5: the IDD logic lives in `advisory.application.NeedsStatements`; `TurnOrchestrator` grew by about 15 lines.
- Review (combined sonnet + Blind Hunter, 2026-10-26). Fixed: dependants count only as a whole number of zero or more; household is normalized; `costsApply` comes from the topic; tests for the cost-free fix and for IDD with the flag absent and on `protection_duplicate`; Modulith (advisory no longer reads `DemandsAndNeeds.FIELDS`). Dismissed: the "latest" ordering (the repository orders by `seq`); loading the whole chat per Home read (chats are small); the Home statement is the chat state, not recomputed (scope decision); null labels (`FACT_LABELS` covers every field the Core reads).
- Live Ollama check (local `spring-boot:run`, Jonas without a stored Needs Profile, llama3.1, 2026-10-26), two app additions:
  - The NLU returned "Am I paying twice for my liability insurance?" as `general_explanation` (subject `self`), so the turn went to `INFORM` with a generic, partly wrong text. New grounding rule in `NluExtraction.ground`: a protection topic with a personal subject and first-person wording turns `general_explanation` into `personal_recommendation` (note "request asks about the user's own insurance cover"). The pilot's protection items with `general_explanation` are generic, so the parity test is unaffected; NLU prompt unchanged.
  - With the profile saved on the card, the draft spoke of "the saver" and the question has no figure, so `SUITABILITY` failed `no_profile_reference_or_risk`, and the fix text still asked for "the main risks of the recommended category". The needs prompt now asks the model to speak to the customer as "you" and use household, dependants and occupation; the cost-free fix (`costsApply = false`) asks for the same and the risk each cover protects against. The prompt change alters the config fingerprint.
  - Result: first turn `ASK_PROFILE` with the card asking occupation, household and dependants (AC1); after saving (single, 0, "Software developer") the first attempt escalated (before the prompt fix); a new question released `RECOMMEND_CATEGORY` after one regeneration, 9 checks passed, no category selection, areas liability / occupational disability / cover held twice, liability check names "Parents of Jonas", chips 2 / 0 / 1, no provider name, `GET /api/chat/needs-statement` 200. Residual for the retro: the model text is repetitive, calls two liability contracts "a good thing … extra security" before saying it may be paid twice, and tells the user to ask "your insurance provider" rather than the parents; the deterministic statement carries the correct checks.
