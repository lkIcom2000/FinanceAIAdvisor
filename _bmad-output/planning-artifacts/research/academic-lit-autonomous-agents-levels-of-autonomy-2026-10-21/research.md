---
title: 'academic-lit research: Autonomous agents and levels of autonomy as kernel theory for the Autonomy Gate'
type: 'academic-lit'
topic: 'Autonomous agents and levels of autonomy as kernel theory for the Autonomy Gate'
decision: 'Which theories ground the Autonomy Gate (L0-L4, Mandate, Undo/Revoke) as kernel theories in the thesis literature review?'
source: 'native run (parallel web search, 3 researcher subagents + lead verification)'
status: complete
preset: 'standard'
validation: 'normal'
created: '2026-10-21'
updated: '2026-10-21'
claims_verified: 10
claims_unverified: 1
claims_disputed: 1
---

# academic-lit research: Autonomous agents and levels of autonomy as kernel theory for the Autonomy Gate

**Decision this research serves:** Which theories ground the Autonomy Gate (L0–L4, Mandate, Undo/Revoke) as kernel theories in the thesis literature review?

## Executive summary

**Answer.** Four strands of literature together give the Autonomy Gate a full theoretical grounding. Use them as the kernel theories of the autonomy part of the thesis, in this order of weight:

1. **Levels of automation** (Sheridan & Verplank 1978 [4]; Parasuraman, Sheridan & Wickens 2000 [5]). These are the direct ancestor of the L0–L4 ladder. They justify assigning a level *per function or action* rather than per system. Their secondary evaluation criteria, *automation reliability* and *costs of decision/action consequences* [5], map almost one-to-one onto the gate criteria. Reliability corresponds to data confidence. Cost of consequences corresponds to reversibility, amount and regulatory class.
2. **Delegation and control.** Principal–agent theory (Jensen & Meckling 1976 [16]) frames the Mandate as a bounded delegation contract. Notifications act as *monitoring*, and Undo/Revoke act as *bonding* that caps residual loss. Mixed-initiative interaction (Horvitz 1999 [22]) supplies the decision rule between *act*, *ask* and *do nothing* under uncertainty, together with the principles "efficient direct invocation and termination" and "minimizing the cost of poor guesses" [22].
3. **Trust and reliance.** Lee & See's *appropriate reliance* and *calibration* [18], with algorithm aversion and appreciation [19][20][21], justify visible provenance, user-adjustable parameters and confirmation gates for large actions.
4. **LLM agents and agentic-AI governance.** This strand positions the design. Mainstream LLM agents let the model plan and pick tools (ReAct [11]; the survey frameworks [9][10]). The thesis deliberately moves planning to a deterministic orchestrator. Recent work treats autonomy as a *design decision* (Feng et al. 2025 [13]), argues that risk grows with ceded control (Mitchell et al. 2025 [12]; Chan et al. 2023 [14]), and recommends *requiring approval* and *interruptibility*. The approval recommendation names "an irreversible large financial transaction" explicitly (Shavit et al. 2023 [15], §4.2, §4.7).

**Main findings that drive this answer.**

- The L3 level ("execute within Mandate, notify, Undo/Revoke") has **no direct counterpart** in any framework found. In Sheridan & Verplank it lies between L6 (veto window) and L7 (execute, then necessarily inform) [4][8]. In Feng et al. it lies between *Approver* and *Observer* [13]. A standing, user-pre-committed bound combined with *post-hoc reversal* is therefore a defensible **theoretical contribution** of the thesis. It is a synthesis of levels of automation, agency theory and the mixed-initiative principles, not a borrowed concept.
- Every gate criterion traces to at least one peer-reviewed or primary source (see the mapping table in the Cross-dimension insights section).

**Biggest caveat.** The 10-level wording of Sheridan & Verplank was verified only through secondary sources, whose wording varies [8]. Quote the original report directly or cite it as "as summarised in…". The behavioural-finance justification for pre-commitment (Save More Tomorrow [23]) carries a **low causal-evidence rating** from the US Department of Labor clearinghouse [24]. Lean on Madrian & Shea [28] for the stickiness of defaults, and present pre-commitment as a design rationale, not a proven effect.

## D1 · Classic agent theory and levels of automation

**Agent theory.** Wooldridge & Jennings (1995) is the canonical definition [1]. Under the *weak notion of agency*, an agent shows **autonomy** ("operate without the direct intervention of humans or others, and have some kind of control over their actions and internal state"), **social ability**, **reactivity** and **pro-activeness** ("goal-directed behaviour by taking the initiative") [1]. A "stronger notion" with mentalistic attitudes follows in the paper. Its content was not retrieved this run *(unverified)*. The same survey lists Rao & Georgeff's **belief–desire–intention (BDI)** architecture among the theories of agency [1]. BDI formalises intention as a first-class attitude alongside belief and desire [2][3], with an applied architecture in ICMAS-95 [3] *(medium confidence on details)*.

*Relevance.* Proactive monitoring corresponds to *pro-activeness*. A Mandate can be modelled as a **delegated, bounded intention**. BDI's idea of commitment and reconsideration supports re-asking the user when beliefs change, for example when data confidence drops.

**Levels of automation (LOA).** Sheridan & Verplank's MIT report (1978; NTIS ADA057655) introduced the 10-level scale of human–computer control [4]. Common secondary renderings run from L1 (the human does everything) to L10 (the computer acts autonomously and ignores the human). The intermediate levels include L5 (execute if the human approves), L6 (a restricted time to veto before automatic execution) and L7 ("executes automatically, then necessarily informs the human") [8]. *(Confidence is medium for L6, L7 and L9 and low for the exact wording of the others; secondary sources only.)*

Parasuraman, Sheridan & Wickens (2000) generalise the scale to four **stages**: information acquisition, information analysis, decision and action selection, and action implementation. Each stage can be automated at a different level [5]. They evaluate automation design with **primary criteria** (human performance consequences such as workload, situation awareness and complacency; the sub-items are medium confidence) and **secondary criteria**: *automation reliability* and the *costs of decision/action consequences* [5]. Their 2008 follow-up defends situation awareness, workload and trust as measurable constructs [6].

Kaber (2018) published a critique of the presumptive assumptions in LOA taxonomies [7]. Its content was not retrieved *(unverified)*. Cite it only as a pointer, after reading it.

*Relevance.* The thesis architecture already automates stages unevenly: high automation for information acquisition and analysis (monitoring, Core), deliberately lower automation for decision and execution. This is exactly the per-stage reading in [5].

## D2 · LLM-based agents, levels of autonomy for AI agents, governance

**Mainstream LLM-agent architecture.** Wang et al. (2024) propose a unified construction framework of **profiling, memory, planning and action** modules [9] *(medium: the modules were taken from extracts)*. Xi et al. (2023) frame an LLM agent as **brain, perception and action** [10]. The arXiv version is the verified source. A 2025 Science China Information Sciences version is claimed but its DOI is unverified. **ReAct** (Yao et al., ICLR 2023) interleaves reasoning traces with actions such as API calls, so **the LLM itself selects the next tool** [11].

*Positioning.* The thesis design narrows the LLM to the perception/NLU and phrasing role. A deterministic orchestrator owns planning and tool choice. This is an architectural variant that the survey frameworks do not cover. Frame it as **bounded autonomy**, not as rejecting agent research.

**Levels of autonomy for AI agents.** Feng, McDonald & Zhang (2025) treat autonomy as a **design decision separate from capability** [13]. They define five levels by the user's role: **Operator, Collaborator, Consultant, Approver, Observer**. At the *Approver* level the agent involves the user only at blockers or for consequential actions. At the *Observer* level the user can only monitor and use an off-switch [13]. They propose **autonomy certificates** that cap an agent's maximum level [13]. *(Preprint plus Knight First Amendment Institute essay; not peer-reviewed.)*

Mitchell et al. (2025) argue that **risk to people grows as users cede control**, and that fully autonomous agents should not be developed [12] *(preprint)*. Chan et al. (FAccT 2023) treat agency as a **matter of degree**, driven by underspecification, **directness of impact**, goal-directedness and long-term planning [14].

**Governance practices.** Shavit et al. (2023, OpenAI white paper) list seven practices [15]:

- **4.1** Evaluating Suitability for the Task
- **4.2** Constraining the Action-Space and Requiring Approval
- **4.3** Setting Agents' Default Behaviors
- **4.4** Legibility of Agent Activity
- **4.5** Automatic Monitoring
- **4.6** Attributability
- **4.7** Interruptibility and Maintaining Control

Section 4.2 names "independently initiating an irreversible large financial transaction" as a decision possibly too important to delegate. It requires proactive user authorisation and warns that users who must approve many decisions quickly lose the ability to consider each one [15]. Section 4.7 asks that users can halt "a specific category of actions (revoking access to, e.g., financial credentials)" [15]. *(White paper; text verified in the primary PDF.)*

## D3 · Delegation, trust and financial advice

**Principal–agent theory.** Jensen & Meckling (1976) define agency costs in the delegation of decision authority [16]. Eisenhardt (1989) reviews agency theory's empirical support and its fit for problems with a cooperative structure [17] *(medium)*. The three-part decomposition of agency costs into *monitoring*, *bonding* and *residual loss* is the standard reading. The full text was not read this run, so quote it from the paper.

**Trust in automation.** Lee & See (2004) argue that automation fails when people rely on it inappropriately. Trust guides reliance when full understanding is impractical, and design should aim at **appropriate reliance** through **calibrated** trust. Overtrust leads to misuse and distrust to disuse [18]. The three bases of trust, *performance*, *process* and *purpose*, are medium confidence [18].

**Algorithm aversion and appreciation.** People lose confidence in an algorithm faster than in a human after seeing it err (Dietvorst et al. 2015, five studies) [19]. Letting them **modify an imperfect algorithm even slightly** increases its use and their satisfaction (Dietvorst et al. 2018) [20]. As a counterpoint, lay people may *prefer* algorithmic advice to human advice (Logg et al. 2019) [21]. Replication status was not checked.

**Mixed-initiative interaction.** Horvitz (CHI '99) lists 12 principles for coupling automation with direct manipulation [22]. The ones relevant here are:

- (4) inferring ideal action in light of costs, benefits and uncertainties;
- (5) employing dialog to resolve key uncertainties;
- (6) allowing efficient direct invocation and termination;
- (7) minimising the cost of poor guesses;
- (8) scoping the precision of service to match uncertainty, preferring "doing less" correctly to minimise "costly undoing".

The LookOut prototype chooses between *do nothing*, *dialog* and *act* by expected utility [22]. *(Verified in the primary text.)*

**Pre-commitment and defaults.** In Save More Tomorrow, employees commit in advance to raising savings at future pay rises, with a cap and a free opt-out. Saving rates rose from 4.4% to 10.6% in the first implementation [23]. The US DOL CLEAR review rates the causal evidence as **low** [24] *(disputed)*. Automatic enrolment shows that defaults are sticky and are read as **implicit advice** (Madrian & Shea 2001) [28].

**Robo-advisory.** Jung et al. (2018) set the BISE reference frame for automated financial advice [25]. A design-oriented robo-advisor for risk-averse, low-budget consumers (Jung et al. 2018, Electronic Markets) is the closest prior DSR artefact to the C-class target group [26] *(medium: title paraphrased)*. Cardillo & Chiappini (2024) review the field from 2017 to 2022 in four streams [27]. No systematic review dedicated to *trust* in robo-advice was found.

## Cross-dimension insights

**1. Theory → design principle → design element (the mapping for the chapter).**

| Theory (source) | Design principle derived | Element in the artefact |
|---|---|---|
| LOA scale [4][8]; four-stage model [5] | Autonomy is graded and assigned **per action and per stage**, not per system | L0–L4 ladder; monitoring and analysis highly automated, execution gated |
| PSW secondary criterion "cost of decision/action consequences" [5]; Chan "directness of impact" [14]; Shavit §4.2 [15] | The higher the consequence cost, the lower the permitted level | Gate criteria *reversibility*, *amount relative to wealth*, *regulatory class* |
| PSW secondary criterion "automation reliability" [5]; Horvitz (2), (8) [22] | Act only as precisely as the data allows; under uncertainty "do less" | Gate criterion *data confidence* → cap at L1 |
| Principal–agent theory [16][17]; BDI delegated intention [2][3] | Autonomy only inside an explicit, bounded delegation contract | **Mandate** with bounds (per action, per month, accounts, floor) |
| Agency costs: monitoring [16]; Shavit §4.4–4.5 legibility and monitoring [15]; PSW situation awareness [5][6] | Keep the principal informed at low cost | Immediate **Notification** for every L3 action; audit record; "Why?" |
| Agency costs: bonding and residual loss [16]; Horvitz (6), (7) [22]; Shavit §4.7 [15] | Make failed delegation cheap to reverse and to stop | **One-tap Undo** within one business day; **Revoke** per Mandate |
| Horvitz (4), (5) expected utility of act / ask / nothing [22]; Feng *Approver* [13] | Ask when acting is uncertain or consequential | **L2 confirmation** with exact action and amount; downgrade reason shown |
| Lee & See calibration [18]; Logg algorithm appreciation [21] | Prevent over-reliance by low-literacy users | Confirmation for large actions; visible uncertainty; check badge |
| Dietvorst 2015/2018 [19][20] | Users keep control over parameters; errors are visible and correctable | Editable Mandate bounds; parameter proposal card; Undo |
| Pre-commitment [23]; defaults as implicit advice [28] | The user sets the rule in a calm moment; defaults carry advice responsibility | Mandate creation from a template; template defaults pass the Check Chain |
| Feng autonomy as design decision, certificates [13]; Mitchell [12] | The autonomy ceiling is configured, not emergent | L3 cap configured per action type; L4 excluded from the MVP |
| ReAct and survey frameworks [9][10][11] (contrast) | The LLM never plans or selects tools | Deterministic orchestrator; the LLM only extracts and phrases |

**2. The L3 level is the contribution.** Standing pre-committed bounds (agency theory, pre-commitment) combined with execute-then-inform (LOA L7) and cheap post-hoc reversal (Horvitz 6/7, Shavit 4.7) form a combination that none of the frameworks names as a level. It also resolves a tension in the literature. Shavit §4.2 warns of **approval fatigue** when every action needs a click [15], while PSW warn that over-automation brings **complacency and lost situation awareness** [5]. L3 for small, reversible actions with immediate notification sits between the two.

**3. The criteria are theoretically closed.** Each of the five gate criteria maps to a published construct. No criterion rests on intuition alone. The *thresholds*, such as 5% of liquid assets, remain empirical, which matches the thesis's split between design principles and design parameters.

## Recommendations

1. **Make "Autonomous agents and levels of autonomy" a theory section with three subsections:** (a) agents and LOA [1][4][5], (b) delegation and control [16][22][15], (c) trust and reliance [18][19][20][21]. Use the LLM-agent strand [9]–[14] for positioning in the related-work section. *Confidence: high; the core citations are verified with DOIs.* Feeds the thesis literature review and the design-principles section of the DSR rigor cycle (PRD OQ6).
2. **Present L3 (Mandate + notify + Undo/Revoke) explicitly as the theoretical contribution of the Sub-RQ,** using cross-dimension insight 2. *Confidence: medium.* The absence of a counterpart rests on the frameworks checked here, and no 2026 frameworks were found.
3. **Add the mapping table (insight 1) to the requirement verification matrix** as the rigor-cycle requirements for FR-35 to FR-40. Feeds PRD §8.6 and OQ6.
4. **Downgrade the behavioural-finance argument** from "evidence" to "design rationale", citing [23] together with the caveat [24] and the stronger default evidence [28].
5. **Before submission, read and quote from the originals:** the Sheridan & Verplank levels [4], the agency-cost definition [16], Kaber's critique [7], and the Lee & See trust bases [18].

## Open questions

- **Exact 10-level wording** in Sheridan & Verplank [4]. Answer by obtaining the NTIS/DTIC report ADA057655, or by citing Parasuraman et al. [5], who reproduce the scale (check their Table 1).
- **What Kaber (2018) criticises** [7], and the responses in the same JCEDM issue. This needs institutional access to SAGE.
- **Adjustable autonomy literature** (Bradshaw et al.; Mostafa et al. 2019) as a bridge between agent theory and LOA. Not searched; a Deepen run or a quick Scholar search would cover it.
- **Recent (2026) autonomy frameworks for AI agents.** None were retrieved. Morris et al. 2024 ("Levels of AGI"), Kasirzadeh & Gabriel 2025 and Kapoor et al. 2024 remain unchecked leads.
- **The journal DOI of Xi et al.** [10]. Check on the Springer/SCIS site.
- **EU AI Act Art. 14 (human oversight)** as a normative anchor. Not checked; EUR-Lex.
- **Replication status** of algorithm aversion and appreciation [19][21].

## Source appendix

| # | Finding it supports | Publisher | Pub date | Accessed | Confidence |
|---|---|---|---|---|---|
| [1] | Wooldridge & Jennings, "Intelligent agents: theory and practice", KER 10(2):115–152; weak agency | [Cambridge UP, doi:10.1017/S0269888900008122](https://doi.org/10.1017/S0269888900008122) | 1995-06 | 2026-10-21 | high |
| [2] | Rao & Georgeff, "Modeling rational agents within a BDI-architecture", KR'91, pp. 473–484 | [DBLP record](https://dblp.l3s.de/d2r/resource/publications/conf/kr/RaoG91) | 1991 | 2026-10-21 | high (bibliographic), medium (content) |
| [3] | Rao & Georgeff, "BDI agents: from theory to practice", ICMAS-95, pp. 312–319 | [AAAI](https://aaai.org/?p=70582) | 1995-06 | 2026-10-21 | medium-high |
| [4] | Sheridan & Verplank, *Human and computer control of undersea teleoperators*, MIT Man-Machine Systems Lab, tech. report (NTIS ADA057655) | [NTIS NTRL](https://ntrl.ntis.gov/NTRL/dashboard/searchResults/titleDetail/ADA057655.xhtml) | 1978 | 2026-10-21 | high (bibliographic); level wording unverified |
| [5] | Parasuraman, Sheridan & Wickens, "A model for types and levels of human interaction with automation", IEEE Trans. SMC-A 30(3):286–297 | [IEEE, doi:10.1109/3468.844354](https://doi.org/10.1109/3468.844354) | 2000-05 | 2026-10-21 | high |
| [6] | Parasuraman, Sheridan & Wickens, "Situation awareness, mental workload, and trust in automation…", JCEDM 2(2):140–160 | [SAGE/HFES, doi:10.1518/155534308X284417](https://doi.org/10.1518/155534308X284417) | 2008-06 | 2026-10-21 | high (bibliographic), medium (content) |
| [7] | Kaber, "Issues in human–automation interaction modeling…", JCEDM 12(1):7–24 | [SAGE, doi:10.1177/1555343417737203](https://doi.org/10.1177/1555343417737203) | 2018-03 (online 2017-10) | 2026-10-21 | high (bibliographic); content unverified |
| [8] | Shneiderman, "Human-Centered AI: Reliable, Safe & Trustworthy" (secondary rendering of the 10-level scale), preprint | [arXiv:2002.04087](https://arxiv.org/abs/2002.04087) | 2020 | 2026-10-21 | medium (secondary) |
| [9] | Wang et al., "A survey on large language model based autonomous agents", Front. Comput. Sci. 18(6) | [Springer/HEP, doi:10.1007/s11704-024-40231-1](https://doi.org/10.1007/s11704-024-40231-1) | 2024-03 | 2026-10-21 | high (bibliographic), medium (modules) |
| [10] | Xi et al., "The rise and potential of large language model based agents: a survey", preprint | [arXiv:2309.07864](https://arxiv.org/abs/2309.07864) | 2023-09 | 2026-10-21 | high (preprint); journal version unverified |
| [11] | Yao et al., "ReAct: Synergizing reasoning and acting in language models", ICLR 2023 | [arXiv:2210.03629 (camera-ready)](https://arxiv.org/abs/2210.03629) | 2023 | 2026-10-21 | high |
| [12] | Mitchell, Ghosh, Luccioni & Pistilli, "Fully autonomous AI agents should not be developed", preprint | [arXiv:2502.02649](https://arxiv.org/abs/2502.02649) | 2025-02 (v3 2025-10) | 2026-10-21 | high (preprint) |
| [13] | Feng, McDonald & Zhang, "Levels of autonomy for AI agents", preprint and institute essay | [arXiv:2506.12469](https://arxiv.org/abs/2506.12469); [Knight Institute](https://knightcolumbia.org/content/levels-of-autonomy-for-ai-agents-1) | 2025-07 | 2026-10-21 | high (not peer-reviewed) |
| [14] | Chan et al., "Harms from increasingly agentic algorithmic systems", FAccT '23 | [ACM, doi:10.1145/3593013.3594033](https://doi.org/10.1145/3593013.3594033) | 2023-06 | 2026-10-21 | high |
| [15] | Shavit et al., "Practices for governing agentic AI systems", white paper, §4.1–4.7 | [OpenAI PDF](https://cdn.openai.com/papers/practices-for-governing-agentic-ai-systems.pdf) | 2023 | 2026-10-21 | high (primary text; not peer-reviewed) |
| [16] | Jensen & Meckling, "Theory of the firm: managerial behavior, agency costs and ownership structure", JFE 3(4):305–360 | [Elsevier, doi:10.1016/0304-405X(76)90026-X](https://doi.org/10.1016/0304-405X(76)90026-X) | 1976-10 | 2026-10-21 | high (bibliographic); definitions from standard reading |
| [17] | Eisenhardt, "Agency theory: an assessment and review", AMR 14(1):57–74 | [AoM, doi:10.5465/amr.1989.4279003](https://doi.org/10.5465/amr.1989.4279003) | 1989-01 | 2026-10-21 | high (bibliographic), medium (content) |
| [18] | Lee & See, "Trust in automation: designing for appropriate reliance", Human Factors 46(1):50–80 | [SAGE/HFES, doi:10.1518/hfes.46.1.50_30392](https://doi.org/10.1518/hfes.46.1.50_30392) | 2004 | 2026-10-21 | high (core), medium (three bases) |
| [19] | Dietvorst, Simmons & Massey, "Algorithm aversion…", J. Exp. Psych.: General 144(1):114–126 | [APA, doi:10.1037/xge0000033](https://doi.org/10.1037/xge0000033) | 2015 | 2026-10-21 | high |
| [20] | Dietvorst, Simmons & Massey, "Overcoming algorithm aversion…", Management Science 64(3):1155–1170 | [INFORMS, doi:10.1287/mnsc.2016.2643](https://doi.org/10.1287/mnsc.2016.2643) | 2018-03 | 2026-10-21 | high |
| [21] | Logg, Minson & Moore, "Algorithm appreciation…", OBHDP 151:90–103 | [Elsevier, doi:10.1016/j.obhdp.2018.12.005](https://doi.org/10.1016/j.obhdp.2018.12.005) | 2019-03 | 2026-10-21 | high |
| [22] | Horvitz, "Principles of mixed-initiative user interfaces", CHI '99, pp. 159–166; 12 principles | [ACM, doi:10.1145/302979.303030](https://doi.org/10.1145/302979.303030); [author PDF](https://erichorvitz.com/chi99horvitz.pdf) | 1999-05 | 2026-10-21 | high (primary text) |
| [23] | Thaler & Benartzi, "Save More Tomorrow…", JPE 112(S1):S164–S187; 4.4% → 10.6% | [UChicago Press, doi:10.1086/380085](https://doi.org/10.1086/380085) | 2004-02 | 2026-10-21 | low (causal claim disputed) |
| [24] | US DOL CLEAR evidence review of SMarT: causal evidence rated low | [US Dept. of Labor](https://clear.dol.gov/study/save-more-tomorrow%E2%84%A2-using-behavioral-economics-increase-employee-saving-thaler-benartzi-2004) | n.d. | 2026-10-21 | medium |
| [25] | Jung, Dorner, Glaser & Morana, "Robo-advisory: digitalization and automation of financial advisory", BISE 60(1):81–86 | [Springer, doi:10.1007/s12599-018-0521-9](https://doi.org/10.1007/s12599-018-0521-9) | 2018-02 | 2026-10-21 | high |
| [26] | Jung et al., design-oriented robo-advisor for risk-averse, low-budget consumers, Electronic Markets 28(3):367–380 | [DBLP author listing](https://dblp.org/pid/177/4395) | 2018 | 2026-10-21 | medium (title paraphrased) |
| [27] | Cardillo & Chiappini, "Robo-advisors: a systematic literature review", Finance Research Letters 62 | [Elsevier, doi:10.1016/j.frl.2024.105119](https://doi.org/10.1016/j.frl.2024.105119) | 2024 | 2026-10-21 | high |
| [28] | Madrian & Shea, "The power of suggestion: inertia in 401(k) participation and savings behavior", QJE 116(4):1149–1187 | [OUP/MIT Press, doi:10.1162/003355301753265543](https://doi.org/10.1162/003355301753265543) | 2001-11 | 2026-10-21 | high |

## Staleness map

Seminal works ([1]–[7], [16]–[25], [28]) have no freshness bar. Check them only for being superseded. The fast-ageing claims are the state-of-the-art claims on LLM-agent autonomy frameworks (ML freshness bar: 6 months):

| Claim | Class | Published | Re-check by |
|---|---|---|---|
| Feng et al. five levels as the current reference framework [13] | state of the art (ML) | 2025-07 | 2026-01 (**overdue**: re-check before writing) |
| Mitchell et al. position on autonomy risk [12] (v3) | state of the art (ML) | 2025-10 | 2026-04 (**overdue**) |
| Wang et al. / Xi et al. surveys as best recent surveys [9][10] | state of the art (ML) | 2024–2025 | **overdue** |

The earliest re-check date is already past. A **Refresh** run on "AI agent autonomy levels 2026" should precede the final write-up of the related-work section.
