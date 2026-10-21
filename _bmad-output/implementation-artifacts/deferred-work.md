
## Deferred from: code review of spec-1-6-llm-behind-one-port-served-by-a-pinned-ollama-model (2026-10-21)

- `LlmResult.truncated` misses prompts Ollama cut or served from the KV cache (`prompt_eval_count < numCtx`). Maybe-false, medium if true; settle with the AD-5 prompt budget (own token count) in the advisory epic.
