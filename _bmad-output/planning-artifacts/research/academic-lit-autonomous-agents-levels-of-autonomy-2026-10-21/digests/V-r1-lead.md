# Verification by lead (round 1), accessed 2026-10-21

Crossref API (api.crossref.org/works/<doi>) confirmed title, venue, volume/issue/pages:
- 10.1518/hfes.46.1.50_30392: Lee & See (2004), Human Factors 46(1), 50-80. verified
- 10.1037/xge0000033: Dietvorst, Simmons & Massey (2015), J. Exp. Psych.: General 144(1), 114-126. verified
- 10.1145/302979.303030: Horvitz (1999), Proc. CHI '99, 159-166. verified
- 10.1016/0304-405X(76)90026-X: Jensen & Meckling (1976), J. Financial Economics 3(4), 305-360. verified
- 10.1518/155534308X284417: Parasuraman, Sheridan & Wickens (2008), JCEDM 2(2), 140-160. verified
- 10.1007/s11704-024-40231-1: Wang et al. (2024), Frontiers of Computer Science 18(6). verified

Primary full text read (PDF text extracted locally):
- Horvitz (1999), https://erichorvitz.com/chi99horvitz.pdf: 12 critical factors, verbatim headings incl. (4) "Inferring ideal action in light of costs, benefits, and uncertainties", (5) "Employing dialog to resolve key uncertainties", (6) "Allowing efficient direct invocation and termination", (7) "Minimizing the cost of poor guesses about action and timing", (8) "Scoping precision of service to match uncertainty" (prefer "doing less" but correctly; minimise "costly undoing"). LookOut decides between do nothing / dialog / act via expected utility eu(A|E)=p(G|E)u(A,G)+[1-p(G|E)]u(A,¬G). verified
- Shavit et al., https://cdn.openai.com/papers/practices-for-governing-agentic-ai-systems.pdf: section headings 4.1-4.7 verbatim (Evaluating Suitability for the Task; Constraining the Action-Space and Requiring Approval; Setting Agents' Default Behaviors; Legibility of Agent Activity; Automatic Monitoring; Attributability; Interruptibility and Maintaining Control). 4.2 names "independently initiating an irreversible large financial transaction" as a decision possibly too important to delegate, and requires proactive user authorisation ("human-in-the-loop"), noting approval fatigue when users must approve many decisions quickly. 4.7: users should be able to halt "a specific category of actions (revoking access to, e.g., financial credentials)". Publication month not stated in the PDF text. verified
