"""Data Exposure Levels (PRD FR-43, experiment E4).

Controls which personal data the phrasing LLM sees, both in the persona context the system adds
and in the user's own message:

- raw:            message and persona context verbatim
- pseudonymised:  direct identifiers (names, client labels, IBANs, e-mails) replaced
- aggregated:     personal money amounts and ages replaced by bands
- none:           the LLM gets a depersonalised question and Deterministic Core results only

Core results are always passed in full; they are tool output, not persona attributes.
"""

from __future__ import annotations

import math
import re
from typing import Any

LEVELS = ("raw", "pseudonymised", "aggregated", "none")

IBAN_RE = re.compile(r"\b[A-Z]{2}\d{2}(?:\s?[A-Z0-9]{4}){3,7}\b")
EMAIL_RE = re.compile(r"\b[\w.+-]+@[\w-]+\.[\w.]+\b")
CLIENT_RE = re.compile(r"\b(Client|Customer|Mr\.?|Mrs\.?|Ms\.?)\s+[A-Z][a-z]*\b")
MONEY_RE = re.compile(r"(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,]\d+)?\s?(EUR|€|euros?)", re.IGNORECASE)
AGE_RE = re.compile(r"\b(?:age|aged|I am|I'm)\s*:?\s*(\d{2})\b", re.IGNORECASE)


def band(v: float) -> str:
    if v <= 0:
        return "0 EUR"
    step = max(100, 10 ** int(math.log10(v)) / 2)
    lo = math.floor(v / step) * step
    return f"{lo:,.0f}–{lo + step:,.0f} EUR"


def filter_message(message: str, level: str, depersonalized: str) -> str:
    if level == "raw":
        return message
    if level == "none":
        return depersonalized or "[question withheld: no personal data may be shared at this exposure level]"
    out = IBAN_RE.sub("[IBAN]", EMAIL_RE.sub("[EMAIL]", message))
    out = CLIENT_RE.sub(lambda m: f"{m.group(1)} X", out)
    if level == "aggregated":
        out = MONEY_RE.sub(lambda m: band(_num(m.group(0))), out)
        out = AGE_RE.sub(lambda m: m.group(0).replace(m.group(1), f"{int(m.group(1)) // 10 * 10}s"), out)
    return out


def filter_profile(profile: dict[str, Any], level: str, name: str | None = None) -> dict[str, Any]:
    if level == "none":
        return {}
    out: dict[str, Any] = {}
    for k, v in profile.items():
        if level == "aggregated" and isinstance(v, (int, float)) and k not in ("investment_horizon_years", "children"):
            out[k] = f"{int(v) // 10 * 10}s" if k == "age" else band(float(v))
        else:
            out[k] = v
    if name and level == "raw":
        out["name"] = name
    return out


def _num(s: str) -> float:
    digits = re.sub(r"[^\d.,]", "", s)
    if re.fullmatch(r"\d{1,3}([.,]\d{3})+", digits):
        return float(re.sub(r"[.,]", "", digits))
    return float(digits.replace(",", "."))
