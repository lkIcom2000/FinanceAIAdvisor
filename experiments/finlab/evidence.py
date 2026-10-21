"""Provenance-carrying result types (mirrors spine AD-2 in the experiment track)."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field, asdict
from decimal import Decimal, ROUND_HALF_UP
from typing import Any

UNITS = {"EUR", "FRACTION", "PP", "MONTHS", "YEARS", "COUNT"}


def q2(x: float | Decimal) -> Decimal:
    """Round half-up to 2 places (the only rounding point for money)."""
    return Decimal(str(x)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


@dataclass(frozen=True)
class SourcedNumber:
    prov_id: str
    value: Decimal
    unit: str
    label: str
    source: str  # "core" | "rule" | "kb" | "user"

    def as_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["value"] = str(self.value)
        return d


@dataclass
class CoreResult:
    function: str
    inputs: dict[str, Any]
    outputs: dict[str, Any]
    assumptions: list[str]
    rule_param_versions: dict[str, str] = field(default_factory=dict)
    numbers: list[SourcedNumber] = field(default_factory=list)
    number_keys: list[str] = field(default_factory=list)

    def add(self, key: str, value: float | Decimal | int, unit: str, label: str) -> None:
        assert unit in UNITS, unit
        v = Decimal(str(value))
        pid = figure_key(self.function, self.inputs, key)
        self.numbers.append(SourcedNumber(pid, v, unit, label, "core"))
        self.number_keys.append(key)
        self.outputs[key] = str(v) if isinstance(value, Decimal) else value

    def as_dict(self) -> dict[str, Any]:
        return {
            "function": self.function,
            "inputs": self.inputs,
            "outputs": self.outputs,
            "assumptions": self.assumptions,
            "rule_param_versions": self.rule_param_versions,
            "numbers": [n.as_dict() for n in self.numbers],
            "number_keys": self.number_keys,
        }


def figure_key(function: str, inputs: dict[str, Any], output_key: str) -> str:
    payload = json.dumps([function, inputs, output_key], sort_keys=True, default=str)
    return hashlib.sha1(payload.encode()).hexdigest()[:16]


@dataclass
class Evidence:
    """Per-turn ledger of every sourced number (Core, rules, KB passages, user message)."""

    numbers: list[SourcedNumber] = field(default_factory=list)
    core_results: list[CoreResult] = field(default_factory=list)

    def add_core(self, r: CoreResult) -> None:
        self.core_results.append(r)
        self.numbers.extend(r.numbers)

    def add_raw(self, values: list[Decimal], source: str, label: str) -> None:
        for i, v in enumerate(values):
            pid = figure_key(source, {"label": label}, str(i))
            self.numbers.append(SourcedNumber(pid, v, "COUNT", label, source))

    def values(self) -> list[Decimal]:
        return [n.value for n in self.numbers]

    def as_dict(self) -> dict[str, Any]:
        return {
            "core_results": [r.as_dict() for r in self.core_results],
            "numbers": [n.as_dict() for n in self.numbers],
        }
