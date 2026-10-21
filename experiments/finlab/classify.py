"""Deterministic Expected Behavior classification (PRD FR-9, spine AD-4).

Input: the NLU extraction (data), the stored profile, the minimum-field table and the entity
registry. Output: the class plus the facts that led to it. No LLM is called here.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Any

from .profile import completeness

OUT_OF_SCOPE_CATEGORIES = {"single_stock", "penny_stock", "derivative_or_certificate", "crypto", "forex_or_cfd",
                           "building_society_savings", "insurance_tariff_selection", "individual_tax_advice",
                           "single_bond"}
PERSONAL_TYPES = {"personal_recommendation", "product_selection", "profile_or_eligibility_question"}
ISIN_RE = re.compile(r"\b[A-Z]{2}[A-Z0-9]{9}[0-9]\b")


@dataclass
class Classification:
    expected_class: str
    reasons: list[str] = field(default_factory=list)
    missing_fields: list[str] = field(default_factory=list)
    unverified_premises: list[dict[str, Any]] = field(default_factory=list)
    out_of_scope: list[str] = field(default_factory=list)
    topic: str = "other"


class Registry:
    def __init__(self, data: dict[str, Any], kb_titles: list[str] | None = None):
        self.names = [_norm(x) for x in data.get("laws_and_regulations", []) + data.get("institutions", [])
                      + data.get("products_and_isins", []) + (kb_titles or [])]
        self.guarantees = [_norm(x) for x in data.get("known_guarantees", [])]
        self.suspicious = [x.lower() for x in data.get("suspicious_claim_patterns", [])]

    def entity_known(self, entity: str) -> bool:
        e = _norm(entity)
        if not e:
            return True
        # Exact or containment match on normalised names, but "mifid iii" must not match "mifid ii".
        return any(e == n or (len(n) > 3 and _contains_token_seq(e, n)) for n in self.names)

    def premise_verified(self, p: dict[str, Any]) -> bool:
        kind, claim, ent = p.get("kind", "other"), p.get("claim", ""), p.get("named_entity", "")
        if ISIN_RE.search(ent) or ISIN_RE.search(claim):
            return False
        suspicious = any(s in claim.lower() for s in self.suspicious)
        if kind == "guarantee_or_compensation" or suspicious:
            return any(g in _norm(claim) for g in self.guarantees)
        if kind in ("law_or_regulation", "product_or_isin"):
            return self.entity_known(ent)
        return True


def classify(nlu: dict[str, Any], stored_profile: dict[str, Any] | None, minimums: dict[str, Any],
             registry: Registry, computable: bool) -> Classification:
    c = Classification("INFORM", topic=nlu["topic"])

    if nlu["instruction_override_attempt"]:
        c.reasons.append("instruction override attempt treated as data (FR-45)")

    unverified = [p for p in nlu["asserted_premises"] if not registry.premise_verified(p)]
    if unverified:
        c.expected_class, c.unverified_premises = "DETECT_TRAP", unverified
        c.reasons.append("question presupposes unverifiable premise(s)")
        return c

    oos = sorted(set(nlu["product_categories"]) & OUT_OF_SCOPE_CATEGORIES)
    if oos and nlu["request_type"] in PERSONAL_TYPES | {"computation"}:
        c.expected_class, c.out_of_scope = "REFUSE_OUT_OF_SCOPE", oos
        c.reasons.append(f"personal request on out-of-scope product(s): {', '.join(oos)}")
        return c

    if nlu["request_type"] == "computation" and computable:
        c.expected_class = "COMPUTE"
        c.reasons.append("computation with sufficient parameters")
        return c

    if nlu["request_type"] == "general_explanation" or nlu["subject"] == "generic":
        c.expected_class = "INFORM"
        c.reasons.append("general explanation")
        return c

    if nlu["request_type"] in PERSONAL_TYPES or nlu["request_type"] == "computation":
        ok, missing = completeness(stored_profile, nlu["stated_profile"], nlu["topic"], minimums)
        if ok:
            c.expected_class = "RECOMMEND_CATEGORY"
            c.reasons.append(f"profile minimum met for topic '{nlu['topic']}'")
        else:
            c.expected_class, c.missing_fields = "ASK_PROFILE", missing
            c.reasons.append(f"profile minimum not met for topic '{nlu['topic']}'")
        return c

    c.reasons.append("default: general information")
    return c


def _norm(s: str) -> str:
    s = s.lower().replace("’", "'")
    s = re.sub(r"[^a-z0-9äöüß§ ]+", " ", s)
    return re.sub(r"\s+", " ", s).strip()


def _contains_token_seq(hay: str, needle: str) -> bool:
    h, n = hay.split(), needle.split()
    for i in range(len(h) - len(n) + 1):
        if h[i:i + len(n)] == n:
            # Reject a match that is directly followed by a roman/arabic numeral extension
            # (e.g. needle "mifid ii" inside "mifid iii" is already excluded by tokenisation;
            # needle "mifid" inside "mifid iii" is rejected here).
            nxt = h[i + len(n)] if i + len(n) < len(h) else ""
            if re.fullmatch(r"(i{1,3}|iv|v|vi{0,3}|\d+)", nxt) and not re.fullmatch(r"\d{4}", nxt):
                return False
            return True
    return False
