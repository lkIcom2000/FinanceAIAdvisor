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

## Run locally

Requires Docker with Compose v2. One image and one compose file serve every environment; environments differ only
in `SPRING_DATASOURCE_*`, `APP_OLLAMA_URL`, `APP_AGENT_CONFIG`, `SPRING_PROFILES_ACTIVE` and secrets.

```sh
cp deploy/.env.example deploy/.env     # set FINE_DB_PASSWORD and FINE_APP_DB_PASSWORD
docker compose -f deploy/docker-compose.yml up -d --build
curl -s localhost:8080/actuator/health # {"status":"UP"}
```

The app reads `config/`, `data/` and `models/` from `APP_HOME` (`/app` in the image, mounted read-only from the
repository) and refuses to start if one of them is missing.

Flyway migrates as the database owner (`FINE_DB_PASSWORD`); the app connects as the role `fine_app`
(`FINE_APP_DB_PASSWORD`), which may only insert into and read the append-only audit tables. Flyway sets
`fine_app`'s password on every start, so changing the secret needs only a restart.

### Demo accounts

All accounts use the password `fine-demo` (synthetic personas only; never use real data).

| Username | Role | Lands on |
|---|---|---|
| `p-lena`, `p-marco`, `p-jonas`, `p-petra`, `p-tim`, `p-shortfall`, `p-harness` | `PERSONA` | Workspace |
| `advisor-kaya` | `ADVISOR` | Swagger UI, advisor endpoints |
| `researcher` | `RESEARCHER` | Swagger UI, research endpoints (only with `SPRING_PROFILES_ACTIVE=research`) |

For frontend development, run the backend on port 8080 and `npm start` in `frontend/` (proxies `/api` and Swagger UI).

After an intended API change:

```sh
cd backend && ./mvnw test -Dtest=OpenApiSnapshotTest -Dopenapi.update=true
cd frontend && npm run api:generate
```
