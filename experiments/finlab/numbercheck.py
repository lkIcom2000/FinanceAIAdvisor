"""Number Check (PRD FR-12).

Every number in a drafted text must equal a sourced number of the turn after rounding to the
precision shown in the text. Numbers marked as approximate may deviate by up to 5 %. Dates,
list positions and quoted article/paragraph numbers are excluded. English (1,000.50) and German
(1.000,50) formats are both parsed. Percent values are compared as fractions and as percent.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation

NUM_RE = re.compile(
    r"(?<![\w.,])"                       # not glued to a word or another number
    r"(?P<sign>[-−–]?)"
    r"(?P<num>\d{1,3}(?:[.,  ]\d{3})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?)"
    r"(?P<suffix>\s?(?:%|percent|per cent|k\b|thousand|million|mn\b|bn\b|billion))?",
    re.IGNORECASE,
)
APPROX_RE = re.compile(r"\b(about|around|roughly|approximately|approx\.?|circa|ca\.|nearly|almost|some|up to|~)\s*$",
                       re.IGNORECASE)
REF_BEFORE_RE = re.compile(
    r"(article|art\.|§|section|paragraph|para\.|annex|recital|level|step|item|question|tier|class|"
    r"pillar|chapter|no\.|nr\.|number|version|mifid|ucits|basel|solvency|psd|ifrs|figure|table)\s*$",
    re.IGNORECASE)
MONTHS = r"(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\.?"
DATE_RES = [
    re.compile(r"\b\d{1,2}[./]\d{1,2}[./]\d{2,4}\b"),
    re.compile(r"\b\d{4}-\d{2}-\d{2}\b"),
    re.compile(rf"\b\d{{1,2}}(st|nd|rd|th)?\s+{MONTHS}(\s+\d{{4}})?\b", re.IGNORECASE),
    re.compile(rf"\b{MONTHS}\s+\d{{1,2}}(st|nd|rd|th)?(,?\s+\d{{4}})?\b", re.IGNORECASE),
    re.compile(r"\b(19|20)\d{2}\b"),                     # years
    re.compile(r"\[S\d+\]"),                              # citation markers
    re.compile(r"\b[A-Z]{2}[A-Z0-9]{9}[0-9]\b"),         # ISINs are checked elsewhere
    re.compile(r"\bL[0-4]\b|\bE[1-4]\b|\bSM-\d+\b|\bFR-\d+\b"),
]
LIST_POS_RE = re.compile(r"^\s*(\d{1,2})[.)]\s", re.MULTILINE)


@dataclass
class FoundNumber:
    text: str
    value: Decimal
    is_percent: bool
    approximate: bool
    decimals: int
    start: int


@dataclass
class NumberCheckResult:
    passed: bool
    checked: list[dict]
    mismatches: list[dict]


def parse_numbers(text: str) -> list[FoundNumber]:
    masked = text
    for rx in DATE_RES:
        masked = rx.sub(lambda m: " " * len(m.group(0)), masked)
    masked = LIST_POS_RE.sub(lambda m: m.group(0).replace(m.group(1), " " * len(m.group(1))), masked)
    out: list[FoundNumber] = []
    for m in NUM_RE.finditer(masked):
        before = masked[max(0, m.start() - 14):m.start()]
        if REF_BEFORE_RE.search(before):
            continue
        val, decimals = _to_decimal(m.group("num"))
        if val is None:
            continue
        suffix = (m.group("suffix") or "").strip().lower()
        mult = {"k": 1000, "thousand": 1000, "million": 10**6, "mn": 10**6, "bn": 10**9, "billion": 10**9}.get(suffix, 1)
        val = val * mult
        if m.group("sign"):
            val = -val
        out.append(FoundNumber(m.group(0).strip(), val, suffix in ("%", "percent", "per cent"),
                               bool(APPROX_RE.search(text[max(0, m.start() - 16):m.start()])), decimals, m.start()))
    return out


def _to_decimal(s: str) -> tuple[Decimal | None, int]:
    s = s.replace(" ", "").replace(" ", "")
    if "," in s and "." in s:
        dec_sep = "," if s.rfind(",") > s.rfind(".") else "."
    elif "," in s:
        # "1,000" (thousands) vs "2,5" (German decimal)
        dec_sep = "." if re.fullmatch(r"\d{1,3}(,\d{3})+", s) else ","
    elif "." in s:
        dec_sep = "," if re.fullmatch(r"\d{1,3}(\.\d{3})+", s) else "."
    else:
        dec_sep = "."
    thou = "," if dec_sep == "." else "."
    s = s.replace(thou, "").replace(dec_sep, ".")
    decimals = len(s.split(".")[1]) if "." in s else 0
    try:
        return Decimal(s), decimals
    except InvalidOperation:
        return None, 0


def matches(found: FoundNumber, sourced: list[Decimal]) -> bool:
    candidates = {found.value, abs(found.value)}
    if found.is_percent:
        candidates |= {found.value / 100, abs(found.value) / 100}
    for c in candidates:
        for s in sourced:
            for sv in (s, s * 100, abs(s), abs(s) * 100):
                if found.approximate:
                    if sv != 0 and abs(c - sv) <= abs(sv) * Decimal("0.05"):
                        return True
                    if sv == 0 and c == 0:
                        return True
                else:
                    q = Decimal(1).scaleb(-found.decimals) if found.decimals else Decimal(1)
                    try:
                        if sv.quantize(q) == c.quantize(q):
                            return True
                    except InvalidOperation:
                        continue
                    # also accept the shown value when the sourced value rounds to it at
                    # a coarser shown precision (e.g. "about 522" vs 522.0)
    return False


def check(text: str, sourced: list[Decimal]) -> NumberCheckResult:
    found = parse_numbers(text)
    checked, bad = [], []
    for f in found:
        ok = matches(f, sourced)
        rec = {"text": f.text, "value": str(f.value), "approximate": f.approximate, "ok": ok}
        checked.append(rec)
        if not ok:
            bad.append(rec)
    return NumberCheckResult(not bad, checked, bad)


def numbers_in(text: str) -> list[Decimal]:
    """Numbers in the user's own message are sourced numbers (FR-12)."""
    vals = []
    for f in parse_numbers(text):
        vals.append(f.value)
        if f.is_percent:
            vals.append(f.value / 100)
    # years and dates in the user message are also allowed to be repeated
    for m in re.finditer(r"\b(19|20)\d{2}\b", text):
        vals.append(Decimal(m.group(0)))
    return vals
