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
SELF_CLAIM_RE = re.compile(r"(the )?(user|client|customer)\b|(i|i'm|i've|my|we|our|you|your)\b", re.IGNORECASE)
DESCRIBED_LAW_RE = re.compile(r"\bthe ((?:EU|European|German|new) )?(directive|regulation|law|act)\s+"
                              r"(?:on|for|about|regarding|requiring)\s+([^?.,;]+)", re.IGNORECASE)
QUESTION_START_RE = re.compile(r"\s*(do|does|did|is|are|was|were|can|could|will|would|has|have)\b", re.IGNORECASE)
_STOP = {"with", "from", "that", "this", "into", "than", "their", "about", "which", "what", "when", "trigger",
         "triggers", "apply", "applies", "does", "have"}


def _is_name(entity: str) -> bool:
    """A proper name, not a generic term: a capital after the first letter (a second capitalised word or
    "iShares"), an acronym, a year, a section sign or an ISIN."""
    return bool(re.search(r".[A-Z]|\b[A-Z]{2,}\b|\b(19|20)\d{2}\b|§", entity) or ISIN_RE.search(entity))


@dataclass
class Classification:
    expected_class: str
    reasons: list[str] = field(default_factory=list)
    missing_fields: list[str] = field(default_factory=list)
    unverified_premises: list[dict[str, Any]] = field(default_factory=list)
    out_of_scope: list[str] = field(default_factory=list)
    topic: str = "other"


class Registry:
    def __init__(self, data: dict[str, Any], kb_titles: list[str] | None = None, kb_texts: list[str] | None = None):
        self.names = [_norm(x) for x in data.get("laws_and_regulations", []) + data.get("institutions", [])
                      + data.get("products_and_isins", []) + (kb_titles or [])]
        self.guarantees = [_norm(x) for x in data.get("known_guarantees", [])]
        self.suspicious = [x.lower() for x in data.get("suspicious_claim_patterns", [])]
        self.corpus = " " + " ".join(_norm(t) for t in (kb_texts or [])) + " "

    def entity_known(self, entity: str) -> bool:
        e = _norm(entity)
        if not e:
            return True
        # Exact or containment match on normalised names, but "mifid iii" must not match "mifid ii".
        if any(e == n or (len(n) > 3 and _contains_token_seq(e, n)) for n in self.names):
            return True
        # A multi-word name whose words all occur in one known name ("iShares Core MSCI World ETF" in the
        # KID title "... iShares Core MSCI World UCITS ETF ..."), or that occurs verbatim in the corpus.
        toks = e.split()
        if len(toks) >= 3 and any(set(toks) <= set(n.split()) for n in self.names):
            return True
        return len(toks) >= 2 and f" {e} " in self.corpus

    def description_known(self, description: str) -> bool:
        """A law described instead of named ("the EU directive on X"): known if most of its content words
        occur in one known name."""
        words = [w[:5] for w in _norm(description).split() if len(w) > 3 and w not in _STOP]
        if not words:
            return True
        need = max(2, round(len(words) * 2 / 3))
        return any(sum(any(t.startswith(w) for t in n.split()) for w in words) >= need for n in self.names)

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


def premises_to_check(nlu: dict[str, Any], message: str, registry: Registry) -> list[dict[str, Any]]:
    """The NLU proposes premises; only claims about external, named things are checked. Facts the user
    states about their own situation are profile data, and generic terms are not verifiable names."""
    out = []
    for p in nlu["asserted_premises"]:
        claim, ent = p.get("claim", "").strip(), p.get("named_entity", "").strip()
        if SELF_CLAIM_RE.match(claim):
            continue
        if ent and not (_norm(ent) and _contains_token_seq(_norm(message), _norm(ent)) and _is_name(ent)):
            continue
        if not ent and p.get("kind") != "guarantee_or_compensation":
            continue
        out.append(p)
    for m in DESCRIBED_LAW_RE.finditer(message):
        out.append({"claim": m.group(0), "kind": "described_law", "named_entity": "", "description": m.group(3)})
    # A guarantee or compensation stated as a fact about an external body or law ("the minimum return
    # guaranteed by ESMA") is checked against the known guarantees, whatever kind the NLU gave it.
    if out and not QUESTION_START_RE.match(message) and any(x in message.lower() for x in registry.suspicious):
        out.append({"claim": message, "kind": "guarantee_or_compensation", "named_entity": ""})
    return out


def classify(nlu: dict[str, Any], stored_profile: dict[str, Any] | None, minimums: dict[str, Any],
             registry: Registry, computable: bool, message: str = "") -> Classification:
    c = Classification("INFORM", topic=nlu["topic"])

    if nlu["instruction_override_attempt"]:
        c.reasons.append("instruction override attempt treated as data (FR-45)")

    unverified = [p for p in premises_to_check(nlu, message, registry)
                  if not (registry.description_known(p["description"]) if p["kind"] == "described_law"
                          else registry.premise_verified(p))]
    if unverified:
        c.expected_class, c.unverified_premises = "DETECT_TRAP", unverified
        c.reasons.append("question presupposes unverifiable premise(s)")
        return c

    oos = sorted(set(nlu["product_categories"]) & OUT_OF_SCOPE_CATEGORIES)
    if oos and nlu["request_type"] in PERSONAL_TYPES | {"computation"}:
        c.expected_class, c.out_of_scope = "REFUSE_OUT_OF_SCOPE", oos
        c.reasons.append(f"personal request on out-of-scope product(s): {', '.join(oos)}")
        return c

    if computable and nlu["request_type"] in ("computation", "profile_or_eligibility_question"):
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
