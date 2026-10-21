"""Profile fields and the single completeness function (spine AD-4, AD-23)."""

from __future__ import annotations

from typing import Any

# Fields the NLU may extract from a message (StatedProfile) or that a persona may store.
PROFILE_FIELDS: dict[str, str] = {
    "age": "number",
    "occupation": "string",
    "employment_status": "string",
    "net_income": "number",
    "gross_income": "number",
    "monthly_expenses": "number",
    "monthly_surplus": "number",
    "emergency_fund": "number",
    "current_balance": "number",
    "risk_tolerance": "string",
    "loss_aversion": "string",
    "loss_bearing_capacity": "string",
    "investment_horizon_years": "number",
    "goal": "string",
    "knowledge_experience": "string",
    "sustainability_preference": "string",
    "current_holdings": "string",
    "tax_allowance_unused": "number",
    "pension_situation": "string",
    "children": "number",
    "household": "string",
    "dependants": "string",
    "existing_contract_details": "string",
    "existing_contracts": "array",
    "amount": "number",
    "requested_monthly_amount": "number",
}

# Fields every topic needs before a persisted recommendation in a persona workspace (FR-17).
# Used only for stored profiles; turn-scoped recommendations use the per-topic minimums.
STRICT_SUITABILITY_FIELDS = [
    "goal", "investment_horizon_years", "net_income", "monthly_expenses", "emergency_fund",
    "loss_bearing_capacity", "risk_tolerance", "knowledge_experience", "sustainability_preference",
]


def effective_profile(stored: dict[str, Any] | None, stated: dict[str, Any] | None) -> dict[str, Any]:
    """Stated facts override stored ones for this turn only; nothing is persisted."""
    out = dict(stored or {})
    for k, v in (stated or {}).items():
        if v not in (None, "", [], {}):
            out[k] = v
    return out


def completeness(stored: dict[str, Any] | None, stated: dict[str, Any] | None, topic: str,
                 minimums: dict[str, list[list[str]]]) -> tuple[bool, list[str]]:
    """Return (minimum met, missing fields). AND over groups, OR within a group."""
    prof = effective_profile(stored, stated)
    groups = minimums.get(topic)
    if groups is None:
        return False, ["topic_not_supported_for_personal_recommendation"]
    missing: list[str] = []
    for group in groups:
        if not any(prof.get(f) not in (None, "", [], {}) for f in group):
            missing.append(" or ".join(group))
    return not missing, missing
