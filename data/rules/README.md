# Rule Parameters

`rule_parameters.yaml` holds the versioned Rule Parameters (PRD FR-22, AD-9) that the Deterministic Core reads through
`knowledge.RuleParamPort`. The files in this directory are part of the config fingerprint (AD-14).

**Reference date: 2026-06-30.** Values reflect the law and the ECB decisions known on that date (PRD OQ4). Entries marked
by their source as a design parameter or assumption are modelling choices, not legal values.

## Rules

- Append-only: a change adds a new entry with a new `version` and closes the previous one with `valid_to`. Never edit the
  value of an existing `(key, version)`. Startup fails on a duplicate `(key, version)` or overlapping versions of a key.
- `valid_to` is the last day a version applies; `null` means open-ended.
- A key without a version valid at the date asked makes the Core refuse to compute; the turn escalates (UJ-5).

## Modelling decisions

- **Riester subsidy (§§ 10a, 84-86 EStG)** is valid to 2026-12-31. The retirement-savings reform announced for
  2027-01-01 (new subsidy rules and a new subsidised product) is not confirmed, so no version exists from 2027 on. A
  subsidy question about next year or the new subsidised product therefore escalates to a human advisor.
- **ECB key interest rates** (deposit facility, main refinancing operations, marginal lending facility) are stored as
  fractions, one version per decision, with the decision as source. Verify against the ECB before changing the
  reference date.
