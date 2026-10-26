# Epic 4 Context: Knowledge Base and Rule Parameters — Explanations with Valid Sources

## Goal

Fine explains finance topics with cited, dated sources and computes subsidies from versioned Rule Parameters, including the ECB key rates. Missing or expired sources lead to an honest answer or an Escalation, never to model knowledge.

## Stories

- Story 4.1: Versioned Rule Parameters and the Subsidy Calculator
- Story 4.2: Knowledge Base ingest with a pinned snapshot (check the E1 result first; reserve item 2: minimal/off if RAG does not win)
- Story 4.3: Explanations with cited, valid sources (check E1 first)

## Requirements & Constraints

- Reference data are versioned files with validity windows (AD-9): `data/rules/*.yaml` entries `(key, value, unit, validFrom, validTo, source, version)`, append-only; `data/kb/*.md` with front matter `(docId, source, version, validFrom, validTo)`. Both hashed LF-normalised into the config fingerprint (AD-14; `data/rules` already in `ConfigFingerprints.fileSet`).
- The Core refuses to compute with a Rule Parameter not valid at the date asked (`NoValidRuleParameterException`); the turn routes `ESCALATE` (`BlockRouting`, `HandoverReason`, Story 3.5).
- Expired KB documents are excluded from retrieval; citing an expired or unknown passage fails `SOURCE_VALIDITY` → `ESCALATE`; no valid source → the Story 3.3 template with outcome `INFORM`; `retrieval: false` skips stage 4 without code changes.
- Embeddings: Ollama `/api/embed`, pinned `bge-m3`, `truncate: false`; pgvector via `hibernate-vector`; startup refuses on a snapshot mismatch.

## Technical Decisions

- Existing: `knowledge.RuleParamPort` + `YamlRuleParameters` (overlap check), `RuleParameterValue` into pure Core functions, gateway `record(...)` writes rule versions with validity into provenance (Why dialog), `TurnState.Tools.ruleParameterError` → `invalid_rule_parameter` route.
- Profile data (income, children) arrive with Epic 6; until then the chat checks subsidy rules for a later year but does not compute subsidy amounts (decision 2026-10-26).
- Retro A5: check `TurnOrchestrator` size (506 lines at Epic 4 start) before adding the retrieval stage in 4.3; extract stage classes if it keeps growing.

## Cross-Story Dependencies

- 4.3 needs 4.2 (passages) and fills `TurnOrchestrator.kbRetrieve` (stage 4, pass-through today).
- Experiment counterparts: `experiments/finlab/core.py:223-250` (`riester_subsidy`), `experiments/data/rules/rule_parameters.yaml`, `experiments/rag_corpus/`, E1 results `experiments/results/E1/`.
