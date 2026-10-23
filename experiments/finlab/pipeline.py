"""The advisory turn as a fixed pipeline (PRD §4.3, §4.7, §8.1; spine AD-3).

1 NLU extract -> 2 classify (rules) -> 3 tool plan + Core -> 4 KB retrieve -> 5 exposure filter +
prompt budget -> 6 LLM draft -> 7 shadow checks on draft -> 8 Rule Engine -> 9 Judge -> 10 block
routing -> 11 record + release.

What each compliance level gives to the model or enforces is defined ONLY in FLAGS (PRD §8.1).
"""

from __future__ import annotations

import datetime as dt
import functools
import json
import re
import time
from dataclasses import dataclass, field, asdict
from decimal import Decimal
from pathlib import Path
from typing import Any

import yaml

from . import checks as chk
from . import core, exposure, numbercheck, prompts
from .classify import Registry, classify
from .evidence import Evidence
from .llm import Decoding, Ollama
from .nlu import Nlu, ground
from .profile import effective_profile
from .rag import KnowledgeBase, Passage
from .rules import NoValidRuleParameter, RuleBook

# computed = run and recorded for measurement; given = passed to the LLM; enforce = Rule Engine blocks
FLAGS: dict[str, dict[str, Any]] = {
    "baseline": {"class_given": False, "core_given": False, "retrieval_allowed": False, "compliance_prompt": False,
                 "enforce": False, "routing": False, "judge": False},
    "A": {"class_given": True, "core_given": True, "retrieval_allowed": True, "compliance_prompt": True,
          "enforce": False, "routing": False, "judge": False},
    "A+B": {"class_given": True, "core_given": True, "retrieval_allowed": True, "compliance_prompt": True,
            "enforce": True, "routing": True, "judge": False},
    "A+B+C": {"class_given": True, "core_given": True, "retrieval_allowed": True, "compliance_prompt": True,
              "enforce": True, "routing": True, "judge": True},
}


@dataclass
class ExperimentConfig:
    id: str
    phrasing_model: str = "llama3.1:8b-instruct-q4_K_M"
    retrieval: bool = True
    role_prompt: str = "advisor"
    compliance: str = "A+B+C"
    data_exposure: str = "aggregated"
    nlu_model: str = "llama3.1:8b-instruct-q4_K_M"
    judge_model: str = "llama3.1:8b-instruct-q4_K_M"
    embed_model: str = "bge-m3"
    decoding: Decoding = field(default_factory=Decoding)
    retrieval_k: int = 4
    retrieval_min_score: float = 0.45

    def as_dict(self) -> dict[str, Any]:
        d = asdict(self)
        return d

    @classmethod
    def from_dict(cls, d: dict[str, Any]) -> "ExperimentConfig":
        d = dict(d)
        dec = d.pop("decoding", None)
        cfg = cls(**d)
        if dec:
            cfg.decoding = Decoding(**dec)
        return cfg


class Lab:
    """Everything a turn needs that is not part of the configuration."""

    def __init__(self, base_dir: str | Path, client: Ollama, nlu_model: str, results_dir: str | Path,
                 as_of: dt.date = dt.date(2026, 10, 20), kb: KnowledgeBase | None = None):
        base = Path(base_dir)
        self.base, self.client, self.as_of, self.kb = base, client, as_of, kb
        self.rules = RuleBook.load(base / "data/rules/rule_parameters.yaml")
        self.category_rules = yaml.safe_load((base / "data/rules/category_rules.yaml").read_text(encoding="utf-8"))
        self.registry_data = yaml.safe_load((base / "data/known_entities.yaml").read_text(encoding="utf-8"))
        self.personas = json.loads((base / "data/personas/personas.json").read_text(encoding="utf-8"))
        self.item_personas = {str(k): v for k, v in
                              (yaml.safe_load((base / "item_personas.yaml").read_text(encoding="utf-8")) or {}).items()}
        self.nlu = Nlu(client, nlu_model, Path(results_dir) / "cache" / "nlu")

    @functools.cached_property
    def registry(self) -> Registry:
        """Premise verification asks whether a law or product is real. That does not depend on whether an
        arm uses retrieval, so the registry always includes the Knowledge Base (same classes in every arm)."""
        if not self.kb:
            return Registry(self.registry_data)
        return Registry(self.registry_data, self.kb.titles, [p.text for p in self.kb.passages])


# --------------------------------------------------------------------------- tool plan
def _tool_plan(lab: Lab, n: dict[str, Any], persona: dict[str, Any] | None, as_of: dt.date) -> list[core.CoreResult]:
    """Computation tools decided from the NLU parameters (before classification)."""
    p, topic, out = n["computation_params"], n["topic"], []
    if topic == "withdrawal_duration" or {"portfolio", "monthly_withdrawal"} <= p.keys():
        if {"portfolio", "monthly_withdrawal", "annual_rate"} <= p.keys():
            out.append(core.withdrawal_duration(p["portfolio"], p["monthly_withdrawal"], p["annual_rate"]))
    if topic == "shortfall_check" or "due_amount" in p:
        if persona and persona.get("flows"):
            out.append(core.cash_flow_forecast(persona["flows"], as_of))
        elif {"net_income", "fixed_costs", "balance", "due_amount"} <= p.keys():
            out.append(core.simple_shortfall(p["net_income"], p["fixed_costs"], p["balance"], p["due_amount"],
                                             p.get("variable_spending")))
    if topic == "savings_projection" and {"monthly_contribution", "years"} <= p.keys():
        out.append(core.monte_carlo(p["monthly_contribution"], int(p["years"]), p.get("initial", 0), 42,
                                    lab.rules, as_of))
    return out


def _advice_tools(lab: Lab, cls: str, topic: str, prof: dict[str, Any], message: str,
                  as_of: dt.date) -> tuple[list[core.CoreResult], dict[str, Any] | None]:
    out, category = [], None
    inc, exp = prof.get("net_income"), prof.get("monthly_expenses")
    if cls == "RECOMMEND_CATEGORY":
        if inc is not None and exp is not None:
            out.append(core.monthly_surplus(inc, exp))
        if exp is not None:
            out.append(core.emergency_fund(exp, prof.get("emergency_fund"), lab.rules, as_of))
        sel = core.select_category(prof, topic, lab.rules, lab.category_rules, as_of)
        out.append(sel)
        category = sel.outputs
        if topic in ("asset_allocation", "savings_plan", "investment_decision", "diversification", "fund_selection",
                     "sustainability_fit", "retirement_strategy", "product_variant_tax"):
            amount = prof.get("amount") or (prof.get("requested_monthly_amount") or 0) * 12 or 10000
            out.append(core.cost_comparison(float(amount), 10, lab.rules, as_of))
        if topic == "protection_duplicate" and prof.get("existing_contracts"):
            out.append(core.duplicate_coverage(prof["existing_contracts"]))
    elif topic == "liquidity_management" and exp is not None:
        out.append(core.emergency_fund(exp, prof.get("emergency_fund"), lab.rules, as_of))
    elif topic == "cost_question":
        m = re.search(r"(\d+(?:[.,]\d+)?)\s?%\s*(front[- ]end|load|Ausgabeaufschlag)", message, re.IGNORECASE)
        load = float(m.group(1).replace(",", ".")) / 100 if m else None
        out.append(core.cost_comparison(10000.0, 10, lab.rules, as_of, front_load=load))
    return out, category


# --------------------------------------------------------------------------- prompt
def _system_prompt(cfg: ExperimentConfig, flags: dict[str, Any], cls: Any, category: dict[str, Any] | None) -> str:
    parts = [prompts.ROLE_PROMPTS[cfg.role_prompt]]
    if flags["compliance_prompt"]:
        parts.append(prompts.COMPLIANCE_INSTRUCTIONS)
    if flags["class_given"]:
        tmpl = prompts.CLASS_INSTRUCTIONS[cls.expected_class]
        conflicts = ""
        if category and category.get("conflicts"):
            conflicts = "The request conflicts with the profile (" + "; ".join(category["conflicts"]) + \
                        "): recommend against it and state the conflicting figures."
        parts.append(tmpl.format(
            missing=", ".join(cls.missing_fields) or "your goals, horizon, income and expenses, emergency fund, risk tolerance",
            category=(category or {}).get("category", "").replace("_", " "),
            conflicts=conflicts,
            oos=", ".join(cls.out_of_scope).replace("_", " ") or "this product",
            premises="; ".join(p.get("claim", "") for p in cls.unverified_premises)))
    return "\n\n".join(parts)


def _user_prompt(message: str, tool_text: str, passages: list[Passage], profile_ctx: dict[str, Any]) -> str:
    blocks = []
    if tool_text:
        blocks.append("TOOL RESULTS (deterministic calculations; use these numbers exactly):\n" + tool_text)
    if passages:
        blocks.append("SOURCES:\n" + "\n".join(f"[S{i}] {p.citation()}: {p.text}" for i, p in enumerate(passages, 1)))
    if profile_ctx:
        blocks.append("CUSTOMER PROFILE ON FILE:\n" + json.dumps(profile_ctx, ensure_ascii=False))
    blocks.append(f"<user_message>\n{message}\n</user_message>")
    return "\n\n".join(blocks)


def _fit_budget(system: str, message: str, tool_text: str, passages: list[Passage], profile_ctx: dict[str, Any],
                decoding: Decoding) -> tuple[str, list[Passage], bool]:
    """Drop KB passages (last first) until the prompt fits; never drop tool results or the system part."""
    budget = decoding.num_ctx - decoding.num_predict - 200
    kept, truncated = list(passages), False
    while True:
        user = _user_prompt(message, tool_text, kept, profile_ctx)
        if (len(system) + len(user)) / 3.2 <= budget or not kept:
            return user, kept, truncated
        kept.pop()
        truncated = True


# --------------------------------------------------------------------------- turn
def run_turn(lab: Lab, cfg: ExperimentConfig, item: dict[str, Any]) -> dict[str, Any]:
    t0 = time.time()
    flags = FLAGS[cfg.compliance]
    message = item["prompt_text"]
    persona_id = lab.item_personas.get(str(item["id"]))
    persona = lab.personas.get(persona_id) if persona_id else None
    as_of = dt.date.fromisoformat(persona["simulated_date"]) if persona else lab.as_of
    stored = (persona or {}).get("profile", {})

    # 1-2 NLU + classification (always computed, for measurement)
    n_raw = lab.nlu.extract(message)
    n, grounding = ground(n_raw, message)
    rule_error = None
    try:
        comp_tools = _tool_plan(lab, n, persona, as_of)
    except NoValidRuleParameter as e:
        comp_tools, rule_error = [], str(e)
    cls = classify(n, stored, lab.category_rules["profile_minimums"], lab.registry, computable=bool(comp_tools),
                   message=message)
    prof = effective_profile(stored, n["stated_profile"])

    # 3 Core tools
    ev = Evidence()
    for r in comp_tools:
        ev.add_core(r)
    category = None
    try:
        adv, category = _advice_tools(lab, cls.expected_class, n["topic"], prof, message, as_of)
        for r in adv:
            ev.add_core(r)
    except NoValidRuleParameter as e:
        rule_error = str(e)
    rule_values = [p.value for p in lab.rules.all_valid(as_of)]
    ev.add_raw(rule_values, "rule", "rule parameters valid at as-of date")
    user_numbers = numbercheck.numbers_in(message)
    ev.add_raw(user_numbers, "user", "numbers in the user's message")

    # 4 retrieval
    passages: list[Passage] = []
    retrieval_on = cfg.retrieval and flags["retrieval_allowed"] and lab.kb is not None
    if retrieval_on and cls.expected_class != "REFUSE_OUT_OF_SCOPE":
        query = n["depersonalized_question"] or message
        passages = lab.kb.search(lab.client, query, as_of, cfg.retrieval_k, cfg.retrieval_min_score)
    for p in passages:
        ev.add_raw(numbercheck.numbers_in(p.text), "kb", p.pid)

    # 5 exposure + prompt
    shown_msg = exposure.filter_message(message, cfg.data_exposure, n["depersonalized_question"])
    profile_ctx = exposure.filter_profile(stored, cfg.data_exposure, (persona or {}).get("name"))
    # band limits shown to the model (aggregated level) are sourced numbers, not inventions
    ev.add_raw(numbercheck.numbers_in(shown_msg + " " + json.dumps(profile_ctx, ensure_ascii=False)),
               "exposure", "values shown after the exposure filter")
    tool_text = prompts.format_tool_results([r.as_dict() for r in ev.core_results]) if flags["core_given"] else ""
    system = _system_prompt(cfg, flags, cls, category)
    user, passages_used, truncated = _fit_budget(system, shown_msg, tool_text, passages, profile_ctx, cfg.decoding)
    msgs = [{"role": "system", "content": system}, {"role": "user", "content": user}]

    # 6 draft
    draft_res = lab.client.chat(cfg.phrasing_model, msgs, cfg.decoding)
    draft = draft_res.text
    sourced = ev.values()

    def run_checks(text: str) -> list[chk.CheckResult]:
        return chk.run_checks(text, expected_class=cls.expected_class, user_message=message, sourced=sourced,
                              retrieval_on=retrieval_on, n_passages=len(passages_used),
                              profile_ok=cls.expected_class == "RECOMMEND_CATEGORY", category=category,
                              topic=n["topic"], oos=cls.out_of_scope)

    # 7 shadow checks on the first draft (all configurations)
    draft_checks = run_checks(draft)

    # 8-10 Rule Engine, Judge, block routing
    released, outcome, route, regenerated, judge = draft, None, "released", False, None
    final_checks = draft_checks
    if flags["enforce"]:
        if rule_error:
            released, outcome, route = prompts.TEMPLATES["ESCALATE"], "ESCALATE", "invalid_rule_parameter"
        else:
            fails = chk.failed(final_checks)
            # Repairable failures get one regeneration with a targeted instruction before routing.
            fixes = []
            for f in fails:
                if f.check_id == "NUMBER_MATCH":
                    fixes.append(f"Your draft used numbers that are not in TOOL RESULTS, SOURCES or the user's message: "
                                 f"{', '.join(m['text'] for m in f.details['mismatches'])}. Use only the numbers from "
                                 f"TOOL RESULTS, SOURCES or the user's message. Do not calculate.")
                elif f.check_id == "SOURCE_VALIDITY" and f.reason_code in ("missing_citation", "invalid_citation"):
                    fixes.append(f"Base the explanation on the SOURCES and cite each source you use as [S1] to "
                                 f"[S{len(passages_used)}]. If the SOURCES do not cover the question, say so.")
            if fixes:
                regenerated = True
                fix = " ".join(fixes) + " Rewrite the whole answer."
                regen = lab.client.chat(cfg.phrasing_model,
                                        msgs + [{"role": "assistant", "content": draft}, {"role": "user", "content": fix}],
                                        cfg.decoding)
                released = regen.text
                final_checks = run_checks(released)
                fails = chk.failed(final_checks)
            outcome, route, released = _route(fails, cls, released)
            if outcome is None and flags["judge"]:
                j, _ = lab.client.chat_json(cfg.judge_model, [
                    {"role": "system", "content": prompts.JUDGE_SYSTEM},
                    {"role": "user", "content": _judge_input(message, cls.expected_class, tool_text, passages_used,
                                                             released)}],
                    Decoding(temperature=0.0, seed=cfg.decoding.seed, num_ctx=4096, num_predict=200),
                    prompts.JUDGE_SCHEMA)
                judge = j if j else {"verdict": "none", "reason": "unparseable", "parse_failure": True}
                if judge.get("verdict") in ("block", "escalate"):
                    released, outcome, route = prompts.TEMPLATES["ESCALATE"], "ESCALATE", f"judge_{judge['verdict']}"
            if outcome is None:
                outcome = cls.expected_class
        # A routed answer is a fixed, pre-approved template, not model text: there is nothing for the
        # Check Chain to judge, and checking it against the original class would count every block as a
        # guardrail violation (SM-3). The draft's violations stay visible in draft_checks.
        if route != "released":
            final_checks = []

    return {
        "item_id": item["id"], "config_id": cfg.id, "compliance": cfg.compliance,
        "persona_id": persona_id, "as_of": as_of.isoformat(),
        "nlu": n, "nlu_raw": n_raw, "grounding": grounding, "class": cls.expected_class, "class_reasons": cls.reasons, "missing_fields": cls.missing_fields,
        "unverified_premises": cls.unverified_premises, "out_of_scope": cls.out_of_scope,
        "rule_error": rule_error, "evidence": ev.as_dict(), "category": category,
        "passages": [{"pid": p.pid, "citation": p.citation(), "score": round(p.score, 3)} for p in passages_used],
        "retrieval_on": retrieval_on, "truncated": truncated,
        "prompt_hash": prompts.prompt_hash(system), "exposed_message": shown_msg,
        "draft": draft, "draft_checks": [c.as_dict() for c in draft_checks],
        "regenerated": regenerated, "judge": judge, "route": route,
        "released": released, "outcome": outcome, "final_checks": [c.as_dict() for c in final_checks],
        "model": cfg.phrasing_model, "model_digest": draft_res.digest, "prompt_tokens": draft_res.prompt_tokens,
        "latency_s": round(time.time() - t0, 2),
    }


def _judge_input(message: str, answer_type: str, tool_text: str, passages: list[Passage], answer: str,
                 max_evidence_chars: int = 5000) -> str:
    """The judge sees the same evidence as the phrasing model; without it, every computed number or sourced
    fact looks invented (E1 pilot run)."""
    evidence = [tool_text] if tool_text else []
    evidence += [f"[S{i}] {p.citation()}: {p.text[:400]}" for i, p in enumerate(passages, 1)]
    ev = "\n".join(evidence)[:max_evidence_chars] or "(none)"
    return (f"Customer question:\n{message}\n\nAnswer type: {answer_type}\n\n"
            f"EVIDENCE given to the advisor (deterministic calculations and cited sources):\n{ev}\n\n"
            f"Drafted answer:\n{answer}")


def _route(fails: list[chk.CheckResult], cls: Any, text: str) -> tuple[str | None, str, str]:
    """FR-52 block routing. Returns (outcome or None if released, route, released text)."""
    if not fails:
        return None, "released", text
    ids = {f.check_id for f in fails}
    if cls.expected_class == "REFUSE_OUT_OF_SCOPE" and "SCOPE" in ids:
        oos = ", ".join(cls.out_of_scope).replace("_", " ") or "these products"
        return "REFUSE_OUT_OF_SCOPE", "block_scope", prompts.TEMPLATES["REFUSE_OUT_OF_SCOPE"].format(oos=oos)
    if "PROFILE_COMPLETE" in ids:
        missing = ", ".join(cls.missing_fields) or "your goals, horizon, income and expenses, emergency fund and risk tolerance"
        return "ASK_PROFILE", "block_profile", prompts.TEMPLATES["ASK_PROFILE"].format(missing=missing)
    if ids == {"SOURCE_VALIDITY"} and next(f for f in fails).reason_code == "no_valid_source":
        return "INFORM", "no_valid_source", prompts.TEMPLATES["NO_VALID_SOURCE"]
    if "NUMBER_MATCH" in ids:
        return "ESCALATE", "number_mismatch_after_regeneration", prompts.TEMPLATES["ESCALATE"]
    return "ESCALATE", "block_" + "_".join(sorted(ids)).lower(), prompts.TEMPLATES["ESCALATE"]
