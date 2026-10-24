# Colab Experiments (Experiment Track)

There is one notebook per experiment. The notebooks run independently of the final application (PRD §8, sprint change proposal 2026-10-20). Their results enter the app only as configuration (architecture spine AD-25).

| Notebook | Experiment | Reference taken from |
|---|---|---|
| `E1_model_and_knowledge.ipynb` | E1: Llama 3.1 · Llama 3.1 + RAG · finance-chat · Llama 2 | initial Reference Configuration |
| `E2_role_prompt.ipynb` | E2: plain vs. advisor role prompt | `results/E1/winner.json` |
| `E3_compliance_stack.ipynb` | E3: `A` → `A+B` → `A+B+C` | `results/E2/winner.json` |
| `E4_data_exposure_and_baseline.ipynb` | E4: raw → pseudonymised → aggregated → none, then hybrid vs. pure-LLM baseline, SM-1b audit sample, config handoff | `results/E3/winner.json` |

Run them in order. Each experiment varies one factor, and its winner becomes the reference for the next (PRD §8.1).

## Before the first run

1. **Upload the folder.** Upload the whole `experiments/` folder to Google Drive as `MyDrive/FinanceTool/experiments`. If you use a different location, set `DRIVE_DIR` in the first code cell.
2. **Add the PDFs.** Put the Knowledge Base PDFs in `experiments/rag_corpus/pdfs/` and add one row per PDF to `rag_corpus/metadata.csv` (see `rag_corpus/README.md`). Without PDFs, the RAG arm answers `INFORM` questions with the "no valid source" template.
3. **Choose the GPU.** In Colab, select *Runtime → Change runtime type → T4 GPU*. Keep the same GPU type for all runs; the notebook stops if it differs (`EXPECTED_GPU`).
4. **Smoke test.** For a first check, set `SMOKE_TEST = True`. This runs 7 items per arm in about 5 minutes.

## What a run produces (on Drive, `experiments/results/`)

```text
results/
  models_lock.json                 # model digests; a later run fails if a model changed
  cache/nlu/                       # NLU extractions, shared by all arms (same pinned NLU model)
  cache/kb_<snapshot>/             # embedded Knowledge Base
  E1/<arm>/manifest.json           # config, code/data/testset hashes, model digests, Ollama version, GPU
  E1/<arm>/items.jsonl             # one record per item: NLU, class, evidence, draft, checks, route, outcome
  E1/<arm>/items_scored.jsonl      # + scorer outcome label and SM-9 quality score
  E1/<arm>/metrics.json            # SM-1a, SM-3, SM-4, SM-6, SM-7, SM-9, SM-C1, SM-C2
  E1/winner.json                   # chosen arm + comparison table (read by E2)
  ...
  HYBRID_VS_BASELINE/              # final comparison, sm1b_audit_sample.csv
  agent_config_derived.yaml        # handoff to config/agent/reference.yaml of the app
```

A disconnect loses at most the item in progress. Re-running the run cell skips finished items.

## Runtime (T4, rough)

| Step | Time |
|---|---|
| First setup (Ollama, models: about 20 GB download) | 10–15 min |
| One arm over 75 items (65 Q&A + 10 red-team), incl. scoring | 25–40 min |

E1 has 4 arms, so it takes about 2–3 hours. Colab's free tier may disconnect during that time; the runs resume where they stopped.

One arm of E2, E3 and E4, and `hybrid-final`, has the same configuration as the previous winner. `runner.reuse_winner` copies that winner's results instead of running the arm again (the manifest records `reused_from`). It reuses only if code, data, test set, Knowledge Base, rule snapshot, model digests, Ollama version, GPU type and scorer are unchanged; otherwise it prints what differs and runs the arm. This leaves 11 GPU arms instead of 15.

## Code

The logic lives in `experiments/finlab/`. All four notebooks share it:

- **`core.py`** — Deterministic Core. It is the only source of numbers. Item 40 gives 522/538 withdrawals, and the item 56 persona gives −82 EUR on the due date.
- **`nlu.py` + `classify.py`** — The pinned NLU model extracts structure, and deterministic rules then decide the Expected Behavior class. False premises are checked against `data/known_entities.yaml`.
- **`checks.py` + `numbercheck.py`** — The Check Chain (FR-12, FR-25).
- **`pipeline.py`** — The fixed turn pipeline. `FLAGS` defines what each compliance level gives to the model or enforces.
- **`exposure.py`** — Data Exposure Levels (E4).
- **`rag.py`** — PDF ingest, validity filter and citations.
- **`metrics.py`** — Metrics, scorer and winner proposal.
- **`runner.py`** — Run folders, resume and audit export.

Run `python -m finlab.selftest` from the `experiments/` folder to check the deterministic parts locally, without Ollama.

## Pipeline revision after the E1 pilot run (2026-10-23)

The first full E1 run is kept as a **pilot run** (`results/Pilot_E1/`). In every arm, 20 of 65 items were classified wrongly, 27–45 % of in-scope items were escalated, and three of four arms had identical SM-4 values, so the deterministic layers, not the models, decided the outcome. The causes and the changes:

| Cause in the pilot run | Change | Where |
|---|---|---|
| Facts the user states about their own situation, and generic terms ("active mutual fund"), were checked as premises, giving false `DETECT_TRAP` | Only claims about external, named things are checked: not about the user, a proper name, and present in the message | `classify.premises_to_check` |
| Real product names were not found (word order differs from the KID title); terms used in the corpus were unknown | Multi-word names match if all their words occur in one known name, or verbatim in the corpus. The registry includes the Knowledge Base in every arm, so classes do not depend on the arm | `classify.Registry`, `pipeline.Lab.registry` |
| A described law ("the EU directive on X") or a guarantee attributed to an institution was not checked | Described laws are checked against known names; stated guarantees are checked against the known guarantees | `classify.premises_to_check` |
| The NLU listed out-of-scope categories not in the question (emergency fund → "single stock") and missed crypto | An out-of-scope category needs matching wording in the message; unambiguous wording adds it | `nlu.ground` |
| A general question about a described person was treated as personal; withdrawal parameters were missing | Subject is generic if the message names no person; withdrawal parameters are read from the message | `nlu.ground` |
| A computable question phrased as "will I run into a shortfall?" was not routed to the calculation | Computable eligibility questions are `COMPUTE` | `classify.classify` |
| The judge saw only question and answer, so computed numbers looked invented | The judge gets the same evidence (tool results, cited sources) and is told what is not a rule break | `pipeline._judge_input`, `prompts.JUDGE_SYSTEM` |
| A missing citation escalated at once | One regeneration with a citation instruction, as for number mismatches | `pipeline.run_turn` |
| "Guaranteed" in a product description (pension insurance, deposit guarantee) or in a negated sentence counted as a promise | Feature descriptions and negated sentences are not promises; promises to the user still are | `checks._describes_feature` |
| A routed template was checked against the original class (SM-3 counted every block) | Templates are not checked | `pipeline.run_turn` |

Replayed on the NLU outputs stored in the pilot run, classification accuracy rises from 45/65 to 58/65 with no item getting worse. The remaining 7 errors are NLU extractions of topic or profile fields (e.g. "50 EUR left monthly" read as expenses); they are not fixed, to avoid tuning to the test set.

**Held-out items.** `heldout.csv` holds 5 items written after the pilot analysis but **before** the code changes. They run in every arm and are reported separately (`heldout_behavior_accuracy`, `heldout_classification_accuracy`); they are not part of the primary metrics. They show whether the revision generalises beyond the 65 items it was derived from. With 5 items this is an indication, not a proof.

The NLU prompt is unchanged, so cached NLU extractions stay valid.

**Known side effect (E1 full run).** The scope wording lists are English only. Red-team item R08 asks in German for an insurance "Tarifname"; the evidence rule drops the NLU's correct `insurance_tariff_selection`, and the answer is released as `INFORM` instead of a refusal. In the pilot run, the NLU category alone decided. Not fixed during the experiments; German wording belongs in the lists for the application.

**E1 result (full run, 2026-10-23).** Winner `e1-llama3` (no retrieval), the only arm within both counter-metric tolerances: SM-4 76.9 %, SM-9 3.72, SM-C1 7.1 %, SM-C2 12.5 %, SM-3 0. Retrieval lowered answer quality on `INFORM` items from 4.1 to 2.8: the top passages often come from one partly relevant document, and unanswered citations escalate. Better retrieval (e.g. at most one passage per document, reranking) is future work, not tuned here. Held-out items: 5/5 classified correctly, 4/5 answered as expected in every arm.

**E2–E4 and final comparison (2026-10-23).** E2: `e2-advisor` (plain exceeds SM-C1). E3: `e3-AB`; the judge stopped 3 answers the rule engine had approved, all 3 false positives on inspection (items 11, 35, R07). E4: `e4-none`; no measurable loss without personal data. Hybrid vs. pure LLM: SM-3 0 vs. 32, SM-6 2 vs. 17, SM-4 81.5 % vs. 41.5 %. Final configuration: Llama 3.1 8B, no retrieval, advisor prompt, `A+B`, exposure `none` (`results/agent_config_derived.yaml`).

**Findings for the application.**
- *Phantom citations.* With retrieval off, 17 released answers of the final configuration contain `[S1]`-style markers that point to nothing; compliance rule 6 shows the format, and `SOURCE_VALIDITY` applies only with retrieval on. The app must reject citation markers when no sources were given.
- *Depersonalisation is done by the NLU model.* At exposure `none`, R09 still passed a personal amount (20,000 EUR) to the phrasing model. The app should strip amounts deterministically.
- *Pseudonymisation was not tested in effect:* only 1 of 80 messages differs from `raw`, because the test set holds almost no direct identifiers.

## Known limitations (record in the thesis)

- **Run-to-run variance despite a fixed seed.** The same configuration on 7 smoke items gave SM-4 6/7 (E1, E2) and 7/7 (E3); one draft (item 31) once contained a wrong number. Likely cause: Ollama reuses cached prompt state across similar requests, which changes GPU numerics slightly. Each arm is one sample, so differences of 1–2 items between arms are within noise. Reusing an identical winner arm is not affected: it is the same configuration measured once.
- The checks are deterministic text heuristics. They are auditable, but they produce false positives and false negatives. SM-C1 measures the false positives, and the SM-1b manual audit plus the SM-9 scoring catch the misses.
- The few-shot examples of the NLU and the entity registry were written independently of the test items, but by the same author. This is a threat to validity, because they may be tuned to the test set without intent.
- Rule Parameters marked `verify: true` in `data/rules/rule_parameters.yaml` must be checked against their sources (OQ4).
- Monte Carlo uses a parametric return assumption until historical index data is placed in `data/index/`.
- Colab is not guaranteed EU hosting. Only synthetic data is processed.
