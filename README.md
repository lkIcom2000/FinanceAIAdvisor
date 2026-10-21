# FinanceAIAdvisor — "Fine"

Research prototype of a hybrid financial advisory agent for retail customers (master's thesis, Design Science Research).
Spring Boot 4.1 modular monolith (Spring Modulith) with an Angular 22 frontend.

Planning artifacts live in `_bmad-output/planning-artifacts/` (PRD, architecture spine, UX, epics);
sprint tracking in `_bmad-output/implementation-artifacts/sprint-status.yaml`.

## Layout

| Path | Contents |
|---|---|
| `backend/` | Maven project (wrapper committed), Java 25, Spring Boot 4.1.1, modules under `de.financetool.<module>` |
| `frontend/` | Angular 22 workspace; API client generated from `frontend/openapi/openapi.json` |
| `config/`, `data/`, `models/` | Versioned configuration, reference data and model pins (LF, read from `APP_HOME`) |
| `experiments/` | Colab experiment track (E1–E4); results enter the app only as configuration |

## Build

Requires JDK 25 and Node.js 24.15+.

```sh
cd backend && ./mvnw verify      # module boundaries (Spring Modulith), ArchUnit rules, OpenAPI snapshot
cd frontend && npm ci && npm run build   # fails if the generated API client is stale
```

After an intended API change:

```sh
cd backend && ./mvnw test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true
cd frontend && npm run api:generate
```
