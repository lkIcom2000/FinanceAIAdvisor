"""Knowledge Base retrieval over the PDFs in rag_corpus/pdfs (PRD FR-13, FR-23, FR-24).

Every PDF needs a row in rag_corpus/metadata.csv with its source, version and validity window.
PDFs without metadata are skipped (and reported), because a passage without a validity date
cannot be cited (FR-23). Expired documents are excluded for questions at the as-of date.
"""

from __future__ import annotations

import csv
import datetime as dt
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from .llm import Ollama


@dataclass
class Passage:
    pid: str
    doc_id: str
    title: str
    source: str
    version: str
    valid_from: dt.date
    valid_to: dt.date | None
    page: int
    text: str
    score: float = 0.0

    def valid_at(self, d: dt.date) -> bool:
        return self.valid_from <= d and (self.valid_to is None or d <= self.valid_to)

    def citation(self) -> str:
        to = self.valid_to.isoformat() if self.valid_to else "open"
        return f"{self.title} ({self.source}, version {self.version}, valid {self.valid_from.isoformat()} to {to}, p. {self.page})"


class KnowledgeBase:
    def __init__(self, passages: list[Passage], vectors: np.ndarray, snapshot_id: str, embed_model: str):
        self.passages, self.vectors, self.snapshot_id, self.embed_model = passages, vectors, snapshot_id, embed_model

    @property
    def titles(self) -> list[str]:
        return sorted({p.title for p in self.passages})

    # ------------------------------------------------------------------ build
    @classmethod
    def build(cls, corpus_dir: str | Path, client: Ollama, embed_model: str, cache_dir: str | Path,
              chunk_chars: int = 900, overlap: int = 150) -> "KnowledgeBase":
        from pypdf import PdfReader  # imported lazily: only needed when retrieval is used

        corpus = Path(corpus_dir)
        meta = _read_metadata(corpus / "metadata.csv")
        pdfs = sorted((corpus / "pdfs").glob("*.pdf"))
        h = hashlib.sha256()
        for p in pdfs:
            h.update(p.name.encode())
            h.update(p.read_bytes())
        h.update((corpus / "metadata.csv").read_bytes().replace(b"\r\n", b"\n"))
        h.update(f"{embed_model}|{client.digest(embed_model)}|{chunk_chars}|{overlap}".encode())
        snapshot = h.hexdigest()[:16]
        cache = Path(cache_dir) / f"kb_{snapshot}"
        if (cache / "vectors.npy").exists():
            passages = [_passage_from_json(d) for d in json.loads((cache / "passages.json").read_text(encoding="utf-8"))]
            return cls(passages, np.load(cache / "vectors.npy"), snapshot, embed_model)

        passages: list[Passage] = []
        skipped = []
        for pdf in pdfs:
            m = meta.get(pdf.name)
            if not m:
                skipped.append(pdf.name)
                continue
            reader = PdfReader(str(pdf))
            for pno, page in enumerate(reader.pages, start=1):
                text = " ".join((page.extract_text() or "").split())
                for i, chunk in enumerate(_chunks(text, chunk_chars, overlap)):
                    passages.append(Passage(
                        pid=f"{m['doc_id']}:{pno}:{i}", doc_id=m["doc_id"], title=m["title"], source=m["source"],
                        version=m["version"], valid_from=dt.date.fromisoformat(m["valid_from"]),
                        valid_to=dt.date.fromisoformat(m["valid_to"]) if m.get("valid_to") else None,
                        page=pno, text=chunk))
        if skipped:
            print(f"[rag] skipped PDFs without metadata row: {skipped}")
        if not passages:
            print("[rag] no passages: add PDFs to rag_corpus/pdfs and rows to rag_corpus/metadata.csv")
            return cls([], np.zeros((0, 1)), snapshot, embed_model)
        vecs: list[list[float]] = []
        for i in range(0, len(passages), 32):
            vecs.extend(client.embed(embed_model, [p.text for p in passages[i:i + 32]]))
        arr = np.asarray(vecs, dtype=np.float32)
        arr /= np.linalg.norm(arr, axis=1, keepdims=True) + 1e-9
        cache.mkdir(parents=True, exist_ok=True)
        np.save(cache / "vectors.npy", arr)
        (cache / "passages.json").write_text(json.dumps([_passage_to_json(p) for p in passages], ensure_ascii=False),
                                             encoding="utf-8")
        print(f"[rag] indexed {len(passages)} passages from {len(pdfs) - len(skipped)} PDFs, snapshot {snapshot}")
        return cls(passages, arr, snapshot, embed_model)

    # ------------------------------------------------------------------ query
    def search(self, client: Ollama, query: str, as_of: dt.date, k: int = 4, min_score: float = 0.45) -> list[Passage]:
        if not self.passages:
            return []
        q = np.asarray(client.embed(self.embed_model, [query])[0], dtype=np.float32)
        q /= np.linalg.norm(q) + 1e-9
        scores = self.vectors @ q
        out: list[Passage] = []
        for idx in np.argsort(-scores):
            p = self.passages[int(idx)]
            if scores[idx] < min_score or len(out) >= k:
                break
            if p.valid_at(as_of):  # expired documents are excluded (FR-23)
                out.append(Passage(**{**p.__dict__, "score": float(scores[idx])}))
        return out


def _read_metadata(path: Path) -> dict[str, dict[str, str]]:
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as f:
        return {r["filename"]: r for r in csv.DictReader(f) if r.get("filename") and not r["filename"].startswith("#")}


def _chunks(text: str, size: int, overlap: int) -> list[str]:
    if not text:
        return []
    out, i = [], 0
    while i < len(text):
        out.append(text[i:i + size])
        i += size - overlap
    return out


def _passage_to_json(p: Passage) -> dict:
    d = dict(p.__dict__)
    d["valid_from"] = p.valid_from.isoformat()
    d["valid_to"] = p.valid_to.isoformat() if p.valid_to else None
    return d


def _passage_from_json(d: dict) -> Passage:
    d = dict(d)
    d["valid_from"] = dt.date.fromisoformat(d["valid_from"])
    d["valid_to"] = dt.date.fromisoformat(d["valid_to"]) if d["valid_to"] else None
    return Passage(**d)
