# Forged Idea: Autonomous Hybrid Finance Agent

Master's thesis, Design Science Research. All artifacts in English.

## Research questions
- **Main RQ:** How can a compliant, responsible and secure AI be implemented to advise C-class customers in financial decisions?
- **Sub-RQ:** Under which conditions may such an agent act autonomously, and how can these conditions be enforced deterministically?

## Locked decisions
- **Target user (C-class customer):** mass-market retail customer (approx. < €3.5k net/month, < €50k liquid assets) who gets no meaningful personal advice; low financial literacy, potentially vulnerable.
- **"Advise" = real advice (variant B):** assumes a licensed operator (bank/savings bank). Contribution = compliance-by-design: regulatory duties (suitability assessment + statement, cost transparency, documentation, conflicts of interest, IDD needs assessment) implemented as deterministic guardrails; the LLM only phrases within them.
- **Core claim** (replaces "guarantees absolute accuracy"): (1) every number and product recommendation comes only from deterministic, auditable modules; (2) forecasts are shown as ranges with explicit assumptions; (3) every recommendation provably passes regulatory checks before reaching the user. The hybrid guarantees auditability, not truth.
- **Three pillars:** Compliant (full check chain per recommendation) · Responsible (explanation per recommendation, visible uncertainty, no commission incentive, autonomy gate, easy revocation) · Secure (data minimisation towards the LLM, prompt-injection defence, tool whitelist, GDPR hosting; swappable LLM layer).
- **Autonomy = mandate, not discretion:** the user pre-commits to a rule in a calm moment (behavioural-finance rationale: defaults, Save More Tomorrow); the agent acts autonomously only inside it.
- **Deterministic autonomy gate** per action type. Criteria: reversibility, amount relative to wealth, regulatory class, data confidence, mandate present. Levels: L0 inform, L1 recommend, L2 execute after confirmation, L3 execute within mandate + notify + revoke, L4 fully autonomous. **MVP:** L0–L2 complete, L3 for a few reversible low-risk actions. Raising autonomy later = configuration.
- **Triggers:** life/contract/regulatory events and rule-based rebalancing (e.g. ±5 pp drift). "Market developments" = changes in rules, subsidies, rates, never price moves (no market timing).
- **Journeys:** J1 proactive monitoring loop · J2 what-if scenario simulation · J3 financial Q&A (general explanations on any topic if source-backed; personal recommendations only in scope; out of scope = transparent refusal, itself a guardrail).
- **Product scope (shared domain layer, all journeys):** (1) liquidity & emergency fund (current account, overnight deposit; cash-flow forecast, shortfall detection); (2) wealth building & retirement (low-cost diversified ETF savings plans, state subsidies; MiFID II/WpHG; Monte Carlo ranges, subsidy calculator); (3) protection, needs analysis only (liability, disability, duplicate policies; IDD; no product selection). Two regulatory regimes → generalisable guardrails.
- **Data:** 5–10 synthetic personas, 12–24 months of transactions, contracts and holdings, with planted ground truth (shortfall, duplicate insurance, salary jump); access through a PSD2/FinTS-style adapter; historical index data for Monte Carlo.
- **Knowledge:** curated, versioned RAG with validity dates and cited sources; hard rules (subsidy amounts, allowances, deadlines) also as versioned parameters in the computation core. RAG explains, the core computes.
- **Compliance stack:** layered ablation A (prompt) → A+B (+rule engine) → A+B+C (+LLM-as-judge). The judge may only block or escalate, never approve; approval comes only from the rule engine. Measure the "cost of caution" (false-positive blocks).
- **Escalation:** blocked cases go to a human bank advisor through an advisor-assistant view (question, intended answer, block reason).
- **DSR split:** design *principles* (deterministic core, autonomy gate, block-only judge, data minimisation) are justified normatively and *validated* by the experiments; design *parameters* are decided empirically.

## Experiments
- **E1 (model/knowledge):** Llama 3.x vs. Llama 3.x + RAG vs. fin-chat (Llama-2-based) vs. Llama 2 as control. Isolates domain adaptation (fin-chat vs. Llama 2), model generation (Llama 2 vs. 3) and knowledge access (± RAG). No own fine-tuning.
- **E2 (role prompt):** plain vs. "licensed advisor with suitability duty for end customers". The advisor-assistant variant is evaluated separately as the escalation interface.
- **E3 (compliance):** layered ablation as above.
- **E4 (data minimisation):** raw → pseudonymised → aggregated features only → no personal data (LLM sees tool results only).
- **Evaluation:** hybrid vs. pure-LLM baseline. Metrics: number mismatch text vs. core, guardrail violations, rule/subsidy hallucinations, check-chain completeness, red-team defence rate, comprehensibility.
- **Test set (`sampleQuestions.csv`):** rewrite the Compliance_Advisory gold answers to variant B (profile missing → ask/run suitability; profile present + in scope → reasoned category recommendation; out of scope/speculative → transparent refusal). Keep Hallucination_Trap. Add in-scope C-customer questions (emergency fund, subsidies, disability/insurance needs).

## Rejected
- "Guarantees absolute accuracy": forecasts are uncertain, and the LLM can introduce errors at its input and output edges.
- Information-only (variant A): it does not answer the RQ.
- Product brokerage funnel (Check24), crypto, building-society savings, single bonds, further insurance products: out of the MVP, mentioned as outlook.
- Market-driven reallocation: market timing contradicts the passive ETF approach and harms vulnerable users.
- Real bank data or a PSD2 aggregator: licence, cost, GDPR, and no ground truth.
- Own fine-tuning: a separate project, not the thesis contribution.
- LLM-as-judge with approval power: breaks deterministic enforcement.

## Open, carry into PRD/architecture
- Concrete list of L3 mandate actions.
- Test set rewrite and in-scope extension; IDD/protection currently untested.
- How "comprehensibility" is measured (user study? participants?); bias if LLM-as-judge is also used for scoring.
- English UI for a German-market product: threat to validity.
