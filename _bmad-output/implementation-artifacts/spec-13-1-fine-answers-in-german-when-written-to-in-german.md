---
title: 'Story 13.1: Fine answers in German when written to in German'
type: 'feature'
created: '2026-10-28'
status: 'done'
baseline_commit: 'fd8925bfd7e5ff69f5afca22442d2e10066e3104'
route: 'dispatch'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A German question gets an English answer (Epic 3 compose check, 2026-10-26). The role, class and compliance prompts, the fixed class texts and the Rule Engine word lists are English only, so a German draft would also fail or slip past the checks.

**Approach:** A deterministic language detector (`de`/`en`) runs once per turn on the user's message. The phrasing and regeneration prompts tell the model the answer language, the fixed class texts and chips exist in both languages, the Rule Engine reads German numbers and German word lists, and the Audit Record stores the language.

## Boundaries & Constraints

**Always:** Detection is deterministic (word and character cues, no model call) and the turn keeps one language from detection to release. The Rule Engine word lists hold both languages at once (a union, not a switch), so a mixed or misdetected draft is still checked. Numbers still come only from the Deterministic Core. Prompt changes are a documented deviation from the experiment track (E4 ran English, single-turn), and the new prompt hashes show it in the Audit Record. English turns behave as before; existing English fixtures and `golden.json` stay green unchanged.

**Never:** No UI translation (Angular chrome, `CheckId` captions, cards stay English). No NLU prompt change (the NLU cache key stays valid). No third language. No migration (the language goes into the JSONB audit payload). No LLM-translated class texts.

**Decisions (Commc, 2026-10-28):** (1) A message without clear language cues takes the language of the previous released turn, else English (today's behaviour). (2) Retro A6: only `NumberParser` gets German formats; `ExposureFilter` and `NluExtraction` amount parsing stay apart in this story (record why in Implementation Notes) and A6 moves to `deferred-work.md`. (3) Full spec kept despite ~1800 tokens.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| German question | "Wie viel Rente bekomme ich mit 67?" | German answer; audit `language: "de"` | N/A |
| English question | "How much pension will I get?" | English answer as today; `language: "en"` | N/A |
| Out of scope, German | "Soll ich einen Bausparvertrag abschließen?" | German refusal text and German chips | N/A |
| Trap, German | "Garantiert mir der ETF 5 % pro Jahr?" | German premise callout ahead of the answer | N/A |
| Regeneration, German | First German draft fails a check | Fix prompt asks for German; released text German | Escalation notice German |
| Ambiguous message | "ok", "150 EUR?", "ETF" | Language of the previous released turn, else English | N/A |
| German numbers | Draft "1.234,56 EUR", "4 %", "2,5 Prozent", "1,2 Mio." | Number Check reads 1234.56, 0.04, 0.025, 1200000 | Unsourced number → mismatch as today |

</frozen-after-approval>

## Code Map

- `config/prompts/role-advisor.txt`, `compliance-rules.txt` -- add one answer-language line with a placeholder (`{answer_language}`); class files unchanged unless a sentence says "English". Hashes are computed by `adapter/PromptFiles.sha256` and flow via `TurnState.Prompt.promptHashes`; nothing to change there.
- `advisory/domain/ClassInstruction.java`, `PhrasingPrompt.build` -- fill the language placeholder; `ClassInstruction`'s sentences are for the model and stay English.
- `advisory/application/PromptStage.java:79-99` -- builds the phrasing prompt; pass the turn language.
- `advisory/domain/RegenerationFix.of` (lines 76-101), called from `application/CheckStages` -- fix sentences stay English (model-facing) but end with the answer-language instruction.
- `advisory/domain/TurnState` -- add a `language` component (set in `TurnOrchestrator` / `ClassifyStage` before NLU; `previous` holds the prior turn for fallback). Many constructors/`with*` methods; follow the existing pattern.
- `advisory/domain/ResponseTemplates.java` -- all user-facing class texts: `ESCALATE`, `REFUSE_OUT_OF_SCOPE`/`refuseOutOfScope`, `REFUSAL_SUGGESTIONS`, `ASK_PROFILE`/`askProfile`/`WHY_ASK_PROFILE`/`DEFAULT_MISSING`/`withProfileReason`, `LIMITATIONS`, `PARAMETER_PROPOSAL`, `range()`, `GUARANTEE_CALLOUT`/`ASSUMPTION_CALLOUT`/`premiseCallout`, `recommendationLead`, `CONFLICTS`/`plainConflict`, `NEEDS_LEAD`. Add a language parameter and German variants.
- `advisory/domain/NoValidSource` (`TEXT`, `suggestion()`), `application/NeedsStatements.FACT_LABELS`, `application/Recommendations.FACT_LABELS`/`CHIPS` -- German variants.
- Callers that choose texts: `domain/BlockRouting.route` (55-86), `application/DraftStage` (39-76), `application/ReleaseStage` (87).
- `advisory/domain/CheckChain.java` (lists at 32-115) -- add German alternatives to `REFUSAL`, `ALTERNATIVE`, `ROLE_BREAK`, `GUARANTEE`, `TIMING`, `NEGATION`/`NEG_WORD`, `PROMISE_TO_USER`, `TARIFF` (Tarif), `VERDICT`, `ASK`, `RISK`, `COST`, `COMMISSION`. Do not change the English alternatives.
- `advisory/domain/NumberParser.java` -- `toDecimal` already reads "1.234,56"/"2,5"; add German suffixes (Prozent, Tsd., Mio., Mrd.), `APPROX` (etwa, rund, ungefähr, circa), `REF_BEFORE` (Absatz, Abs., Ziffer, Nr.), German `MONTHS` (März, Mai, Juni, Okt., Dez.).
- `advisory/application/ResponseAuditBody.java` + `ReleaseStage.java:115-129` -- add `language`. `AuditWriter.SCHEMA_VERSION` stays 1 (additive field).
- Tests: `domain/CheckChainTest` (`test/resources/checks/cases.json`, `golden.json` untouched), `domain/NumberCheckTest` (`test/resources/numbers/cases.json`), `ResponseTemplatesTest`, `PhrasingPromptTest`, `PromptAndRegistryFilesTest`, `web/ChatEndToEndTest` with `testing/StubLlmConfiguration.StubLlm`.

## Tasks & Acceptance

**Execution:**
- [x] `advisory/domain/Language.java` (new) -- `enum Language {DE, EN}` with `detect(String text, Language previous)`: umlaut/ß and German function-word count vs English function-word count; tie → previous turn's language, else English -- one deterministic source of the turn language.
- [x] `advisory/domain/TurnState.java`, `application/TurnOrchestrator.java` -- carry `language`, detected where the NLU stage sets `previous` (the fallback needs it) and before classification -- every stage reads the same value.
- [x] `config/prompts/role-advisor.txt`, `domain/PhrasingPrompt`/`ClassInstruction`, `application/PromptStage.java` -- answer-language instruction ("Answer in German." / "Answer in English.") -- the model writes in the user's language.
- [x] `advisory/domain/RegenerationFix.java`, `application/CheckStages.java` -- append the same instruction to the fix -- regeneration keeps the language.
- [x] `advisory/domain/ResponseTemplates.java`, `NoValidSource.java`, `application/NeedsStatements.java`, `application/Recommendations.java`, `domain/BlockRouting.java`, `application/DraftStage.java`, `application/ReleaseStage.java` -- German variants selected by the turn language -- class texts and chips match.
- [x] `advisory/domain/CheckChain.java` -- German word-list alternatives -- German drafts are checked like English ones.
- [x] `advisory/domain/NumberParser.java` -- German suffixes, approximations, references, months -- Number Check reads German formats.
- [x] `advisory/application/ResponseAuditBody.java`, `ReleaseStage.java` -- record `language` -- audit shows the language used.
- [x] Tests -- `LanguageTest` (matrix incl. ambiguous cases); German cases appended to `numbers/cases.json` and `checks/cases.json` (each failing check code has one German fixture that fails and one that passes); `ResponseTemplatesTest` German variants; `ChatEndToEndTest` German turn (German refusal text, audit `language: "de"`) and German regeneration path.
- [x] This spec's Implementation Notes -- record the E4 deviation as 13.5 did (which prompt files gained which line; `experiments/finlab` unchanged; `promptHashes` change).

**Acceptance Criteria:**
- Given a German (or English) chat message, when Fine answers, then the released text, class texts, chips and the regeneration path are in that language and the Audit Record holds `language`.
- Given a German draft, when the Rule Engine checks it, then the Number Check reads German number formats and the scope, guarantee/timing and product-naming checks fire on German wording, proven by recorded fixtures in both languages.
- Given the reference configuration, when prompts change, then the phrasing prompt hashes in the Audit Record differ from before and the deviation is documented.

## Implementation Notes

- E4 deviation (documented): `config/prompts/role-advisor.txt` gained one line, `Answer in {answer_language}, the language of the customer's message; keep this language for the whole answer.`; `PhrasingPrompt.build(..., Language)` fills it (`Language.fill`; a role prompt without the placeholder gets `Answer in <Language>.` as its last line). `compliance-rules.txt` and the class files are unchanged (one line in one file is enough; no class file says "English"). The regeneration fix keeps its English sentences and now ends with `Answer in German.` / `Answer in English.` (also on English turns, so the two English fix expectations in `TurnOrchestratorTest` gained that sentence). E4 ran English and single-turn; `experiments/finlab` is unchanged; the Audit Record's `promptHashes` change with `role-advisor.txt` (NLU prompts and the NLU cache key are untouched).
- `Language.detect`: German words (or any word with ä/ö/ü/ß) against English words; words common to both ("was", "will", "also", "an", "in", "so", "am", "per") are in neither list; a tie takes the previous turn's language, else English. The previous turn's language is read from the stored, unfiltered turn (`PromptStage.Earlier`, `Language.of(user, agent)`: its message, else its released answer), because at exposure `none` the filtered previous turn may be absent. It is set in the NLU stage next to `previous`, before classification (`TurnState.language`, English until then).
- Texts: `ResponseTemplates` holds German variants of every class text (escalation notice, refusal with German out-of-scope names, chips, profile question with German field names, limitations, proposal, range with German amount format `12.345,67`, premise callouts, recommendation and needs leads with German category labels keyed by the Core category); the German refusal lists the categories after a colon ("Dazu kann ich nicht beraten. Nicht Teil dieses Service: {oos}. …"), so it reads correctly for one or several, singular or plural; `NoValidSource` has a German text and related questions. English constants and the one-argument methods are unchanged. `BlockRouting.route`, `BlockRoutingStage`, `DraftStage` and `ReleaseStage` pick by `turn.language()`.
- Not translated, by the Never boundary ("cards stay English"): `Recommendations.FACT_LABELS`/`CHIPS` (card rows and figure keys), `NeedsStatements.FACT_LABELS`, `HandoverReason` texts (handover card), the proposal button caption (the German proposal text names it "Calculate"). The Code Map listed the fact labels; the frozen boundary wins.
- `CheckChain`: German alternatives are a union inside the existing patterns (`GUARANTEE`, `NEGATION`, `NEG_WORD`, `REFUSAL`, `ALTERNATIVE`, `VERDICT`, `ASK`, `RISK`, `COST`, `COMMISSION`, `FEATURE`, `PROMISE_TO_USER`, `OUT_OF_SCOPE_RECOMMENDATION`, `TARIFF`, `ROLE_BREAK`); German timing is a separate `TIMING_DE` (whole match as detail) so the English `TIMING` keeps its group-joined details: a trade verb followed by a point in time or market move, or (reverse order) a time word with a trade verb only together with advice wording (sollten, empfehle(n), besser, lohnt sich, am besten), so "Heute können Sie … kaufen" passes. German `RISK` holds only risk-specific stems (risik, verlust, schwank, kursrückgang, kursverlust, wertverlust), not bare "fallen"/"sinken" ("Kosten fallen an"). `SUITABILITY` also accepts the German address (`CUSTOMER_DE`: Ihnen/Ihr/Ihre/..., not "Sie"), the counterpart of `contains("your")`; without it no German recommendation could pass.
- `NumberParser`: suffixes Prozent, Tausend, Tsd., Mio., Millionen, Mrd., Milliarde(n) (a trailing dot is dropped before the multiplier lookup; "millionen" sits ahead of "million" in the alternation; the German word suffixes need a word end, `(?!\p{L})`, so "5 Prozentpunkte" reads 5 and "3 tausendmal" reads 3; the English suffixes are unchanged); `APPROX` etwa, rund, ungefähr, knapp, fast, bis zu (circa/ca. were there); `REF_BEFORE` Absatz, Abs., Ziffer, Buchstabe, Anhang, Kapitel, Stufe, Schritt, Frage, Abbildung, Tabelle (Nr. was there; "Satz" left out, it would mask "Steuersatz 25 %"); German months März, Mai, Okt., Dez. (Juni/Juli via jun/jul), and a German day with a dot ("3. März 2026").
- Retro A6 (Decision 2): only `NumberParser` gets German formats. `ExposureFilter` (banding of amounts for the prompt) and `NluExtraction` (grounding of stated figures) keep their own parsers: changing them alters exposure values and NLU groundings recorded in earlier turns and the E1 pilot fixtures, which is its own change; the A6 entry is in `deferred-work.md`.
- Tests: `LanguageTest` (matrix rows, "Was bedeutet …?" questions and ambiguous messages), 15 German rows in `numbers/cases.json`, 25 German rows in `checks/cases.json` (`"language": "de"` with an `expect` map; the golden comparison skips them, so `golden.json` is unchanged; `everyCheckThatCanFailOnWordingHasAFailingAndAPassingGermanCase` covers every check id except `DOCUMENTATION`), German variants in `ResponseTemplatesTest`, `PhrasingPromptTest`, `BlockRoutingTest`; `ChatEndToEndTest`: German refusal + chips + audit `language: "de"` + ambiguous follow-up keeping German, and the German regeneration path (fix ends with `Answer in German.`, German escalation notice); the English turn asserts `language: "en"`.
- Audit: `ResponseAuditBody.language` (`de`/`en`), additive; `AuditWriter.SCHEMA_VERSION` stays 1; no migration.

## Spec Change Log

## Review Triage Log

Review ran as one combined sonnet reviewer (blind, edge-case and verification-gap lenses over the code and test diff, fixtures excluded) instead of three layers, per the project's lean build loop; recorded here as the workflow deviation.

| # | Finding | Verdict | Evidence | Route |
|---|---------|---------|----------|-------|
| 1 | `Language`: "Was bedeutet TER?" scores 0:0 and falls back to English on a first turn | medium | "was" is deliberately in neither list, "bedeutet" was missing | patch (question words added, `LanguageTest` rows) |
| 2 | `NumberParser`: German word suffixes without word end ("5 Prozentpunkte" read as percent, "3 tausendmal" as 3000) | medium | suffix alternation had no trailing guard | patch (`(?!\p{L})`, number cases) |
| 3 | `NumberParser` ignores the turn language, German "1,234" read as 1234 | medium (rare) | `toDecimal` unchanged, treats single-separator groups as thousands | defer (`deferred-work.md`, with Retro A6) |
| 4 | `TIMING_DE` reverse order flags neutral "Heute können Sie … kaufen" | medium | reverse alternative had no advice word | patch (advice word required, passing case) |
| 5 | `RISK` accepts bare "fallen/sinken/rückgang"; `CUSTOMER_DE` lacks "Ihnen" | medium | "Kosten fallen an" satisfied the risk test; "Ich empfehle Ihnen" failed suitability | patch |
| 6 | German `premiseCallout` only tested with an empty premise list; no German trap E2E | medium | `ResponseTemplatesTest` asserted the fallback only | patch (unit assertions per branch; E2E routing covered by `BlockRoutingTest`) |
| 7 | Dotted German day swallows "5. Marktrisiko" as a date | low | real but rare; the undotted English form already behaved so; fix would change English month matching | reject |
| 8 | `REFUSE_OUT_OF_SCOPE_DE` "{oos} sind" ungrammatical for singular categories | low (user-visible) | singular German category names | patch (neutral phrasing, key-coverage test) |

## Verification

**Commands:**
- `cd backend && ./mvnw verify` (JAVA_HOME = JDK 25, Docker running) -- expected: all tests green, 0 skipped, `golden.json` unchanged.
