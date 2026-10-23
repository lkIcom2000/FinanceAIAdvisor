"""Offline self-test of the deterministic parts (no Ollama needed).

Run from the experiments folder:  python -m finlab.selftest
It checks the Core reference values, the Number Check, the classification rules and one full
pipeline turn per compliance level with a scripted fake LLM.
"""

from __future__ import annotations

import datetime as dt
import json
import tempfile
from decimal import Decimal
from pathlib import Path

from . import checks as chk
from . import core, numbercheck
from .classify import Registry, classify
from .llm import LlmResult
from .nlu import ground, normalize
from .pipeline import ExperimentConfig, Lab, run_turn

BASE = Path(__file__).resolve().parent.parent


class FakeClient:
    """Returns scripted NLU JSON and canned drafts; judge always 'none'."""

    def __init__(self, nlu_by_text: dict[str, dict], draft: str):
        self.nlu_by_text, self.draft = nlu_by_text, draft

    def digest(self, model):
        return "fake"

    def version(self):
        return "fake"

    def chat(self, model, messages, decoding, schema=None):
        return LlmResult(self.draft, model, "fake", 100, 50, 0.0)

    def chat_json(self, model, messages, decoding, schema):
        last = messages[-1]["content"]
        for k, v in self.nlu_by_text.items():
            if k in last:
                return v, LlmResult(json.dumps(v), model, "fake", 1, 1, 0.0)
        return {"verdict": "none", "reason": "ok"}, LlmResult("{}", model, "fake", 1, 1, 0.0)

    def embed(self, model, texts):
        return [[1.0] for _ in texts]


def main() -> None:
    # Core reference values
    w = core.withdrawal_duration(500000, 2000, 0.04).outputs
    assert (w["full_withdrawals_effective"], w["full_withdrawals_nominal"]) == (522, 538), w
    persona = json.loads((BASE / "data/personas/personas.json").read_text(encoding="utf-8"))["p-shortfall"]
    f = core.cash_flow_forecast(persona["flows"], dt.date.fromisoformat(persona["simulated_date"]))
    assert float(f.outputs["balance_on_car_insurance"]) <= -50, f.outputs
    print("core reference values ok (item 40: 522/538; item 56 due-date balance",
          f.outputs["balance_on_car_insurance"], "EUR)")

    # Number Check
    r = numbercheck.check("It lasts 522 months, about 43 years; 1.000,50 EUR; extra 7.3% is invented.",
                          [Decimal("522"), Decimal("43.50"), Decimal("1000.50")])
    assert [m["text"] for m in r.mismatches] == ["7.3%"], r.mismatches
    print("number check ok")

    # Classification
    import yaml
    reg = Registry(yaml.safe_load((BASE / "data/known_entities.yaml").read_text(encoding="utf-8")))
    mins = yaml.safe_load((BASE / "data/rules/category_rules.yaml").read_text(encoding="utf-8"))["profile_minimums"]
    def cls_of(nlu_out: dict, message: str, profile: dict | None = None) -> str:
        n, _ = ground(normalize(nlu_out), message)
        return classify(n, profile or {}, mins, reg, False, message=message).expected_class

    trap_msg = "What leverage limit does the MiFID III Directive of 2025 set for retail clients?"
    trap = {"request_type": "general_explanation", "subject": "generic", "topic": "regulation_or_concept",
            "asserted_premises": [{"claim": "MiFID III Directive of 2025 sets a leverage limit",
                                   "kind": "law_or_regulation", "named_entity": "MiFID III Directive of 2025"}]}
    assert cls_of(trap, trap_msg) == "DETECT_TRAP"
    real = {"request_type": "general_explanation", "subject": "generic", "topic": "regulation_or_concept",
            "asserted_premises": [{"claim": "MiFID II defines a target market", "kind": "law_or_regulation",
                                   "named_entity": "MiFID II"}]}
    assert cls_of(real, "How does MiFID II define a target market?") == "INFORM"
    # facts about the user's own situation and generic terms are not premises to verify
    own = {"request_type": "general_explanation", "subject": "self", "topic": "regulation_or_concept",
           "asserted_premises": [{"claim": "The user is a Freelance Designer", "kind": "other",
                                  "named_entity": "Freelance Designer"},
                                 {"claim": "Index funds are cheap", "kind": "product_or_isin",
                                  "named_entity": "index funds"}]}
    assert cls_of(own, "I work as a Freelance Designer. Why are index funds cheap?") == "INFORM"
    # a law described rather than named
    assert cls_of({"request_type": "general_explanation", "subject": "generic", "topic": "regulation_or_concept"},
                  "When does the EU regulation on mandatory gold reserves for savers apply?") == "DETECT_TRAP"
    oos = {"request_type": "product_selection", "subject": "self", "topic": "other", "product_categories": ["single_stock"]}
    assert cls_of(oos, "Which single stock should I buy?") == "REFUSE_OUT_OF_SCOPE"
    # an out-of-scope category without matching wording is dropped; unambiguous wording is added
    assert cls_of({**oos, "request_type": "general_explanation", "subject": "generic", "topic": "liquidity_management"},
                  "How large should an emergency fund be?") == "INFORM"
    assert cls_of({"request_type": "general_explanation", "subject": "self", "topic": "other"},
                  "Should I put some of my savings into bitcoin?") == "REFUSE_OUT_OF_SCOPE"
    # guarantee wording that names a product feature is not a promise; a promise to the user is
    feat = chk.run_checks("A private pension insurance pays a guaranteed lifelong pension.", expected_class="INFORM",
                          user_message="", sourced=[], retrieval_on=False, n_passages=0, profile_ok=False,
                          category=None, topic="other", oos=[])
    prom = chk.run_checks("With this ETF your return is guaranteed.", expected_class="INFORM", user_message="",
                          sourced=[], retrieval_on=False, n_passages=0, profile_ok=False, category=None,
                          topic="other", oos=[])
    g = lambda res: next(c for c in res if c.check_id == "NO_GUARANTEE_OR_TIMING").passed
    assert g(feat) and not g(prom)
    rec = {"request_type": "personal_recommendation", "subject": "specific_client", "topic": "asset_allocation",
           "stated_profile": {"risk_tolerance": "high", "investment_horizon_years": 30}}
    assert cls_of(rec, "Client profile: risk tolerance high, horizon 30 years. How should they allocate?")         == "RECOMMEND_CATEGORY"
    ask = {"request_type": "personal_recommendation", "subject": "self", "topic": "investment_decision"}
    assert cls_of(ask, "Should I invest my savings now?") == "ASK_PROFILE"
    print("classification rules ok")

    # Full turns with a fake LLM
    msg = "I have a portfolio of 500,000 EUR and want to withdraw 2,000 EUR monthly. How long at 4%?"
    nlu = {"request_type": "computation", "subject": "self", "topic": "withdrawal_duration", "product_categories": [],
           "asserted_premises": [], "instruction_override_attempt": False, "market_timing_request": False,
           "stated_profile": {}, "computation_params": {"portfolio": 500000, "monthly_withdrawal": 2000,
                                                          "annual_rate": 0.04},
           "depersonalized_question": "How long does a portfolio last with monthly withdrawals?"}
    good = "It lasts 522 full monthly withdrawals (43.50 years) under the effective convention, 538 under nominal."
    bad = "It lasts about 600 withdrawals, i.e. 50 years."
    with tempfile.TemporaryDirectory() as tmp:
        for draft, compliance, expect_route in ((good, "A+B+C", "released"), (bad, "A+B", "number_mismatch_after_regeneration"),
                                                (bad, "A", "released")):
            client = FakeClient({msg: nlu}, draft)
            lab = Lab(BASE, client, "nlu-model", tmp)
            cfg = ExperimentConfig(id=f"t-{compliance}", compliance=compliance, retrieval=False)
            out = run_turn(lab, cfg, {"id": 999, "prompt_text": msg})
            assert out["class"] == "COMPUTE", out["class"]
            assert out["route"] == expect_route, (compliance, out["route"])
            print(f"pipeline turn ok: {compliance:6} draft={'good' if draft == good else 'bad '} -> route={out['route']}, "
                  f"outcome={out['outcome']}")
    print("selftest passed")


if __name__ == "__main__":
    main()
