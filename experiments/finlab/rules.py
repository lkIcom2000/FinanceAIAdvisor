"""Versioned Rule Parameters with validity windows (PRD FR-22, spine AD-9)."""

from __future__ import annotations

import datetime as dt
import hashlib
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

import yaml


class NoValidRuleParameter(Exception):
    """Raised when no entry for a key is valid at the requested date (routes to ESCALATE)."""


@dataclass(frozen=True)
class RuleParam:
    key: str
    value: Decimal
    unit: str
    valid_from: dt.date
    valid_to: dt.date | None
    source: str
    version: str

    def valid_at(self, d: dt.date) -> bool:
        return self.valid_from <= d and (self.valid_to is None or d <= self.valid_to)


class RuleBook:
    def __init__(self, params: list[RuleParam], snapshot_id: str):
        self.params = params
        self.snapshot_id = snapshot_id

    @classmethod
    def load(cls, path: str | Path) -> "RuleBook":
        raw = Path(path).read_bytes().replace(b"\r\n", b"\n")
        data = yaml.safe_load(raw)
        params = [
            RuleParam(
                key=p["key"],
                value=Decimal(str(p["value"])),
                unit=p["unit"],
                valid_from=_date(p["valid_from"]),
                valid_to=_date(p["valid_to"]) if p.get("valid_to") else None,
                source=p["source"],
                version=str(p["version"]),
            )
            for p in data["parameters"]
        ]
        return cls(params, hashlib.sha256(raw).hexdigest()[:16])

    def get(self, key: str, as_of: dt.date) -> RuleParam:
        valid = [p for p in self.params if p.key == key and p.valid_at(as_of)]
        if not valid:
            raise NoValidRuleParameter(f"No valid Rule Parameter '{key}' at {as_of}")
        return max(valid, key=lambda p: p.valid_from)

    def value(self, key: str, as_of: dt.date) -> Decimal:
        return self.get(key, as_of).value

    def all_valid(self, as_of: dt.date) -> list[RuleParam]:
        keys = sorted({p.key for p in self.params})
        out = []
        for k in keys:
            try:
                out.append(self.get(k, as_of))
            except NoValidRuleParameter:
                pass
        return out


def _date(v) -> dt.date:
    return v if isinstance(v, dt.date) else dt.date.fromisoformat(str(v))
