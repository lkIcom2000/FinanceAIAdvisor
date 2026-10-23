"""Thin client for Ollama's native API (/api/chat, /api/embed, /api/tags).

The LLM is an untrusted text edge: callers pass messages in and get text out. Nothing here
interprets model output as a tool call or an action.
"""

from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from typing import Any

import requests


@dataclass
class Decoding:
    temperature: float = 0.0
    seed: int = 42
    num_ctx: int = 4096
    num_predict: int = 700
    top_p: float = 0.9

    def options(self) -> dict[str, Any]:
        return {"temperature": self.temperature, "seed": self.seed, "num_ctx": self.num_ctx,
                "num_predict": self.num_predict, "top_p": self.top_p}


@dataclass
class LlmResult:
    text: str
    model: str
    digest: str
    prompt_tokens: int
    output_tokens: int
    latency_s: float
    options: dict[str, Any] = field(default_factory=dict)


class Ollama:
    def __init__(self, url: str = "http://127.0.0.1:11434", timeout: int = 600):
        self.url = url.rstrip("/")
        self.timeout = timeout
        self._digests: dict[str, str] = {}

    def digest(self, model: str) -> str:
        if model not in self._digests:
            tags = requests.get(f"{self.url}/api/tags", timeout=30).json().get("models", [])
            for m in tags:
                self._digests[m["name"]] = m.get("digest", "")
                self._digests.setdefault(m["model"], m.get("digest", ""))
        return self._digests.get(model, "") or self._digests.get(f"{model}:latest", "")

    def version(self) -> str:
        try:
            return requests.get(f"{self.url}/api/version", timeout=10).json().get("version", "")
        except requests.RequestException:
            return ""

    def chat(self, model: str, messages: list[dict[str, str]], decoding: Decoding,
             schema: dict[str, Any] | None = None) -> LlmResult:
        body: dict[str, Any] = {"model": model, "messages": messages, "stream": False,
                                "options": decoding.options(), "keep_alive": "30m"}
        if schema is not None:
            body["format"] = schema
        t0 = time.time()
        # A 500 from Ollama is usually a crashed model runner (e.g. while swapping models on the GPU);
        # the server restarts it, so the same seeded request is retried twice before giving up.
        for attempt in range(3):
            resp = requests.post(f"{self.url}/api/chat", json=body, timeout=self.timeout)
            if resp.status_code < 500 or attempt == 2:
                break
            print(f"[ollama] {resp.status_code} for {model}: {resp.text[:200]!r}; retrying")
            time.sleep(10)
        resp.raise_for_status()
        data = resp.json()
        return LlmResult(
            text=data.get("message", {}).get("content", ""),
            model=model,
            digest=self.digest(model),
            prompt_tokens=int(data.get("prompt_eval_count", 0) or 0),
            output_tokens=int(data.get("eval_count", 0) or 0),
            latency_s=round(time.time() - t0, 2),
            options=decoding.options(),
        )

    def chat_json(self, model: str, messages: list[dict[str, str]], decoding: Decoding,
                  schema: dict[str, Any]) -> tuple[dict[str, Any] | None, LlmResult]:
        res = self.chat(model, messages, decoding, schema)
        try:
            return json.loads(res.text), res
        except json.JSONDecodeError:
            return None, res

    def embed(self, model: str, texts: list[str]) -> list[list[float]]:
        # truncate=False: an over-long chunk must fail loudly instead of being cut silently.
        resp = requests.post(f"{self.url}/api/embed",
                             json={"model": model, "input": texts, "truncate": False},
                             timeout=self.timeout)
        resp.raise_for_status()
        return resp.json()["embeddings"]
