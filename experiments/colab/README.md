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

## Known limitations (record in the thesis)

- The checks are deterministic text heuristics. They are auditable, but they produce false positives and false negatives. SM-C1 measures the false positives, and the SM-1b manual audit plus the SM-9 scoring catch the misses.
- The few-shot examples of the NLU and the entity registry were written independently of the test items, but by the same author. This is a threat to validity, because they may be tuned to the test set without intent.
- Rule Parameters marked `verify: true` in `data/rules/rule_parameters.yaml` must be checked against their sources (OQ4).
- Monte Carlo uses a parametric return assumption until historical index data is placed in `data/index/`.
- Colab is not guaranteed EU hosting. Only synthetic data is processed.
