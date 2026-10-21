"""Run experiment arms over the test sets and write reproducible run folders (PRD FR-50, Experiment Track)."""

from __future__ import annotations

import csv
import datetime as dt
import hashlib
import json
import platform
import subprocess
from pathlib import Path
from typing import Any

from . import __version__, metrics
from .pipeline import ExperimentConfig, Lab, run_turn


def load_items(base: Path, include_redteam: bool = True, ids: list[int] | None = None) -> list[dict[str, Any]]:
    items: list[dict[str, Any]] = []
    with (base / "sampleQuestions_variantB.csv").open(encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if ids and int(r["id"]) not in ids:
                continue
            items.append({"id": int(r["id"]), "set": "qa", "category": r["category"], "prompt_text": r["prompt_text"],
                          "guideline": r["reference_answer_guideline"], "expected_behavior": r["expected_behavior"],
                          "scope": r["scope"], "injection": r["id"] == "65"})
    rt = base / "redteam.csv"
    if include_redteam and rt.exists() and not ids:
        with rt.open(encoding="utf-8") as f:
            for r in csv.DictReader(f):
                items.append({"id": r["id"], "set": "redteam", "category": r["category"], "prompt_text": r["prompt_text"],
                              "guideline": r.get("reference_answer_guideline", ""),
                              "expected_behavior": r["expected_behavior"], "scope": r["scope"], "injection": True})
    return items


def gpu_info() -> str:
    try:
        return subprocess.run(["nvidia-smi", "--query-gpu=name,driver_version", "--format=csv,noheader"],
                              capture_output=True, text=True, timeout=10).stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return "unknown"


def file_hash(*paths: Path) -> str:
    h = hashlib.sha256()
    for p in sorted(paths):
        if p.is_dir():
            for f in sorted(p.rglob("*")):
                if f.is_file() and "__pycache__" not in f.parts:
                    h.update(f.name.encode())
                    h.update(f.read_bytes().replace(b"\r\n", b"\n"))
        elif p.exists():
            h.update(p.read_bytes().replace(b"\r\n", b"\n"))
    return h.hexdigest()[:16]


def run_arm(lab: Lab, cfg: ExperimentConfig, items: list[dict[str, Any]], out_dir: Path,
            experiment: str, resume: bool = True) -> list[dict[str, Any]]:
    """Run one configuration over all items. Results are appended item by item, so a Colab
    disconnect loses at most one item; with resume=True finished items are skipped."""
    run_dir = out_dir / experiment / cfg.id
    run_dir.mkdir(parents=True, exist_ok=True)
    out = run_dir / "items.jsonl"
    done: dict[str, dict[str, Any]] = {}
    if resume and out.exists():
        for line in out.read_text(encoding="utf-8").splitlines():
            r = json.loads(line)
            done[str(r["item_id"])] = r
    manifest = {
        "experiment": experiment, "config": cfg.as_dict(), "started": dt.datetime.utcnow().isoformat() + "Z",
        "finlab_version": __version__, "code_hash": file_hash(Path(__file__).parent),
        "data_hash": file_hash(lab.base / "data", lab.base / "item_personas.yaml"),
        "testset_hash": file_hash(lab.base / "sampleQuestions_variantB.csv", lab.base / "redteam.csv"),
        "rule_snapshot": lab.rules.snapshot_id, "kb_snapshot": lab.kb.snapshot_id if lab.kb else None,
        "ollama_version": lab.client.version(), "gpu": gpu_info(), "python": platform.python_version(),
        "models": {m: lab.client.digest(m) for m in {cfg.phrasing_model, cfg.nlu_model, cfg.judge_model, cfg.embed_model}},
        "nlu_prompt_hash": lab.nlu.prompt_hash, "as_of_default": lab.as_of.isoformat(),
    }
    (run_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    records = []
    with out.open("a", encoding="utf-8") as f:
        for i, it in enumerate(items, 1):
            if str(it["id"]) in done:
                records.append(done[str(it["id"])])
                continue
            rec = run_turn(lab, cfg, it)
            rec.update({"set": it["set"], "category": it["category"], "question": it["prompt_text"],
                        "guideline": it["guideline"], "expected_behavior": it["expected_behavior"],
                        "scope": it["scope"], "injection": it["injection"]})
            f.write(json.dumps(rec, ensure_ascii=False, default=str) + "\n")
            f.flush()
            records.append(rec)
            print(f"[{cfg.id}] {i}/{len(items)} item {it['id']}: class={rec['class']} outcome={rec['outcome']} "
                  f"route={rec['route']} ({rec['latency_s']}s)")
    return records


def save_records(records: list[dict[str, Any]], path: Path) -> None:
    with path.open("w", encoding="utf-8") as f:
        for r in records:
            f.write(json.dumps(r, ensure_ascii=False, default=str) + "\n")


def finish_arm(lab: Lab, records: list[dict[str, Any]], out_dir: Path, experiment: str, arm: str,
               scorer_model: str | None, score_quality: bool) -> dict[str, Any]:
    """Label outcomes where routing is off, optionally score quality, compute and save metrics."""
    if scorer_model:
        metrics.label_outcomes(records, lab.client, scorer_model)
        if score_quality:
            metrics.score_quality(records, lab.client, scorer_model)
    run_dir = out_dir / experiment / arm
    save_records(records, run_dir / "items_scored.jsonl")
    m = metrics.compute(records)
    m["scorer_model"] = scorer_model
    m["scorer_digest"] = lab.client.digest(scorer_model) if scorer_model else None
    (run_dir / "metrics.json").write_text(json.dumps(m, indent=2), encoding="utf-8")
    return m


def write_winner(out_dir: Path, experiment: str, winner: str, rows: list[dict[str, Any]],
                 configs: dict[str, ExperimentConfig]) -> None:
    d = out_dir / experiment
    (d / "winner.json").write_text(json.dumps({
        "experiment": experiment, "winner": winner, "config": configs[winner].as_dict(),
        "decided": dt.datetime.utcnow().isoformat() + "Z",
        "note": "Proposal from metrics.select_winner; review the comparison table before using it.",
        "comparison": rows}, indent=2), encoding="utf-8")


def load_reference(out_dir: Path, previous: str | None, default: ExperimentConfig) -> ExperimentConfig:
    """The winner of the previous experiment becomes the reference for the next (PRD §8.1 run plan)."""
    if previous:
        p = out_dir / previous / "winner.json"
        if p.exists():
            cfg = ExperimentConfig.from_dict(json.loads(p.read_text(encoding="utf-8"))["config"])
            print(f"Reference configuration taken from {previous} winner: {cfg.id}")
            return cfg
        print(f"No {previous}/winner.json found; using the initial Reference Configuration.")
    return default


def audit_sample(records: list[dict[str, Any]], path: Path, n: int = 50, seed: int = 1) -> None:
    """SM-1b: export >= 50 released answers for the manual number audit."""
    import random
    rnd = random.Random(seed)
    pool = [r for r in records if r["outcome"] != "ESCALATE"]
    sample = rnd.sample(pool, min(n, len(pool)))
    with path.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["item_id", "question", "released_answer", "sourced_numbers", "unsourced_or_wrong_number (y/n)",
                    "note"])
        for r in sample:
            nums = sorted({x["value"] for x in r["evidence"]["numbers"]})
            w.writerow([r["item_id"], r["question"], r["released"], " ".join(nums), "", ""])
