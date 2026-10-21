"""Colab setup helpers: pinned Ollama runtime, model pulls, finance-chat import, model lock."""

from __future__ import annotations

import hashlib
import json
import os
import subprocess
import time
from pathlib import Path

import requests

OLLAMA_VERSION = "0.40.6"
FINCHAT_REPO = "TheBloke/finance-chat-GGUF"
FINCHAT_FILE = "finance-chat.Q4_K_M.gguf"
FINCHAT_TAG = "finance-chat:7b-q4_K_M"

# Llama-2 chat template (AdaptLLM/finance-chat is fine-tuned from LLaMA-2-Chat-7B).
FINCHAT_MODELFILE = """FROM {path}
TEMPLATE \"\"\"[INST] <<SYS>>
{{{{ .System }}}}
<</SYS>>

{{{{ .Prompt }}}} [/INST]\"\"\"
PARAMETER stop "[INST]"
PARAMETER stop "[/INST]"
PARAMETER stop "<<SYS>>"
PARAMETER stop "<</SYS>>"
"""


def sh(cmd: str) -> None:
    print(f"$ {cmd}")
    subprocess.run(cmd, shell=True, check=True)


def install_ollama(version: str = OLLAMA_VERSION) -> None:
    """Install the pinned Ollama runtime. zstd is needed for the release archives."""
    sh("apt-get -qq update && apt-get -qq install -y zstd pciutils lshw > /dev/null")
    sh(f"curl -fsSL https://ollama.com/install.sh | OLLAMA_VERSION={version} sh")


def start_server(url: str = "http://127.0.0.1:11434") -> None:
    env = dict(os.environ, OLLAMA_KEEP_ALIVE="30m", OLLAMA_HOST="127.0.0.1:11434")
    subprocess.Popen(["ollama", "serve"], stdout=open("/content/ollama.log", "w"), stderr=subprocess.STDOUT, env=env)
    for _ in range(60):
        try:
            v = requests.get(f"{url}/api/version", timeout=2).json()["version"]
            print(f"Ollama {v} is running")
            if v != OLLAMA_VERSION:
                print(f"WARNING: expected Ollama {OLLAMA_VERSION}, got {v}")
            return
        except requests.RequestException:
            time.sleep(1)
    raise RuntimeError("Ollama server did not start; see /content/ollama.log")


def pull(model: str) -> None:
    if model == FINCHAT_TAG:
        import_finchat()
    else:
        sh(f"ollama pull {model}")


def import_finchat(cache_dir: str = "/content/hf") -> str:
    from huggingface_hub import hf_hub_download

    path = hf_hub_download(FINCHAT_REPO, FINCHAT_FILE, cache_dir=cache_dir)
    mf = Path("/content/finance-chat.Modelfile")
    mf.write_text(FINCHAT_MODELFILE.format(path=path), encoding="utf-8")
    sh(f"ollama create {FINCHAT_TAG} -f {mf}")
    return path


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def write_or_check_lock(lock_path: Path, models: list[str], url: str = "http://127.0.0.1:11434") -> dict[str, str]:
    """Record model digests on first use; on later runs fail if a digest changed (AD-18 analogue)."""
    tags = {m["name"]: m.get("digest", "") for m in requests.get(f"{url}/api/tags", timeout=30).json()["models"]}
    current = {m: tags.get(m) or tags.get(f"{m}:latest", "") for m in models}
    lock = json.loads(lock_path.read_text(encoding="utf-8")) if lock_path.exists() else {}
    changed = {m: (lock[m], d) for m, d in current.items() if m in lock and lock[m] != d}
    if changed:
        raise RuntimeError(f"Model digests differ from {lock_path}: {changed}. Re-pinned models change results; "
                           f"delete the entry deliberately if this is intended.")
    lock.update(current)
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    lock_path.write_text(json.dumps(lock, indent=2), encoding="utf-8")
    return current


def check_gpu(expected: str | None) -> str:
    out = subprocess.run("nvidia-smi --query-gpu=name --format=csv,noheader", shell=True, capture_output=True,
                         text=True).stdout.strip()
    print(f"GPU: {out or 'none'}")
    if expected and expected not in out:
        raise RuntimeError(f"Comparative runs must use the same GPU type: expected '{expected}', got '{out}'. "
                           f"Change the Colab runtime type or update EXPECTED_GPU deliberately.")
    return out
