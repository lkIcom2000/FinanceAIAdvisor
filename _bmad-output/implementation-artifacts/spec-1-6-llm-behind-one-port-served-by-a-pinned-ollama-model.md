---
title: 'Story 1.6: LLM behind one port, served by a pinned Ollama model'
type: 'feature'
created: '2026-10-21'
status: 'done'
baseline_commit: 'f77f460be819627aeb580cc82424203923a1161d'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** No component can call a model yet, and nothing guarantees that the model answering is the one the configuration names (FR-46, AD-5, AD-18, AD-20).

**Approach:** One `llm.LlmPort` with `generate(role, messages, decoding, schema)` and `embed(texts)`, implemented by a thin Ollama adapter on Spring `RestClient` (`/api/chat`, `/api/embed`, `/api/tags`) that resolves the model tag from `AgentConfiguration.llm[role]`. `models/` lists the pulled tags and `models/lock.json` pins each tag's `/api/tags` digest and GGUF sha256; at startup the adapter checks every bound model (phrasing, nlu, judge, embedding) against the lock and runs a JSON-schema smoke call for `nlu` and `judge`, failing fast on any mismatch.

## Boundaries & Constraints

**Always:** LLM output is text only; `LlmResult(text, modelTag, digest, decoding, promptTokens, truncated, latency)`; `truncated` is true when Ollama stops for length or the prompt fills `numCtx`; schema passed as Ollama `format`; `stream: false`; embeddings with `truncate: false`; Ollama URL from `APP_OLLAMA_URL`; the adapter is the only reader of `AgentConfiguration` outside `RequestContextService` (ArchUnit allow-list extended, documented).

**Never:** no Spring AI; no exposure filter, prompt budget or prompt files (later epics); no model-specific code paths; no streaming; no automatic model pull by the app.

**Decisions (2026-10-21):** startup checks run on every app start as AD-18 says, although on this CPU-only dev PC the smoke calls add roughly 30–60 s; tests switch them off with `app.llm.verify-on-startup=false` (test configuration only). The models are pulled once into the compose `ollama` volume (~6 GB). As agreed for 1.5–1.7, the checkpoint is approved and review is deferred to the joint review after 1.7.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Generate | role `phrasing`, messages, decoding | POST `/api/chat` with `model` = `llm.phrasing`, options from decoding; `LlmResult` with tag, lock digest, token count, latency | N/A |
| Schema | `schema` given | request `format` = schema | N/A |
| Truncated | `done_reason: length` or `prompt_eval_count >= numCtx` | `truncated = true` | N/A |
| Config swap | two configs differing only in `llm.phrasing` | requests use the respective tag, no code change | N/A |
| Embed | texts | POST `/api/embed` with `llm.embedding`, `truncate: false`; vectors + tag + digest | HTTP error propagates |
| Lock mismatch | bound tag missing in lock or `/api/tags` digest differs | startup stops naming tag, expected and actual digest | fail fast |
| Smoke fail | `nlu`/`judge` output not a JSON object with boolean `ok` | startup stops naming the role and model | fail fast |
| Ollama down | no answer at startup | startup stops naming the URL | fail fast |

</frozen-after-approval>

## Code Map

- `backend/src/main/java/de/financetool/llm/package-info.java` -- module, depends only on `shared`; empty otherwise.
- `backend/src/main/java/de/financetool/shared/config/AgentConfiguration.java` -- `Llm(phrasing, nlu, judge, embedding)`, `Decoding(temperature, topP, seed, numCtx, numPredict)`; reuse `Decoding` in the port.
- `backend/src/test/java/de/financetool/ArchitectureRules.java:75` -- `CONFIG_ONLY_THROUGH_REQUEST_CONTEXT`; add the llm adapter class to the allow-list.
- `backend/src/main/java/de/financetool/shared/config/ConfigFingerprints.java:34` -- already fingerprints `models/lock.json` once it exists.
- `deploy/docker-compose.yml` -- `ollama` service (no published port, volume `ollama-data`), app `depends_on: ollama: service_started`; add an Ollama healthcheck so the app waits for it.
- `backend/src/test/java/de/financetool/PostgresIntegrationTest.java`, `src/test/resources/application-webtest.yaml` -- switch the startup checks off.
- `experiments/finlab/llm.py` -- reference client (options mapping, digest lookup by `name`/`model`, `truncate: false`); copy behaviour, do not import.

## Tasks & Acceptance

**Execution:**
- [x] `llm/` API -- `LlmRole` (`PHRASING, NLU, JUDGE`), `LlmMessage(role, content)` with `system|user|assistant`, `LlmResult`, `Embeddings(vectors, modelTag, digest)`, `LlmPort` -- AC 1.
- [x] `llm/adapter/OllamaClient.java` -- `RestClient` calls for chat, embed, tags (timeouts configured) -- AC 1.
- [x] `llm/adapter/ModelLock.java` + `OllamaLlmAdapter.java` -- read `models/lock.json` via `AppHome`, resolve tags per role, verify lock and run the smoke calls in an `ApplicationRunner` when `app.llm.verify-on-startup` is true -- AC 2, 3.
- [x] `models/` -- `models/README.md` (tags, how to pull and lock), `models/update-lock.sh` (pulls the tags and writes `lock.json` from `/api/tags` and the manifest's model layer), `models/lock.json` generated from the pulled models -- AC 2.
- [x] `application.yaml`, compose -- `app.ollama.url: ${APP_OLLAMA_URL:http://localhost:11434}`, `app.llm.verify-on-startup: true`; Ollama healthcheck + `service_healthy` -- AC 2.
- [x] Tests -- `OllamaLlmAdapterTest` with `MockRestServiceServer` (matrix rows: generate, schema, truncated, config swap, embed, lock mismatch, smoke fail, Ollama down); ArchUnit allow-list; checks off in DB/webtest tests -- matrix.

**Acceptance Criteria:**
- Given the port, when it is called, then the adapter resolves the tag from the active configuration and returns `LlmResult` with tag, digest, decoding, prompt tokens, truncated and latency.
- Given `models/lock.json`, when the app starts against the compose Ollama, then all bound models verify and the `nlu`/`judge` smoke calls return valid JSON, else startup stops.
- Given two configurations differing only in the phrasing tag, when each is active, then phrasing calls use its tag without code change, and tag and digest are in the result for the Audit Record.

## Implementation Notes

- Timing: start 2026-10-21T18:40+02:00.
- Implemented directly in this session (fast path). `LlmMessage(author, content)`: `author` instead of `role` to avoid confusion with `LlmRole`.
- Spring Boot 4 offers no `RestClient.Builder` bean without the restclient starter; the adapter builds its own `RestClient` (JDK HTTP client, 5 s connect, 10 min read for CPU inference).
- `OllamaLlmAdapter` is allow-listed in `CONFIG_ONLY_THROUGH_REQUEST_CONTEXT` (comment in `ArchitectureRules`). The digest in `LlmResult` comes from the lock (verified at startup).
- Startup check is an `ApplicationRunner` (after context refresh, before readiness); `app.llm.verify-on-startup` is `true` in `application.yaml` and `false` only in `application-webtest.yaml` and `PostgresIntegrationTest`.
- `models/tags.txt` lists the tags; `models/update-lock.sh` pulls them through `docker compose exec ollama` and writes `lock.json` (digest from `/api/tags` via a pinned `curlimages/curl:8.11.1` container on the compose network, since the Ollama image has no HTTP client; ggufSha256 = the manifest's model layer). Found and fixed while locking: `docker compose exec` swallowed the loop's stdin, and Git Bash rewrote container paths (`MSYS_NO_PATHCONV=1`). Compose `ollama` got a healthcheck (`ollama list`); `app` waits for `service_healthy`.
- Verified: `./mvnw -B verify -Dfine.it.required=true` BUILD SUCCESS, 111 tests, 0 failures, 0 skipped (`OllamaLlmAdapterTest` 11).
- Verified with Docker Compose (CPU only, Ryzen 5 1600): models pulled and locked (`llama3.1:8b-instruct-q4_K_M`, `bge-m3`); app log "Models verified against models/lock.json and smoke calls passed in 55689 ms"; `/actuator/health` UP after the checks.
- Timing: end 2026-10-21T19:05+02:00.

## Spec Change Log

## Review Triage Log

- Review layers not run: the user will review Stories 1.5–1.7 together after 1.7.

## Design Notes

- The `/api/tags` digest identifies the tag's manifest, and the manifest pins the GGUF layer by sha256, so checking the tag digest also pins the weights; `ggufSha256` is recorded in the lock for traceability (read from the manifest when locking). It is not the sha256 of the manifest file bytes (checked), so the lock script reads `/api/tags`.
- Smoke schema: `{"type":"object","properties":{"ok":{"type":"boolean"}},"required":["ok"]}`, prompt "Answer with the JSON object {\"ok\": true}.", `numPredict` 32, the configured temperature and seed.

## Verification

**Commands:**
- `cd backend && ./mvnw -B verify -Dfine.it.required=true` -- expected: BUILD SUCCESS, 0 skipped.
- `docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build` -- expected: log shows lock verified and smoke calls passed; `/actuator/health` UP.
