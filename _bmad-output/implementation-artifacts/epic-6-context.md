# Epic 6 Context: Personal Advice That Fits Me

## Goal

Fine collects the Suitability Profile in one form card, gives Category Recommendations with a Suitability Statement only when the profile allows it, recommends against plans that do not fit, and produces a short IDD demands-and-needs statement (UJ-2, UJ-4).

## Stories

- Story 6.1: Profile module and the completeness gate
- Story 6.2: Suitability assessment in one form card
- Story 6.3: Category Recommendation with a Suitability Statement
- Story 6.4: Short demands-and-needs statement (IDD)

## Requirements & Constraints

- `profile` is the only writer of `suitability_profiles` and `needs_profiles` (AD-12); values are persisted only through the confirm control, every field carries `confirmedAt`; registered with persona reset.
- `StatedProfile` (NLU `statedProfile`) is valid for one turn and never persisted (AD-23). `profile.ProfileCompleteness.evaluate(stored, stated, topic)` is the single completeness implementation (chat, gate, UI), minimums in `config/profile-minimums.yaml` (AND over groups, OR within a group).
- Minimum met → `RECOMMEND_CATEGORY`, strict FR-17 fields still unknown disclosed as limitations; not met → `ASK_PROFILE`. Persisted recommendations follow the strict FR-17 rule (6.2).
- `DialogState` `ASSESSMENT(fieldQueue)` for the form card (AD-24, 6.2); typing a new question cancels it.
- Category selection and cost calculation are pure Core functions whose only inputs are the Suitability Profile and Rule Parameters (FR-27, 6.3).

## Technical Decisions

- Persona fixtures hold no profile (Commc 2026-10-26): reset deletes the persona's profile rows; whether Marco needs a seeded profile for 6.3 is decided in 6.3.
- The advisory domain keeps its `ProfileCompleteness` port (pure, test lambdas); the bean delegates to `profile.ProfileCompleteness`.
- Retro A5: `TurnOrchestrator` was 520 lines at Epic 6 start; extract stage classes when 6.2/6.3 grow it further.

## Cross-Story Dependencies

- 6.2 builds on the 6.1 confirm control (`Profiles.confirm`) and the `ASK_PROFILE` missing fields.
- 6.3 fills `ClassInstruction` `{category}`/`{conflicts}` and `CheckChain.Facts` category selection.
- Experiment counterparts: `experiments/finlab/profile.py`, `experiments/finlab/classify.py:141`, `experiments/finlab/pipeline.py:200-240`, `experiments/data/rules/category_rules.yaml`.
