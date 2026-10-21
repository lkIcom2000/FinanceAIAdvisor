---
lens: currency / reality-check of committed decisions
target: ARCHITECTURE-SPINE.md (2026-10-20)
reviewed: 2026-10-20
verdict: PASS WITH FIXES (no blocker; 2 high, 3 medium, 3 low)
---

# Currency Review: Architecture Spine

## Scope

Checks every decision the spine commits to. Each one must have been confirmed against the web, a registry or the starter, not asserted from training data. Items already verified by the author (memlog line 30) are not re-checked here: PyPI/npm/GitHub versions, Azure PG17 vector 0.8.2, ACA serverless GPU in Sweden Central, Ollama `/api/chat` with `format` + `options.seed`, and TheBloke/finance-chat-GGUF. This review covers the claims that were left unverified.

## Verified today (no action)

| Claim | Evidence (2026-10-20) | Status |
|---|---|---|
| `llama3.1:8b-instruct-q4_K_M` on Ollama library | ollama.com/library/llama3.1/tags lists it; registry.ollama.ai manifest returns 200 | OK |
| `llama2:7b-chat-q4_K_M` on Ollama library | ollama.com/library/llama2/tags lists it; manifest 200 | OK |
| `bge-m3` on Ollama library | tags `bge-m3:567m`, `567m-fp16`, `latest` (all about 1.2 GB F16; no Q4 variant). "all Q4_K_M except embeddings" is consistent | OK |
| fin-chat Q4_K_M GGUF | HF API: `TheBloke/finance-chat-GGUF` has `finance-chat.Q4_K_M.gguf` | OK |
| Ollama exposes a digest for pinning | `GET /api/tags` returns `digest` (sha256 of manifest) per model; `/api/show` does **not** return a digest | OK, but see F4 |
| `pgvector/pgvector` image for PG17 | Docker Hub tags `0.8.2-pg17`, `pg17`, `0.8.7-pg17` exist | OK, but see F2 |
| Ollama runtime 0.40.x | Docker Hub `ollama/ollama:0.40.6` (latest stable; 0.40.7-rc0 exists) | OK |
| import-linter | PyPI 2.15, requires Python >=3.10, so it works with 3.13 | OK (not in Stack table, see F7) |
| openapi-typescript | npm latest 7.13.0 (2026-02-11) | OK, but see F1 |
| Vite 8.3.4 Node engines | `^20.19.0 or >=22.12.0`, so Node 24 fits | OK |
| Node 24 LTS | nodejs.org index: v26 lines are not LTS yet. 24 is still the current LTS line | OK (Node 26 becomes LTS around end of Oct 2026, so this is still a fine choice) |
| ACA serverless GPU T4/A100 Sweden Central | MS Learn gpu-serverless-overview (updated 2026-09-24) | OK, but see F3 |
| Postgres Flexible Server row triggers | No documented restriction. Ordinary `CREATE TRIGGER` needs only table ownership or the TRIGGER privilege, and PL/pgSQL is built in. Only event triggers and extensions need `azure_pg_admin` | OK, but see F5 |

## Findings

### F1 (High): "TypeScript 5.x" is stale-looking but must be deliberately pinned, and the reason is unstated
- **Evidence:** the npm `typescript` latest is **7.0.2** (2026-07-08; the native/Go compiler line, with 7.1.0-beta out). The last 5.x is 5.9.3. `openapi-typescript@7.13.0` declares `peerDependencies: { typescript: "^5.x" }`, and it uses the TS compiler API to emit its AST.
- **Risk:** a starter (`npm create vite@latest`) or an agent following "latest" installs TS 7. That breaks or warns on the AD-19 type-generation step. The memlog never verified TS, and the spine's "5.x" reads as an outdated assertion, not a decision.
- **Fix:** change the Stack row to `TypeScript 5.9.x (pinned; openapi-typescript 7.x peer-requires ^5)` and add `openapi-typescript 7.13.x` as its own row. Also check that the current `create-vite` React-TS template does not default to TS 7. If it does, pin it in `web/package.json`.

### F2 (High): local and Azure pgvector versions diverge, which breaks the AD-17 "same behaviour" promise
- **Evidence:** Docker Hub `pgvector/pgvector:pg17` currently resolves to **0.8.7**. Azure Flexible Server PG17 provides **0.8.2** (author-verified).
- **Risk:** the floating `pg17` tag gives a different extension version locally and in Azure (HNSW/iterative scan behaviour and defaults changed across 0.8.x). This undermines AD-17, and the KB snapshot is not reproducible across environments.
- **Fix:** in AD-17 and the compose file, pin `pgvector/pgvector:0.8.2-pg17` (the tag exists). Also pin `CREATE EXTENSION vector VERSION '0.8.2'` in the Alembic migration.

### F3 (Medium): ACA serverless GPU running Ollama was never reality-checked for cold start, storage and quota
- **Evidence (MS Learn, 2026-09-24):** this is supported only in **workload-profiles** environments (not Consumption-only). Scale-to-zero by default. GPU quota must be checked or requested on the environment (enabled by default for PAYG/EA, but not guaranteed for student or sponsorship subscriptions). One GPU container per app. The guidance recommends a **storage mount** for model weights and **Premium ACR artifact streaming** to cut cold start. Container storage is ephemeral, so `ollama pull` at boot repeats on every cold start. The NVIDIA driver moves 570 -> 580 and CUDA 12 -> 13 (rollout from May 2026). No official Microsoft Ollama-on-ACA tutorial was found (only a community Tech Community post).
- **Risk:** the first chat turn of a user-study session after idle waits for a GPU replica, image pull and loading about 4 x 4-5 GB GGUFs. That can mean minutes, and AD-7's non-streaming request can time out (ACA ingress default about 240 s). Pushing fin-chat into the image also needs a Premium ACR.
- **Fix:** add to AD-17: the workload-profiles environment, `minReplicas=1` on the Ollama app during study windows (or a warm-up call in study prep), weights on an Azure Files mount with `OLLAMA_MODELS` pointing at it, `OLLAMA_KEEP_ALIVE=-1`, and a quota check as a deploy-epic prerequisite. Pin an `ollama/ollama` image that bundles its own CUDA, and smoke-test it on the driver 580 stack. Move "weights baked vs Azure Files" out of Deferred, since cold start makes it more than cosmetic.

### F4 (Medium): AD-18 pins model digests but not the Ollama runtime, and the fin-chat digest is locally minted
- **Evidence:** `/api/tags` digest = the manifest hash. For library models it matches the registry. For `fin-chat` built by `ollama create` from a Modelfile, the manifest is generated locally, and the layer set (template and params blobs) can change with the Ollama version. The Stack says "Ollama 0.40.x" (a range). Outputs under a fixed seed also depend on the bundled llama.cpp version and on the GPU (local GPU vs T4).
- **Fix:** in `models/lock.json`, record the **GGUF blob sha256** (HF `finance-chat.Q4_K_M.gguf`) in addition to the manifest digest. Pin the exact Ollama image tag (e.g. `ollama/ollama:0.40.6`) in compose and Azure, and record the Ollama version from `GET /api/version` in every run manifest (AD-14). State in AD-17 that seed reproducibility holds only within the same hardware and runtime, which is one reason experiments run locally.

### F5 (Medium): the append-only trigger is not enough on its own
- **Evidence:** standard PG semantics. Row triggers on UPDATE/DELETE do not fire on `TRUNCATE`, and the table owner can `ALTER TABLE ... DISABLE TRIGGER` or `DROP TRIGGER`. Flexible Server permits row triggers for the owner (no superuser needed). This is not a currency issue, but the AD-13 rule was asserted without a check.
- **Fix:** in AD-13, add a `BEFORE TRUNCATE` statement trigger. The app connects as a non-owner role with `INSERT, SELECT` only on `audit_records`, and migrations run as the owner role.

### F6 (Low/Medium): structured outputs on Llama 2-era models is unverified
- **Evidence:** Ollama docs say `format` accepts a JSON schema, and it is grammar-constrained and model-agnostic in principle. No source confirms reliability on Llama 2 chat or fin-chat specifically, and community reports show uneven schema adherence on some models. AD-4 applies `format` only to the NLU role (Llama 3.1), which is fine. If the **Judge** (`none|block|escalate`) is ever bound to an E1 model under test (Llama 2 or fin-chat), schema adherence is untested. AD-6 parses anything else as `none`, which would silently weaken C.
- **Fix:** add a model-setup smoke test that runs `format` against each pinned model and asserts schema-valid output. Count Judge parse failures in metrics rather than folding them into `none` silently.

### F7 (Low): tools named in rules but missing from the Stack table
- import-linter (AD-1 CI contract) and openapi-typescript (AD-19) are committed to but are not in the Stack table, and the memlog has no verification line for them. Fix: add `import-linter 2.15.x` and `openapi-typescript 7.13.x` rows.

### F8 (Low): version ranges that leave the starter free to drift
- `uv` is "latest at setup". The Stack says "Ollama 0.40.x" and "TanStack Query 5.x" while the memlog has exact versions (5.104.5, 0.40.6). Fix: the Stack table should carry the memlog's exact verified versions for anything that touches reproducibility (Ollama, pgvector, TS). Minor ranges are fine for the rest.

## Notes (no action)
- Python 3.13 is fine. 3.14 is current-stable and 3.15 is due about now, but 3.13 is well supported by the pinned stack.
- Llama 3.1 8B and Llama 2 7B are old in 2026. That is acceptable because they are fixed by the PRD and E1 design and are still on the Ollama library. The risk to watch is library delisting, so keep the GGUF blobs archived alongside `lock.json`.

## Sources
- https://ollama.com/library/llama3.1/tags, https://ollama.com/library/llama2/tags, https://ollama.com/library/bge-m3/tags
- https://registry.ollama.ai/v2/library/{llama3.1,llama2,bge-m3}/manifests/... (HTTP 200)
- https://raw.githubusercontent.com/ollama/ollama/main/docs/api.md (`/api/tags` digest, `/api/show`, `format`, `seed`)
- https://hub.docker.com/r/pgvector/pgvector/tags, https://hub.docker.com/r/ollama/ollama/tags
- https://registry.npmjs.org/typescript, https://registry.npmjs.org/openapi-typescript/7.13.0, https://registry.npmjs.org/vite/latest, https://nodejs.org/dist/index.json
- https://pypi.org/pypi/import-linter/json
- https://huggingface.co/api/models/TheBloke/finance-chat-GGUF
- https://learn.microsoft.com/azure/container-apps/gpu-serverless-overview, https://docs.azure.cn/en-us/container-apps/cold-start, https://techcommunity.microsoft.com/blog/appsonazureblog/-/4371463
- https://learn.microsoft.com/en-us/azure/postgresql/security/security-access-control
