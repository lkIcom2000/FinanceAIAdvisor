"""Deterministic Core (PRD FR-20 to FR-22): the only source of numbers and category selections.

Every function is pure: all inputs, including the as-of date, seeds and Rule Parameters,
arrive as arguments. Every function returns a CoreResult whose numbers carry provenance.
"""

from __future__ import annotations

import datetime as dt
import math
from decimal import Decimal
from typing import Any

import numpy as np

from .evidence import CoreResult, q2
from .rules import RuleBook


# --------------------------------------------------------------------------- withdrawals
def withdrawal_duration(portfolio: float, monthly_withdrawal: float, annual_rate: float) -> CoreResult:
    """Number of full month-end withdrawals under two rate conventions (test item 40)."""
    r = CoreResult(
        "withdrawal_duration",
        {"portfolio": portfolio, "monthly_withdrawal": monthly_withdrawal, "annual_rate": annual_rate},
        {},
        [
            "Withdrawals at month end after that month's return",
            "Constant return, no costs, no taxes, no inflation adjustment",
            "Convention A: annual rate is effective, monthly rate = (1 + r)^(1/12) - 1",
            "Convention B: annual rate is nominal, monthly rate = r / 12",
        ],
    )
    for key, monthly in (
        ("effective", (1 + annual_rate) ** (1 / 12) - 1),
        ("nominal", annual_rate / 12),
    ):
        n, last = _count_withdrawals(portfolio, monthly_withdrawal, monthly)
        r.add(f"full_withdrawals_{key}", n, "COUNT", f"Full monthly withdrawals ({key} convention)")
        r.add(f"years_{key}", q2(n / 12), "YEARS", f"Duration in years ({key} convention)")
        r.add(f"final_withdrawal_{key}", q2(last), "EUR", f"Reduced final withdrawal ({key} convention)")
    r.add("monthly_withdrawal", q2(monthly_withdrawal), "EUR", "Monthly withdrawal")
    r.add("portfolio", q2(portfolio), "EUR", "Starting portfolio")
    r.add("annual_rate", Decimal(str(annual_rate)), "FRACTION", "Assumed annual return")
    return r


def _count_withdrawals(balance: float, w: float, monthly_rate: float) -> tuple[int, float]:
    n = 0
    if monthly_rate > 0 and balance * monthly_rate >= w:
        return 10**6, 0.0  # perpetual
    while True:
        balance *= 1 + monthly_rate
        if balance >= w:
            balance -= w
            n += 1
        else:
            return n, balance


# --------------------------------------------------------------------------- cash flow
def cash_flow_forecast(flows: dict[str, Any], as_of: dt.date, horizon_days: int = 92) -> CoreResult:
    """Daily current-account balance projection from recurring and one-off flows (FR-20, FR-30).

    flows = {
      "balance": 450,                                   # current account at as_of (end of day)
      "recurring": [{"label": "salary", "amount": 2300, "day": 1}, ...],   # +income / -cost
      "variable_monthly": 400,                          # spread evenly over the days of a month
      "one_off": [{"label": "car insurance", "amount": -620, "date": "2026-11-28"}],
    }
    """
    balance = float(flows["balance"])
    recurring = flows.get("recurring", [])
    variable = float(flows.get("variable_monthly", 0))
    one_off = [(dt.date.fromisoformat(str(o["date"])), float(o["amount"]), o["label"]) for o in flows.get("one_off", [])]
    r = CoreResult(
        "cash_flow_forecast",
        {"flows": flows, "as_of": as_of.isoformat(), "horizon_days": horizon_days},
        {},
        [
            "Recurring flows repeat monthly on the stated day",
            f"Variable spending of {variable:.2f} EUR per month is spread evenly over the days of each month",
            "One-off flows are booked on their due date",
            "No interest, no unplanned income or spending",
        ],
    )
    min_bal, min_day = balance, as_of
    due_balances: list[tuple[str, dt.date, float]] = []
    day = as_of
    for _ in range(horizon_days):
        day += dt.timedelta(days=1)
        dim = _days_in_month(day)
        for f in recurring:
            if day.day == min(int(f["day"]), dim):
                balance += float(f["amount"])
        balance -= variable / dim
        for d, amt, label in one_off:
            if d == day:
                balance += amt
                due_balances.append((label, d, balance))
        if balance < min_bal:
            min_bal, min_day = balance, day
    r.add("current_balance", q2(flows["balance"]), "EUR", "Current account balance today")
    r.add("min_balance", q2(min_bal), "EUR", "Lowest forecast balance")
    r.outputs["min_balance_date"] = min_day.isoformat()
    r.outputs["shortfall"] = min_bal < 0
    if min_bal < 0:
        r.add("shortfall_amount", q2(-min_bal), "EUR", "Forecast shortfall amount")
    for label, d, b in due_balances:
        r.add(f"balance_on_{label}", q2(b), "EUR", f"Forecast balance on {d.isoformat()} after '{label}'")
        r.outputs[f"date_of_{label}"] = d.isoformat()
    for f in recurring:
        r.add(f"recurring_{f['label']}", q2(abs(float(f["amount"]))), "EUR", f"Monthly {f['label']}")
    if variable:
        r.add("variable_monthly", q2(variable), "EUR", "Variable monthly spending")
    for _, amt, label in one_off:
        r.add(f"one_off_{label}", q2(abs(amt)), "EUR", f"One-off {label}")
    return r


def simple_shortfall(net_income: float, fixed_costs: float, balance: float, due_amount: float,
                     variable_spending: float | None = None) -> CoreResult:
    """Shortfall check from stated figures only, when no persona transaction data is available."""
    var = variable_spending or 0.0
    after = balance + net_income - fixed_costs - var - due_amount
    r = CoreResult(
        "simple_shortfall",
        {"net_income": net_income, "fixed_costs": fixed_costs, "balance": balance,
         "due_amount": due_amount, "variable_spending": var},
        {},
        [
            "One salary credit and one month of costs before the due date",
            "Variable spending only as stated" if var else "Variable spending not stated and not included",
            "Result depends on the order of debits and credits within the month",
        ],
    )
    r.add("monthly_surplus", q2(net_income - fixed_costs - var), "EUR", "Monthly surplus")
    r.add("balance_after_due", q2(after), "EUR", "Balance after the due payment")
    r.outputs["shortfall"] = after < 0
    return r


def _days_in_month(d: dt.date) -> int:
    nxt = (d.replace(day=28) + dt.timedelta(days=4)).replace(day=1)
    return (nxt - dt.timedelta(days=1)).day


# --------------------------------------------------------------------------- liquidity
def emergency_fund(monthly_expenses: float, emergency_fund_now: float | None, rules: RuleBook,
                   as_of: dt.date) -> CoreResult:
    mn = rules.get("emergency_fund_months_min", as_of)
    mx = rules.get("emergency_fund_months_max", as_of)
    r = CoreResult(
        "emergency_fund",
        {"monthly_expenses": monthly_expenses, "emergency_fund_now": emergency_fund_now, "as_of": as_of.isoformat()},
        {},
        [f"Target = {mn.value}-{mx.value} months of expenses ({mn.source})",
         "Held in an instantly available account (current account / overnight deposit)"],
        {mn.key: mn.version, mx.key: mx.version},
    )
    r.add("target_min", q2(monthly_expenses * float(mn.value)), "EUR", "Emergency fund target (lower)")
    r.add("target_max", q2(monthly_expenses * float(mx.value)), "EUR", "Emergency fund target (upper)")
    r.add("months_min", mn.value, "MONTHS", "Months of expenses (lower)")
    r.add("months_max", mx.value, "MONTHS", "Months of expenses (upper)")
    if emergency_fund_now is not None:
        gap = max(0.0, monthly_expenses * float(mn.value) - emergency_fund_now)
        r.add("gap_to_min", q2(gap), "EUR", "Gap to lower target")
        r.add("months_covered", q2(emergency_fund_now / monthly_expenses) if monthly_expenses else 0, "MONTHS",
              "Months of expenses currently covered")
        r.outputs["adequate"] = gap == 0
    return r


def monthly_surplus(net_income: float, monthly_expenses: float) -> CoreResult:
    r = CoreResult("monthly_surplus", {"net_income": net_income, "monthly_expenses": monthly_expenses}, {},
                   ["Net income minus all regular monthly expenses"])
    r.add("monthly_surplus", q2(net_income - monthly_expenses), "EUR", "Monthly surplus")
    r.add("net_income", q2(net_income), "EUR", "Net income")
    r.add("monthly_expenses", q2(monthly_expenses), "EUR", "Monthly expenses")
    return r


# --------------------------------------------------------------------------- projection
def monte_carlo(monthly_contribution: float, years: int, initial: float, seed: int, rules: RuleBook,
                as_of: dt.date, n_paths: int = 5000, index_monthly_returns: np.ndarray | None = None) -> CoreResult:
    """Range projection (p10/p50/p90). Bootstraps historical monthly returns when supplied."""
    mu = float(rules.value("projection_equity_mean_return", as_of))
    sigma = float(rules.value("projection_equity_volatility", as_of))
    ter = float(rules.value("reference_ter_world_equity_etf", as_of))
    infl = float(rules.value("projection_inflation", as_of))
    rng = np.random.default_rng(seed)
    months = years * 12
    if index_monthly_returns is not None and len(index_monthly_returns) > 24:
        rets = rng.choice(index_monthly_returns, size=(n_paths, months), replace=True)
        model = f"Bootstrap of {len(index_monthly_returns)} historical monthly index returns"
    else:
        m_mu = (1 + mu) ** (1 / 12) - 1
        m_sigma = sigma / math.sqrt(12)
        rets = rng.normal(m_mu, m_sigma, size=(n_paths, months))
        model = f"Normal monthly returns, {mu:.1%} p.a. mean, {sigma:.1%} p.a. volatility (assumption)"
    rets = rets - ter / 12
    wealth = np.full(n_paths, float(initial))
    for t in range(months):
        wealth = wealth * (1 + rets[:, t]) + monthly_contribution
    p10, p50, p90 = np.percentile(wealth, [10, 50, 90])
    paid_in = initial + monthly_contribution * months
    r = CoreResult(
        "monte_carlo",
        {"monthly_contribution": monthly_contribution, "years": years, "initial": initial, "seed": seed,
         "n_paths": n_paths, "as_of": as_of.isoformat()},
        {},
        [model, f"Costs: {ter:.2%} p.a. TER deducted", "Nominal values, before taxes",
         f"Inflation assumption for real values: {infl:.1%} p.a.", "A range, not a promise"],
    )
    r.add("p10", q2(p10), "EUR", "10th percentile")
    r.add("p50", q2(p50), "EUR", "Median")
    r.add("p90", q2(p90), "EUR", "90th percentile")
    r.add("paid_in", q2(paid_in), "EUR", "Total paid in")
    r.add("p50_real", q2(p50 / (1 + infl) ** years), "EUR", "Median in today's money")
    return r


# --------------------------------------------------------------------------- subsidy
def riester_subsidy(prior_year_gross_income: float, children_birth_years: list[int], rules: RuleBook,
                    as_of: dt.date) -> CoreResult:
    basic = rules.get("riester_basic_allowance", as_of)
    c_new = rules.get("riester_child_allowance_born_2008_or_later", as_of)
    c_old = rules.get("riester_child_allowance_born_before_2008", as_of)
    rate = rules.get("riester_min_contribution_rate", as_of)
    floor = rules.get("riester_min_contribution_floor", as_of)
    cap = rules.get("riester_special_expense_cap", as_of)
    child_total = sum(float(c_new.value) if y >= 2008 else float(c_old.value) for y in children_birth_years)
    allowances = float(basic.value) + child_total
    min_total = min(float(cap.value), float(rate.value) * prior_year_gross_income)
    own = max(float(floor.value), min_total - allowances)
    r = CoreResult(
        "riester_subsidy",
        {"prior_year_gross_income": prior_year_gross_income, "children_birth_years": children_birth_years,
         "as_of": as_of.isoformat()},
        {},
        ["Directly eligible person (e.g. employee with statutory pension insurance)",
         "Child allowances only if child benefit is received for the child",
         "Tax deduction (Günstigerprüfung) not included"],
        {p.key: p.version for p in (basic, c_new, c_old, rate, floor, cap)},
    )
    r.add("basic_allowance", basic.value, "EUR", "Basic allowance per year")
    r.add("child_allowances", q2(child_total), "EUR", "Child allowances per year")
    r.add("total_allowances", q2(allowances), "EUR", "Total allowances per year")
    r.add("min_own_contribution", q2(own), "EUR", "Minimum own contribution per year for full allowances")
    return r


# --------------------------------------------------------------------------- costs
def cost_comparison(amount: float, years: int, rules: RuleBook, as_of: dt.date,
                    front_load: float | None = None, active_ter: float | None = None) -> CoreResult:
    etf = float(rules.value("reference_ter_world_equity_etf", as_of))
    act = active_ter if active_ter is not None else float(rules.value("reference_ter_active_fund", as_of))
    load = front_load if front_load is not None else float(rules.value("reference_front_load_active_fund", as_of))
    r = CoreResult(
        "cost_comparison",
        {"amount": amount, "years": years, "front_load": load, "active_ter": act, "etf_ter": etf,
         "as_of": as_of.isoformat()},
        {},
        ["Ex-ante costs on a constant invested amount, no returns assumed",
         "ETF: ongoing charges only; active fund: front-end load once plus ongoing charges",
         "Illustrative reference costs, not the costs of a specific product"],
    )
    etf_total = amount * etf * years
    act_total = amount * load + amount * act * years
    r.add("etf_ter", Decimal(str(etf)), "FRACTION", "ETF ongoing charges p.a.")
    r.add("active_ter", Decimal(str(act)), "FRACTION", "Active fund ongoing charges p.a.")
    r.add("front_load", Decimal(str(load)), "FRACTION", "Active fund front-end load")
    r.add("etf_costs_total", q2(etf_total), "EUR", f"ETF costs over {years} years")
    r.add("active_costs_total", q2(act_total), "EUR", f"Active fund costs over {years} years")
    r.add("cost_difference", q2(act_total - etf_total), "EUR", "Cost difference")
    r.add("front_load_amount", q2(amount * load), "EUR", "Front-end load in EUR")
    return r


# --------------------------------------------------------------------------- drift / duplicates
def allocation_drift(target: dict[str, float], current: dict[str, float]) -> CoreResult:
    r = CoreResult("allocation_drift", {"target": target, "current": current}, {},
                   ["Drift in percentage points = current share - target share"])
    for k in sorted(set(target) | set(current)):
        r.add(f"drift_{k}", q2((current.get(k, 0) - target.get(k, 0)) * 100), "PP", f"Drift {k} (pp)")
    return r


def duplicate_coverage(contracts: list[dict[str, Any]]) -> CoreResult:
    """contracts = [{"risk": "private_liability", "holder": "own"}, {"risk": "private_liability", "holder": "family"}]"""
    by_risk: dict[str, list[dict[str, Any]]] = {}
    for c in contracts:
        by_risk.setdefault(c["risk"], []).append(c)
    dups = {k: v for k, v in by_risk.items() if len(v) > 1}
    r = CoreResult("duplicate_coverage", {"contracts": contracts}, {"duplicates": sorted(dups)},
                   ["Two or more contracts covering the same risk for the same person",
                    "Whether the family policy still covers the person depends on its conditions (e.g. own household, end of first education)"])
    r.add("duplicate_risks", len(dups), "COUNT", "Number of risks covered more than once")
    return r


# --------------------------------------------------------------------------- category selection
def select_category(profile: dict[str, Any], topic: str, rules: RuleBook, category_rules: dict[str, Any],
                    as_of: dt.date) -> CoreResult:
    """Rule-based Category Recommendation (FR-20, FR-27). Inputs: profile + rules only."""
    short_h = float(rules.value("short_horizon_years_max", as_of))
    horizon = profile.get("investment_horizon_years")
    risk = _risk_bucket(profile)
    ef_ok = _ef_adequate(profile, rules, as_of)
    surplus = profile.get("monthly_surplus")
    if surplus is None and profile.get("net_income") is not None and profile.get("monthly_expenses") is not None:
        surplus = profile["net_income"] - profile["monthly_expenses"]
    conflicts: list[str] = []
    reasons: list[str] = []
    cat, eq = "", None

    if topic == "protection_duplicate":
        cat = "review_duplicate_cover"
        reasons.append("Same risk appears to be covered by more than one contract")
    elif topic == "leverage_or_loan":
        cat = "no_credit_financed_investing"
        reasons.append("Credit-financed equity investing adds loss risk the profile cannot absorb")
        if ef_ok is False:
            conflicts.append("emergency fund below target")
    elif topic == "product_variant_tax":
        cat = "distributing_or_accumulating_with_unused_allowance"
        reasons.append("Unused saver allowance can absorb distributions or the advance lump sum")
    elif topic == "sustainability_fit":
        cat = "sustainable_diversified_etf"
        reasons.append("Stated sustainability preference must be met (MiFID II sustainability preferences)")
    elif topic == "diversification":
        cat = "broad_world_equity_etf_for_diversification"
        reasons.append("Concentrated single-sector holdings; a broad index reduces concentration risk")
    elif horizon is not None and horizon <= short_h:
        cat = "short_term_deposit_or_money_market"
        reasons.append(f"Horizon of {horizon} years is at most {short_h:g} years")
    elif ef_ok is False:
        cat = "build_emergency_fund_first"
        reasons.append("Emergency fund below target")
        conflicts.append("emergency fund below target")
    elif risk in ("very_low",) or profile.get("loss_aversion") in ("high", "very_high"):
        cat = "deposit_or_low_risk_mix"
        reasons.append("Very low risk tolerance or strong loss aversion")
    else:
        cat = "diversified_low_cost_world_equity_etf_savings_plan" if (risk in ("high", "very_high")) \
            else "balanced_etf_portfolio"
        eq = category_rules["equity_share_by_risk"].get(risk or "medium")
        reasons.append(f"Risk tolerance '{risk or 'medium'}' with horizon {horizon} years")

    requested = profile.get("requested_monthly_amount")
    if requested is not None and surplus is not None and requested > surplus:
        conflicts.append("requested monthly amount exceeds monthly surplus")

    r = CoreResult(
        "select_category",
        {"profile": profile, "topic": topic, "as_of": as_of.isoformat()},
        {"category": cat, "reasons": reasons, "conflicts": conflicts, "risk_bucket": risk},
        ["Selection inputs are limited to the profile and Rule Parameters (no commission, no product preference)",
         "Category level only: no issuer, ISIN, insurer or tariff"],
    )
    if eq:
        r.add("equity_share_min", q2(eq[0] * 100), "COUNT", "Equity share lower bound (%)")
        r.add("equity_share_max", q2(eq[1] * 100), "COUNT", "Equity share upper bound (%)")
    if surplus is not None:
        r.add("monthly_surplus", q2(surplus), "EUR", "Monthly surplus")
    if requested is not None:
        r.add("requested_monthly_amount", q2(requested), "EUR", "Requested monthly amount")
    return r


def _risk_bucket(p: dict[str, Any]) -> str | None:
    v = (p.get("risk_tolerance") or "").lower().replace(" ", "_")
    for b in ("very_low", "very_high", "low", "medium", "high"):
        if b in v:
            return b
    return None


def _ef_adequate(p: dict[str, Any], rules: RuleBook, as_of: dt.date) -> bool | None:
    ef, exp = p.get("emergency_fund"), p.get("monthly_expenses")
    if ef is None or exp is None:
        return None
    return ef >= exp * float(rules.value("emergency_fund_months_min", as_of))
