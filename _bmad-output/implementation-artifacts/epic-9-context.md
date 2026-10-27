# Epic 9 Context: Rules — Fine Acts Within Limits I Set

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

A persona sets up a rule (Mandate) from a template with explicit bounds. Fine then executes the two L3 Actions (`SHORTFALL_TRANSFER`, `EF_TOP_UP`) within it, notifies immediately, and the persona can undo a transfer with one tap or stop the rule at any time (UJ-1, Lena at 15 Oct).

## Stories

- Story 9.1: Set up a rule with explicit limits (FR-36, FR-7 active Mandates)
- Story 9.2: Fine acts within my rule and tells me immediately (FR-37, FR-38 notify, FR-35 L3 path; preset "UJ-1 shortfall, 15 Oct (Lena)")
- Story 9.3: Undo with one tap (FR-38 Undo, FR-7 Undo control)
- Story 9.4: Stop a rule at any time (FR-38 Revoke, FR-7 Revoke control)

## Requirements & Constraints

- FR-36: a Mandate is created from one of two templates with explicit bounds (max per Action, max per month, source and target account, floor); the user sees a plain-language summary and confirms. Chat can only suggest setting up a rule, never create one (FR-44).
- FR-37: only `EF_TOP_UP` and `SHORTFALL_TRANSFER` can reach L3; every other type is capped at L2 even with all criteria met.
- FR-35 (L3 path): an active covering Mandate with the amount inside its bounds and the projected post-Action state above the floor, plus all other criteria met → L3. Breaching the floor or bounds, or no covering Mandate → L2 with the plain reason (scenarios S2, S5); a revoked Mandate → at most L2 (S3).
- FR-38: every L3 Action writes a "Done for you" Notification in the same transaction as the execution; one-tap Undo within one business day on the simulated clock; Revoke of the Mandate takes effect immediately.
- FR-7: Home shows active Mandates with bounds and Revoke; each executed-L3 Notification has Undo with its deadline.
- Customer text says "rule", never "Mandate"; English number format (`1,240.00 EUR` in confirmations, `1,240 EUR` in text, real minus sign).

## Technical Decisions

- AD-10: `ActionService` is the only writer of `actions` and `mandates`. Entry points: `propose` (monitoring), `confirm(actionId)` (same row), `undo(actionId)` (only `EXECUTED` L3 within one business day by the simulated clock; bypasses the gate; compensating Action linked by `undoesId`; original becomes `UNDONE`), `revoke(mandateId)` (sets `revokedAt`, never deletes). Mandate validity is re-checked at every execution. Execution only via `BankPort`.
- Gate context gets the Mandate from `MandateLookup.covering(scope, action)`; the gate evaluates bounds on the projected post-Action state (floor). Seam from Epic 8: `NoMandates` (replace), `MandateLookup.active` (Story 8.5 `activeRules`).
- Table `mandates` in `actions`, registered with reset (`ActionResetter`, `PersonaOwnedTablesTest`). One transaction per use case (AD-21): mandate create, undo, revoke.
- Study presets: add a `mandate` setup step type to `StudyPresets` and dispatch it in `PrepareStudyUseCase`; preset "UJ-1 shortfall, 15 Oct (Lena)" yields "2 notifications (1 Done for you, 1 Recommendation), 1 rule active".
- Epic 8 leftovers to handle here: `ActionState.proposedAt(L3)` throws; `gateMeasures` ignores a target overnight account after an `EF_TOP_UP`; superseded item still shows Needs your OK (deferred-work §Epic 8).

## UX & Interaction Patterns

- Home "Your rules": rule card titled with the plain-language summary ("Keep my current account above 0 EUR, using my overnight deposit, but never let the overnight deposit fall below 2,000 EUR"), bounds as label/value pairs, "Actions under this rule", "Active since …", **Revoke** (error colour, always visible). Revoked rules move to a collapsed, greyed "Stopped rules" list with the revoke date. Empty state: "You haven't set up any rules. With a rule, Fine can take care of small things for you, like keeping your current account above zero." plus **Set up a rule**.
- Rule setup dialog (M3 stepper, 3 steps): (1) Choose a rule — "Keep a safety cushion in my current account", "Top up my emergency fund"; (2) Set limits — max per transfer, max per month, from and to account, floor, pre-filled by the Core with the suggestion source shown; (3) Check and confirm — full summary, what Fine will and will not do, how to undo and stop; button **Start this rule**. Opened from Home or a "Set up a rule" chip in chat.
- Done-for-you Notification: "I moved 200 EUR from your overnight deposit to your current account, as your rule allows. You can undo this until tomorrow, 5 pm." with explanation and rule reference; **Undo transfer** labelled with the deadline ("until Thu 16 Oct, 5 pm").
- Undo: one tap, no dialog; item flips to "Undone"; snackbar "Transfer undone. 200 EUR is back in your overnight deposit." (no redo); after the window the button becomes "Undo was possible until …"; a late or repeated undo → `ProblemDetail`.
- Revoke dialog: "Stop this rule?" / "Fine won't move money under this rule anymore. Transfers already made stay as they are; you can still undo the last one until …" / **Stop this rule**, **Keep it**.
- Downgrade reasons (UX-DR25): "This needs your OK because {amount} is more than your rule allows ({bound})." / "…because you haven't set up a rule for this." / floor: "…because it would take your overnight deposit below the 2,000 EUR you set as its minimum."
