# Adversarial Review (Java retarget): Architecture Spine, FinanceTool 2026-10-20

**Lens:** build two units one level down (epics or stories, each built by a different coding agent). Each unit follows every AD exactly, yet the two still cannot work together. Every such pair marks a hole in the spine.
**Scope:** only holes that are new since AD-21..AD-24. Most come from the Spring Boot 4.1, Spring Modulith 2.1 and Angular 22 retarget, or from the scope split (AD-25).
**Inputs:** `ARCHITECTURE-SPINE.md` (Java version), `prd.md`. The earlier pass is `review-adversarial.md`, H1–H12.

## Verdict

**Not yet safe to break into epics. Five tightenings are needed before story writing, and a few more are worth adding.**

The domain rules remain strong: AD-2, AD-3, AD-6, AD-10 and AD-23/24 survived the retarget. The new holes are at the *seams the Java stack introduced*. Five points are unclear:

- what Spring Modulith counts as a module's API;
- where ports live when the consumer and the implementer are different modules;
- which modules may depend on which;
- which Spring/JPA idioms silently create a second writer or a second transaction;
- what the OpenAPI document actually says about money, sealed types and error codes.

Every finding below needs only a short addition to an existing AD, or one new AD. None needs per-story detail.

Severity: **High** means the units fail to integrate, or Modulith `verify()` / ArchUnit fails as soon as both are merged. **Medium** means they integrate but produce wrong or untraceable results.

---

## J1. Ports and module APIs sit in sub-packages that Modulith treats as internal (High)

**Pair.**
- *Unit A (`llm` epic)* follows the Layer table literally. It puts `LlmPort` in `de.financetool.llm.port` and `OllamaAdapter` in `llm.adapter`.
- *Unit B (`advisory` epic)* imports `llm.port.LlmPort`. Spring Modulith treats every sub-package of a module as internal unless it is a `@NamedInterface`, so `verify()` fails. If Unit B instead follows textbook hexagonal style, it declares its own consumer-side `advisory.port.LlmPort`, and the `llm` adapter must then depend on `advisory`. The result is two `LlmPort` interfaces and a reversed dependency.

The same split appears for:
- `BankPort` (consumed by `actions`, implemented by `bank`);
- `KnowledgePort` and `RuleParamPort` (consumed by `advisory` and `core`, implemented by `knowledge`);
- every "owner's exposed service" from AD-12, such as `ProfileService.markStale`, `ChatService.appendAdvisorMessage` and `ActionService.propose`. These classes sit in `<module>.application`, which is also internal by default.

**Tightening (add to AD-12):**
- *Module API.* A module's API is exactly the types in its base package `de.financetool.<module>`. Sub-packages are internal. Do not use `@NamedInterface`.
- *Cross-module ports.* A cross-module port is declared in the **providing** module's base package. For example, `llm.LlmPort` is implemented in `llm.adapter`, and `bank.BankPort` in `bank.adapter`. `<module>.port` holds only ports that the module both declares and implements itself, such as repositories and `ClockPort`.
- *Pure Java.* API types are interfaces and records only, with no Spring annotations, so a consuming `..domain..` can still be pure.

## J2. No declared module dependency graph, so cycles appear and the natural fix breaks AD-7 (High)

**Pair.** These dependency pairs are each correct per AD-12 but form cycles that Modulith rejects:

| Cycle | Edge one way | Edge the other way |
|---|---|---|
| `advisory` ↔ `escalation` | the orchestrator opens an `EscalationCase` on `ESCALATE` | `escalation.reply()` calls `ChatService.appendAdvisorMessage` (AD-12 note) |
| `personas` ↔ every owner | `PersonaResetService` calls each owner's `reset` | each owner's entry point reads persona state to build `RequestContext` (AD-22) |
| `monitoring` ↔ `actions` | monitoring calls `propose()` | the Notification status is "derived from the Action state", and an agent may implement this by having `actions` update notifications |

An agent that removes a cycle will reach for the idiomatic Modulith tool, `@ApplicationModuleListener`. That listener is asynchronous, runs after commit and uses `REQUIRES_NEW`. The advisor reply or the reset of a module then commits separately: AD-7 and AD-21 are broken, and `MANDATORY` throws inside the listener.

**Tightening (new short AD, or a table added to AD-12):**
- *Allowed DAG.* Declare the allowed dependencies, e.g. `shared ← personas ← {bank, knowledge, llm, audit, profile} ← core ← {actions, escalation} ← {monitoring, advisory}`. Enforce it with `ApplicationModules.verify()`.
- *Breaking cycles.* Every cycle is removed with a synchronous, in-transaction Spring `@EventListener` on an event record in the publisher's API. Examples are `AdvisorReplyPosted` and `PersonaResetRequested`. `@ApplicationModuleListener`, `@TransactionalEventListener`, `@Async` and the event-publication registry are forbidden.

## J3. "Use-case service" and "module service" are not separate classes, and AD-16 contradicts AD-21 (High)

**Pair 1.**
- *Unit A (actions web)* has `ActionsController.confirm` call `ActionService.confirm` directly. The method is a use case called by a controller, so per AD-21 it uses `@Transactional(REQUIRED)`.
- *Unit B (actions domain)* applies AD-21 rule 2: every module service method that writes is `MANDATORY`. It marks all four `ActionService` methods `MANDATORY`, because `propose` is called by monitoring.
- The controller call then fails at runtime. AD-10 names one class with four entry points, which straddle both categories.

**Pair 2.**
- *Unit C (monitoring)* follows AD-16: "per Trigger, in one transaction", using `REQUIRES_NEW` per Trigger or a `TransactionTemplate`.
- *Unit D (study preparation, FR-31)* follows AD-21: each use case runs in exactly one transaction. It wraps reset, date change and `MonitoringService.run` in one transaction.
- Unit C's per-Trigger commits then either fail the `MANDATORY` contract or escape the outer transaction.

**Pair 3.**
- *Unit E (acceptance harness)* marks test classes `@Transactional`, the default Spring test idiom, so rows roll back between items.
- `MANDATORY` is then satisfied by the *test's* transaction. Tests pass even when a controller path has no use-case transaction. This defeats AD-15 ("same code path as users get").
- AD-13 triggers also reject TRUNCATE on audit tables, so the harness cannot clean them. The per-suite export in `target/verification/` then mixes rows from earlier runs.

**Tightening (add to AD-21):**
- *Two kinds of class.* Use cases are separate `*UseCase` classes in `<module>.application` with `REQUIRED`. Module API services (J1) are `MANDATORY`. A controller, startup task or test calls only `*UseCase` classes. `ActionService` keeps its four methods as `MANDATORY`, and `ConfirmActionUseCase` and the other use cases wrap them.
- *Monitoring.* One monitoring run is one transaction. "Per Trigger" in AD-16 means "atomically within the run". Idempotency keys make a re-run after a rollback safe.
- *Acceptance tests.* Acceptance tests are never `@Transactional`. Each suite run sets a `runId`, which goes into `RequestContext` and each Audit Record. The export filters by `runId`.

## J4. JPA entities and associations crossing module boundaries create a hidden second writer (High)

**Pair 1.**
- *Unit A (profile)* exposes `ProfileService.find(scope)` and returns the managed `SuitabilityProfile` entity.
- *Unit B (advisory, assessment flow)* calls it inside the turn's single transaction (AD-21) and sets a field on the returned object.
- Hibernate dirty-checking flushes the change at commit. `advisory` has now written `suitability_profiles` without going through the confirm control (AD-24). Modulith `verify()` does not see this, because no repository was touched.
- One transaction per use case makes this *more* likely, not less.

**Pair 2.**
- *Unit C (monitoring)* maps `Notification.action` as `@ManyToOne Action`, following the ER diagram.
- *Unit D (actions)* maps `actionId` as a UUID.
- `PersonaResetService` calls owners' `reset` in whatever order it likes. Under Unit C's mapping, a DB FK from `notifications` to `actions` makes `actions.reset` fail if it runs first. The ER diagram also has FKs `MESSAGE ↔ ESCALATION_CASE` in both directions, across two modules.

**Tightening (add to AD-12):**
- *No entities across modules.* Module APIs never return or accept JPA entities. They exchange immutable records.
- *References by id.* A cross-module reference is an id value (a typed id record from `shared`). No JPA association and no DB foreign key crosses a module boundary.
- *Reset order.* `PersonaResetService` runs resets in reverse DAG order (J2).

## J5. `evidence_items`: `core` writes a table owned by `audit`, and `provId` ignores the persona (High)

**Pair 1 (two owners).**
- *Unit A (core)* implements AD-2: "`CoreGateway` persists each item … in `evidence_items`". It adds an `EvidenceItemRepository` in `core.adapter`.
- *Unit B (audit)* implements AD-12 and AD-13: audit is the only writer of `evidence_items`, and the insert is `putIfAbsent`.
- Modulith rejects Unit A's version. If Unit A instead writes through raw JDBC, the table has two writers that disagree about the insert-if-absent semantics: `ON CONFLICT DO NOTHING` against check-then-insert, which races under the append-only trigger.
- The spine also does not say who owns `/api/provenance/{provId}`.

**Pair 2 (persona isolation).**
- *Unit B (audit)* follows AD-11: persona-scoped repository methods require a `PersonaScope`. It adds `personaId` to `evidence_items` and scopes the provenance read.
- *Unit A (core)* computes `provId` from function, inputs, `asOf`, snapshots and output key, with no persona (AD-2).
- Two personas with identical inputs produce the same `provId`; seeded fixtures and the `p-harness` items make this likely. The row keeps the first persona, so persona B's "Why?" returns 404, or B reads A's row.

**Tightening:**
- *AD-2.* `CoreGateway` calls `audit.EvidenceStore.putIfAbsent(items)` (`MANDATORY`, `INSERT … ON CONFLICT DO NOTHING`), and audit is the only writer. `/api/provenance/{provId}` lives in `audit.web`.
- *AD-2, figure key.* Add `personaId` to the inputs of the `provId` hash, so a figure key is unique per persona and the read can be persona-scoped.

## J6. Shared value types and `RequestContext` live outside `..domain..`, so pure domain code cannot use them (High)

**Pair.**
- *Unit A (core.domain)* returns `CoreResult` and `List<SourcedNumber>` using `Money` and `Unit` from `shared` (source tree: "`shared/` … ids, money"). ArchUnit AD-1 then fails, because `shared` is not `..domain..`.
- *Unit B (advisory.domain)* complies by defining its own `SourcedNumber` and `Unit`. Now two types exist, and the Number Check compares objects that never match.
- The spine never says where `CoreResult`, `SourcedNumber`, `Unit`, `Evidence`, `CheckResult` or `SimulationSpec` live.
- `RequestContext` carries `agentConfig`. If that field is the `@ConfigurationProperties` bean (Jakarta Validation annotations), then no domain function may take a `RequestContext`. Some agents will pass it into domain code anyway.
- `shared` must also be declared a Modulith *shared module*, or every module's dependency on it counts as an explicit dependency that has to be listed.

**Tightening (add to AD-1 and AD-22):**
- *Shared value types.* Cross-module value types live in `de.financetool.shared.domain`. This covers ids, `Money`, `Unit`, `SourcedNumber`, `CoreResult`, `Evidence`, `CheckResult`, `SimulationSpec` and `FinancialSnapshot` (J7). The package counts as `..domain..`.
- *RequestContext.* `RequestContext` lives in `shared` and holds only plain records: an `AgentConfigSnapshot`, never the Spring bean.
- *Domain inputs.* Domain functions receive unpacked values, never `RequestContext`.
- *Modulith.* Declare `shared` with `@Modulithic(sharedModules = "shared")`.

## J7. Bank reads and derived financial measures have no single owner (Medium-High)

**Pair 1 (two implementations of one measure).**
- *Unit A (actions)* implements `DataConfidence.of(transactions, asOf)` and `Liquidity.liquidAssets(...)` in `actions.domain`, as AD-10 says. It reads transactions through `BankPort`, the only port named for `bank`.
- *Unit B (core / Overview)* computes emergency-fund coverage, and with it "liquid assets", in `core.domain`. It reads `bank` through some other API.
- The Overview and the gate's "5% of liquid assets" (FR-35) now use two definitions that disagree. FR-39 then shows a cap reason whose number does not match the panel.

**Pair 2 (figures outside CoreGateway).**
- *Unit C (monitoring)* writes `TriggerDetection.detect(snapshot, asOf, rules)` and computes the forecast shortfall and the drift in pp inside `monitoring.domain`.
- Those figures did not come from `CoreGateway`, so they have no `provId`. The Notification cannot show "figures with their provenance" (FR-32), and they can differ from the Core's cash-flow forecast.

**Pair 3 (unfiltered dates).**
- `bank` returns all fixture transactions, including planted events dated after `simulatedDate`.
- One consumer filters by `asOf` and another does not, so a Trigger fires "early" in one path only.
- AD-10 also gives no thresholds argument to `DataConfidence.of`, so one agent hard-codes 95% / 5 days.

**Tightening (add to AD-10 and AD-12):**
- *One read path.* `bank` exposes one read API, `BankReadPort.snapshot(PersonaScope, asOf) → FinancialSnapshot`, which is already filtered to dates on or before `asOf`. `BankPort` is mutations only.
- *One home for measures.* `DataConfidence`, `Liquidity` and the forecast functions live only in `core.domain` and take thresholds as arguments.
- *Figures via CoreGateway.* Any figure shown to the user, or used as an Action amount, comes from a `CoreGateway` call. `TriggerDetection` consumes `CoreResult`s and does not compute figures itself.

## J8. `ProfileCompleteness` has two homes, and the gate's suitability input is missing (Medium-High)

**Pair.**
- *Unit A (advisory)* puts `ProfileCompleteness` in `advisory.domain`, as AD-4 says ("Pure rules in `advisory.domain` … `ProfileCompleteness.evaluate`").
- *Unit B (profile)* puts it in `profile.domain`, as the source tree says ("`profile/` … completeness").
- *Unit C (actions)* needs "investment without a complete, confirmed Suitability Profile → L1" (FR-35). The gate-context list in AD-10 omits this input, so the unit either depends on `advisory` (wrong direction under J2) or writes a third implementation.

**Tightening:**
- *AD-4.* `ProfileCompleteness` lives in `profile.domain` and is exposed through the `profile` API. Classification in `advisory.domain` receives its result as an argument.
- *AD-10.* Add `suitabilityComplete` (stored profile only, no `StatedProfile`) to the gate context.

## J9. The wire format is not pinned in the OpenAPI document (Medium-High)

**Pair 1 (money).**
- *Unit A (backend)* follows the Money convention and serialises `BigDecimal` as the string `"1234.50"`, plus `currency`.
- springdoc documents `BigDecimal` as `type: number` by default. `ng-openapi-gen` therefore types it as `number`.
- *Unit B (frontend)* trusts the generated type, as AD-19 requires. Angular then receives a string typed as `number`, and `+`, sorting and ECharts break, or a developer adds `parseFloat`.

**Pair 2 (sealed types).** `DialogState`, outcomes and other sealed interfaces serialise through Jackson without a discriminator unless one is annotated. The generated TS type is a union the frontend cannot tell apart, so the `ASSESSMENT` and `PARAMETER_PROPOSAL` cards cannot be distinguished.

**Pair 3 (error codes).**
- Spring's `ProblemDetail` puts `code` into a free-form `properties` map. The OpenAPI document has no enum for it, so the generated client has nothing to switch on.
- The frontend unit hand-writes `enum ProblemCode`, which AD-19 forbids, or matches on strings.

**Pair 4 (compliance level).** The compliance level enum is `A_B` in Java and `A+B` on the wire. The generated TS enum follows whatever springdoc emits, which may not be what Jackson produces.

**Tightening (add to AD-19):**
- *Explicit schemas.* The OpenAPI document is the contract, and these schemas are registered explicitly:
  - `Money {amount: string (format decimal), currency: "EUR"}`;
  - a `Problem` schema extending RFC 9457, with `code` as an OpenAPI enum generated from the Java `ProblemCode` enum;
  - every sealed type on the wire, with `@JsonTypeInfo(use = NAME, property = "kind")` and a `oneOf` discriminator;
  - enums with `@JsonValue`, documented with their wire values.
- *CI check.* CI regenerates the client and fails on a diff.

## J10. AD-25 config files are not fingerprinted consistently with AD-14 (Medium)

**Pair 1 (missing hashes).**
- *Unit A (config loader)* hashes `config/agent/<id>.yaml` (AD-14). It binds `autonomy.yaml` and `profile-minimums.yaml` as separate `@ConfigurationProperties`.
- AD-13 records the config hash, the threshold hash and the prompt hashes. Nothing hashes `profile-minimums.yaml`, and nothing records a `models/lock.json` hash.
- An AD-25 change to profile minimums therefore changes outcomes on items 31–39 with no trace in the Audit Record. That is exactly what AD-25 exists to prevent.

**Pair 2 (what is hashed).**
- *Unit B* hashes the raw file bytes.
- *Unit C* hashes the re-serialised bound object. That drops the `derivedFrom` block, which binding ignores as an unknown field, and depends on key order.
- The same file then gets different hashes.

**Pair 3 (line endings).** The author works on Windows. With `core.autocrlf`, files checked out there have CRLF and the image built from them has LF, or the other way round. The KB snapshot hash (AD-9) and the config hashes then differ between an IDE run and the container, and AD-9 refuses to start against a DB ingested by the other run.

**Pair 4 (rule snapshot).** `ruleSnapshot` appears in `RequestContext` and AD-13, but AD-9 defines only the KB snapshot id. One unit hashes the files, and another uses a list of per-key versions.

**Tightening (add to AD-14):**
- *ConfigFingerprint.* At startup a `ConfigFingerprint` is computed: SHA-256 over the raw, LF-normalised bytes of each AD-25 file (including `derivedFrom`) and of `data/rules/*.yaml`.
- *Recording.* It is stored in `RequestContext` and recorded per file in every Audit Record.
- *Rule snapshot.* The rule snapshot id is the hash of the `data/rules` part.
- *Line endings.* Add `.gitattributes` with `* text=auto eol=lf` for `config/`, `data/` and `models/`.

## J11. Where `config/`, `data/` and `models/` are found at runtime (Medium)

**Pair.**
- *Unit A (deploy)* copies `config/`, `data/` and `models/` to `/app/...` in the image.
- *Unit B (backend)* loads them from the classpath or from the working directory, so IDE runs work on the local PC.
- AD-17 allows only five environment-variable differences, and none of them is a path.

**Tightening (add to AD-17):**
- *Fixed paths.* The three roots resolve from one property, `app.home`. It defaults to `/app` in the image and is set by a local Spring profile `local` for IDE runs.
- *Source of truth.* The files are never packaged on the classpath.

## J12. The source of `/api/simulate` evidence and its `RequestContext` (Low-Medium)

**Pair.**
- FR-34 parameter adjustment calls `/api/simulate` outside any chat turn. Unit A builds its `RequestContext` with the *agent config's* seeds.
- The chat what-if path, after the proposal is confirmed, builds its context from the turn's seeds.
- The same `SimulationSpec` then gives different p10/p90 values and different `provId`s, so the FR-8 highlight fails.

**Tightening (add to AD-2, what-if spec):** the seed for a stochastic Core call is part of `SimulationSpec`. It is set once, when the spec is first created, and travels with the spec, never taken from context.

---

## Checked and found tight enough

- The flags table (AD-3) and the single `ComponentFlags` lookup are not affected by the retarget.
- `LlmPort` as the only model boundary (AD-5) stays tight once J1 fixes where the port lives.
- The Action state machine and undo semantics (AD-10) hold, given J3's use-case wrappers.
- The append-only audit role and triggers (AD-13) are correct as written. J3 only adds `runId` for test isolation.
- AD-25's route into the app ("only by editing these files") is sound. J10 only makes the edits traceable.

## Smallest patch set

| Finding | Where | Size |
|---|---|---|
| J1 | AD-12: one paragraph on API = base package, providing-module ports | 3 lines |
| J2 | AD-12: dependency DAG + synchronous in-transaction events only | 4 lines |
| J3 | AD-21: `*UseCase` vs `MANDATORY` API; monitoring run = one transaction; tests not `@Transactional`, `runId` | 4 lines |
| J4 | AD-12: no entities or FKs across modules; reset order | 2 lines |
| J5 | AD-2: `EvidenceStore` in `audit`; `personaId` in the `provId` hash | 2 lines |
| J6 | AD-1/AD-22: `shared.domain`; `RequestContext` holds plain records; shared module | 3 lines |
| J7 | AD-10/AD-12: `BankReadPort.snapshot`; measures in `core.domain`; figures only via `CoreGateway` | 3 lines |
| J8 | AD-4/AD-10: `ProfileCompleteness` in `profile.domain`; `suitabilityComplete` in the gate context | 2 lines |
| J9 | AD-19: explicit Money, Problem and discriminator schemas; regenerate-diff CI check | 3 lines |
| J10 | AD-14: `ConfigFingerprint`; rule snapshot id; LF normalisation | 3 lines |
| J11 | AD-17: `app.home` | 1 line |
| J12 | AD-2: seed lives in `SimulationSpec` | 1 line |
